/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.common.collect.Iterables;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.NamedType;
import com.neosyn.cg.cg.TypeRef;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.graph.Graph;
import com.neosyn.models.graph.GraphFactory;
import com.neosyn.models.graph.Vertex;
import com.neosyn.models.util.Void;

/**
 * This class defines a solver of dependency between variables.
 * 

 *
 */
public class DependencySolver extends VoidCxSwitch {

	private static class VertexAdapter extends AdapterImpl {

		private final EObject contents;

		public VertexAdapter(EObject contents) {
			Objects.requireNonNull(contents);
			this.contents = contents;
		}

		@SuppressWarnings("unchecked")
		public <T extends EObject> T getContent() {
			return (T) contents;
		}

		@Override
		public boolean isAdapterForType(Object type) {
			return type == getClass();
		}

		@Override
		public String toString() {
			return contents.toString();
		}

	}

	private static VertexAdapter getVertexAdapter(EObject eObject) {
		return (VertexAdapter) EcoreUtil.getAdapter(eObject.eAdapters(), VertexAdapter.class);
	}

	private Vertex declaration;

	private List<EObject> eObjects;

	private Graph graph;

	public DependencySolver() {
		eObjects = new ArrayList<>();
	}

	public void add(Variable variable) {
		eObjects.add(variable);
	}

	public void addAll(Iterable<? extends EObject> iterable) {
		Iterables.addAll(eObjects, iterable);
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		super.caseExpressionVariable(expr);
		return visit(this, expr.getSource());
	}

	@Override
	public Void caseTypeRef(TypeRef ref) {
		NamedType typeDef = ref.getTypeDef();
		return handle(typeDef);
	}

	@Override
	public Void caseVariable(Variable variable) {
		CgType type = CgUtil.getType(variable);
		visit(this, type);
		return super.caseVariable(variable);
	}

	@Override
	public Void caseVarRef(VarRef ref) {
		Variable variable = ref.getVariable();
		return handle(variable);
	}

	public void computeOrder() {
		graph = GraphFactory.eINSTANCE.createGraph();
		for (EObject eObject : eObjects) {
			Vertex vertex = GraphFactory.eINSTANCE.createVertex();
			vertex.eAdapters().add(new VertexAdapter(eObject));
			eObject.eAdapters().add(new VertexAdapter(vertex));
			graph.add(vertex);
		}

		for (EObject eObject : eObjects) {
			declaration = getVertexAdapter(eObject).getContent();
			visit(this, eObject);
		}
	}

	public Iterable<EObject> getObjects() {
		Set<EObject> ordered = new LinkedHashSet<>(eObjects.size());
		// use a linked list to remove fast
		List<EObject> workList = new LinkedList<>(eObjects);
		while (!workList.isEmpty()) {
			Iterator<EObject> it = workList.iterator();
			next: while (it.hasNext()) {
				EObject item = it.next();
				Vertex vertex = (Vertex) getVertexAdapter(item).getContent();
				for (Vertex pred : vertex.getPredecessors()) {
					EObject previous = getVertexAdapter(pred).getContent();
					if (!ordered.contains(previous)) {
						continue next;
					}
				}

				it.remove();
				ordered.add(item);
			}
		}

		// remove adapters from objects
		// very important in the case of bundles, whose constants are used in many places
		for (EObject eObject : eObjects) {
			VertexAdapter adapter = getVertexAdapter(eObject);
			eObject.eAdapters().remove(adapter);
		}

		return ordered;
	}

	private Void handle(EObject eObject) {
		VertexAdapter adapter = getVertexAdapter(eObject);
		if (adapter == null) {
			// type not in current entity
			return DONE;
		}

		Edge edge = GraphFactory.eINSTANCE.createEdge();

		Vertex vertex = adapter.getContent();
		edge.setSource(vertex);
		edge.setTarget(declaration);
		graph.add(edge);
		return DONE;
	}

}
