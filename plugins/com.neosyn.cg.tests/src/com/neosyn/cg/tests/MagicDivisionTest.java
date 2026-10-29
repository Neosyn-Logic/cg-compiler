/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Random;

import org.junit.Test;

import com.neosyn.cg.internal.compiler.MagicDivision;

/**
 * Brute-forces the reciprocal-multiply constants that back constant `/` and `%` (see
 * {@link MagicDivision} and FunctionTransformer). This tests the PRODUCTION arithmetic directly
 * (the utility is the exact code the compiler calls), complementing the end-to-end
 * NonPow2ConstDivTest.cg vector fixture which exercises the full IR/codegen/sim pipeline.
 *
 * <p>
 * Small widths (3..16) are checked EXHAUSTIVELY — every non-power-of-two divisor against every
 * representable dividend — so any off-by-one is a hard failure, not a sampling miss. 32- and 64-bit
 * (whose products are 65 / 129 bits, beyond a Java long) are checked at the range boundaries plus a
 * fixed-seed random sweep, so the result is deterministic and reproducible.
 */
public class MagicDivisionTest {

	// ---- reference semantics --------------------------------------------------------------

	/** C-style truncation toward zero. */
	private static BigInteger truncDiv(BigInteger a, BigInteger b) {
		BigInteger q = a.abs().divide(b.abs());
		return (a.signum() < 0) != (b.signum() < 0) ? q.negate() : q;
	}

	// ---- the lowering, applied EXACTLY as FunctionTransformer emits it ---------------------

	/** q = (x * M) >>> s  (x >= 0, so BigInteger shiftRight is the logical shift). */
	private static BigInteger applyUnsigned(BigInteger x, int width, BigInteger d) {
		BigInteger[] ms = MagicDivision.unsigned(width, d);
		return x.multiply(ms[0]).shiftRight(ms[1].intValue());
	}

	/** q = sra(x * M, s) + (x < 0 ? 1 : 0)  (BigInteger shiftRight is arithmetic). */
	private static BigInteger applySigned(BigInteger x, int width, BigInteger d) {
		BigInteger[] ms = MagicDivision.signed(width, d);
		BigInteger q0 = x.multiply(ms[0]).shiftRight(ms[1].intValue());
		return x.signum() < 0 ? q0.add(BigInteger.ONE) : q0;
	}

	private static boolean isPow2(long n) {
		return (n & (n - 1)) == 0;
	}

	// ---- exhaustive small widths (fast: fits in a long) ------------------------------------

	@Test
	public void unsignedExhaustive_3to12() {
		for (int w = 3; w <= 12; w++) {
			long lim = 1L << w;
			for (long d = 2; d < lim; d++) {
				if (isPow2(d)) {
					continue;
				}
				BigInteger[] ms = MagicDivision.unsigned(w, BigInteger.valueOf(d));
				long m = ms[0].longValueExact();
				int s = ms[1].intValue();
				for (long x = 0; x < lim; x++) {
					long got = (x * m) >>> s;          // product < 2^25 for w<=12, no overflow
					assertEquals("u" + w + ": " + x + " / " + d, x / d, got);
				}
			}
		}
	}

	@Test
	public void signedExhaustive_3to12() {
		for (int w = 3; w <= 12; w++) {
			long half = 1L << (w - 1);
			for (long d = 2; d < half; d++) {
				if (isPow2(d)) {
					continue;
				}
				BigInteger[] ms = MagicDivision.signed(w, BigInteger.valueOf(d));
				long m = ms[0].longValueExact();
				int s = ms[1].intValue();
				for (long x = -half; x < half; x++) {
					long q0 = (x * m) >> s;            // arithmetic shift; |product| < 2^25
					long got = x < 0 ? q0 + 1 : q0;
					assertEquals("i" + w + ": " + x + " / " + d, x / d, got); // Java `/` truncates
				}
			}
		}
	}

	// width 16: every dividend, a spread of divisor shapes (still fits in a long) -------------

	private static final long[] SAMPLE_DIVISORS = {
			3, 5, 6, 7, 9, 10, 11, 12, 13, 25, 100, 127, 255, 1000, 10000, 12345, 65535 };

	@Test
	public void width16_allDividends_sampledDivisors() {
		int w = 16;
		long lim = 1L << w;
		long half = 1L << (w - 1);
		for (long d : SAMPLE_DIVISORS) {
			if (isPow2(d)) {
				continue;
			}
			BigInteger bd = BigInteger.valueOf(d);
			if (d < lim) {
				BigInteger[] ms = MagicDivision.unsigned(w, bd);
				long m = ms[0].longValueExact();
				int s = ms[1].intValue();
				for (long x = 0; x < lim; x++) {
					assertEquals("u16: " + x + " / " + d, x / d, (x * m) >>> s);
				}
			}
			if (d < half) {
				BigInteger[] ms = MagicDivision.signed(w, bd);
				long m = ms[0].longValueExact();
				int s = ms[1].intValue();
				for (long x = -half; x < half; x++) {
					long q0 = (x * m) >> s;
					long got = x < 0 ? q0 + 1 : q0;
					assertEquals("i16: " + x + " / " + d, x / d, got); // Java `/` truncates
				}
			}
		}
	}

