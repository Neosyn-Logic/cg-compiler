/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.generator;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.neosyn.core.transformations.ProcedureTransformation;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.ir.transform.UniqueNameComputer;
import com.neosyn.models.util.Void;

/**
 * This class visits all references to local variables, and each local variable which is found to be
 * used across more than one procedure is promoted to a state variable.
 * 

 * 
 */
public class VariablePromoter extends AbstractIrVisitor {

	private final UniqueNameComputer nameComputer;

	private final Multimap<Var, Procedure> procMap;

	private final List<Var> stateVars;

	public VariablePromoter(List<Var> stateVars) {
		this.stateVars = stateVars;
		nameComputer = new UniqueNameComputer(stateVars);
		procMap = LinkedHashMultimap.create();
	}

	@Override
	public Void caseInstLoad(InstLoad load) {
		Var variable = load.getSource().getVariable();
		if (variable.isLocal()) {
			procMap.put(variable, procedure);
		}

		return DONE;
	}

	@Override
	public Void caseInstStore(InstStore store) {
		Var variable = store.getTarget().getVariable();
		if (variable.isLocal()) {
			procMap.put(variable, procedure);
		}

		return DONE;
	}

	/**
	 * Visits the given actor, and promotes any local variable that is defined/used across more than
	 * one procedure.
	 * 
	 * @param actor
	 *            an actor
	 */
	public void visit(Actor actor) {
		new ProcedureTransformation(this).doSwitch(actor);

		for (Var variable : procMap.keySet()) {
			Collection<Procedure> procedures = procMap.get(variable);
			if (procedures.size() > 1) {
				// change name
				String procName = procedures.iterator().next().getName();
				String name = procName + "_" + variable.getName();
				variable.setName(nameComputer.getUniqueName(name));

				// must not be constant
				variable.setAssignable(true);

				// promotes to state variable
				stateVars.add(variable);
			}
		}
	}

}
