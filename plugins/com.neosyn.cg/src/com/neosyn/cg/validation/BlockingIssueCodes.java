/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.validation;

import java.util.Set;

/**
 * The validator issue codes that BLOCK a build. ONE list, read by both the CLI
 * ({@code ValidationHelper}) and the editor ({@code CgResourceValidator}): each kept its own copy
 * until 2026-10-19, the editor's stopped at 4 codes while the CLI's grew to 11, and the editor
 * showed an impossible cast or an untyped port as a yellow warning that the CLI refused to build
 * (TRAPS T4). Every validator ERROR whose code is not here is reported as a warning, in both.
 */
public final class BlockingIssueCodes {

	private BlockingIssueCodes() {
	}

	/**
	 * Issue codes whose @Check ERROR diagnostics hard-fail a CLI build
	 * ({@code generate}/{@code simulate}). Deliberately a short allowlist: the
	 * declarative validators also flag idiomatic narrowing the sim/Verilog
	 * backends handle correctly via truncation (e.g. {@code i = i + 1} on a u8,
	 * the RISC-V multiplier's u128→u64), so blocking on every @Check error would
	 * reject pervasive, valid designs. Codes listed here are genuine silent
	 * miscompiles that the backend does NOT handle. Everything else from the
	 * validators is surfaced as a non-blocking advisory warning. Extend this set
	 * as further real-miscompile checks are vetted.
	 */
	public static final Set<String> CODES = Set.of(
			// A mismatched-width network connection silently zero-extends /
			// truncates on both backends (the S132 check).
			IssueCodes.ERR_PORT_WIDTH_MISMATCH,
			// A bad generic/instantiation argument: a name that isn't a const
			// parameter, or a non-const value where a const is required. The
			// instance is built wrong (or with silent defaults) rather than
			// truncated, so this is a genuine error, not a tolerated narrowing.
			IssueCodes.ERR_GENERIC_ARG_UNKNOWN,
			IssueCodes.ERR_GENERIC_ARG_NOT_CONST,
			// A port with NO TYPE (`in x;`). Unlike the two judgement calls recorded below,
			// this one needs no weighing: the construct emits NOTHING today. The HDL emitter
			// NPEs on the malformed port and the entity is dropped, so `generate` produced
			// only std library modules and still exited 0 behind "Partial success" (T51).
			// No shipped design can depend on the current behaviour, because the current
			// behaviour is that the design does not build. Blocking converts a silent skip
			// into a message that names the port.
			IssueCodes.ERR_PORT_NO_TYPE,
			// Malformed `test:` vectors (T143): the test bench generator casts them and crashes,
			// so the entity was skipped (generate) or a Java stack printed (simulate). Like
			// ERR_PORT_NO_TYPE, nothing is built from them today, so blocking can only replace a
			// crash with the message. A missing or unknown port is NOT coded: both still build.
			IssueCodes.ERR_TEST_VECTORS,
			// The same entity (package + name) defined in two files (T157): one copy was used, by
			// load order, and an edit to the other was silently ignored. Blocking names both files.
			IssueCodes.ERR_DUPLICATE_ENTITY,
			// A cast the types do not allow, e.g. `(u8) someBool` (T26): shown as a warning, then the
			// Verilog printer crashed on it and the task was skipped. Nothing can be built from it.
			IssueCodes.ERR_CAST_IMPOSSIBLE,
			// Arithmetic or a shift on a bool, e.g. `1 - rowp` (T181): a warning, then IR generation
			// crashed on the untyped expression and the task was skipped. Comparisons are not this code.
			IssueCodes.ERR_BOOL_ARITHMETIC,
			// A combinational task that assigns a state variable (T184): it becomes a latch in the Verilog
			// and VHDL. A state variable it only reads (a ROM table) is not this code.
			IssueCodes.ERR_COMBINATIONAL_STATE,
			// A network link between ports with different INTERFACE types (bare to sync/push,
			// either to stream). The handshake the grouped side expects is not there, so the
			// consumer's input never goes valid, its action can never fire, and the design
			// computes nothing while every tool reports success.
			//
			// Added S172, backed out the same day when it reddened 2 of the 15 IP gates, and
			// restored S173 once BOTH of those sites had been measured. Neither was a false
			// positive -- they were two shipped bugs this check had been reporting all along as
			// a warning nobody had to act on:
			//   - Ethernet PhyTop exposed a top-level `phyReady_valid` output that NOTHING drove
			//     (`phyReady` inherited `push` from the port before it in a comma list, while
			//     PhyConfig drives it bare).
			//   - SHA256 TopShaStep1 and TopShaStep2 drove a BARE ROM output into the compression
			//     core's `in push u32` K input. That input never went valid, the core never
			//     fired, and NOTHING WAS EVER COMPUTED. Both tops were ELABORATE-ONLY in the
			//     gate, which proves the RTL is well formed and never that it computes anything;
			//     step 2 even had a value test sitting in the tree that nothing ran. Both now
			//     compute the FIPS-180-4 "abc" digest and are value-checked on both backends.
			//
			// So "a bare-to-sync link is never intentional" is not a guess any more: every
			// instance of it in this codebase was a bug. TRAPS T24.
			IssueCodes.ERR_PORT_INTERFACE_MISMATCH,
			// An early `return` in a void action (TRAPS T35): the simulator honours it, the HDL
			// printers emit nothing for it, so the "skipped" statements run in hardware. It was
			// blocking once (2026-08-23) and un-blocked the same hour because Intel-8088
			// `SplitAddress` depended on the drop. The real defect was T131: the simulator HELD
			// a combinational output the hardware sets to 0, so only the dropped-return spelling
			// passed both back ends. With the simulator matching the hardware, SplitAddress
			// without the guard is green on both and its Verilog is unchanged, so the one
			// shipped use is gone and the construct is blocked.
			IssueCodes.ERR_RETURN_SKIPS_WORK,
			// TRAPS T58: a framed-stream misuse.
			IssueCodes.ERR_FRAMED_MISUSE,
			// A one-bit integer (`u1`, `i1`): decided 2026-10-19 (TRAPS T4, T165). It was a validator
			// error printed as a `warning:` that generate built through, and `bool` is the one-bit type.
			IssueCodes.ERR_TYPE_ONE_BIT,
			// `confirm` / `ack` (SYNC_ACK) ports: the `_ack` input is declared and never read, so a value
			// the consumer is not ready for is lost. Refused, not implemented: decided 2026-10-19 (T11).
			IssueCodes.ERR_CONFIRM_UNSUPPORTED,
			// A state variable's initial value that does not fit its type, or a too-long array initialiser
			// (TRAPS T173): silently truncated, or a simulator crash plus Verilog writing past the array.
			IssueCodes.ERR_INIT_VALUE_OUT_OF_RANGE,
			// Assigning to a `const` (or to a function parameter, which is one) (TRAPS T174): as a warning the
			// build went on, the simulator crashed (IncompatibleClassChangeError on the field) and the
			// Verilog assigned to a localparam -- not legal Verilog -- behind "Success!".
			IssueCodes.ERR_CANNOT_ASSIGN_CONST);
	// NB deliberately NOT here: ERR_OPERATOR_UNDEFINED (`bool == 1`). It looks like a
	// hard error -- the expression types as null and the Verilog emitter NPEs on it --
	// but the BYTECODE backend handles it correctly and on purpose (S162:
	// compareOperands treats a null common type as an int comparison, TestGenerator
	// coerces the vector literals), and SimulateSafetyTest pins that. Blocking it here
	// breaks a supported, tested program. The bug is the Verilog emitter's NPE, and
	// that is where it is fixed.

}
