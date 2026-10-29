/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_DUPLICATE_PROPERTIES;
import static org.junit.Assert.assertEquals;
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
 * The `properties` block may appear ANYWHERE among an entity's members, and at most once.
 *
 * <p>It used to be pinned to the first slot, which made the natural spelling — ports first,
 * then the test block — a parse error reported as {@code mismatched input 'properties'
 * expecting '}'}. That was the most common failure observed when language models write C⏚
 * (6 occurrences in one probe, never once recovered from), and the message never mentions
 * order, so the rule cannot be learned from it. Every other member was already position-free.
 *
 * <p>The cost of that freedom is that nothing syntactic stops a SECOND block, and since the
 * feature is single-valued the LAST one silently wins — verified before this validation
 * existed: a first, correct fixture was discarded with no diagnostic. A test that looks
 * present and never runs is the worst failure mode this compiler has, hence an error.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class PropertiesPositionValidationTest {

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

	private List<Diagnostic> errorsOf(Module m) {
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		return diagnostics.getDiagnostic().getChildren().stream()
				.filter(d -> d.getSeverity() == Diagnostic.ERROR).collect(Collectors.toList());
	}

	private boolean hasDuplicateError(Module m) {
		return errorsOf(m).stream().anyMatch(d -> d instanceof AbstractValidationDiagnostic
				&& ERR_DUPLICATE_PROPERTIES.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
	}

	private static String task(String body) {
		return "package test;\ntask T {\n" + body + "}\n";
	}

	private static final String PROPS = "    properties { test: { a: [1], y: [1] } }\n";
	private static final String PORTS = "    sync { in u8 a; out u8 y; }\n";
	private static final String LOOP = "    void loop() { y.write(a.read()); }\n";

	// ---------------------------------------------------------------- position

	@Test
	public void propertiesFirstStillParses() throws Exception {
		assertFalse(hasDuplicateError(parse(task(PROPS + PORTS + LOOP))));
	}

	@Test
	public void propertiesAfterPortsParses() throws Exception {
		// THE regression this change exists for: previously `mismatched input 'properties'`.
		assertFalse(hasDuplicateError(parse(task(PORTS + PROPS + LOOP))));
	}

	@Test
	public void propertiesLastParses() throws Exception {
		assertFalse(hasDuplicateError(parse(task(PORTS + LOOP + PROPS))));
	}

	@Test
	public void propertiesAnywhereInANetworkParses() throws Exception {
		parse("package test;\nnetwork N {\n"
				+ "    in push u8 a; out push u8 y;\n"
				+ "    properties { test: { a: [1], y: [1] } }\n"
				+ "}\n");
	}

	@Test
	public void propertiesAnywhereInABundleParses() throws Exception {
		parse("package test;\nbundle B {\n"
				+ "    const u8 K = 3;\n"
				+ "    properties { }\n"
				+ "}\n");
	}

	// --------------------------------------------------------------- duplicate

	@Test
	public void twoPropertiesBlocksIsAnError() throws Exception {
		assertTrue("a second properties block silently overrides the first, so it must be an error",
				hasDuplicateError(parse(task(PROPS + PORTS + PROPS + LOOP))));
	}

	@Test
	public void threePropertiesBlocksIsStillOneError() throws Exception {
		Module m = parse(task(PROPS + PROPS + PORTS + PROPS + LOOP));
		assertTrue(hasDuplicateError(m));
		assertEquals("one diagnostic per entity, not one per extra block", 1,
				errorsOf(m).stream().filter(d -> d instanceof AbstractValidationDiagnostic
						&& ERR_DUPLICATE_PROPERTIES
								.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
						.count());
	}

	@Test
	public void nestedAnonymousTaskHasItsOwnBlock() throws Exception {
		// The control for the counting logic: a network with ONE block containing an
		// anonymous task with ONE block is legal. Counting keywords without attributing
		// them to their own entity would call this a duplicate.
		Module m = parse("package test;\nnetwork N {\n"
				+ "    properties { test: { a: [1], y: [1] } }\n"
				+ "    in push u8 a; out push u8 y;\n"
				+ "    inner = new task {\n"
				+ "        properties { }\n"
				+ "        in stream u8 i; out stream u8 o;\n"
				+ "        void loop() { o.write(i.read()); }\n"
				+ "    };\n"
				+ "    inner.reads(a);\n"
				+ "}\n");
		assertFalse("a nested task's own block is not a duplicate", hasDuplicateError(m));
	}
}
