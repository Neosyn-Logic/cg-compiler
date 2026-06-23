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

import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.Var;

/**
 * This interface defines an argument that can be given to an instance.
 * 

 * @model
 */
public interface Argument extends EObject {

	/**
	 * Returns the value of this argument.
	 * 
	 * @return the value of this argument
	 * @model containment="true"
	 */
	Expression getValue();

	/**
	 * Returns the variable to which this argument associates a value.
	 * 
	 * @return the variable to which this argument associates a value
	 * @model
	 */
	Var getVariable();

	/**
	 * Sets the value of this attribute.
	 * 
	 * @param newValue
	 *            a value
	 */
	void setValue(Expression newValue);

	/**
	 * Sets the variable to which this argument associates a value.
	 * 
	 * @param newVariable
	 *            a variable
	 */
	void setVariable(Var newVariable);

}
