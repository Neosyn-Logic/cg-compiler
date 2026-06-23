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
 * This interface defines a definition of a variable.
 * 

 * @model
 */
public interface Def extends EObject {

	/**
	 * Returns the var defined by this definition.
	 * 
	 * @return the var defined by this definition
	 * @model type="Var" opposite="defs"
	 */
	Var getVariable();

	/**
	 * Sets the variable defined by this definition.
	 * 
	 * @param variable
	 *            the variable defined by this definition
	 */
	void setVariable(Var variable);

}
