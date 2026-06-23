/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import java.util.List;

import org.eclipse.emf.common.util.EList;

import com.neosyn.models.graph.Graph;

/**
 * This class defines a Finite State Machine (FSM). A FSM is a directed multi-graph, where a vertex
 * is a state, and an edge is a list of actions.
 * 

 * @model
 */
public interface FSM extends Graph {

	/**
	 * Returns the initial state.
	 * 
	 * @return the initial state
	 * @model
	 */
	State getInitialState();

	/**
	 * Returns the list of this FSM's transitions. This returns the same as {@link #getVertices()}
	 * but as a list of {@link State}s rather than as a list of {@link Vertex}s.
	 * 
	 * @return the list of states
	 */
	EList<State> getStates();

	/**
	 * Returns the list of actions that are the target of transitions from the given source state.
	 * 
	 * @param source
	 *            a state.
	 * @return the list of actions that are the target of transitions from the given source state
	 */
	List<Action> getTargetActions(State source);

	/**
	 * Returns the list of this FSM's transitions. This returns the same as {@link #getEdges()} but
	 * as a list of {@link Transition}s rather than as a list of edges.
	 * 
	 * @return the list of this FSM's transitions
	 */
	EList<Transition> getTransitions();

	/**
	 * Sets the initial state of this FSM to the given state.
	 * 
	 * @param state
	 *            a state
	 */
	void setInitialState(State state);

}
