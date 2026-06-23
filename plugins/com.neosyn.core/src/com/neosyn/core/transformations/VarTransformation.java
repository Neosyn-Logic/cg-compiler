/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.models.util.SwitchUtil.DONE;

import com.google.common.collect.Iterables;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.Void;

/**
 * This class defines a module transformation that transforms all variables in an actor/unit.
 * 

 * 
 */
public class VarTransformation extends ProcedureTransformation {

	public VarTransformation(AbstractIrVisitor irVisitor) {
		super(irVisitor);
	}

	@Override
	public Void caseEntity(Entity entity) {
		for (Port port : Iterables.concat(entity.getInputs(), entity.getOutputs())) {
			irVisitor.doSwitch(port);
		}

		for (Var var : entity.getVariables()) {
			irVisitor.doSwitch(var);
		}

		for (Procedure procedure : entity.getProcedures()) {
			visitProcedure(procedure);
		}

		return DONE;
	}

	protected void visitProcedure(Procedure procedure) {
		for (Var var : procedure.getParameters()) {
			irVisitor.doSwitch(var);
		}

		for (Var var : procedure.getLocals()) {
			irVisitor.doSwitch(var);
		}
	}

}
