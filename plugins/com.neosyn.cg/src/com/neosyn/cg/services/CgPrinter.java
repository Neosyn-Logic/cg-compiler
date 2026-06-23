/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.services;

import static com.neosyn.cg.CgConstants.NAME_SIZEOF;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static org.eclipse.xtext.nodemodel.util.NodeModelUtils.getNode;
import static org.eclipse.xtext.nodemodel.util.NodeModelUtils.getTokenText;

import java.util.Iterator;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Array;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Element;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionBoolean;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionFloat;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionString;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Null;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.Primitive;
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.TypeRef;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.models.util.Void;

/**
 * This class defines a simple Cx pretty printer (a lightweight alternative to the whole Xtext
 * serialization thing).
 * 

 * 
 */
public class CgPrinter extends CgSwitch<Void> {

	private StringBuilder builder;

	public CgPrinter() {
		this.builder = new StringBuilder();
	}

	public CgPrinter(StringBuilder builder) {
		this.builder = builder;
	}

	@Override
	public Void caseArray(Array array) {
		builder.append('[');
		Iterator<Element> it = array.getElements().iterator();
		if (it.hasNext()) {
			doSwitch(it.next());
			while (it.hasNext()) {
				builder.append(',');
				doSwitch(it.next());
			}
		}
		builder.append(']');
		return DONE;
	}

	@Override
	public Void caseExpressionBinary(ExpressionBinary expr) {
		builder.append('(');
		doSwitch(expr.getLeft());
		builder.append(expr.getOperator());
		doSwitch(expr.getRight());
		builder.append(')');
		return DONE;
	}

	@Override
	public Void caseExpressionBoolean(ExpressionBoolean expr) {
		builder.append(expr.isValue());
		return DONE;
	}

	@Override
	public Void caseExpressionCast(ExpressionCast expr) {
		builder.append('(');
		doSwitch(expr.getType());
		builder.append(')');

		builder.append('(');
		doSwitch(expr.getExpression());
		builder.append(')');

		return DONE;
	}

	@Override
	public Void caseExpressionFloat(ExpressionFloat expr) {
		builder.append(expr.getValue());
		return DONE;
	}

	@Override
	public Void caseExpressionIf(ExpressionIf expr) {
		builder.append('(');
		doSwitch(expr.getCondition());
		builder.append('?');
		doSwitch(expr.getThen());
		builder.append(':');
		doSwitch(expr.getElse());
		builder.append(')');
		return DONE;
	}

	@Override
	public Void caseExpressionInteger(ExpressionInteger expr) {
		builder.append(expr.getValue());
		return DONE;
	}

	@Override
	public Void caseExpressionList(ExpressionList expr) {
		builder.append('{');
		Iterator<CgExpression> it = expr.getValues().iterator();
		if (it.hasNext()) {
			doSwitch(it.next());
			while (it.hasNext()) {
				builder.append(',');
				doSwitch(it.next());
			}
		}
		builder.append('}');
		return DONE;
	}

	@Override
	public Void caseExpressionString(ExpressionString expr) {
		builder.append('"');
		builder.append(expr.getValue());
		builder.append('"');
		return DONE;
	}

	@Override
	public Void caseExpressionUnary(ExpressionUnary expr) {
		boolean isSizeof = NAME_SIZEOF.equals(expr.getOperator());
		builder.append(expr.getOperator());
		if (isSizeof) {
			builder.append('(');
		}
		doSwitch(expr.getExpression());
		if (isSizeof) {
			builder.append(')');
		}
		return DONE;
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		doSwitch(expr.getSource());

		for (CgExpression index : expr.getIndexes()) {
			builder.append('[');
			doSwitch(index);
			builder.append(']');
		}

		String property = expr.getPropertyName();
		if (property != null) {
			builder.append('.');
			builder.append(property);
		}

		Iterator<CgExpression> it = expr.getParameters().iterator();
		if (it.hasNext()) {
			builder.append('(');
			doSwitch(it.next());
			while (it.hasNext()) {
				builder.append(',');
				doSwitch(it.next());
			}
			builder.append(')');
		}

		return DONE;
	}

	@Override
	public Void caseNull(Null null_) {
		builder.append("null");
		return DONE;
	}

	@Override
	public Void caseObj(Obj obj) {
		builder.append('{');
		Iterator<Pair> it = obj.getMembers().iterator();
		if (it.hasNext()) {
			doSwitch(it.next());
			while (it.hasNext()) {
				builder.append(',');
				doSwitch(it.next());
			}
		}
		builder.append('}');
		return DONE;
	}

	@Override
	public Void casePair(Pair pair) {
		builder.append('"');
		builder.append(pair.getKey());
		builder.append('"');
		builder.append(':');

		Element value = pair.getValue();
		if (value == null) {
			builder.append("null");
		} else {
			doSwitch(pair.getValue());
		}
		return DONE;
	}

	@Override
	public Void casePrimitive(Primitive primitive) {
		doSwitch(primitive.getValue());
		return DONE;
	}

	@Override
	public Void caseTypeDecl(TypeDecl type) {
		String spec = type.getSpec();
		if ("bool".equals(spec) || "char".equals(spec) || "float".equals(spec)
				|| "void".equals(spec)) {
			builder.append(spec);
			return DONE;
		}

		if (spec == null) {
			spec = "int";
		}

		char ch = spec.charAt(0);
		if ((ch == 'i' || ch == 'u') && Character.isDigit(spec.charAt(1))) {
			builder.append(spec);
		} else {
			if (ch == 'u') {
				builder.append("unsigned ");
				builder.append(spec.substring(1));
			} else {
				boolean signed = type.isSigned() || !type.isUnsigned();
				builder.append(signed ? "signed" : "unsigned");
				builder.append(" ");
				builder.append(spec);
			}
		}

		CgExpression size = type.getSize();
		if (size != null) {
			builder.append('<');
			doSwitch(size);
			builder.append('>');
		}

		return DONE;
	}

	@Override
	public Void caseTypeRef(TypeRef ref) {
		builder.append(getTokenText(getNode(ref)));
		return DONE;
	}

	@Override
	public Void caseVariable(Variable variable) {
		builder.append(variable.getName());
		if (CgUtil.isFunction(variable)) {
			builder.append('(');
			Iterator<Variable> it = variable.getParameters().iterator();
			if (it.hasNext()) {
				doSwitch(CgUtil.getType(it.next()));
				while (it.hasNext()) {
					builder.append(", ");
					doSwitch(CgUtil.getType(it.next()));
				}
			}
			builder.append(')');
		}
		return DONE;
	}

	@Override
	public Void caseVarRef(VarRef ref) {
		builder.append(getTokenText(getNode(ref)));
		return DONE;
	}

	@Override
	public Void doSwitch(EObject eObject) {
		if (eObject == null) {
			builder.append("null");
			return DONE;
		} else {
			return doSwitch(eObject.eClass(), eObject);
		}
	}

	public String toString(EObject eObject) {
		doSwitch(eObject);
		return builder.toString();
	}

}
