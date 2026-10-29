/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static java.math.BigInteger.ONE;

import java.math.BigInteger;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * Specialises {@link TypeSystemAdapter} for HDL targets: it additionally sizes integer
 * literals to the width of the context they appear in ({@link #caseExprInt}), masking or
 * sign-normalising the value so the emitted constant matches the target bit width exactly.
 */
public abstract class HdlTypeSystemAdapter extends TypeSystemAdapter {

	@Override
	public Expression caseExprInt(ExprInt expr) {
		// A context with NO common type -- `bool == 1`, whose operands share no IR type,
		// so the comparison never typed. This used to NPE here (TypeUtil.getSize(null)),
		// which the HDL handler caught and turned into "[skipped] <entity>" behind a
		// "Partial success": the user's design silently produced no .v at all while the
		// library modules did. The BYTECODE backend handles the same program on purpose
		// (S162: a null common type is compared as an int, and SimulateSafetyTest pins
		// it), so the HDL side must not reject it either. Size the literal by its own
		// natural width and carry on -- for `b == 1` that emits a 1-bit constant, which
		// is what the comparison against a bool needs.
		if (getTarget() == null) {
			BigInteger v = expr.getValue();
			int natural = Math.max(1, TypeUtil.getSize(v));
			expr.setValue(getUnsigned(v, natural));
			expr.setComputedType(IrFactory.eINSTANCE.createTypeInt(natural, v.signum() < 0));
			return expr;
		}

		// set the size of integer literals
		int targetSize = TypeUtil.getSize(getTarget());

		BigInteger value = expr.getValue();
		int size = TypeUtil.getSize(value);
		if (targetSize < size) {
			BigInteger mask = ONE.shiftLeft(targetSize).subtract(ONE);
			expr.setValue(getUnsigned(value, size).and(mask));
		} else {
			expr.setValue(getUnsigned(value, targetSize));
		}

		expr.setComputedType(EcoreUtil.copy(getTarget()));

		return expr;
	}

	private BigInteger getUnsigned(BigInteger value, int size) {
		if (value.signum() < 0) {
			return ONE.shiftLeft(size).add(value);
		} else {
			return value;
		}
	}

}
