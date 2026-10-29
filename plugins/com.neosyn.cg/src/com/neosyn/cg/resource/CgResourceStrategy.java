/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.resource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.AbstractRule;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.impl.DefaultResourceDescriptionStrategy;
import org.eclipse.xtext.util.IAcceptor;

import com.google.common.collect.ImmutableMap;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.services.CgPrinter;
import com.neosyn.models.dpn.InterfaceType;

/**
 * This class describes a strategy that exports ports and bundle variables. It creates object
 * descriptions with user data that describes the type and value (if any) of variables.
 * 
 */
public class CgResourceStrategy extends DefaultResourceDescriptionStrategy {

	@Override
	public boolean createEObjectDescriptions(EObject eObject,
			IAcceptor<IEObjectDescription> acceptor) {
		if (isInImplicitBundle(eObject)) {
			// File-scope declarations are private to their file: the scope provider puts them
			// in scope for that file only (CgImportedNamespaceScopeProvider
			// .scopeForFileDeclarations). Exported, a member of the unnamed bundle got the name
			// `<package>.<member>`, and the implicit same-package import made it resolve from
			// EVERY file of the package -- depending on load order -- where the back end cannot
			// compile it: a NullPointerException, "no compiled body" or "Unresolved reference",
			// instead of "twice cannot be resolved" at the use (user report, 2026-09-28).
			return false;
		}
		if (eObject instanceof Variable) {
			Variable variable = (Variable) eObject;
			if (CgUtil.isPort(variable)) {
				createVariable(variable, acceptor);
			} else {
				Bundle bundle = EcoreUtil2.getContainerOfType(variable, Bundle.class);
				if (bundle != null) {
					// only bundles export constants and functions
					createVariable(variable, acceptor);
				}
			}
		} else if (eObject instanceof Typedef) {
			Typedef typedef = (Typedef) eObject;
			Bundle bundle = EcoreUtil2.getContainerOfType(typedef, Bundle.class);
			if (bundle != null) {
				// only bundles export typedefs
				createTypedef(typedef, acceptor);
			}
		} else if (eObject instanceof Inst) {
			Inst inst = (Inst) eObject;
			createInst(inst, acceptor);
		} else {
			return super.createEObjectDescriptions(eObject, acceptor);
		}

		// no need to visit contents of variable or typedef
		return false;
	}

	/**
	 * Is this object the implicit (file-scope) bundle, or inside it? Recognised by the grammar
	 * rule that parsed it, not by a null name: TopEntitiesLoader names the bundle
	 * `<file>_pkg` when it instantiates it.
	 */
	private static boolean isInImplicitBundle(EObject eObject) {
		return isImplicitBundle(EcoreUtil2.getContainerOfType(eObject, Bundle.class));
	}

	/** Is this the implicit (file-scope) bundle of its file? */
	public static boolean isImplicitBundle(Bundle bundle) {
		if (bundle == null) {
			return false;
		}
		ICompositeNode node = NodeModelUtils.getNode(bundle);
		if (node == null) {
			return false;
		}
		EObject element = node.getGrammarElement();
		AbstractRule rule = element instanceof RuleCall ? ((RuleCall) element).getRule()
				: GrammarUtil.containingRule(element);
		return rule != null && "ImplicitBundle".equals(rule.getName());
	}

	private void createInst(Inst inst, IAcceptor<IEObjectDescription> acceptor) {
		Map<String, String> userData = null;

		// if inst has arguments, adds it to user data
		Obj obj = inst.getArguments();
		if (obj != null) {
			userData = ImmutableMap.of("properties", new CgPrinter().toString(obj));
		}

		// create eobject description
		QualifiedName qualifiedName = getQualifiedNameProvider().getFullyQualifiedName(inst);
		if (qualifiedName != null) {
			acceptor.accept(EObjectDescription.create(qualifiedName, inst, userData));
		}
	}

	private void createTypedef(Typedef typedef, IAcceptor<IEObjectDescription> acceptor) {
		QualifiedName qualifiedName = getQualifiedNameProvider().getFullyQualifiedName(typedef);
		if (qualifiedName != null && typedef.getType() != null) {
			String type = new CgPrinter().toString(typedef.getType());
			Map<String, String> userData = ImmutableMap.of("type", type);
			acceptor.accept(EObjectDescription.create(qualifiedName, typedef, userData));
		}
	}

	private void createVariable(Variable variable, IAcceptor<IEObjectDescription> acceptor) {
		StringBuilder builder = new StringBuilder();
		Iterator<Variable> it = variable.getParameters().iterator();
		if (it.hasNext()) {
			builder.append('(');
			getType(builder, it.next());
			while (it.hasNext()) {
				builder.append(',');
				getType(builder, it.next());
			}
			builder.append(")->");
		}
		getType(builder, variable);

		for (CgExpression dim : variable.getDimensions()) {
			builder.append('[');
			new CgPrinter(builder).doSwitch(dim);
			builder.append(']');
		}

		// add type to user data
		Map<String, String> userData = new HashMap<>(2);
		userData.put("type", builder.toString());

		// if variable has value, adds it to user data
		EObject value = variable.getValue();
		if (value != null) {
			userData.put("value", new CgPrinter().toString(value));
		}

		// add interface type for ports
		if (CgUtil.isPort(variable)) {
			InterfaceType iface = CgUtil.getInterface(variable);
			userData.put("interface", iface.toString());
		}

		// create eobject description
		QualifiedName qualifiedName = getQualifiedNameProvider().getFullyQualifiedName(variable);
		if (qualifiedName != null) {
			acceptor.accept(EObjectDescription.create(qualifiedName, variable, userData));
		}
	}

	private void getType(StringBuilder builder, Variable variable) {
		CgType type = CgUtil.getType(variable);
		if (type != null) {
			new CgPrinter(builder).doSwitch(type);
		}
	}

}
