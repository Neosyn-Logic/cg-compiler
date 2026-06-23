/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import org.eclipse.emf.common.util.EList;

/**
 * This interface defines a Call instruction, which possibly stores the result to a local variable.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstCall extends Instruction {

	/**
	 * Returns the arguments of this call instruction.
	 * 
	 * @return the arguments of this call instruction
	 * @model containment="true"
	 * @generated
	 */
	EList<Expression> getArguments();

	/**
	 * Returns the procedure referenced by this call instruction.
	 * 
	 * @return the procedure referenced by this call instruction
	 * @model
	 */
	Procedure getProcedure();

	/**
	 * Returns the target of this call (may be <code>null</code>).
	 * 
	 * @return the target of this call (may be <code>null</code>)
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Returns <code>true</code> if this call has a result.
	 * 
	 * @return <code>true</code> if this call has a result
	 */
	boolean hasResult();

	/**
	 * Returns the value of the '<em><b>Assert</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Assert</em>' attribute.
	 * @see #setAssert(boolean)
	 * @see com.neosyn.models.ir.IrPackage#getInstCall_Assert()
	 * @model
	 * @generated
	 */
	boolean isAssert();

	/**
	 * Returns the value of the '<em><b>Print</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Print</em>' attribute.
	 * @see #setPrint(boolean)
	 * @see com.neosyn.models.ir.IrPackage#getInstCall_Print()
	 * @model
	 * @generated
	 */
	boolean isPrint();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.InstCall#isAssert <em>Assert</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Assert</em>' attribute.
	 * @see #isAssert()
	 * @generated
	 */
	void setAssert(boolean value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.InstCall#isPrint <em>Print</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Print</em>' attribute.
	 * @see #isPrint()
	 * @generated
	 */
	void setPrint(boolean value);

	/**
	 * Sets the procedure referenced by this call instruction.
	 * 
	 * @param procedure
	 *            a procedure
	 */
	void setProcedure(Procedure procedure);

	/**
	 * Sets the target of this call instruction.
	 * 
	 * @param target
	 *            a local variable (may be <code>null</code>)
	 */
	void setTarget(Def target);

}
