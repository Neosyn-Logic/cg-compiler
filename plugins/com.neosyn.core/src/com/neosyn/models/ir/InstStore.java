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
 * This interface defines a instruction that Stores data to memory from an expression. The target
 * can be a global (scalar or array), or a local array.
 * 

 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstStore extends Instruction {

	/**
	 * Returns the (possibly empty) list of indexes of this store.
	 * 
	 * @return the (possibly empty) list of indexes of this store
	 * @model containment="true"
	 */
	EList<Expression> getIndexes();

	/**
	 * Returns the target of this store.
	 * 
	 * @return the target of this store
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Returns the value of this store.
	 * 
	 * @return the value of this store
	 * @model containment="true"
	 */
	Expression getValue();

	/**
	 * Sets the target of this store.
	 * 
	 * @param target
	 *            a local variable
	 */
	void setTarget(Def target);

	/**
	 * Sets the value of this store.
	 * 
	 * @param value
	 *            an expression
	 */
	void setValue(Expression value);

}
