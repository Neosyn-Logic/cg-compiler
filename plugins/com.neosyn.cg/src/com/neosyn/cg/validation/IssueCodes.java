/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.validation;

/**
 * This interface defines all codes for issues.
 * 
 */
public interface IssueCodes {

	String ERR_ARRAY_MULTI_NON_POWER_OF_TWO = "errArrayMultiDimNonPowerOfTwo";

	String ERR_CANNOT_ASSIGN_CONST = "errCannotAssignConstant";

	/**
	 * Two or more bundles import each other (directly or transitively), forming a
	 * dependency cycle. Such cycles cause unbounded recursion during
	 * instantiation; the instantiator breaks the recursion to avoid a
	 * StackOverflowError, and this diagnostic surfaces the underlying cause.
	 */
	String ERR_CYCLIC_IMPORT = "errCyclicImport";

	/**
	 * / and % operators are only allowed with constants that are power of two.
	 */
	String ERR_DIV_MOD_NOT_CONST_POW_Of_TWO = "errDivModByNonPowerOfTwoConstant";

	String ERR_DUPLICATE_DECLARATIONS = "errDuplicateDeclarations";

	/**
	 * Two files define the same entity (same package and name). Only one of them is used, and
	 * which one depends on load order, so an edit to the other is ignored without notice (T157).
	 */
	String ERR_DUPLICATE_ENTITY = "errDuplicateEntity";

	/**
	 * A cast the types do not allow, such as {@code (u8) someBool}. Nothing could be built from it:
	 * generate crashed in the Verilog printer and skipped the task (T26), so it blocks.
	 */
	String ERR_CAST_IMPOSSIBLE = "errCastImpossible";

	/**
	 * break / continue used outside of any enclosing loop. They lower correctly
	 * inside a loop (the scheduler decomposes the loop into FSM states with the
	 * break/continue routed to the exit/header), but have no meaning otherwise.
	 */
	String ERR_LOOP_CTRL_OUTSIDE_LOOP = "errLoopControlOutsideLoop";

	String ERR_ENTRY_FUNCTION_BAD_TYPE = "errEntryFunctionBadType";

	String ERR_EXPECTED_CONST = "errExpectedConstant";

	String ERR_ILLEGAL_FENCE = "errIllegalFence";

	String ERR_LOCAL_NOT_INITIALIZED = "errLocalNotInitialized";

	/**
	 * When a local variable has the same name as a port, which causes the variable
	 * to shadow the port and prevents proper port writes.
	 */
	String ERR_LOCAL_SHADOWS_PORT = "errLocalShadowsPort";

	/**
	 * multiple reads from the same port are forbidden in expressions
	 */
	String ERR_MULTIPLE_READS = "errMultipleReads";

	String ERR_NO_SIDE_EFFECTS = "errNoSideEffects";

	String ERR_SIDE_EFFECTS_FUNCTION = "errNonVoidHasSideEffects";

	/**
	 * struct field type must be a primitive (integer/bool); nested structs not supported in v1.
	 * See .claude/L1_STRUCT_DESIGN.md §2/§6. Retired Tier 2.3 — nested structs are now
	 * supported; the remaining guard is {@link #ERR_STRUCT_CYCLE}.
	 */
	String ERR_STRUCT_FIELD_TYPE_NOT_PRIMITIVE = "errStructFieldTypeNotPrimitive";

	/**
	 * recursive struct definition — a struct contains itself directly or transitively,
	 * which would flatten infinitely. Tier 2.3.
	 */
	String ERR_STRUCT_CYCLE = "errStructCycle";

	/**
	 * struct types are not allowed as port types in v1. See .claude/L1_STRUCT_DESIGN.md §2/§6.
	 */
	String ERR_STRUCT_AS_PORT_TYPE = "errStructAsPortType";

	/**
	 * whole-struct values are not supported in v1; access individual fields with `.fieldName`.
	 * See .claude/L1_STRUCT_DESIGN.md §2/§6.
	 */
	String ERR_STRUCT_WHOLE_VALUE_USE = "errStructWholeValueUse";

