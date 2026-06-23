/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.resource.XtextResource;

import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Entity;

/**
 * Handles IR (Intermediate Representation) generation from C⏚ source files.
 */
public class IrGenerationHandler {

    private final IInstantiator instantiator;
    private final ResourceProvider resourceProvider;
    private final TransformationHandler transformationHandler;
    private ValidationHelper validationHelper;

    // Track serialized entities to prevent re-serialization
    private final Set<String> serializedEntityNames = new HashSet<>();

    @FunctionalInterface
    public interface ResourceProvider {
        XtextResource getResource(URI uri);
    }

    public IrGenerationHandler(IInstantiator instantiator, ResourceProvider resourceProvider) {
        this.instantiator = instantiator;
        this.resourceProvider = resourceProvider;
        this.transformationHandler = new TransformationHandler(instantiator);
        this.validationHelper = new ValidationHelper();
    }

    public void setValidationHelper(ValidationHelper helper) {
        this.validationHelper = helper;
    }

    /**
     * Clear tracking state for fresh generation.
     */
    public void clearTrackingState() {
        serializedEntityNames.clear();
        transformationHandler.clearTrackingState();
    }

    /**
     * Validate a single .cg file and return its compile errors (syntax,
     * linking, and @Check validation) as formatted strings. Returns an empty
     * list if the file is clean or the resource cannot be loaded.
     *
     * <p>Used by the HDL path to hard-fail when the file being generated has
     * compile errors — without it, a parse error silently drops the design
     * body and "Success! Generated N files." reports a degenerate/empty module.
     * Mirrors the target-file check the simulate path already performs.
     */
    public List<String> validateFile(String fileUri) {
        return validateFileResult(fileUri).getErrors();
    }

    /**
     * Validate a single .cg file and return the full result: build-blocking
     * {@code errors} (syntax/linking + the allowlisted genuine-miscompile
     * {@code @Check} codes) and non-blocking advisory {@code warnings} (every
     * other validator finding). Returns an empty result if the file is clean,
     * the resource cannot be loaded, or no validation helper is wired.
     */
    public ValidationHelper.ValidationResult validateFileResult(String fileUri) {
        if (validationHelper == null) {
            return new ValidationHelper.ValidationResult();
        }
        try {
            XtextResource resource = resourceProvider.getResource(URI.createURI(fileUri));
            if (resource == null) {
                return new ValidationHelper.ValidationResult();
            }
            return validationHelper.validate(resource);
        } catch (Exception e) {
            ServerUtils.debugLog("[IR] validateFile failed for " + fileUri + ": " + e.getMessage());
            return new ValidationHelper.ValidationResult();
        }
    }

    /**
     * Pre-load every .cg URI into the shared resource set and resolve all
     * cross-file proxies. Required before per-file IR generation so that
     * `import` references (typedef constants, sibling bundles) point at
     * loaded EObjects instead of unresolved proxies — otherwise downstream
     * `Typer.caseTypedef` sees a proxy typedef with null eContainer and the
     * IR Var ends up with a null Type.
     */
    public void preloadAndResolve(List<String> uris) {
        org.eclipse.emf.ecore.resource.ResourceSet sharedRS = null;
        for (String uri : uris) {
            try {
                XtextResource r = resourceProvider.getResource(URI.createURI(uri));
                if (r != null && sharedRS == null) {
                    sharedRS = r.getResourceSet();
                }
            } catch (Exception e) {
                ServerUtils.debugLog("[IR] preloadAndResolve failed for " + uri + ": " + e.getMessage());
            }
        }
        if (sharedRS != null) {
            try {
                org.eclipse.emf.ecore.util.EcoreUtil.resolveAll(sharedRS);
            } catch (Exception e) {
                ServerUtils.debugLog("[IR] preloadAndResolve resolveAll failed: " + e.getMessage());
            }
        }
    }

