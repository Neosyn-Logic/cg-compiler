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
 * <!-- begin-user-doc --> This class defines a block.<!-- end-user-doc -->
 *
 *
 * @see com.neosyn.models.ir.IrPackage#getBlock()
 * @model abstract="true"
 * @generated
 */
public interface Block extends EObject {

	/**
	 * Returns <code>true</code> if this block is a BlockBasic.
	 * 
	 * @return <code>true</code> if this block is a BlockBasic
	 */
	boolean isBlockBasic();

	/**
	 * Returns <code>true</code> if this block is an BlockIf.
	 * 
	 * @return <code>true</code> if this block is an BlockIf
	 */
	boolean isBlockIf();

	/**
	 * Returns <code>true</code> if this block is a BlockWhile.
	 * 
	 * @return <code>true</code> if this block is a BlockWhile
	 */
	boolean isBlockWhile();

}
