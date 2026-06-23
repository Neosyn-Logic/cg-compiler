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
import java.util.Map;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.impl.EValidatorRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.Constants;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.IAcceptor;
import org.eclipse.xtext.validation.AbstractInjectableValidator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.CheckType;
import org.eclipse.xtext.validation.EValidatorRegistrar;
import org.eclipse.xtext.validation.Issue;
import org.eclipse.xtext.validation.ResourceValidatorImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.neosyn.cg.validation.CgValidator;
import com.neosyn.cg.validation.IssueCodes;

import org.eclipse.xtext.diagnostics.Severity;
import java.util.Set;

/**
 * Makes the live editor run the declarative {@link CgValidator} (@Checks:
 * type/width mismatch, port-width, generic-arg, network connectivity, duplicate
 * names, …), not just the parser's syntax errors.
 *
 * <p>Root cause this fixes: the stock {@link ResourceValidatorImpl} path never
 * actually runs the language-specific {@code @Check}s on the C⏚ model (the
 * validation context routing drops {@code CURRENT_LANGUAGE_NAME}), so a
 * {@code u64} assigned to a {@code u32}, a port-width mismatch, an unknown
 * generic arg, etc. never squiggled live — only syntax errors did. The CLI
 * {@code generate}/{@code simulate} path works because it drives a
 * language-aware {@link Diagnostician} directly.
 *
 * <p>Fix: do the same here. We keep the framework's syntax/link validation
 * (via {@code super.validate}) and, on a NORMAL pass, additionally run a
 * {@link Diagnostician} built over a registry that maps {@code CgPackage} to
 * {@code CgValidator}, with {@code CURRENT_LANGUAGE_NAME} injected into the
 * context — exactly what the command path does. Its EMF diagnostics are turned
 * into positioned Xtext {@link Issue}s with the framework's own converter
 * ({@code issueFromEValidatorDiagnostic}), so squiggles land on the right span.
 *
 * <p>Perf: a C⏚ {@code module} is a single source file, so the {@code
 * checkModule} instantiation this triggers is per-file; and we only run it on
 * NORMAL validations (not the FAST pass fired on every keystroke), so typing
 * stays responsive. Best-effort throughout — any failure falls back to the
 * stock (syntax-only) behaviour rather than breaking editor validation.
 */
@Singleton
public class CgResourceValidator extends ResourceValidatorImpl {

    @Inject
    private Provider<EValidatorRegistrar> registrarProvider;

    @Inject
    private Provider<CgValidator> validatorProvider;

    @Inject
    @Named(Constants.LANGUAGE_NAME)
    private String languageName;

    /**
     * @Check issue codes that stay hard ERRORs in the editor. Mirrors
     * {@code ValidationHelper.BLOCKING_ISSUE_CODES}: a genuine silent miscompile
     * the backend does NOT handle. Every other validator ERROR (chiefly the
     * idiomatic narrowing the sim/Verilog backends truncate correctly — e.g. a
     * u9→u8 increment or a u64→u32 multiply low-word) is demoted to a WARNING so
     * valid designs are not flooded with red, exactly as on the CLI.
     */
    private static final Set<String> BLOCKING_ISSUE_CODES = Set.of(
            IssueCodes.ERR_PORT_WIDTH_MISMATCH,
            IssueCodes.ERR_GENERIC_ARG_UNKNOWN,
            IssueCodes.ERR_GENERIC_ARG_NOT_CONST);

    private volatile Diagnostician cgDiagnostician;
    private volatile boolean built = false;

