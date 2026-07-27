/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import static java.math.BigInteger.ONE;

import java.math.BigInteger;

/**
 * Magic-number ("reciprocal multiply") constants for division by an invariant integer, the same
 * technique GCC/LLVM emit. Given a width-bit dividend and a positive non-power-of-two divisor
 * {@code d}, {@link FunctionTransformer} lowers {@code x / d} to {@code (x * M) >> s} for a magic
 * constant {@code M} and shift {@code s} computed here — exact for every representable dividend
 * (floor for unsigned, truncation toward zero for signed once the sign correction is added).
 *
 * <p>
 * Pulled out of {@link FunctionTransformer} so the arithmetic can be brute-force unit-tested in
 * isolation (see {@code MagicDivisionTest}), independently of the IR/codegen pipeline.
 *
 * <p>
 * References: Granlund &amp; Montgomery, "Division by Invariant Integers using Multiplication"
 * (PLDI '94); Warren, <i>Hacker's Delight</i>, ch. 10.
 */
public final class MagicDivision {

	private static final BigInteger TWO = BigInteger.valueOf(2);

	private MagicDivision() {
	}

	/**
	 * Unsigned magic constant: returns {@code {M, s}} such that
	 * {@code floor(x / d) == (x * M) >>> s} for every {@code 0 <= x < 2^width}, with d a
	 * non-power-of-two divisor in {@code [2, 2^width)}. {@code M = ceil(2^s / d)} with the smallest
	 * s meeting the exactness bound {@code e * (2^width - 1) < 2^s}, {@code e = M*d - 2^s}. M is up
	 * to width+1 bits, so {@code x * M} is a >width-bit product (full width, never truncated).
	 *
	 * @param width
	 *            bit width of the (unsigned) dividend
	 * @param d
	 *            the divisor (>= 2, not a power of two, &lt; 2^width)
	 * @return a two-element array {@code {M, s}}
	 */
	public static BigInteger[] unsigned(int width, BigInteger d) {
		BigInteger xMax = ONE.shiftLeft(width).subtract(ONE);
		for (int s = 0;; s++) {
			BigInteger pow = ONE.shiftLeft(s);
			BigInteger m = pow.add(d).subtract(ONE).divide(d); // ceil(2^s / d)
			BigInteger e = m.multiply(d).subtract(pow);        // 0 <= e < d
			if (e.multiply(xMax).compareTo(pow) < 0) {
				return new BigInteger[] { m, BigInteger.valueOf(s) };
			}
		}
	}

	/**
	 * Signed magic constant (Hacker's Delight {@code magic()}): returns {@code {M, s}} for a
	 * positive d such that {@code trunc(x / d) == sra(x * M, s) + (x < 0 ? 1 : 0)} for every signed
	 * width-bit x, with d a non-power-of-two divisor in {@code [2, 2^(width-1))}. M is returned as a
	 * positive full-precision integer (the caller multiplies in a signed local one bit wider than
	 * M, so the product is a signed*positive full-width multiply with no N-bit wraparound).
	 *
	 * @param width
	 *            bit width of the (signed) dividend
	 * @param d
	 *            the divisor (>= 2, not a power of two, &lt; 2^(width-1))
	 * @return a two-element array {@code {M, s}}
	 */
	public static BigInteger[] signed(int width, BigInteger d) {
		BigInteger twoPow = ONE.shiftLeft(width - 1); // 2^(width-1)
		BigInteger anc = twoPow.subtract(ONE).subtract(twoPow.mod(d));
		int p = width - 1;
		BigInteger q1 = twoPow.divide(anc);
		BigInteger r1 = twoPow.subtract(q1.multiply(anc));
		BigInteger q2 = twoPow.divide(d);
		BigInteger r2 = twoPow.subtract(q2.multiply(d));
		BigInteger delta;
		do {
			p++;
			q1 = q1.multiply(TWO);
			r1 = r1.multiply(TWO);
			if (r1.compareTo(anc) >= 0) {
				q1 = q1.add(ONE);
				r1 = r1.subtract(anc);
			}
			q2 = q2.multiply(TWO);
			r2 = r2.multiply(TWO);
			if (r2.compareTo(d) >= 0) {
				q2 = q2.add(ONE);
				r2 = r2.subtract(d);
			}
			delta = d.subtract(r2);
		} while (q1.compareTo(delta) < 0 || (q1.equals(delta) && r1.signum() == 0));
		return new BigInteger[] { q2.add(ONE), BigInteger.valueOf(p) };
	}
}
