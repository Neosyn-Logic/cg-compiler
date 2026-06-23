/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import com.neosyn.models.ir.Instruction;

/**
 * <!-- begin-user-doc -->This class defines a state of a Finite State Machine.<!-- end-user-doc -->
 * 

 * @model
 */
public interface Goto extends Instruction {

	/**
	 * Returns the value of the '<em><b>Target</b></em>' reference. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Target</em>' reference.
	 * @see #setTarget(State)
	 * @see com.neosyn.models.dpn.DpnPackage#getGoto_Target()
	 * @model
	 * @generated
	 */
	State getTarget();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Goto#getTarget <em>Target</em>}'
	 * reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Target</em>' reference.
	 * @see #getTarget()
	 * @generated
	 */
	void setTarget(State value);

}
