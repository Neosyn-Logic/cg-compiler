/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import java.util.List;

import com.neosyn.models.graph.Vertex;

/**
 * <!-- begin-user-doc -->This class defines a state of a Finite State Machine.<!-- end-user-doc -->
 * 

 * @model
 */
public interface State extends Vertex {

	<T> T get(String key);

	List<Action> getActions();

	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see com.neosyn.models.dpn.DpnPackage#getState_Name()
	 * @model
	 * @generated
	 */
	String getName();

	<T> void put(String key, T value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.State#getName <em>Name</em>}' attribute.
	 * <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

}
