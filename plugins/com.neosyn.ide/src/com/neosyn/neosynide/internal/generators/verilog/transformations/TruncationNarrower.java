/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractExpressionTransformer;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.EcoreHelper;

/**
 * Narrows arithmetic whose result is immediately truncated.
 *
 * <p>When a value is truncated to {@code N} bits, only its low {@code N} bits
 * matter. For the low-bits-preserving operators (+, -, *, &amp;, |, ^) the low
 * {@code N} bits of the result depend only on the low {@code N} bits of each
 * operand — this is exactly two's-complement arithmetic. So
 * {@code trunc(N, a OP b) == trunc(N, trunc(N,a) OP trunc(N,b))}, and the demand
 * can be pushed all the way down to the leaves.
 *
 * <p>Without this, {@code (u8)((row*32 + col) & 0xFF)} (row, col are 32-bit
 * {@code int}) is emitted as 38/39-bit sign-extended arithmetic before a final
 * 8-bit truncation. The width blow-up comes from {@link VerilogTypeSystemAdapter}
 * widening operands to a common type; this pass runs right after it and pushes
 * the truncation demand back down, dropping the wide intermediate resizes.
 *
 * <p>Conservative by construction: descent stops (and the sub-expression is
 * simply truncated to {@code N}) at any node that is not a low-bits-preserving
 * binary or a resize — shifts, division, modulo, comparisons, calls, etc. all
 * end the descent, so their (potentially high-bit-dependent) semantics are
 * untouched.
 */
public class TruncationNarrower extends AbstractExpressionTransformer {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	/**
	 * Input-port variables of the current entity. A later pass
	 * (AddBufferedInputs) rewrites reads of these into a {@code (valid ? buf :
	 * port)} ternary, and Verilog does not allow a part-select on a conditional
	 * expression — so we must never truncate such a leaf into {@code port[n:0]}.
	 */
	private Set<Var> inputVars = Collections.emptySet();

	@Override
	public void setProcedure(Procedure procedure) {
		super.setProcedure(procedure);
		Entity entity = EcoreHelper.getContainerOfType(procedure, Entity.class);
		inputVars = (entity != null) ? new HashSet<>(entity.getInputs()) : Collections.<Var>emptySet();
	}

	@Override
	public Expression caseExprResize(ExprResize resize) {
		// Let the base transformer recurse into children first (so nested
		// truncations deeper in the tree are handled in their own right).
		resize = (ExprResize) super.caseExprResize(resize);

		int target = resize.getTargetSize();
		Expression inner = resize.getExpr();
		if (isWiderThan(inner, target)) {
			// This resize truncates: only the low `target` bits of `inner`
			// survive, so push that demand into the producer.
			resize.setExpr(demandLowBits(inner, target));
		}
		return resize;
	}

	/**
	 * Returns an expression whose low {@code n} bits equal {@code expr}'s low
	 * {@code n} bits, rewritten to compute in narrow ({@code ~n}-bit) arithmetic
	 * instead of the wide extended form.
	 */
	private Expression demandLowBits(Expression expr, int n) {
		if (!isWiderThan(expr, n)) {
			// Already n bits or narrower — nothing above bit n-1 to discard.
			return expr;
		}

		if (expr instanceof ExprInt) {
			// Re-type a wide literal to its low n bits directly (8'h20 rather
			// than `temp = 38'sh20; temp[7:0]`). Keeping it a literal also lets
			// the mask-elimination peephole recognise an `& 0xFF` afterwards.
			ExprInt lit = (ExprInt) expr;
			Type lt = TypeUtil.getType(lit);
			boolean signed = lt.isInt() && ((TypeInt) lt).isSigned();
			BigInteger lowMask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
			ExprInt narrowed = ir.createExprInt(lit.getValue().and(lowMask));
			narrowed.setComputedType(ir.createTypeInt(n, signed));
			return narrowed;
		}

		if (expr instanceof ExprResize) {
			ExprResize rz = (ExprResize) expr;
			int m = rz.getTargetSize();
			if (m >= n) {
				// Resizing to m >= n then taking the low n bits is the same as
				// taking the low n bits of the inner value — drop this resize.
				return demandLowBits(rz.getExpr(), n);
			}
			// Resizes below n: it genuinely narrows past our demand; keep it.
			rz.setExpr(demandLowBits(rz.getExpr(), m));
			return rz;
		}

		if (expr instanceof ExprBinary) {
			ExprBinary bin = (ExprBinary) expr;
			if (isLowBitsPreserving(bin.getOp())) {
				bin.setE1(demandLowBits(bin.getE1(), n));
				bin.setE2(demandLowBits(bin.getE2(), n));
				return bin;
			}
		}

		// Never truncate an input-port read: AddBufferedInputs turns it into a
		// `(valid ? buf : port)` ternary, and `(ternary)[n:0]` is illegal
		// Verilog. Leave it wide; the outer truncation still bounds the result.
		// (Input ports are narrow anyway — the width blow-up is in state/local
		// arithmetic, which we still narrow.)
		if (expr instanceof ExprVar) {
			Var v = ((ExprVar) expr).getUse().getVariable();
			if (inputVars.contains(v)) {
				return expr;
			}
		}

		// A wide leaf (variable, etc.) or a node we must not descend into:
		// truncate it to n bits. The low n bits are preserved.
		ExprResize truncated = ir.createExprResize();
		truncated.setTargetSize(n);
		truncated.setExpr(expr);
		return truncated;
	}

	private boolean isWiderThan(Expression expr, int n) {
		Type t = TypeUtil.getType(expr);
		return t.isInt() && ((TypeInt) t).getSize() > n;
	}

	/**
	 * True for operators whose low {@code N} output bits depend only on the low
	 * {@code N} bits of the operands (two's-complement add/sub/mul and bitwise).
	 * Notably excludes shifts, division, modulo and comparisons.
	 */
	private boolean isLowBitsPreserving(OpBinary op) {
		switch (op) {
		case PLUS:
		case MINUS:
		case TIMES:
		case BITAND:
		case BITOR:
		case BITXOR:
			return true;
		default:
			return false;
		}
	}
}
