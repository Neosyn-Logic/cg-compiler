/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.services;

import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import com.google.common.collect.Iterables;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
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
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.models.util.Void;

/**
 * This class defines a full void switch.
 * 

 * 
 */
public abstract class VoidCxSwitch extends CgSwitch<Void> {

	@Override
	public Void caseBlock(Block block) {
		return visit(this, block.getStmts());
	}

	@Override
	public Void caseBranch(Branch branch) {
		return visit(this, branch.getCondition(), branch.getBody());
	}

	@Override
	public Void caseCgExpression(CgExpression expr) {
		return DONE;
	}

	@Override
	public Void caseExpressionBinary(ExpressionBinary expr) {
		return visit(this, expr.getLeft(), expr.getRight());
	}

	@Override
	public Void caseExpressionCast(ExpressionCast expr) {
		return visit(this, expr.getExpression());
	}

	@Override
	public Void caseExpressionIf(ExpressionIf expr) {
		return visit(this, expr.getCondition(), expr.getThen(), expr.getElse());
	}

	@Override
	public Void caseExpressionList(ExpressionList list) {
		return visit(this, list.getValues());
	}

	@Override
	public Void caseExpressionUnary(ExpressionUnary expr) {
		return visit(this, expr.getExpression());
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		return visit(this, Iterables.concat(expr.getIndexes(), expr.getParameters()));
	}

	@Override
	public Void caseInst(Inst inst) {
		return visit(this, inst.getTask());
	}

	@Override
	public Void caseModule(Module module) {
		return visit(this, module.getEntities());
	}

	@Override
	public Void caseNetwork(Network network) {
		return visit(this, network.getInstances());
	}

	@Override
	public Void caseStatement(Statement stmt) {
		return DONE;
	}

	@Override
	public Void caseStatementAssert(StatementAssert stmt) {
		return visit(this, stmt.getCondition());
	}

	@Override
	public Void caseStatementAssign(StatementAssign stmt) {
		return visit(this, stmt.getTarget(), stmt.getValue());
	}

	@Override
	public Void caseStatementIf(StatementIf stmtIf) {
		return visit(this, stmtIf.getBranches());
	}

	@Override
	public Void caseStatementLabeled(StatementLabeled stmt) {
		return visit(this, stmt.getStmt());
	}

	@Override
	public Void caseStatementLoop(StatementLoop stmt) {
		return visit(this, stmt.getInit(), stmt.getCondition(), stmt.getBody(), stmt.getAfter());
	}

	@Override
	public Void caseStatementPrint(StatementPrint stmt) {
		return visit(this, stmt.getArgs());
	}

	@Override
	public Void caseStatementReturn(StatementReturn stmt) {
		return visit(this, stmt.getValue());
	}

	@Override
	public Void caseStatementVariable(StatementVariable stmt) {
		return visit(this, stmt.getVariables());
	}

	@Override
	public Void caseStatementWrite(StatementWrite write) {
		return visit(this, write.getValue());
	}

	@Override
	public Void caseTypeDecl(TypeDecl type) {
		return visit(this, type.getSize());
	}

	@Override
	public Void caseTypedef(Typedef typedef) {
		return visit(this, typedef.getType());
	}

	@Override
	public Void caseVarDecl(VarDecl decl) {
		return visit(this, decl.getVariables());
	}

	@Override
	public Void caseVariable(Variable variable) {
		visit(this, variable.getDimensions());
		visit(this, variable.getBody());
		return visit(this, variable.getValue());
	}

}