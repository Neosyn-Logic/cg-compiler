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

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.node.Node;

/**
 * This class defines a if behavior to handle mono-cycle if statements.
 * 

 *
 */
public class IfBehaviorMono implements IfBehavior {

	private Action action;

	private List<Action> actions;

	private ScheduleFsm schedule;

	public IfBehaviorMono(ScheduleFsm schedule) {
		this.schedule = schedule;
		this.action = schedule.getAction();
		actions = new ArrayList<>();
	}

	@Override
	public Node fork() {
		return schedule.getNode();
	}

	@Override
	public void join(Node fork) {
		schedule.getTransition().setAction(action);

		schedule.setNode(fork);
		for (Action branchAction : actions) {
			DpnFactory.eINSTANCE.addPatterns(action, branchAction);
		}
		fork.clearChildren();
	}

	@Override
	public void startBranch(Node fork) {
		Action copy = DpnFactory.eINSTANCE.copy(action);
		schedule.getTransition().setAction(copy);
		actions.add(copy);

		schedule.setNode(new Node(fork, fork.getContent()));
	}

}
