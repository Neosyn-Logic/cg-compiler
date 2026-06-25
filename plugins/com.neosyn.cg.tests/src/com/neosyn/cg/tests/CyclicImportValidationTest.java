/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_CYCLIC_IMPORT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.xtext.junit4.validation.AssertableDiagnostics;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.neosyn.cg.cg.Module;

/**
 * Regression tests for mutually-importing bundles.
 *
 * <p>Two bundles that import each other form a dependency cycle. Instantiation
 * loads imported bundles recursively, so before Session 119 this recursed until
 * a {@link StackOverflowError} (reproducible via
 * {@code generate --target verilog} on any project containing such a pair).
 * Two changes close the gap:
 * <ul>
 *   <li>{@code InstantiatorImpl} tracks in-progress URIs and bails out of the
 *       re-entrant call, so the generator stays alive (no crash);</li>
 *   <li>{@code CgValidator#checkNoCyclicImport} surfaces the cause as a normal
 *       {@code errCyclicImport} validation error.</li>
 * </ul>
 *
 * <p>The first test exercises both: {@code tester.validate} runs
 * {@code CgValidator#checkModule}, which calls {@code instantiator.update} — so
 * if the guard were missing this test would StackOverflow rather than fail an
 * assertion.
 */
@InjectWith(CgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CyclicImportValidationTest extends AbstractCxTest {

    @Test
    public void testMutualBundleImportReported() throws Exception {
        // Load both halves of the cycle into the shared resource set so the
        // cross-file imports resolve before validation.
        getModule("com/neosyn/test/cyclic/CycleB.cg");
        Module cycleA = getModule("com/neosyn/test/cyclic/CycleA.cg");

        List<Diagnostic> errors = errorsWithCode(cycleA, ERR_CYCLIC_IMPORT);
        assertFalse("Expected an errCyclicImport diagnostic on the mutual-import cycle",
            errors.isEmpty());
        assertTrue("Message should mention the cycle",
            errors.get(0).getMessage().toLowerCase().contains("cycl"));
    }

    @Test
    public void testOneWayImportNotReported() throws Exception {
        getModule("com/neosyn/test/cyclic/OneWayProducer.cg");
        Module consumer = getModule("com/neosyn/test/cyclic/OneWayConsumer.cg");

        List<Diagnostic> errors = errorsWithCode(consumer, ERR_CYCLIC_IMPORT);
        assertTrue("A one-directional import must not be flagged as a cycle",
            errors.isEmpty());
    }

    private List<Diagnostic> errorsWithCode(Module module, String code) {
        assertNotNull(module);
        AssertableDiagnostics diagnostics = tester.validate(module);
        return diagnostics.getDiagnostic().getChildren().stream()
            .filter(d -> d.getSeverity() == Diagnostic.ERROR)
            .filter(d -> d instanceof AbstractValidationDiagnostic
                && code.equals(((AbstractValidationDiagnostic) d).getIssueCode()))
            .collect(Collectors.toList());
    }
}
