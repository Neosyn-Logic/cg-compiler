/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.linking;

import static com.neosyn.cg.cg.CgPackage.Literals.VAR_REF__OBJECTS;
import static org.eclipse.xtext.nodemodel.util.NodeModelUtils.findNodesForFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.linking.impl.IllegalNodeException;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;

import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a linking service for Cx that can link qualified variable references.
 * 

 *
 */
public class CgLinkingService extends DefaultLinkingService {

	@Override
	public List<EObject> getLinkedObjects(EObject context, EReference ref, INode node)
			throws IllegalNodeException {
		final EClass requiredType = ref.getEReferenceType();
		if (requiredType == null) {
			return Collections.<EObject> emptyList();
		}

		if (!(context instanceof VarRef)) {
			return super.getLinkedObjects(context, ref, node);
		}

		VarRef varRef = (VarRef) context;
		return linkObjects(varRef, node);
	}

	private List<String> getSegments(List<INode> nodes) {
		List<String> segments = new ArrayList<>();
		for (INode node : nodes) {
			segments.add(getCrossRefNodeAsString(node));
		}
		return segments;
	}

	/**
	 * Resolves the <code>i</code><sup>th</sup> prefix of the given VarRef, where <code>i</code> is
	 * the position of <code>node</code> in the VarRef's nodes corresponding to the prefixes.
	 * 
	 * @param ref
	 *            variable reference
	 * @param node
	 *            a node matching a proxy we should resolve
	 * @return if no match can be found, an empty list, otherwise a singleton with the resolved
	 *         object
	 */
	private List<EObject> linkObjects(VarRef ref, INode node) {
		List<INode> nodes = findNodesForFeature(ref, VAR_REF__OBJECTS);
		List<String> segments = getSegments(nodes);
		int i = nodes.indexOf(node);

		// important: we need to make sure objects before this one are resolved
		// because ReferenceFinder solves in any order, and this messes up linking.
		// This is done by calling "get" on each object, causing proxies to be resolved
		// (by recursively invoking the linking service)
		boolean resolved = true;
		for (int j = 0; j < i; j++) {
			// resolved = all previous objects have been successfully resolved (<=> are not proxies)
			resolved &= !ref.getObjects().get(j).eIsProxy();
		}

		// solves ith segment
		final IScope scope = getScope(ref, VAR_REF__OBJECTS);
		QualifiedName name = QualifiedName.create(segments.subList(0, i + 1));
		IEObjectDescription objDesc = scope.getSingleElement(name);
		if (objDesc == null) {
			EObject cter = ref.eContainer();

			// must be the latest segment (and there must be more than one segment!)
			// and all segments before this one must have been resolved
			int last = segments.size() - 1;
			if (i > 0 && i == last && resolved && cter instanceof ExpressionVariable) {
				ExpressionVariable expr = (ExpressionVariable) cter;
				// Debug logging removed - System.out corrupts LSP JSON-RPC protocol
				// if (expr.getProperty() != null) {
				//     Variable variable = expr.getProperty();
				//     String varName = variable.getName();
				//     // existing variable in ExpressionVariable: varName
				// }

				// create variable with property name and set it on expr
				Variable variable = CgFactory.eINSTANCE.createVariable();
				variable.setName(segments.get(i));

				expr.eSetDeliver(false);
				expr.setProperty(variable);
				expr.eSetDeliver(true);

				return Collections.singletonList(variable);
			}

			return Collections.emptyList();
		} else {
			return Collections.singletonList(objDesc.getEObjectOrProxy());
		}
	}

}
