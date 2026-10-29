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

import com.google.gson.JsonElement;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.generator.CombinationalVisitor;
import com.neosyn.cg.generator.FsmBeautifier;
import com.neosyn.cg.generator.LoadStoreReplacer;
import com.neosyn.cg.generator.SideEffectRemover;
import com.neosyn.cg.generator.VariablePromoter;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.ActorTransformer;
import com.neosyn.cg.internal.compiler.CommentTranslator;
import com.neosyn.cg.internal.compiler.FunctionTransformer;
import com.neosyn.cg.internal.scheduler.CombinationalScheduler;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.IfScheduler;
import com.neosyn.cg.internal.scheduler.Schedule;
import com.neosyn.core.transformations.ProcedureTransformation;
import com.neosyn.core.transformations.SchedulerTransformation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.ir.transform.StoreOnceTransformation;

import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_LOOP_DEPRECATED;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static com.neosyn.cg.CgConstants.NAME_SETUP_DEPRECATED;

/**
 * Handles transformation of C⏚ AST to IR (Intermediate Representation).
 * This includes scheduling and code generation for tasks, networks, and bundles.
 */
public class TransformationHandler {

    private final IInstantiator instantiator;

    // Track transformed actors to prevent re-transformation
    private final Set<String> transformedActorNames = new HashSet<>();

    // Transform errors collected this run (entity name included) so the caller
    // can surface them instead of losing them to stderr — see IrGenerationHandler.
    private final List<String> transformErrors = new ArrayList<>();

    /**
     * Names of the entities whose transform threw, so a later pass can SKIP them rather
     * than emit them. A failed transform leaves the actor half-built: the HDL backend
     * happily prints it as a module with the right ports and a body that drives nothing,
     * which elaborates and synthesises and computes nothing (TRAPS T70, and the same shape
     * as the SHA256 steps found in S173). Carried as names, not parsed back out of the
     * message strings.
     */
    private final Set<String> failedEntityNames = new HashSet<>();

    public List<String> getTransformErrors() {
        return transformErrors;
    }

    /** Entities whose transform threw this run. See {@link #failedEntityNames}. */
    public Set<String> getFailedEntityNames() {
        return failedEntityNames;
    }

    public void clearTransformErrors() {
        transformErrors.clear();
        failedEntityNames.clear();
    }

    /**
     * Non-null while generate holds the transform CRASH lines back until its blocking check has
     * run. Generate must build IR BEFORE that check (validating first breaks cross-file
     * resolution: 16 census failures, TRAPS T180), so a crash on code the front end then refuses
     * (a bool added into a u4) used to be printed FIRST, ahead of the real errors. The deadlock
     * error is a finding of its own, not a crash, and is still printed at once.
     */
    private List<String> heldPrints;

    /** Hold the transform crash lines instead of printing them; see {@link #heldPrints}. */
    public void holdPrints() {
        heldPrints = new ArrayList<>();
    }

    /** Stop holding: print the held lines, or drop them (the run is refused for blocking errors). */
    public void releasePrints(boolean print) {
        List<String> held = heldPrints;
        heldPrints = null;
        if (held == null) {
            return;
        }
        for (String line : held) {
            if (print) {
                System.err.println(line);
            } else {
                ServerUtils.debugLog("[Transform] not printed (blocking errors refused the run): " + line);
            }
        }
    }

    private void printError(String line) {
        if (heldPrints != null) {
            heldPrints.add(line);
        } else {
            System.err.println(line);
        }
    }

    public TransformationHandler(IInstantiator instantiator) {
        this.instantiator = instantiator;
    }

    /**
     * Clear tracking state for fresh transformation.
     */
    public void clearTrackingState() {
        transformedActorNames.clear();
    }

