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

		// A one-bit value tested against zero is that bit: `data[0]`, not `(data[0 : 0] != 1'h0)`
		// (TRAPS T116, 147 sites) -- the same one-bit unsigned value for every input, X included.
		if (op == OpBinary.NE && e2 instanceof ExprInt && (e2 as ExprInt).value.signum == 0
				&& TypeUtil.getType(e1).int && (TypeUtil.getType(e1) as TypeInt).size == 1) {
			return doSwitch(e1)
		}
		// A constant shift amount is written as the number it is: `{x, 1'b0}` rather than
		// `{x, {(1'h1){1'b0}}}` (7497 sites), `x >> 1` rather than `x >> 8'h1` (2423) -- a shift
		// amount is self-determined in Verilog, so its width never mattered (TRAPS T116).
		val amount = if (e2 instanceof ExprInt && (e2 as ExprInt).value.signum > 0) (e2 as ExprInt).value else null
		if (op == OpBinary.SHIFT_LEFT) {
			if (amount !== null) '''{«doSwitch(e1)», «amount»'b0}''' else '''{«doSwitch(e1)», {(«doSwitch(e2)»){1'b0}}}'''
		} else if (op == OpBinary.SHIFT_RIGHT) {
			// in Verilog, >>> is arithmetic shift, and >> is logical shift
			// this test is because arithmetic is only needed when shifting a signed expression
			// (arithmetic shift of an unsigned number is <=> to logical shift)
			'''(«doSwitch(e1)» «IF type.int && (type as TypeInt).signed»>>>«ELSE»>>«ENDIF» «IF amount !== null»«amount»«ELSE»«doSwitch(e2)»«ENDIF»)'''
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

		val index = typeName.indexOf('.')
		val targetSize = Integer.parseInt(typeName.substring(index + 1))
		val sourceSize = typeConv.expr.type.size
		val kind = typeName.substring(0, index)

		// Guard against invalid sizes (can happen with unresolved built-in entity port types)
		if (targetSize <= 0 || sourceSize <= 0) {
			// Skip conversion if sizes are invalid
			return doSwitch(typeConv.expr)
		}

		// zext concatenates a zero prefix — never bit-selects its operand — so it
		// is valid for any expression.
		if (kind == "zext") {
			return '''{«targetSize - sourceSize»'b0, «doSwitch(typeConv.expr)»}'''
		}
		// trunc/sext BIT-SELECT their operand, but Verilog cannot bit-select a
		// parenthesized expression. When AddBufferedInputs substitutes a stream
		// input port with a `valid ? internal : port` ternary, an inline
		// sext/trunc of that port would emit invalid `(ternary)[MSB]`. Distribute
		// the conversion into the ternary's arms — which ARE simple selectable
		// operands — so sext(c ? a : b) becomes (c ? sext(a) : sext(b)):
		// value-identical, valid on every arm. A simple (non-ternary) operand
		// takes the byte-identical direct form, so gated designs are unaffected.
		emitTruncOrSext(kind, typeConv.expr, targetSize)
	}

	/**
	 * Emits a {@code trunc}/{@code sext} type conversion, distributing over a
	 * ternary operand so a bit-select is never applied to a parenthesized
	 * expression (invalid Verilog). Recurses for nested ternaries; a simple
	 * operand takes the direct bit-select form (byte-identical to the old path).
	 */
	def private CharSequence emitTruncOrSext(String kind, Expression operand, int targetSize) {
		if (operand instanceof ExprTernary) {
			return '''(«doSwitch(operand.cond)» ? «emitTruncOrSext(kind, operand.e1, targetSize)» : «emitTruncOrSext(kind, operand.e2, targetSize)»)'''
		}
		val e = doSwitch(operand)
		val srcSize = operand.type.size
		if (kind == "trunc") {
			// one bit is `x[0]`, not the one-bit range `x[0 : 0]` (TRAPS T116, 962 sites)
			if (targetSize == 1) '''«e»[0]''' else '''«e»[«targetSize - 1» : 0]'''
		} else {
			// A Verilog CONCATENATION IS ALWAYS UNSIGNED (IEEE 1364 / 1800), even when
			// every operand is signed. Emitting a bare `{{k{x[msb]}}, x}` therefore
			// produced a sign-extended BIT PATTERN inside an UNSIGNED expression, and
			// Verilog's rule that one unsigned operand makes the whole expression
			// unsigned then silently degraded the enclosing operators: `*` became an
			// unsigned multiply and `>>>` an ordinary logical shift. That miscompiled
			// every signed value whose high bit was set — e.g. the reciprocal-multiply
			// lowering of `x / 7` returned 930884682 instead of -142857142 for
			// x = -1000000000, in the HARDWARE, while the bytecode simulator was right.
			// $signed() restores the declared signedness without changing a single bit,
			// so the multiply stays full-width and `>>>` stays arithmetic.
			'''$signed({{«targetSize - srcSize»{«e»[«srcSize - 1»]}}, «e»})'''
		}
	}

	override caseExprUnary(ExprUnary expr) {
		val subExpr = doSwitch(expr.expr)
		// A binary or ternary operand already prints its own parentheses (or braces), and a name or
		// a boolean needs none: `!(i < 4'h8)`, `!busy`, not `! ((i < 4'h8))` (TRAPS T116, 4202 sites).
		val sub = expr.expr
		val operand = if (sub instanceof ExprBinary || sub instanceof ExprTernary || sub instanceof ExprVar
				|| sub instanceof ExprBool) subExpr else '''(«subExpr»)'''

		switch (expr.op) {
		case OpUnary.BITNOT: '''~«operand»'''
		case OpUnary.LOGIC_NOT: '''!«operand»'''
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