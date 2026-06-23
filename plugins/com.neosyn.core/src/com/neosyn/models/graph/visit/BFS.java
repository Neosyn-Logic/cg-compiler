/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.graph.visit;

import java.util.ArrayDeque;
import java.util.Deque;

import com.neosyn.models.graph.Vertex;

/**
 * This class defines Breadth-First Search (BFS) for a graph.
 * 

 * 
 */
public class BFS extends Ordering {

	/**
	 * Builds the list of vertices that can be reached from the given vertex
	 * using breadth-first search.
	 * 
	 * @param vertex
	 *            a vertex
	 */
	public BFS(Vertex vertex) {
		visitVertex(vertex);
	}

	/**
	 * Builds the search starting from the given vertex.
	 * 
	 * @param vertex
	 *            a vertex
	 */
	public void visitVertex(Vertex vertex) {
		Deque<Vertex> visitList = new ArrayDeque<Vertex>();
		visitList.addLast(vertex);

		while (!visitList.isEmpty()) {
			Vertex next = visitList.removeFirst();

			// only adds the successors if they have not been visited yet.
			if (!visited.contains(next)) {
				visited.add(next);
				vertices.add(next);
				for (Vertex succ : next.getSuccessors()) {
					visitList.addLast(succ);
				}
			}
		}
	}

}
