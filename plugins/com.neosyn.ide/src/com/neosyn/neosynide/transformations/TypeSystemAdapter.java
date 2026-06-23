/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.neosyn.models.ir.util.TypeUtil.getLargest;
import static com.neosyn.models.ir.util.TypeUtil.getType;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.transform.AbstractExpressionTransformer;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * This class defines common functionality to adapt the IR bit-accurate type system to target
 * languages using resize and type conversions. This class must be extended to handle behavior that
 * is specific to the target language (C, VHDL, Verilog) with
 * {@link #getExpressionType(OpBinary, Type, Type, Type)} and
 * {@link #getOperandType(OpBinary, Type, Type, int)}.
 * 
 * <p>
 * Why? Because the type of an IR expression implicitly grows or shrinks as needed, which is not
 * true of most languages. This class (and subclasses) adds explicit resizes and type conversions.
 * </p>
 * 

 *
 */
public abstract class TypeSystemAdapter extends AbstractExpressionTransformer {

	protected static final IrFactory ir = IrFactory.eINSTANCE;

	@Override
	public Expression caseExprBinary(ExprBinary expr) {
		OpBinary op = expr.getOp();
		Type t1 = getType(expr.getE1());
		Type t2 = getType(expr.getE2());

		Type type = getExpressionType(op, getType(expr), t1, t2);
		Type t1Cast = getOperandType(op, type, t1, 1);
		Type t2Cast = getOperandType(op, type, t2, 2);

		expr.setE1(transform(t1Cast, expr.getE1()));
		expr.setE2(transform(t2Cast, expr.getE2()));

		// store type for later use
		if (op.isComparison()) {
			expr.setComputedType(ir.createTypeBool());
		} else {
			expr.setComputedType(type);
		}

		return expr;
	}

	@Override
	public Expression caseExprResize(ExprResize resize) {
		Expression expr = resize.getExpr();
		if (expr.isExprInt()) {
			// special case for integer expressions
			return transform(getTarget(), expr);
		} else {
			Type type = getType(resize);
			Expression result = transform(type, expr);
			if (result.isExprResize()) {
				resize = (ExprResize) result;
			} else {
				resize.setExpr(result);
			}

			return resize;
		}
	}

	protected Expression cast(Type target, Type source, Expression expr) {
		return ir.cast(target, source, expr, false);
	}

	protected Type getExpressionType(OpBinary op, Type typeExpr, Type t1, Type t2) {
		if (op.isArithmetic() || op == OpBinary.SHIFT_LEFT) {
			return typeExpr;
		}
		return getLargest(t1, t2);
	}

	/**
	 * Returns the type to which the operand should be cast.
	 * 
	 * @param op
	 *            binary operator
	 * @param typeExpr
	 *            expression type
	 * @param typeOper
	 *            operand type
	 * @param n
	 *            1 for first operand, 2 for second operand
	 * @return a type
	 */
	protected abstract Type getOperandType(OpBinary op, Type typeExpr, Type typeOper, int n);

	@Override
	protected Expression transform(Type target, Expression expr) {
		Expression result = super.transform(target, expr);
		if (expr.isExprList()) {
			return result;
		}

		if (result.getComputedType() == null) {
			result.setComputedType(EcoreUtil.copy(TypeUtil.getType(result)));
		}

		return cast(target, getType(result), result);
	}

}
