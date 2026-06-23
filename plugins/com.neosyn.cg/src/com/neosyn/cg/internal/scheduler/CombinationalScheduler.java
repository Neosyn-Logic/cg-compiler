/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.models.util.SwitchUtil.DONE;

import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.util.Void;

/**
 * Simple scheduler for combinational actors.
 *
 * Combinational actors have no clocks and execute all logic in one "instant".
 * This scheduler creates a minimal FSM structure (one state, one transition, one action)
 * and associates all statements with that single action. Unlike CycleScheduler + IfScheduler,
 * this scheduler:
 * - Never starts new cycles (no multi-cycle behavior)
 * - Never develops if-statements into multiple transitions
 * - Keeps all if-else branches in a single action
 *
 * This consolidates combinational handling into one place, avoiding scattered
 * isCombinational() checks throughout the scheduling code.
 */
public class CombinationalScheduler extends AbstractCycleScheduler {

	private final Transition transition;
	private final Action action;
	private boolean suppressAssociation;

	/**
	 * Pre-creates the FSM structure for a combinational actor before super() is called.
	 * This ensures ScheduleFsm sees actor.getFsm() != null and doesn't create its own FSM.
	 *
	 * @param actor the actor to set up
	 * @return the same actor (for chaining in super() call)
	 */
	private static Actor setupCombinationalFsm(Actor actor) {
		// Create minimal FSM structure
		FSM fsm = DpnFactory.eINSTANCE.createFSM();
		actor.setFsm(fsm);

		// Single state
		State state = DpnFactory.eINSTANCE.createState();
		fsm.add(state);
		fsm.setInitialState(state);

		// Single transition (self-loop)
		Transition trans = DpnFactory.eINSTANCE.createTransition();
		trans.setSource(state);
		trans.setTarget(state);
		fsm.add(trans);

		// Single action
		Action act = DpnFactory.eINSTANCE.createActionNop();
		actor.getActions().add(act);
		trans.setAction(act);

		return actor;
	}

	/**
	 * Creates a new combinational scheduler for the given actor.
	 *
	 * @param instantiator the instantiator
	 * @param actor the combinational actor
	 */
	public CombinationalScheduler(IInstantiator instantiator, Actor actor) {
		// Static method sets up FSM before super() - ScheduleFsm will see it and not create its own
		super(instantiator, setupCombinationalFsm(actor));

		// Get references to the structures created by setupCombinationalFsm
		FSM fsm = actor.getFsm();
		transition = fsm.getTransitions().get(0);
		action = transition.getAction();

		// CRITICAL: Set the ScheduleFsm's node content to our transition
		// ScheduleFsm skipped this because it saw actor.getFsm() != null
		// But methods like visitBranch() need getTransition() to return our transition
		// Note: Don't use schedule.setTransition() as it creates a new action
		schedule.getNode().setContent(transition);

		// Disable cycle starting for combinational actors - all logic in one instant
		schedule.setDisableCycles(true);
	}

	/**
	 * Schedules the setup and loop functions for a combinational actor.
	 * All statements are associated with the single action.
	 *
	 * @param setup the setup function (may be null)
	 * @param loop the loop function (may be null)
	 */
	public void schedule(Variable setup, Variable loop) {
		// Visit setup function
		if (setup != null) {
			doSwitch(setup);
		}

		// Visit loop function
		if (loop != null) {
			doSwitch(loop);
		}
	}

	@Override
	protected Void associate(org.eclipse.emf.ecore.EObject eObject) {
		if (!suppressAssociation) {
			transition.getBody().add(eObject);
		}
		return DONE;
	}

	@Override
	public Void caseStatementIf(StatementIf stmtIf) {
		// Suppress association during branch visits — visitBranch registers port
		// reads/writes in the action's patterns but must NOT add individual branch
		// body statements to the transition body. Only the TOP-LEVEL if-statement
		// is associated, so FunctionTransformer creates a proper BlockIf structure
		// instead of duplicating branch bodies in the leading BlockBasic.
		//
		// Save/restore suppressAssociation to handle nested if-statements correctly.
		// Without this, a nested if resets the flag to false, causing it (and subsequent
		// statements in the enclosing branch) to be independently associated with the
		// transition body — separated from their variable declaration scope.
		boolean wasSuppressed = suppressAssociation;
		suppressAssociation = true;
		for (Branch branch : stmtIf.getBranches()) {
			schedule.visitBranch(this, branch);
		}
		suppressAssociation = wasSuppressed;
		if (!suppressAssociation) {
			associate(stmtIf);
		}
		return DONE;
	}
}
