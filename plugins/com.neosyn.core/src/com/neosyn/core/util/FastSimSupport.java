/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.util;

import java.util.Set;

/**
 * Single source of truth for which built-in components the bytecode (fast)
 * simulator can model.
 *
 * <p>A built-in is fast-simulatable only if it has a Java model in the runtime
 * fragment ({@code fragments/com.neosyn.ide.libraries/lib/java/src/std/}). The
 * DDR primitives {@code std.lib.MuxDDR} / {@code std.lib.DemuxDDR} are
 * dual-clock-edge hardware with no such model and cannot be represented in the
 * cycle-based bytecode VM — designs that use them must be simulated with the
 * cycle-accurate Verilog backend instead.
 *
 * <p>Lives in {@code com.neosyn.core} so both the generator
 * ({@code com.neosyn.ide}) and the LSP simulate handler ({@code com.neosyn.cg.ui})
 * can share it without coupling to each other.
 */
public final class FastSimSupport {

	/** The {@code std.*} built-ins that have a bytecode model and can be fast-simulated. */
	public static final Set<String> SUPPORTED_BUILTINS = Set.of(
			"std.mem.SinglePortRAM",
			"std.mem.DualPortRAM",
			"std.mem.PseudoDualPortRAM",
			"std.fifo.SynchronousFIFO",
			"std.lib.SynchronizerFF",
			"std.lib.SynchronizerMux");

	private FastSimSupport() {
	}

	/**
	 * True if the given entity type can be fast-simulated. Non-builtin entities
	 * ({@code null} or not {@code std.*}) are always supported here; only built-ins
	 * without a bytecode model return false.
	 */
	public static boolean isSupported(String entityType) {
		return entityType == null || !entityType.startsWith("std.")
				|| SUPPORTED_BUILTINS.contains(entityType);
	}
}
