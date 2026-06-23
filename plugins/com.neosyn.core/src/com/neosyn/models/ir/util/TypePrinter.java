/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.util;

import static com.neosyn.models.util.SwitchUtil.DONE;

import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.TypeBool;
import com.neosyn.models.ir.TypeFloat;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.TypeString;
import com.neosyn.models.ir.TypeVoid;
import com.neosyn.models.util.Void;

/**
 * This class defines a type printer for Cx-like types.
 * 

 * 
 */
public class TypePrinter extends IrSwitch<Void> {

	private StringBuilder builder;

	@Override
	public Void caseTypeArray(TypeArray type) {
		doSwitch(type.getElementType());
		for (int dim : type.getDimensions()) {
			builder.append('[');
			builder.append(dim);
			builder.append(']');
		}
		return DONE;
	}

	@Override
	public Void caseTypeBool(TypeBool type) {
		builder.append("bool");
		return DONE;
	}

	@Override
	public Void caseTypeFloat(TypeFloat type) {
		builder.append("float");
		return DONE;
	}

	@Override
	public Void caseTypeInt(TypeInt type) {
		if (type.isSigned()) {
			builder.append('i');
		} else {
			builder.append('u');
		}

		int size = type.getSize();
		builder.append(size);

		return DONE;
	}

	@Override
	public Void caseTypeString(TypeString type) {
		builder.append("String");
		return DONE;
	}

	@Override
	public Void caseTypeVoid(TypeVoid type) {
		builder.append("void");
		return DONE;
	}

	public String toString(Type type) {
		builder = new StringBuilder();
		doSwitch(type);
		return builder.toString();
	}

}
