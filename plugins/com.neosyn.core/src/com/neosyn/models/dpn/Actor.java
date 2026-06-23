/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc --> This class defines an actor. An actor has parameters, input and output
 * ports, state variables, procedures, actions and an FSM.<!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.dpn.Actor#getActions <em>Actions</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Actor#getBufferedInputs <em>Buffered Inputs</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Actor#getFsm <em>Fsm</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.dpn.DpnPackage#getActor()
 * @model
 * @generated
 */
public interface Actor extends Entity {

	/**
	 * Returns the value of the '<em><b>Actions</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.dpn.Action}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Actions</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getActor_Actions()
	 * @model containment="true"
	 * @generated
	 */
	EList<Action> getActions();

	/**
	 * Returns the value of the '<em><b>Buffered Inputs</b></em>' reference list. The list contents
	 * are of type {@link com.neosyn.models.dpn.Port}. <!-- begin-user-doc -->Returns the list of
	 * buffered input ready ports.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Buffered Inputs</em>' reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getActor_BufferedInputs()
	 * @model
	 * @generated
	 */
	EList<Port> getBufferedInputs();

	/**
	 * Returns the value of the '<em><b>Fsm</b></em>' containment reference. <!-- begin-user-doc
	 * --><!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Fsm</em>' containment reference.
	 * @see #setFsm(FSM)
	 * @see com.neosyn.models.dpn.DpnPackage#getActor_Fsm()
	 * @model containment="true"
	 * @generated
	 */
	FSM getFsm();

	/**
	 * Returns true if this actor has an FSM.
	 * 
	 * @return true if this actor has an FSM
	 */
	boolean hasFsm();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Actor#getFsm <em>Fsm</em>}' containment
	 * reference. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Fsm</em>' containment reference.
	 * @see #getFsm()
	 * @generated
	 */
	void setFsm(FSM value);

}
