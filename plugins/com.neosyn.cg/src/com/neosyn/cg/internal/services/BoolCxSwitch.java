/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import static com.neosyn.models.util.SwitchUtil.check;

import com.google.common.collect.Iterables;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Statement;
import com.neosyn.cg.cg.StatementAssert;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLabeled;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementPrint;
import com.neosyn.cg.cg.StatementReturn;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;

/**
 * This class defines a full boolean switch.
 * 

 * 
 */
public class BoolCxSwitch extends CgSwitch<Boolean> {

	@Override
	public Boolean caseBlock(Block block) {
		return check(this, block.getStmts());
	}

	@Override
	public Boolean caseBranch(Branch branch) {
		return check(this, branch.getCondition(), branch.getBody());
	}

	@Override
	public Boolean caseCgExpression(CgExpression expr) {
		return false;
	}

	@Override
	public Boolean caseExpressionBinary(ExpressionBinary expr) {
		return check(this, expr.getLeft(), expr.getRight());
	}

	@Override
	public Boolean caseExpressionIf(ExpressionIf expr) {
		return check(this, expr.getCondition(), expr.getThen(), expr.getElse());
	}

	@Override
	public Boolean caseExpressionList(ExpressionList list) {
		return check(this, list.getValues());
	}

	@Override
	public Boolean caseExpressionUnary(ExpressionUnary expr) {
		return doSwitch(expr.getExpression());
	}

	@Override
	public Boolean caseExpressionVariable(ExpressionVariable expr) {
		return check(this, Iterables.concat(expr.getIndexes(), expr.getParameters()));
	}

	@Override
	public Boolean caseStatement(Statement stmt) {
		return false;
	}

	@Override
	public Boolean caseStatementAssert(StatementAssert stmt) {
		return doSwitch(stmt.getCondition());
	}

	@Override
	public Boolean caseStatementAssign(StatementAssign stmt) {
		return check(this, stmt.getTarget(), stmt.getValue());
	}

	@Override
	public Boolean caseStatementIf(StatementIf stmtIf) {
		return check(this, stmtIf.getBranches());
	}

	@Override
	public Boolean caseStatementLabeled(StatementLabeled stmt) {
		return check(this, stmt.getStmt());
	}

	@Override
	public Boolean caseStatementLoop(StatementLoop stmt) {
		return check(this, stmt.getInit(), stmt.getCondition(), stmt.getBody(), stmt.getAfter());
	}

	@Override
	public Boolean caseStatementPrint(StatementPrint print) {
		return check(this, print.getArgs());
	}

	@Override
	public Boolean caseStatementReturn(StatementReturn stmt) {
		return check(this, stmt.getValue());
	}

	@Override
	public Boolean caseStatementVariable(StatementVariable stmt) {
		return check(this, stmt.getVariables());
	}

	@Override
	public Boolean caseStatementWrite(StatementWrite write) {
		return doSwitch(write.getValue());
	}

	@Override
	public Boolean caseVariable(Variable variable) {
		if (check(this, Iterables.concat(variable.getDimensions(), variable.getParameters()))) {
			return true;
		}
		return check(this, variable.getBody(), variable.getValue());
	}

}