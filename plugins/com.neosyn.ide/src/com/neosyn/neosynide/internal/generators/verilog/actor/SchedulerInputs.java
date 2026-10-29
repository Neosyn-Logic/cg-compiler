/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.actor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;

/**
 * The module signals a scheduler function reads, which it takes as INPUTS (TRAPS T147).
 *
 * A scheduler that is not inlined is printed as a Verilog function and called from the
 * combinational process, {@code always @(*)}. IEEE 1364 makes {@code @(*)} sensitive to a
 * function's ARGUMENTS only, not to the signals its body reads -- so with no real arguments the
 * process did not re-run when, say, a counter the condition depends on changed, and an
 * event-driven simulator kept a stale {@code ready} (a FIFO reader gated by a counter never
 * popped). Synthesis was right; simulation was not. Passing every signal the body reads, under
 * its own name (the input shadows the module signal, so the body is printed unchanged), puts
 * them in the sensitivity list.
 *
 * Left out: the scheduler's own locals; arrays (Verilog-2001 cannot pass one to a function);
 * and constant state variables, which are printed as {@code localparam}.
 */
final class SchedulerInputs {

	private SchedulerInputs() {
	}

	/** In order of first use, so the declaration and every call agree. */
	static List<Var> of(Action action) {
		Procedure scheduler = action.getScheduler();
		Set<Var> vars = new LinkedHashSet<>();
		for (Iterator<EObject> it = scheduler.eAllContents(); it.hasNext();) {
			EObject object = it.next();
			if (object instanceof Use) {
				Var variable = ((Use) object).getVariable();
				if (variable == null || scheduler.getLocals().contains(variable) || variable.getType().isArray()) {
					continue;
				}
				if (!variable.isAssignable() && variable.eContainer() instanceof Entity) {
					continue;
				}
				vars.add(variable);
			}
		}
		return new ArrayList<>(vars);
	}

}