    /**
     * Transform all entities in a module.
     */
    public void transformModule(Module module) {
        ServerUtils.debugLog("[Transform] transformModule() called");

        // Do not transform a source the front end has ALREADY rejected. The parser or linker has
        // already reported it precisely, with a file and a line; running the IR pipeline on the
        // wreckage only printed a crash naming the compiler's internals ABOVE the real diagnostic
        // (a NullPointerException on an undeclared name, an "Unresolved reference '?.?'"). The
        // generate path refuses a file with compile errors anyway, so nothing downstream wanted
        // this IR. Validation is unaffected (it runs through ValidationHelper on its own path).
        org.eclipse.emf.ecore.resource.Resource res = module.eResource();
        if (res != null && !res.getErrors().isEmpty()) {
            ServerUtils.debugLog("[Transform] SKIPPED: " + res.getErrors().size()
                    + " unresolved error(s) in " + res.getURI()
                    + " — the front end already reported them.");
            return;
        }

        // Translate comments
        new CommentTranslator(instantiator).doSwitch(module);

        for (CgEntity cxEntity : module.getEntities()) {
            ServerUtils.debugLog("[Transform] Processing entity: " + cxEntity.getName());

            instantiator.forEachMapping(cxEntity, entity -> transformEntity(cxEntity, entity));
        }

        // Also transform every specialization created during this transform. A
        // specialization is filed under its TEMPLATE cx entity, so a cross-file
        // specialization (e.g. LoadKey specialized from a parameterized Expander in
        // another file) is keyed under a template NOT in this module, and the loop above
        // never reaches it -- leaving the specialized actor with ports but NO compiled
        // BODY, so at simulation it never fires and the design deadlocks. Dispatch on the
        // TEMPLATE's kind, exactly as the module loop does; transformTask's
        // already-transformed guard makes this idempotent for same-module specializations.
        // Mirrors the specialization serialization pass in IrGenerationHandler.
        instantiator.forEachSpecialization(this::transformEntity);

        ServerUtils.debugLog("[Transform] transformModule() completed");
    }

    /**
     * Transforms one IR entity using its source Cx entity to pick the pipeline.
     */
    private void transformEntity(CgEntity cxEntity, Entity entity) {
        if (entity == null) {
            ServerUtils.debugLog("[Transform] Entity mapping is null, skipping");
            return;
        }

        switch (cxEntity.eClass().getClassifierID()) {
        case CgPackage.BUNDLE:
            transformBundle((Bundle) cxEntity, (Unit) entity);
            break;

        case CgPackage.NETWORK:
            transformNetwork((Network) cxEntity, (DPN) entity);
            break;

        case CgPackage.TASK:
            transformTask((Task) cxEntity, (Actor) entity);
            break;
        }
    }

    /**
     * Transform a bundle to a unit.
     */
    public void transformBundle(Bundle bundle, Unit unit) {
        ServerUtils.debugLog("[Transform] transformBundle: " + bundle.getName());
        transformConstantFunctions(unit, CgUtil.getVariables(bundle));
        new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(unit);
    }

    /**
     * Transform constant functions to IR procedures.
     */
    private void transformConstantFunctions(Entity entity, Iterable<Variable> variables) {
        for (Variable variable : variables) {
            if (CgUtil.isFunctionConstant(variable)) {
                new FunctionTransformer(instantiator, entity).doSwitch(variable);
            }
        }
    }

    /**
     * Transform inner tasks of a network.
     */
    public void transformNetwork(Network network, DPN dpn) {
        ServerUtils.debugLog("[Transform] transformNetwork: " + network.getName());
        List<Instance> inner = new ArrayList<>();
        for (Inst inst : network.getInstances()) {
            Task task = inst.getTask();
            if (task != null) {
                Instance instance = instantiator.getMapping(dpn, inst);
                if (instance != null && instance.getEntity() instanceof Actor) {
                    Actor actor = (Actor) instance.getEntity();
                    transformTask(task, actor);
                    inner.add(instance);
                }
            }
        }
        // after ALL inner tasks are scheduled: the check needs the peer's actions too
        for (Instance instance : inner) {
            checkRequestReplyInOneCycle(dpn, instance, (Actor) instance.getEntity());
        }
    }

