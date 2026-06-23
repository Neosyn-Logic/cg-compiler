/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import com.google.gson.JsonObject;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;

/**
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Entity</b></em>'. <!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.dpn.Entity#getFileName <em>File Name</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getInputs <em>Inputs</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getLineNumber <em>Line Number</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getName <em>Name</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getOutputs <em>Outputs</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getProperties <em>Properties</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getProcedures <em>Procedures</em>}</li>
 * <li>{@link com.neosyn.models.dpn.Entity#getVariables <em>Variables</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.dpn.DpnPackage#getEntity()
 * @model
 * @generated
 */
public interface Entity extends EObject {

	/**
	 * Returns the file this network is defined in.
	 * 
	 * @return the file this network is defined in
	 */
	IFile getFile();

	/**
	 * Returns the value of the '<em><b>File Name</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>File Name</em>' attribute.
	 * @see #setFileName(String)
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_FileName()
	 * @model
	 * @generated
	 */
	String getFileName();

	/**
	 * Returns the value of the '<em><b>Inputs</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.dpn.Port}. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Inputs</em>' reference list isn't clear, there really should be
	 * more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Inputs</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Inputs()
	 * @model containment="true"
	 * @generated
	 */
	EList<Port> getInputs();

	/**
	 * Returns the value of the '<em><b>Line Number</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Line Number</em>' attribute.
	 * @see #setLineNumber(int)
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_LineNumber()
	 * @model
	 * @generated
	 */
	int getLineNumber();

	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Name</em>' attribute isn't clear, there really should be more of a
	 * description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Name()
	 * @model
	 * @generated
	 */
	String getName();

	/**
	 * Returns the value of the '<em><b>Outputs</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.dpn.Port}. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Outputs</em>' reference list isn't clear, there really should be
	 * more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Outputs</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Outputs()
	 * @model containment="true"
	 * @generated
	 */
	EList<Port> getOutputs();

	String getPackage();

	/**
	 * Returns a procedure of this actor whose name matches the given name.
	 * 
	 * @param name
	 *            the procedure name
	 * @return a procedure whose name matches the given name
	 */
	Procedure getProcedure(String name);

	/**
	 * Returns the value of the '<em><b>Procedures</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.ir.Procedure}. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Procedures</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Procedures()
	 * @model containment="true"
	 * @generated
	 */
	EList<Procedure> getProcedures();

	/**
	 * Returns the value of the '<em><b>Properties</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Properties</em>' attribute.
	 * @see #setProperties(JsonObject)
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Properties()
	 * @model dataType="com.neosyn.models.dpn.JsonObject"
	 * @generated
	 */
	JsonObject getProperties();

	/**
	 * Returns the last component of the qualified name returned by {@link #getName()}.
	 * 
	 * @return an unqualified name
	 */
	String getSimpleName();

	/**
	 * Returns the variable with the given name.
	 * 
	 * @param name
	 *            name of a variable
	 * @return the variable with the given name
	 */
	Var getVariable(String name);

	/**
	 * Returns the value of the '<em><b>Variables</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.ir.Var}. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Parameters</em>' reference list isn't clear, there really should
	 * be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Variables</em>' containment reference list.
	 * @see com.neosyn.models.dpn.DpnPackage#getEntity_Variables()
	 * @model containment="true"
	 * @generated
	 */
	EList<Var> getVariables();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Entity#getFileName <em>File Name</em>}'
	 * attribute. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>File Name</em>' attribute.
	 * @see #getFileName()
	 * @generated
	 */
	void setFileName(String value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Entity#getLineNumber
	 * <em>Line Number</em>}' attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Line Number</em>' attribute.
	 * @see #getLineNumber()
	 * @generated
	 */
	void setLineNumber(int value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Entity#getName <em>Name</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Entity#getProperties <em>Properties</em>
	 * }' attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Properties</em>' attribute.
	 * @see #getProperties()
	 * @generated
	 */
	void setProperties(JsonObject value);

} // Entity
