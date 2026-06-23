/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.graph;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->This class defines an edge. An edge has a source vertex and a target
 * vertex, as well as a list of attributes.<!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.graph.Edge#getLabel <em>Label</em>}</li>
 * <li>{@link com.neosyn.models.graph.Edge#getSource <em>Source</em>}</li>
 * <li>{@link com.neosyn.models.graph.Edge#getTarget <em>Target</em>}</li>
 * <li>{@link com.neosyn.models.graph.Edge#getValue <em>Value</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.graph.GraphPackage#getEdge()
 * @model
 * @generated
 */
public interface Edge extends EObject {

	/**
	 * Returns the value of the '<em><b>Label</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Label</em>' attribute.
	 * @see #setLabel(String)
	 * @see com.neosyn.models.graph.GraphPackage#getEdge_Label()
	 * @model
	 * @generated
	 */
	String getLabel();

	/**
	 * Returns the value of the '<em><b>Source</b></em>' reference. It is bidirectional and its
	 * opposite is '{@link com.neosyn.models.graph.Vertex#getOutgoing <em>Outgoing</em>}'. <!--
	 * begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Source</em>' reference.
	 * @see #setSource(Vertex)
	 * @see com.neosyn.models.graph.GraphPackage#getEdge_Source()
	 * @see com.neosyn.models.graph.Vertex#getOutgoing
	 * @model opposite="outgoing"
	 * @generated
	 */
	Vertex getSource();

	/**
	 * Returns the value of the '<em><b>Target</b></em>' reference. It is bidirectional and its
	 * opposite is '{@link com.neosyn.models.graph.Vertex#getIncoming <em>Incoming</em>}'. <!--
	 * begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Target</em>' reference.
	 * @see #setTarget(Vertex)
	 * @see com.neosyn.models.graph.GraphPackage#getEdge_Target()
	 * @see com.neosyn.models.graph.Vertex#getIncoming
	 * @model opposite="incoming"
	 * @generated
	 */
	Vertex getTarget();

	/**
	 * Sets the value of the '{@link com.neosyn.models.graph.Edge#getLabel <em>Label</em>}'
	 * attribute. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Label</em>' attribute.
	 * @see #getLabel()
	 * @generated
	 */
	void setLabel(String value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.graph.Edge#getSource <em>Source</em>}'
	 * reference. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Source</em>' reference.
	 * @see #getSource()
	 * @generated
	 */
	void setSource(Vertex value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.graph.Edge#getTarget <em>Target</em>}'
	 * reference. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Target</em>' reference.
	 * @see #getTarget()
	 * @generated
	 */
	void setTarget(Vertex value);

	/**
	 * Returns the value of the '<em><b>Value</b></em>' reference. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Value</em>' reference isn't clear, there really should be more of
	 * a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Value</em>' reference.
	 * @see #setValue(EObject)
	 * @see com.neosyn.models.graph.GraphPackage#getEdge_Value()
	 * @model
	 * @generated
	 */
	EObject getValue();

	/**
	 * Sets the value of the '{@link com.neosyn.models.graph.Edge#getValue <em>Value</em>}'
	 * reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Value</em>' reference.
	 * @see #getValue()
	 * @generated
	 */
	void setValue(EObject value);

}
