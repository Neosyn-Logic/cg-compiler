/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

/**
 * This class defines an integer type.
 * 

 * @author Jerome Gorin
 * @model extends="net.sf.orcc.ir.Type"
 * 
 */
public interface TypeInt extends Type {

	/**
	 * Returns the value of the '<em><b>Size</b></em>' attribute. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Size</em>' attribute.
	 * @see #setSize(int)
	 * @see com.neosyn.models.ir.IrPackage#getTypeInt_Size()
	 * @model
	 * @generated
	 */
	int getSize();

	/**
	 * Returns the value of the '<em><b>Signed</b></em>' attribute. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 * 
	 * @return the value of the '<em>Signed</em>' attribute.
	 * @see #setSigned(boolean)
	 * @see com.neosyn.models.ir.IrPackage#getTypeInt_Signed()
	 * @model
	 * @generated
	 */
	boolean isSigned();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.TypeInt#isSigned <em>Signed</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Signed</em>' attribute.
	 * @see #isSigned()
	 * @generated
	 */
	void setSigned(boolean value);

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.TypeInt#getSize <em>Size</em>}'
	 * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Size</em>' attribute.
	 * @see #getSize()
	 * @generated
	 */
	void setSize(int value);

}
