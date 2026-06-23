/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import static com.neosyn.cg.CgConstants.PROP_AVAILABLE;
import static com.neosyn.cg.CgConstants.PROP_READ;
import static com.neosyn.cg.CgConstants.PROP_READY;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.StatementFence;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a switch that visits a statement and returns true if it (or any expression or
 * statement it contains) may be a schedule modifier like fence and idle, and any action on a port
 * (available, read, write).
 * 

 * 
 */
public class ScheduleModifierSwitch extends BoolCxSwitch {

	@Override
	public Boolean caseExpressionVariable(ExpressionVariable expr) {
		String prop = expr.getPropertyName();
		if (PROP_AVAILABLE.equals(prop) || PROP_READ.equals(prop) || PROP_READY.equals(prop)) {
			return true;
		}

		Variable variable = expr.getSource().getVariable();
		if (CgUtil.isFunctionNotConstant(variable)) {
			// if function has side-effect, we visit it
			if (doSwitch(variable)) {
				return true;
			}
		}

		return super.caseExpressionVariable(expr);
	}

	@Override
	public Boolean caseStatementFence(StatementFence stmt) {
		return true;
	}

	@Override
	public Boolean caseStatementIdle(StatementIdle stmt) {
		return true;
	}

	@Override
	public Boolean caseStatementWrite(StatementWrite stmt) {
		return true;
	}

}