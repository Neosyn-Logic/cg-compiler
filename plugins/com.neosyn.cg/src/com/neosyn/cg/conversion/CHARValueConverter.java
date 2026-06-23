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
 * This class defines a value converter for characters.
 * 

 */
public class CHARValueConverter extends AbstractLexerBasedConverter<BigInteger> {

	private static void appendZeroes(StringBuilder builder, int actual, int expected) {
		int remaining = expected - actual;
		while (remaining > 0) {
			builder.append('0');
			remaining--;
		}
	}

	public static void convertChar(StringBuilder builder, int value) {
		switch (value) {
		case '\'':
			builder.append("\\'");
			break;
		case '\"':
			// not necessary for char, but this method may be used by strings too
			builder.append("\\\"");
			break;
		case '\\':
			builder.append("\\\\");
			break;
		case '\b':
			builder.append("\\b");
			break;
		case '\f':
			builder.append("\\f");
			break;
		case '\n':
			builder.append("\\n");
			break;
		case '\r':
			builder.append("\\r");
			break;
		case '\t':
			builder.append("\\t");
			break;
		default:
			if (value == 0) {
				builder.append("\\0");
			} else if (value > 31 && value < 127) {
				// printable ASCII
				builder.append((char) value);
			} else {
				String str = Integer.toUnsignedString(value, 16);
				int size = str.length();
				if (size <= 2) {
					builder.append("\\x");
					appendZeroes(builder, size, 2);
				} else if (size <= 4) {
					builder.append("\\u");
					appendZeroes(builder, size, 4);
				} else if (size <= 8) {
					builder.append("\\U");
					appendZeroes(builder, size, 8);
				}
				builder.append(str);
			}
		}
	}

	@Override
	protected void assertValidValue(BigInteger value) {
		super.assertValidValue(value);
		if (value.signum() == -1) {
			throw new ValueConverterException(getRuleName() + "-value may not be negative (value:"
					+ value + ").", null, null);
		}
	}

	@Override
	protected String toEscapedString(BigInteger value) {
		StringBuilder builder = new StringBuilder("'");
		int intValue = value.intValue();
		convertChar(builder, intValue);
		builder.append('\'');
		return builder.toString();
	}

	@Override
	public BigInteger toValue(String string, INode node) {
		if (Strings.isEmpty(string)) {
			throw new ValueConverterException("Couldn't convert empty char literal to int.", node,
					null);
		}

		StringLexer lexer = new StringLexer(string);
		int value = lexer.getNextChar();
		return BigInteger.valueOf(value);
	}

}