    /**
     * TRAPS T31: {@code c.write(x); v = r.read();} in one scheduled action, where {@code c} goes
     * to instance X and {@code r} comes back from the same X. The action's guard waits for
     * {@code r} before it may send {@code c}, and X answers only after it receives {@code c}, so it
     * never fires: {@code simulate} printed nothing until a 10-million-cycle timeout, and the HDL
     * was equally dead, while {@code generate} said "Success!". Flagged only when all three hold:
     * the source wrote first (recorded by Schedule.read), the FINAL action still holds both
     * ports, and the request's consumer is the reply's producer. `fence;` splits the action.
     */
    private void checkRequestReplyInOneCycle(DPN dpn, Instance instance, Actor actor) {
        for (Schedule.ReadAfterWrite raw : Schedule.readsAfterWrite(actor)) {
            Endpoint replyFrom = dpn.getIncoming(new Endpoint(instance, raw.read));
            if (replyFrom == null || replyFrom.getInstance() == null) {
                continue;
            }
            Instance peer = replyFrom.getInstance();
            if (!(peer.getEntity() instanceof Actor)) {
                continue;
            }
            for (Port out : raw.writtenBefore) {
                if (!out.isSynchronous() || !sameAction(actor, out, raw.read)) {
                    continue;
                }
                for (Endpoint to : dpn.getOutgoing(new Endpoint(instance, out))) {
                    if (to.getInstance() != peer
                            || !replyNeedsRequest((Actor) peer.getEntity(), replyFrom.getPort(), to.getPort())) {
                        continue;
                    }
                    String file = raw.ref.eResource() != null && raw.ref.eResource().getURI() != null
                            ? raw.ref.eResource().getURI().lastSegment() : "?";
                    org.eclipse.xtext.nodemodel.INode node =
                            org.eclipse.xtext.nodemodel.util.NodeModelUtils.findActualNodeFor(raw.ref);
                    String message = file + ":" + (node == null ? "?" : String.valueOf(node.getStartLine()))
                            + ": `" + raw.read.getName() + ".read()` waits in the same cycle for the answer"
                            + " to `" + out.getName() + ".write(...)`, and both go through `"
                            + peer.getName() + "`, which only sees the request after this cycle -- this"
                            + " never completes (deadlock). Put `fence;` between the write and the read.";
                    transformErrors.add(message);
                    failedEntityNames.add(actor.getName());
                    // printed like the other transform errors: the result's error list alone reached
                    // the user only as "skipped -- its transform failed"
                    System.err.println("[neosyn] error: " + message);
                    return;
                }
            }
        }
    }

    /**
     * Whether the peer can only produce {@code reply} by consuming {@code request}: EVERY action
     * that writes the reply also reads the request. SoC's bus slave writes its rdata in setup()
     * and on every cycle whatever its select does, so the decoder that selects it and reads it
     * back in one cycle is fine -- flagging it (the first version did) blocked a working design.
     */
    private static boolean replyNeedsRequest(Actor peer, Port reply, Port request) {
        boolean writesReply = false;
        for (Action action : peer.getActions()) {
            if (action.getOutputPattern() != null && action.getOutputPattern().contains(reply)) {
                writesReply = true;
                if (action.getInputPattern() == null || !action.getInputPattern().contains(request)) {
                    return false;
                }
            }
        }
        return writesReply;
    }

