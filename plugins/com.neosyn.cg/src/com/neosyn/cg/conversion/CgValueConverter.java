/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.conversion;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.eclipse.xtext.common.services.DefaultTerminalConverters;
import org.eclipse.xtext.conversion.IValueConverter;
import org.eclipse.xtext.conversion.ValueConverter;
import org.eclipse.xtext.conversion.impl.KeywordAlternativeConverter;
import org.eclipse.xtext.conversion.impl.QualifiedNameValueConverter;

import com.google.inject.Inject;

/**
 * Converts "true" and "false" to booleans, and hexadecimal to integer.
 */
public class CgValueConverter extends DefaultTerminalConverters {

	@Inject
	private BOOLValueConverter boolValueConverter;

	@Inject
	private CHARValueConverter charValueConverter;

	@Inject
	private BigDecimalValueConverter floatConverter;

	@Inject
	private QualifiedNameValueConverter fullyQualifiedNameConverter;

	@Inject
	private BigIntegerValueConverter integerConverter;

	@Inject
	private CgQualifiedNameValueConverter qualifiedNameValueConverter;

	@Inject
	private STRINGValueConverter stringValueConverter;

	@Inject
	private KeywordAlternativeConverter validIDConverter;

	@ValueConverter(rule = "BOOL")
	public IValueConverter<Boolean> getBoolConverter() {
		return boolValueConverter;
	}

	@ValueConverter(rule = "CHAR")
	public IValueConverter<BigInteger> getCharConverter() {
		return charValueConverter;
	}

	@ValueConverter(rule = "FLOAT")
	public IValueConverter<BigDecimal> getFloatConverter() {
		return floatConverter;
	}

	@ValueConverter(rule = "FullyQualifiedName")
	public IValueConverter<String> getFullyQualifiedNameConverter() {
		return fullyQualifiedNameConverter;
	}

	@ValueConverter(rule = "INTEGER")
	public IValueConverter<BigInteger> getIntegerConverter() {
		return integerConverter;
	}

	@ValueConverter(rule = "QualifiedName")
	public IValueConverter<String> getQualifiedNameValueConverter() {
		return qualifiedNameValueConverter;
	}

	@ValueConverter(rule = "ValidID")
	public IValueConverter<String> getValidIDConverter() {
		return validIDConverter;
	}

	@ValueConverter(rule = "STRING")
	public IValueConverter<String> STRING() {
		return stringValueConverter;
	}

}