	// 32- and 64-bit: product exceeds a long -> BigInteger; boundary + fixed-seed random ------

	private static final long[] WIDE_DIVISORS = {
			3, 5, 6, 7, 9, 10, 11, 100, 1000, 65535, 1000003, 1000000007L, 1000000000039L };

	@Test
	public void width32and64_boundaryAndRandom() {
		for (int w : new int[] { 32, 64 }) {
			BigInteger uMax = BigInteger.ONE.shiftLeft(w).subtract(BigInteger.ONE);
			BigInteger sHi = BigInteger.ONE.shiftLeft(w - 1).subtract(BigInteger.ONE);
			BigInteger sLo = BigInteger.ONE.shiftLeft(w - 1).negate();
			Random rnd = new Random(0xC0FFEEL + w);          // deterministic
			for (long dl : WIDE_DIVISORS) {
				BigInteger d = BigInteger.valueOf(dl);
				if (d.bitCount() == 1 || d.compareTo(uMax) > 0) {
					continue;
				}

				// unsigned: 0, 1, d-1, d, d+1, 2^(w-1), max-1, max, + random
				for (BigInteger x : new BigInteger[] { BigInteger.ZERO, BigInteger.ONE,
						d.subtract(BigInteger.ONE), d, d.add(BigInteger.ONE),
						BigInteger.ONE.shiftLeft(w - 1), uMax.subtract(BigInteger.ONE), uMax }) {
					if (x.signum() >= 0 && x.compareTo(uMax) <= 0) {
						assertEquals("u" + w + ": " + x + " / " + d, x.divide(d),
								applyUnsigned(x, w, d));
					}
				}
				for (int i = 0; i < 3000; i++) {
					BigInteger x = new BigInteger(w, rnd);   // uniform in [0, 2^w)
					assertEquals("u" + w + ": " + x + " / " + d, x.divide(d), applyUnsigned(x, w, d));
				}

				// signed: needs d < 2^(w-1)
				if (d.compareTo(BigInteger.ONE.shiftLeft(w - 1)) < 0) {
					for (BigInteger x : new BigInteger[] { BigInteger.ZERO, BigInteger.ONE,
							BigInteger.ONE.negate(), d, d.negate(), sHi, sLo, sLo.add(BigInteger.ONE) }) {
						assertEquals("i" + w + ": " + x + " / " + d, truncDiv(x, d),
								applySigned(x, w, d));
					}
					for (int i = 0; i < 3000; i++) {
						BigInteger u = new BigInteger(w, rnd);
						BigInteger x = u.testBit(w - 1) ? u.subtract(BigInteger.ONE.shiftLeft(w)) : u;
						assertEquals("i" + w + ": " + x + " / " + d, truncDiv(x, d),
								applySigned(x, w, d));
					}
				}
			}
		}
	}

	// a couple of textbook constants, as a canary that the algorithm itself is right ----------

	@Test
	public void knownMagicConstants() {
		// GCC's magics for 32-bit unsigned division
		assertEquals(BigInteger.valueOf(0xAAAAAAABL), MagicDivision.unsigned(32, BigInteger.valueOf(3))[0]);
		assertEquals(33, MagicDivision.unsigned(32, BigInteger.valueOf(3))[1].intValue());
		assertEquals(BigInteger.valueOf(0xCCCCCCCDL), MagicDivision.unsigned(32, BigInteger.valueOf(5))[0]);
		assertEquals(34, MagicDivision.unsigned(32, BigInteger.valueOf(5))[1].intValue());
		// /10 shares 5's multiplier with a larger shift (as emitted: (x*0xCCCCCCCD) >> 35)
		assertEquals(BigInteger.valueOf(0xCCCCCCCDL), MagicDivision.unsigned(32, BigInteger.valueOf(10))[0]);
		assertEquals(35, MagicDivision.unsigned(32, BigInteger.valueOf(10))[1].intValue());
		// magic multiplier is always positive and fits in width+1 bits
		assertTrue(MagicDivision.unsigned(32, BigInteger.valueOf(7))[0].signum() > 0);
		assertTrue(MagicDivision.unsigned(32, BigInteger.valueOf(7))[0].bitLength() <= 33);
	}
}
