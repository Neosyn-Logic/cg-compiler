/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.node.Node;

/**
 * This class defines a basic if behavior for use in CycleDetector based on Schedule.
 * 

 *
 */
public class IfBehaviorBasic implements IfBehavior {

	private Schedule schedule;

	public IfBehaviorBasic(Schedule schedule) {
		this.schedule = schedule;
	}

	@Override
	public Node fork() {
		return schedule.getNode();
	}

	@Override
	public void join(Node fork) {
		schedule.setNode(fork);
		Action action = schedule.getAction();
		for (Node child : fork.getChildren()) {
			DpnFactory.eINSTANCE.addPatterns(action, schedule.getAction(child));
		}
		fork.clearChildren();
	}

	@Override
	public void startBranch(Node fork) {
		Node node = new Node(fork, DpnFactory.eINSTANCE.copy((Action) fork.getContent()));
		schedule.setNode(node);
	}

}
