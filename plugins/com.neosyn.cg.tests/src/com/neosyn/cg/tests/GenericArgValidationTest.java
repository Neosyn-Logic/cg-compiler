/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_GENERIC_ARG_NOT_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_GENERIC_ARG_UNKNOWN;
import static com.neosyn.cg.validation.IssueCodes.ERR_GENERIC_TYPEARG_ARITY;
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
 * L3 Generics iter #1 — validation of named-instantiation arguments
 * {@code new Foo({K: v})}. See .claude/L3_GENERICS_DESIGN.md.
 *
 * Generics already work via {@code const} parameters + property-arg
 * instantiation; the instantiator silently drops a key that does not match a
 * const parameter, so a typo quietly uses the default. These tests pin the
 * two guard-rail sentinels:
 *   - ERR_GENERIC_ARG_UNKNOWN   (key names no parameter of the task)
 *   - ERR_GENERIC_ARG_NOT_CONST (key names a non-const variable)
 * and confirm the accepted cases (valid const override, reserved framework
 * keys clock/clocks/reset) do NOT fire.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class GenericArgValidationTest {

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

    // ===================== ERR_GENERIC_ARG_UNKNOWN =====================

    @Test
    public void testUnknownArgKeyRejected() throws Exception {
        // `Bogus` names no parameter of Widget — today silently ignored.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Widget { const int W = 8; out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { w = new Widget({Bogus: 5}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_GENERIC_ARG_UNKNOWN);
        assertFalse("Expected unknown-arg sentinel on `{Bogus: 5}`", errors.isEmpty());
        assertTrue("Message should name the offending key",
            errors.get(0).getMessage().contains("Bogus"));
    }

    @Test
    public void testValidConstArgAccepted() throws Exception {
        // `W` is a const parameter of Widget — overriding it is the whole point.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Widget { const int W = 8; out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { w = new Widget({W: 16}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("A valid const override must not fire the unknown-arg sentinel",
            errorsWithCode(result, ERR_GENERIC_ARG_UNKNOWN).isEmpty());
    }

    @Test
    public void testReservedKeysAccepted() throws Exception {
        // clock / clocks / reset are framework-reserved instantiation keys; they
        // bind to the properties system, not to a const, and must be accepted.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Widget { const int W = 8; out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { w = new Widget({W: 16, clock: \"clk\"}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Reserved key `clock` must not fire the unknown-arg sentinel",
            errorsWithCode(result, ERR_GENERIC_ARG_UNKNOWN).isEmpty());
    }

    @Test
    public void testAnonymousTaskNoArgsAccepted() throws Exception {
        // Anonymous-task instantiation has no argument list — nothing to check.
        Module result = parseHelper.parse(
            "package test;\n" +
            "network N {\n" +
            "    w = new task { const int W = 8; out u8 q; void loop() { q.write(0); idle(1); } };\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Anonymous task must not fire the unknown-arg sentinel",
            errorsWithCode(result, ERR_GENERIC_ARG_UNKNOWN).isEmpty());
    }

    // ==================== ERR_GENERIC_ARG_NOT_CONST ====================

    @Test
    public void testNonConstArgRejected() throws Exception {
        // `W` here is a (mutable) state variable, not a const. Setting it at
        // instantiation is not a parameter override.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Widget { int W = 8; out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { w = new Widget({W: 16}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_GENERIC_ARG_NOT_CONST);
        assertFalse("Expected not-const sentinel on a state-var override", errors.isEmpty());
    }

    @Test
    public void testConstArgIsNotFlaggedNotConst() throws Exception {
        // A genuine const must NOT trip the not-const sentinel.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Widget { const int W = 8; out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { w = new Widget({W: 16}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("A const override must not fire the not-const sentinel",
            errorsWithCode(result, ERR_GENERIC_ARG_NOT_CONST).isEmpty());
    }

    // ==================== ERR_GENERIC_TYPEARG_ARITY ====================
    // iter #2 — the `<...>` angle-bracket sugar.

    @Test
    public void testTooManyTypeArgsRejected() throws Exception {
        // Cell declares one parameter but the instance supplies two positionally;
        // the second has no formal to bind to.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Cell<int W = 8> { out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { c = new Cell<4, 7>(); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_GENERIC_TYPEARG_ARITY);
        assertFalse("Expected too-many-type-args sentinel on `<4, 7>`", errors.isEmpty());
    }

    @Test
    public void testExactTypeArgsAccepted() throws Exception {
        // One parameter, one positional type-arg — exact arity, no error.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Cell<int W = 8> { out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { c = new Cell<4>(); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Exact-arity type args must not fire the arity sentinel",
            errorsWithCode(result, ERR_GENERIC_TYPEARG_ARITY).isEmpty());
    }

    @Test
    public void testFewerTypeArgsAccepted() throws Exception {
        // Two parameters, one positional type-arg — the unsupplied DEPTH keeps
        // its default. Fewer args is allowed.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Cell<int W = 8, int DEPTH = 4> { out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { c = new Cell<6>(); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Fewer type args than params must not fire the arity sentinel",
            errorsWithCode(result, ERR_GENERIC_TYPEARG_ARITY).isEmpty());
    }

    @Test
    public void testNamedArgOnGenericParamAccepted() throws Exception {
        // A `<...>`-declared formal is also nameable in the `{K: v}` form. The
        // named-arg checker must index getParams(), not just getDecls(), so this
        // must NOT trip the unknown-arg sentinel.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Cell<int W = 8> { out u8 q; void loop() { q.write(0); idle(1); } }\n" +
            "network N { c = new Cell({W: 4}); }\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("A `<...>` formal named in `{W: 4}` must not be flagged unknown",
            errorsWithCode(result, ERR_GENERIC_ARG_UNKNOWN).isEmpty());
    }

    // =========================================================================

    private List<Diagnostic> errorsWithCode(Module module, String code) {
        AssertableDiagnostics diagnostics = validatorTester.validate(module);
        return diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic
                && code.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());
    }
}
