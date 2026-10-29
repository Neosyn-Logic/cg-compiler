/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_FRAMED_MISUSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.TreeSet;

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
 * TRAPS T58 step 2a: framed ports in the front end. `in framed u8 din;` is a stream that carries a frame
 * boundary, written with {@code dout.write(v, last)} and read as {@code din.last}. Every misuse is
 * an error, and -- until the back ends carry the boundary -- so is any framed port at all, so a
 * design is refused rather than generated without it.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class FramedStreamValidationTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	/** Issue codes of the ERROR diagnostics for {@code src}; the source must parse. */
	private Set<String> errorCodes(String src) throws Exception {
		Module m = parseHelper.parse(src);
		assertNotNull(m);
		assertTrue("should parse: " + m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		Set<String> codes = new TreeSet<>();
		for (Diagnostic d : diagnostics.getDiagnostic().getChildren()) {
			if (d.getSeverity() == Diagnostic.ERROR && d instanceof AbstractValidationDiagnostic) {
				String code = ((AbstractValidationDiagnostic) d).getIssueCode();
				if (code != null) {
					codes.add(code);
				}
			}
		}
		return codes;
	}

	private static String task(String ports, String body) {
		return "package test;\ntask T {\n" + ports + "\n    void loop() {\n" + body + "\n    }\n}\n";
	}

	/** All three back ends carry `last` (slices 2c-2e): correct framed use draws no error at all. */
	@Test
	public void correctUse_isAccepted() throws Exception {
		Set<String> codes = errorCodes(task("    in framed u8 a;\n    out framed u8 y;",
				"        u8 v = a.read();\n        y.write(v, a.last);"));
		assertTrue("correct framed use draws no error: " + codes, codes.isEmpty());
	}

	@Test
	public void blockForm_isAccepted() throws Exception {
		Set<String> codes = errorCodes(task("    framed { in u8 a; out u8 y; }",
				"        u8 v = a.read();\n        y.write(v, a.last);"));
		assertFalse(codes.toString(), codes.contains(ERR_FRAMED_MISUSE));
	}

	/** `framed` alone makes a handshaked stream: `available` (legal only on sync inputs) is accepted. */
	@Test
	public void aFramedPortIsAStream() throws Exception {
		Set<String> codes = errorCodes(task("    in framed u8 a;\n    out framed u8 y;",
				"        if (a.available()) { u8 v = a.read(); y.write(v, a.last); }"));
		assertTrue("no error: " + codes, codes.isEmpty());
	}

	@Test
	public void lastOnAnUnframedInput_isAMisuse() throws Exception {
		assertTrue(errorCodes(task("    in stream u8 a;\n    out stream u8 y;",
				"        u8 v = a.read();\n        if (a.last) { y.write(v); }")).contains(ERR_FRAMED_MISUSE));
	}

	@Test
	public void writeWithLastOnAnUnframedOutput_isAMisuse() throws Exception {
		assertTrue(errorCodes(task("    in stream u8 a;\n    out stream u8 y;",
				"        y.write(a.read(), true);")).contains(ERR_FRAMED_MISUSE));
	}

	@Test
	public void framedOutputWrittenWithoutItsBoundary_isAMisuse() throws Exception {
		assertTrue(errorCodes(task("    in stream u8 a;\n    out framed u8 y;",
				"        y.write(a.read());")).contains(ERR_FRAMED_MISUSE));
	}

	/** A struct port fans out per field; its frame boundary is not lowered yet, so it is refused. */
	@Test
	public void framedStructPort_isRefused() throws Exception {
		Set<String> codes = errorCodes("package test;\ntask T {\n    struct S { u8 lo; u8 hi; }\n"
				+ "    in framed S a;\n    out framed u8 y;\n"
				+ "    void loop() {\n        S s = a.read();\n        y.write(s.lo, a.last);\n    }\n}\n");
		assertTrue(codes.toString(), codes.contains(ERR_FRAMED_MISUSE));
	}

	/** Control for the above: the same struct port, unframed, is no framed misuse. */
	@Test
	public void unframedStructPort_isNoFramedMisuse() throws Exception {
		Set<String> codes = errorCodes("package test;\ntask T {\n    struct S { u8 lo; u8 hi; }\n"
				+ "    in stream S a;\n    out stream u8 y;\n"
				+ "    void loop() {\n        S s = a.read();\n        y.write(s.lo);\n    }\n}\n");
		assertFalse(codes.toString(), codes.contains(ERR_FRAMED_MISUSE));
	}

	/** `frame` stays an ordinary name (156 uses in the IP cores): only `framed` is a keyword. */
	@Test
	public void frameIsStillAName() throws Exception {
		Set<String> codes = errorCodes(task("    in stream u8 a;\n    out stream u8 y;\n    u8 frame;",
				"        frame = a.read();\n        y.write(frame);"));
		assertFalse("an unframed design gets no framed errors: " + codes, codes.contains(ERR_FRAMED_MISUSE));
	}
}
