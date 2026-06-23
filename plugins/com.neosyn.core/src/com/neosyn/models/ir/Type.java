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
 * This interface defines a type.
 * 

 * @author Jerome Gorin
 * @model abstract="true"
 * 
 */
public interface Type extends EObject {

	/**
	 * Returns true if this type is <tt>List</tt>.
	 * 
	 * @return true if this type is <tt>List</tt>
	 */
	boolean isArray();

	/**
	 * Returns true if this type is <tt>bool</tt>.
	 * 
	 * @return true if this type is <tt>bool</tt>
	 */
	boolean isBool();

	/**
	 * Returns true if this type is <tt>float</tt>.
	 * 
	 * @return true if this type is <tt>float</tt>
	 */
	boolean isFloat();

	/**
	 * Returns true if this type is <tt>int</tt>.
	 * 
	 * @return true if this type is <tt>int</tt>
	 */
	boolean isInt();

	/**
	 * Returns true if this type is <tt>String</tt>.
	 * 
	 * @return true if this type is <tt>String</tt>
	 */
	boolean isString();

	/**
	 * Returns true if this type is <tt>void</tt>.
	 * 
	 * @return true if this type is <tt>void</tt>
	 */
	boolean isVoid();

}
