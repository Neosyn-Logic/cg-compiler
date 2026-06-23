/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.generator;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.Iterator;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Goto;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.graph.Vertex;
import com.neosyn.models.graph.visit.ReversePostOrder;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.Void;

/**
 * This class defines a beautifier that renames actions/states of the actor's
 * FSM that it visits. It also sorts by topological order.
 * 

 * 
 */
public class FsmBeautifier {

	private static class GotoRemover extends AbstractIrVisitor {

		@Override
		public Void caseInstruction(Instruction inst) {
			if (inst instanceof Goto) {
				delete(inst);
			}
			return DONE;
		}

	}

	private static final int FIRST = 97;

	private static final int RANGE = (122 - FIRST) + 1;

	/**
	 * Generates a new identifier.
	 * 
	 * @return a new identifier
	 */
	private static String newIdentifier(int count) {
		StringBuilder builder = new StringBuilder();
		int work = count;
		do {
			builder.append((char) ((work % RANGE) + FIRST));
			work = (work / RANGE) - 1;
		} while (work >= 0);

		return builder.reverse().toString();
	}

	public static void main(String[] args) {
		System.out.println(newIdentifier(0));
		System.out.println(newIdentifier(1));
		System.out.println(newIdentifier(25));
		System.out.println();
		System.out.println(newIdentifier(26));
		System.out.println(newIdentifier(27));
		System.out.println(newIdentifier(51));
		System.out.println();
		System.out.println(newIdentifier(52));
		System.out.println(newIdentifier(77));
		System.out.println();
		System.out.println(newIdentifier(78));
		System.out.println(newIdentifier(103));
		System.out.println();
		System.out.println(newIdentifier(676));
		System.out.println(newIdentifier(701));
		System.out.println();
		System.out.println(newIdentifier(702));
		System.out.println(newIdentifier(727));
		System.out.println();
		System.out.println(newIdentifier(728));
		System.out.println(newIdentifier(753));
	}

	private FSM fsm;

	private void removeFsm(Actor actor) {
		actor.setFsm(null);

		GotoRemover remover = new GotoRemover();
		for (Action action : actor.getActions()) {
			remover.doSwitch(action.getBody());
		}
	}

	/**
	 * Renames the states of the FSM.
	 * 
	 * @param actorName
	 *            base name of the states (simple name of the actor)
	 */
	private void renameStates(String actorName) {
		// the "currentName" is set by a state with a name
		String currentName = null;
		for (State state : fsm.getStates()) {
			String stateName = state.getName();
			if (stateName == null) {
				if (currentName == null) {
					// this is the case for an actor whose first state has no
					// name
					currentName = "FSM_" + actorName;
				}
				state.setName(currentName);
			} else {
				currentName = stateName;
			}
		}

		// rename consecutive states
		Multiset<String> visited = HashMultiset.create();
		for (State state : fsm.getStates()) {
			String name = state.getName();
			int n = visited.count(name);
			if (n > 0) {
				state.setName(name + "_" + n);
			}

			visited.add(name);
		}
	}

	/**
	 * Sorts the FSM by reverse post-order (equivalent to topological order, but
	 * cycle tolerant).
	 */
	private void sortFsm() {
		ReversePostOrder order = new ReversePostOrder(fsm, fsm.getInitialState());
		int i = 0;
		for (Vertex vertex : order) {
			fsm.getVertices().move(i, vertex);
			i++;
		}
	}

	/**
	 * Visits the given actor
	 * 
	 * @param actor
	 *            an actor
	 */
	public void visit(Actor actor) {
		fsm = actor.getFsm();

		// if FSM is empty removes it from actor and leave
		if (fsm.getStates().isEmpty()) {
			removeFsm(actor);
			return;
		}

		sortFsm();
		renameStates(actor.getSimpleName());
		visitTransitions();

		// if there is only one state, removes the FSM
		if (fsm.getStates().size() <= 1) {
			int i = 0;
			for (Transition transition : fsm.getTransitions()) {
				actor.getActions().move(i, transition.getAction());
				i++;
			}

			// remove FSM
			removeFsm(actor);
		}
	}

	/**
	 * Visits the given transition, renames actions according to the given name,
	 * and set up the line numbers of its body and scheduler procedures.
	 * 
	 * @param transition
	 *            transition
	 * @param name
	 *            name of the action
	 */
	private void visitTransition(Transition transition, String name) {
		Iterator<Integer> it = transition.getLines().iterator();
		int lineNumber = it.hasNext() ? it.next() : 0;

		Action action = transition.getAction();
		action.setName(name);

		Procedure body = action.getBody();
		body.setLineNumber(lineNumber);
		body.setName(name);

		Procedure comb = action.getCombinational();
		comb.setLineNumber(lineNumber);
		comb.setName("comb_" + name);

		Procedure scheduler = action.getScheduler();
		scheduler.setLineNumber(lineNumber);
		scheduler.setName("isSchedulable_" + name);
	}

	/**
	 * Visits all transitions, renaming actions and setting up their line
	 * numbers.
	 */
	private void visitTransitions() {
		for (State state : fsm.getStates()) {
			int i = 0;
			for (Edge edge : state.getOutgoing()) {
				String name = state.getName();
				name += "_" + newIdentifier(i);
				i++;

				visitTransition((Transition) edge, name);
			}
		}
	}

}
