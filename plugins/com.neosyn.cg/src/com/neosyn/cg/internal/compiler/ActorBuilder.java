/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Var;

/**
 * This class defines a dataflow builder that inherits from the IR builder.
 * 

 * 
 */
public class ActorBuilder extends IrBuilder {

	private boolean ignoreWrites;

	protected Integer line;

	private Transition transition;

	public ActorBuilder(IInstantiator instantiator, Actor actor) {
		super(instantiator, actor);
		// bug #11: struct STATE vars were flattened to persistent entity field
		// Vars by SkeletonMaker; register them so `state.field` access and
		// whole-struct copy resolve through the same structFieldMap as locals.
		importStructFields(instantiator.getStructStateVars(actor));
	}

	/**
	 * Creates the body of the action associated with the given transition info. This method simply
	 * uses the transformer to visit the objects associated with the info's body.
	 * 
	 * @param info
	 *            transition info
	 */
	private void createBody() {
		// use the 'body' procedure
		setProcedure(transition.getAction().getBody());
		for (EObject eObject : transition.getBody()) {
			transformer.doSwitch(eObject);
		}
	}

	/**
	 * Creates the scheduler of the action associated with the given transition info.
	 * 
	 * @param info
	 *            transition info
	 */
	private void createScheduler() {
		// use the 'scheduler' procedure
		setProcedure(transition.getAction().getScheduler());

		// translate statements and condition
		ignoreWrites = true;
		Expression expr = null;
		List<EObject> eObjects = transition.getScheduler();
		for (EObject eObject : eObjects) {
			// translate object
			EObject irObject = transformer.doSwitch(eObject);
			if (irObject instanceof Expression) {
				expr = translateCondition(expr, (Expression) irObject);
			}
		}
		ignoreWrites = false;

		// adds a return if the expression is not null
		if (expr == null) {
			expr = ir.createExprBool(true);
		}
		add(ir.createInstReturn(expr));
		// NOTE: scheduler procedures intentionally contain InstStore to state
		// variables. This is the compilation strategy for within-cycle data
		// dependencies between sibling if-guards. See
		// .claude/SCHEDULER_LEAK_INVESTIGATION.md.
	}

	final Actor getActor() {
		return (Actor) entity;
	}

	/**
	 * Returns the IR port that corresponds to the given variable reference.
	 * 
	 * @param ref
	 *            a variable reference
	 * @return an IR port
	 */
	public Port getPort(VarRef ref) {
		return instantiator.getPort(entity, ref);
	}

	/**
	 * Returns true if writes should be ignored, which is the case when translating the scheduler.
	 * 
	 * @return
	 */
	public boolean ignoreWrites() {
		return ignoreWrites;
	}

	private Expression translateCondition(Expression expr, Expression condition) {
		// assign to new 'cond' variable
		Var condVar = createLocal(0, ir.createTypeBool(), "cond");
		add(ir.createInstAssign(condVar, condition));
		Expression cond = ir.createExprVar(condVar);

		if (expr == null) {
			return cond;
		} else {
			return ir.createExprBinary(expr, OpBinary.LOGIC_AND, cond);
		}
	}

	final void updateLineInfo(int lineNumber) {
		if (line == null) {
			transition.getLines().add(lineNumber);
		} else {
			transition.getLines().add(line);
		}
	}

	/**
	 * Visits the given transition and creates the IR of the action associated with it.
	 * 
	 * @param transition
	 *            a transition
	 */
	public void visitTransition(Transition transition) {
		this.transition = transition;

		createScheduler();
		createBody();

		transition.getBody().clear();
		transition.getScheduler().clear();
	}

}
