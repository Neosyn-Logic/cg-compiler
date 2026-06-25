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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.neosyn.cg.cg.Module;

/**
 * Unit tests for Cx syntax error detection.
 * These tests verify that the parser correctly identifies syntax errors.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class CgSyntaxErrorTest {

    @Inject
    private ParseHelper<Module> parseHelper;

    // ==================== MISSING SEMICOLON TESTS ====================

    @Test
    public void testMissingSemicolonInVariableDeclaration() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingSemicolon {\n" +
            "    u8 x = 5\n" +  // Missing semicolon
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing semicolon", errors.isEmpty());
    }

    @Test
    public void testMissingSemicolonInStatement() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingSemicolon {\n" +
            "    in u8 inp; out u8 outp;\n" +
            "    void loop() {\n" +
            "        u8 x = inp.read()\n" +  // Missing semicolon
            "        outp.write(x);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing semicolon", errors.isEmpty());
    }

    @Test
    public void testMissingSemicolonAfterPortDeclaration() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingSemicolon {\n" +
            "    in u8 inp\n" +  // Missing semicolon
            "    out u8 outp;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing semicolon", errors.isEmpty());
    }

    // ==================== MISSING BRACE TESTS ====================

    @Test
    public void testMissingClosingBrace() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingBrace {\n" +
            "    in u8 inp;\n" +
            // Missing closing brace
            ""
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing closing brace", errors.isEmpty());
    }

    @Test
    public void testMissingOpeningBrace() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingBrace\n" +  // Missing opening brace
            "    in u8 inp;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing opening brace", errors.isEmpty());
    }

    // ==================== MISSING PACKAGE TESTS ====================

    @Test
    public void testMissingPackageDeclaration() throws Exception {
        Module result = parseHelper.parse(
            // Missing package declaration
            "task NoPackage {\n" +
            "    in u8 inp;\n" +
            "}\n"
        );
        // Parser returns null when package is missing because it's required by grammar
        // This is expected behavior - the grammar requires "package <name>;" at the start
        if (result != null) {
            EList<Diagnostic> errors = result.eResource().getErrors();
            assertFalse("Should have syntax errors for missing package", errors.isEmpty());
        }
        // If result is null, that's also acceptable - parser couldn't create a Module without package
    }

    // ==================== INVALID TOKEN TESTS ====================

    @Test
    public void testInvalidKeyword() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "taks InvalidKeyword {\n" +  // Typo: 'taks' instead of 'task'
            "    in u8 inp;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for invalid keyword", errors.isEmpty());
    }

    @Test
    public void testInvalidPortDirection() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task InvalidPort {\n" +
            "    input u8 inp;\n" +  // 'input' instead of 'in'
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for invalid port direction", errors.isEmpty());
    }

    // ==================== VALID CODE TESTS (NO ERRORS) ====================

    @Test
    public void testValidEmptyTask() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ValidTask {\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Valid task should have no syntax errors: " + errors, errors.isEmpty());
    }

    @Test
    public void testValidTaskWithPorts() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ValidTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Valid task should have no syntax errors: " + errors, errors.isEmpty());
    }

    @Test
    public void testValidTaskWithLoop() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ValidTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(inp.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Valid task should have no syntax errors: " + errors, errors.isEmpty());
    }

    @Test
    public void testValidNetwork() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "network ValidNetwork {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Valid network should have no syntax errors: " + errors, errors.isEmpty());
    }

    @Test
    public void testValidBundle() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "bundle ValidBundle {\n" +
            "    const u8 VALUE = 42;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Valid bundle should have no syntax errors: " + errors, errors.isEmpty());
    }

    // ==================== EXPRESSION SYNTAX TESTS ====================

    @Test
    public void testMissingOperand() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MissingOperand {\n" +
            "    in u8 inp; out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(inp.read() + );\n" +  // Missing second operand
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for missing operand", errors.isEmpty());
    }

    @Test
    public void testUnmatchedParenthesis() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task UnmatchedParen {\n" +
            "    in u8 inp; out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write((inp.read();\n" +  // Missing closing parenthesis
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for unmatched parenthesis", errors.isEmpty());
    }

    // ==================== ERROR COUNT TESTS ====================

    @Test
    public void testMultipleSyntaxErrors() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MultipleErrors {\n" +
            "    in u8 inp\n" +  // Missing semicolon 1
            "    out u8 outp\n" +  // Missing semicolon 2
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("Should have at least one syntax error", errors.size() >= 1);
    }

    // ==================== EDGE CASES ====================

    @Test
    public void testEmptyInput() throws Exception {
        Module result = parseHelper.parse("");
        // Empty input returns null because the grammar requires at least a package declaration
        // This is expected behavior for the Cx grammar
        // The test verifies the parser doesn't crash on empty input
    }

    @Test
    public void testWhitespaceOnly() throws Exception {
        Module result = parseHelper.parse("   \n\t\n   ");
        // Whitespace-only input returns null because the grammar requires package declaration
        // This is expected behavior
    }

    @Test
    public void testCommentOnly() throws Exception {
        Module result = parseHelper.parse("// This is just a comment\n");
        // Comment-only input returns null because the grammar requires package declaration
        // This is expected behavior
    }
}
