/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.graph;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->This class defines a graph model as a list of vertices and edges, which
 * are both contained in this graph. The model supports hierarchy by making Graph extends
 * Vertex.<!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.graph.Graph#getEdges <em>Edges</em>}</li>
 * <li>{@link com.neosyn.models.graph.Graph#getVertices <em>Vertices</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.graph.GraphPackage#getGraph()
 * @model
 * @generated
 */
public interface Graph extends Vertex {

	/**
	 * Adds the given edge to this graph's edges. Subclasses may (and are expected to) override to
	 * add the given edge to reference lists.
	 * 
	 * @param edge
	 *            an edge
	 */
	void add(Edge edge);

	/**
	 * Adds the given vertex to this graph's vertices. Subclasses may (and are expected to) override
	 * to add the given vertex to reference lists.
	 * 
	 * @param vertex
	 *            a vertex
	 */
	void add(Vertex vertex);

	/**
	 * Creates and adds an edge to this graph between the two given source and target vertices.
	 * 
	 * @param source
	 *            source vertex
	 * @param target
	 *            target vertex
	 * @return the newly-created edge
	 */
	Edge add(Vertex source, Vertex target);

	/**
	 * Returns the value of the '<em><b>Edges</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.graph.Edge}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Edges</em>' containment reference list.
	 * @see com.neosyn.models.graph.GraphPackage#getGraph_Edges()
	 * @model containment="true"
	 * @generated
	 */
	EList<Edge> getEdges();

	/**
	 * Returns the value of the '<em><b>Vertices</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.graph.Vertex}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Vertices</em>' containment reference list.
	 * @see com.neosyn.models.graph.GraphPackage#getGraph_Vertices()
	 * @model containment="true"
	 * @generated
	 */
	EList<Vertex> getVertices();

	/**
	 * Removes the given edge from the list of edges and unlinks it (set its source and target
	 * attributes to <code>null</code>).
	 * 
	 * @param edge
	 *            an edge
	 */
	void remove(Edge edge);

	/**
	 * Removes the given vertex from the list of vertices, along with all its incoming and outgoing
	 * edges.
	 * 
	 * @param vertex
	 *            a vertex
	 */
	void remove(Vertex vertex);

}
