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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import com.neosyn.core.ICodeGenerator;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Unit;

/**
 * Handles HDL (Verilog/VHDL) generation from IR files.
 */
public class HdlGenerationHandler {

    private final ICodeGenerator verilogGenerator;
    private final IrGenerationHandler irHandler;
    private final IrLoader irLoader;

    @FunctionalInterface
    public interface IrLoader {
        org.eclipse.emf.ecore.resource.ResourceSet loadAllIrEntities(String irDir);
    }

    public HdlGenerationHandler(ICodeGenerator verilogGenerator,
                                IrGenerationHandler irHandler, IrLoader irLoader) {
        this.verilogGenerator = verilogGenerator;
        this.irHandler = irHandler;
        this.irLoader = irLoader;
    }

    /**
     * Generate HDL for a project.
     */
    public GenerateResult generate(GenerateParams params) {
        GenerateResult result = new GenerateResult();
        List<String> generatedFiles = new ArrayList<>();

        try {
            irHandler.clearTrackingState();
            ServerUtils.debugLog("[HDL] generate() called for: " + params.getUri());

            // Select generator
            String target = params.getTarget();
            if (target == null || target.isEmpty()) {
                target = "verilog";
            }
            target = target.toLowerCase();

            if ("vhdl".equals(target)) {
                result.setSuccess(false);
                result.setMessage("VHDL output is part of the commercial Neosyn "
                        + "distribution. The open-source compiler targets Verilog.");
                return result;
            }
            ICodeGenerator generator = verilogGenerator;

            if (generator == null) {
                result.setSuccess(false);
                result.setMessage("Generator not available for target: " + target);
                return result;
            }

            // Get file path and project root
            String fileUri = params.getUri();
            if (fileUri == null || fileUri.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No file URI provided");
                return result;
            }

            String filePath = ServerUtils.convertUriToPath(fileUri);
            if (filePath == null) {
                result.setSuccess(false);
                result.setMessage("Invalid file URI");
                return result;
            }

            String projectRoot = ServerUtils.findProjectRoot(filePath);
            ServerUtils.debugLog("[HDL] Project root: " + projectRoot);

            String irDir = projectRoot + File.separator + ".ir";

            // Honor --output / outputDirectory if the caller supplied one;
            // otherwise default to <projectRoot>/{verilog,vhdl}-gen via the
            // generator's internal `<target>-gen/` suffix on projectRoot.
            String hdlOutputDir;
            String overrideDir = params.getOutputDirectory();
            if (overrideDir != null && !overrideDir.isEmpty()) {
                File overrideFile = new File(overrideDir);
                if (!overrideFile.isAbsolute()) {
                    overrideFile = new File(projectRoot, overrideDir);
                }
                hdlOutputDir = overrideFile.getAbsolutePath();
                ServerUtils.debugLog("[HDL] Output dir override: " + hdlOutputDir);
            } else {
                hdlOutputDir = projectRoot;
            }

            // Clean existing HDL output
            String targetGenDir = hdlOutputDir + File.separator + target + "-gen";
            ServerUtils.cleanDirectory(targetGenDir);

            generator.setOutputFolder(hdlOutputDir);

            // Generate IR for all .cg files — this project plus every project it
            // cross-references via cg.deps (no source copying), so a SoC can pull
            // in sibling core packages.
            List<String> allIrFiles = new ArrayList<>();
            List<String> cgUris = ServerUtils.collectCgUris(ServerUtils.resolveSourceRoots(projectRoot));
            ServerUtils.debugLog("[HDL] Found " + cgUris.size() + " .cg files across source roots");

            // Pre-load and resolve every .cg file so cross-file proxy references
            // (e.g. a Counter task's `count_t` typedef defined in a sibling
            // Definitions bundle) are linked before per-file IR generation runs —
            // otherwise the typedef stays an unresolved proxy with no
            // eContainer/eResource and IR Vars end up with null types.
            irHandler.preloadAndResolve(cgUris);

            for (String cgUri : cgUris) {
                try {
                    List<String> irFiles = irHandler.generateIRForFile(cgUri, irDir);
                    allIrFiles.addAll(irFiles);
                } catch (Exception e) {
                    ServerUtils.debugLog("[HDL] Error generating IR: " + e.getMessage());
                }
            }

            ServerUtils.debugLog("[HDL] Generated " + allIrFiles.size() + " IR files");

            if (allIrFiles.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No IR files generated - check for compilation errors");
                return result;
            }

            // Hard-fail if the FILE being generated has compile errors (syntax,
            // linking, or validation). A swallowed parse error silently drops the
            // design body, so without this the HDL backend would emit a
            // degenerate/empty module and still report "Success! Generated N
            // files." Errors in *unrelated* project files don't block generation.
            // Mirrors the target-file check the simulate path performs.
            String targetFileName = Paths.get(filePath).getFileName().toString();
            ValidationHelper.ValidationResult targetValidation = irHandler.validateFileResult(
                    Paths.get(filePath).toUri().toString());
            // Non-blocking advisories (e.g. stricter-than-backend type narrowing):
            // print them but don't fail the build.
            for (String warn : targetValidation.getWarnings()) {
                System.err.println("[neosyn] warning: " + warn);
            }
            List<String> targetErrors = targetValidation.getErrors();
            if (!targetErrors.isEmpty()) {
                result.setSuccess(false);
                result.setErrors(new ArrayList<>(targetErrors));
                result.setMessage("Cannot generate HDL for " + targetFileName + ": "
                        + targetErrors.size() + " compile error(s) — fix them first "
                        + "(a parse error silently drops the design body).");
                for (String err : targetErrors) {
                    System.err.println("[neosyn] " + err);
                }
                ServerUtils.debugLog("[HDL] Aborting: " + targetErrors.size()
                        + " compile error(s) in " + targetFileName);
                return result;
            }

            // Load all IR entities
            org.eclipse.emf.ecore.resource.ResourceSet sharedResourceSet = irLoader.loadAllIrEntities(irDir);

            // Collect entities and units
            Set<Entity> allEntities = new LinkedHashSet<>();
            Set<Unit> units = new LinkedHashSet<>();

            for (Resource res : sharedResourceSet.getResources()) {
                if (!res.getContents().isEmpty()) {
                    EObject obj = res.getContents().get(0);
                    if (obj instanceof Unit) {
                        units.add((Unit) obj);
                    } else if (obj instanceof Entity) {
                        allEntities.add((Entity) obj);
                    }
                }
            }

            ServerUtils.debugLog("[HDL] Found " + allEntities.size() + " entities, " + units.size() + " units");

            // Generate HDL for each entity
            List<String> failedEntities = new ArrayList<>();
            for (Entity entity : allEntities) {
                try {
                    generator.transform(entity);
                    generator.print(entity);

                    String hdlPath = hdlOutputDir + File.separator +
                        generator.getName().toLowerCase() + "-gen" + File.separator +
                        entity.getName().replace('.', File.separatorChar) + "." +
                        generator.getFileExtension();
                    generatedFiles.add(hdlPath);

                    // Generate testbench for test entities
                    String simpleName = entity.getSimpleName();
                    if (simpleName != null && simpleName.contains("Test")) {
                        generator.printTestbench(entity);
                        String tbPath = hdlOutputDir + File.separator +
                            generator.getName().toLowerCase() + "-gen" + File.separator +
                            entity.getName().replace('.', File.separatorChar) + ".tb." +
                            generator.getFileExtension();
                        generatedFiles.add(tbPath);
                    }
                } catch (Exception e) {
                    ServerUtils.debugLog("[HDL] Error processing " + entity.getName() + ": " + e.getMessage());
                    // Without this, "Success! Generated N files." hides the fact
                    // that some entities silently produced no .v / .vhd — and the
                    // network instantiating them ends up with phantom modules.
                    String summary = entity.getName() + ": " + e.getClass().getSimpleName()
                            + " — " + e.getMessage();
                    System.err.println("[neosyn] HDL emit error in " + summary);
                    if (System.getProperty("neosyn.debug.hdl") != null) {
                        e.printStackTrace();
                    }
                    failedEntities.add(summary);
                    result.getErrors().add(summary);
                }
            }

            // Process units
            for (Unit unit : units) {
                try {
                    generator.transform(unit);
                    generator.print(unit);
                } catch (Exception e) {
                    ServerUtils.debugLog("[HDL] Error processing unit: " + e.getMessage());
                }
            }

            generator.copyLibraries();

            result.setSuccess(true);
            String msg = "Generated " + generatedFiles.size() + " HDL file(s)";
            if (!failedEntities.isEmpty()) {
                msg += "; " + failedEntities.size() + " entity/entities skipped due to compiler errors (see stderr)";
            }
            result.setMessage(msg);
            result.setGeneratedFiles(generatedFiles);
            ServerUtils.debugLog("[HDL] Completed: " + generatedFiles.size() + " files, " + failedEntities.size() + " failed");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error generating HDL: " + e.getMessage());
            ServerUtils.debugLog("[HDL] ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        return result;
    }
}