    /**
     * Generate IR for all .cg files in a project.
     */
    public GenerateResult generateIR(GenerateIRParams params) {
        GenerateResult result = new GenerateResult();
        List<String> generatedFiles = new ArrayList<>();

        try {
            clearTrackingState();
            ServerUtils.debugLog("[IR] generateIR() called");

            // Get the file URI to find project root
            String fileUri = params.getUri();
            if (fileUri == null || fileUri.isEmpty()) {
                if (params.getUris() != null && !params.getUris().isEmpty()) {
                    fileUri = params.getUris().get(0);
                } else if (params.getProjectUri() != null && !params.getProjectUri().isEmpty()) {
                    fileUri = params.getProjectUri();
                }
            }

            if (fileUri == null || fileUri.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No file or project URI provided");
                return result;
            }

            String filePath = ServerUtils.convertUriToPath(fileUri);
            if (filePath == null) {
                result.setSuccess(false);
                result.setMessage("Invalid URI");
                return result;
            }

            // Find all .cg files starting from the workspace folder
            Path searchPath = Paths.get(filePath);
            if (!Files.isDirectory(searchPath)) {
                searchPath = searchPath.getParent();
            }

            List<String> allCgUris;
            try (Stream<Path> paths = Files.walk(searchPath)) {
                allCgUris = paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".cg"))
                     .filter(p -> !p.toString().contains(File.separator + "."))
                     .map(p -> p.toUri().toString())
                     .collect(java.util.stream.Collectors.toList());
            }

            ServerUtils.debugLog("[IR] Found " + allCgUris.size() + " .cg files starting from " + searchPath);

            if (allCgUris.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No .cg files found in project");
                return result;
            }

            // Use first .cg file to determine project root
            String firstCgPath = ServerUtils.convertUriToPath(allCgUris.get(0));
            String projectRoot = ServerUtils.findProjectRoot(firstCgPath);
            ServerUtils.debugLog("[IR] Project root: " + projectRoot);

            if (projectRoot == null) {
                result.setSuccess(false);
                result.setMessage("No valid C⏚ project found.");
                return result;
            }

            // IR output directory is at project root
            String outputDir = projectRoot + File.separator + ".ir";
            ServerUtils.debugLog("[IR] Output directory: " + outputDir);

            // Clean the .ir directory before regenerating
            ServerUtils.cleanDirectory(outputDir);
            Files.createDirectories(Paths.get(outputDir));

            // Primary set: the files discovered from the request (whole project
            // when simulate passes a projectUri; the input file's subtree when
            // generate-ir is given a single file) — unchanged from before.
            Path projectPath = Paths.get(projectRoot);
            List<String> urisToProcess = allCgUris.stream()
                .filter(uri -> {
                    String uriPath = ServerUtils.convertUriToPath(uri);
                    return uriPath != null && Paths.get(uriPath).startsWith(projectPath);
                })
                .collect(java.util.stream.Collectors.toList());

            // Plus every project this one cross-references via a cg.deps manifest
            // (no source copying): walk the dependency roots and add their .cg
            // files so an `import` of a sibling project's package resolves like
            // an intra-project import.
            List<String> sourceRoots = ServerUtils.resolveSourceRoots(projectRoot);
            if (sourceRoots.size() > 1) {
                List<String> depRoots = new ArrayList<>(sourceRoots.subList(1, sourceRoots.size()));
                for (String depUri : ServerUtils.collectCgUris(depRoots)) {
                    if (!urisToProcess.contains(depUri)) {
                        urisToProcess.add(depUri);
                    }
                }
                ServerUtils.debugLog("[IR] Added dependency roots: " + depRoots);
            }

            ServerUtils.debugLog("[IR] Compiling " + urisToProcess.size() + " .cg files");

            // Pre-load every .cg file into the shared resource set BEFORE running
            // per-file transformation. Cross-file references (e.g. `import` of a
            // bundle's constants from a sibling file) are resolved by the Xtext
            // scope provider searching the resource set; if a target file hasn't
            // been loaded yet when a source file is validated/transformed, the
            // proxy stays unresolved and downstream scheduling NPEs because
            // `VarRef.getVariable()` returns null.
            preloadAndResolve(urisToProcess);

            // Process each file and collect validation errors/warnings
            List<String> allErrors = new ArrayList<>();
            List<String> allWarnings = new ArrayList<>();
            // Transform errors (per-entity IR-gen failures) were previously only
            // printed to stderr and lost — collect them so callers (simulate,
            // generateIR) can surface them instead of "succeeding" on a broken IR.
            transformationHandler.clearTransformErrors();

