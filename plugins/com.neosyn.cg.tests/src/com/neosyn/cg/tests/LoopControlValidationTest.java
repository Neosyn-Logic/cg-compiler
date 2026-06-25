/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_LOOP_CTRL_OUTSIDE_LOOP;
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
 * {@code break} / {@code continue} parse cleanly and lower inside a loop. The
 * validator only objects when they appear with no enclosing loop.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class LoopControlValidationTest {

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

    /** Wraps the body in a task with a {@code for} loop around {@code loopBody}. */
    private Module inLoop(String loopBody) throws Exception {
        Module m = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "  out push u8 o;\n" +
            "  void loop() {\n" +
            "    u8 s = 0;\n" +
            "    for (u8 i = 0; i < 8; i++) {\n" + loopBody +
            "    }\n" +
            "    o.write(s); idle(1);\n" +
            "  }\n" +
            "}\n");
        assertParses(m);
        return m;
    }

    /** Wraps the body in a task with NO enclosing loop. */
    private Module noLoop(String stmts) throws Exception {
        Module m = parseHelper.parse(
            "package test;\n" +
            "task T {\n" +
            "  out push u8 o;\n" +
            "  void loop() {\n" +
            "    u8 s = 0;\n" + stmts +
            "    o.write(s); idle(1);\n" +
            "  }\n" +
            "}\n");
        assertParses(m);
        return m;
    }

    private static void assertParses(Module m) {
        assertNotNull(m);
        assertTrue("break/continue must parse (no error cascade): "
                + m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
    }

    @Test
    public void testContinueInsideLoopOk() throws Exception {
        assertTrue("`continue` inside a loop must not be flagged",
            errors(inLoop("      if (i == 3) { continue; } s = (u8) (s + 1);\n")).isEmpty());
    }

    @Test
    public void testBreakInsideLoopOk() throws Exception {
        assertTrue("`break` inside a loop must not be flagged",
            errors(inLoop("      if (i == 4) { break; } s = (u8) (s + 1);\n")).isEmpty());
    }

    @Test
    public void testContinueOutsideLoopFlagged() throws Exception {
        assertFalse("`continue` with no enclosing loop must be flagged",
            errors(noLoop("    if (s == 0) { continue; }\n")).isEmpty());
    }

    @Test
    public void testBreakOutsideLoopFlagged() throws Exception {
        assertFalse("`break` with no enclosing loop must be flagged",
            errors(noLoop("    if (s == 0) { break; }\n")).isEmpty());
    }

    private List<Diagnostic> errors(Module module) {
        AssertableDiagnostics diagnostics = validatorTester.validate(module);
        return diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic
                && ERR_LOOP_CTRL_OUTSIDE_LOOP.equals(
                    ((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());
    }
}
