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

/**
 * <!-- begin-user-doc -->This class defines a While block. A while block is a block with a value
 * used in its condition.<!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.BlockWhile#getCondition <em>Condition</em>}</li>
 * <li>{@link com.neosyn.models.ir.BlockWhile#getJoinBlock <em>Join Block</em>}</li>
 * <li>{@link com.neosyn.models.ir.BlockWhile#getLineNumber <em>Line Number</em>}</li>
 * <li>{@link com.neosyn.models.ir.BlockWhile#getBlocks <em>Blocks</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getBlockWhile()
 * @model
 * @generated
 */
public interface BlockWhile extends Block {

	/**
	 * Returns the value of the '<em><b>Condition</b></em>' containment reference. <!--
	 * begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Condition</em>' containment reference.
	 * @see #setCondition(Expression)
	 * @see com.neosyn.models.ir.IrPackage#getBlockWhile_Condition()
	 * @model containment="true"
	 * @generated
	 */
	Expression getCondition();

	/**
	 * Returns the value of the '<em><b>Line Number</b></em>' attribute. <!-- begin-user-doc
	 * -->Returns the line number on which this "while" starts.
	 * 
	 * @return the line number on which this "while" starts<!-- end-user-doc -->
	 * @return the value of the '<em>Line Number</em>' attribute.
	 * @see #setLineNumber(int)
	 * @see com.neosyn.models.ir.IrPackage#getBlockWhile_LineNumber()
	 * @model
	 * @generated
	 */
	int getLineNumber();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.BlockWhile#getCondition
	 * <em>Condition</em>}' containment reference. <!-- begin-user-doc --><!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Condition</em>' containment reference.
	 * @see #getCondition()
	 * @generated
	 */
	void setCondition(Expression value);

	/**
	 * Returns the value of the '<em><b>Join Block</b></em>' containment reference. <!--
	 * begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Join Block</em>' containment reference isn't clear, there really
	 * should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Join Block</em>' containment reference.
	 * @see #setJoinBlock(BlockBasic)
	 * @see com.neosyn.models.ir.IrPackage#getBlockWhile_JoinBlock()
	 * @model containment="true"
	 * @generated
	 */
	BlockBasic getJoinBlock();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.BlockWhile#getJoinBlock
	 * <em>Join Block</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Join Block</em>' containment reference.
	 * @see #getJoinBlock()
	 * @generated
	 */
	void setJoinBlock(BlockBasic value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.BlockWhile#getLineNumber
	 * <em>Line Number</em>}' attribute. <!-- begin-user-doc -->Sets the line number on which this
	 * "while" starts. <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Line Number</em>' attribute.
	 * @see #getLineNumber()
	 * @generated
	 */
	void setLineNumber(int value);

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
	 * @see com.neosyn.models.ir.IrPackage#getBlockWhile_Blocks()
	 * @model containment="true"
	 * @generated
	 */
	EList<Block> getBlocks();

}
