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
 * This interface defines an instruction that Loads data from memory to a local variable. The source
 * can be a global (scalar or array), or a local array.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstLoad extends Instruction {

	/**
	 * Returns the (possibly empty) list of indexes of this load.
	 * 
	 * @return the (possibly empty) list of indexes of this load
	 * @model containment="true"
	 */
	EList<Expression> getIndexes();

	/**
	 * Returns the variable loaded by this load instruction.
	 * 
	 * @return the variable loaded by this load instruction
	 * @model containment="true"
	 */
	Use getSource();

	/**
	 * Returns the target of this load instruction.
	 * 
	 * @return the target of this load instruction
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Sets the variable loaded by this load instruction.
	 * 
	 * @param source
	 *            the variable loaded by this load instruction
	 */
	void setSource(Use source);

	/**
	 * Sets the target of this load.
	 * 
	 * @param target
	 *            a local variable
	 */
	void setTarget(Def target);

}
