/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;

/**
 * Renames entity variables that collide with a name the HDL back end declares for a port.
 * <p>
 * Besides the port itself, each port brings companion signals the back end synthesizes --
 * {@code <port>_valid}, {@code <port>_ready}, and {@code <port>_last} on a framed port --
 * plus {@code clock} and {@code reset_n}. They are declarations in the entity's scope even
 * though no Cg declaration produced them, so a state variable called {@code a_valid} beside
 * a port {@code a} is declared twice and the file does not build:
 *
 * <pre>
 *   iverilog: error: 'a_valid' has already been declared in this scope.   (TRAPS T141)
 *   GHDL:     error: identifier "a" already used for a declaration
 * </pre>
 *
 * <b>VHDL identifiers are case-INSENSITIVE</b> ({@code foldCase}): there a port {@code a} and
 * a constant {@code A} -- distinct in Cg and in Verilog -- also collide. Verilog keeps case, so
 * it renames only exact clashes.
 * <p>
 * Ports win, variables move: a port name is part of the entity's interface and is what the
 * testbench and every instantiating network wire to, whereas a variable is entity-internal
 * and can be called anything. This runs before printing and gives the loser a suffixed name,
 * which every later reference picks up because they all read {@link Var#getName()}.
 */
public class PortNameDisambiguator {

	private final boolean foldCase;

	private PortNameDisambiguator(boolean foldCase) {
		this.foldCase = foldCase;
	}

	/** A name as the target language compares it: lower-cased when it folds case. */
	private String key(String name) {
		return foldCase ? name.toLowerCase(Locale.ROOT) : name;
	}

	/** Names the entity declares that a variable may not take, as {@link #key} spells them. */
	private Set<String> reservedNames(Entity entity) {
		Set<String> taken = new HashSet<>();
		// Always emitted into the port list.
		taken.add("clock");
		taken.add("reset_n");
		for (Port port : entity.getInputs()) {
			addPort(taken, port);
		}
		for (Port port : entity.getOutputs()) {
			addPort(taken, port);
		}
		return taken;
	}

	private void addPort(Set<String> taken, Port port) {
		String name = port.getName();
		if (name == null) {
			return;
		}
		String base = key(name);
		taken.add(base);
		// A `sync ready` / push port also produces these signals in the same scope.
		taken.add(base + "_valid");
		taken.add(base + "_ready");
		if (port.isFramed()) {
			taken.add(base + "_last"); // the frame boundary (TRAPS T58)
		}
	}

	/**
	 * Renames every variable of {@code entity} that would clash with a port.
	 *
	 * @param foldCase
	 *            true for a case-insensitive target (VHDL), false for Verilog
	 */
	public static void disambiguate(Entity entity, boolean foldCase) {
		new PortNameDisambiguator(foldCase).run(entity);
	}

	private void run(Entity entity) {
		Set<String> taken = reservedNames(entity);
		if (taken.isEmpty()) {
			return;
		}

		// Entity-level state variables and constants share the architecture's
		// declarative part with the ports, so they are the ones that can clash.
		for (Var variable : entity.getVariables()) {
			rename(variable, taken);
		}
		// Procedure locals are declared inside a process/function and shadow rather
		// than collide, but a shadowed port is unreadable from that body -- which is
		// a silent wrong-hardware trap, not a compile error. Move them too.
		for (Procedure procedure : entity.getProcedures()) {
			for (Var local : procedure.getLocals()) {
				rename(local, taken);
			}
		}
	}

	private void rename(Var variable, Set<String> taken) {
		String name = variable.getName();
		if (name == null || !taken.contains(key(name))) {
			return;
		}
		// `_v` for variable. A trailing underscore or a doubled one would itself be an
		// illegal VHDL identifier, so the suffix never ends in `_`.
		String candidate = name + "_v";
		int n = 2;
		while (taken.contains(key(candidate))) {
			candidate = name + "_v" + n;
			n++;
		}
		variable.setName(candidate);
		// Claim it, so two variables colliding with the same port get distinct names.
		taken.add(key(candidate));
	}

}
