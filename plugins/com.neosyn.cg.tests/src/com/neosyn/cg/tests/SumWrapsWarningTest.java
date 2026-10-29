/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.WARN_SUM_WRAPS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.xtext.junit4.validation.AssertableDiagnostics;
import org.eclipse.xtext.junit4.validation.ValidatorTester;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.validation.CgValidator;

/**
 * M1 (model friendliness, 2026-09-30): a sum or product of two non-constant operands stored into a
 * local too narrow for it wraps before it is used. `u8 s = a + b + c + d; y.write(s >> 2);` -- the
 * broken average that gave a user's pool2x2 58/77/20 instead of 80 -- compiled without a word.
 * It now warns; a counter increment and an explicit cast, where the wrap is intended, do not.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class SumWrapsWarningTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	private boolean warns(String body) throws Exception {
		Module m = parseHelper.parse("package test;\ntask T {\n    in stream u8 a, b, c, d;\n"
				+ "    out stream u8 y;\n    u8 i;\n    void loop() {\n" + body + "\n    }\n}\n");
		assertNotNull(m);
		assertTrue("should parse: " + m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		for (Diagnostic d : diagnostics.getDiagnostic().getChildren()) {
			if (d instanceof AbstractValidationDiagnostic
					&& WARN_SUM_WRAPS.equals(((AbstractValidationDiagnostic) d).getIssueCode())) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void theBrokenAverageWarns() throws Exception {
		assertTrue(warns("        u8 s = a.read() + b.read() + c.read() + d.read();\n        y.write(s >> 2);"));
	}

	@Test
	public void aNarrowProductWarns() throws Exception {
		assertTrue(warns("        u8 p = a.read() * b.read();\n        y.write(p);"));
	}

	@Test
	public void aWideEnoughVariableIsSilent() throws Exception {
		assertFalse(warns("        u10 s = a.read() + b.read() + c.read() + d.read();\n        y.write((u8) (s >> 2));"));
	}

	@Test
	public void aCounterIncrementIsSilent() throws Exception {
		assertFalse(warns("        i = i + 1;\n        y.write(a.read());"));
	}

	@Test
	public void anExplicitCastIsSilent() throws Exception {
		assertFalse(warns("        u8 s = (u8) (a.read() + b.read());\n        y.write(s);"));
	}
}
