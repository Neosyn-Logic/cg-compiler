/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_PORT_WIDTH_MISMATCH;
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
 * L3 Generics Tier 3.4 — validation of port-width compatibility on network
 * connections {@code consumer.reads(producer.port)}.
 *
 * Before this check the bytecode sim and the Verilog backend silently
 * zero-extended or truncated a mismatched-width connection, so wiring a 4-bit
 * producer into an 8-bit consumer (e.g. a generic instance specialized to the
 * wrong width) compiled to a latent miscompile. The check runs on the
 * monomorphized DPN, where each port carries its concrete per-instance width,
 * and is therefore equally effective for generic-width ports and fixed-width
 * ports. These tests pin:
 *   - ERR_PORT_WIDTH_MISMATCH fires when connected port widths differ
 *     (both the generic-instance case and the plain fixed-width case);
 *   - it does NOT fire when the widths match.
 * The companion positive fixture is GenPortTest (bytecode + iverilog).
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class PortWidthMismatchValidationTest {

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

    @Test
    public void testGenericWidthMismatchRejected() throws Exception {
        // A 4-bit producer wired into an 8-bit consumer: the same parameterized
        // task specialized to two different widths and wired together.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Producer { const int W = 8; out push uint<W> o;\n" +
            "  void setup() { o.write((uint<W>) 0); } }\n" +
            "task Consumer { const int W = 8; in push uint<W> i;\n" +
            "  void setup() { uint<W> v = i.read; } }\n" +
            "network N {\n" +
            "  p = new Producer({W: 4});\n" +
            "  c = new Consumer({W: 8});\n" +
            "  c.reads(p.o);\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        List<Diagnostic> errors = errorsWithCode(result, ERR_PORT_WIDTH_MISMATCH);
        assertFalse("Expected width-mismatch sentinel on a 4-bit -> 8-bit connection",
            errors.isEmpty());
        assertTrue("Message should report both widths",
            errors.get(0).getMessage().contains("4-bit")
                && errors.get(0).getMessage().contains("8-bit"));
    }

    @Test
    public void testGenericWidthMatchAccepted() throws Exception {
        // Both instances specialized to 8 bits: matched widths, no error.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Producer { const int W = 8; out push uint<W> o;\n" +
            "  void setup() { o.write((uint<W>) 0); } }\n" +
            "task Consumer { const int W = 8; in push uint<W> i;\n" +
            "  void setup() { uint<W> v = i.read; } }\n" +
            "network N {\n" +
            "  p = new Producer({W: 8});\n" +
            "  c = new Consumer({W: 8});\n" +
            "  c.reads(p.o);\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("Width-matched connection must not fire the mismatch sentinel",
            errorsWithCode(result, ERR_PORT_WIDTH_MISMATCH).isEmpty());
    }

    @Test
    public void testFixedWidthMismatchRejected() throws Exception {
        // The check is not generics-specific: a plain u8 -> uint<4> connection is
        // just as silent a miscompile and must also be flagged.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Producer { out push u8 o; void setup() { o.write(0); } }\n" +
            "task Consumer { in push uint<4> i; void setup() { uint<4> v = i.read; } }\n" +
            "network N {\n" +
            "  p = new Producer();\n" +
            "  c = new Consumer();\n" +
            "  c.reads(p.o);\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertFalse("Expected width-mismatch sentinel on an 8-bit -> 4-bit connection",
            errorsWithCode(result, ERR_PORT_WIDTH_MISMATCH).isEmpty());
    }

    @Test
    public void testFixedWidthMatchAccepted() throws Exception {
        // Plain u8 -> u8: matched, no error.
        Module result = parseHelper.parse(
            "package test;\n" +
            "task Producer { out push u8 o; void setup() { o.write(0); } }\n" +
            "task Consumer { in push u8 i; void setup() { u8 v = i.read; } }\n" +
            "network N {\n" +
            "  p = new Producer();\n" +
            "  c = new Consumer();\n" +
            "  c.reads(p.o);\n" +
            "}\n"
        );
        assertNotNull(result);
        assertTrue("No parse errors", result.eResource().getErrors().isEmpty());

        assertTrue("A width-matched fixed-width connection must not fire the sentinel",
            errorsWithCode(result, ERR_PORT_WIDTH_MISMATCH).isEmpty());
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
