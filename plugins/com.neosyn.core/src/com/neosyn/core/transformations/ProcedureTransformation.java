/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.models.util.SwitchUtil.CASCADE;
import static com.neosyn.models.util.SwitchUtil.DONE;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.Void;

/**
 * This class defines a transformation that transforms all procedures in an actor/unit.
 * 

 * 
 */
public class ProcedureTransformation extends Transformation {

	public ProcedureTransformation(AbstractIrVisitor irVisitor) {
		super(irVisitor);
	}

	@Override
	public Void caseActor(Actor actor) {
		caseEntity(actor);

		for (Action action : actor.getActions()) {
			visitProcedure(action.getBody());
			visitProcedure(action.getCombinational());
			visitProcedure(action.getScheduler());
		}

		return DONE;
	}

	@Override
	public Void caseEntity(Entity entity) {
		for (Procedure procedure : entity.getProcedures()) {
			visitProcedure(procedure);
		}

		return DONE;
	}

	@Override
	public Void caseUnit(Unit unit) {
		return CASCADE;
	}

	protected void visitProcedure(Procedure procedure) {
		irVisitor.doSwitch(procedure);
	}

}
