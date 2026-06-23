/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import com.neosyn.models.dpn.State;
import com.neosyn.models.node.Node;

/**
 * This class defines a if behavior to handle multi-cycle if statements.
 * 

 *
 */
public class IfBehaviorMulti implements IfBehavior {

	private ScheduleFsm schedule;

	private State state;

	public IfBehaviorMulti(ScheduleFsm schedule, State fork) {
		this.schedule = schedule;
		this.state = fork;
	}

	@Override
	public Node fork() {
		return schedule.getNode();
	}

	@Override
	public void join(Node fork) {
		schedule.setNode(fork);
	}

	@Override
	public void startBranch(Node fork) {
		schedule.setNode(new Node(fork));
		schedule.addTransitionFrom(state);
	}

}
