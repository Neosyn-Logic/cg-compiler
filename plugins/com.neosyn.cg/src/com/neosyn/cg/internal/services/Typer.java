/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import static com.neosyn.cg.CgConstants.NAME_SIZEOF;
import static com.neosyn.cg.CgConstants.PROP_AVAILABLE;
import static com.neosyn.cg.CgConstants.PROP_LAST;
import static com.neosyn.cg.CgConstants.PROP_LENGTH;
import static com.neosyn.cg.CgConstants.PROP_READ;
import static com.neosyn.cg.CgConstants.PROP_READY;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgType;
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
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.TypeRef;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.ir.util.ValueUtil;

/**
 * This class defines a typer for Cx AST. Note that types must have been
 * transformed to IR types first.
 * 
 */
public class Typer extends CgSwitch<Type> {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	/**
	 * Returns an array containing the dimensions of the given type. Returns an
	 * empty array if <code>type</code> is not an array.
	 * 
	 * @param type
	 *            a type
	 * @return an array of dimensions
	 */
	public static int getNumDimensions(final Type type) {
		if (type instanceof TypeArray) {
			return ((TypeArray) type).getDimensions().size();
		} else {
			return 0;
		}
	}

	private Entity entity;

	private IInstantiator instantiator;

	public Typer(IInstantiator instantiator) {
		this.instantiator = instantiator;
	}

	@Override
	public Type caseExpressionBinary(ExpressionBinary expression) {
		OpBinary op = OpBinary.getOperator(expression.getOperator());
		Type t1 = doSwitch(expression.getLeft());
		Type t2 = doSwitch(expression.getRight());

		CgExpression right = expression.getRight();
		Object amount = instantiator.evaluate(entity, right);
		return TypeUtil.getTypeBinary(op, t1, t2, amount);
	}

	@Override
	public Type caseExpressionBoolean(ExpressionBoolean expression) {
		return ir.createTypeBool();
	}

	@Override
	public Type caseExpressionCast(ExpressionCast expression) {
		return doSwitch(expression.getType());
	}

	@Override
	public Type caseExpressionFloat(ExpressionFloat expr) {
		return ir.createTypeFloat();
	}

	@Override
	public Type caseExpressionIf(ExpressionIf expression) {
		Type t1 = doSwitch(expression.getThen());
		Type t2 = doSwitch(expression.getElse());
		return TypeUtil.getLargest(t1, t2);
	}

	@Override
	public Type caseExpressionInteger(ExpressionInteger expression) {
		BigInteger value = expression.getValue();
		return ir.createTypeIntOrUint(value);
	}

	@Override
	public Type caseExpressionList(ExpressionList list) {
		List<CgExpression> values = list.getValues();
		int size = values.size();

		// compute the LUB of all expressions
		Type type = getType(values);
		return ir.createTypeArray(type, size);
	}

	@Override
	public Type caseExpressionString(ExpressionString expression) {
		return ir.createTypeString();
	}

	@Override
	public Type caseExpressionUnary(ExpressionUnary expression) {
		if (NAME_SIZEOF.equals(expression.getOperator())) {
			Object size = instantiator.evaluate(entity, expression);
			if (size == null) {
				// cannot evaluate size => returns null type
				return null;
			}
			return ir.createTypeIntOrUint((BigInteger) size);
		}

		OpUnary op = OpUnary.getOperator(expression.getOperator());
		CgExpression subExpr = expression.getExpression();
		Type type = doSwitch(subExpr);
		Object amount = instantiator.evaluate(entity, subExpr);
		return TypeUtil.getTypeUnary(op, type, amount);
	}

