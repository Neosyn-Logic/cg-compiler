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

import com.neosyn.cg.CgConstants;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;

/**
 * Linking service that resolves qualified C⏚ variable references Xtext's default linker can't —
 * chains like {@code inst.port.field} where each segment's scope depends on the previous
 * segment's resolved type. It fabricates the intermediate property references, but guards the
 * case where an instance's entity is still an unresolved proxy (a same-package {@code new Foo()}),
 * so it does not invent a bogus {@code .computed} property (see {@link #isUnresolvedInstancePort}).
 */
public class CgLinkingService extends DefaultLinkingService {

	@Override
	public List<EObject> getLinkedObjects(EObject context, EReference ref, INode node)
			throws IllegalNodeException {
		final EClass requiredType = ref.getEReferenceType();
		if (requiredType == null) {
			return Collections.<EObject> emptyList();
		}

		// `W[0].read()` -- an indexed ARRAY port. The index breaks the VarRef, so `read`
		// lands in ExpressionVariable.members instead of VarRef.objects and never reaches
		// the property fabrication below. Fabricate it here on the same terms.
		if (context instanceof ExpressionVariable
				&& ref == CgPackage.Literals.EXPRESSION_VARIABLE__MEMBERS) {
			List<EObject> linked = super.getLinkedObjects(context, ref, node);
			if (!linked.isEmpty()) {
				return linked;
			}
			return linkArrayPortProperty((ExpressionVariable) context, node);
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
			if (i > 0 && i == last && resolved && cter instanceof ExpressionVariable
					&& !isUnresolvedInstancePort(ref, i)) {
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

	/**
	 * Fabricates the {@code .read} / {@code .available} property of an indexed array port
	 * reference, mirroring what {@link #linkObjects} does for the un-indexed `x.read()`
	 * (where both segments live in one VarRef).
	 *
	 * <p>
	 * Deliberately narrower than that path, which fabricates a property for ANY unresolved
	 * trailing segment: here only the two real port properties are accepted, so `W[0].raed()`
	 * still reports an unresolved name instead of being silently invented. `.ready` needs no
	 * fabrication -- the grammar gives it its own slot, and getPropertyName() falls back to it.
	 */
	private List<EObject> linkArrayPortProperty(ExpressionVariable expr, INode node) {
		String name = getCrossRefNodeAsString(node);
		if (!CgConstants.PROP_READ.equals(name) && !CgConstants.PROP_AVAILABLE.equals(name)
				&& !CgConstants.PROP_LAST.equals(name)) {
			return Collections.emptyList();
		}
		// only an INDEXED reference splits the property off the VarRef; without an index
		// `x.read()` is handled by linkObjects and must not be touched here.
		if (expr.getIndexes().isEmpty() || expr.getMembers().size() != 1) {
			return Collections.emptyList();
		}
		VarRef source = expr.getSource();
		if (source == null) {
			return Collections.emptyList();
		}
		// Requiring DIMENSIONS here is a safety property, not a nicety. Fabricating the
		// property for an indexed SCALAR port (`x[0].read()`) would leave the index with
		// nothing to bind to, and the lowering would then read the scalar and drop it
		// silently -- validators do not fire on the CLI path (TRAPS T69), so nothing would
		// catch it. Refusing to link instead makes that spelling a hard error everywhere.
		Variable portVar = source.getVariable();
		if (portVar == null || portVar.eIsProxy() || portVar.getDimensions().isEmpty()
				|| !CgUtil.isPort(portVar)) {
			return Collections.emptyList();
		}

		Variable property = CgFactory.eINSTANCE.createVariable();
		property.setName(name);

		expr.eSetDeliver(false);
		expr.setProperty(property);
		expr.eSetDeliver(true);

		return Collections.singletonList(property);
	}

	/**
	 * True when the segment at index {@code i} is a member of a preceding instance
	 * whose entity reference is not yet resolved (still a proxy). This happens for
	 * a reference like {@code checksum.computed} while the {@code checksum} instance's
	 * {@code new Foo()} entity link is transiently unresolved — most notably when the
	 * referencing network is in the SAME package as the entity (lazy-linking
	 * reentrancy). In that state the instance-port scope is empty, so the trailing
	 * segment ({@code computed}) is a REAL port, not a {@code .read}/{@code .available}
	 * style property. Fabricating a synthetic property for it would permanently
	 * mis-bind the reference; returning here leaves it unresolved so it re-links once
	 * the entity proxy resolves.
	 */
	private boolean isUnresolvedInstancePort(VarRef ref, int i) {
		if (i < 1 || i > ref.getObjects().size()) {
			return false;
		}
		EObject prev = ref.getObjects().get(i - 1);
		if (prev instanceof Inst) {
			Inst inst = (Inst) prev;
			if (inst.getTask() == null) {
				Instantiable entity = inst.getEntity();
				return entity == null || entity.eIsProxy();
			}
		}
		return false;
	}

}
