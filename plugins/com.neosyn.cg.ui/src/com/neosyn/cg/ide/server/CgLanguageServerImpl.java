/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.models.dpn.DpnPackage;
import com.neosyn.models.ir.impl.IrResourceFactoryImpl;

import static com.neosyn.core.ICoreConstants.FILE_EXT_IR;

/**
 * Extended Language Server implementation with Neosyn-specific methods.
 * Provides FSM/Graph data for visualization and HDL/IR/Simulation capabilities.
 */
@Singleton
public class CgLanguageServerImpl extends LanguageServerImpl implements CgLanguageServer {

    // Static initialization
    static {
        java.util.Map<String, Object> extToFactoryMap = Resource.Factory.Registry.INSTANCE
                .getExtensionToFactoryMap();
        if (extToFactoryMap.get(FILE_EXT_IR) == null) {
            extToFactoryMap.put(FILE_EXT_IR, new IrResourceFactoryImpl());
        }
        @SuppressWarnings("unused")
        Object dpnPackage = DpnPackage.eINSTANCE;
    }

    @Inject
    private IInstantiator instantiator;

    @Inject
    @Named("Verilog")
    private ICodeGenerator verilogGenerator;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private org.eclipse.xtext.validation.IResourceValidator resourceValidator;

    @Inject
    private Provider<org.eclipse.xtext.validation.EValidatorRegistrar> eValidatorRegistrarProvider;

    @Inject
    private Provider<com.neosyn.cg.validation.CgValidator> cgValidatorProvider;

    @Inject
    @Named(org.eclipse.xtext.Constants.LANGUAGE_NAME)
    private String languageName;

    // The standalone CLI injector registers the EMF package but NOT the
    // declarative validator, so the injected ResourceValidatorImpl's
    // diagnostician consults a registry with no CgValidator and runs zero
    // @Checks — a port-width mismatch, generic-arg error, type error etc.
    // silently produced wrong HDL / a passing sim on `generate`/`simulate`.
    // (The LSP server uses a different launcher whose build pipeline registers
    // validators, so editor squiggles were unaffected.) buildCgDiagnostician()
    // constructs a language-aware diagnostician over a registry that has
    // CgValidator; this flag makes that one-time build idempotent.
    private boolean validatorWired = false;

    // Handlers (lazy initialization)
    private IrGenerationHandler irHandler;
    private HdlGenerationHandler hdlHandler;
    private LanguageClient languageClient;

    // Shared XtextResourceSet for the fallback resource path. A single set lets
    // Xtext resolve cross-file proxies (e.g. imported bundle constants); a fresh
    // per-call set leaves every file isolated and cross-file refs unresolved.
    private XtextResourceSet sharedFallbackResourceSet;

    @Override
    public void connect(LanguageClient client) {
        super.connect(client);
        this.languageClient = client;
    }

    private IrGenerationHandler getIrHandler() {
        if (irHandler == null) {
            irHandler = new IrGenerationHandler(instantiator, this::getResource);
            // Set up validation helper with the injected validator, plus a
            // language-aware diagnostician so the CLI path actually runs @Checks.
            ValidationHelper validationHelper = new ValidationHelper(resourceValidator);
            Diagnostician d = buildCgDiagnostician();
            if (d != null) {
                validationHelper.setDiagnostician(d);
            }
            irHandler.setValidationHelper(validationHelper);
        }
        return irHandler;
    }

