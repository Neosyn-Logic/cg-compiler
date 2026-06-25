/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_ENUM_LITERAL_DUPLICATE_VALUE;
import static com.neosyn.cg.validation.IssueCodes.ERR_ENUM_LITERAL_OUT_OF_RANGE;
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
 * L2 iter #2 enum sentinel validation tests.
 *
 * Two sentinels declared in .claude/L2_ENUM_DESIGN.md §7:
 *   - ERR_ENUM_LITERAL_OUT_OF_RANGE (value exceeds underlying width or negative)
 *   - ERR_ENUM_LITERAL_DUPLICATE_VALUE (two literals same effective value)
 *
 * iter #1 had no sentinels because every deferred surface was a parser
 * syntax error. iter #2 introduces explicit values + explicit underlying
 * width, which can now produce semantically invalid (but parseable) enums.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class EnumSentinelValidationTest {

    @Inject
    private ParseHelper<Module> parseHelper;

    @Inject
    private Injector injector;

    private ValidatorTester<CgValidator> validatorTester;

    @Before
    public void setUp() {
        CgValidator validator = injector.getInstance(CgValidator.class);
        validatorTester = new ValidatorTester<>(validator, injector);
    }

    // ==================== ERR_ENUM_LITERAL_OUT_OF_RANGE ====================

    @Test
    public void testValueExceedsUnderlyingWidth() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E : u2 { A = 0, B = 4 }\n" +  // 4 > u2 max (3)
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_OUT_OF_RANGE);
        assertFalse("Expected out-of-range sentinel", errors.isEmpty());
        assertTrue(errors.get(0).getMessage().contains("out of range"));
    }

    @Test
    public void testImplicitGapFillExceedsUnderlyingWidth() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E : u2 { A, B, C, D, E }\n" +  // 4 implicit literals exceed u2
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_OUT_OF_RANGE);
        assertFalse("Expected out-of-range on implicit-fill overflow", errors.isEmpty());
    }

    @Test
    public void testNegativeLiteralRejected() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E : u4 { A = -1 }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_OUT_OF_RANGE);
        assertFalse("Expected out-of-range on negative literal", errors.isEmpty());
    }

    @Test
    public void testValuesInRangeAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E : u4 { A = 0, B = 1, C = 15 }\n" +  // all fit u4
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_OUT_OF_RANGE);
        assertTrue("No range error on in-range literals", errors.isEmpty());
    }

    // ==================== ERR_ENUM_LITERAL_DUPLICATE_VALUE ====================

    @Test
    public void testExplicitDuplicateValueRejected() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E { A = 5, B = 5 }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_DUPLICATE_VALUE);
        assertFalse("Expected duplicate-value sentinel", errors.isEmpty());
        assertTrue(errors.get(0).getMessage().contains("duplicates"));
    }

    @Test
    public void testImplicitCollidesWithExplicit() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E { A = 0, B = 0 }\n" +  // explicit 0 then explicit 0
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_DUPLICATE_VALUE);
        assertFalse("Expected duplicate-value on A=0,B=0", errors.isEmpty());
    }

    @Test
    public void testGapFillOverlap() throws Exception {
        // C-style gap fill: A=0, B=1 (implicit), C=1 (explicit) → B and C collide.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E { A = 0, B, C = 1 }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_DUPLICATE_VALUE);
        assertFalse("Expected duplicate-value when implicit fill collides with later explicit",
            errors.isEmpty());
    }

    @Test
    public void testDistinctValuesAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    enum E { A = 0, B = 1, C = 5, D = 10 }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());
        List<Diagnostic> errors = errorsWithCode(result, ERR_ENUM_LITERAL_DUPLICATE_VALUE);
        assertTrue("No duplicate error on distinct values", errors.isEmpty());
    }

    // ==================== helpers ====================

    private List<Diagnostic> errorsWithCode(Module module, String code) {
        AssertableDiagnostics diagnostics = validatorTester.validate(module);
        return diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic
                && code.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());
    }
}
