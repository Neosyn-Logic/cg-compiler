/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_STRUCT_AS_PORT_TYPE;
import static com.neosyn.cg.validation.IssueCodes.ERR_STRUCT_CYCLE;
import static com.neosyn.cg.validation.IssueCodes.ERR_STRUCT_FIELD_TYPE_NOT_PRIMITIVE;
import static com.neosyn.cg.validation.IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE;
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
 * L1 struct sentinel validation tests.
 *
 * Each test covers one of the three v1 deferral sentinels declared in
 * .claude/L1_STRUCT_DESIGN.md §6:
 *   - ERR_STRUCT_FIELD_TYPE_NOT_PRIMITIVE (nested struct fields)
 *   - ERR_STRUCT_AS_PORT_TYPE            (struct-typed ports)
 *   - ERR_STRUCT_WHOLE_VALUE_USE         (whole-struct values in expressions)
 *
 * Without these sentinels, the deferred surfaces NPE deep in IR-gen or
 * silently mis-lower. With them, the user gets a clean v1 error.
 *
 * STRUCT_FIELD_ARRAY is not a sentinel because the grammar already forbids
 * field dimensions (syntax error). STRUCT_UNKNOWN_FIELD is not a sentinel
 * because the scope provider's getFieldDescs scopes only valid fields and
 * the Xtext linker emits "Couldn't resolve reference" for typos.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class StructSentinelValidationTest {

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

    // ============= STRUCT nesting (Tier 2.3) + recursion guard =============

    @Test
    public void testNestedStructFieldAccepted() throws Exception {
        // Tier 2.3: nested struct fields are now supported (they flatten
        // recursively). No sentinel, no cycle error.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task NestedStructs {\n" +
            "    struct Inner { u8 a; }\n" +
            "    struct Outer { Inner i; }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull("Parse should succeed", result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Nested struct fields must be accepted",
            errorsWithCode(result, ERR_STRUCT_FIELD_TYPE_NOT_PRIMITIVE).isEmpty());
        assertTrue("Non-recursive nesting must not fire the cycle guard",
            errorsWithCode(result, ERR_STRUCT_CYCLE).isEmpty());
    }

    @Test
    public void testNestedFieldAccessAccepted() throws Exception {
        // Multi-segment nested access (`o.lo.a`) must resolve in scoping and not
        // trip the whole-value sentinel.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task NestedAccess {\n" +
            "    struct Inner { u8 a; }\n" +
            "    struct Outer { Inner lo; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Outer o;\n" +
            "        o.lo.a = 5;\n" +
            "        q.write(o.lo.a); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse/linking errors (nested access resolves)",
            result.eResource().getErrors().isEmpty());
        assertTrue("Nested leaf access must not fire whole-value sentinel",
            errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE).isEmpty());
    }

    @Test
    public void testArrayElementFieldAccessAccepted() throws Exception {
        // Tier 2.3: per-element field access on an array of struct (`ps[i].lo`)
        // must resolve and not trip the whole-value sentinel.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ArrayAccess {\n" +
            "    struct Pair { u8 lo; u16 hi; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair ps[4];\n" +
            "        ps[0].lo = 1;\n" +
            "        ps[1].hi = 2;\n" +
            "        q.write(ps[0].lo); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse/linking errors (ps[i].lo resolves)",
            result.eResource().getErrors().isEmpty());
        assertTrue("Array-element field access must not fire whole-value sentinel",
            errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE).isEmpty());
    }

    @Test
    public void testWholeArrayElementValueRejected() throws Exception {
        // A whole struct-array element used as a value (`Pair p = ps[0];`) is
        // not supported in v1 — access fields with `ps[0].field`.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task ArrayElementValue {\n" +
            "    struct Pair { u8 lo; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair ps[4];\n" +
            "        ps[0].lo = 1;\n" +
            "        Pair p = ps[0];\n" +
            "        q.write(p.lo); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertFalse("Expected whole-value sentinel on `Pair p = ps[0];`",
            errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE).isEmpty());
    }

    @Test
    public void testRecursiveStructRejected() throws Exception {
        // A struct that contains itself would flatten infinitely — reject it.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Recursive {\n" +
            "    struct Node { u8 v; Node next; }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_CYCLE);
        assertFalse("Expected cycle error on self-referential struct", errors.isEmpty());
        assertTrue("Error message should mention 'recursive'",
            errors.get(0).getMessage().toLowerCase().contains("recursive"));
    }

    @Test
    public void testMutuallyRecursiveStructRejected() throws Exception {
        // Transitive cycle A -> B -> A is also rejected.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MutualRecursive {\n" +
            "    struct A { B b; }\n" +
            "    struct B { A a; }\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertFalse("Expected cycle error on mutually-recursive structs",
            errorsWithCode(result, ERR_STRUCT_CYCLE).isEmpty());
    }

    @Test
    public void testPrimitiveStructFieldsAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task PrimitiveFields {\n" +
            "    struct Pair { u8 lo; u16 hi; bool flag; }\n" +
            "    out u32 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        p.lo = 1; p.hi = 2; p.flag = true;\n" +
            "        q.write(p.hi); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_FIELD_TYPE_NOT_PRIMITIVE);
        assertTrue("No nested-struct sentinel on primitive fields", errors.isEmpty());
    }

    // ==================== STRUCT_AS_PORT_TYPE ====================

    @Test
    public void testBareStructPortsAccepted() throws Exception {
        // Tier 2.2: struct-typed ports are supported with the bare interface.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task StructPort {\n" +
            "    struct Pair { u8 a; }\n" +
            "    in Pair din;\n" +
            "    out Pair dout;\n" +
            "    void loop() { Pair p = din.read(); dout.write(p); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_AS_PORT_TYPE);
        assertTrue("Bare struct ports must be accepted", errors.isEmpty());
    }

    @Test
    public void testNonBareStructPortAccepted() throws Exception {
        // Tier 2.4: non-bare struct ports (push/stream/confirm) are now
        // supported — the first flattened field carries the shared handshake,
        // the rest are bare data. So the old bare-only sentinel must not fire.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task PushStructPort {\n" +
            "    struct Pair { u8 a; }\n" +
            "    in push Pair data;\n" +
            "    out u8 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Non-bare struct ports must now be accepted",
            errorsWithCode(result, ERR_STRUCT_AS_PORT_TYPE).isEmpty());
    }

    @Test
    public void testPrimitivePortsAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task PrimitivePorts {\n" +
            "    struct Pair { u8 a; }\n" +
            "    in u8 x;\n" +
            "    out u32 q;\n" +
            "    void loop() { q.write(0); idle(1); }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_AS_PORT_TYPE);
        assertTrue("No struct-port sentinel on primitive ports", errors.isEmpty());
    }

    // ==================== STRUCT_WHOLE_VALUE_USE ====================

    @Test
    public void testSameTypeStructAssignmentAccepted() throws Exception {
        // Tier 2.1: a whole-struct copy between same-typed structs is now
        // supported (lowers to a field-wise copy), so it must NOT fire the
        // whole-value sentinel — both the initializer and the reassignment form.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task StructAssign {\n" +
            "    struct Pair { u8 a; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        p.a = 1;\n" +
            "        Pair p2 = p;\n" +
            "        Pair p3;\n" +
            "        p3 = p2;\n" +
            "        q.write(0); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertTrue("Same-type whole-struct copy must be accepted", errors.isEmpty());
    }

    @Test
    public void testMismatchedStructCopyRejected() throws Exception {
        // A whole-struct copy between DIFFERENT struct types is still rejected —
        // field pairing by name would silently mis-lower.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task MismatchCopy {\n" +
            "    struct Pair { u8 a; }\n" +
            "    struct Duo { u8 a; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        Duo d;\n" +
            "        p.a = 1;\n" +
            "        d = p;\n" +
            "        q.write(0); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertFalse("Expected sentinel on mismatched-type struct copy", errors.isEmpty());
    }

    @Test
    public void testWholeStructWriteArgRejected() throws Exception {
        // Passing a whole struct as a port-write argument is still unsupported
        // in v1 (struct ports are Tier 2.2) — must keep firing the sentinel.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task StructWriteArg {\n" +
            "    struct Pair { u8 a; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        p.a = 1;\n" +
            "        q.write(p); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertFalse("Expected whole-struct sentinel on `q.write(p)`", errors.isEmpty());
    }

    @Test
    public void testFieldAccessAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task FieldAccess {\n" +
            "    struct Pair { u8 lo; u16 hi; }\n" +
            "    out u32 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        p.lo = 1;\n" +
            "        p.hi = 2;\n" +
            "        q.write(p.hi); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertTrue("No whole-struct sentinel on `.field` access", errors.isEmpty());
    }

    @Test
    public void testWholeSubStructValueRejected() throws Exception {
        // Tier 2.3: nested leaf access is fine, but using a whole sub-struct as
        // a value (`Inner i = o.lo;`) is not supported in v1 — reject cleanly.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task SubStructValue {\n" +
            "    struct Inner { u8 a; }\n" +
            "    struct Outer { Inner lo; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Outer o;\n" +
            "        o.lo.a = 1;\n" +
            "        Inner i = o.lo;\n" +
            "        q.write(i.a); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertFalse("Expected whole-value sentinel on `Inner i = o.lo;`", errors.isEmpty());
    }

    @Test
    public void testStructDeclarationOnlyAccepted() throws Exception {
        Module result = parseHelper.parse(
            "package test;\n" +
            "task StructDeclOnly {\n" +
            "    struct Pair { u8 a; }\n" +
            "    out u8 q;\n" +
            "    void loop() {\n" +
            "        Pair p;\n" +
            "        p.a = 5;\n" +
            "        q.write(p.a); idle(1);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        // `Pair p;` parses as a StatementVariable, not an ExpressionVariable, so
        // it must not trigger the whole-value sentinel.
        List<Diagnostic> errors = errorsWithCode(result, ERR_STRUCT_WHOLE_VALUE_USE);
        assertTrue("Declaration `Pair p;` must not fire whole-value sentinel",
            errors.isEmpty());
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