    /**
     * Build a {@link Diagnostician} that runs the declarative
     * {@link com.neosyn.cg.validation.CgValidator} (and its @ComposedChecks:
     * ImportUri / NamesAreUnique / Expression / Structural / Warning) on a C⏚
     * model. See {@link #validatorWired}. Two pieces the standalone CLI injector
     * does not provide on its own:
     * <ol>
     *   <li>an {@link org.eclipse.emf.ecore.EValidator.Registry} that actually
     *       maps {@code CgPackage} to the validator composite (we register it
     *       ourselves via a fresh registrar), and</li>
     *   <li>a validation context carrying {@code CURRENT_LANGUAGE_NAME} — without
     *       it the language-specific validator is silently skipped (this is the
     *       exact reason {@code IResourceValidator} returned zero issues on the
     *       CLI path). We override {@code createDefaultContext()} to set it,
     *       mirroring Xtext's own {@code ValidatorTester}.</li>
     * </ol>
     * Cached after first build; best-effort — a failure returns null and the
     * ValidationHelper falls back to its (no-@Check) legacy path rather than
     * breaking {@code generate}/{@code simulate}.
     */
    private Diagnostician cgDiagnostician;

    private Diagnostician buildCgDiagnostician() {
        if (validatorWired) {
            return cgDiagnostician;
        }
        validatorWired = true;
        try {
            org.eclipse.xtext.validation.EValidatorRegistrar registrar = eValidatorRegistrarProvider.get();
            // Register into a fresh, private registry so we never mutate shared
            // global validator state.
            final org.eclipse.emf.ecore.EValidator.Registry registry =
                    new org.eclipse.emf.ecore.impl.EValidatorRegistryImpl();
            registrar.setRegistry(registry);
            // Registers CgValidator + its @ComposedChecks into the registry.
            cgValidatorProvider.get().register(registrar);

            final String lang = languageName;
            cgDiagnostician = new Diagnostician(registry) {
                @Override
                public java.util.Map<Object, Object> createDefaultContext() {
                    java.util.Map<Object, Object> context = super.createDefaultContext();
                    context.put(org.eclipse.xtext.validation.AbstractInjectableValidator.CURRENT_LANGUAGE_NAME, lang);
                    context.put(org.eclipse.xtext.validation.CheckMode.KEY, org.eclipse.xtext.validation.CheckMode.ALL);
                    return context;
                }
            };
        } catch (Exception e) {
            ServerUtils.debugLog("[Validation] could not build CgValidator diagnostician: " + e.getMessage());
            cgDiagnostician = null;
        }
        return cgDiagnostician;
    }

    private HdlGenerationHandler getHdlHandler() {
        if (hdlHandler == null) {
            hdlHandler = new HdlGenerationHandler(verilogGenerator,
                    getIrHandler(), this::loadAllIrEntities);
        }
        return hdlHandler;
    }

