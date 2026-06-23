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
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Expr Ternary</b></em>'. <!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.ExprTernary#getCond <em>Cond</em>}</li>
 * <li>{@link com.neosyn.models.ir.ExprTernary#getE1 <em>E1</em>}</li>
 * <li>{@link com.neosyn.models.ir.ExprTernary#getE2 <em>E2</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getExprTernary()
 * @model
 * @generated
 */
public interface ExprTernary extends Expression {
	/**
	 * Returns the value of the '<em><b>Cond</b></em>' containment reference. <!-- begin-user-doc
	 * -->
	 * <p>
	 * If the meaning of the '<em>Cond</em>' containment reference isn't clear, there really should
	 * be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Cond</em>' containment reference.
	 * @see #setCond(Expression)
	 * @see com.neosyn.models.ir.IrPackage#getExprTernary_Cond()
	 * @model containment="true"
	 * @generated
	 */
	Expression getCond();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.ExprTernary#getCond <em>Cond</em>}'
	 * containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Cond</em>' containment reference.
	 * @see #getCond()
	 * @generated
	 */
	void setCond(Expression value);

	/**
	 * Returns the value of the '<em><b>E1</b></em>' containment reference. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>E1</em>' containment reference isn't clear, there really should be
	 * more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>E1</em>' containment reference.
	 * @see #setE1(Expression)
	 * @see com.neosyn.models.ir.IrPackage#getExprTernary_E1()
	 * @model containment="true"
	 * @generated
	 */
	Expression getE1();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.ExprTernary#getE1 <em>E1</em>}'
	 * containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>E1</em>' containment reference.
	 * @see #getE1()
	 * @generated
	 */
	void setE1(Expression value);

	/**
	 * Returns the value of the '<em><b>E2</b></em>' containment reference. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>E2</em>' containment reference isn't clear, there really should be
	 * more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>E2</em>' containment reference.
	 * @see #setE2(Expression)
	 * @see com.neosyn.models.ir.IrPackage#getExprTernary_E2()
	 * @model containment="true"
	 * @generated
	 */
	Expression getE2();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.ExprTernary#getE2 <em>E2</em>}'
	 * containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>E2</em>' containment reference.
	 * @see #getE2()
	 * @generated
	 */
	void setE2(Expression value);

} // ExprTernary
