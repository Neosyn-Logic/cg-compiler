/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.cg.internal.AstUtil.assign;
import static com.neosyn.cg.internal.AstUtil.decrement;
import static com.neosyn.cg.internal.AstUtil.expr;
import static com.neosyn.cg.internal.AstUtil.not;
import static com.neosyn.cg.internal.AstUtil.notZero;
import static com.neosyn.cg.internal.AstUtil.type;
import static com.neosyn.models.util.SwitchUtil.DONE;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.Variable;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.UniqueNameComputer;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines how to schedule a idle statement.
 * 

 *
 */
public class IdleScheduler {

	private static final int IDLE_THRESHOLD = 8;

	private ScheduleFsm schedule;

	public IdleScheduler(ScheduleFsm schedule) {
		this.schedule = schedule;
	}

	/**
	 * Associates the given object to the current transition(s).
	 * 
	 * @param eObject
	 *            a statement or an expression
	 * @return DONE
	 */
	protected Void associate(EObject eObject) {
		for (Transition transition : schedule.getTransitions()) {
			transition.getBody().add(eObject);
		}
		return DONE;
	}

	private void createIdleLoop(StatementIdle idle, int numCycles) {
		int size = TypeUtil.getSize(numCycles);

		// create Cx variable
		Variable cntIdle = CgFactory.eINSTANCE.createVariable();
		cntIdle.setType(type(size, false));

		// create IR variable
		String name = new UniqueNameComputer(schedule.actor.getVariables())
				.getUniqueName("cnt_idle");
		Type type = IrFactory.eINSTANCE.createTypeInt(size, false);
		Var irCntIdle = IrFactory.eINSTANCE.createVar(type, name, true);
		schedule.actor.getVariables().add(irCntIdle);

		// add mapping
		schedule.instantiator.putMapping(schedule.actor, cntIdle, irCntIdle);

		// add assign to cntIdle
		Transition transition = schedule.getTransition();
		transition.getBody().add(assign(cntIdle, expr(numCycles)));

		// start new cycle for loop's body
		schedule.startNewCycle();
		transition = schedule.getTransition();
		associate(idle);

		// save 'fork' state
		State fork = transition.getSource();

		// add condition to transition's scheduler, and decrement counter in loop body
		ExpressionBinary cntIdleNotZero = notZero(expr(cntIdle));
		transition.getScheduler().add(cntIdleNotZero);
		transition.getBody().add(decrement(cntIdle));

		// make edges loop back to fork state
		schedule.mergeTransitions(fork);

		// starts a new cycle from the 'fork' state for exit edge and updates its condition
		transition = schedule.startNewCycleFrom(fork);
		transition.getScheduler().add(not(cntIdleNotZero));
	}

	/**
	 * Schedules the given idle statement
	 * 
	 * @param idle
	 *            a 'idle' statement
	 */
	public void scheduleIdle(StatementIdle idle) {
		int numCycles = schedule.instantiator.evaluateInt(schedule.actor, idle.getNumCycles());

		// check whether we can start idling in this transition or we need a fence first
		boolean isFenceNeeded = false;
		for (Transition transition : schedule.getTransitions()) {
			if (!Schedule.isEmpty(transition.getBody())) {
				isFenceNeeded = true;
				break;
			}
		}

		if (!isFenceNeeded) {
			associate(idle);
			numCycles--;
		}

		if (numCycles > IDLE_THRESHOLD) {
			createIdleLoop(idle, numCycles - 1);
		} else {
			// adds as many transitions as numCycles
			for (int i = 0; i < numCycles; i++) {
				schedule.startNewCycle();
				associate(idle);
			}
		}

		schedule.startNewCycle();
	}

}
