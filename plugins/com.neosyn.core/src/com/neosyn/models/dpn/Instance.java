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
import com.google.gson.JsonObject;
import com.neosyn.models.graph.Vertex;

/**
 * <!-- begin-user-doc --><!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.dpn.Instance#getArguments <em>Arguments</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Instance#getEntity <em>Entity</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Instance#getName <em>Name</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Instance#getProperties <em>Properties</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.dpn.DpnPackage#getInstance()
 * @model
 * @generated
 */
public interface Instance extends Vertex {

	/**
	 * Returns the argument of this instance that has the given name, or <code>null</code> if no
	 * such argument exists.
	 * 
	 * @param name
	 *            name of an argument
	 * @return an argument, or <code>null</code>
	 */
	Argument getArgument(String name);

	/**
	 * Returns the value of the '<em><b>Arguments</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.dpn.Argument}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Arguments</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getInstance_Arguments()
	 * @model containment="true"
	 * @generated
	 */
	EList<Argument> getArguments();

	DPN getDPN();

	/**
	 * Returns the value of the '<em><b>Entity</b></em>' reference. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Entity</em>' reference.
	 * @see #setEntity(Entity)
	 * @see com.neosyn.models.dpn.DpnPackage#getInstance_Entity()
	 * @model
	 * @generated
	 */
	Entity getEntity();

	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see com.neosyn.models.dpn.DpnPackage#getInstance_Name()
	 * @model
	 * @generated
	 */
	String getName();

	/**
	 * Returns the value of the '<em><b>Properties</b></em>' attribute. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Properties</em>' attribute isn't clear, there really should be
	 * more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Properties</em>' attribute.
	 * @see #setProperties(JsonObject)
	 * @see com.neosyn.models.dpn.DpnPackage#getInstance_Properties()
	 * @model dataType="com.neosyn.models.dpn.JsonObject"
	 * @generated
	 */
	JsonObject getProperties();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Instance#getEntity <em>Entity</em>}'
	 * reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Entity</em>' reference.
	 * @see #getEntity()
	 * @generated
	 */
	void setEntity(Entity value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Instance#getName <em>Name</em>}'
	 * attribute. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Instance#getProperties
	 * <em>Properties</em>}' attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Properties</em>' attribute.
	 * @see #getProperties()
	 * @generated
	 */
	void setProperties(JsonObject value);

}
