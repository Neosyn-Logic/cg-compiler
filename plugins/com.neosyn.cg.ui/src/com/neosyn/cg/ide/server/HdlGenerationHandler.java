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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.util.SimTargetSelector;
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

            // Target vendor (S181): vendor-specific generation rules. Absent = generic = today's HDL.
            String targetVendor = params.getTargetVendor();
            targetVendor = targetVendor == null || targetVendor.isEmpty()
                    ? ICodeGenerator.TARGET_GENERIC : targetVendor.toLowerCase();
            if (!ICodeGenerator.TARGET_VENDORS.contains(targetVendor)) {
                result.setSuccess(false);
                result.setMessage("Unknown target vendor: " + params.getTargetVendor()
                        + " (expected one of " + String.join(", ", ICodeGenerator.TARGET_VENDORS) + ")");
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

            // `generateIRForFile` below only ever ADDS to <project>/.ir — nothing removes
            // an entry whose source has since been renamed or deleted, and
            // `loadAllIrEntities` then loads EVERY .ir in the directory. So a deleted
            // entity keeps being re-emitted as HDL, run after run, behind a "Success!"
            // banner. Measured by renaming one task: the old module reappeared in the
            // output tree with a FRESH mtime, and clearing verilog-gen did not help —
            // only deleting .ir did. See TRAPS T67.
            //
            // Cleaning first is safe because the loop below regenerates IR for EVERY .cg
            // in scope (this project plus its cg.deps roots), so nothing still reachable
            // from a source file can be lost. What it does drop is precisely the IR of a
            // file that no longer exists — the IR that must not be trusted. This mirrors
            // what the project-wide generateIR() path has always done; the per-file path
            // used here was the anomaly.
            ServerUtils.cleanDirectory(irDir);
            Files.createDirectories(Paths.get(irDir));

            // IR first and the blocking check after it -- the order is load-bearing (checking first broke
            // cross-file resolution in 8 projects, TRAPS T180) -- but a transform crash on code that check
            // then refuses (a bool added into a u4: NullPointerException) must not be printed FIRST, ahead
            // of the real errors. Its lines are held here and released below.
            irHandler.holdTransformPrints();
            for (String cgUri : cgUris) {
                try {
                    List<String> irFiles = irHandler.generateIRForFile(cgUri, irDir);
                    allIrFiles.addAll(irFiles);
                } catch (Exception e) {
                    ServerUtils.debugLog("[HDL] Error generating IR: " + e.getMessage());
                }
            }

            ServerUtils.debugLog("[HDL] Generated " + allIrFiles.size() + " IR files");

            // Hard-fail on compile errors (syntax, linking, or a BLOCKING validation finding) in
            // any file whose HDL this run emits. A swallowed parse error silently drops a design
            // body, and a blocking finding marks code no back end builds correctly, so without this
            // the backend emits a degenerate or wrong module and still reports "Success!".
            //
            // Directory form: every .cg beneath the directory the caller named (TRAPS T62 -- the
            // check used to be skipped there). File form: every .cg of the file's PROJECT, because
            // the loop above made IR for all of them and every IR entity is emitted below. It used
            // to check the named file only, so a sibling with an impossible cast came out as
            // Verilog behind "Success!" and one with an untyped port crashed the emitter (TRAPS
            // T170) -- and `generate Top.cg` accepted what `generate .` refused. Validating the
            // whole project measured as free: UDP 2.60 s vs 2.68 s, RiscvSoC 4.02 s vs 4.09 s.
            // cg.deps roots outside the project/directory are NOT validated: a dependency is
            // another project, checked by its own build.
            Path targetPath = Paths.get(filePath).toAbsolutePath().normalize();
            boolean targetIsDirectory = Files.isDirectory(targetPath);
            Path scope = targetIsDirectory ? targetPath : Paths.get(projectRoot).toAbsolutePath().normalize();
            String namedUri = targetIsDirectory ? null : targetPath.toUri().toString();
            List<String> targetUris = new ArrayList<>();
            if (namedUri != null) {
                targetUris.add(namedUri);   // first, so its errors are listed first
            }
            for (String cgUri : cgUris) {
                String cgPath = ServerUtils.convertUriToPath(cgUri);
                if (cgPath == null) {
                    continue;
                }
                Path p = Paths.get(cgPath).toAbsolutePath().normalize();
                if (p.startsWith(scope) && !p.equals(targetPath)) {
                    targetUris.add(cgUri);
                }
            }

            List<String> targetErrors = new ArrayList<>();
            Set<String> filesWithErrors = new LinkedHashSet<>();
            for (String targetUri : targetUris) {
                ValidationHelper.ValidationResult targetValidation =
                        irHandler.validateFileResult(targetUri);
                // Non-blocking advisories (e.g. stricter-than-backend type narrowing): print them
                // but don't fail the build. File form prints the named file's only, as before --
                // the other files are checked for what BLOCKS, not re-reviewed.
                if (namedUri == null || targetUri.equals(namedUri)) {
                    for (String warn : targetValidation.getWarnings()) {
                        System.err.println("[neosyn] warning: " + warn);
                    }
                }
                if (!targetValidation.getErrors().isEmpty()) {
                    String errPath = ServerUtils.convertUriToPath(targetUri);
                    filesWithErrors.add(errPath == null
                            ? targetUri : Paths.get(errPath).getFileName().toString());
                    targetErrors.addAll(targetValidation.getErrors());
                }
            }
            if (!targetErrors.isEmpty()) {
                String named = targetPath.getFileName().toString();
                boolean onlyNamed = namedUri != null && filesWithErrors.size() == 1
                        && filesWithErrors.contains(named);
                result.setSuccess(false);
                result.setErrors(new ArrayList<>(targetErrors));
                result.setMessage(onlyNamed
                        ? "Cannot generate HDL for " + named + ": " + targetErrors.size()
                          + " compile error(s) — fix them first."
                        : "Cannot generate HDL: " + targetErrors.size() + " compile error(s) in "
                          + filesWithErrors.size() + " file(s) (" + String.join(", ", filesWithErrors)
                          + ") — fix them first"
                          + (namedUri != null ? "; generate builds every file of the project." : "."));
                for (String err : targetErrors) {
                    System.err.println("[neosyn] " + err);
                }
                // Nothing is emitted, and a transform crash here is the refused code's consequence, not
                // a second error: drop it (generate-ir skips such a file's IR altogether, T180).
                irHandler.releaseTransformPrints(false);
                ServerUtils.debugLog("[HDL] Aborting: " + targetErrors.size()
                        + " compile error(s) in " + filesWithErrors.size() + " file(s)");
                return result;
            }
            irHandler.releaseTransformPrints(true);

            // After the compile-error check, not before it: with no IR at all, the message used to be only
            // "No IR files generated - check for compilation errors" and the errors themselves were never
            // printed (TRAPS T61: `y cannot be resolved` went unsaid).
            if (allIrFiles.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No IR files generated - check for compilation errors");
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

            // A C⏚ entity is namespaced by its package, but Verilog has ONE FLAT
            // global module namespace (and VHDL one entity namespace per library), and
            // both backends name the emitted module by SIMPLE name. Two entities with
            // the same simple name in different packages therefore emit two different
            // `module Foo` into a single output tree — a deliverable no Verilog tool
            // will compile: verilator binds one and reports PINNOTFOUND on the other's
            // pins; iverilog reports "already been declared in this scope". Until now
            // that shipped silently behind "Success! Generated N files."
            //
            // This is the only point that sees every name about to be emitted with
            // nothing yet written, so the check belongs here rather than in a backend.
            // It also catches ghost entities resurrected from stale .ir whose source no
            // longer exists, which is how the collision reached three shipped IP cores.
            Map<String, Set<String>> bySimpleName = new LinkedHashMap<>();
            for (Entity entity : allEntities) {
                String simple = entity.getSimpleName();
                String qualified = entity.getName();
                if (simple == null || qualified == null) {
                    continue;
                }
                bySimpleName.computeIfAbsent(simple, k -> new LinkedHashSet<>()).add(qualified);
            }
            List<String> nameCollisions = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : bySimpleName.entrySet()) {
                if (entry.getValue().size() > 1) {
                    nameCollisions.add(entry.getKey() + "': " + String.join(", ", entry.getValue()));
                }
            }
            if (!nameCollisions.isEmpty()) {
                List<String> collisionErrors = new ArrayList<>();
                for (String collision : nameCollisions) {
                    String msg = "Duplicate HDL module name '" + collision
                            + ". C⏚ namespaces these by package, but Verilog and VHDL do not: "
                            + "both would emit the same module name into one output tree, which no "
                            + "Verilog tool can compile. Rename one of the entities. (If one of the "
                            + "names above has no source file any more, delete the project's .ir "
                            + "directory \u2014 it is being re-emitted from stale IR.)";
                    collisionErrors.add(msg);
                    System.err.println("[neosyn] " + msg);
                }
                result.setSuccess(false);
                result.setErrors(collisionErrors);
                result.setMessage("Cannot generate HDL: " + nameCollisions.size()
                        + " duplicate module name(s) across packages \u2014 the output would not compile.");
                ServerUtils.debugLog("[HDL] Aborting: " + nameCollisions.size() + " module-name collision(s)");
                return result;
            }

            // `top`: resolve it before anything is written, so a wrong name costs nothing
            String topName = null;
            if (params.getTop() != null && !params.getTop().isEmpty()) {
                String wanted = params.getTop();
                List<String> matches = new ArrayList<>();
                for (Entity entity : allEntities) {
                    String qualified = entity.getName();
                    if (qualified != null && (qualified.equals(wanted) || qualified.endsWith("." + wanted))) {
                        matches.add(qualified);
                    }
                }
                if (matches.size() != 1) {
                    List<String> names = new ArrayList<>();
                    for (Entity entity : allEntities) {
                        if (entity.getName() != null && !entity.getName().startsWith("std.")) {
                            names.add(entity.getSimpleName());
                        }
                    }
                    java.util.Collections.sort(names);
                    String msg = matches.isEmpty()
                            ? "No entity named '" + wanted + "' to use as top. Entities: " + String.join(", ", names)
                            : "'" + wanted + "' names several entities: " + String.join(", ", matches)
                                    + " \u2014 give the qualified name";
                    result.setSuccess(false);
                    result.getErrors().add(msg);
                    result.setMessage("Cannot generate HDL: " + msg);
                    return result;
                }
                topName = matches.get(0);
            }

            // Up-front backend hook (as fullBuild does): mark direct held-producer stream links
            // across every network BEFORE transforming any entity — here the entities are a flat
            // set, so a leaf actor can be transformed before its parent network. prepare() only
            // acts on DPNs and is idempotent, so calling it for every entity is safe.
            for (Entity entity : allEntities) {
                generator.prepare(entity);
            }

            // TRAPS T70. An entity whose TRANSFORM threw is half-built: the backend will
            // happily print it as a module with the right ports and a body that drives
            // nothing, which elaborates, synthesises and computes nothing -- the same shape
            // as the SHA256 steps found in S173. The IR pass already knows which entities
            // those are; before this it told nobody, so `generate` emitted the dead module
            // and reported "Success! Generated N files." with exit 0.
            //
            // Note this catches a DIFFERENT class from the compile-error hard-fail above
            // (T62): that one rejects the SOURCE before generation, this one rejects an
            // entity that parsed and validated but failed to lower. The guard existed at
            // one site and not its sibling, which is the fourth instance of that pattern
            // this week (T59, T72, T74).
            // Design roots: non-test networks no other non-test entity instantiates. A vendor rule (the reset
            // release synchronizer) changes only these -- never a sub-network, never a testbench. Set on every
            // run, generic included: the generators are reused across requests. "Test" is SimTargetSelector.isTest
            // (property, name, or test package): checking the property alone put synchronizers into TopTest,
            // TestSha, net/tests/TestPhy and the test-only helper intel8088/test/InnerNet.
            Set<String> instantiated = new LinkedHashSet<>();
            for (Entity entity : allEntities) {
                if (entity instanceof com.neosyn.models.dpn.DPN
                        && !com.neosyn.core.util.SimTargetSelector.isTest(entity)) {
                    for (com.neosyn.models.dpn.Instance instance : ((com.neosyn.models.dpn.DPN) entity).getInstances()) {
                        String child = com.neosyn.models.util.BuiltinPortTypeResolver.getEntityTypeFromInstance(instance);
                        if (child != null) {
                            instantiated.add(child);
                        } else {
                            ServerUtils.debugLog("[HDL] design roots: unresolved instance "
                                    + instance.getName() + " in " + entity.getName());
                        }
                    }
                }
            }
            Set<String> designRoots = new LinkedHashSet<>();
            for (Entity entity : allEntities) {
                if (entity instanceof com.neosyn.models.dpn.DPN
                        && !com.neosyn.core.util.SimTargetSelector.isTest(entity)
                        && !instantiated.contains(entity.getName())) {
                    designRoots.add(entity.getName());
                }
            }
            generator.setTarget(targetVendor, designRoots);
            ServerUtils.debugLog("[HDL] target vendor " + targetVendor + ", design roots " + designRoots);

            java.util.Set<String> transformFailed = irHandler.getFailedTransformEntities();
            List<String> failedEntities = new ArrayList<>();
            for (String err : irHandler.getTransformErrors()) {
                if (!result.getErrors().contains(err)) {
                    result.getErrors().add(err);
                }
            }
            for (Entity entity : allEntities) {
                if (transformFailed.contains(entity.getName())) {
                    String summary = entity.getName() + ": skipped — its transform failed "
                        + "(emitting it would produce a module that drives nothing)";
                    System.err.println("[neosyn] " + summary);
                    failedEntities.add(summary);
                    continue;
                }
                try {
                    generator.transform(entity);
                    generator.print(entity);

                    String hdlPath = hdlOutputDir + File.separator +
                        generator.getName().toLowerCase() + "-gen" + File.separator +
                        entity.getName().replace('.', File.separatorChar) + "." +
                        generator.getFileExtension();
                    generatedFiles.add(hdlPath);

                    // Generate testbench for test entities.
                    //
                    // TRAPS T108. This was `simpleName.contains("Test")` -- a substring match on
                    // the NAME, failing open and silently. A design declaring
                    // `properties { test: { terminate: ... } }` under any other name generated
                    // its design files, NO testbench at all, and reported
                    // "Success! Generated N files."
                    //
                    // That refused the product's OWN documented convention: the canonical
                    // testbench pattern names the network `<Design>_test`, which contains no
                    // capital-T "Test". Measured 2026-09-20: 38 such networks across cg-ip-cores
                    // and tools/cg-agent-kit, including all 18 agent-kit example networks, could
                    // not produce a testbench on any released build -- so their iverilog leg had
                    // never run. `Counter_test`, which declares a `test` property AND follows the
                    // convention, produced 8 files and zero .tb.v.
                    //
                    // SimTargetSelector already encodes the rule the rest of the product uses;
                    // the HDL path simply never called it. Deliberately NOT isTest(), whose third
                    // convention (a `test`/`tests` package segment) would emit a testbench for
                    // every helper entity that only lives beside one.
                    if (SimTargetSelector.isTestbench(entity) || SimTargetSelector.isTestByName(entity)) {
                        generator.printTestbench(entity);
                        // TRAPS T76. This used to REBUILD the path by convention --
                        // `<out>/verilog-gen/<entity>.tb.v` -- while printTestbench actually
                        // writes to `<out>/testbench/<entity>.tb.v`. Measured on cg-ip-cores:
                        // 0 *.tb.v under any verilog-gen/, 451 under testbench/. So every
                        // testbench in "Generated N HDL file(s)" was a path that did not
                        // exist, and the IDE's iverilog/GHDL lookup searched the reported
                        // tree and found nothing. Report where it IS; if that ever moves,
                        // the existsSync check makes the report follow rather than lie.
                        String tbRel = entity.getName().replace('.', File.separatorChar)
                            + ".tb." + generator.getFileExtension();
                        String tbInTestbench = hdlOutputDir + File.separator + "testbench"
                            + File.separator + tbRel;
                        String tbInGenDir = hdlOutputDir + File.separator +
                            generator.getName().toLowerCase() + "-gen" + File.separator + tbRel;
                        generatedFiles.add(new java.io.File(tbInTestbench).exists()
                            ? tbInTestbench : tbInGenDir);
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
            if (topName != null) {
                List<String> written = new ArrayList<>();
                for (Entity entity : allEntities) {
                    written.add(entity.getName());
                }
                for (Unit unit : units) {
                    written.add(unit.getName());
                }
                pruneToTop(targetGenDir, generator.getFileExtension(), topName, written, generatedFiles);
            } else {
                pruneUnusedStdFiles(targetGenDir, hdlOutputDir + File.separator + "testbench", generatedFiles);
            }

            // Success now MIRRORS IrGenerationHandler:316 (`setSuccess(allErrors.isEmpty())`).
            // It used to be an unconditional `true`, so a run that skipped entities still
            // reported success and the CLI exited 0 over a design it had rejected (T70).
            result.setSuccess(result.getErrors().isEmpty() && failedEntities.isEmpty());
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
        } finally {
            // no-op unless a path out of the try skipped the release below the blocking check
            irHandler.releaseTransformPrints(true);
        }

        return result;
    }

    /**
     * Removes the standard-library files (under {@code <target>-gen/std/}) that the design does
     * not use. Every entity loaded into the project is printed, the built-in library declarations
     * included, and printing a built-in copies its RTL -- so every output carried SIX std/ files
     * even for a design that used none: (c) Neosyn files the customer did not need and could
     * redistribute by accident (user report), and noise in every file list.
     *
     * A std/ file is kept when its module name appears, as a whole word, in a design file, a
     * testbench, or another kept std/ file (a FIFO pulls in its RAM). A mention in a comment only
     * KEEPS a file, which is safe; a real reference can never be missed.
     *
     * A removed file also leaves {@code generatedFiles}: "Success! Generated N files." and the
     * list under it are read by people and by scripts, and they used to name std/ files that were
     * no longer on disk.
     */
    static void pruneUnusedStdFiles(String targetGenDir, String testbenchDir, List<String> generatedFiles) {
        Path gen = Paths.get(targetGenDir);
        Path std = gen.resolve("std");
        if (!Files.isDirectory(std)) {
            return;
        }
        try {
            List<Path> libs = new ArrayList<>();
            List<Path> roots = new ArrayList<>();
            try (java.util.stream.Stream<Path> all = Files.walk(gen)) {
                all.filter(Files::isRegularFile).forEach(f -> (f.startsWith(std) ? libs : roots).add(f));
            }
            Path tb = Paths.get(testbenchDir);
            if (Files.isDirectory(tb)) {
                try (java.util.stream.Stream<Path> all = Files.walk(tb)) {
                    all.filter(Files::isRegularFile).forEach(roots::add);
                }
            }
            java.util.Set<Path> kept = referenced(roots, libs);
            for (Path f : libs) {
                if (!kept.contains(f)) {
                    Files.deleteIfExists(f);
                    Path removed = f.toAbsolutePath().normalize();
                    generatedFiles.removeIf(s -> Paths.get(s).toAbsolutePath().normalize().equals(removed));
                    ServerUtils.debugLog("[HDL] Unused standard-library file removed: " + f);
                }
            }
            removeEmptyDirs(std, true);
        } catch (IOException e) {
            ServerUtils.debugLog("[HDL] std pruning skipped: " + e.getMessage());
        }
    }

    /**
     * {@code --top}: keeps the top entity's file and what it uses, as {@link #pruneUnusedStdFiles}
     * does for std/, and removes every other file THIS run wrote -- never a file it did not write,
     * since the output directory defaults to the project root. A testbench stays when its entity
     * does. The dropped files also leave {@code generatedFiles}.
     */
    static void pruneToTop(String targetGenDir, String extension, String topName, List<String> written,
            List<String> generatedFiles) {
        Path gen = Paths.get(targetGenDir);
        try {
            List<Path> candidates = new ArrayList<>();
            for (String name : written) {
                Path f = gen.resolve(name.replace('.', File.separatorChar) + "." + extension);
                if (Files.isRegularFile(f)) {
                    candidates.add(f);
                }
            }
            Path std = gen.resolve("std");
            if (Files.isDirectory(std)) {
                try (java.util.stream.Stream<Path> all = Files.walk(std)) {
                    all.filter(Files::isRegularFile).filter(f -> !candidates.contains(f)).forEach(candidates::add);
                }
            }
            Path top = gen.resolve(topName.replace('.', File.separatorChar) + "." + extension);
            java.util.Set<Path> kept = referenced(java.util.Collections.singletonList(top), candidates);
            kept.add(top);
            java.util.Set<String> keptModules = new java.util.HashSet<>();
            for (Path f : kept) {
                keptModules.add(moduleName(f));
            }
            for (Path f : candidates) {
                if (!kept.contains(f)) {
                    Files.deleteIfExists(f);
                }
            }
            // testbenches (<Entity>.tb.<ext>) follow their entity
            for (java.util.Iterator<String> it = generatedFiles.iterator(); it.hasNext();) {
                Path f = Paths.get(it.next());
                String module = moduleName(f);
                if (module.endsWith(".tb")) {
                    module = module.substring(0, module.length() - 3);
                }
                if (!keptModules.contains(module)) {
                    Files.deleteIfExists(f);
                    it.remove();
                }
            }
            removeEmptyDirs(gen, false);
        } catch (IOException e) {
            ServerUtils.debugLog("[HDL] top pruning skipped: " + e.getMessage());
        }
    }

    /**
     * The candidates whose module name appears as a whole word in a root or in another referenced
     * candidate (a FIFO pulls in its RAM). A mention in a comment only KEEPS a file, which is safe; a
     * real reference can never be missed. Module name = file name, as both back ends emit it.
     */
    private static java.util.Set<Path> referenced(List<Path> roots, List<Path> candidates) throws IOException {
        java.util.Map<String, List<Path>> byModule = new java.util.HashMap<>();
        for (Path f : candidates) {
            byModule.computeIfAbsent(moduleName(f), k -> new ArrayList<>()).add(f);
        }
        java.util.Set<Path> kept = new java.util.HashSet<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Deque<Path> todo = new java.util.ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Path next = todo.pop();
            if (!Files.isRegularFile(next)) {
                continue;
            }
            String text = new String(Files.readAllBytes(next), java.nio.charset.StandardCharsets.UTF_8);
            for (java.util.Map.Entry<String, List<Path>> e : byModule.entrySet()) {
                if (!seen.contains(e.getKey())
                        && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(e.getKey()) + "\\b")
                                .matcher(text).find()) {
                    seen.add(e.getKey());
                    kept.addAll(e.getValue());
                    todo.addAll(e.getValue());          // a kept file may need others
                }
            }
        }
        return kept;
    }

    private static String moduleName(Path f) {
        return f.getFileName().toString().replaceFirst("\\.[^.]+$", "");
    }

    /** Drops now-empty directories under the given one, deepest first, and itself if asked. */
    private static void removeEmptyDirs(Path root, boolean itself) throws IOException {
        try (java.util.stream.Stream<Path> dirs = Files.walk(root)) {
            dirs.filter(Files::isDirectory).filter(d -> itself || !d.equals(root))
                    .sorted(java.util.Comparator.reverseOrder()).forEach(d -> {
                try (java.util.stream.Stream<Path> inside = Files.list(d)) {
                    if (!inside.findAny().isPresent()) {
                        Files.delete(d);
                    }
                } catch (IOException ignored) {
                    // leave it
                }
            });
        }
    }
}
