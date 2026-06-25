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

import java.util.Arrays;

import org.junit.Test;

import com.neosyn.cg.ide.server.ValidationHelper.ValidationResult;

/**
 * Guards diagnostic de-duplication in {@link ValidationResult}.
 *
 * <p>A single mistake was reported many times: the same syntax error surfaces
 * both via {@code resource.getErrors()} and via the Xtext {@code
 * IResourceValidator} (which re-reports it as an Issue), and the lexer emits one
 * identical "no viable alternative" per bad character. {@code ValidationResult}
 * now collapses exact-duplicate messages (order-preserving) so the CLI / simulate
 * / generate listing shows each distinct problem once. Regression for the
 * "one typo → 31-error cascade" report.
 */
public class ValidationResultDedupTest {

	@Test
	public void exactDuplicateErrorsAreCollapsed() {
		ValidationResult r = new ValidationResult();
		// Same error from two sources (resource.getErrors + validator issue),
		// plus the lexer emitting it three times for three bad characters.
		r.addError("Foo.cg:6: no viable alternative at character '@'");
		r.addError("Foo.cg:6: no viable alternative at character '@'");
		r.addError("Foo.cg:6: no viable alternative at character '@'");
		r.addError("Foo.cg:7: missing ';' at 'outp'");
		r.addError("Foo.cg:7: missing ';' at 'outp'");

		assertEquals(Arrays.asList(
				"Foo.cg:6: no viable alternative at character '@'",
				"Foo.cg:7: missing ';' at 'outp'"), r.getErrors());
	}

	@Test
	public void distinctErrorsArePreservedInOrder() {
		ValidationResult r = new ValidationResult();
		r.addError("a");
		r.addError("b");
		r.addError("c");
		r.addError("b"); // duplicate of an earlier one

		assertEquals(Arrays.asList("a", "b", "c"), r.getErrors());
	}

	@Test
	public void duplicateWarningsAreCollapsed() {
		ValidationResult r = new ValidationResult();
		r.addWarning("deprecated keyword 'sync'");
		r.addWarning("deprecated keyword 'sync'");
		assertEquals(Arrays.asList("deprecated keyword 'sync'"), r.getWarnings());
	}

	@Test
	public void nullMessagesAreIgnored() {
		ValidationResult r = new ValidationResult();
		r.addError(null);
		r.addWarning(null);
		assertEquals(0, r.getErrors().size());
		assertEquals(0, r.getWarnings().size());
	}
}
