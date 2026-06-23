/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.conversion;

import java.math.BigInteger;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractLexerBasedConverter;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.util.Strings;

/**
 * This class defines a value converter for integer numbers.
 * 

 */
public class BigIntegerValueConverter extends AbstractLexerBasedConverter<BigInteger> {

	@Override
	protected void assertValidValue(BigInteger value) {
		super.assertValidValue(value);
		if (value.signum() == -1) {
			throw new ValueConverterException(
					getRuleName() + "-value may not be negative (value:" + value + ").", null,
					null);
		}
	}

	@Override
	protected String toEscapedString(BigInteger value) {
		return value.toString();
	}

	@Override
	public BigInteger toValue(String string, INode node) {
		if (Strings.isEmpty(string)) {
			throw new ValueConverterException("Couldn't convert empty string to int.", node, null);
		}

		try {
			// strip underscores if necessary
			string = string.replace("_", "");

			// compute radix
			int radix;
			int index = 0; // for octal and decimal numbers
			if (string.startsWith("0b")) {
				radix = 2;
				index = 2;
			} else if (string.startsWith("0x")) {
				radix = 16;
				index = 2;
			} else if (string.startsWith("0")) {
				radix = 8;
			} else {
				radix = 10;
			}

			return new BigInteger(string.substring(index), radix);
		} catch (NumberFormatException e) {
			throw new ValueConverterException(string + " is not a valid integer", node, e);
		}
	}

}
