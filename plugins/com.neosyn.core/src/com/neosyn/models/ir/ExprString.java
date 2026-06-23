/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir;

import java.lang.String;

/**
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Expr Cast</b></em>'. <!--
 * end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.ir.ExprString#getValue <em>Value</em>}</li>
 * </ul>
 *
 * @see com.neosyn.models.ir.IrPackage#getExprString()
 * @model
 * @generated
 */
public interface ExprString extends Expression {

	/**
	 * Returns the value of this string expression.
	 * 
	 * @return the value of this string expression
	 * @model dataType="org.eclipse.emf.ecore.EString"
	 */
	String getValue();

	void setValue(String value);

}
