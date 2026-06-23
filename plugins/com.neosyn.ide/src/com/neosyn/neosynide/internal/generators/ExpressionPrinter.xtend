/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators

import com.neosyn.models.ir.Expression
import com.neosyn.models.ir.util.IrSwitch

/**
 * This class defines common methods to expression printer in code generators.
 * 

 */
class ExpressionPrinter extends IrSwitch<CharSequence> {

	protected int branch = 0 // left

	protected int precedence = Integer.MAX_VALUE

	def doSwitch(Expression expression, int newPrecedence, int newBranch) {
		val oldBranch = branch
		val oldPrecedence = precedence

		branch = newBranch
		precedence = newPrecedence

		val result = doSwitch(expression)

		precedence = oldPrecedence
		branch = oldBranch

		result
	}

}
