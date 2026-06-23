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
 * This interface defines an assignment of the result of a <code>phi</code> function to a target
 * local variable.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstPhi extends Instruction {

	/**
	 * Returns the "old" variable of this phi. Only used when translating to SSA form.
	 * 
	 * @return the "old" variable of this phi
	 * @model
	 */
	Var getOldVariable();

	/**
	 * Returns the target of this call (may be <code>null</code>).
	 * 
	 * @return the target of this phi assignment (may be <code>null</code>)
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Returns the values of this phi instruction.
	 * 
	 * @return the values of this phi instruction
	 * @model containment="true"
	 */
	EList<Expression> getValues();

	/**
	 * Sets the "old" variable to be remembered when examining the "else" branch of an if. Only used
	 * when translating to SSA form.
	 * 
	 * @param old
	 *            an "old" variable
	 */
	void setOldVariable(Var old);

	/**
	 * Sets the target of this phi assignment.
	 * 
	 * @param target
	 *            a local variable
	 */
	void setTarget(Def target);

}
