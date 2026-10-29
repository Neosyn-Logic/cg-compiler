/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.models.ir.ExprBool
import com.neosyn.models.ir.ExprFloat
import com.neosyn.models.ir.ExprInt
import com.neosyn.models.ir.ExprList
import com.neosyn.models.ir.util.IrSwitch

import static extension com.neosyn.neosynide.internal.generators.GeneratorExtensions.getNumberOfHexadecimalDigits

/**
 * Expression printer for the {@code .hex} files that back {@code $readmemh}-initialised
 * arrays: it prints boolean values as integers ("1" for <code>true</code>, "0" for
 * <code>false</code>).
 */
class VerilogHexPrinter extends IrSwitch<CharSequence> {

	val int numDigits

	val int size

	new(int size) {
		this.size = size
		numDigits = size.numberOfHexadecimalDigits
	}

	override caseExprBool(ExprBool expr) {
		if (expr.value) {
			"1"
		} else {
			"0"
		}
	}

	override caseExprFloat(ExprFloat expr) {
		expr.value.stripTrailingZeros.toString
	}

	override caseExprInt(ExprInt expr) {
		val value = if (expr.value < 0bi) {
				expr.value + 1bi.shiftLeft(size)
			} else {
				expr.value // print hexadecimal format with the correct size
			}

		String.format("%0" + numDigits + "x", value)
	}

	override caseExprList(ExprList expr) '''
		«FOR value : expr.value»
			«doSwitch(value)»
		«ENDFOR»
	'''

}