    @Override
    public List<Issue> validate(Resource resource, CheckMode mode, CancelIndicator monitor) {
        List<Issue> issues = new ArrayList<>(super.validate(resource, mode, monitor));

        // Only run the (heavier) declarative @Checks on a NORMAL pass — the FAST
        // pass fires on every keystroke and is meant to stay cheap.
        if (mode == null || !mode.shouldCheck(CheckType.NORMAL)) {
            return issues;
        }

        Diagnostician diagnostician = getCgDiagnostician();
        if (diagnostician == null || resource.getContents().isEmpty()) {
            return issues;
        }
        try {
            final List<Issue> extra = new ArrayList<>();
            IAcceptor<Issue> acceptor = extra::add;
            for (EObject root : resource.getContents()) {
                // Use the 3-arg validate with our own BasicDiagnostic + context.
                // The 1-arg Diagnostician.validate(EObject) builds its root via an
                // EMF resource-bundle string ("_UI_DiagnosticRoot_diagnostic") that
                // is absent from the shaded standalone jar and throws
                // MissingResourceException (mirrors ValidationHelper).
                org.eclipse.emf.common.util.BasicDiagnostic chain =
                        new org.eclipse.emf.common.util.BasicDiagnostic();
                diagnostician.validate(root, chain, diagnostician.createDefaultContext());
                for (Diagnostic child : chain.getChildren()) {
                    issueFromEValidatorDiagnostic(child, acceptor);
                }
            }
            // Avoid duplicating anything super already reported (same line+message).
            for (Issue e : extra) {
                // Demote non-blocking @Check ERRORs to WARNINGs (CLI policy), so
                // idiomatic narrowing doesn't turn valid designs red.
                if (e.getSeverity() == Severity.ERROR
                        && !BLOCKING_ISSUE_CODES.contains(e.getCode())
                        && e instanceof Issue.IssueImpl) {
                    ((Issue.IssueImpl) e).setSeverity(Severity.WARNING);
                }
                boolean dup = false;
                for (Issue s : issues) {
                    if (s.getLineNumber() != null && s.getLineNumber().equals(e.getLineNumber())
                            && s.getMessage() != null && s.getMessage().equals(e.getMessage())) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    issues.add(e);
                }
            }
        } catch (Exception e) {
            ServerUtils.debugLog("[CgResourceValidator] @Check pass failed (falling back to syntax-only): "
                    + e.getMessage());
        }
        return issues;
    }

    /** Lazily build a {@link Diagnostician} that actually runs {@link CgValidator}. */
    private Diagnostician getCgDiagnostician() {
        if (built) {
            return cgDiagnostician;
        }
        synchronized (this) {
            if (built) {
                return cgDiagnostician;
            }
            built = true;
            try {
                // nsURI-tolerant registry: in the standalone/shaded server the
                // live resource's CgPackage can be a DIFFERENT instance than the
                // injector's, and EValidator.Registry keys by identity — so an
                // identity miss must fall back to an nsURI match or the validator
                // is silently not found (0 issues despite correct registration).
                final EValidator.Registry registry = new EValidatorRegistryImpl() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object get(Object key) {
                        Object v = super.get(key);
                        if (v == null && key instanceof org.eclipse.emf.ecore.EPackage) {
                            String ns = ((org.eclipse.emf.ecore.EPackage) key).getNsURI();
                            for (Map.Entry<org.eclipse.emf.ecore.EPackage, Object> e : entrySet()) {
                                if (e.getKey().getNsURI().equals(ns)) {
                                    return e.getValue();
                                }
                            }
                        }
                        return v;
                    }
                };
                EValidatorRegistrar registrar = registrarProvider.get();
                registrar.setRegistry(registry);
                // Registers CgValidator + its @ComposedChecks into the private registry.
                validatorProvider.get().register(registrar);

                final String lang = languageName;
                cgDiagnostician = new Diagnostician(registry) {
                    @Override
                    public Map<Object, Object> createDefaultContext() {
                        Map<Object, Object> context = super.createDefaultContext();
                        // Without this the language-specific @Checks are skipped.
                        context.put(AbstractInjectableValidator.CURRENT_LANGUAGE_NAME, lang);
                        context.put(CheckMode.KEY, CheckMode.ALL);
                        return context;
                    }
                };
            } catch (Exception e) {
                ServerUtils.debugLog("[CgResourceValidator] could not build language-aware "
                        + "diagnostician: " + e.getMessage());
                cgDiagnostician = null;
            }
            return cgDiagnostician;
        }
    }
}