    private static boolean sameAction(Actor actor, Port out, Port in) {
        for (Action action : actor.getActions()) {
            if (action.getOutputPattern() != null && action.getInputPattern() != null
                    && action.getOutputPattern().contains(out) && action.getInputPattern().contains(in)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transform a task to an actor.
     * Runs the full scheduling and transformation pipeline including
     * combinational vs synchronous task detection.
     */
    public void transformTask(Task task, Actor actor) {
        String actorName = actor.getName();
        ServerUtils.debugLog("[Transform] transformTask: " + task.getName() + " -> " + actorName);

        // Skip if already transformed
        if (transformedActorNames.contains(actorName)) {
            ServerUtils.debugLog("[Transform]   SKIPPING (already transformed): " + actorName);
            return;
        }
        transformedActorNames.add(actorName);

        try {
            // Clear existing actions and reset FSM
            actor.getActions().clear();
            actor.setFsm(null);
            Schedule.forgetReadsAfterWrite(actor);

            // Transform constant functions
            transformConstantFunctions(actor, CgUtil.getVariables(task));

            // Find setup and loop functions
            Variable setup = null;
            Variable loop = null;
            for (Variable function : CgUtil.getFunctions(task)) {
                String name = function.getName();
                if (NAME_SETUP.equals(name) || NAME_SETUP_DEPRECATED.equals(name)) {
                    setup = function;
                } else if (NAME_LOOP.equals(name) || NAME_LOOP_DEPRECATED.equals(name)) {
                    loop = function;
                }
            }

            // Check if actor is combinational (no clocks OR explicit type: "combinational")
            JsonElement clocks = actor.getProperties().get("clocks");
            boolean isCombinational = clocks != null && clocks.isJsonArray() && clocks.getAsJsonArray().isEmpty();

            // Also check for explicit type: "combinational"
            JsonElement type = actor.getProperties().get("type");
            if (type != null && type.isJsonPrimitive() && "combinational".equals(type.getAsString())) {
                isCombinational = true;
            }

            // Check for experimental scheduler
            JsonElement schedulerProp = actor.getProperties().get("scheduler");
            if (schedulerProp != null && "experimental".equals(schedulerProp.getAsString())) {
                ServerUtils.debugLog("[Transform]   Using experimental scheduler");
                new com.neosyn.cg.internal.scheduler.experimental.ExperimentalScheduler(instantiator, actor).schedule(setup, loop);
            } else if (isCombinational) {
                ServerUtils.debugLog("[Transform]   Using combinational scheduler (no clocks)");
                new CombinationalScheduler(instantiator, actor).schedule(setup, loop);
            } else {
                ServerUtils.debugLog("[Transform]   Using standard scheduler");
                CycleScheduler scheduler = new CycleScheduler(instantiator, actor);
                scheduler.schedule(setup, loop);
                new IfScheduler(instantiator, actor).visit();
            }

            // Transform AST to IR
            new ActorTransformer(instantiator, actor).visit();
            // TRAPS T58: `din.last` outside the cycle of a read of `din` reads a stale wire
            for (String warning : com.neosyn.cg.internal.compiler.FramedLastCheck.lastOutsideRead(actor)) {
                System.err.println("[neosyn] warning: " + warning);
            }
            // TRAPS T160: a combinational task's actions fire every cycle -- an unguarded forward of a
            // handshaked input presents its output as valid every cycle (what killed the MAC receive path, T158)
            if (isCombinational) {
                for (String warning : com.neosyn.cg.internal.compiler.CombinationalForwardCheck.unguardedForwards(actor)) {
                    System.err.println("[neosyn] warning: " + warning);
                }
            }
            ServerUtils.debugLog("[Transform]   Post-ActorTransformer: " + countInstructions(actor) + " instructions for " + actorName);

            if (isCombinational) {
                // Combinational tasks: beautify FSM (sets procedure names), then clear FSM
                new FsmBeautifier().visit(actor);
                new CombinationalVisitor().visit(actor);
                actor.setFsm(null);
                ServerUtils.debugLog("[Transform]   Cleared FSM for combinational actor");
            } else {
                // Synchronous tasks: full post-processing
                new FsmBeautifier().visit(actor);
                new VariablePromoter(actor.getVariables()).visit(actor);
                new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(actor);
                new CombinationalVisitor().visit(actor);
                new SchedulerTransformation(new StoreOnceTransformation()).doSwitch(actor);
                new SchedulerTransformation(new SideEffectRemover()).doSwitch(actor);
            }

            ServerUtils.debugLog("[Transform]   Completed: " + actor.getActions().size() + " actions, " + countInstructions(actor) + " instructions for " + actorName);

        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            ServerUtils.debugLog("[Transform] ERROR transforming " + actorName + ": " + e.getClass().getName() + ": " + e.getMessage() + "\n" + sw.toString());
            // Surface failures the way the LSP / CLI user actually sees them.
            // Without this, IR-gen NPEs silently produce malformed .ir files
            // and the downstream HDL pass drops the entity without telling anyone.
            transformErrors.add("Transform error in " + actorName + ": "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());
            failedEntityNames.add(actorName);
            printError("[neosyn] Transform error in " + actorName + ": "
                    + e.getClass().getSimpleName() + " — " + e.getMessage()
                    + " (re-run with -Dneosyn.debug.ir=true for stack trace)");
        }
    }

    /**
     * Count total instructions across all action body procedures.
     */
    private int countInstructions(Actor actor) {
        int total = 0;
        for (com.neosyn.models.dpn.Action action : actor.getActions()) {
            for (com.neosyn.models.ir.Block block : action.getBody().getBlocks()) {
                if (block instanceof com.neosyn.models.ir.BlockBasic) {
                    total += ((com.neosyn.models.ir.BlockBasic) block).getInstructions().size();
                }
            }
            for (com.neosyn.models.ir.Block block : action.getScheduler().getBlocks()) {
                if (block instanceof com.neosyn.models.ir.BlockBasic) {
                    total += ((com.neosyn.models.ir.BlockBasic) block).getInstructions().size();
                }
            }
        }
        return total;
    }
}