	@Override
	public Type caseExpressionVariable(ExpressionVariable expression) {
		VarRef ref = expression.getSource();
		Type type = doSwitch(ref);

		String prop = expression.getPropertyName();
		boolean isPort = CgUtil.isPort(ref.getVariable());
		if (PROP_AVAILABLE.equals(prop) || PROP_READY.equals(prop) || PROP_LAST.equals(prop)) {
			return isPort ? ir.createTypeBool() : null;
		} else if (PROP_READ.equals(prop)) {
			return isPort ? doSwitch(ref) : null;
		} else if (PROP_LENGTH.equals(prop)) {
			Object length = instantiator.evaluate(entity, expression);
			return ValueUtil.isInt(length) ? ir.createTypeIntOrUint((BigInteger) length) : null;
		}

		// if type is not valid
		if (type == null || type.isArray() && ((TypeArray) type).getElementType() == null) {
			return null;
		}

		int numDims = getNumDimensions(type);
		List<CgExpression> indexes = expression.getIndexes();

		// A RANGE SLICE v[hi:lo] is an unsigned integer of width hi-lo+1. A single-bit
		// select v[n] stays `bool` (below) — the two are different types on purpose, so
		// `v[3]` keeps behaving as it always has and only the new form is widened.
		// Bounds are compile-time constants (the grammar's SliceBound admits only literals,
		// names and one +/-), so the width is known here.
		if (expression.getSliceHigh() != null && expression.getSliceLow() != null) {
			Object hi = instantiator.evaluate(entity, expression.getSliceHigh());
			Object lo = instantiator.evaluate(entity, expression.getSliceLow());
			if (!ValueUtil.isInt(hi) || !ValueUtil.isInt(lo)) {
				return null;
			}
			int high = ((BigInteger) hi).intValue();
			int low = ((BigInteger) lo).intValue();
			if (high < low) {
				return null;
			}
			return ir.createTypeInt(high - low + 1, false);
		}

		if (indexes.isEmpty()) {
			return type;
		} else if (indexes.size() > numDims + 1) {
			// too many indexes
			return null;
		} else if (indexes.size() == numDims + 1) {
			// bit selection
			return ir.createTypeBool();
		} else {
			// scalar
			type = ((TypeArray) type).getElementType();
			return IrUtil.copy(type);
		}
	}

	@Override
	public Type caseTypeDecl(TypeDecl type) {
		String spec = type.getSpec();

		if ("bool".equals(spec)) {
			return ir.createTypeBool();
		} else if ("float".equals(spec)) {
			return ir.createTypeFloat();
		} else if ("void".equals(spec)) {
			return ir.createTypeVoid();
		}

		boolean signed = type.isSigned() || !type.isUnsigned();
		int size;

		if (spec == null || spec.isEmpty()) {
			// signed or unsigned with no qualifier means implicit "int"
			// if type is used in a TypeGen the size will be overridden
			size = 32;
		} else {
			char ch = spec.charAt(0);
			String rest;
			if (ch == 'u') {
				rest = spec.substring(1);
				signed = false;
			} else {
				rest = spec;
			}

			if ("char".equals(rest)) {
				size = 8;
				signed = type.isSigned(); // unless explicitly signed, char
											// defaults to unsigned
			} else if ("short".equals(rest)) {
				size = 16;
			} else if ("int".equals(rest)) {
				size = 32;
			} else if ("long".equals(rest)) {
				size = 64;
			} else {
				// like i15 or u29
				if (rest == spec) {
					// must use substring
					rest = spec.substring(1);
				}
				size = new BigInteger(rest).intValue();
			}
		}

		if (type.getSize() != null) {
			size = instantiator.evaluateInt(entity, type.getSize());
		}

		return ir.createTypeInt(size, signed);
	}

	@Override
	public Type caseTypedef(Typedef typedef) {
		Type type = instantiator.getMapping(entity, typedef);
		return EcoreUtil.copy(type);
	}

	@Override
	public Type caseTypeRef(TypeRef type) {
		return doSwitch(type.getTypeDef());
	}

	/**
	 * L2 — enum lowering: returns the underlying integer type. If the user
	 * wrote `enum N : u<W> { ... }`, that explicit type wins. Otherwise the
	 * width is derived from the max effective literal value (iter #2 honours
	 * explicit `LIT = N` values with C-style "previous + 1" fill). See
	 * .claude/L2_ENUM_DESIGN.md §6.
	 */
	@Override
	public Type caseEnum(com.neosyn.cg.cg.Enum enm) {
		CgType underlying = enm.getUnderlying();
		if (underlying != null) {
			return doSwitch(underlying);
		}
		BigInteger max = BigInteger.ZERO;
		for (Variable lit : enm.getLiterals()) {
			BigInteger v = CgUtil.getEnumLiteralValue(instantiator, entity, lit);
			if (v.compareTo(max) > 0) {
				max = v;
			}
		}
		int width = Math.max(1, max.bitLength());
		return ir.createTypeInt(width, false);
	}