            for (String uri : urisToProcess) {
                try {
                    // Cheap per-file check only (syntax/link). The expensive
                    // declarative @Check validation (which monomorphizes the
                    // whole design) runs ONCE on the target file via
                    // validateFileResult — running it per file hangs simulate on
                    // a large multi-core project.
                    URI emfUri = URI.createURI(uri);
                    XtextResource resource = resourceProvider.getResource(emfUri);
                    if (resource != null && validationHelper != null) {
                        ValidationHelper.ValidationResult validationResult = validationHelper.validateSyntaxOnly(resource);
                        allErrors.addAll(validationResult.getErrors());
                        allWarnings.addAll(validationResult.getWarnings());
                    }

                    // Generate IR
                    List<String> generated = generateIRForFile(uri, outputDir);
                    generatedFiles.addAll(generated);
                    ServerUtils.debugLog("[IR] Generated " + generated.size() + " IR files for " + uri);
                } catch (Exception e) {
                    allErrors.add("Error processing " + uri + ": " + e.getMessage());
                    ServerUtils.debugLog("[IR] Error generating IR for " + uri + ": " + e.getMessage());
                }
            }

            // Fold in any transform errors collected during IR generation.
            allErrors.addAll(transformationHandler.getTransformErrors());

            // Set result with validation info
            result.setErrors(allErrors);
            result.setWarnings(allWarnings);
            result.setSuccess(allErrors.isEmpty());

            // Surface the collected errors on stderr so the CLI ("Generation
            // completed with N error(s)") is actionable instead of opaque.
            for (String err : allErrors) {
                System.err.println("[neosyn] " + err);
            }

