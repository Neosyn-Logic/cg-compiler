/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import java.util.Iterator;
import java.util.ListIterator;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc --> This class defines a basic block. A basic block only contains
 * instructions.<!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.BlockBasic#getInstructions <em>Instructions</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getBlockBasic()
 * @model
 * @generated
 */
public interface BlockBasic extends Block {

	/**
	 * Appends the specified instruction to the end of this block.
	 * 
	 * @param instruction
	 *            an instruction
	 */
	void add(Instruction instruction);

	/**
	 * Appends the specified instruction to this block at the specified index.
	 * 
	 * @param index
	 *            the index
	 * @param instruction
	 *            an instruction
	 */
	void add(int index, Instruction instruction);

	/**
	 * Returns the value of the '<em><b>Instructions</b></em>' containment reference list. The list
	 * contents are of type {@link com.neosyn.models.ir.Instruction}. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Instructions</em>' containment reference list.
	 * @see com.neosyn.models.ir.IrPackage#getBlockBasic_Instructions()
	 * @model containment="true"
	 * @generated
	 */
	EList<Instruction> getInstructions();

	/**
	 * Returns the index of the given instruction in the list of instructions of this block.
	 * 
	 * @param instruction
	 *            an instruction
	 * @return the index of the given instruction in the list of instructions of this block
	 */
	int indexOf(Instruction instruction);

	Iterator<Instruction> iterator();

	/**
	 * Returns a list iterator over the elements in this list (in proper sequence) that is
	 * positioned after the last instruction.
	 * 
	 * @return a list iterator over the elements in this list (in proper sequence)
	 */
	ListIterator<Instruction> lastListIterator();

	/**
	 * Returns a list iterator over the elements in this list (in proper sequence).
	 * 
	 * @return a list iterator over the elements in this list (in proper sequence)
	 */
	ListIterator<Instruction> listIterator();

	/**
	 * Returns a list iterator over the elements in this list already positioned at index (in proper
	 * sequence).
	 * 
	 * @return a list iterator over the elements in this list already positioned at index (in proper
	 *         sequence)
	 */
	ListIterator<Instruction> listIterator(int index);

}
