/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_RETURN_SKIPS_WORK;
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
 * A {@code return} in a void action is rejected when — and only when — it would skip work.
 *
 * <p>{@code return} belongs at the end of a constant function; {@code setup} and {@code loop} are
 * void and return implicitly. The grammar accepted an early one anyway, and the two backends then
 * disagreed about what it meant: the bytecode compiler emits a real return, so the simulator
 * honours the guard, while the HDL printers emit NOTHING for a value-less return, leaving
 * {@code if (cond) begin end} followed by every statement the guard was meant to skip.
 *
 * <p>That was not hypothetical. The shipped Intel-8088 {@code SplitAddress} guarded on
 * {@code !addr.available()} and its generated Verilog asserted {@code rf1_addr_valid} on every
 * input change regardless — Fast Sim right, netlist wrong, which is the worst direction here
 * because the fast backend is the one the gates run.
 *
 * <p>The second half matters as much as the first: a return in TAIL position skips nothing and
 * behaves identically on both backends, so it must NOT be reported. Rejecting those too would
 * have made this check a nuisance and invited a blanket suppression.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class ReturnSkipsWorkValidationTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	private Module parse(String src) throws Exception {
		Module result = parseHelper.parse(src);
		assertNotNull("parse result should not be null", result);
		assertTrue("should parse without syntax errors: " + result.eResource().getErrors(),
				result.eResource().getErrors().isEmpty());
		return result;
	}

	private boolean reportsSkippedWork(String body) throws Exception {
		Module m = parse("package test;\ntask T {\n    sync { in u8 a; out u8 y; }\n" + body + "}\n");
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		List<Diagnostic> errors = diagnostics.getDiagnostic().getChildren().stream()
				.filter(d -> d.getSeverity() == Diagnostic.ERROR).collect(Collectors.toList());
		return errors.stream().anyMatch(d -> d instanceof AbstractValidationDiagnostic
				&& ERR_RETURN_SKIPS_WORK.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
	}

	/** The Intel-8088 shape: a guard that skips the whole body. */
	@Test
	public void earlyGuardReturnIsRejected() throws Exception {
		assertTrue("a return that skips the rest of the action must be reported",
				reportsSkippedWork("    void loop() {\n"
						+ "        if (!a.available()) { return; }\n"
						+ "        y.write(a.read());\n"
						+ "    }\n"));
	}

	/** Statements after the enclosing `if` are skipped just the same. */
	@Test
	public void returnInsideNestedIfIsRejected() throws Exception {
		assertTrue("a return nested two levels deep still skips what follows the outer if",
				reportsSkippedWork("    void loop() {\n"
						+ "        u8 v = a.read();\n"
						+ "        if (v > 0) { if (v > 4) { return; } }\n"
						+ "        y.write(v);\n"
						+ "    }\n"));
	}

	/** Last statement of the body: nothing follows, so nothing is skipped. */
	@Test
	public void tailReturnIsAccepted() throws Exception {
		assertFalse("a return in tail position skips nothing and must not be reported",
				reportsSkippedWork("    void loop() {\n"
						+ "        u8 v = a.read();\n"
						+ "        y.write(v);\n"
						+ "        return;\n"
						+ "    }\n"));
	}

	/**
	 * A return last inside a branch that is itself last. Nothing follows the {@code if}, so the
	 * else-path simply runs instead — no statement is skipped on either backend.
	 */
	@Test
	public void returnLastInsideTrailingIfIsAccepted() throws Exception {
		assertFalse("a return closing the final if skips nothing and must not be reported",
				reportsSkippedWork("    void loop() {\n"
						+ "        u8 v = a.read();\n"
						+ "        if (v == 0) { return; }\n"
						+ "    }\n"));
	}

	/** A value-returning return belongs to a typed function and is unrelated to this defect. */
	@Test
	public void valueReturnInConstFunctionIsAccepted() throws Exception {
		Module m = parse("package test;\ntask T {\n"
				+ "    sync { in u8 a; out u8 y; }\n"
				+ "    const u8 twice(u8 v) { return (u8) (v * 2); }\n"
				+ "    void loop() { y.write(twice(a.read())); }\n"
				+ "}\n");
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		assertFalse("a const function's return must not be reported",
				diagnostics.getDiagnostic().getChildren().stream()
						.filter(d -> d.getSeverity() == Diagnostic.ERROR)
						.anyMatch(d -> d instanceof AbstractValidationDiagnostic && ERR_RETURN_SKIPS_WORK
								.equals(((AbstractValidationDiagnostic) d).getIssueCode())));
	}
}
