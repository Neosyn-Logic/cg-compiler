/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Statement;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Entity;

/**
 * This class defines a switch that extends the statement switch to handle loops. It returns true if
 * a loop is complex (i.e. if it is not compile-time unrollable).
 * 

 * 
 */
public class LoopSwitch extends ScheduleModifierSwitch {

	private Entity entity;

	private IInstantiator instantiator;

	public LoopSwitch(IInstantiator instantiator, Entity entity) {
		this.instantiator = instantiator;
		this.entity = entity;
	}

	@Override
	public Boolean caseStatementLoop(StatementLoop stmt) {
		// first check if loop contains cycle modifiers or other complex loops
		if (!super.caseStatementLoop(stmt)) {
			Statement init = stmt.getInit();
			StatementAssign after = stmt.getAfter();
			if (init != null && after != null) {
				if (init instanceof StatementAssign) {
					StatementAssign assign = (StatementAssign) init;
					Variable variable = assign.getTarget().getSource().getVariable();
					if (CgUtil.isLocal(variable)) {
						Object value = instantiator.evaluate(entity, assign.getValue());
						if (value != null) {
							return checkBounds(variable, value, stmt.getCondition());
						}
					}
				} else if (init instanceof StatementVariable) {
					StatementVariable stmtVar = (StatementVariable) init;
					for (Variable variable : stmtVar.getVariables()) {
						Object value = instantiator.evaluate(entity, variable.getValue());
						if (value == null || checkBounds(variable, value, stmt.getCondition())) {
							return true;
						}
					}
					return false;
				}
			}
		}

		return true;
	}

	private boolean checkBounds(Variable variable, Object init, CgExpression condition) {
		if (condition instanceof ExpressionBinary) {
			ExpressionBinary exprBin = (ExpressionBinary) condition;
			CgExpression left = exprBin.getLeft();
			CgExpression right = exprBin.getRight();

			if (left instanceof ExpressionVariable) {
				ExpressionVariable exprVar = (ExpressionVariable) left;
				if (exprVar.getSource().getVariable() == variable && exprVar.getIndexes().isEmpty()) {
					Object max = instantiator.evaluate(entity, right);
					if (max != null) {
						// loop is not complex
						return false;
					}
				}
			}
		}

		return true;
	}

}