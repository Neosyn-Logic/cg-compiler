/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A scheduler function ({@code isSchedulable_*}) is called every cycle to decide whether its
 * action fires, and developing an {@code if} copies the branch's statements into it. Prints were
 * already removed from it; asserts were not, so {@code if (x.available()) { assert(x.read() == 7); }}
 * checked {@code x} on every cycle, including the ones before anything was written. A correct
 * design failed on the first cycle in Verilog and VHDL while the bytecode simulator passed.
 *
 * <p>Both directions are checked: no assert in any scheduler function, AND every assert still in
 * the action bodies -- a fix that deleted the checks would otherwise pass.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogAssertInSchedulerTest extends AbstractVerilogCodegenTest {

	private static final String ASSERT = "Assertion failed:";

	@Test
	public void assertsRunInTheActionNotInItsScheduler() throws Exception {
		String v = codeOf("com/neosyn/test/codegen/AssertInGuardedBranch.cg",
				"com.neosyn.test.codegen.AssertInGuardedBranch",
				"com.neosyn.test.codegen.AssertInGuardedBranch_chk");

		// A scheduler with statements is printed as a function; one that is a bare condition is
		// inlined into the `if`. So after the fix there may be no function left to look at, and
		// the count below -- not a function count -- is what shows the ifs were developed.
		Matcher fn = Pattern.compile("(?s)function\\s+isSchedulable_\\w+.*?endfunction").matcher(v);
		StringBuilder outside = new StringBuilder();
		int last = 0;
		while (fn.find()) {
			assertTrue("an assert is evaluated inside a scheduler function, i.e. every cycle "
					+ "whether or not its guard holds:\n" + fn.group(), !fn.group().contains(ASSERT));
			outside.append(v, last, fn.start());
			last = fn.end();
		}
		outside.append(v.substring(last));

		// The x-only action carries 1 assert, the y-only 2, the both-at-once action 3. Six also
		// proves the two ifs were developed into three actions (undeveloped, there would be 3).
		assertEquals("every assert must still be in an action body", 6, count(outside.toString(), ASSERT));
	}

	private static int count(String haystack, String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
			n++;
		}
		return n;
	}
}
