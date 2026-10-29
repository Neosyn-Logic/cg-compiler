/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.util.TypeUtil;

import static com.neosyn.models.ir.util.TypeUtil.getType;
import com.neosyn.neosynide.transformations.HdlTypeSystemAdapter;

/**
 * Verilog specialisation of {@link HdlTypeSystemAdapter}. Two Verilog-specific rules:
 * unary minus is rewritten as {@code 0 - x} (Verilog has no bit-accurate unary negate),
 * and a left shift is emitted as a bit concatenation — so its operands must keep their own
 * types rather than being cast to the (wider) result type (see {@link #getOperandType}).
 */
public class VerilogTypeSystemAdapter extends HdlTypeSystemAdapter {

	@Override
	public Expression caseExprUnary(ExprUnary exprUn) {
		Expression expr = exprUn.getExpr();
		if (exprUn.getOp() == OpUnary.MINUS) {
			ExprBinary exprBin = ir.createExprBinary(ir.createExprInt(0), OpBinary.MINUS, expr);
			return transform(negationType(expr), exprBin);
		}

		return super.caseExprUnary(exprUn);
	}

	/**
	 * The type a negation must carry: {@code signed<N + 1>}, where N is the operand's width.
	 *
	 * <p>Rewriting {@code -x} as {@code 0 - x} is correct arithmetic and the wrong TYPE. The
	 * language rule for unary minus is {@code -e : signed<N + 1>}, but the additive rule is
	 * {@code unify(T1, T2)} with one extra bit — and {@code unify} of two UNSIGNED operands is
	 * unsigned. So negating an unsigned value produced an unsigned difference, which is negative
	 * whenever the operand is positive.
	 *
	 * <p>The damage appeared one level up, as a zero-extension. For {@code -a + b} with
	 * {@code a = 1, b = 3} the emitted Verilog was:
	 *
	 * <pre>
	 * reg [8 : 0] orion;                                   // unsigned
	 * orion = (9'h0 - {1'b0, a});                          // 9'h1FF, i.e. -1
	 * lyra  = ($signed({1'b0, orion}) + $signed({2'b0, b}));  // ZERO-extends -> 511
	 * </pre>
	 *
	 * 511 + 3 = 514, which as signed 10-bit is -510 — exactly what the netlist computed while
	 * the bytecode simulator returned 2. Typing the negation signed makes the widening a
	 * sign-extension, and the two backends agree.
	 *
	 * <p>Found in the shipped Sobel core, where both gradients mixed {@code u8} pixels into a
	 * signed accumulator: the HDL computed a different gradient than the simulator on every
	 * edge. Verilog only — VHDL negates through its own path.
	 *
	 * <p>Falls back to the ambient target when the operand has no usable type, so a
	 * partially-typed expression cannot crash code generation here.
	 */
	private Type negationType(Expression operand) {
		Type operandType = getType(operand);
		if (operandType == null || !operandType.isInt()) {
			return getTarget();
		}
		return ir.createTypeInt(TypeUtil.getSize(operandType) + 1, true);
	}

	@Override
	protected Type getOperandType(OpBinary op, Type typeExpr, Type typeOper, int n) {
		switch (op) {
		case SHIFT_LEFT:
			// we implement the left shift as a bit concatenation
			// so we must not cast operands to the size of the result
			// instead we just cast them to their respective type
			return typeOper;
		default:
			return typeExpr;
		}
	}

}
