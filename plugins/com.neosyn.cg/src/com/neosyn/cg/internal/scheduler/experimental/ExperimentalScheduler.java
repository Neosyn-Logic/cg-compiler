/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler.experimental;

import static com.neosyn.cg.internal.AstUtil.and;
import static com.neosyn.cg.internal.AstUtil.expr;
import static com.neosyn.cg.internal.AstUtil.gotoState;
import static com.neosyn.cg.internal.AstUtil.not;

import java.util.ArrayList;
import java.util.List;

import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.AstUtil;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.ICycleListener;
import com.neosyn.cg.internal.scheduler.IfBehavior;
import com.neosyn.cg.internal.scheduler.ScheduleFsm;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.node.Node;

public class ExperimentalScheduler extends CycleScheduler {

	private class CycleListener implements ICycleListener {

		private Branch branch;

		private Node fork;

		public CycleListener(Node fork, Branch branch) {
			this.fork = fork;
			this.branch = branch;
		}

		@Override
		public void newCycleStarted() {
			schedule.removeListener(this);

			Transition currentTransition = schedule.getTransition();

			// Debug logging removed - System.out corrupts LSP JSON-RPC protocol
			for (Transition transition : ScheduleFsm.getTransitions(fork)) {
				if (transition != currentTransition) {
					addGoto(transition, currentTransition.getSource(), branch.getCondition());
				}
			}

			// Transition transition = ScheduleFsm.getTransition(fork);
			// addGoto(transition, currentTransition.getSource(), branch.getCondition());
		}

	}

	public ExperimentalScheduler(IInstantiator instantiator, Actor actor) {
		super(instantiator, actor);
	}

	private void addGoto(Transition transition, State target, CgExpression condition) {
		StatementIf _if = getIfWithGotos(transition.getBody());
		if (_if == null) {
			_if = CgFactory.eINSTANCE.createStatementIf();
			transition.getBody().add(_if);
		}

		Block block = CgFactory.eINSTANCE.createBlock();
		block.getStmts().add(gotoState(target));

		Branch _branch = CgFactory.eINSTANCE.createBranch();
		_branch.setCondition(AstUtil.copy(condition));
		_branch.setBody(block);
		_if.getBranches().add(_branch);
	}

	/**
	 * Translates a multi-cycle if statement.
	 * 
	 * @param stmtIf
	 */
	protected void translateMultiCycleIf(StatementIf stmtIf) {
		IfBehavior behavior = new IfBehaviorMulti(schedule);
		Node forkNode = behavior.fork();

		// visits all branches
		List<CgExpression> previousConditions = new ArrayList<>();
		for (Branch branch : stmtIf.getBranches()) {
			ICycleListener listener = new CycleListener(forkNode, branch);
			schedule.addListener(listener);

			behavior.startBranch(forkNode);

			// reverse previous conditions
			CgExpression condition = expr(true);
			for (CgExpression previous : previousConditions) {
				// visit each previous condition as if it were reversed
				// to properly update peek patterns
				schedule.visitCondition(this, previous);
				condition = and(condition, not(previous));
			}

			// visit this branch's condition
			if (branch.getCondition() != null) {
				condition = and(condition, branch.getCondition());
				previousConditions.add(branch.getCondition());
			}

			// visits the branch
			schedule.visitBranch(this, branch);

			schedule.removeListener(listener);
		}

		behavior.join(forkNode);
	}

}
