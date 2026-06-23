/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import static org.eclipse.xtext.nodemodel.util.NodeModelUtils.findNodesForFeature;

import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.ImportNormalizer;
import org.eclipse.xtext.scoping.impl.ImportedNamespaceAwareLocalScopeProvider;

import com.google.common.collect.Lists;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Import;
import com.neosyn.cg.cg.Imported;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a simple namespace aware scope provider for Java-like imports in Cx.
 * 

 * 
 */
public class CgImportedNamespaceScopeProvider extends ImportedNamespaceAwareLocalScopeProvider {

	/**
	 * Creates a new imported namespace resolver and adds it to the normalizers list.
	 * 
	 * @param normalizers
	 *            a list of normalizers
	 * @param name
	 *            name
	 * @param ignoreCase
	 *            case (in)sensitive flag
	 */
	private void addImportNormalizer(List<ImportNormalizer> normalizers, String name,
			boolean ignoreCase) {
		ImportNormalizer normalizer = createImportedNamespaceResolver(name, ignoreCase);
		if (normalizer != null) {
			normalizers.add(normalizer);
		}
	}

	@Override
	protected ImportNormalizer doCreateImportNormalizer(QualifiedName importedNamespace,
			boolean wildcard, boolean ignoreCase) {
		return new CgImportNormalizer(importedNamespace, wildcard);
	}

	@Override
	public IScope getScope(EObject context, EReference reference) {
		if (context == null)
			throw new NullPointerException("context");

		IScope result = null;
		if (context.eContainer() != null) {
			IScope outer = getScope(context.eContainer(), reference);
			if (context instanceof Network && reference == CgPackage.Literals.VAR_REF__OBJECTS) {
				Network network = (Network) context;
				Iterable<Inst> instances = network.getInstances();
				Iterable<Variable> ports = CgUtil.getPorts(network.getPortDecls());
				result = Scopes.scopeFor(instances, Scopes.scopeFor(ports, outer));
			} else {
				result = outer;
			}
		} else {
			result = getResourceScope(context.eResource(), CgPackage.Literals.IMPORTED__TYPE);
		}
		return getLocalElementsScope(result, context, reference);
	}

	/**
	 * Returns a list of import normalizers.
	 * 
	 * @param imports
	 *            a list of import directives
	 * @param ignoreCase
	 *            case (in)sensitive flag
	 * @return
	 */
	private List<ImportNormalizer> getImportNormalizers(EList<Import> imports, boolean ignoreCase) {
		List<ImportNormalizer> resolvers = Lists.newArrayList();
		for (Import imp : imports) {
			for (Imported imported : imp.getImported()) {
				String name = getName(imported);
				if (imported.isWildcard()) {
					addImportNormalizer(resolvers, name + ".*", ignoreCase);
				} else {
					addImportNormalizer(resolvers, name, ignoreCase);
				}
			}
		}
		return resolvers;
	}

	/**
	 * Returns the name of the given object.
	 * 
	 * @param to
	 *            an object
	 * @return a name
	 */
	private String getName(Imported imp) {
		List<INode> nodes = findNodesForFeature(imp, Literals.IMPORTED__TYPE);
		if (!nodes.isEmpty()) {
			INode node = nodes.get(0);
			return NodeModelUtils.getTokenText(node);
		}
		return null;
	}

	@Override
	protected List<ImportNormalizer> internalGetImportedNamespaceResolvers(final EObject context,
			boolean ignoreCase) {
		if (context instanceof Module) {
			Module module = (Module) context;
			List<ImportNormalizer> result = Lists.newArrayList(
					getImportNormalizers(module.getImports(), ignoreCase));
			addImplicitPackageImports(result, module.getPackage(), ignoreCase);
			return result;
		} else if (context instanceof CgEntity) {
			CgEntity entity = (CgEntity) context;
			return getImportNormalizers(entity.getImports(), ignoreCase);
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * Add Java-style implicit wildcard imports for the current module's
	 * package and every ancestor package. From package
	 * {@code com.neosyn.arith.gcd} this adds wildcard normalizers for
	 * {@code com.neosyn.arith.gcd.*}, {@code com.neosyn.arith.*},
	 * {@code com.neosyn.*}, and {@code com.*}. With these, sibling-package
	 * partial qualified names like {@code arith.gcd.Gcd} resolve correctly
	 * via the longest matching prefix — replicating the historical Cx
	 * behavior so users don't need to spell out a full import for a same-
	 * project sibling entity.
	 */
	private void addImplicitPackageImports(List<ImportNormalizer> normalizers,
			String pkg, boolean ignoreCase) {
		if (pkg == null || pkg.isEmpty()) {
			return;
		}
		String[] parts = pkg.split("\\.");
		StringBuilder prefix = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				prefix.append('.');
			}
			prefix.append(parts[i]);
			addImportNormalizer(normalizers, prefix.toString() + ".*", ignoreCase);
		}
	}

}
