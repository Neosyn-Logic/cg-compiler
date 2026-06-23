/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.cg.CgExpression;
import com.neosyn.models.ir.Expression;

/**
 * This interface defines a method for a class that can transform a Cx expression into an IR
 * expression.
 * 

 * 
 */
public interface Transformer {

	EObject doSwitch(EObject eObject);

	/**
	 * Transforms the given expression without assigning to a particular target.
	 * 
	 * @param expression
	 *            an AST expression
	 * @return an IR expression
	 */
	Expression transformExpr(CgExpression expression);

}
