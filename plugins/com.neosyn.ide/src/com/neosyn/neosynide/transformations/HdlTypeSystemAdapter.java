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
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * This class extends the TypeSystemAdapter for HDLs.
 * 

 *
 */
public abstract class HdlTypeSystemAdapter extends TypeSystemAdapter {

	@Override
	public Expression caseExprInt(ExprInt expr) {
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