	@Override
	public Type caseVariable(Variable variable) {
		if (CgUtil.isFunction(variable) && CgUtil.isVoid(variable)) {
			return ir.createTypeVoid();
		}

		// L2 — enum literal: type is the enclosing enum's underlying width.
		// EnumLiteral has no `type` slot in the grammar, so CgUtil.getType
		// would return null. See .claude/L2_ENUM_DESIGN.md §6.
		if (variable.eContainer() instanceof com.neosyn.cg.cg.Enum) {
			return doSwitch(variable.eContainer());
		}

		CgType varType = CgUtil.getType(variable);

		Type type = doSwitch(varType);
		List<CgExpression> dimensions = variable.getDimensions();
		if (dimensions.isEmpty()) {
			return type;
		}

		TypeArray array = ir.createTypeArray();
		array.setElementType(type);

		for (CgExpression dimension : dimensions) {
			Object value = instantiator.evaluate(entity, dimension);
			if (ValueUtil.isInt(value)) {
				array.getDimensions().add(((BigInteger) value).intValue());
			} else {
				array.getDimensions().add(0);
			}
		}
		return array;
	}

	@Override
	public Type caseVarRef(VarRef ref) {
		Variable variable = ref.getVariable();
		if (CgUtil.isPort(variable)) {
			Port port = instantiator.getPort(entity, ref);
			if (port == null) {
				// An ARRAY port has no single IR port -- it fanned out to one per element
				// -- so getPort is null for it by design. Every element carries the same
				// type, so the first one answers the question. Without this the reference
				// fell into printError, whose Eclipse marker API is absent from the CLI:
				// `(u8) W[0].read()` died with NoClassDefFoundError rather than typing.
				java.util.List<Port> elements = instantiator.getArrayPortElements(entity, ref);
				if (elements != null && !elements.isEmpty() && elements.get(0) != null) {
					return elements.get(0).getType();
				}
				printError(variable);
				return null;
			}
			return port.getType();
		}
		return doSwitch(ref.getVariable());
	}

	/**
	 * A port reference that has no IR port. In the Eclipse IDE that raises a workspace marker; in
	 * the standalone jar there is no workspace, and the marker call threw NoClassDefFoundError out
	 * of the validator, which printed only "Error executing EValidator" and skipped every check
	 * after it for that object (TRAPS T130: a STRUCT port, which flattens to one IR port per field
	 * like an array port does, reached here on every write). The type stays null, which every
	 * caller already treats as "not checkable".
	 */
	public void printError(Variable variable) {
		if (variable.eResource() != null && variable.eResource().getURI() != null
				&& variable.eResource().getURI().isPlatform()) {
			WorkspaceMarkers.portProblem(entity.getName(), variable.getName());
		}
	}

	@Override
	public Type doSwitch(EObject eObject) {
		if (eObject == null) {
			return null;
		}

		return super.doSwitch(eObject);
	}

	/**
	 * Computes and returns the type of the given object.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the type of the given object
	 */
	public Type getType(Entity entity, EObject eObject) {
		Entity oldEntity = this.entity;
		this.entity = entity;
		try {
			return doSwitch(eObject);
		} finally {
			this.entity = oldEntity;
		}
	}

	/**
	 * Returns the type of the given list of objects using their URI.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the type of the given object
	 */
	public Type getType(List<? extends EObject> eObjects) {
		Iterator<? extends EObject> it = eObjects.iterator();
		if (!it.hasNext()) {
			return null;
		}

		Type type = doSwitch(it.next());
		while (it.hasNext()) {
			type = TypeUtil.getLargest(type, doSwitch(it.next()));
		}

		return type;
	}

}
