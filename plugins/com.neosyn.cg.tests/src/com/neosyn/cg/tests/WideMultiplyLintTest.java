/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.WARN_WIDE_MULTIPLY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

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
 * Guards the wide-multiply hint (WARN_WIDE_MULTIPLY): {@code a * b} of two runtime operands each
 * wider than a DSP tile's 18-bit port earns an advisory. A constant operand (folds to shift-adds)
 * and narrow operands (fit one DSP) must NOT warn. The warning is advisory only — never an error.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class WideMultiplyLintTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	private boolean warnsWideMultiply(String body) throws Exception {
		Module result = parseHelper.parse(body);
		assertNotNull("parse result", result);
		assertTrue("no parse errors", result.eResource().getErrors().isEmpty());
		AssertableDiagnostics diagnostics = validatorTester.validate(result);
		// The hint is advisory: there must never be an ERROR from it.
		List<Diagnostic> errors = diagnostics.getDiagnostic().getChildren().stream()
				.filter(d -> d.getSeverity() == Diagnostic.ERROR)
				.filter(d -> d instanceof AbstractValidationDiagnostic
						&& WARN_WIDE_MULTIPLY.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
				.collect(Collectors.toList());
		assertTrue("wide-multiply hint must never be an error", errors.isEmpty());
		return diagnostics.getDiagnostic().getChildren().stream()
				.anyMatch(d -> d instanceof AbstractValidationDiagnostic
						&& WARN_WIDE_MULTIPLY.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
	}

	@Test
	public void wideVariableMultiply_warns() throws Exception {
		assertTrue("32x32 runtime multiply should hint", warnsWideMultiply(
				"package t;\n"
				+ "task M {\n"
				+ "    in int<32> a; in int<32> b; out int<64> p;\n"
				+ "    void loop() { p.write(a.read() * b.read()); }\n"
				+ "}\n"));
	}

	@Test
	public void narrowMultiply_doesNotWarn() throws Exception {
		assertFalse("16x16 fits one DSP — no hint", warnsWideMultiply(
				"package t;\n"
				+ "task M {\n"
				+ "    in int<16> a; in int<16> b; out int<32> p;\n"
				+ "    void loop() { p.write(a.read() * b.read()); }\n"
				+ "}\n"));
	}

	@Test
	public void constantOperandMultiply_doesNotWarn() throws Exception {
		assertFalse("a constant operand folds to shift-adds — no hint", warnsWideMultiply(
				"package t;\n"
				+ "task M {\n"
				+ "    in int<32> a; out int<64> p;\n"
				+ "    void loop() { p.write(a.read() * 1000000); }\n"
				+ "}\n"));
	}
}