	/**
	 * enum literal's explicit value exceeds the range of the underlying type (or is negative).
	 * See .claude/L2_ENUM_DESIGN.md §7.
	 */
	String ERR_ENUM_LITERAL_OUT_OF_RANGE = "errEnumLiteralOutOfRange";

	/**
	 * two enum literals (in the same enum) resolve to the same effective value.
	 * See .claude/L2_ENUM_DESIGN.md §7.
	 */
	String ERR_ENUM_LITERAL_DUPLICATE_VALUE = "errEnumLiteralDuplicateValue";

	/**
	 * an instantiation argument `new Foo({K: v})` names `K` that is not a const
	 * parameter of `Foo` (nor a reserved key like clock/clocks/reset). Today such
	 * a key is silently ignored, so a typo uses the default. L3 Generics iter #1.
	 * See .claude/L3_GENERICS_DESIGN.md.
	 */
	String ERR_GENERIC_ARG_UNKNOWN = "errGenericArgUnknown";

	/**
	 * an instantiation argument `new Foo({K: v})` names `K` that exists in `Foo`
	 * but is not `const`; only const parameters can be set at instantiation.
	 * L3 Generics iter #1.
	 */
	String ERR_GENERIC_ARG_NOT_CONST = "errGenericArgNotConst";

	/**
	 * a positional type-argument list `new Foo<a, b, c>()` supplies more
	 * arguments than `Foo` declares formal parameters. Extra args have no
	 * parameter to bind to. L3 Generics iter #2 (the angle-bracket sugar).
	 * See .claude/L3_GENERICS_DESIGN.md.
	 */
	String ERR_GENERIC_TYPEARG_ARITY = "errGenericTypeArgArity";

	/**
	 * a network connection {@code consumer.reads(producer.port)} wires two ports
	 * whose data widths differ. Today the mismatch is silent: the bytecode sim
	 * and the Verilog backend implicitly zero-extend or truncate, so a wrong
	 * width compiles to a latent miscompile. This surfaces most often with
	 * generic-width ports (L3 Tier 3.4) where an instance is specialized to the
	 * wrong width, but applies to fixed-width ports too. See
	 * .claude/L3_GENERICS_DESIGN.md §3.4.
	 */
	String ERR_PORT_WIDTH_MISMATCH = "errPortWidthMismatch";

	String ERR_TYPE_MISMATCH = "errTypeMismatch";

	String ERR_TYPE_ONE_BIT = "errTypeOneBit";

	String ERR_VAR_DECL = "errVariableDeclaration";

	String SHOULD_REPLACE_NAME = "shouldReplaceName";

	/**
	 * When deprecated interface keywords (sync, ready, ack) are used instead of new keywords (push, stream, confirm).
	 */
	String SHOULD_REPLACE_INTERFACE_KEYWORD = "shouldReplaceInterfaceKeyword";

	String SYNTAX_ERROR_ARRAY_BRACE = "syntaxErrorArrayBrace";

	String SYNTAX_ERROR_SINGLE_QUOTE = "syntaxErrorSingleQuote";

	/** A const/typedef/function written at file scope, after `package`. */
	String SYNTAX_ERROR_TOP_LEVEL_DECL = "syntaxErrorTopLevelDecl";

	/** `const` written AFTER the type instead of before it, e.g. `u16 const f()`. */
	String SYNTAX_ERROR_CONST_AFTER_TYPE = "syntaxErrorConstAfterType";

	/** M2: `task D[int W : 32]` -- parameters in brackets (other languages); C⏚ uses `<int W = 32>`. */
	String SYNTAX_ERROR_GENERIC_BRACKETS = "syntaxErrorGenericBrackets";

	/** M2: `do { ... } while (c);` -- C⏚ has no do-while. */
	String SYNTAX_ERROR_DO_WHILE = "syntaxErrorDoWhile";

	/** `switch (s) { case A: ... }` -- C⏚ has no switch; write an if / else-if chain (TRAPS T171). */
	String SYNTAX_ERROR_SWITCH = "syntaxErrorSwitch";

	/**
	 * A port declared with no type at all (`in x;`). The HDL emitter NPEs on the malformed
	 * port, so without an issue code this reported as a non-blocking `warning:` and the
	 * entity was silently skipped behind "Partial success" with exit 0 — every emitted file
	 * a std library module, none of them the user's design.
	 */
	String ERR_PORT_NO_TYPE = "errPortNoType";

