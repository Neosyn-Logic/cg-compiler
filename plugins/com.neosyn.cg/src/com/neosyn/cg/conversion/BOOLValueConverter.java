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
 * This class defines a value converter for BOOL rule.
 * 

 */
public class BOOLValueConverter extends AbstractLexerBasedConverter<Boolean> {

	public BOOLValueConverter() {
		super();
	}

	@Override
	protected String toEscapedString(Boolean value) {
		return value.toString();
	}

	@Override
	public Boolean toValue(String string, INode node) {
		if (Strings.isEmpty(string)) {
			throw new ValueConverterException(
					"Couldn't convert empty string to boolean", node, null);
		}

		return ("true".equals(string));
	}

	@Override
	public String toString(Boolean value) {
		return value.toString();
	}

}
