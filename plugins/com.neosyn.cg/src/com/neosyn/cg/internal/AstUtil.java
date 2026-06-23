/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal;

import static com.neosyn.cg.cg.CgFactory.eINSTANCE;
import static org.eclipse.emf.ecore.util.EcoreUtil.resolveAll;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionBoolean;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementGoto;
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.models.dpn.State;

/**
 * This class defines methods to manipulate the AST.
 * 

 * 
 */
public class AstUtil {

	private static void addAdapters(Copier copier) {
		for (Entry<EObject, EObject> entry : copier.entrySet()) {
			if (entry.getKey() instanceof VarRef) {
				final EObject original = entry.getKey();
				final EObject copy = entry.getValue();
				copy.eAdapters().add(new CopyOf(original));
			}
		}
	}

	/**
	 * Creates the AST of <code>expr1 && expr2</code>. Expressions are copied. If
	 * <code>expr1 == true</code>, returns a copy of <code>expr2</code>, and vice-versa.
	 * 
	 * @param expr1
	 *            first operand
	 * @param expr2
	 *            second operand
	 * @return a binary expression
	 */
	public static CgExpression and(CgExpression expr1, CgExpression expr2) {
		if (isTrue(expr1)) {
			return copyIfNeeded(expr2);
		} else if (isTrue(expr2)) {
			return copyIfNeeded(expr1);
		} else {
			ExpressionBinary exprBin = eINSTANCE.createExpressionBinary();
			exprBin.setLeft(copyIfNeeded(expr1));
			exprBin.setOperator("&&");
			exprBin.setRight(copyIfNeeded(expr2));
			return exprBin;
		}
	}

	/**
	 * Creates an assign statement.
	 * 
	 * @param variable
	 * @param value
	 * @return
	 */
	public static StatementAssign assign(Variable variable, CgExpression value) {
		StatementAssign assign = eINSTANCE.createStatementAssign();
		assign.setTarget(expr(variable));
		assign.setOp("=");
		assign.setValue(copyIfNeeded(value));
		return assign;
	}

	public static <T extends EObject> T copy(T eObject) {
		Copier copier = new Copier();
		EObject result = copier.copy(eObject);
		copier.copyReferences();

		addAdapters(copier);

		@SuppressWarnings("unchecked")
		T t = (T) result;
		return t;
	}

	private static <T extends EObject> Collection<T> copyAll(Collection<? extends T> eObjects) {
		Copier copier = new Copier();
		Collection<T> result = new ArrayList<>(eObjects.size());
		for (EObject eObject : eObjects) {
			@SuppressWarnings("unchecked")
			T copy = (T) copier.copy(eObject);
			result.add(copy);
		}

		copier.copyReferences();
		addAdapters(copier);

		return result;
	}

	/**
	 * If expression is already contained in an expression, returns a copy of it.
	 * 
	 * @param expression
	 *            a Cx expression
	 * @return <code>expression</code> itself, or a copy of it
	 */
	private static CgExpression copyIfNeeded(CgExpression expression) {
		if (expression.eContainer() == null) {
			return expression;
		}
		return copy(expression);
	}

	private static CgExpression createArithmetic(Variable variable, List<CgExpression> indexes,
			String op, CgExpression value) {
		ExpressionBinary exprBin = eINSTANCE.createExpressionBinary();

		ExpressionVariable left = expr(variable);
		if (!indexes.isEmpty()) {
			left.getIndexes().addAll(copyAll(indexes));
		}

		exprBin.setLeft(left);
		exprBin.setOperator(op);
		exprBin.setRight(copyIfNeeded(value));

		// add cast
		ExpressionCast cast = eINSTANCE.createExpressionCast();
		CgType type = copy(CgUtil.getType(variable));
		cast.setType(type);
		cast.setExpression(exprBin);
		return cast;
	}

	/**
	 * Decrements the given variable.
	 * 
	 * @param variable
	 *            a variable
	 * @return an assign statement
	 */
	public static StatementAssign decrement(Variable variable) {
		StatementAssign assign = eINSTANCE.createStatementAssign();
		assign.setTarget(expr(variable));
		assign.setOp("--");
		return assign;
	}

