/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprBool;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.util.IrSwitch;

/**
 * This class defines a constant folder.
 * 

 * 
 */
public class ConstantFolder extends IrSwitch<Expression> {

	public static boolean isFalse(Expression expr) {
		return expr.isExprBool() && !((ExprBool) expr).isValue();
	}

	public static boolean isTrue(Expression expr) {
		return expr.isExprBool() && ((ExprBool) expr).isValue();
	}

	@Override
	public Expression caseExprBinary(ExprBinary expr) {
		Expression e1 = doSwitch(expr.getE1());
		Expression e2 = doSwitch(expr.getE2());

		OpBinary op = expr.getOp();
		if (op == OpBinary.LOGIC_AND) {
			if (isFalse(e1) || isFalse(e2)) {
				return IrFactory.eINSTANCE.createExprBool(false);
			} else if (isTrue(e1)) {
				return e2;
			} else if (isTrue(e2)) {
				return e1;
			}
		}

		expr.setE1(e1);
		expr.setE2(e2);

		return expr;
	}

	@Override
	public Expression caseExpression(Expression expr) {
		return expr;
	}

	@Override
	public Expression caseExprResize(ExprResize expr) {
		Expression subExpr = doSwitch(expr.getExpr());
		expr.setExpr(subExpr);

		return expr;
	}

	@Override
	public Expression caseExprUnary(ExprUnary expr) {
		Expression subExpr = doSwitch(expr.getExpr());

		OpUnary op = expr.getOp();
		if (op == OpUnary.LOGIC_NOT) {
			if (isTrue(subExpr)) {
				return IrFactory.eINSTANCE.createExprBool(false);
			} else if (isFalse(subExpr)) {
				return IrFactory.eINSTANCE.createExprBool(true);
			}
		}

		expr.setExpr(subExpr);

		return expr;
	}

}
