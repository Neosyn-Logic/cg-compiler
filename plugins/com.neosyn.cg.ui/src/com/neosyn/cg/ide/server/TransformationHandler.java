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
import com.neosyn.core.transformations.ProcedureTransformation;
import com.neosyn.core.transformations.SchedulerTransformation;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
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

    public List<String> getTransformErrors() {
        return transformErrors;
    }

    public void clearTransformErrors() {
        transformErrors.clear();
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

        // Translate comments
        new CommentTranslator(instantiator).doSwitch(module);

        for (CgEntity cxEntity : module.getEntities()) {
            ServerUtils.debugLog("[Transform] Processing entity: " + cxEntity.getName());

            instantiator.forEachMapping(cxEntity, entity -> {
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
            });
        }

        ServerUtils.debugLog("[Transform] transformModule() completed");
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
        for (Inst inst : network.getInstances()) {
            Task task = inst.getTask();
            if (task != null) {
                Instance instance = instantiator.getMapping(dpn, inst);
                if (instance != null && instance.getEntity() instanceof Actor) {
                    Actor actor = (Actor) instance.getEntity();
                    transformTask(task, actor);
                }
            }
        }
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
            System.err.println("[neosyn] Transform error in " + actorName + ": "
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
