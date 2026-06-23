/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.models.ir.Procedure;

/**
 * This class defines an action.
 * 

 * @author Samuel Keller
 * @model
 */
public interface Action extends EObject {

	/**
	 * Returns the value of the '<em><b>Body</b></em>' containment reference. <!-- begin-user-doc
	 * -->Returns the procedure that holds the body of this action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Body</em>' containment reference.
	 * @see #setBody(Procedure)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_Body()
	 * @model containment="true"
	 * @generated
	 */
	Procedure getBody();

	/**
	 * Returns the value of the '<em><b>Combinational</b></em>' containment reference. <!--
	 * begin-user-doc -->Returns the procedure that holds the combinational assignments to output
	 * ports in this action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Combinational</em>' containment reference.
	 * @see #setCombinational(Procedure)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_Combinational()
	 * @model containment="true"
	 * @generated
	 */
	Procedure getCombinational();

	/**
	 * Returns the value of the '<em><b>Input Pattern</b></em>' containment reference. <!--
	 * begin-user-doc -->Returns the input pattern of this action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Input Pattern</em>' containment reference.
	 * @see #setInputPattern(Pattern)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_InputPattern()
	 * @model containment="true"
	 * @generated
	 */
	Pattern getInputPattern();

	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute. <!-- begin-user-doc --><!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_Name()
	 * @model
	 * @generated
	 */
	String getName();

	/**
	 * Returns the value of the '<em><b>Output Pattern</b></em>' containment reference. <!--
	 * begin-user-doc -->Returns the output pattern of this action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Output Pattern</em>' containment reference.
	 * @see #setOutputPattern(Pattern)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_OutputPattern()
	 * @model containment="true"
	 * @generated
	 */
	Pattern getOutputPattern();

	/**
	 * Returns the value of the '<em><b>Peek Pattern</b></em>' containment reference. <!--
	 * begin-user-doc -->Returns the peek pattern of this action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Peek Pattern</em>' containment reference.
	 * @see #setPeekPattern(Pattern)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_PeekPattern()
	 * @model containment="true"
	 * @generated
	 */
	Pattern getPeekPattern();

	/**
	 * Returns the value of the '<em><b>Scheduler</b></em>' containment reference. <!--
	 * begin-user-doc -->Returns the procedure that holds the scheduling information of this
	 * action.<!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Scheduler</em>' containment reference.
	 * @see #setScheduler(Procedure)
	 * @see com.neosyn.models.dpn.DpnPackage#getAction_Scheduler()
	 * @model containment="true"
	 * @generated
	 */
	Procedure getScheduler();

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Action#getBody <em>Body</em>}'
	 * containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Body</em>' containment reference.
	 * @see #getBody()
	 * @generated
	 */
	void setBody(Procedure value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Action#getCombinational
	 * <em>Combinational</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Combinational</em>' containment reference.
	 * @see #getCombinational()
	 * @generated
	 */
	void setCombinational(Procedure value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Action#getInputPattern
	 * <em>Input Pattern</em>}' containment reference. <!-- begin-user-doc -->Sets the input pattern
	 * of this action to the given pattern.<!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Input Pattern</em>' containment reference.
	 * @see #getInputPattern()
	 * @generated
	 */
	void setInputPattern(Pattern value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Action#getName <em>Name</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

	/**
	 * <!-- begin-user-doc -->Sets the output pattern of this action to the given pattern.<!--
	 * end-user-doc -->
	 * 
	 * @param pattern
	 *            a pattern
	 */
	void setOutputPattern(Pattern pattern);

	/**
	 * <!-- begin-user-doc -->Sets the peek pattern of this action to the given pattern.<!--
	 * end-user-doc -->
	 * 
	 * @param pattern
	 *            a pattern
	 */
	void setPeekPattern(Pattern pattern);

	/**
	 * Sets the value of the '{@link com.neosyn.models.dpn.Action#getScheduler <em>Scheduler</em>}'
	 * containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Scheduler</em>' containment reference.
	 * @see #getScheduler()
	 * @generated
	 */
	void setScheduler(Procedure value);

}
