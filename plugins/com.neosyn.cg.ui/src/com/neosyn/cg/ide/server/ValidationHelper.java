/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Inject;
import com.neosyn.cg.validation.IssueCodes;

/**
 * Helper class to run Xtext validation and collect errors/warnings.
 *
 * This runs the @Check validators from ExpressionValidator, StructuralValidator,
 * and CgValidator that don't run automatically in the LSP context.
 */
public class ValidationHelper {

    /**
     * Issue codes whose @Check ERROR diagnostics hard-fail a CLI build
     * ({@code generate}/{@code simulate}). Deliberately a short allowlist: the
     * declarative validators also flag idiomatic narrowing the sim/Verilog
     * backends handle correctly via truncation (e.g. {@code i = i + 1} on a u8,
     * the RISC-V multiplier's u128→u64), so blocking on every @Check error would
     * reject pervasive, valid designs. Codes listed here are genuine silent
     * miscompiles that the backend does NOT handle. Everything else from the
     * validators is surfaced as a non-blocking advisory warning. Extend this set
     * as further real-miscompile checks are vetted.
     */
    private static final Set<String> BLOCKING_ISSUE_CODES = Set.of(
            // A mismatched-width network connection silently zero-extends /
            // truncates on both backends (the S132 check).
            IssueCodes.ERR_PORT_WIDTH_MISMATCH,
            // A bad generic/instantiation argument: a name that isn't a const
            // parameter, or a non-const value where a const is required. The
            // instance is built wrong (or with silent defaults) rather than
            // truncated, so this is a genuine error, not a tolerated narrowing.
            IssueCodes.ERR_GENERIC_ARG_UNKNOWN,
            IssueCodes.ERR_GENERIC_ARG_NOT_CONST);

    private IResourceValidator resourceValidator;

    /**
     * Language-aware diagnostician used to run the declarative {@code @Check}
     * validators. The standalone CLI injector's {@link IResourceValidator}
     * consults a registry that has no {@code CgValidator}, so we run validation
     * through a diagnostician built over a registry that does — and whose
     * {@code createDefaultContext()} sets {@code CURRENT_LANGUAGE_NAME}, without
     * which the language-specific validator is silently skipped. Set by
     * {@code CgLanguageServerImpl}; when null we fall back to the (registry-less)
     * resource validator, preserving the old no-op-on-CLI behaviour.
     */
    private Diagnostician diagnostician;

    public ValidationHelper() {
    }

    @Inject
    public ValidationHelper(IResourceValidator resourceValidator) {
        this.resourceValidator = resourceValidator;
    }

    public void setResourceValidator(IResourceValidator resourceValidator) {
        this.resourceValidator = resourceValidator;
    }

    public void setDiagnostician(Diagnostician diagnostician) {
        this.diagnostician = diagnostician;
    }

    /**
     * Validation result containing errors and warnings.
     *
     * <p>Messages are de-duplicated (order-preserving): a single mistake is
     * otherwise reported many times because the syntax errors are surfaced both
     * by {@code resource.getErrors()} and by the Xtext {@link IResourceValidator}
     * (which re-reports them as Issues), and the lexer emits one identical
     * "no viable alternative" per bad character. Collapsing exact duplicates
     * turns a 31-line cascade from one typo into the handful of distinct
     * problems the author actually has to fix. This affects only the CLI /
     * simulate / generate error listing — editor squiggles use a separate
     * offset-based diagnostic path and keep every individual marker.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public void addError(String error) {
            if (error != null && !errors.contains(error)) {
                errors.add(error);
            }
        }

        public void addWarning(String warning) {
            if (warning != null && !warnings.contains(warning)) {
                warnings.add(warning);
            }
        }
    }

    /**
     * Cheap, syntax-only validation: collect just the resource's parse/link
     * diagnostics ({@code getErrors()}/{@code getWarnings()}), WITHOUT running
     * the declarative {@code @Check} validators. The full {@link #validate}
     * runs the diagnostician, which monomorphizes the whole design — far too
     * expensive to do once per file across a large multi-core project (it made
     * {@code simulate} on an SoC hang). Callers that validate every file in a
     * project use this; the single target file still gets the full {@link
     * #validate}.
     */
    public ValidationResult validateSyntaxOnly(XtextResource resource) {
        ValidationResult result = new ValidationResult();
        if (resource == null) {
            return result;
        }
        for (Resource.Diagnostic diagnostic : resource.getErrors()) {
            result.addError(formatLocation(resource, diagnostic) + diagnostic.getMessage());
        }
        for (Resource.Diagnostic diagnostic : resource.getWarnings()) {
            result.addWarning(formatLocation(resource, diagnostic) + diagnostic.getMessage());
        }
        return result;
    }

