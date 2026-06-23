/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.generator;

import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_LOOP_DEPRECATED;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static com.neosyn.cg.CgConstants.NAME_SETUP_DEPRECATED;
import static com.neosyn.core.IProperties.PROP_CLOCKS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.AbstractFileSystemAccess;
import org.eclipse.xtext.generator.IFileSystemAccess;
import org.eclipse.xtext.generator.IGenerator;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.neosyn.core.NeosynCore;
import com.neosyn.core.transformations.ProcedureTransformation;
import com.neosyn.core.transformations.SchedulerTransformation;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.ActorTransformer;
import com.neosyn.cg.internal.compiler.CommentTranslator;
import com.neosyn.cg.internal.compiler.FunctionTransformer;
import com.neosyn.cg.internal.scheduler.CombinationalScheduler;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.IfScheduler;
import com.neosyn.cg.internal.scheduler.experimental.ExperimentalScheduler;
import com.neosyn.cg.internal.services.EdgeColoring;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.ir.transform.StoreOnceTransformation;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Executable;

/**
 * This class defines a generator for Cx resources.
 * 

 */
public class CgGenerator implements IGenerator {

	@Inject
	private IInstantiator instantiator;

	/**
	 * Using the given file system access, compiles the given module and serializes the IR version
	 * of its entities.
	 * 
	 * @param fsa
	 *            file system access
	 * @param module
	 *            Cx module
	 */
	private void compile(final IFileSystemAccess fsa, Module module) {
		// translate comments for this module
		new CommentTranslator(instantiator).doSwitch(module);

		for (final CgEntity cxEntity : module.getEntities()) {
			instantiator.forEachMapping(cxEntity, new Executable<Entity>() {
				@Override
				public void exec(Entity entity) {
					switch (cxEntity.eClass().getClassifierID()) {
					case CgPackage.BUNDLE:
						transformBundle((Bundle) cxEntity, (Unit) entity);
						break;

					case CgPackage.NETWORK:
						// transforms and serializes all inner tasks of the network
						DPN dpn = (DPN) entity;
						Iterable<Actor> actors = transformNetwork((Network) cxEntity, dpn);
						for (Actor inner : actors) {
							serialize(fsa, inner);
						}

						// serializes built-in entities instantiated by this network
						for (Instance instance : dpn.getInstances()) {
							Entity subEntity = instance.getEntity();
							if (CoreUtil.isBuiltin(subEntity)) {
								serialize(fsa, subEntity);
							}
						}
						break;

					case CgPackage.TASK:
						transformTask((Task) cxEntity, (Actor) entity);
						break;
					}

					serialize(fsa, entity);
				}
			});
		}
	}

	@Override
	public void doGenerate(Resource resource, IFileSystemAccess fsa) {
		// do nothing if the resource does not contain anything
		if (resource.getContents().isEmpty()) {
			return;
		}

		IFile file = EcoreHelper.getFile(resource);
		if (file != null) {
			try {
				file.deleteMarkers(EdgeColoring.TYPE, false, IResource.DEPTH_ZERO);
			} catch (CoreException e) {
				NeosynCore.log(e);
			}
		}

		// compile module
		compile(fsa, (Module) resource.getContents().get(0));
	}

	/**
	 * Using the given file system access, serializes the given entity.
	 * 
	 * @param fsa
	 *            Xtext file system access
	 * @param entity
	 *            IR entity
	 */
	private void serialize(IFileSystemAccess fsa, Entity entity) {
		// serializes to byte array (never throws exception)
		OutputStream os = new ByteArrayOutputStream();
		try {
			entity.eResource().save(os, null);
		} catch (IOException e) {
			// byte array output stream never throws exception
		}

		// serialize to relative file name (obtained by deresolving URI against base URI)
		URI base = ((AbstractFileSystemAccess) fsa).getURI("");
		if (!base.lastSegment().isEmpty()) {
			// last segment must be empty for URI to be deresolved properly
			base = base.appendSegment("");
		}

		URI uri = entity.eResource().getURI();
		String fileName = uri.deresolve(base).toString();
		if (uri.isPlatformResource()) {
			CoreUtil.ensureCaseConsistency(new Path(uri.toPlatformString(true)));
		}
		fsa.generateFile(fileName, os.toString());

		// color edges of FSM (if applicable)
		new EdgeColoring().visit(entity);
	}

	/**
	 * Transforms the given bundle into a unit.
	 * 
	 * @param bundle
	 *            Cx bundle
	 * @param unit
	 *            IR unit
	 */
	private void transformBundle(Bundle bundle, Unit unit) {
		transformConstantFunctions(unit, CgUtil.getVariables(bundle));
		new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(unit);
	}

