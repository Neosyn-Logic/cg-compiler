/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies that a {@code for} loop with a compile-time-constant trip count is
 * unrolled into straight-line IR (so it lowers to synthesizable Verilog with no
 * {@code while}), while a data-dependent loop still falls back to a {@code while}.
 *
 * <p>Value-correctness of the unrolled output is gated elsewhere — the
 * {@code ComputationForLoop} / {@code BarrelShifter} fixtures in
 * {@code BytecodePassTests} / {@code IverilogPassTests} carry expected-result
 * vectors and exercise the same unroll path.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class ConstantLoopUnrollTest extends AbstractVerilogCodegenTest {

	/** A constant-bound loop unrolls — no `while` survives. */
	@Test
	public void testConstantLoopIsUnrolled() throws Exception {
		String v = codeOf("golden/GoldenLoopUnroll.cg", "com.neosyn.test.golden.ConstUnroll");
		assertFalse("constant-bound for loop must not emit a `while`", v.contains("while"));
		// The eight iterations collapse to straight-line code that the backend
		// constant-folds; the final write is still present.
		assertTrue("the loop result should still be written", v.contains("dout <="));
	}

	/**
	 * A data-dependent loop is not unrollable: the loop condition must survive as
	 * a runtime comparison against the input-derived bound (a scheduler-lowered
	 * FSM back-edge). Were it wrongly unrolled, the index/bound comparison would
	 * be constant-folded away.
	 */
	@Test
	public void testDataDependentLoopIsNotUnrolled() throws Exception {
		String v = codeOf("golden/GoldenLoopUnroll.cg", "com.neosyn.test.golden.DataDepLoop")
				.replaceAll("\\s+", " ");
		assertTrue("data-dependent loop must keep its runtime `k < bound` test",
				v.contains("_a_k < FSM_DataDepLoop_a_bound"));
	}
}
