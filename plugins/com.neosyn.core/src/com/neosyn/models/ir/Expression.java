/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import org.eclipse.emf.ecore.EObject;

/**
 * This interface defines an expression.
 * 

 * @model abstract="true"
 * 
 */
public interface Expression extends EObject {

	/**
	 * Returns the value of the '<em><b>Computed Type</b></em>' containment reference. <!--
	 * begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Computed Type</em>' containment reference isn't clear, there
	 * really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Computed Type</em>' containment reference.
	 * @see #setComputedType(Type)
	 * @see com.neosyn.models.ir.IrPackage#getExprBinary_ComputedType()
	 * @model containment="true" transient="true"
	 * @generated
	 */
	Type getComputedType();

	/**
	 * Returns true if the expression is a binary expression.
	 * 
	 * @return true if the expression is a binary expression
	 */
	boolean isExprBinary();

	/**
	 * Returns true if the expression is a boolean expression.
	 * 
	 * @return true if the expression is a boolean expression
	 */
	boolean isExprBool();

	/**
	 * Returns true if the expression is a float expression.
	 * 
	 * @return true if the expression is a float expression
	 */
	boolean isExprFloat();

	/**
	 * Returns true if the expression is an integer expression.
	 * 
	 * @return true if the expression is an integer expression
	 */
	boolean isExprInt();

	/**
	 * Returns true if the expression is a list expression.
	 * 
	 * @return true if the expression is a list expression
	 */
	boolean isExprList();

	boolean isExprResize();

	/**
	 * Returns true if the expression is a string expression.
	 * 
	 * @return true if the expression is a string expression
	 */
	boolean isExprString();

	boolean isExprTypeConv();

	/**
	 * Returns true if the expression is a unary expression.
	 * 
	 * @return true if the expression is a unary expression
	 */
	boolean isExprUnary();

	/**
	 * Returns true if the expression is a variable expression.
	 * 
	 * @return true if the expression is a variable expression
	 */
	boolean isExprVar();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.Expression#getComputedType
	 * <em>Computed Type</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Computed Type</em>' containment reference.
	 * @see #getComputedType()
	 * @generated
	 */
	void setComputedType(Type value);

}
