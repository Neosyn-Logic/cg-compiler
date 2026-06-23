/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.conversion;

/**
 * This class defines a string lexer.
 * 

 *
 */
public class StringLexer {

	private String buffer;

	private int index;

	public StringLexer(String buffer) {
		this.buffer = buffer;
		index = 1;
	}

	private int getEscapedNumber(int length, int radix) {
		int num = Integer.parseUnsignedInt(buffer.substring(index, index + length), radix);
		index += length;
		return num;
	}

	public int getNextChar() {
		char c = buffer.charAt(index);
		index++;
		if (c == '\\') {
			c = buffer.charAt(index);

			// first attempt octal sequence
			int length = 0;
			while (c >= '0' && c <= '7') {
				length++;
				if (hasMoreChars(index + length)) {
					c = buffer.charAt(index + length);
				} else {
					break;
				}
			}

			if (length > 0) {
				return getEscapedNumber(length, 8);
			}

			index++;
			switch (c) {
			case 'x':
				return getEscapedNumber(2, 16);
			case 'u':
				return getEscapedNumber(4, 16);
			case 'U':
				return getEscapedNumber(8, 16);

			case 'b':
				c = '\b';
				break;
			case 'f':
				c = '\f';
				break;
			case 'n':
				c = '\n';
				break;
			case 'r':
				c = '\r';
				break;
			case 't':
				c = '\t';
				break;
			}

			// default falls through for ', ", \, to end of method
		}

		return c;
	}

	public boolean hasMoreChars() {
		return hasMoreChars(index);
	}

	private boolean hasMoreChars(int index) {
		return index < buffer.length() - 1;
	}
}
