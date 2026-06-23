/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

/**
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Expr Cast</b></em>'. <!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.ExprBinary#getE1 <em>E1</em>}</li>
 * <li>{@link com.neosyn.models.ir.ExprBinary#getE2 <em>E2</em>}</li>
 * <li>{@link com.neosyn.models.ir.ExprBinary#getOp <em>Op</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getExprBinary()
 * @model
 * @generated
 */
public interface ExprBinary extends Expression {

	/**
	 * Returns the first operand of this binary expression as an expression.
	 * 
	 * @return the first operand of this binary expression
	 * @model containment="true"
	 */
	Expression getE1();

	/**
	 * Returns the second operand of this binary expression as an expression.
	 * 
	 * @return the second operand of this binary expression
	 * @model containment="true"
	 */
	Expression getE2();

	/**
	 * Returns the operator of this binary expression.
	 * 
	 * @return the operator of this binary expression
	 * @model
	 */
	OpBinary getOp();

	/**
	 * Sets the first operand of this binary expression as an expression.
	 * 
	 * @param e1
	 *            the first operand of this binary expression
	 */
	void setE1(Expression e1);

	/**
	 * Sets the second operand of this binary expression as an expression.
	 * 
	 * @param e2
	 *            the second operand of this binary expression
	 */
	void setE2(Expression e2);

	/**
	 * Sets the operator of this binary expression.
	 * 
	 * @param op
	 *            the operator of this binary expression
	 */
	void setOp(OpBinary op);

}
