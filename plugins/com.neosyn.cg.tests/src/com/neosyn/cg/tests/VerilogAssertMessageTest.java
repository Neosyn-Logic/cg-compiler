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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * An asserted expression is stringified into a {@code $display("Assertion failed: ...")}
 * message, so whatever the names print as ends up inside a string literal.
 *
 * <p>Names that are Verilog keywords used to print as escaped identifiers ({@code \reg } for
 * {@code reg}), and the leading backslash was then read as a string escape: {@code \E} is an
 * unknown escape that Verilator rejects outright (iverilog is lenient), so the whole module
 * failed to compile. Two things fix that and this test holds both: a keyword is now RENAMED
 * rather than escaped (so no backslash reaches the message in the first place), and the message
 * copy escapes any backslash that does (S187, TRAPS T116).
 *
 * @see VerilogCastSimplificationTest
 * @see GeneratedNoiseTest
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogAssertMessageTest extends AbstractVerilogCodegenTest {

	@Test
	public void assertionMessageEscapesReservedIdentifierBackslashes() throws Exception {
		String v = codeOf("com/neosyn/test/codegen/ReservedWordAssert.cg",
				"com.neosyn.test.codegen.ReservedWordAssert");

		// Sanity: the fixture genuinely emits an assertion over the two names, so the checks
		// below are not vacuously true.
		assertTrue("fixture should emit an assertion", v.contains("Assertion failed:"));
		assertTrue("the keyword `reg` is renamed, not escaped:\n" + v, v.contains("reg_v"));
		assertTrue("`EXPECT` is not a Verilog keyword and keeps its name:\n" + v,
				v.contains("EXPECT") && !v.contains("\\EXPECT"));
		assertFalse("an escaped identifier is left:\n" + v, v.contains("\\reg"));

		// Every $display("Assertion failed: ...") string must have its backslashes
		// escaped — no bare `\E`/`\r` sequence that Verilator reads as an escape.
		Matcher m = Pattern.compile("\\$display\\(\"Assertion failed: ([^\"]*)\"").matcher(v);
		int checked = 0;
		while (m.find()) {
			checked++;
			String msg = m.group(1);
			// A single backslash followed by a non-backslash is a raw escape leak
			// (the fix doubles every backslash, so only "\\" pairs may appear).
			assertFalse("raw escaped-identifier backslash leaked into the assertion "
					+ "string (breaks Verilator): " + msg,
					Pattern.compile("(?<!\\\\)\\\\(?!\\\\)").matcher(msg).find());
		}
		assertTrue("expected at least one Assertion-failed message to check", checked > 0);
	}

	/** TRAPS T175: the message is the source text with file:line, and the two values are printed. */
	@Test
	public void assertionMessageIsTheSourceTextWithValues() throws Exception {
		String v = codeOf("com/neosyn/test/codegen/AssertIndexMessage.cg",
				"com.neosyn.test.codegen.AssertIndexMessage");
		// The Verilog path folds `v` away, so the left side is the expression it stood for; what must hold is
		// the location, the array element as written, a value slot per side, and no IR temporary for E[i].
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("\\$display\\(\"Assertion failed: ([^\"]*)\"").matcher(v);
		assertTrue("an assertion message:\n" + v, m.find());
		String msg = m.group(1);
		assertTrue("location: " + msg, msg.startsWith("AssertIndexMessage.cg:10 in AssertIndexMessage: "));
		assertTrue("the element as written: " + msg, msg.contains("== E[i] -- "));
		assertTrue("a value per side: " + msg, msg.endsWith(", E[i] = %0d"));
		assertFalse("no IR temporary: " + msg, msg.contains("E_0") || msg.contains("i_1") || msg.contains("zext"));
	}
}
