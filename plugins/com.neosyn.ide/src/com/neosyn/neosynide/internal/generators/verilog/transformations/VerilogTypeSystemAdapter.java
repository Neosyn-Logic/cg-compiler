/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Type;
import com.neosyn.neosynide.transformations.HdlTypeSystemAdapter;

/**
 * This class defines an expression switch that modifies the IR to support Cx's type system in
 * Verilog.
 * 

 * 
 */
public class VerilogTypeSystemAdapter extends HdlTypeSystemAdapter {

	@Override
	public Expression caseExprUnary(ExprUnary exprUn) {
		Expression expr = exprUn.getExpr();
		if (exprUn.getOp() == OpUnary.MINUS) {
			ExprBinary exprBin = ir.createExprBinary(ir.createExprInt(0), OpBinary.MINUS, expr);
			return transform(getTarget(), exprBin);
		}

		return super.caseExprUnary(exprUn);
	}

	@Override
	protected Type getOperandType(OpBinary op, Type typeExpr, Type typeOper, int n) {
		switch (op) {
		case SHIFT_LEFT:
			// we implement the left shift as a bit concatenation
			// so we must not cast operands to the size of the result
			// instead we just cast them to their respective type
			return typeOper;
		default:
			return typeExpr;
		}
	}

}