	/**
	 * Returns a new Cx boolean expression.
	 * 
	 * @param value
	 *            a boolean value
	 * @return a boolean expression
	 */
	public static ExpressionBoolean expr(boolean value) {
		ExpressionBoolean exprBool = eINSTANCE.createExpressionBoolean();
		exprBool.setValue(value);
		return exprBool;
	}

	/**
	 * Creates a new integer expression with the given value.
	 * 
	 * @param value
	 *            an integer value
	 * @return an integer expression
	 */
	public static ExpressionInteger expr(int value) {
		ExpressionInteger expr = eINSTANCE.createExpressionInteger();
		expr.setValue(BigInteger.valueOf(value));
		return expr;
	}

	/**
	 * Creates a new expression variable that references the given variable.
	 * 
	 * @param variable
	 *            a variable
	 * @return an expression variable
	 */
	public static ExpressionVariable expr(Variable variable) {
		ExpressionVariable expr = eINSTANCE.createExpressionVariable();
		VarRef ref = eINSTANCE.createVarRef();
		ref.getObjects().add(variable);
		expr.setSource(ref);
		return expr;
	}

	/**
	 * Returns the expression resulting from an assign created from the assignment operator
	 * (post-increment/decrement or compound operator).
	 * 
	 * @param assign
	 *            an assign statement
	 * @return an expression
	 */
	public static CgExpression getAssignValue(StatementAssign assign) {
		String op = assign.getOp();
		Variable variable = assign.getTarget().getSource().getVariable();
		List<CgExpression> indexes = assign.getTarget().getIndexes();
		CgExpression value = assign.getValue();
		if (value == null) {
			// handle post-decrement/increment
			if ("++".equals(op)) {
				value = createArithmetic(variable, indexes, "+", expr(1));
			} else if ("--".equals(op)) {
				value = createArithmetic(variable, indexes, "-", expr(1));
			}
		} else {
			// compound op
			if (op.length() > 1) {
				// resolve value now, because proxies in "value" can't be
				// resolved by Xtext since no node model is attached to the AST
				// nodes created
				resolveAll(value);

				String binOp = op.substring(0, op.length() - 1);
				value = createArithmetic(variable, indexes, binOp, value);
			}
		}

		return value;
	}

	public static StatementGoto gotoState(State target) {
		StatementGoto stmtGoto = eINSTANCE.createStatementGoto();
		stmtGoto.setTarget(target);
		return stmtGoto;
	}

	private static boolean isTrue(CgExpression expression) {
		if (expression instanceof ExpressionBoolean) {
			return ((ExpressionBoolean) expression).isValue();
		}
		return false;
	}

	/**
	 * Returns <code>!expression</code>.
	 * 
	 * @param expression
	 * @return
	 */
	public static CgExpression not(CgExpression expression) {
		if (expression instanceof ExpressionUnary) {
			ExpressionUnary unary = (ExpressionUnary) expression;
			if ("!".equals(unary.getOperator())) {
				return copy(unary.getExpression());
			}
		}

		ExpressionUnary not = eINSTANCE.createExpressionUnary();
		not.setExpression(copyIfNeeded(expression));
		not.setOperator("!");
		return not;
	}

	/**
	 * Returns <code>expression != 0</code>.
	 * 
	 * @param expression
	 * @return
	 */
	public static ExpressionBinary notZero(CgExpression expr) {
		ExpressionBinary cmp = eINSTANCE.createExpressionBinary();
		cmp.setLeft(copyIfNeeded(expr));
		cmp.setOperator("!=");
		cmp.setRight(expr(0));
		return cmp;
	}

	/**
	 * Returns a Cx type int&lt;size&gt; or uint&lt;size&gt; depending on the <code>signed</code>
	 * flag.
	 * 
	 * @param size
	 *            size of the type
	 * @param signed
	 *            signed or not
	 * @return a Cx type
	 */
	public static CgType type(int size, boolean signed) {
		TypeDecl type = eINSTANCE.createTypeDecl();
		type.setSpec((signed ? "i" : "u") + size);
		return type;
	}

}
