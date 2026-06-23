/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

/**
 * This class defines a procedure.
 * 

 * @model
 */
public interface Procedure extends EObject {

	/**
	 * Returns the value of the '<em><b>Blocks</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.ir.Block}. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Blocks</em>' containment reference list isn't clear, there really
	 * should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Blocks</em>' containment reference list.
	 * @see com.neosyn.models.ir.IrPackage#getProcedure_Blocks()
	 * @model containment="true"
	 * @generated
	 */
	EList<Block> getBlocks();

	/**
	 * Returns the first block in the list of blocks of the given procedure. A new block is created
	 * if there is no block in the given block list.
	 * 
	 * @param procedure
	 *            a procedure
	 * @return a block
	 */
	BlockBasic getFirst();

	/**
	 * Returns the last block in the list of blocks of the given procedure. A new block is created
	 * if there is no block in the given block list.
	 * 
	 * @param procedure
	 *            a procedure
	 * @return a block
	 */
	BlockBasic getLast();

	/**
	 * Returns the line number on which this procedure starts.
	 * 
	 * @return the line number on which this procedure starts
	 * @model
	 */
	public int getLineNumber();

	/**
	 * Returns the local variable of this procedure that has the given name.
	 * 
	 * @param name
	 *            name of the local variable
	 * 
	 * @return the local variable of this procedure that has the given name.
	 */
	Var getLocal(String name);

	/**
	 * Returns the local variables of this procedure as an ordered map.
	 * 
	 * @return the local variables of this procedure as an ordered map
	 * @model containment="true"
	 */
	EList<Var> getLocals();

	/**
	 * Returns the name of this procedure.
	 * 
	 * @return the name of this procedure
	 * @model dataType="org.eclipse.emf.ecore.EString"
	 */
	String getName();

	/**
	 * Returns the parameters of this procedure.
	 * 
	 * @return the parameters of this procedure
	 * @model containment="true"
	 */
	EList<Var> getParameters();

	/**
	 * Returns the return type of this procedure.
	 * 
	 * @return the return type of this procedure
	 * @model containment="true"
	 */
	Type getReturnType();

	/**
	 * Sets the line number on which this procedure starts.
	 * 
	 * @param newLineNumber
	 *            the line number on which this procedure starts
	 */
	void setLineNumber(int newLineNumber);

	/**
	 * Sets the name of this procedure.
	 * 
	 * @param name
	 *            the new name
	 */
	void setName(String name);

	/**
	 * Sets the return type of this procedure
	 * 
	 * @param returnType
	 *            a type
	 */
	void setReturnType(Type returnType);

}
