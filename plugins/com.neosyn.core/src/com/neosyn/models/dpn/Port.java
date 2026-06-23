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
import com.neosyn.models.ir.Var;

/**
 * <!-- begin-user-doc -->This class defines a port. A port has a location, a type, a name.<!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.dpn.Port#getAdditionalInputs <em>Additional Inputs</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Port#getAdditionalOutputs <em>Additional Outputs</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Port#getInterface <em>Interface</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Port#isSynchronous <em>Synchronous</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.dpn.DpnPackage#getPort()
 * @model
 * @generated
 */
public interface Port extends Var {

	/**
	 * Returns the value of the '<em><b>Additional Inputs</b></em>' containment reference list. The
	 * list contents are of type {@link com.neosyn.models.ir.Var}. <!-- begin-user-doc -->Returns
	 * additional input variables for +valid +ready ports.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Additional Inputs</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getPort_AdditionalInputs()
	 * @model containment="true"
	 * @generated
	 */
	EList<Var> getAdditionalInputs();

	/**
	 * Returns the value of the '<em><b>Additional Outputs</b></em>' containment reference list. The
	 * list contents are of type {@link com.neosyn.models.ir.Var}. <!-- begin-user-doc -->Returns
	 * additional output variables for +valid +ready ports.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Additional Outputs</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getPort_AdditionalOutputs()
	 * @model containment="true"
	 * @generated
	 */
	EList<Var> getAdditionalOutputs();

	/**
	 * Returns the direction of this port. The port must be contained in an entity, or this method
	 * throws an exception.
	 * 
	 * @return a direction
	 */
	Direction getDirection();

	/**
	 * Returns the value of the '<em><b>Interface</b></em>' attribute. The literals are from the
	 * enumeration {@link com.neosyn.models.dpn.InterfaceType}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Interface</em>' attribute.
	 * @see com.neosyn.models.dpn.InterfaceType
	 * @see #setInterface(InterfaceType)
	 * @see com.neosyn.models.dpn.DpnPackage#getPort_Interface()
	 * @model
	 * @generated
	 */
	InterfaceType getInterface();

	/**
	 * Returns the value of the '<em><b>Synchronous</b></em>' attribute. The default value is
	 * <code>"true"</code>. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Synchronous</em>' attribute.
	 * @see #setSynchronous(boolean)
	 * @see com.neosyn.models.dpn.DpnPackage#getPort_Synchronous()
	 * @model default="true"
	 * @generated
	 */
	boolean isSynchronous();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Port#getInterface <em>Interface</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Interface</em>' attribute.
	 * @see com.neosyn.models.dpn.InterfaceType
	 * @see #getInterface()
	 * @generated
	 */
	void setInterface(InterfaceType value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Port#isSynchronous <em>Synchronous</em>}
	 * ' attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Synchronous</em>' attribute.
	 * @see #isSynchronous()
	 * @generated
	 */
	void setSynchronous(boolean value);

}
