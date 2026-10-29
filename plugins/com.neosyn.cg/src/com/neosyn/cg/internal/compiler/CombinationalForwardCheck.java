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

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;

/**
 * TRAPS T160. A {@code combinational} task's actions fire EVERY cycle, so
 * {@code out.write(in.read)} presents {@code out} as valid every cycle -- the idle ones too,
 * repeating whatever {@code in} last held -- unless the read is guarded by {@code in.available}.
 * The language says so, nothing warned, and that one unguarded forward in the Ethernet MAC's
 * ReceiveData made its receive path reject every frame for years (T158).
 *
 * <p>Warned per action: it reads the VALUE of a handshaked input (push, stream, confirm), writes a
 * handshaked output, and looks at no input's valid signal. An action that tests any input's
 * {@code available} is taken as deliberate (the 8088 BIU gates its outputs on the offset that
 * arrives with the data); an input with no valid (a RAM's {@code q}) is not a handshaked input.
 */
public final class CombinationalForwardCheck {

	private CombinationalForwardCheck() {
	}

	/** One warning per offending write, as {@code file:line: message}. */
	public static List<String> unguardedForwards(Actor actor) {
		Set<String> warnings = new LinkedHashSet<>();
		String file = actor.getFileName() == null ? "?" : actor.getFileName().replaceAll(".*[/\\\\]", "");
		Set<Var> inputValues = new HashSet<>();
		Set<Var> inputValids = new HashSet<>();
		for (Port in : actor.getInputs()) {
			if (handshaked(in) && !in.getAdditionalInputs().isEmpty()) {
				inputValues.add(in);
				inputValids.add(in.getAdditionalInputs().get(0));
			}
		}
		for (Action action : actor.getActions()) {
			Set<String> readFrom = new java.util.TreeSet<>();
			boolean guarded = false;
			List<Def> writes = new ArrayList<>();
			for (Iterator<EObject> it = action.eAllContents(); it.hasNext();) {
				EObject o = it.next();
				if (o instanceof Use) {
					Var v = ((Use) o).getVariable();
					if (inputValids.contains(v)) {
						guarded = true;
					} else if (inputValues.contains(v)) {
						readFrom.add(v.getName());
					}
				} else if (o instanceof Def) {
					Var v = ((Def) o).getVariable();
					if (v instanceof Port && actor.getOutputs().contains(v) && handshaked((Port) v)
							&& !((Port) v).getAdditionalOutputs().isEmpty()) {
						writes.add((Def) o);
					}
				}
			}
			if (readFrom.isEmpty() || guarded || writes.isEmpty()) {
				continue;
			}
			for (Def write : writes) {
				String out = write.getVariable().getName();
				int line = write.eContainer() instanceof Instruction
						? ((Instruction) write.eContainer()).getLineNumber() : 0;
				String in = String.join("`, `", readFrom);
				String first = readFrom.iterator().next();
				warnings.add(file + ":" + line + ": `" + out + "` is written in a combinational task, whose"
						+ " actions fire EVERY cycle, after reading `" + in + "` without checking `available` --"
						+ " so `" + out + "` is presented as valid every cycle, the idle ones too. Guard it, e.g."
						+ " `if (" + first + ".available) { ... " + out + ".write(...); }`");
			}
		}
		return new ArrayList<>(warnings);
	}

	private static boolean handshaked(Port port) {
		return port.getInterface() != null && port.getInterface().isSync();
	}
}
