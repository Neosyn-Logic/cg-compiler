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
import static com.neosyn.cg.internal.AstUtil.not;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import java.util.ArrayDeque;
import java.util.Deque;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import com.google.common.base.Joiner;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.Enter;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.StatementAssert;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementBreak;
import com.neosyn.cg.cg.StatementContinue;
import com.neosyn.cg.cg.StatementFence;
import com.neosyn.cg.cg.StatementGoto;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLabeled;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementPrint;
import com.neosyn.cg.cg.StatementReturn;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.TransformerUtil;
import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.util.SwitchUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines a cycle scheduler.
 * 

 * 
 */
public abstract class AbstractCycleScheduler extends VoidCxSwitch {

	private Deque<String> callChain = new ArrayDeque<>();

	protected final ScheduleFsm schedule;

	/**
	 * Creates a new cycle scheduler that will schedule cycles in the given actor.
	 * 
	 * @param mapper
	 *            mapper
	 * @param actor
	 *            an actor
	 */
	public AbstractCycleScheduler(IInstantiator instantiator, Actor actor) {
		schedule = new ScheduleFsm(instantiator, actor);
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
			// A transition whose target is already set has been routed by a
			// break/continue (it jumps to the loop exit/header). Statements that
			// textually follow the break/continue in the same branch are
			// unreachable — do not append them, or they would execute before the
			// implicit goto. This is the "terminate the path" rule (no separate
			// flag needed): in normal flow targets are only set at cycle end.
			if (transition.getTarget() != null) {
				continue;
			}
			transition.getBody().add(eObject);
		}
		return DONE;
	}

	private Void call(VarRef ref, EList<CgExpression> parameters) {
		// visit parameters
		visit(this, parameters);

		// we call 'associate' with a enter object before the call
		// (and later with a leave, see below)
		// this gives us additional flexibility and safety, especially to avoid double visiting
		Enter enter = CgFactory.eINSTANCE.createEnter();
		enter.setFunction(ref);
		enter.setLineNumber(TransformerUtil.getStartLine(ref));
		enter.getParameters().addAll(parameters);
		associate(enter);

		// visit function (schedule its statements)
		Variable variable = ref.getVariable();
		pushName(variable.getName());
		doSwitch(variable);
		popName();

		// and now we call associate with a leave object after the call
		associate(CgFactory.eINSTANCE.createLeave());

		return DONE;
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		VarRef ref = expr.getSource();
		Variable variable = ref.getVariable();
		if (CgUtil.isFunctionNotConstant(variable)) {
			return call(ref, expr.getParameters());
		} else {
			String property = expr.getPropertyName();
			if (PROP_READ.equals(property)) {
				schedule.read(ref);
			} else if (PROP_AVAILABLE.equals(property)) {
				schedule.available(ref);
			} else if (PROP_READY.equals(property)) {
				schedule.ready(ref);
			}

			return super.caseExpressionVariable(expr);
		}
	}

	@Override
	public Void caseStatementAssert(StatementAssert stmt) {
		super.caseStatementAssert(stmt);
		return associate(stmt);
	}

	@Override
	public Void caseStatementAssign(StatementAssign stmt) {
		super.caseStatementAssign(stmt);

		Variable variable = stmt.getTarget().getSource().getVariable();
		if (!CgUtil.isFunctionNotConstant(variable)) {
			// if variable is a function with side effects, 'associate' has already been called by
			// caseExpressionVariable, so we must not associate this statement again
			associate(stmt);
		}

		return DONE;
	}

	@Override
	public Void caseStatementFence(StatementFence stmt) {
		schedule.startNewCycle();
		return DONE;
	}

	@Override
	public Void caseStatementGoto(StatementGoto stmt) {
		return associate(stmt);
	}

	@Override
	public abstract Void caseStatementIf(StatementIf stmtIf);

	@Override
	public Void caseStatementLabeled(StatementLabeled stmt) {
		visit(this, stmt.getStmt());

		String name = getCurrentName();
		if (name == null) {
			name = stmt.getLabel();
		} else {
			name += stmt.getLabel();
		}
		schedule.setStateName(name);

		return DONE;
	}

	@Override
	public Void caseStatementLoop(StatementLoop stmt) {
		if (CgUtil.isLoopSimple(schedule.instantiator, schedule.actor, stmt)
				&& !CgUtil.loopSubtreeHasLoopControl(stmt)) {
			// record this statement (unrolled later in FunctionTransformer). A
			// constant-bound loop is NOT simple if ANY break/continue lives in its
			// subtree (including a NESTED loop): it must FSM-lower so the
			// scheduler — not the FunctionTransformer unroller, which has no
			// break/continue lowering — owns the whole nest. The recursive
			// caseStatementLoop then gives each loop its own exit/header states.
			associate(stmt);
			return DONE;
		}

		SwitchUtil.visit(this, stmt.getInit());

		// start new cycle for loop's body
		schedule.startNewCycle();
		Transition transition = schedule.getTransition();

		// save 'fork' state
		State fork = transition.getSource();

		// pre-create the loop-exit state when the body breaks, so a `break`
		// visited mid-body has a concrete state to jump to (the exit edge is
		// only built after the body).
		boolean hasBreak = loopBodyHasBreak(stmt);
		State loopExit = hasBreak ? schedule.newState() : null;

		// A `for` loop carrying a `continue` needs a dedicated state that runs
		// the `after` (increment) clause before re-testing the condition, so
		// `continue` does not skip the increment. A `while` loop has no `after`,
		// so `continue` jumps straight to the header `fork`.
		boolean useContinueState = loopBodyHasContinue(stmt) && stmt.getAfter() != null;
		State loopContinue = useContinueState ? schedule.newState() : null;
		State continueTarget = (loopContinue != null) ? loopContinue : fork;
		schedule.pushLoop(fork, continueTarget, loopExit);

		// add condition to transition's scheduler
		transition.getScheduler().add(stmt.getCondition());

		// visit condition and body
		schedule.visitCondition(this, stmt.getCondition());
		if (useContinueState) {
			// route the body's normal fall-through AND every `continue` through
			// loopContinue, which runs the `after` clause and then loops back to
			// the header.
			SwitchUtil.visit(this, stmt.getBody());
			schedule.mergeTransitions(loopContinue);
			schedule.startNewCycleFrom(loopContinue);
			SwitchUtil.visit(this, stmt.getAfter());
			schedule.mergeTransitions(fork);
		} else {
			SwitchUtil.visit(this, stmt.getBody(), stmt.getAfter());
			// make edges loop back to fork state (transitions a break/continue
			// already retargeted are skipped — mergeTransitions only fills
			// danglers)
			schedule.mergeTransitions(fork);
		}
		schedule.popLoop();

		// starts a new cycle from the 'fork' state for exit edge and updates its condition
		transition = schedule.startNewCycleFrom(fork);
		transition.getScheduler().add(not(stmt.getCondition()));

		// we also visit the condition on the exit edge
		// why? because we want to register peeks, so they are later transformed
		// to reads (at the next cycle break).
		schedule.visitCondition(this, stmt.getCondition());

		// route the natural exit edge through loopExit so post-loop flow and any
		// `break` converge on the same state, then continue scheduling from it.
		if (hasBreak) {
			schedule.mergeTransitions(loopExit);
			schedule.startNewCycleFrom(loopExit);
		}

		return DONE;
	}

	@Override
	public Void caseStatementBreak(StatementBreak stmt) {
		ScheduleFsm.LoopContext loop = schedule.currentLoop();
		if (loop == null || loop.exit == null) {
			throw new IllegalArgumentException(
					"'break' outside a loop, or loop-exit state missing");
		}
		// Route every still-open current transition to the loop exit; do NOT
		// associate the break statement (the goto is synthesised from the target
		// by addMissingGotos). associate() then skips these closed transitions,
		// so statements after the break in this branch are dropped as unreachable.
		for (Transition transition : schedule.getTransitions()) {
			if (transition.getTarget() == null) {
				transition.setTarget(loop.exit);
			}
		}
		return DONE;
	}

	@Override
	public Void caseStatementContinue(StatementContinue stmt) {
		ScheduleFsm.LoopContext loop = schedule.currentLoop();
		if (loop == null) {
			throw new IllegalArgumentException("'continue' outside a loop");
		}
		// continue jumps to the continue target: the header for a `while` loop,
		// or the dedicated `after`-running state for a `for` loop.
		for (Transition transition : schedule.getTransitions()) {
			if (transition.getTarget() == null) {
				transition.setTarget(loop.continueTarget);
			}
		}
		return DONE;
	}

	/**
	 * Scans a loop body for a `break` that binds to THIS loop (does not descend
	 * into nested loops — an inner break binds to the inner loop).
	 */
	private static boolean loopBodyHasBreak(StatementLoop loop) {
		return loopControlPresent(loop, true, false);
	}

	/** True if the loop body contains a `continue` binding to THIS loop. */
	private static boolean loopBodyHasContinue(StatementLoop loop) {
		return loopControlPresent(loop, false, true);
	}

	/** True if the loop body contains a `break` or `continue` binding to it. */
	private static boolean loopBodyHasLoopControl(StatementLoop loop) {
		return loopControlPresent(loop, true, true);
	}

	/**
	 * True if any branch of the given `if` carries a `break`/`continue` that binds
	 * to the enclosing loop (does not descend into nested loops). Used by the
	 * CycleScheduler to force such an `if` to break the cycle BEFORE the branches,
	 * so the loop-variable updates preceding the `if` are not folded into the
	 * re-evaluated loop condition (which would deadlock — see continue lowering).
	 */
	protected static boolean ifBodyHasLoopControl(StatementIf stmtIf) {
		return loopControlPresent(stmtIf, true, true);
	}

	private static boolean loopControlPresent(EObject scope, boolean wantBreak, boolean wantContinue) {
		for (org.eclipse.emf.ecore.EObject child : scope.eContents()) {
			if (child instanceof StatementLoop) {
				continue; // nested loop owns its own break/continue
			}
			if ((wantBreak && child instanceof StatementBreak)
					|| (wantContinue && child instanceof StatementContinue)) {
				return true;
			}
			if (loopControlPresent(child, wantBreak, wantContinue)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Void caseStatementPrint(StatementPrint stmt) {
		super.caseStatementPrint(stmt);
		return associate(stmt);
	}

	@Override
	public Void caseStatementReturn(StatementReturn stmt) {
		super.caseStatementReturn(stmt);
		return associate(stmt);
	}

	@Override
	public Void caseStatementWrite(StatementWrite stmt) {
		schedule.write(this, stmt);
		return associate(stmt);
	}

	@Override
	public Void caseVariable(Variable variable) {
		if (CgUtil.isFunction(variable)) {
			visit(this, variable.getBody());

			// must not associate the function with the current transition
			return DONE;
		}

		super.caseVariable(variable);
		return associate(variable);
	}

	private String getCurrentName() {
		if (callChain.isEmpty()) {
			return null;
		} else {
			return Joiner.on('_').join(callChain);
		}
	}

	private void popName() {
		callChain.removeLast();
		schedule.setStateName(getCurrentName());
	}

	private void pushName(String name) {
		callChain.addLast(name);
		schedule.setStateName(getCurrentName());
	}

}
