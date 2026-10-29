/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_CONFIRM_UNSUPPORTED;
import static com.neosyn.cg.validation.IssueCodes.SHOULD_REPLACE_INTERFACE_KEYWORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
 * TRAPS T10: the deprecated interface spellings are reported by the COMPILER (a non-blocking warning),
 * so the CLI and the agent kit see them -- only the VS Code extension used to, and `sync { }` was read
 * as the grouped form of a bare port.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class DeprecatedInterfaceWarningTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	/** "SEVERITY code" for every coded diagnostic of {@code src}; the source must parse. */
	private List<String> diagnostics(String src) throws Exception {
		Module m = parseHelper.parse(src);
		assertNotNull(m);
		assertTrue("should parse: " + m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		List<String> out = new ArrayList<>();
		for (Diagnostic d : diagnostics.getDiagnostic().getChildren()) {
			if (d instanceof AbstractValidationDiagnostic) {
				String code = ((AbstractValidationDiagnostic) d).getIssueCode();
				if (code != null) {
					out.add((d.getSeverity() == Diagnostic.ERROR ? "ERROR " : "WARNING ") + code);
				}
			}
		}
		return out;
	}

	private static String task(String ports, String body) {
		return "package test;\ntask T {\n" + ports + "\n    void loop() {\n" + body + "\n    }\n}\n";
	}

	private static long count(List<String> diags, String entry) {
		return diags.stream().filter(entry::equals).count();
	}

	private static final String WARN = "WARNING " + SHOULD_REPLACE_INTERFACE_KEYWORD;

	@Test
	public void everyDeprecatedSpelling_warns() throws Exception {
		String[] spellings = {
				"    in sync u8 x; out u8 y;",            // inline sync
				"    in u8 x(sync); out u8 y;",            // parentheses
				"    in sync ready u8 x; out u8 y;",      // inline sync ready
				"    sync { in u8 x; } out u8 y;",        // block
				"    sync ready { in u8 x; } out u8 y;",  // ready block
		};
		for (String ports : spellings) {
			List<String> diags = diagnostics(task(ports, "        y.write(x.read());"));
			assertEquals(ports + " -> " + diags, 1, count(diags, WARN));
			assertTrue(ports + " must not block: " + diags, diags.stream().noneMatch(d -> d.startsWith("ERROR")));
		}
	}

	@Test
	public void modernSpellings_doNotWarn() throws Exception {
		List<String> diags = diagnostics(task("    in push u8 a; in stream u8 b; stream { in u8 c; } push { out u8 y; }",
				"        y.write(a.read() + b.read() + c.read());"));
		assertEquals(diags.toString(), 0, count(diags, WARN));
	}

	@Test
	public void syncCombinational_hasNoModernSpelling_soNoWarning() throws Exception {
		List<String> diags = diagnostics(task("    in u8 x; out u8 y (sync combinational);", "        y.write(x.read());"));
		assertEquals(diags.toString(), 0, count(diags, WARN));
	}

	@Test
	public void syncAck_isRefused_notMerelyDeprecated() throws Exception {
		List<String> diags = diagnostics(task("    in stream u8 x; out sync ack u8 y;", "        y.write(x.read());"));
		assertEquals(diags.toString(), 1, count(diags, "ERROR " + ERR_CONFIRM_UNSUPPORTED));
		assertEquals(diags.toString(), 0, count(diags, WARN));
	}
}
