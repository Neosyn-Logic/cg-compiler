/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.util.DpnSwitch;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.Void;

/**
 * This class defines a transformation that replaces input patterns with tests on ports' additional
 * signals in schedulers, and output patterns with assignments to ports' additional signals.
 *
 * <p>Each action's scheduler is ANDed with the valids of the ports it reads, and nothing else: the
 * else of {@code if (c) { d.read(); } else { ... }} keeps the guard {@code !c}, so while c holds
 * and d has no value the task WAITS -- for a stream and a push port alike. Until TRAPS T167 (S201)
 * a "complement rewrite" (a6b5363, for Intel-8088 HalfRF) turned that else into
 * {@code !(d_valid && c)}, so it also ran whenever d was empty: a checker written that way could
 * finish having read nothing (T16, T166). HalfRF now says what it relied on --
 * {@code if (dest.available() && data.available())} -- and the rewrite is gone.
 */
public class PatternImplementation extends DpnSwitch<Void> {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	@Override
	public Void caseActor(Actor actor) {
		for (Action action : actor.getActions()) {
			updateScheduler(action.getScheduler(), action.getInputPattern().getPorts());
		}
		return DONE;
	}

	private void updateScheduler(Procedure scheduler, List<Port> ports) {
		InstReturn ret = getReturn(scheduler);
		if (ret == null) {
			return;
		}
		Expression expr = buildPortValidsExpr(ports);
		if (expr != null) {
			if (EcoreUtil.equals(ir.createExprBool(true), ret.getValue())) {
				ret.setValue(expr);
			} else {
				ret.setValue(ir.createExprBinary(expr, OpBinary.LOGIC_AND, ret.getValue()));
			}
		}
	}

	/**
	 * AND-combine every additional input (validity signal) of the given ports.
	 * Returns null if no ports contribute any signal.
	 */
	private Expression buildPortValidsExpr(List<Port> ports) {
		Expression expr = null;
		for (Port port : ports) {
			for (Var signal : port.getAdditionalInputs()) {
				if (signal == port.getLast()) {
					// TRAPS T58: the frame boundary is data, not a handshake -- an action never waits on it
					continue;
				}
				if (expr == null) {
					expr = ir.createExprVar(signal);
				} else {
					expr = ir.createExprBinary(expr, OpBinary.LOGIC_AND, ir.createExprVar(signal));
				}
			}
		}
		return expr;
	}

	private InstReturn getReturn(Procedure scheduler) {
		if (scheduler == null || scheduler.getLast() == null
				|| scheduler.getLast().getInstructions().isEmpty()) {
			return null;
		}
		Instruction last = scheduler.getLast().lastListIterator().previous();
		return last instanceof InstReturn ? (InstReturn) last : null;
	}

}