    private final CgInlayHintService inlayHintService = new CgInlayHintService();

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        return super.initialize(params).thenApply(result -> {
            ServerCapabilities caps = result.getCapabilities();
            // E1: advertise inlay hints (eager labels, no resolve round-trip).
            caps.setInlayHintProvider(Either.forLeft(Boolean.TRUE));
            // E1.5: drop the false-positive signatureHelp trigger — C⏚ has no
            // call-argument lists, so "(" / "," only produced empty popups.
            caps.setSignatureHelpProvider(null);
            return result;
        });
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.createURI(params.getTextDocument().getUri());
                XtextResource resource = getResource(uri);
                if (resource == null) {
                    return Collections.<InlayHint>emptyList();
                }
                return inlayHintService.computeInlayHints(resource);
            } catch (Exception e) {
                return Collections.<InlayHint>emptyList();
            }
        });
    }

    // ========== FSM View ==========

    @Override
    public CompletableFuture<FsmData> getFsm(GetFsmParams params) {
        return CompletableFuture.supplyAsync(() -> {
            FsmData result = new FsmData();

            try {
                // Compile the file to IR in memory and read the REAL FSM the HDL
                // backend emits, so the FSM View matches the generated Verilog
                // state-for-state. (The legacy AST heuristic only ever guessed a
                // setup+loop pair and missed scheduler-split states.)
                Module module = getIrHandler().transformInMemory(params.getUri());
                if (module == null) {
                    result.setError("Not a C⏚ module: " + params.getUri());
                    return result;
                }

                Task targetTask = findTask(module, params.getTaskName());
                if (targetTask == null) {
                    result.setError("No task found"
                            + (params.getTaskName() != null ? " with name: " + params.getTaskName() : ""));
                    return result;
                }
                result.setTaskName(params.getTaskName());

                // Map the task AST node to its compiled Actor and extract the FSM.
                com.neosyn.models.dpn.Actor[] actorHolder = new com.neosyn.models.dpn.Actor[1];
                instantiator.forEachMapping(targetTask, entity -> {
                    if (entity instanceof com.neosyn.models.dpn.Actor) {
                        actorHolder[0] = (com.neosyn.models.dpn.Actor) entity;
                    }
                });

                if (actorHolder[0] == null) {
                    // Could not resolve a compiled actor; signal an error so the
                    // client can fall back to its local source view.
                    result.setError("Could not compile task to IR");
                    return result;
                }

                // Populates states/transitions from the real FSM. An actor with
                // no FSM leaves them empty — the correct "No FSM" outcome.
                FsmGraphExtractor.extractFsmFromActor(actorHolder[0], result);

            } catch (Exception e) {
                result.setError("Error extracting FSM: " + e.getMessage());
            }

            return result;
        });
    }

    // ========== Graph View ==========

    @Override
    public CompletableFuture<GraphData> getGraph(GetGraphParams params) {
        return CompletableFuture.supplyAsync(() -> {
            GraphData result = new GraphData();

            try {
                // Compile the file to IR in memory and read the REAL compiled
                // DPN (the same network the HDL backend traverses), so the
                // Network View matches the generated Verilog instead of
                // approximating connections from source text. The legacy AST
                // path dropped multi-line reads(...)/writes(...) and could not
                // resolve named-entity ports; the client falls back to its
                // local source view if this path reports an error.
                Module module = getIrHandler().transformInMemory(params.getUri());
                if (module == null) {
                    result.setError("Not a C⏚ module: " + params.getUri());
                    return result;
                }

                Network targetNetwork = findNetwork(module, params.getNetworkName());
                if (targetNetwork == null) {
                    result.setError("No network found" + (params.getNetworkName() != null ? " with name: " + params.getNetworkName() : ""));
                    return result;
                }
                result.setNetworkName(targetNetwork.getName());

                // Map the network AST node to its compiled DPN.
                com.neosyn.models.dpn.DPN[] dpnHolder = new com.neosyn.models.dpn.DPN[1];
                instantiator.forEachMapping(targetNetwork, entity -> {
                    if (entity instanceof com.neosyn.models.dpn.DPN) {
                        dpnHolder[0] = (com.neosyn.models.dpn.DPN) entity;
                    }
                });

                if (dpnHolder[0] == null) {
                    result.setError("Could not compile network to IR");
                    return result;
                }

                FsmGraphExtractor.extractGraphFromDpn(dpnHolder[0], targetNetwork, result);

            } catch (Exception e) {
                result.setError("Error extracting graph: " + e.getMessage());
            }

            return result;
        });
    }

    // ========== HDL Generation ==========

    @Override
    public CompletableFuture<GenerateResult> generate(GenerateParams params) {
        return CompletableFuture.supplyAsync(() -> getHdlHandler().generate(params));
    }

    // ========== IR Generation ==========

    @Override
    public CompletableFuture<GenerateResult> generateIR(GenerateIRParams params) {
        return CompletableFuture.supplyAsync(() -> getIrHandler().generateIR(params));
    }

    // ========== Simulation ==========

    @Override
    public CompletableFuture<SimulateResult> simulate(SimulateParams params) {
        // The fast (bytecode) simulator is part of the commercial Neosyn
        // distribution and is not included in the open-source Verilog compiler.
        return CompletableFuture.completedFuture(new SimulateResult(false,
                "Fast simulation is part of the commercial Neosyn distribution and "
                        + "is not available in the open-source Verilog compiler. "
                        + "Use 'generate' to produce Verilog and simulate with your "
                        + "own tool (e.g. Icarus Verilog, Verilator)."));
    }

    // ========== Helper Methods ==========

    /**
     * Find a task in the module by name.
     */
    private Task findTask(Module module, String taskName) {
        for (CgEntity entity : module.getEntities()) {
            if (entity instanceof Task) {
                Task task = (Task) entity;
                if (taskName == null || taskName.equals(task.getName())) {
                    return task;
                }
            }
            if (entity instanceof Network) {
                Network network = (Network) entity;
                for (Inst inst : network.getInstances()) {
                    if (inst.getTask() != null) {
                        Task task = inst.getTask();
                        if (taskName == null || taskName.equals(inst.getName())) {
                            return task;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find a network in the module by name.
     */
    private Network findNetwork(Module module, String networkName) {
        for (CgEntity entity : module.getEntities()) {
            if (entity instanceof Network) {
                Network network = (Network) entity;
                if (networkName == null || networkName.equals(network.getName())) {
                    return network;
                }
            }
        }
        return null;
    }

    /**
     * Get XtextResource for a URI.
     */
    private XtextResource getResource(URI uri) {
        try {
            XtextResource resource = (XtextResource) getWorkspaceManager().getProjectManager(uri)
                .getResource(uri);
            if (resource != null) {
                return resource;
            }
        } catch (Exception e) {
            // Workspace manager not available
        }

        // Fallback: share a single XtextResourceSet across calls so that
        // cross-file proxy references (e.g. `import Bundle.*` constants used
        // by a sibling file) can resolve. Creating a fresh set per call
        // isolates each file and leaves every cross-file reference unresolved.
        try {
            if (sharedFallbackResourceSet == null) {
                sharedFallbackResourceSet = resourceSetProvider.get();
                sharedFallbackResourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
            }
            return (XtextResource) sharedFallbackResourceSet.getResource(uri, true);
        } catch (Exception e) {
            ServerUtils.debugLog("[Resource] Failed to load: " + uri + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Load all IR entities from a directory.
     */
    private ResourceSet loadAllIrEntities(String irDir) {
        ResourceSet resourceSet = new ResourceSetImpl();

        // Register IR factory
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put(FILE_EXT_IR, new IrResourceFactoryImpl());

        Path irPath = Paths.get(irDir);
        if (!Files.exists(irPath)) {
            return resourceSet;
        }

        try (Stream<Path> paths = Files.walk(irPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".ir"))
                 .forEach(p -> {
                     try {
                         URI uri = URI.createFileURI(p.toString());
                         resourceSet.getResource(uri, true);
                     } catch (Exception e) {
                         ServerUtils.debugLog("[IR] Failed to load: " + p);
                     }
                 });
        } catch (Exception e) {
            ServerUtils.debugLog("[IR] Error loading IR files: " + e.getMessage());
        }

        // Load built-in IR from classpath
        loadClasspathResources(resourceSet);

        return resourceSet;
    }

    /**
     * Load built-in IR resources from classpath.
     */
    private void loadClasspathResources(ResourceSet resourceSet) {
        String[] builtinPaths = {
            "builtin-ir/std/mem/SinglePortRAM.ir",
            "builtin-ir/std/mem/DualPortRAM.ir",
            "builtin-ir/std/mem/PseudoDualPortRAM.ir",
            "builtin-ir/std/fifo/SynchronousFIFO.ir",
            "builtin-ir/std/lib/SynchronizerMux.ir"
        };

        for (String path : builtinPaths) {
            try {
                java.net.URL url = getClass().getClassLoader().getResource(path);
                if (url != null) {
                    URI uri = URI.createURI(url.toString());
                    resourceSet.getResource(uri, true);
                    ServerUtils.debugLog("[IR] Loaded classpath: " + path);
                }
            } catch (Exception e) {
                // Ignore - built-in may not be available
            }
        }
    }
}
