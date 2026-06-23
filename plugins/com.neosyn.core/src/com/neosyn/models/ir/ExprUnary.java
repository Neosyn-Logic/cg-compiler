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
 * <li>{@link com.neosyn.models.ir.ExprUnary#getExpr <em>Expr</em>}</li>
 * <li>{@link com.neosyn.models.ir.ExprUnary#getOp <em>Op</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getExprUnary()
 * @model
 * @generated
 */
public interface ExprUnary extends Expression {

	/**
	 * Returns the operand of this unary expression as an expression.
	 * 
	 * @return the operand of this unary expression
	 * @model containment="true"
	 */
	Expression getExpr();

	/**
	 * Returns the operator of this unary expression.
	 * 
	 * @return the operator of this unary expression
	 * @model
	 */
	OpUnary getOp();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.ExprUnary#getExpr <em>Expr</em>}'
	 * containment reference. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Expr</em>' containment reference.
	 * @see #getExpr()
	 * @generated
	 */
	void setExpr(Expression value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.ExprUnary#getOp <em>Op</em>}' attribute.
	 * <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Op</em>' attribute.
	 * @see com.neosyn.models.ir.OpUnary
	 * @see #getOp()
	 * @generated
	 */
	void setOp(OpUnary value);

}
