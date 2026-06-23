/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import com.neosyn.models.graph.Edge;

/**
 * This class represents a connection in a network. A connection can have a number of attributes,
 * that can be types or expressions.
 * 

 * @author Herve Yviquel
 * @model
 */
public interface Connection extends Edge {

	Endpoint getSourceEndpoint();

	/**
	 * Returns the value of the '<em><b>Source Port</b></em>' reference. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Source Port</em>' reference.
	 * @see #setSourcePort(Port)
	 * @see com.neosyn.models.dpn.DpnPackage#getConnection_SourcePort()
	 * @model
	 * @generated
	 */
	Port getSourcePort();

	Endpoint getTargetEndpoint();

	/**
	 * Returns the value of the '<em><b>Target Port</b></em>' reference. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Target Port</em>' reference.
	 * @see #setTargetPort(Port)
	 * @see com.neosyn.models.dpn.DpnPackage#getConnection_TargetPort()
	 * @model
	 * @generated
	 */
	Port getTargetPort();

	/**
	 * @generated
	 */
	void setSourcePort(Port value);

	/**
	 * @generated
	 */
	void setTargetPort(Port value);

}
