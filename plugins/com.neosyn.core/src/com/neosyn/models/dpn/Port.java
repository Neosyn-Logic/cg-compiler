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

	/**
	 * Suffix of the frame-boundary signal of a {@code framed} stream port (TRAPS T58): the port
	 * {@code din} carries {@code din_last} beside {@code din_valid}, as AXI-Stream carries TLAST.
	 *
	 * @generated NOT
	 */
	String LAST = "last";

	/**
	 * The frame-boundary signal of this port, or {@code null} if the port is not framed. It travels
	 * WITH the data: an additional input of a framed input port, an additional output of a framed
	 * output port, always after the handshake signal (index 0 is still valid/ready).
	 *
	 * @generated NOT
	 */
	default Var getLast() {
		String name = getName() + "_" + LAST;
		for (Var signal : getDirection() == Direction.INPUT ? getAdditionalInputs() : getAdditionalOutputs()) {
			if (name.equals(signal.getName())) {
				return signal;
			}
		}
		return null;
	}

	/**
	 * Whether this port is a {@code framed} stream (TRAPS T58): it carries a frame boundary.
	 *
	 * @generated NOT
	 */
	default boolean isFramed() {
		return getLast() != null;
	}

	/**
	 * The companion signal suffixes of this port, for wires and port maps: its interface's
	 * ({@code valid}, {@code ready}, ...) and, on a framed port, {@code last} (TRAPS T58). The
	 * interface alone cannot say: framed is a property of the port, not of the interface type.
	 *
	 * @generated NOT
	 */
	default java.util.List<String> getSignals() {
		java.util.List<String> signals = new java.util.ArrayList<>(getInterface().getSignals());
		if (isFramed()) {
			signals.add(LAST);
		}
		return signals;
	}

}
