/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import org.eclipse.emf.ecore.EObject;

/**
 * This class defines an instruction.
 * 

 * @model abstract="true"
 */
public interface Instruction extends EObject {

	/**
	 * Returns the block that contains this instruction.
	 * 
	 * @return the block that contains this instruction
	 */
	BlockBasic getBlock();

	/**
	 * Returns the line number of this instruction.
	 * 
	 * @return the line number of this instruction
	 * @model
	 */
	public int getLineNumber();

	/**
	 * Returns <code>true</code> if the instruction is an Assign.
	 * 
	 * @return <code>true</code> if the instruction is an Assign
	 */
	boolean isInstAssign();

	/**
	 * Returns <code>true</code> if the instruction is a Call.
	 * 
	 * @return <code>true</code> if the instruction is a Call
	 */
	boolean isInstCall();

	/**
	 * Returns <code>true</code> if the instruction is a Load.
	 * 
	 * @return <code>true</code> if the instruction is a Load
	 */
	boolean isInstLoad();

	/**
	 * Returns <code>true</code> if the instruction is a Phi.
	 * 
	 * @return <code>true</code> if the instruction is a Phi
	 */
	boolean isInstPhi();

	/**
	 * Returns <code>true</code> if the instruction is a Return.
	 * 
	 * @return <code>true</code> if the instruction is a Return
	 */
	boolean isInstReturn();

	/**
	 * Return <code>true</code> if the instruction is a backend specific instruction
	 * 
	 * @return <code>true</code> if the instruction is a backend specific instruction
	 */
	boolean isInstSpecific();

	/**
	 * Returns <code>true</code> if the instruction is a Store.
	 * 
	 * @return <code>true</code> if the instruction is a Store
	 */
	boolean isInstStore();

	/**
	 * Sets the line number of this instruction.
	 * 
	 * @param newLineNumber
	 *            the line number of this instruction
	 */
	void setLineNumber(int newLineNumber);

}
