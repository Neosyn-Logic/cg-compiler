/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.models.ir.ExprBinary
import com.neosyn.models.ir.ExprBool
import com.neosyn.models.ir.ExprInt
import com.neosyn.models.ir.ExprList
import com.neosyn.models.ir.ExprResize
import com.neosyn.models.ir.ExprString
import com.neosyn.models.ir.ExprTernary
import com.neosyn.models.ir.ExprTypeConv
import com.neosyn.models.ir.ExprUnary
import com.neosyn.models.ir.ExprVar
import com.neosyn.models.ir.Expression
import com.neosyn.models.ir.OpBinary
import com.neosyn.models.ir.OpUnary
import com.neosyn.models.ir.TypeInt
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.neosynide.internal.generators.ExpressionPrinter
import com.neosyn.neosynide.internal.generators.Namer

import static extension com.neosyn.models.ir.util.TypeUtil.getSize
import static extension com.neosyn.models.ir.util.TypeUtil.getType

/**
 * This class defines the expression printer for the Verilog code generator.
 * 


 */
class VerilogExpressionPrinter extends ExpressionPrinter {

	val protected Namer namer

	new(Namer namer) {
		this.namer = namer
	}

	override caseExprBinary(ExprBinary expr) {
		val e1 = expr.e1
		val e2 = expr.e2
		val op = expr.op
		val type = TypeUtil.getType(expr)

		if (op == OpBinary.SHIFT_LEFT) {
			'''{«doSwitch(e1)», {(«doSwitch(e2)»){1'b0}}}'''
		} else if (op == OpBinary.SHIFT_RIGHT) {
			// in Verilog, >>> is arithmetic shift, and >> is logical shift
			// this test is because arithmetic is only needed when shifting a signed expression
			// (arithmetic shift of an unsigned number is <=> to logical shift)
			'''(«doSwitch(e1)» «IF type.int && (type as TypeInt).signed»>>>«ELSE»>>«ENDIF» «doSwitch(e2)»)'''
		} else {
			'''(«doSwitch(e1)» «op.text» «doSwitch(e2)»)'''
		}
	}

	override caseExprBool(ExprBool expr) {
		if (expr.isValue()) "1'b1" else "1'b0"
	}

	override caseExprInt(ExprInt expr) {
		'''«expr.computedType.size»'«IF expr.computedType.signed»s«ENDIF»h«expr.value.toString(16)»'''
	}

	override caseExprList(ExprList expr)
		'''«FOR value : expr.value SEPARATOR ", "»«doSwitch(value, Integer.MAX_VALUE, 0)»«ENDFOR»'''

	override caseExprResize(ExprResize cast) {
		throw new UnsupportedOperationException
	}

	override caseExprString(ExprString expr) {
		// do not quote the value, since this is only used in $display
		// Escape special characters (newlines, tabs, etc.) for Verilog string literals
		expr.value
			.replace("\\", "\\\\")  // escape backslashes first
			.replace("\n", "\\n")   // escape newlines
			.replace("\r", "\\r")   // escape carriage returns
			.replace("\t", "\\t")   // escape tabs
			.replace("\"", "\\\"")  // escape quotes
	}

	override caseExprTernary(ExprTernary expr) {
		'''(«doSwitch(expr.cond)» ? «doSwitch(expr.e1)» : «doSwitch(expr.e2)»)'''
	}

	override caseExprTypeConv(ExprTypeConv typeConv) {
		val typeName = typeConv.typeName
		if (typeName == "signed" || typeName == "unsigned") {
			// Collapse directly-nested sign reinterpretations: only the
			// outermost $signed/$unsigned determines signedness in the
			// enclosing context, so $unsigned($unsigned(x)) and
			// $unsigned($signed(x)) are both just $unsigned(x). Removes the
			// redundant double-wrap the cast pipeline produces (e.g. a u8
			// cast feeding a u8 port).
			return '''$«typeName»(«doSwitch(stripSignConv(typeConv.expr))»)'''
		}

		val expr = doSwitch(typeConv.expr)
		val index = typeName.indexOf('.')
		val targetSize = Integer.parseInt(typeName.substring(index + 1))
		val sourceSize = typeConv.expr.type.size

		// Guard against invalid sizes (can happen with unresolved built-in entity port types)
		if (targetSize <= 0 || sourceSize <= 0) {
			// Skip conversion if sizes are invalid
			return expr
		}

		switch (typeName.substring(0, index)) {
			case "trunc": '''«expr»[«targetSize - 1» : 0]'''
			case "sext": '''{{«targetSize - sourceSize»{«expr»[«sourceSize - 1»]}}, «expr»}'''
			case "zext": '''{«targetSize - sourceSize»'b0, «expr»}'''
		}
	}

	override caseExprUnary(ExprUnary expr) {
		val subExpr = doSwitch(expr.expr)

		switch (expr.op) {
		case OpUnary.BITNOT: '''~ («subExpr»)'''
		case OpUnary.LOGIC_NOT: '''! («subExpr»)'''
		default: throw new UnsupportedOperationException
		}
	}

	override caseExprVar(ExprVar expr) {
		namer.getName(expr.use.variable)
	}

	/**
	 * Peel directly-nested $signed/$unsigned conversions, returning the first
	 * inner expression that is not itself a sign reinterpretation. The
	 * outermost cast (handled by the caller) determines the final signedness.
	 */
	def private Expression stripSignConv(Expression e) {
		if (e instanceof ExprTypeConv) {
			val tn = e.typeName
			if (tn == "signed" || tn == "unsigned") {
				return stripSignConv(e.expr)
			}
		}
		return e
	}

}