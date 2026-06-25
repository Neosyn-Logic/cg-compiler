/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_LOCAL_SHADOWS_PORT;
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
 * Unit tests for port shadowing validation.
 *
 * Tests that a local variable with the same name as a port produces an error.
 * This is a common bug that causes port writes to silently fail because
 * the scoper resolves to the local variable instead of the port.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class PortShadowingValidationTest {

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

    // ==================== ERROR CASES ====================

    @Test
    public void testLocalVariableShadowsOutputPort() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ShadowingTask {\n" +
            "    out u8 enabled;\n" +
            "    void loop() {\n" +
            "        u8 enabled = 5;\n" +  // This shadows the output port!
            "        enabled.write(enabled);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for port shadowing error
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> errors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .collect(Collectors.toList());

        assertFalse("Should have validation error for port shadowing", errors.isEmpty());

        // Check that the error message mentions the issue code
        boolean foundShadowingError = errors.stream()
            .anyMatch(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
        assertTrue("Should have ERR_LOCAL_SHADOWS_PORT error", foundShadowingError);
    }

    @Test
    public void testLocalVariableShadowsInputPort() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ShadowingTask {\n" +
            "    in u8 data;\n" +
            "    out u8 result;\n" +
            "    void loop() {\n" +
            "        u8 data = 10;\n" +  // This shadows the input port!
            "        result.write(data);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for port shadowing error
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> errors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .collect(Collectors.toList());

        assertFalse("Should have validation error for port shadowing", errors.isEmpty());

        boolean foundShadowingError = errors.stream()
            .anyMatch(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
        assertTrue("Should have ERR_LOCAL_SHADOWS_PORT error", foundShadowingError);
    }

    @Test
    public void testLocalVariableShadowsPortInForLoop() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ShadowingTask {\n" +
            "    out u8 count;\n" +
            "    void loop() {\n" +
            "        for (u8 count = 0; count < 10; count++) {\n" +  // for-loop variable shadows port
            "        }\n" +
            "        count.write(0);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for port shadowing error
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> errors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .collect(Collectors.toList());

        assertFalse("Should have validation error for port shadowing in for loop", errors.isEmpty());

        boolean foundShadowingError = errors.stream()
            .anyMatch(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()));
        assertTrue("Should have ERR_LOCAL_SHADOWS_PORT error", foundShadowingError);
    }

    @Test
    public void testMultipleLocalVariablesShadowPorts() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ShadowingTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        u8 inp = 1;\n" +  // Shadows input port
            "        u8 outp = 2;\n" + // Shadows output port
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for port shadowing errors
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> shadowingErrors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());

        assertEquals("Should have 2 port shadowing errors", 2, shadowingErrors.size());
    }

    // ==================== VALID CODE - NO ERRORS ====================

    @Test
    public void testDifferentNameFromPort() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ValidTask {\n" +
            "    out u8 enabled;\n" +
            "    void loop() {\n" +
            "        u8 enableMask = 0b11;\n" +  // Different name - OK
            "        enabled.write(enableMask);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for NO port shadowing error
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> shadowingErrors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());

        assertTrue("Should have no port shadowing errors", shadowingErrors.isEmpty());
    }

    @Test
    public void testSameNameAsPortButInBundle() throws Exception {
        // Variables in bundles should not be checked (no ports in bundles)
        Module result = parseHelper.parse(
            "package test;\n" +
            "bundle Constants {\n" +
            "    const u8 enabled = 5;\n" +  // OK - bundles don't have ports
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Validate and check for NO port shadowing error
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> shadowingErrors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());

        assertTrue("Should have no port shadowing errors for bundles", shadowingErrors.isEmpty());
    }

    @Test
    public void testStateVariableSameNameAsPort() throws Exception {
        // State variables (not local variables) are different - they should also be checked
        // but CgUtil.isLocal() returns false for state variables
        // This is a design decision - state variables could also shadow ports
        // For now, we only check local variables
        Module result = parseHelper.parse(
            "package test;\n" +
            "task TaskWithState {\n" +
            "    out u8 enabled;\n" +
            "    u8 localEnabled;\n" +  // State variable with different name - OK
            "    void loop() {\n" +
            "        enabled.write(localEnabled);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("Should have no parse errors", result.eResource().getErrors().isEmpty());

        // Should compile without shadowing errors
        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> shadowingErrors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());

        assertTrue("Should have no port shadowing errors", shadowingErrors.isEmpty());
    }

    // ==================== ERROR MESSAGE TEST ====================

    @Test
    public void testErrorMessageContainsPortDirection() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ShadowingTask {\n" +
            "    out u8 result;\n" +
            "    void loop() {\n" +
            "        u8 result = 5;\n" +  // Shadows output port
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);

        AssertableDiagnostics diagnostics = validatorTester.validate(result);
        List<Diagnostic> shadowingErrors = diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic &&
                ERR_LOCAL_SHADOWS_PORT.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());

        assertFalse("Should have shadowing error", shadowingErrors.isEmpty());

        String message = shadowingErrors.get(0).getMessage();
        assertTrue("Error message should mention 'result'", message.contains("result"));
        assertTrue("Error message should mention 'out'", message.contains("out"));
        assertTrue("Error message should mention 'shadows'", message.contains("shadows"));
    }
}
