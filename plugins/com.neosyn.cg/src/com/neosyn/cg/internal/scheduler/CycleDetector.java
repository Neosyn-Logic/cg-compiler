/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.cg.CgConstants.PROP_AVAILABLE;
import static com.neosyn.cg.CgConstants.PROP_READ;
import static com.neosyn.cg.CgConstants.PROP_READY;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.StatementBreak;
import com.neosyn.cg.cg.StatementContinue;
import com.neosyn.cg.cg.StatementFence;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.node.Node;
import com.neosyn.models.util.Void;

/**
 * This class defines a cycle detector.
 * 

 * 
 */
public class CycleDetector extends VoidCxSwitch implements ICycleListener {

	private final Schedule schedule;

	/**
	 * Creates a new cycle detector with a new schedule.
	 */
	public CycleDetector(IInstantiator instantiator, Actor actor) {
		schedule = new Schedule(instantiator, actor);
	}

	/**
	 * Creates a new cycle detector with a copy of the given schedule, so if cycle breaks occur, the
	 * given schedule is not modified.
	 * 
	 * @param schedule
	 *            a schedule
	 */
	public CycleDetector(Schedule schedule) {
		this.schedule = new Schedule(schedule);
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		VarRef ref = expr.getSource();
		String property = expr.getPropertyName();
		if (PROP_READ.equals(property)) {
			schedule.read(ref);
		} else if (PROP_AVAILABLE.equals(property)) {
			schedule.available(ref);
		} else if (PROP_READY.equals(property)) {
			schedule.ready(ref);
		}

		Variable variable = ref.getVariable();
		super.caseExpressionVariable(expr);
		if (CgUtil.isFunctionNotConstant(variable)) {
			// if variable is a function with side-effect, we visit it
			doSwitch(variable);
		}

		return DONE;
	}

	@Override
	public Void caseStatementFence(StatementFence stmt) {
		schedule.startNewCycle();
		return DONE;
	}

	@Override
	public Void caseStatementBreak(StatementBreak stmt) {
		// Force an `if` carrying a break to be treated as multi-cycle, so it is
		// developed in the CycleScheduler pass (where the loop-exit/header states
		// are live) rather than deferred to IfDeveloper's separate ScheduleFsm.
		schedule.startNewCycle();
		return DONE;
	}

	@Override
	public Void caseStatementContinue(StatementContinue stmt) {
		schedule.startNewCycle();
		return DONE;
	}

	@Override
	public Void caseStatementIdle(StatementIdle stmt) {
		schedule.startNewCycle();
		return DONE;
	}

	@Override
	public Void caseStatementIf(StatementIf stmtIf) {
		IfBehavior ifBehavior = new IfBehaviorBasic(schedule);
		Node fork = ifBehavior.fork();
		for (Branch branch : stmtIf.getBranches()) {
			ifBehavior.startBranch(fork);
			schedule.visitBranch(this, branch);
		}
		ifBehavior.join(fork);

		return DONE;
	}

	@Override
	public Void caseStatementLoop(StatementLoop stmt) {
		if (CgUtil.isLoopSimple(schedule.instantiator, schedule.actor, stmt)
				&& !CgUtil.loopSubtreeHasLoopControl(stmt)) {
			return DONE;
		}
		// A constant-bound loop carrying break/continue is FSM-lowered (multi-
		// cycle), so it must report a cycle break here too — otherwise an
		// enclosing `if` is wrongly treated as a simple if and deferred to
		// IfDeveloper, whose separate ScheduleFsm has no loop context (the loop
		// variable then fails to resolve during IR generation).

		visit(this, stmt.getInit());
		schedule.startNewCycle();

		// visit condition and body
		schedule.visitCondition(this, stmt.getCondition());
		visit(this, stmt.getBody(), stmt.getAfter());

		// starts a new cycle
		schedule.startNewCycle();

		// this deserves an explanation
		// we visit the condition again because we're on the exit edge of the loop
		// and we must record the peeks we do here
		schedule.visitCondition(this, stmt.getCondition());

		return DONE;
	}

	@Override
	public Void caseStatementWrite(StatementWrite stmt) {
		schedule.write(this, stmt);
		return DONE;
	}

	/**
	 * Returns <code>true</code> if the given object contains cycle breaks.
	 * 
	 * @param eObject
	 *            an EObject
	 * @return a boolean indicating if the object has cycle breaks
	 */
	public boolean hasCycleBreaks(EObject eObject) {
		Schedule oldSchedule = schedule;
		try {
			oldSchedule.addListener(this);
			doSwitch(eObject);
			return false;
		} catch (CycleBreakException e) {
			return true;
		} finally {
			oldSchedule.removeListener(this);
		}
	}

	@Override
	public void newCycleStarted() {
		throw new CycleBreakException();
	}

}
