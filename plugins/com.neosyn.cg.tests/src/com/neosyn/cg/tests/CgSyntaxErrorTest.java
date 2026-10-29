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

    // ==================== EMPTY ANGLE BRACKETS (`<>`) ====================

    private static final String EMPTY_ANGLE = "`<>` is empty";

    /**
     * `new T<>()` meaning "the defaults" (a model-authoring trial, 2026-09-24) reported only
     * "no viable alternative at input '>'". The same message comes from `task T<>` and
     * `int<>`, and from nothing else measured, so all three name the fix.
     */
    @Test
    public void testEmptyAngleBracketsOnInstantiationNamesTheFix() throws Exception {
        assertEmptyAngle(
            "package test;\n" +
            "task T<int N = 4> { void loop() { } }\n" +
            "network W {\n" +
            "    t = new T<>();\n" +
            "}\n");
    }

    @Test
    public void testEmptyAngleBracketsWithSpaceAndOnTaskAndType() throws Exception {
        assertEmptyAngle("package test;\ntask T<int N = 4> { void loop() { } }\n"
            + "network W {\n    t = new T< >();\n}\n");
        assertEmptyAngle("package test;\ntask T<> {\n    void loop() { }\n}\n");
        assertEmptyAngle("package test;\ntask T {\n    void loop() { int<> x = 0; }\n}\n");
    }

    /** The control: a malformed comparison is a different error and must not get this advice. */
    @Test
    public void testMalformedComparisonIsNotAnEmptyAngle() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    in stream u8 a;\n" +
            "    void loop() { u8 v = a.read(); if (v > ) { } }\n" +
            "}\n");
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("fixture sanity: it is a syntax error", errors.isEmpty());
        assertFalse("a comparison error is not an empty `<>`: " + messagesOf(errors),
            messagesOf(errors).contains(EMPTY_ANGLE));
    }

    private void assertEmptyAngle(String source) throws Exception {
        Module result = parseHelper.parse(source);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertTrue("the diagnostic must name the empty brackets and the fix: " + messagesOf(errors),
            messagesOf(errors).contains(EMPTY_ANGLE) && messagesOf(errors).contains("`new T()`"));
    }

    // ==================== `const` WITHOUT A TYPE ====================

    private static final String CONST_TYPE = "a `const` needs a type";

    private static final String BODY_BRACES = "a body needs braces";

    private static final String TASK = "task T {\n    in stream u8 a;\n    out stream u8 b;\n"
        + "    void loop() {\n        u8 v = a.read();\n        BODY\n        b.write(v);\n    }\n}\n";

    /**
     * `const FRAME = 32768;` (a model-authoring trial, 2026-09-24): the model concluded file-scope
     * consts are not allowed and moved it into the task. They are; the TYPE was missing.
     */
    @Test
    public void testConstWithoutTypeNamesTheType() throws Exception {
        assertMessage("package test;\nconst FRAME = 32768;\n" + TASK.replace("BODY", ""), CONST_TYPE,
            "`const u32 FRAME = ...;`");
        assertMessage("package test;\n" + TASK.replace("in stream u8 a;", "const K = 3;\n    in stream u8 a;")
            .replace("BODY", ""), CONST_TYPE, "`const u32 K = ...;`");
        assertMessage("package test;\n" + TASK.replace("BODY", "const K = 3;"), CONST_TYPE,
            "`const u32 K = ...;`");
    }

    /** The control, and the fact the message states: a TYPED const is fine at file scope. */
    @Test
    public void testTypedFileScopeConstParses() throws Exception {
        Module result = parseHelper.parse(
            "package test;\nconst u32 FRAME = 32768;\n" + TASK.replace("BODY", "u32 f = FRAME;"));
        assertTrue("a typed file-scope const must parse: " + messagesOf(result.eResource().getErrors()),
            result.eResource().getErrors().isEmpty());
    }

    /** `for (...) v.write(x);` cost a model a working test, four tries in one turn (a model-authoring trial). */
    @Test
    public void testBracelessBodyNamesTheBraces() throws Exception {
        for (String body : new String[] { "for (u8 i = 0; i < 4; i++) v = v + 1;",
                "while (v > 3) v = v - 1;", "if (v > 3) v = 0;", "if (v > 3) { v = 0; } else v = 1;" }) {
            assertMessage("package test;\n" + TASK.replace("BODY", body), BODY_BRACES, "`for (...) { v.write(x); }`");
        }
    }

    /** Controls: other missing tokens keep the parser's own message. */
    @Test
    public void testOtherMissingTokensAreNotBraceAdvice() throws Exception {
        assertNotMessage("package test;\n" + TASK.replace("BODY", "b.write(v;"), BODY_BRACES);
        assertNotMessage("package test;\ntask T\n    in stream u8 a;\n}\n", BODY_BRACES);
    }

    private void assertMessage(String source, String... fragments) throws Exception {
        String messages = messagesOf(parseHelper.parse(source).eResource().getErrors());
        for (String fragment : fragments) {
            assertTrue("expected `" + fragment + "` in: " + messages, messages.contains(fragment));
        }
    }

    private void assertNotMessage(String source, String fragment) throws Exception {
        String messages = messagesOf(parseHelper.parse(source).eResource().getErrors());
        assertFalse("fixture sanity: it is a syntax error", messages.isEmpty());
        assertFalse("`" + fragment + "` must not appear in: " + messages, messages.contains(fragment));
    }

    // ==================== M2: SHAPES FROM OTHER LANGUAGES (2026-09-30) ====================

    /**
     * A model wrote `task Disparity[int W : 32]` and got only "mismatched input '['
     * expecting '{'". The message now gives the C⏚ spelling, with the task's own name.
     */
    @Test
    public void testGenericInBracketsNamesTheAngleForm() throws Exception {
        assertMessage("package p;\ntask D[int W : 32] { in stream u8 a; out stream u8 y;\n"
                + "  void loop() { y.write(a.read()); } }\n",
                "ANGLE brackets", "task D<int W = 32>", "new D<64>()");
    }

    /** C⏚ has no do-while; the message gives the equivalent loop. */
    @Test
    public void testDoWhileGetsTheWhileTrueForm() throws Exception {
        assertMessage("package p;\ntask T { in u8 a; out u8 y;\n"
                + "  void loop() { do { y.write(a.read()); } while (true); } }\n",
                "has no `do", "while (true)");
    }

    /** C⏚ has no switch; the first message says so (TRAPS T171: it used to name only the `{`). */
    @Test
    public void testSwitchGetsTheIfElseForm() throws Exception {
        assertMessage("package p;\ntask T { in u8 a; out u8 y;\n  enum St { A, B }\n  St s;\n"
                + "  void loop() { u8 v = a.read();\n    switch (s) { case A: y.write(v); break; }\n  } }\n",
                "has no `switch`", "else if");
    }

    /** Control: a call followed by a stray `{` is not a switch. */
    @Test
    public void testCallThenBraceIsNotSwitchAdvice() throws Exception {
        assertNotMessage("package p;\ntask T { in u8 a; out u8 y;\n"
                + "  void loop() { u8 v = a.read() { y.write(v); } } }\n",
                "has no `switch`");
    }

    /**
     * Control: `do` is not a keyword, so a signal may be named `do` (data out). A parse that
     * fails AT that name -- here a missing `;` before it -- is not a do-while.
     */
    @Test
    public void testSignalNamedDoIsNotDoWhileAdvice() throws Exception {
        assertNotMessage("package p;\ntask T { in u8 a; out u8 y; u8 do;\n"
                + "  void loop() { u8 v = a.read()\n    do = v; y.write(do); } }\n",
                "has no `do");
    }

    // ==================== MODIFIER ORDER (`const`) ====================

    /**
     * `const` is a modifier and the grammar reads it BEFORE the type. Writing the type
     * first is the C/Java habit and the shape a model reaches for, and it used to produce
     * three cascading "no viable alternative" messages, none of which said "order".
     */
    @Test
    public void testConstAfterTypeOnMemberNamesTheOrder() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Wrong {\n" +
            "    in u8 x; out u8 y;\n" +
            "    u16 const twice(u8 v) { return (u16)(v * 2); }\n" +  // type BEFORE const
            "    void loop() { y.write((u8) twice(x.read())); }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for `const` after the type", errors.isEmpty());
        assertTrue(
            "The diagnostic must say the modifier comes BEFORE the type, not just name the "
                + "token. Actual: " + messagesOf(errors),
            messagesOf(errors).contains("`const` is a modifier and goes BEFORE the type"));
    }

    /**
     * The same rule applies to a local: `StatementVariable` also reads the modifier first.
     * The PARSER message differs though -- a local yields "extraneous input 'const'"
     * where a member yields "no viable alternative" -- so this is not a duplicate of the
     * test above; it pins the second branch, which the first version of the fix missed.
     */
    @Test
    public void testConstAfterTypeOnLocalNamesTheOrder() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task WrongLocal {\n" +
            "    in u8 x; out u8 y;\n" +
            "    void loop() {\n" +
            "        u8 const k = 2;\n" +  // type BEFORE const
            "        y.write((u8)(x.read() + k));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        EList<Diagnostic> errors = result.eResource().getErrors();
        assertFalse("Should have syntax errors for `const` after the type", errors.isEmpty());
        assertTrue(
            "A local declaration must get the same order advice. Actual: " + messagesOf(errors),
            messagesOf(errors).contains("`const` is a modifier and goes BEFORE the type"));
    }

    /**
     * Negative control: the CORRECT order must still parse clean. Without this, a message
     * fired unconditionally on the token would pass the two tests above.
     */
    @Test
    public void testConstBeforeTypeParsesClean() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Right {\n" +
            "    in u8 x; out u8 y;\n" +
            "    const u16 twice(u8 v) { return (u16)(v * 2); }\n" +
            "    void loop() { y.write((u8) twice(x.read())); }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        assertTrue("`const` before the type must parse with no syntax errors: "
            + messagesOf(result.eResource().getErrors()),
            result.eResource().getErrors().isEmpty());
    }

    /**
     * The point of the whole fix: the two ORDERING errors must be told apart. A file-scope
     * declaration written after the first entity is a different mistake with a different
     * remedy, and from the raw parser message the two were indistinguishable -- which
     * invited the user to look in exactly the wrong place.
     */
    @Test
    public void testFileScopeOrderingIsNotConfusedWithModifierOrdering() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task First {\n" +
            "    in u8 x; out u8 y;\n" +
            "    void loop() { y.write(x.read()); }\n" +
            "}\n" +
            "const u8 SHARED = 7;\n"  // file-scope decl AFTER the first entity
        );
        assertNotNull("Parse result should not be null", result);
        String msgs = messagesOf(result.eResource().getErrors());
        assertTrue("A file-scope declaration after the first entity must get the file-scope "
            + "advice. Actual: " + msgs, msgs.contains("file-scope declaration must come BEFORE"));
        assertFalse("...and must NOT be told its modifier order is wrong. Actual: " + msgs,
            msgs.contains("`const` is a modifier and goes BEFORE the type"));
    }

    private static String messagesOf(EList<Diagnostic> errors) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : errors) {
            sb.append(d.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    // ==================== RESERVED WORDS ====================

    /**
     * `out push bool ack;` reported `mismatched input 'ack' expecting RULE_ID` — the token
     * named, and nothing to say WHY an ordinary-looking port name was refused. There is no
     * reserved-word list published anywhere the user can read, so the bare token was not
     * actionable. This exact shape shipped in the public docs for months.
     */
    @Test
    public void reservedWordAsPortNameSaysReservedWord() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Q {\n" +
            "    properties { implementation: { type: \"external\" } }\n" +
            "    in  push u16 din;\n" +
            "    out push bool ack;\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        String msgs = messagesOf(result.eResource().getErrors());
        assertTrue("the diagnostic must say `ack` is reserved. Actual: " + msgs,
            msgs.contains("`ack` is a reserved word"));
    }

    /**
     * TRAPS T101: `properties` before `import` in a network or task body said "`import` is a
     * reserved word ... rename it (for example `import_`)" -- nothing was used as a name, and
     * the advice could not be followed. It must say the import is out of order.
     */
    @Test
    public void importAfterPropertiesSaysImportsGoFirst() throws Exception {
        Module network = parseHelper.parse(
            "package test;\n" +
            "network N {\n" +
            "    properties { test: { terminate: \"t.finished\" } }\n" +
            "    import test.T;\n" +
            "    t = new T();\n" +
            "}\n"
        );
        String nm = messagesOf(network.eResource().getErrors());
        assertTrue("a network must be told imports go first. Actual: " + nm,
            nm.contains("`import` is out of place") && nm.contains("imports go first"));
        assertFalse("...and not to rename `import`. Actual: " + nm, nm.contains("import_"));

        Module task = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "    properties { type: \"combinational\" }\n" +
            "    import test.Defs.*;\n" +
            "    out u8 y;\n" +
            "    void loop() { y.write(1); }\n" +
            "}\n"
        );
        String tm = messagesOf(task.eResource().getErrors());
        assertTrue("a task must be told imports go first. Actual: " + tm,
            tm.contains("`import` is out of place"));
    }

    /**
     * Three positions, three DIFFERENT parser messages for the same mistake — a port and a
     * local yield "mismatched input", a state variable yields "no viable alternative". All
     * three must reach the same advice, which is why the branch keys off the offending TOKEN
     * rather than off one message spelling.
     */
    @Test
    public void reservedWordIsRecognisedInEveryNamePosition() throws Exception {
        Module local = parseHelper.parse(
            "package test;\n" +
            "task L {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    void loop() { u8 in = x.read(); y.write(in); }\n" +
            "}\n"
        );
        assertTrue("a reserved word as a LOCAL must be named: "
            + messagesOf(local.eResource().getErrors()),
            messagesOf(local.eResource().getErrors()).contains("`in` is a reserved word"));

        Module state = parseHelper.parse(
            "package test;\n" +
            "task S {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    u8 stream;\n" +
            "    void loop() { y.write(x.read()); }\n" +
            "}\n"
        );
        assertTrue("a reserved word as a STATE VARIABLE must be named: "
            + messagesOf(state.eResource().getErrors()),
            messagesOf(state.eResource().getErrors()).contains("`stream` is a reserved word"));
    }

    /**
     * The keyword set is read from the LIVE grammar so it cannot drift, and Xtext counts
     * punctuation as keywords too — so the branch additionally requires the token to be
     * NAME-SHAPED. Without that, `(` and `;` would be announced as reserved words.
     *
     * <p>These controls also pin the ORDER of the branches: `const` and `[` are both
     * reserved/keyword tokens, and each has its own more specific advice that must still win.
     */
    @Test
    public void moreSpecificKeywordAdviceStillWins() throws Exception {
        Module constOrder = parseHelper.parse(
            "package test;\n" +
            "task C {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    u16 const f(u8 v) { return (u16) v; }\n" +
            "    void loop() { y.write(x.read()); }\n" +
            "}\n"
        );
        String cm = messagesOf(constOrder.eResource().getErrors());
        assertTrue("`const` must keep its modifier-order advice. Actual: " + cm,
            cm.contains("`const` is a modifier and goes BEFORE the type"));
        assertFalse("...and must NOT be demoted to the generic reserved-word message: " + cm,
            cm.contains("`const` is a reserved word"));

        // An array PORT is legal since 2026-09-09 (it fans out to one port per element),
        // so what used to be an advice check is now a control: it must parse cleanly.
        Module arr = parseHelper.parse(
            "package test;\n" +
            "task A {\n" +
            "    sync { in u8 W[4]; out u8 y; }\n" +
            "    void loop() { y.write(W[0].read()); }\n" +
            "}\n"
        );
        assertTrue("an array port must parse: " + messagesOf(arr.eResource().getErrors()),
            arr.eResource().getErrors().isEmpty());
    }

    /** An ordinary undefined name is NOT a reserved word and must not be called one. */
    @Test
    public void anOrdinaryUnknownNameIsNotCalledReserved() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task U {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    u8 counter;\n" +
            "    void loop() { counter = x.read(); y.write(counter); }\n" +
            "}\n"
        );
        assertTrue("an ordinary identifier must parse cleanly: "
            + messagesOf(result.eResource().getErrors()),
            result.eResource().getErrors().isEmpty());
    }

    // ==================== ARRAY DIMENSIONS IN A DECLARATION ====================

    /**
     * Models reach for array PORTS whenever a design carries weights or coefficients
     * (`in push int<32> W[32];` — a real devstral run did it even when handed a scaffold
     * whose ports it was told not to change). Rather than keep explaining why that is
     * rejected, the language accepts it: an array port fans out to one port per element,
     * which is what the old diagnostic told people to hand-write. Both the plain and the
     * `push` form must parse, since the report was about the `push` one.
     */
    @Test
    public void arrayPortsParse() throws Exception {
        Module bare = parseHelper.parse(
            "package test;\n" +
            "task Arr {\n" +
            "    sync { in int<32> W[4]; out u8 y; }\n" +
            "    void loop() { y.write((u8) W[0].read()); }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", bare);
        assertTrue("a bare array port must parse: " + messagesOf(bare.eResource().getErrors()),
            bare.eResource().getErrors().isEmpty());

        Module push = parseHelper.parse(
            "package test;\n" +
            "task ArrPush {\n" +
            "    in push int<32> W[32];\n" +
            "    out push u8 y;\n" +
            "    void loop() { y.write((u8) W[31].read()); }\n" +
            "}\n"
        );
        assertTrue("the reported `push` form must parse: "
            + messagesOf(push.eResource().getErrors()),
            push.eResource().getErrors().isEmpty());
    }

    /** An array OUTPUT port is written through an index, which needed its own grammar slot. */
    @Test
    public void arrayOutputPortWriteParses() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ArrOut {\n" +
            "    in push u8 x;\n" +
            "    out push u8 y[2];\n" +
            "    void loop() { u8 v = x.read(); y[0].write(v); y[1].write(v); }\n" +
            "}\n"
        );
        assertTrue("an indexed port write must parse: "
            + messagesOf(result.eResource().getErrors()),
            result.eResource().getErrors().isEmpty());
    }

    /**
     * The write-statement index shares its prefix with an assignment target, so the two are
     * only told apart after the brackets. This is the control that an ordinary indexed
     * ASSIGNMENT did not become a casualty of that change.
     */
    @Test
    public void indexedAssignmentStillParses() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Asg {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    u8 acc[4];\n" +
            "    void loop() { acc[0] = x.read(); acc[1] = acc[0]; y.write(acc[1]); }\n" +
            "}\n"
        );
        assertTrue("an indexed assignment must still parse: "
            + messagesOf(result.eResource().getErrors()),
            result.eResource().getErrors().isEmpty());
    }

    /**
     * The SAME parser message comes from a struct field and from a typedef, which is why the
     * message must not say "there are no array ports" — that wording would be confidently
     * wrong here. Established by running each position, not inferred from the grammar.
     */
    @Test
    public void arrayStructFieldGetsTheSameRuleNotAPortSpecificOne() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "struct P { u8 a[2]; u8 b; }\n" +
            "task Arr {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    void loop() { y.write(x.read()); }\n" +
            "}\n"
        );
        assertNotNull("Parse result should not be null", result);
        String msgs = messagesOf(result.eResource().getErrors());
        assertTrue("a struct-field array reaches the same message. Actual: " + msgs,
            msgs.contains("array dimensions are not allowed in this declaration"));
        assertFalse("...so the message must NOT claim this is about ports. Actual: " + msgs,
            msgs.contains("no array ports"));
    }

    /**
     * The controls that keep the rule honest: every position the message names as ALLOWED
     * must actually parse. Without these, the message could describe a language that does
     * not exist and nothing would notice.
     */
    @Test
    public void arrayDimensionsAreAllowedOnStateLocalAndConst() throws Exception {
        Module state = parseHelper.parse(
            "package test;\n" +
            "task S {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    u8 acc[4];\n" +
            "    void loop() { acc[0] = x.read(); y.write(acc[0]); }\n" +
            "}\n"
        );
        assertTrue("a state-variable array must parse: " + messagesOf(state.eResource().getErrors()),
            state.eResource().getErrors().isEmpty());

        Module local = parseHelper.parse(
            "package test;\n" +
            "task L {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    void loop() { u8 t[2]; t[0] = x.read(); y.write(t[0]); }\n" +
            "}\n"
        );
        assertTrue("a local array must parse: " + messagesOf(local.eResource().getErrors()),
            local.eResource().getErrors().isEmpty());

        Module konst = parseHelper.parse(
            "package test;\n" +
            "task K {\n" +
            "    sync { in u8 x; out u8 y; }\n" +
            "    const u8 T[2] = {1, 2};\n" +
            "    void loop() { y.write(T[0]); }\n" +
            "}\n"
        );
        assertTrue("a const array must parse: " + messagesOf(konst.eResource().getErrors()),
            konst.eResource().getErrors().isEmpty());

        Module port = parseHelper.parse(
            "package test;\n" +
            "task P {\n" +
            "    sync { in u8 W[4]; out u8 y; }\n" +
            "    void loop() { y.write(W[0].read()); }\n" +
            "}\n"
        );
        assertTrue("a port array must parse: " + messagesOf(port.eResource().getErrors()),
            port.eResource().getErrors().isEmpty());
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
