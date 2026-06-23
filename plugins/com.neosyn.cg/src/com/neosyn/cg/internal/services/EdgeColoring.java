/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import com.neosyn.core.NeosynCore;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.graph.Vertex;

/**
 * This class defines a simple edge-coloring BFS implementation.
 * 

 * 
 */
public class EdgeColoring {

	public static final String TYPE = "com.neosyn.cg.cycleIndicator";

	private IFile file;

	private Set<Vertex> visited;

	public EdgeColoring() {
		visited = new HashSet<>();
	}

	private void visit(Actor actor) {
		FSM fsm = actor.getFsm();
		if (fsm == null) {
			return;
		}

		State state = fsm.getInitialState();
		visit(state, 0);
	}

	private void visit(Edge edge, int index) {
		Transition transition = (Transition) edge;

		try {
			for (int line : transition.getLines()) {
				IMarker marker = file.createMarker(TYPE);
				marker.setAttribute(IMarker.LINE_NUMBER, line);
				marker.setAttribute("index", index);
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}

	public void visit(Entity entity) {
		this.file = entity.getFile();
		if (file == null) {
			return;
		}

		if (entity instanceof Actor) {
			visit((Actor) entity);
		}
	}

	private void visit(Vertex vertex, int index) {
		visited.add(vertex);

		for (Edge edge : vertex.getOutgoing()) {
			visit(edge, index);
		}
		index++;

		for (Vertex succ : vertex.getSuccessors()) {
			if (!visited.contains(succ)) {
				// only visits successor if it has not been visited yet
				visit(succ, index);
			}
		}
	}

}
