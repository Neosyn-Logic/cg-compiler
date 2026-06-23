/*******************************************************************************
 * Copyright (c) 2013-2014 Neosyn.
 
 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * History:
 *    Originally developed by Synflow until February 2021 under LGPL.
 *******************************************************************************/
package com.neosyn.models.ir;

import java.lang.Integer;
import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc --> An implementation of the model object ' <em><b>Var</b></em>'. <!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.TypeArray#getDimensions <em>Dimensions</em>}</li>
 * <li>{@link com.neosyn.models.ir.TypeArray#getElementType <em>Element Type</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getTypeArray()
 * @model
 * @generated
 */
public interface TypeArray extends Type {

	/**
	 * Returns the value of the '<em><b>Dimensions</b></em>' attribute list. The list contents are
	 * of type {@link java.lang.Integer}. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Dimensions</em>' attribute list.
	 * @see com.neosyn.models.ir.IrPackage#getTypeArray_Dimensions()
	 * @model unique="false"
	 * @generated
	 */
	EList<Integer> getDimensions();

	/**
	 * Returns the value of the '<em><b>Element Type</b></em>' containment reference. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Element Type</em>' containment reference.
	 * @see #setElementType(Type)
	 * @see com.neosyn.models.ir.IrPackage#getTypeArray_ElementType()
	 * @model containment="true"
	 * @generated
	 */
	Type getElementType();

	/**
	 * Sets the value of the '{@link com.neosyn.models.ir.TypeArray#getElementType
	 * <em>Element Type</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Element Type</em>' containment reference.
	 * @see #getElementType()
	 * @generated
	 */
	void setElementType(Type value);

}
