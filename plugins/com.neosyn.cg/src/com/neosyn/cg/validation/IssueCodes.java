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

 * 
 */
public interface IssueCodes {

	String ERR_ARRAY_MULTI_NON_POWER_OF_TWO = "errArrayMultiDimNonPowerOfTwo";

	/**
	 * when available is used outside of if/for/while
	 */
	String ERR_AVAILABLE = "errAvailable";

	String ERR_CANNOT_ASSIGN_CONST = "errCannotAssignConstant";

	/**
	 * Two or more bundles import each other (directly or transitively), forming a
	 * dependency cycle. Such cycles cause unbounded recursion during
	 * instantiation; the instantiator breaks the recursion to avoid a
	 * StackOverflowError, and this diagnostic surfaces the underlying cause.
	 */
	String ERR_CYCLIC_IMPORT = "errCyclicImport";

	String ERR_CMP_ALWAYS_FALSE = "errCmpAlwaysFalse";

	String ERR_CMP_ALWAYS_TRUE = "errCmpAlwaysTrue";

	/**
	 * / and % operators are only allowed with constants that are power of two.
	 */
	String ERR_DIV_MOD_NOT_CONST_POW_Of_TWO = "errDivModByNonPowerOfTwoConstant";

	String ERR_DUPLICATE_DECLARATIONS = "errDuplicateDeclarations";

	/**
	 * break / continue used outside of any enclosing loop. They lower correctly
	 * inside a loop (the scheduler decomposes the loop into FSM states with the
	 * break/continue routed to the exit/header), but have no meaning otherwise.
	 */
	String ERR_LOOP_CTRL_OUTSIDE_LOOP = "errLoopControlOutsideLoop";

	String ERR_ENTRY_FUNCTION_BAD_TYPE = "errEntryFunctionBadType";

	String ERR_EXPECTED_CONST = "errExpectedConstant";

	String ERR_FUNCTION_CALL = "errFunctionCall";

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

	String ERR_UNRESOLVED_FUNCTION = "errUnresolvedFunction";

	String ERR_VAR_DECL = "errVariableDeclaration";

	String SHOULD_REPLACE_NAME = "shouldReplaceName";

	/**
	 * When deprecated interface keywords (sync, ready, ack) are used instead of new keywords (push, stream, confirm).
	 */
	String SHOULD_REPLACE_INTERFACE_KEYWORD = "shouldReplaceInterfaceKeyword";

	String SYNTAX_ERROR_ARRAY_BRACE = "syntaxErrorArrayBrace";

	String SYNTAX_ERROR_SINGLE_QUOTE = "syntaxErrorSingleQuote";

	/**
	 * When an inline task accesses a state variable from a sibling instance.
	 * Cross-instance state variable access is not supported in bytecode simulation.
	 */
	String WARN_CROSS_INSTANCE_STATE_ACCESS = "warnCrossInstanceStateAccess";

}
