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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.linking.impl.XtextLinkingDiagnostic;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextSyntaxDiagnostic;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Inject;
import com.neosyn.cg.validation.BlockingIssueCodes;

/**
 * Helper class to run Xtext validation and collect errors/warnings.
 *
 * This runs the @Check validators from ExpressionValidator, StructuralValidator,
 * and CgValidator that don't run automatically in the LSP context.
 */
public class ValidationHelper {

    /** The codes that block a build: one list, shared with the editor (TRAPS T4). */
    private static final Set<String> BLOCKING_ISSUE_CODES = BlockingIssueCodes.CODES;

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
            if (error == null || errors.contains(error)) {
                return;
            }
            // The same mistake, once plain and once explained ("X cannot be resolved" and
            // "X cannot be resolved: it is declared at the top of Y.cg, ..."): a file linked
            // before its sibling was loaded gets the plain message, and again after, the
            // explained one. Keep only the explained one.
            for (int i = 0; i < errors.size(); i++) {
                String known = errors.get(i);
                if (error.startsWith(known + ":")) {
                    errors.set(i, error);
                    return;
                }
                if (known.startsWith(error + ":")) {
                    return;
                }
            }
            errors.add(error);
        }

        public void addWarning(String warning) {
            if (warning != null && !warnings.contains(warning)) {
                warnings.add(warning);
            }
        }
    }

    /**
     * M3 (model friendliness, 2026-09-30): on a line where the PARSER failed, only its first parse
     * error. Once the parser derails, the rest of that line is cascade -- `in stream u8 in;` gave
     * the right message plus "extraneous input ')'" and "read cannot be resolved", and a model
     * treats all three as real and spends a turn on the two that are not. Fix the first, check
     * again, and a genuine second error on that line comes back.
     *
     * <p>A line the parser accepted keeps EVERY error: its link errors are not cascade. The first
     * version kept one error per line regardless and dropped T123's "declared at the top of X"
     * hint and two of the three unresolved names behind T109's simulate verdict. Unlocated errors
     * and errors of any other kind are always kept.
     */
    static List<Resource.Diagnostic> firstErrorPerLine(List<Resource.Diagnostic> errors) {
        Set<Integer> derailed = new HashSet<>();
        for (Resource.Diagnostic d : errors) {
            if (d instanceof XtextSyntaxDiagnostic && d.getLine() > 0) {
                derailed.add(d.getLine());
            }
        }
        List<Resource.Diagnostic> kept = new ArrayList<>();
        Set<Integer> reported = new HashSet<>();
        for (Resource.Diagnostic d : errors) {
            boolean cascadeKind = d instanceof XtextSyntaxDiagnostic || d instanceof XtextLinkingDiagnostic;
            if (!cascadeKind || !derailed.contains(d.getLine())) {
                kept.add(d);
            } else if (d instanceof XtextSyntaxDiagnostic && reported.add(d.getLine())) {
                kept.add(d);
            }
        }
        return kept;
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
        for (Resource.Diagnostic diagnostic : firstErrorPerLine(resource.getErrors())) {
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
        for (Resource.Diagnostic diagnostic : firstErrorPerLine(resource.getErrors())) {
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
                result.addError(located(diagnostic));
            } else {
                result.addWarning(located(diagnostic));
            }
        } else if (severity == Diagnostic.WARNING || severity == Diagnostic.INFO) {
            result.addWarning(located(diagnostic));
        }

        for (Diagnostic child : diagnostic.getChildren()) {
            collectCheckDiagnostics(child, result);
        }
    }

    /**
     * TRAPS T130: a validator that throws surfaces as Xtext's bare "Error executing EValidator"
     * -- and the jar's log4j config silences CompositeEValidator, so the exception went nowhere
     * while every later check on that object was skipped. Name it where the user sees it.
     */
    static String internalError(Throwable t) {
        if (t == null) {
            return "";
        }
        String where = "";
        for (StackTraceElement frame : t.getStackTrace()) {
            if (frame.getClassName().startsWith("com.neosyn.")) {
                String cls = frame.getClassName();
                where = " at " + cls.substring(cls.lastIndexOf('.') + 1) + "." + frame.getMethodName()
                        + ":" + frame.getLineNumber();
                break;
            }
        }
        return " (internal compiler error: " + t.getClass().getSimpleName()
                + (t.getMessage() != null ? ": " + t.getMessage() : "") + where
                + " -- some checks were skipped; please report this)";
    }

    /**
     * {@code File.cg:LINE: message} for a validator finding, or the bare message when the
     * position cannot be recovered.
     *
     * <p>These were emitted as bare text, which reads fine to a human staring at one file
     * and is nearly useless to a tool: the agent kit parses {@code file:line:} to build
     * structured diagnostics, so an unlocated validator finding reached the caller as
     * "not ok, no diagnostics" -- nothing to act on, which is precisely the state that
     * makes a model rewrite its program instead of fixing the line. The position is on the
     * diagnostic's source EObject, via the node model.
     */
    private static String located(Diagnostic diagnostic) {
        if (diagnostic.getMessage() == null) {
            // the per-file container chain: no finding of its own (and a null result is not printed)
            return null;
        }
        String message = diagnostic.getMessage() + internalError(diagnostic.getException());
        try {
            List<?> data = diagnostic.getData();
            if (data == null || data.isEmpty() || !(data.get(0) instanceof EObject)) {
                return message;
            }
            EObject source = (EObject) data.get(0);
            INode node = NodeModelUtils.findActualNodeFor(source);
            if (node == null) {
                return message;
            }
            String file = null;
            if (source.eResource() != null && source.eResource().getURI() != null) {
                file = source.eResource().getURI().lastSegment();
            }
            return (file == null ? "" : file + ":") + node.getStartLine() + ": " + message;
        } catch (Exception e) {
            return message;   // a diagnostic must never be lost to its own formatting
        }
    }
}
