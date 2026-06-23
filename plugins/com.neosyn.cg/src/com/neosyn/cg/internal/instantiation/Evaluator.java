/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.cg.CgConstants.NAME_SIZEOF;
import static com.neosyn.cg.CgConstants.PROP_LENGTH;
import static com.neosyn.models.ir.util.TypeUtil.getSize;
import static com.neosyn.models.ir.util.ValueUtil.getType;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionBoolean;
import com.neosyn.cg.cg.ExpressionFloat;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionString;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.ValueUtil;

/**
 * This class defines an expression evaluator.
 * 

 * 
 */
public class Evaluator extends CgSwitch<Object> {

	/**
	 * Returns the Cx value that matches the given runtime value. Value is expected to be one of
	 * Boolean, BigDecimal, BigInteger, String, or Array.
	 * 
	 * @param value
	 *            a runtime value
	 * @return a Cx value (ValueExpr or ValueList)
	 */
	public static CgExpression getCxExpression(Object value) {
		if (ValueUtil.isBool(value)) {
			ExpressionBoolean expr = CgFactory.eINSTANCE.createExpressionBoolean();
			expr.setValue((Boolean) value);
			return expr;
		} else if (ValueUtil.isFloat(value)) {
			ExpressionFloat expr = CgFactory.eINSTANCE.createExpressionFloat();
			expr.setValue((BigDecimal) value);
			return expr;
		} else if (ValueUtil.isInt(value)) {
			ExpressionInteger expr = CgFactory.eINSTANCE.createExpressionInteger();
			expr.setValue((BigInteger) value);
			return expr;
		} else if (ValueUtil.isString(value)) {
			ExpressionString expr = CgFactory.eINSTANCE.createExpressionString();
			expr.setValue((String) value);
			return expr;
		} else if (ValueUtil.isList(value)) {
			ExpressionList list = CgFactory.eINSTANCE.createExpressionList();
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				list.getValues().add(getCxExpression(Array.get(value, i)));
			}
			return list;
		} else {
			return null;
		}
	}

	private Entity entity;

	private IInstantiator instantiator;

	Evaluator(IInstantiator instantiator) {
		this.instantiator = instantiator;
	}

	@Override
	public Object caseExpressionBinary(ExpressionBinary expression) {
		OpBinary op = OpBinary.getOperator(expression.getOperator());
		Object val1 = doSwitch(expression.getLeft());
		Object val2 = doSwitch(expression.getRight());
		return ValueUtil.compute(val1, op, val2);
	}

	@Override
	public Object caseExpressionBoolean(ExpressionBoolean expression) {
		return expression.isValue();
	}

	@Override
	public Object caseExpressionFloat(ExpressionFloat expr) {
		return expr.getValue();
	}

	@Override
	public Object caseExpressionIf(ExpressionIf expression) {
		Object condition = doSwitch(expression.getCondition());

		if (ValueUtil.isBool(condition)) {
			if (ValueUtil.isTrue(condition)) {
				return doSwitch(expression.getThen());
			} else {
				return doSwitch(expression.getElse());
			}
		} else {
			return null;
		}
	}

	@Override
	public Object caseExpressionInteger(ExpressionInteger expression) {
		return expression.getValue();
	}

	@Override
	public Object caseExpressionList(ExpressionList valueList) {
		int size = valueList.getValues().size();
		Object[] objects = new Object[size];
		int i = 0;
		for (CgExpression value : valueList.getValues()) {
			objects[i] = doSwitch(value);
			i++;
		}
		return objects;
	}

	@Override
	public Object caseExpressionString(ExpressionString expression) {
		return expression.getValue();
	}

	@Override
	public Object caseExpressionUnary(ExpressionUnary expression) {
		CgExpression subExpr = expression.getExpression();
		if (NAME_SIZEOF.equals(expression.getOperator())) {
			if (subExpr instanceof ExpressionVariable) {
				ExpressionVariable expr = (ExpressionVariable) subExpr;
				Type typeSource = instantiator.computeType(entity, expr.getSource());
				if (typeSource != null && typeSource.isArray()) {
					Type type = instantiator.computeType(entity, subExpr);
					return BigInteger.valueOf(getSize(type));
				}
			}

			Object value = doSwitch(subExpr);
			if (value == null) {
				return null;
			}
			return BigInteger.valueOf(getSize(getType(value)));
		}

		OpUnary op = OpUnary.getOperator(expression.getOperator());
		Object value = doSwitch(subExpr);
		return ValueUtil.compute(op, value);
	}

	@Override
	public Object caseExpressionVariable(ExpressionVariable expression) {
		Variable variable = expression.getSource().getVariable();

		// An unresolved cross-file reference (e.g. an imported symbol whose
		// defining resource was not loaded) leaves the variable null. We cannot
		// evaluate it to a compile-time constant — return null rather than NPE
		// in isConstant(). The real fix is to load the resource (see
		// ServerUtils.findProjectRoot); this is defence in depth.
		if (variable == null) {
			return null;
		}

		// L2 — enum literal is a compile-time constant. Iter #1 used the
		// literal's index; iter #2 honours explicit `LIT = N` values.
		// See .claude/L2_ENUM_DESIGN.md §6.
		if (variable.eContainer() instanceof com.neosyn.cg.cg.Enum) {
			return CgUtil.getEnumLiteralValue(instantiator, entity, variable);
		}

		Object value;
		if (CgUtil.isConstant(variable)) {
			// only returns the value for constants
			// no cross-variable initializations
			if (CgUtil.isFunction(variable)) {
				// one day we should probably implement an evaluator for functions
				// until then we'll just return null
				return null;
			}

			if (CgUtil.isGlobal(variable)) {
				// global constant variables have already been mapped by the instantiator
				Var var = instantiator.getMapping(entity, variable);
				value = ValueUtil.getValue(var.getInitialValue());
			} else {
				value = doSwitch(variable.getValue());
			}
		} else {
			return null;
		}

		for (CgExpression index : expression.getIndexes()) {
			Object indexValue = doSwitch(index);
			if (!ValueUtil.isList(value)) {
				throw new IllegalArgumentException("trying to use a scalar as an array");
			}

			int ind = ValueUtil.getInt(indexValue);
			try {
				value = Array.get(value, ind);
			} catch (ArrayIndexOutOfBoundsException e) {
				return null;
			}
		}

		if (PROP_LENGTH.equals(expression.getPropertyName())) {
			return ValueUtil.isList(value) ? BigInteger.valueOf(Array.getLength(value)) : null;
		}

		return value;
	}

	@Override
	public Object doSwitch(EObject eObject) {
		if (eObject == null) {
			return null;
		}

		return super.doSwitch(eObject);
	}

	/**
	 * Returns the integer value associated with the given object using its URI. Returns -1 if the
	 * value is not an integer.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the integer value associated with the given object
	 */
	int getIntValue(Entity entity, EObject eObject) {
		Object value = getValue(entity, eObject);
		if (value != null && ValueUtil.isInt(value)) {
			BigInteger intExpr = (BigInteger) value;
			if (intExpr.bitLength() < Integer.SIZE) {
				return intExpr.intValue();
			}
		}

		// evaluated ok, but not as an integer
		return -1;
	}

	/**
	 * Returns the value associated with the given object using its URI.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the value associated with the given object
	 */
	Object getValue(Entity entity, EObject eObject) {
		Entity oldEntity = this.entity;
		this.entity = entity;
		try {
			return doSwitch(eObject);
		} catch (IllegalArgumentException e) {
			return null;
		} finally {
			this.entity = oldEntity;
		}
	}

}
