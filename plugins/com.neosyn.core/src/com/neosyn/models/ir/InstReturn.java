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
 * This interface defines a Return instruction.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstReturn extends Instruction {

	/**
	 * Returns the value returned by this return instruction (may be <code>null</code>).
	 * 
	 * @return the value returned by this return instruction (may be <code>null</code>)
	 * @model containment="true"
	 */
	Expression getValue();

	/**
	 * Sets the value returned by this instruction.
	 * 
	 * @param value
	 *            an expression
	 */
	void setValue(Expression value);

}
