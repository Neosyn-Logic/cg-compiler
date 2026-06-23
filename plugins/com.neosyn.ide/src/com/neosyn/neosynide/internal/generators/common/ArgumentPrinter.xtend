/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.common

import com.neosyn.models.ir.ExprBool
import com.neosyn.models.ir.ExprInt
import com.neosyn.models.ir.ExprString
import com.neosyn.models.ir.util.IrSwitch

/**
 * Prints instance argument values for HDL generation.
 *
 * <p>This class handles the conversion of IR expressions to HDL-appropriate
 * string representations. The boolean format varies by target language:</p>
 * <ul>
 *   <li>Verilog: {@code 1} / {@code 0}</li>
 *   <li>VHDL: {@code true} / {@code false}</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * // For Verilog
 * val printer = new ArgumentPrinter(BoolFormat.VERILOG)
 * val text = printer.doSwitch(argument.value)
 *
 * // For VHDL
 * val printer = new ArgumentPrinter(BoolFormat.VHDL)
 * val text = printer.doSwitch(argument.value)
 * </pre>
 */
class ArgumentPrinter extends IrSwitch<CharSequence> {

	/**
	 * Boolean format for different HDL languages.
	 */
	enum BoolFormat {
		/** Verilog format: 1 for true, 0 for false */
		VERILOG,
		/** VHDL format: true/false strings */
		VHDL
	}

	val BoolFormat boolFormat

	/**
	 * Creates an argument printer with the specified boolean format.
	 *
	 * @param boolFormat the format for boolean values
	 */
	new(BoolFormat boolFormat) {
		this.boolFormat = boolFormat
	}

	override caseExprBool(ExprBool expr) {
		switch boolFormat {
			case VERILOG: if (expr.value) '1' else '0'
			case VHDL: expr.value.toString
		}
	}

	override caseExprInt(ExprInt expr) {
		expr.value.toString
	}

	override caseExprString(ExprString expr) {
		'''"«expr.value»"'''
	}

}