	/**
	 * Transforms constant functions of the given entity to IR procedures.
	 * 
	 * @param entity
	 *            an entity
	 * @param variables
	 *            a list of declarations
	 */
	private void transformConstantFunctions(Entity entity, Iterable<Variable> variables) {
		for (Variable variable : variables) {
			if (CgUtil.isFunctionConstant(variable)) {
				// visit constant functions
				new FunctionTransformer(instantiator, entity).doSwitch(variable);
			}
		}
	}

	/**
	 * Calls {@link #transformTask(Task, Actor)} for each inner task of the given network, and
	 * returns the actors that were transformed.
	 * 
	 * @param network
	 *            a network
	 * @param dpn
	 *            the DPN corresponding to the network
	 * @return an iterable over actors that correspond to inner tasks. May be empty.
	 */
	private Iterable<Actor> transformNetwork(Network network, DPN dpn) {
		List<Actor> innerTasks = new ArrayList<>();
		for (Inst inst : network.getInstances()) {
			final Task task = inst.getTask();
			if (task != null) {
				Instance instance = instantiator.getMapping(dpn, inst);
				Actor actor = (Actor) instance.getEntity();
				transformTask(task, actor);
				innerTasks.add(actor);
			}
		}
		return innerTasks;
	}

	/**
	 * Returns true if the actor is combinational (has no clocks).
	 * This is the SINGLE place where combinational vs synchronous is decided.
	 *
	 * Combinational detection:
	 * 1. properties { clocks: [] } - empty clocks array
	 * 2. properties { type: "combinational" } - explicit type
	 */
	private boolean isCombinational(Actor actor) {
		// Check for empty clocks array
		JsonElement clocks = actor.getProperties().get(PROP_CLOCKS);
		if (clocks != null && clocks.isJsonArray() && clocks.getAsJsonArray().isEmpty()) {
			return true;
		}

		// Check for type: "combinational"
		JsonElement type = actor.getProperties().get("type");
		if (type != null && type.isJsonPrimitive() && "combinational".equals(type.getAsString())) {
			return true;
		}

		return false;
	}

	/**
	 * Transforms the given task to an actor. Routes to either combinational or
	 * synchronous transformation based on the actor type.
	 *
	 * @param task Cx task
	 * @param actor IR actor
	 */
	private void transformTask(Task task, Actor actor) {
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

		// SINGLE decision point: combinational vs synchronous
		if (isCombinational(actor)) {
			transformCombinationalTask(actor, setup, loop);
		} else {
			transformSynchronousTask(actor, setup, loop);
		}
	}

	/**
	 * Transforms a combinational task (no clocks, all logic in one instant).
	 * Uses simplified scheduling - no cycle detection, no if-statement development.
	 */
	private void transformCombinationalTask(Actor actor, Variable setup, Variable loop) {
		// Simple scheduler: one action, no FSM complexity
		// (uses temporary FSM structure for infrastructure compatibility)
		new CombinationalScheduler(instantiator, actor).schedule(setup, loop);

		// Transform AST to IR (needs FSM transitions)
		new ActorTransformer(instantiator, actor).visit();

		// Post-process FSM: rename states and actions (CRITICAL for bytecode generation)
		// This sets procedure names like "comb_s0", "isSchedulable_s0" which ActorCompiler needs
		new FsmBeautifier().visit(actor);

		// Handle combinational behavior
		new CombinationalVisitor().visit(actor);

		// Clear the FSM - combinational actors don't need FSM in the final IR
		// The Verilog/VHDL generators will see hasFsm=false and print actions directly
		actor.setFsm(null);
	}

	/**
	 * Transforms a synchronous task (has clocks, FSM-based scheduling).
	 * Uses full cycle scheduling with if-statement development.
	 */
	private void transformSynchronousTask(Actor actor, Variable setup, Variable loop) {
		// Check for experimental scheduler
		JsonElement schedulerProp = actor.getProperties().get("scheduler");
		if (schedulerProp != null && "experimental".equals(schedulerProp.getAsString())) {
			new ExperimentalScheduler(instantiator, actor).schedule(setup, loop);
		} else {
			// Full FSM-based scheduling
			new CycleScheduler(instantiator, actor).schedule(setup, loop);
			new IfScheduler(instantiator, actor).visit();
		}

		// Transform AST to IR
		new ActorTransformer(instantiator, actor).visit();

		// Post-process FSM: rename states and actions
		new FsmBeautifier().visit(actor);

		// Promote local variables used over more than one cycle to state variables
		new VariablePromoter(actor.getVariables()).visit(actor);
		new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(actor);

		// Handle combinational behavior in actor
		new CombinationalVisitor().visit(actor);

		// Apply store once transformation and remove side effects
		new SchedulerTransformation(new StoreOnceTransformation()).doSwitch(actor);
		new SchedulerTransformation(new SideEffectRemover()).doSwitch(actor);
	}

}