	/**
	 * A task's {@code test:} vectors have a shape the test bench cannot be built from: values
	 * that are not a list, frames on a port that is not {@code framed}, frames mixed with single
	 * values, frames of lists, or {@code test} itself neither an object nor a boolean. Without
	 * a code this was a non-blocking {@code warning:}, then {@code generate} skipped the entity
	 * on a ClassCastException and {@code simulate} printed a raw Java stack (T143).
	 */
	String ERR_TEST_VECTORS = "errTestVectors";

	/**
	 * A network connection wires ports whose INTERFACE types differ — a bare port to a
	 * {@code sync}/{@code push} port, or either to a {@code stream}. The handshake signals the
	 * grouped side expects are simply not there, so the consumer reads a constant 0 for the whole
	 * run while the producer counts happily away, and any assert in that consumer becomes an
	 * assertion about zero. `ExplicitConnector` has always produced this message; without an issue
	 * code it was demoted to a non-blocking `warning:` and the simulator then ran the broken graph
	 * anyway and reported success (T24).
	 */
	String ERR_PORT_INTERFACE_MISMATCH = "errPortInterfaceMismatch";

	/** `[...]` written in a declaration that does not accept array dimensions. */
	String SYNTAX_ERROR_ARRAY_DIMS_HERE = "syntaxErrorArrayDimsHere";

	/** `<>` with nothing inside: `new T<>()`, `task T<>`, `int<>`. */
	String SYNTAX_ERROR_EMPTY_ANGLE_BRACKETS = "syntaxErrorEmptyAngleBrackets";

	/** `const NAME = value;` with no type. */
	String SYNTAX_ERROR_CONST_NEEDS_TYPE = "syntaxErrorConstNeedsType";

	/** A `for`/`while`/`if`/`else` body without braces. */
	String SYNTAX_ERROR_BODY_NEEDS_BRACES = "syntaxErrorBodyNeedsBraces";

	/** A reserved word used where a name was expected (`out push bool ack;`). */
	String SYNTAX_ERROR_RESERVED_WORD = "syntaxErrorReservedWord";

	/** An `import` after other declarations: imports go first (T101). */
	String SYNTAX_ERROR_IMPORT_ORDER = "syntaxErrorImportOrder";

	/** A C/Java integer suffix on a literal (`123L`): C⏚ literals take none. */
	String SYNTAX_ERROR_LITERAL_SUFFIX = "syntaxErrorLiteralSuffix";

	/**
	 * When an inline task accesses a state variable from a sibling instance.
	 * Cross-instance state variable access is not supported in bytecode simulation.
	 */
	String WARN_CROSS_INSTANCE_STATE_ACCESS = "warnCrossInstanceStateAccess";

	/**
	 * When {@code a * b} of two runtime operands is wide enough that it maps to more than one
	 * DSP tile (both operands &gt; 18 bits). The product is a single combinational multiplier,
	 * which may not meet timing at a high clock; pipeline it or use std.math.Multiply.
	 */
	String WARN_WIDE_MULTIPLY = "warnWideMultiply";

	/**
	 * A sum or product of two non-constant operands stored into a local too narrow for it: the value
	 * wraps before it is used (`u8 s = a + b + c + d; s >> 2` -- the classic broken average). Model
	 * friendliness item M1 (2026-09-30). A warning: `i = i + 1` (a constant operand) and an explicit
	 * cast are left alone, since there the wrap is the intent.
	 */
	String WARN_SUM_WRAPS = "warnSumWraps";

	/**
	 * More than one {@code properties} block in a single entity. The grammar allows the block
	 * anywhere among the members, so nothing stops a second one being written; but the feature
	 * is single-valued, so the LAST block silently overwrites every earlier one. Verified
	 * 2026-08-20: a first, correct {@code test:} fixture is discarded by a later block without
	 * any diagnostic — a test that appears present and is not run.
	 */
	String ERR_DUPLICATE_PROPERTIES = "errDuplicateProperties";