            if (allErrors.isEmpty()) {
                result.setMessage("Generated " + generatedFiles.size() + " IR file(s)" +
                    (allWarnings.isEmpty() ? "" : " with " + allWarnings.size() + " warning(s)"));
            } else {
                result.setMessage("Generation completed with " + allErrors.size() + " error(s)");
            }
            result.setGeneratedFiles(generatedFiles);
            ServerUtils.debugLog("[IR] generateIR() completed: " + generatedFiles.size() + " files, " +
                allErrors.size() + " errors, " + allWarnings.size() + " warnings");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error generating IR: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Transform a single file's module to IR in memory, WITHOUT serializing any
     * {@code .ir} files to disk. Callers can then read the live IR (e.g. an
     * Actor's real FSM via {@code instantiator.forEachMapping}). Returns the
     * transformed {@link Module}, or {@code null} if the URI is not a C⏚ module.
     *
     * <p>Used by the FSM View so it renders the same state machine the HDL
     * backend emits, rather than a source-text approximation.
     */
    public Module transformInMemory(String fileUri) {
        // Preload every sibling .cg file in the project and resolve cross-file
        // proxies BEFORE transforming, exactly as generateIR does. Without this,
        // a file that `import`s another (e.g. a network instantiating a task
        // from a sibling file) leaves the imported entity an unresolved proxy
        // and scheduling NPEs on VarRef.getVariable() == null.
        try {
            List<String> projectUris = collectProjectCgUris(fileUri);
            if (!projectUris.isEmpty()) {
                preloadAndResolve(projectUris);
            }
        } catch (Exception e) {
            ServerUtils.debugLog("[IR] transformInMemory preload failed: " + e.getMessage());
        }

        URI uri = URI.createURI(fileUri);
        XtextResource resource = resourceProvider.getResource(uri);
        if (resource == null || resource.getContents().isEmpty()) {
            return null;
        }
        EObject root = resource.getContents().get(0);
        if (!(root instanceof Module)) {
            return null;
        }
        Module module = (Module) root;
        try {
            org.eclipse.emf.ecore.util.EcoreUtil.resolveAll(resource.getResourceSet());
        } catch (Exception e) {
            ServerUtils.debugLog("[IR] transformInMemory resolveAll failed: " + e.getMessage());
        }
        clearTrackingState();
        instantiator.clearData();
        instantiator.updateDirect(module);
        transformationHandler.transformModule(module);
        return module;
    }

    /**
     * Collect every {@code .cg} file URI in the project that owns {@code fileUri}
     * (project root resolved via {@link ServerUtils#findProjectRoot}, falling
     * back to the file's directory). Mirrors generateIR's discovery.
     */
    private List<String> collectProjectCgUris(String fileUri) throws IOException {
        String filePath = ServerUtils.convertUriToPath(fileUri);
        if (filePath == null) {
            return new ArrayList<>();
        }
        Path searchPath = Paths.get(filePath);
        if (!Files.isDirectory(searchPath)) {
            searchPath = searchPath.getParent();
        }
        String projectRoot = ServerUtils.findProjectRoot(filePath);
        Path base = (projectRoot != null) ? Paths.get(projectRoot) : searchPath;
        if (base == null) {
            return new ArrayList<>();
        }
        try (Stream<Path> paths = Files.walk(base)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".cg"))
                    .filter(p -> !p.toString().contains(File.separator + "."))
                    .map(p -> p.toUri().toString())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * Generate IR for a single C⏚ file.
     */
    public List<String> generateIRForFile(String fileUri, String outputDir) throws IOException {
        List<String> generatedFiles = new ArrayList<>();

        ServerUtils.debugLog("[IR] generateIRForFile called for: " + fileUri);

        URI uri = URI.createURI(fileUri);
        XtextResource resource = resourceProvider.getResource(uri);

        if (resource == null || resource.getContents().isEmpty()) {
            ServerUtils.debugLog("[IR] Resource is null or empty for: " + uri);
            return generatedFiles;
        }

        EObject root = resource.getContents().get(0);
        if (!(root instanceof Module)) {
            ServerUtils.debugLog("[IR] Root is not a Module, skipping");
            return generatedFiles;
        }

        Module module = (Module) root;
        ServerUtils.debugLog("[IR] Module package: " + module.getPackage());

        // Clear and update instantiator
        instantiator.clearData();
        instantiator.updateDirect(module);

        // Run transformation pipeline
        transformationHandler.transformModule(module);

        // Serialize IR for each entity
        for (CgEntity cxEntity : module.getEntities()) {
            ServerUtils.debugLog("[IR] Processing CgEntity: " + cxEntity.getName());
            instantiator.forEachMapping(cxEntity, entity -> {
                if (entity == null) return;
                try {
                    String irFile = serializeEntity(entity, outputDir);
                    if (irFile != null) {
                        generatedFiles.add(irFile);
                    }
                } catch (Exception e) {
                    ServerUtils.debugLog("[IR] Error serializing entity: " + e.getMessage());
                }
            });

            // Process inline tasks inside networks
            if (cxEntity instanceof Network) {
                Network network = (Network) cxEntity;
                for (Inst inst : network.getInstances()) {
                    if (inst.getEntity() == null && inst.getTask() != null) {
                        Task inlineTask = inst.getTask();
                        instantiator.forEachMapping(inlineTask, entity -> {
                            if (entity == null) return;
                            try {
                                String irFile = serializeEntity(entity, outputDir);
                                if (irFile != null) {
                                    generatedFiles.add(irFile);
                                }
                            } catch (Exception e) {
                                ServerUtils.debugLog("[IR] Error serializing inline task: " + e.getMessage());
                            }
                        });
                    }
                }
            }
        }

        return generatedFiles;
    }

    /**
     * Serialize an IR entity to a file.
     */
    public String serializeEntity(Entity entity, String outputDir) {
        if (entity == null || entity.eResource() == null) {
            return null;
        }

        String entityName = entity.getName();
        if (entityName == null || entityName.isEmpty()) {
            entityName = "unknown";
        }

        // Skip if already serialized
        if (serializedEntityNames.contains(entityName)) {
            String relativePath = entityName.replace('.', File.separatorChar) + ".ir";
            return Paths.get(outputDir, relativePath).toString();
        }

        try {
            // Log pre-serialization instruction count
            if (entity instanceof com.neosyn.models.dpn.Actor) {
                com.neosyn.models.dpn.Actor actor = (com.neosyn.models.dpn.Actor) entity;
                int total = 0;
                for (com.neosyn.models.dpn.Action action : actor.getActions()) {
                    for (com.neosyn.models.ir.Block block : action.getBody().getBlocks()) {
                        if (block instanceof com.neosyn.models.ir.BlockBasic) {
                            total += ((com.neosyn.models.ir.BlockBasic) block).getInstructions().size();
                        }
                    }
                }
                ServerUtils.debugLog("[IR] Pre-serialize instructions: " + total + " for " + entityName);
            }

            // Ensure all references are contained
            ensureAllReferencesContained(entity);
            preserveInstanceEntityTypes(entity);

            // Log post-cleanup instruction count
            if (entity instanceof com.neosyn.models.dpn.Actor) {
                com.neosyn.models.dpn.Actor actor = (com.neosyn.models.dpn.Actor) entity;
                int total = 0;
                for (com.neosyn.models.dpn.Action action : actor.getActions()) {
                    for (com.neosyn.models.ir.Block block : action.getBody().getBlocks()) {
                        if (block instanceof com.neosyn.models.ir.BlockBasic) {
                            total += ((com.neosyn.models.ir.BlockBasic) block).getInstructions().size();
                        }
                    }
                }
                ServerUtils.debugLog("[IR] Post-cleanup instructions: " + total + " for " + entityName);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.util.Map<Object, Object> saveOptions = new java.util.HashMap<>();
            saveOptions.put(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_PROCESS_DANGLING_HREF,
                           org.eclipse.emf.ecore.xmi.XMLResource.OPTION_PROCESS_DANGLING_HREF_DISCARD);
            entity.eResource().save(baos, saveOptions);

            String relativePath = entityName.replace('.', File.separatorChar) + ".ir";
            Path outputPath = Paths.get(outputDir, relativePath);
            Files.createDirectories(outputPath.getParent());

            try (OutputStream fos = new FileOutputStream(outputPath.toFile())) {
                fos.write(baos.toByteArray());
            }

            serializedEntityNames.add(entityName);
            ServerUtils.debugLog("[IR] Serialized: " + outputPath);
            return outputPath.toString();

        } catch (Exception e) {
            ServerUtils.debugLog("[IR] Serialize error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Ensure all Uses/Defs are properly contained to avoid serialization issues.
     */
    private void ensureAllReferencesContained(Entity entity) {
        List<com.neosyn.models.ir.Var> allVars = new ArrayList<>();
        allVars.addAll(entity.getInputs());
        allVars.addAll(entity.getOutputs());
        allVars.addAll(entity.getVariables());

        for (com.neosyn.models.ir.Procedure proc : entity.getProcedures()) {
            allVars.addAll(proc.getLocals());
            allVars.addAll(proc.getParameters());
        }

        if (entity instanceof com.neosyn.models.dpn.DPN) {
            com.neosyn.models.dpn.DPN dpn = (com.neosyn.models.dpn.DPN) entity;
            for (com.neosyn.models.dpn.Instance instance : dpn.getInstances()) {
                for (com.neosyn.models.dpn.Argument arg : instance.getArguments()) {
                    if (arg.getVariable() != null) {
                        allVars.add(arg.getVariable());
                    }
                }
            }
        }

        for (com.neosyn.models.ir.Var var : allVars) {
            List<com.neosyn.models.ir.Use> usesToCheck = new ArrayList<>(var.getUses());
            for (com.neosyn.models.ir.Use use : usesToCheck) {
                if (use.eResource() == null && use.eContainer() == null) {
                    var.getUses().remove(use);
                }
            }

            List<com.neosyn.models.ir.Def> defsToCheck = new ArrayList<>(var.getDefs());
            for (com.neosyn.models.ir.Def def : defsToCheck) {
                if (def.eResource() == null && def.eContainer() == null) {
                    var.getDefs().remove(def);
                }
            }
        }
    }

    /**
     * Store entity type names in instance properties for bytecode generation.
     */
    private void preserveInstanceEntityTypes(Entity entity) {
        if (!(entity instanceof com.neosyn.models.dpn.DPN)) {
            return;
        }

        com.neosyn.models.dpn.DPN dpn = (com.neosyn.models.dpn.DPN) entity;
        for (com.neosyn.models.dpn.Instance instance : dpn.getInstances()) {
            Entity instanceEntity = instance.getEntity();
            if (instanceEntity != null) {
                String entityTypeName = instanceEntity.getName();
                com.google.gson.JsonObject props = instance.getProperties();
                if (props == null) {
                    props = new com.google.gson.JsonObject();
                }
                props.addProperty("entityType", entityTypeName);
                instance.setProperties(props);
            }
        }
    }
}
