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
 * This interface defines a use of a variable.
 * 

 * @model
 */
public interface Use extends EObject {

	/**
	 * Returns the var referenced by this use.
	 * 
	 * @return the var referenced by this use
	 * @model type="Var" opposite="uses"
	 */
	Var getVariable();

	/**
	 * Sets the variable referenced by this use.
	 * 
	 * @param variable
	 *            the variable referenced by this use
	 */
	void setVariable(Var variable);

}
