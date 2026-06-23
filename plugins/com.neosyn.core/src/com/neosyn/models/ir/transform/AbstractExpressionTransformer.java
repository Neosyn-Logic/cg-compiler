/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import java.util.Iterator;

import org.eclipse.emf.common.util.EList;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprList;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.util.IrSwitch;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * This interface defines an abstract expression transformer.
 * 

 * 
 */
public abstract class AbstractExpressionTransformer extends IrSwitch<Expression> {

	private Procedure procedure;

	private Type target;

	@Override
	public Expression caseExprBinary(ExprBinary expr) {
		Type parent = TypeUtil.getType(expr);
		expr.setE1(transform(parent, expr.getE1()));
		expr.setE2(transform(parent, expr.getE2()));
		return expr;
	}

	@Override
	public Expression caseExpression(Expression expr) {
		return expr;
	}

	@Override
	public Expression caseExprList(ExprList expr) {
		visitExprList(Functions.constant(target), expr.getValue());
		return expr;
	}

	@Override
	public Expression caseExprResize(ExprResize resize) {
		Type type = TypeUtil.getType(resize);
		resize.setExpr(transform(type, resize.getExpr()));
		return resize;
	}

	@Override
	public Expression caseExprTypeConv(ExprTypeConv typeConv) {
		Type type = TypeUtil.getType(typeConv);
		typeConv.setExpr(transform(type, typeConv.getExpr()));
		return typeConv;
	}

	@Override
	public Expression caseExprUnary(ExprUnary expr) {
		expr.setExpr(transform(getTarget(), expr.getExpr()));
		return expr;
	}

	protected Procedure getProcedure() {
		return procedure;
	}

	protected Type getTarget() {
		return target;
	}

	public void setProcedure(Procedure procedure) {
		this.procedure = procedure;
	}

	/**
	 * For internal use. Sets the current target to the given type, calls
	 * {@link #doSwitch(org.eclipse.emf.ecore.EObject)} on the given expression, restores the
	 * previous target, and returns the result of the call.
	 * 
	 * @param target
	 *            a target type
	 * @param expression
	 *            an expression
	 * @return an expression (may or may not be the same as <code>expression</code>)
	 */
	protected Expression transform(Type target, Expression expression) {
		Type oldTarget = this.target;
		this.target = target;
		Expression result = doSwitch(expression);
		this.target = oldTarget;
		return result;
	}

	/**
	 * Visits the given expression with the given type target.
	 * 
	 * @param target
	 *            type of the target to which the expression is assigned
	 * @param expression
	 *            an expression
	 * @return an expression (may or may not be the same as <code>expression</code>)
	 */
	public Expression visitExpr(Type target, Expression expression) {
		return transform(target, expression);
	}

	private void visitExprList(Function<Object, Type> fun, EList<Expression> expressions) {
		int i = 0;
		while (i < expressions.size()) {
			final Expression expr = expressions.get(i);
			final Expression res = visitExpr(fun.apply(null), expr);
			if (res != expr) {
				// remove expr (expr may already have been removed if it is contained in res)
				expressions.remove(expr);

				// add res to the list
				// we need to use "add" and not "set" because "expr" was removed from the list
				// so the list might not be big enough
				expressions.add(i, res);
			}
			i++;
		}
	}

	/**
	 * Visits the given expressions with the given type targets. Updates the expressions in place.
	 * 
	 * @param types
	 *            types of the targets to which the expressions are assigned
	 * @param expressions
	 *            a list of expressions
	 */
	public void visitExprList(Iterable<? extends Type> types, EList<Expression> expressions) {
		final Iterator<? extends Type> it = types.iterator();
		visitExprList(new Function<Object, Type>() {
			@Override
			public Type apply(Object arg0) {
				return it.next();
			}
		}, expressions);
	}

}
