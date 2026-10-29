/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;

/**
 * TRAPS T58 (decided 2026-10-01): {@code din.last} is the frame boundary of the value read IN THE
 * SAME CYCLE (AXI-Stream TLAST), a wire, not a register. Read in a cycle that does not read
 * {@code din}, it is whatever is on the wire then -- the next value's flag, or nothing at all
 * while {@code din} is not valid -- and the design compiles and runs wrong without a word.
 *
 * <p>Checked on the scheduled IR, where one action is one cycle: an action that loads
 * {@code din_last} must also read (or peek) {@code din}. Exact where a source-level check could
 * only guess at the cycles: {@code if (din.last) {...} v = din.read();} in one cycle is fine.
 *
 * <p>TRAPS T150: a guard ({@code isSchedulable_*}) replays the cycle's code to evaluate a later
 * condition, so the guard of the branch that does NOT read {@code din} can still hold a copy of
 * {@code bool l = din.last;} whose value goes nowhere. Such a load counts only when the guard's
 * result can depend on it.
 */
public final class FramedLastCheck {

	private FramedLastCheck() {
	}

	/**
	 * One warning per source line that reads {@code .last} of a framed input in a cycle that does
	 * not read that input, as {@code file:line: message}.
	 */
	public static List<String> lastOutsideRead(Actor actor) {
		Set<String> warnings = new LinkedHashSet<>();
		String file = actor.getFileName() == null ? "?" : actor.getFileName().replaceAll(".*[/\\\\]", "");
		for (Action action : actor.getActions()) {
			Set<Var> liveInGuard = live(action.getScheduler());
			for (Iterator<EObject> it = action.eAllContents(); it.hasNext();) {
				EObject o = it.next();
				if (!(o instanceof InstLoad) || ((InstLoad) o).getSource() == null) {
					continue;
				}
				InstLoad load = (InstLoad) o;
				if (in(load, action.getScheduler()) && !liveInGuard.contains(load.getTarget().getVariable())) {
					continue;
				}
				for (Port port : actor.getInputs()) {
					if (port.getLast() == null || load.getSource().getVariable() != port.getLast()) {
						continue;
					}
					boolean read = action.getInputPattern() != null && action.getInputPattern().contains(port)
							|| action.getPeekPattern() != null && action.getPeekPattern().contains(port);
					if (!read) {
						String name = port.getName();
						warnings.add(file + ":" + load.getLineNumber() + ": `" + name + ".last` is read in a"
								+ " cycle that does not read `" + name + "` -- `last` belongs to the value read IN"
								+ " THAT CYCLE (AXI TLAST); here it is whatever is on the wire. Read it with the"
								+ " value: `u8 v = " + name + ".read(); bool end = " + name + ".last;`");
					}
				}
			}
		}
		return new ArrayList<>(warnings);
	}

	private static boolean in(EObject o, Procedure procedure) {
		for (EObject c = o; c != null; c = c.eContainer()) {
			if (c == procedure) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The variables of {@code procedure} its result can depend on: those read by a return or a
	 * branch or loop condition and, transitively, by the instructions that define them. A guard has
	 * no side effects: the copies of the cycle's {@code print}/{@code assert} it still holds here
	 * (later passes remove them) never run, so a call counts only through the value it returns.
	 */
	private static Set<Var> live(Procedure procedure) {
		Set<Var> live = new HashSet<>();
		if (procedure == null) {
			return live;
		}
		List<Instruction> defining = new ArrayList<>();
		for (Iterator<EObject> it = procedure.eAllContents(); it.hasNext();) {
			EObject o = it.next();
			if (o instanceof BlockIf) {
				addUses(((BlockIf) o).getCondition(), live);
			} else if (o instanceof BlockWhile) {
				addUses(((BlockWhile) o).getCondition(), live);
			} else if (o instanceof Instruction) {
				if (o instanceof InstReturn) {
					addUses(o, live);
				} else if (target((Instruction) o) != null) {
					defining.add((Instruction) o);
				}
			}
		}
		for (boolean changed = true; changed;) {
			changed = false;
			for (Iterator<Instruction> it = defining.iterator(); it.hasNext();) {
				Instruction inst = it.next();
				if (live.contains(target(inst).getVariable())) {
					addUses(inst, live);
					it.remove();
					changed = true;
				}
			}
		}
		return live;
	}

	private static Def target(Instruction inst) {
		EStructuralFeature f = inst.eClass().getEStructuralFeature("target");
		Object t = f == null ? null : inst.eGet(f);
		return t instanceof Def && ((Def) t).getVariable() != null ? (Def) t : null;
	}

	private static void addUses(EObject o, Set<Var> live) {
		if (o == null) {
			return;
		}
		if (o instanceof Use && ((Use) o).getVariable() != null) {
			live.add(((Use) o).getVariable());
		}
		for (Iterator<EObject> it = o.eAllContents(); it.hasNext();) {
			EObject c = it.next();
			if (c instanceof Use && ((Use) c).getVariable() != null) {
				live.add(((Use) c).getVariable());
			}
		}
	}
}
