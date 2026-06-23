/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.graph.visit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.neosyn.models.graph.Vertex;

/**
 * This class defines an ordering.
 * 

 * 
 */
public abstract class Ordering implements Iterable<Vertex> {

	protected final List<Vertex> vertices;

	protected final Set<Vertex> visited;

	/**
	 * Creates a new topological sorter.
	 */
	public Ordering() {
		vertices = new ArrayList<Vertex>();
		visited = new HashSet<Vertex>();
	}

	/**
	 * Creates a new topological sorter.
	 * 
	 * @param n
	 *            the expected number of vertices
	 */
	protected Ordering(int n) {
		vertices = new ArrayList<Vertex>(n);
		visited = new HashSet<Vertex>(n);
	}

	@Override
	public Iterator<Vertex> iterator() {
		return vertices.iterator();
	}

}
