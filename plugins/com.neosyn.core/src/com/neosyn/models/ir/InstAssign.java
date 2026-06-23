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
 * This interface defines an Assign instruction. The target is a local variable, and the value an
 * expression.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstAssign extends Instruction {

	/**
	 * Returns the target of this assignment.
	 * 
	 * @return the target of this assignment
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Returns the value of this assignment.
	 * 
	 * @return the value of this assignment
	 * @model containment="true"
	 */
	Expression getValue();

	/**
	 * Sets the target of this assignment.
	 * 
	 * @param target
	 *            a local variable
	 */
	void setTarget(Def target);

	/**
	 * Sets the value of this assignment. Uses are updated to point to this assignment.
	 * 
	 * @param value
	 *            an expression
	 */
	void setValue(Expression value);

}
