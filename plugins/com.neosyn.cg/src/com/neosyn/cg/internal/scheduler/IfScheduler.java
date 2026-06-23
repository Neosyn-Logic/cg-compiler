/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.scheduler.path.Path;
import com.neosyn.cg.internal.scheduler.path.PathIterable;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.node.Node;
import com.neosyn.models.util.SwitchUtil;

/**
 * This class defines a scheduler of 'if' statements.
 * 

 * 
 */
public class IfScheduler {

	private final Actor actor;

	private final IInstantiator instantiator;

	public IfScheduler(IInstantiator instantiator, Actor actor) {
		this.instantiator = instantiator;
		this.actor = actor;
	}

	private void move(EList<Edge> edges, Transition oldTransition, List<Transition> transitions) {
		int index = edges.indexOf(oldTransition);
		for (Transition transition : transitions) {
			edges.move(index++, transition);
		}
	}

	public void visit() {
		FSM fsm = actor.getFsm();
		for (Transition transition : new ArrayList<>(fsm.getTransitions())) {
			visit(transition);
		}
	}

	private void visit(Transition transition) {
		List<EObject> eObjects = transition.getBody();
		IfAnalyzer analyzer = new IfAnalyzer();
		SwitchUtil.visit(analyzer, eObjects);

		Node node = analyzer.getRoot();
		if (!node.hasChildren()) {
			// no 'if' statement
			return;
		}

		List<Transition> transitions = new ArrayList<>();
		IfDeveloper developer = new IfDeveloper(instantiator, actor);
		for (Path path : new PathIterable(node)) {
			// System.out.println(path);
			transitions.add(developer.visit(transition, path));
		}

		// watch this: we must insert the new transitions AT THE SAME PLACE as the old one
		// why? because order is important: in the case of a loop, we test the condition first
		// so this order MUST BE MAINTAINED
		move(transition.getSource().getOutgoing(), transition, transitions);

		// Null target means self-loop — transition is not in any state's incoming list,
		// so skip the incoming edge reordering
		if (transition.getTarget() != null) {
			move(transition.getTarget().getIncoming(), transition, transitions);
		}

		FSM fsm = actor.getFsm();
		int index = fsm.getTransitions().indexOf(transition);
		fsm.remove(transition);
		fsm.getTransitions().addAll(index, transitions);
	}

}