    /**
     * Validate an Xtext resource using the registered validators.
     *
     * @param resource The XtextResource to validate
     * @return ValidationResult containing errors and warnings
     */
    public ValidationResult validate(XtextResource resource) {
        ValidationResult result = new ValidationResult();

        if (resource == null) {
            result.addError("Resource is null");
            return result;
        }

        // Check for syntax errors first
        for (Resource.Diagnostic diagnostic : resource.getErrors()) {
            String location = formatLocation(resource, diagnostic);
            result.addError(location + diagnostic.getMessage());
        }

        for (Resource.Diagnostic diagnostic : resource.getWarnings()) {
            String location = formatLocation(resource, diagnostic);
            result.addWarning(location + diagnostic.getMessage());
        }

        // Run the declarative @Check validators. Preferred path: a language-aware
        // diagnostician whose registry contains CgValidator (see field doc). It
        // already runs the full CgPackage validator composite (incl. the EMF
        // EObjectValidator), so we don't separately invoke Diagnostician.INSTANCE.
        if (diagnostician != null) {
            if (!resource.getContents().isEmpty()) {
                try {
                    // Validate into our own diagnostic chain with our language-aware
                    // context. We deliberately avoid Diagnostician.validate(EObject),
                    // which builds a default diagnostic via an EMF resource-bundle
                    // string lookup ("_UI_DiagnosticRoot_diagnostic") that is absent
                    // from the shaded standalone jar and throws MissingResourceException.
                    org.eclipse.emf.common.util.BasicDiagnostic chain =
                            new org.eclipse.emf.common.util.BasicDiagnostic();
                    diagnostician.validate(resource.getContents().get(0), chain,
                            diagnostician.createDefaultContext());
                    collectCheckDiagnostics(chain, result);
                } catch (Exception e) {
                    ServerUtils.debugLog("[Validation] Error running diagnostician: " + e.getMessage());
                }
            }
            return result;
        }

        // Legacy fallback (no language-aware diagnostician wired): the standalone
        // resource validator finds no CgValidator, so this runs zero @Checks, but
        // it preserves the prior behaviour for any caller that doesn't set one.
        if (resourceValidator != null) {
            try {
                List<Issue> issues = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
                for (Issue issue : issues) {
                    String message = formatIssue(issue);
                    switch (issue.getSeverity()) {
                        case ERROR:
                            result.addError(message);
                            break;
                        case WARNING:
                            result.addWarning(message);
                            break;
                        case INFO:
                            result.addWarning("[Info] " + message);
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                ServerUtils.debugLog("[Validation] Error running validators: " + e.getMessage());
            }
        }

        if (!resource.getContents().isEmpty()) {
            try {
                Diagnostic diagnostic = Diagnostician.INSTANCE.validate(resource.getContents().get(0));
                collectDiagnostics(diagnostic, result);
            } catch (Exception e) {
                ServerUtils.debugLog("[Validation] Error running EMF validation: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Format a resource diagnostic location.
     */
    private String formatLocation(Resource resource, Resource.Diagnostic diagnostic) {
        String fileName = resource.getURI().lastSegment();
        if (diagnostic.getLine() > 0) {
            return fileName + ":" + diagnostic.getLine() + ": ";
        }
        return fileName + ": ";
    }

    /**
     * Format an Xtext validation issue.
     */
    private String formatIssue(Issue issue) {
        StringBuilder sb = new StringBuilder();

        if (issue.getUriToProblem() != null) {
            String fileName = issue.getUriToProblem().lastSegment();
            if (fileName != null) {
                sb.append(fileName);
            }
        }

        if (issue.getLineNumber() != null && issue.getLineNumber() > 0) {
            sb.append(":").append(issue.getLineNumber());
        }

        if (sb.length() > 0) {
            sb.append(": ");
        }

        sb.append(issue.getMessage());

        return sb.toString();
    }

    /**
     * Recursively collect EMF diagnostics.
     */
    private void collectDiagnostics(Diagnostic diagnostic, ValidationResult result) {
        if (diagnostic.getSeverity() == Diagnostic.ERROR) {
            result.addError(diagnostic.getMessage());
        } else if (diagnostic.getSeverity() == Diagnostic.WARNING) {
            result.addWarning(diagnostic.getMessage());
        }

        for (Diagnostic child : diagnostic.getChildren()) {
            collectDiagnostics(child, result);
        }
    }

    /**
     * Recursively collect declarative {@code @Check} diagnostics, classifying
     * each by whether it should block a CLI build. An ERROR-severity diagnostic
     * blocks (goes to {@code errors}) only when its Xtext issue code is in
     * {@link #BLOCKING_ISSUE_CODES} — a genuine silent miscompile; every other
     * validator finding (stricter-than-backend type narrowing, warnings) is
     * recorded as a non-blocking advisory ({@code warnings}) so the customer
     * still sees it without their build breaking on idiomatic code.
     */
    private void collectCheckDiagnostics(Diagnostic diagnostic, ValidationResult result) {
        int severity = diagnostic.getSeverity();
        if (severity == Diagnostic.ERROR) {
            String issueCode = (diagnostic instanceof AbstractValidationDiagnostic)
                    ? ((AbstractValidationDiagnostic) diagnostic).getIssueCode()
                    : null;
            if (issueCode != null && BLOCKING_ISSUE_CODES.contains(issueCode)) {
                result.addError(diagnostic.getMessage());
            } else {
                result.addWarning(diagnostic.getMessage());
            }
        } else if (severity == Diagnostic.WARNING || severity == Diagnostic.INFO) {
            result.addWarning(diagnostic.getMessage());
        }

        for (Diagnostic child : diagnostic.getChildren()) {
            collectCheckDiagnostics(child, result);
        }
    }
}