	/**
	 * An operator that is undefined for its operand types, e.g. {@code bool == 1}. This is not
	 * a tolerated narrowing: {@code computeType} returned NULL for the whole expression, so the
	 * backend has no type to emit and crashes on it. Observed 2026-08-20: the error carried no
	 * issue code, so it was downgraded to a non-blocking warning, generation continued, and the
	 * Verilog backend threw {@code NullPointerException: Type.isBool() because "type" is null}
	 * and SKIPPED the entity — reported to the user as "Partial success". Blocking here turns
	 * that into one clear message.
	 */
	String ERR_OPERATOR_UNDEFINED = "errOperatorUndefined";

	/**
	 * A {@code return} that would skip work in a {@code void} function — i.e. one that is not
	 * in tail position, such as the guard {@code if (!p.available()) { return; } ...}.
	 *
	 * <p>The language has never had this: {@code return} is valid at the end of a constant
	 * function, and {@code setup}/{@code loop} are void and return implicitly. The grammar
	 * accepted it anyway, and the two backends then disagreed about what it meant. The bytecode
	 * compiler emits a real return, so the simulator honours the guard. The HDL printers emit
	 * NOTHING for a value-less return, so {@code if (cond) begin end} is followed by every
	 * statement the guard was meant to skip, running unconditionally.
	 *
	 * <p>Found live in the shipped Intel-8088 (2026-08-23): {@code SplitAddress} guards on
	 * {@code !addr.available()}, and its generated Verilog asserts {@code rf1_addr_valid} on
	 * every input change regardless. Fast Sim was right and the netlist was wrong, which is the
	 * worst direction for this project — the fast backend is the one the gates run.
	 *
	 * <p>Only a return that actually skips something is reported. One in tail position changes
	 * no behaviour on either backend and is left alone.
	 *
	 * <p><b>Blocking since 2026-09-30 (TRAPS T35 + T131).</b> It was blocking briefly on 2026-08-23
	 * and withdrawn because {@code SplitAddress} depended on the drop: only the dropped-return
	 * spelling passed both back ends. The cause was T131 -- the simulator HELD a combinational
	 * task's unwritten bare output that the hardware sets to 0. With the simulator matching the
	 * hardware, {@code SplitAddress} without the guard passes on both and generates identical
	 * Verilog, so {@code generate} now refuses the construct.
	 */
	String ERR_RETURN_SKIPS_WORK = "errReturnSkipsWork";

	/**
	 * A misuse of a {@code framed} port (TRAPS T58): {@code .last} on anything but a framed input,
	 * {@code write(v, last)} on anything but a framed output, or a framed output written without its
	 * boundary.
	 */
	String ERR_FRAMED_MISUSE = "errFramedMisuse";

	/**
	 * A {@code confirm} (or deprecated {@code ack}) port: its {@code _ack} input was declared and
	 * never read, so a value the consumer was not ready for was lost. Refused (TRAPS T11, decided
	 * 2026-10-19).
	 */
	String ERR_CONFIRM_UNSUPPORTED = "errConfirmUnsupported";

	/**
	 * A state variable's initial value that does not fit its type, or an array initialiser with more
	 * values than the array has elements (TRAPS T173): the value was silently truncated, or the
	 * simulator crashed and the Verilog wrote past the array.
	 */
	String ERR_INIT_VALUE_OUT_OF_RANGE = "errInitValueOutOfRange";

	/**
	 * Arithmetic or a shift with a {@code bool} operand, e.g. {@code 1 - rowp} (TRAPS T181). Only
	 * {@link #ERR_OPERATOR_UNDEFINED}, a warning, before: the build went on and IR generation
	 * crashed on the untyped expression (NullPointerException, entity skipped). Its own code so it
	 * can block while a comparison such as {@code bool == 1} stays supported.
	 */
	String ERR_BOOL_ARITHMETIC = "errBoolArithmetic";

	/**
	 * A {@code combinational} task assigns one of its state variables (TRAPS T184): the generated
	 * process assigns it on some paths only, so it holds its value in a latch. Was an error without a
	 * code, hence only a warning. A state variable the task only reads (a constant table) is allowed.
	 */
	String ERR_COMBINATIONAL_STATE = "errCombinationalState";

}
