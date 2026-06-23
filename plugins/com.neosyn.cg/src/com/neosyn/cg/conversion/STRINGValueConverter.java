/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.conversion;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractLexerBasedConverter;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.util.Strings;

/**
 * This class defines a value converter for strings.
 * 

 */
public class STRINGValueConverter extends AbstractLexerBasedConverter<String> {

	@Override
	protected String toEscapedString(String value) {
		StringBuilder builder = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			CHARValueConverter.convertChar(builder, value.codePointAt(i));
		}
		builder.append('"');
		return builder.toString();
	}

	@Override
	public String toValue(String string, INode node) {
		if (Strings.isEmpty(string)) {
			throw new ValueConverterException("Couldn't convert empty string literal.", node, null);
		}

		StringLexer lexer = new StringLexer(string);
		StringBuilder builder = new StringBuilder();
		while (lexer.hasMoreChars()) {
			int c = lexer.getNextChar();
			if (Character.isValidCodePoint(c)) {
				builder.appendCodePoint(c);
			} else {
				// ignore for now
			}
		}
		return builder.toString();
	}

}
