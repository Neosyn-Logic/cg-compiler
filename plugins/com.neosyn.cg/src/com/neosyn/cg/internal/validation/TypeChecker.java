/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.cg.CgConstants.NAME_SIZEOF;
import static com.neosyn.cg.validation.IssueCodes.ERR_CANNOT_ASSIGN_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_DIV_MOD_NOT_CONST_POW_Of_TWO;
import static com.neosyn.cg.validation.IssueCodes.ERR_EXPECTED_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_MISMATCH;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;
import static java.math.BigInteger.ZERO;
import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;
import static org.eclipse.xtext.validation.ValidationMessageAcceptor.INSIGNIFICANT_INDEX;

import java.math.BigInteger;
import java.util.Iterator;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.neosyn.cg.CgConstants;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementPrint;
import com.neosyn.cg.cg.StatementReturn;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.AstUtil;
import com.neosyn.cg.internal.services.Typer;
import com.neosyn.cg.services.CgPrinter;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.TypeBool;
import com.neosyn.models.ir.util.TypePrinter;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.ir.util.ValueUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines the type checker for Cx with package-access methods that are only accessed by
 * the Cx java validator class.
 * 

 * 
 */
public class TypeChecker extends Checker {

	private Entity entity;

	private final IInstantiator instantiator;

	public TypeChecker(ValidationMessageAcceptor acceptor, IInstantiator instantiator,
			Entity entity) {
		super(acceptor);
		this.instantiator = instantiator;
		this.entity = entity;
	}

	@Override
	public Void caseBranch(Branch stmt) {
		Type typeExpr = instantiator.computeType(entity, stmt.getCondition());
		checkAssign(IrFactory.eINSTANCE.createTypeBool(), typeExpr, stmt,
				Literals.BRANCH__CONDITION, INSIGNIFICANT_INDEX);

		return visit(this, stmt.getCondition(), stmt.getBody());
	}

	@Override
	public Void caseExpressionBinary(ExpressionBinary expression) {
		OpBinary op = OpBinary.getOperator(expression.getOperator());
		CgExpression e1 = expression.getLeft();
		CgExpression e2 = expression.getRight();
		if (e1 == null || e2 == null) {
			return DONE;
		}

		// check sub expressions
		doSwitch(e1);
		doSwitch(e2);

		// /, %, <<, >> require a constant (and power-of-two for / and %) right
		// operand — no hardware divider / variable shifter is generated. Without
		// this the divisor falls through to the IR transformer, which NPEs on a
		// variable divisor and silently miscompiles a non-power-of-two constant
		// (e.g. `x % 3` -> `x & 2`).
		checkExprBinaryHasConstantRightOperand(expression);

		// get types
		Type t1 = instantiator.computeType(entity, e1);
		Type t2 = instantiator.computeType(entity, e2);
		if (t1 == null || t2 == null) {
			return DONE;
		}

		Type type = instantiator.computeType(entity, expression);
		if (type == null) {
			error("The operator " + op.getText() + " is undefined for the argument types "
					+ new TypePrinter().toString(t1) + " and " + new TypePrinter().toString(t2),
					expression, null, INSIGNIFICANT_INDEX);
		}

		if (op.isComparison() && t1 != null && t2 != null) {
			// Range<BigInteger> rangeT1 = getRange(e1, t1);
			// Range<BigInteger> rangeT2 = getRange(e2, t2);
			// if (op == OpBinary.LT) {
			// if (rangeT2.upperEndpoint().compareTo(rangeT1.lowerEndpoint()) <= 0) {
			// error("This condition is always false", expression, null, ERR_CMP_ALWAYS_FALSE);
			// return;
			// }
			//
			// if (rangeT2.lowerEndpoint().compareTo(rangeT1.upperEndpoint()) > 0) {
			// error("This condition is always true", expression, null, ERR_CMP_ALWAYS_TRUE);
			// return;
			// }
			// } else if (op == OpBinary.GT) {
			// if (rangeT2.upperEndpoint().compareTo(rangeT1.lowerEndpoint()) < 0) {
			// error("This condition is always true", expression, null, ERR_CMP_ALWAYS_TRUE);
			// return;
			// }
			//
			// if (rangeT2.lowerEndpoint().compareTo(rangeT1.upperEndpoint()) >= 0) {
			// error("This condition is always false", expression, null, ERR_CMP_ALWAYS_FALSE);
			// return;
			// }
			// }
		}

		return DONE;
	}

	@Override
	public Void caseExpressionCast(ExpressionCast cast) {
		CgExpression expression = cast.getExpression();
		Type source = instantiator.computeType(entity, expression);
		Type target = instantiator.computeType(entity, cast.getType());
		if (source != null && target != null && !TypeUtil.canCast(source, target)) {
			error("Type mismatch: cannot convert from " + new TypePrinter().toString(source)
					+ " to " + new TypePrinter().toString(target), cast, null, INSIGNIFICANT_INDEX,
					ERR_TYPE_MISMATCH);
		}

		return visit(this, expression);
	}

	@Override
	public Void caseExpressionIf(ExpressionIf expr) {
		Type typeExpr = instantiator.computeType(entity, expr.getCondition());
		if (!(typeExpr instanceof TypeBool)) {
			error("Type mismatch: cannot convert from " + new TypePrinter().toString(typeExpr)
					+ " to bool", expr, Literals.EXPRESSION_IF__CONDITION, ERR_TYPE_MISMATCH);
		}

		return DONE;
	}

	@Override
	public Void caseExpressionUnary(ExpressionUnary expression) {
		CgExpression subExpr = expression.getExpression();
		if (subExpr == null) {
			return DONE;
		}

		// check sub expressions
		doSwitch(subExpr);

		Type typeSubExpr = instantiator.computeType(entity, subExpr);
		if (typeSubExpr == null) {
			return DONE;
		}

		if (NAME_SIZEOF.equals(expression.getOperator())) {
			// no need to get type of sizeof expression
			return DONE;
		}

		Type type = instantiator.computeType(entity, expression);
		if (type == null) {
			OpUnary op = OpUnary.getOperator(expression.getOperator());
			error("The operator " + op.getText() + " is undefined for the argument type "
					+ new TypePrinter().toString(typeSubExpr), expression, null,
					INSIGNIFICANT_INDEX);
		}
		return DONE;
	}

	@Override
	public Void caseExpressionVariable(ExpressionVariable expression) {
		doSwitch(expression.getSource());

		Variable variable = expression.getSource().getVariable();
		if (CgUtil.isFunction(variable)) {
			checkParameters(variable, expression);
		} else {
			if (!expression.getParameters().isEmpty()) {
				error("Type mismatch: '" + variable.getName()
						+ "' is not a function and is given arguments", expression, null,
						ERR_TYPE_MISMATCH);
			}

			if (CgConstants.PROP_LENGTH.equals(expression.getPropertyName())) {
				Type type = instantiator.computeType(entity, expression);
				if (type == null) {
					Type typeSource = instantiator.computeType(entity, expression.getSource());
					error("The length property is undefined for the argument type "
							+ new TypePrinter().toString(typeSource), expression, null,
							INSIGNIFICANT_INDEX);
				}
			}
		}

		return DONE;
	}

	@Override
	public Void caseInst(Inst inst) {
		final Task task = inst.getTask();
		if (task != null) {
			Entity oldEntity = entity;
			Instance instance = instantiator.getMapping(entity, inst);
			entity = instance.getEntity();
			doSwitch(task);
			entity = oldEntity;
		}
		return DONE;
	}

	@Override
	public Void caseStatementAssign(StatementAssign stmt) {
		if (stmt.getValue() == null) {
			// increment or decrement, check the variable has been initialized before
			doSwitch(stmt.getTarget());
		} else {
			doSwitch(stmt.getValue());
		}

		ExpressionVariable target = stmt.getTarget();
		VarRef ref = target.getSource();
		Variable variable = ref.getVariable();

		// if this is an assignment, check target is constant
		if (CgUtil.isConstant(variable) && stmt.getOp() != null) {
			error("The constant '" + variable.getName() + "' cannot be assigned.", stmt,
					Literals.STATEMENT_ASSIGN__TARGET, ERR_CANNOT_ASSIGN_CONST);
			return DONE;
		}

		// compute type of variable
		Type targetType = instantiator.computeType(entity, ref);
		if (targetType == null) {
			return DONE;
		}

		// check array access
		checkArrayAccess(stmt, targetType, target.getIndexes());

		// compute type of target
		int dimensions = Typer.getNumDimensions(targetType);
		int indexes = target.getIndexes().size();
		if (indexes == dimensions + 1) {
			targetType = IrFactory.eINSTANCE.createTypeBool();
		} else if (indexes == dimensions) {
			if (targetType.isArray()) {
				targetType = ((TypeArray) targetType).getElementType();
			}
		} else {
			return null;
		}

		// check type
		CgExpression value = AstUtil.getAssignValue(stmt);
		checkAssignImplicitBool(targetType, instantiator.computeType(entity, value), stmt,
				Literals.STATEMENT_ASSIGN__VALUE, INSIGNIFICANT_INDEX);

		return DONE;
	}

	@Override
	public Void caseStatementLoop(StatementLoop stmt) {
		Type typeExpr = instantiator.computeType(entity, stmt.getCondition());
		checkAssign(IrFactory.eINSTANCE.createTypeBool(), typeExpr, stmt,
				Literals.STATEMENT_LOOP__CONDITION, INSIGNIFICANT_INDEX);

		return visit(this, stmt.getInit(), stmt.getCondition(), stmt.getBody(), stmt.getAfter());
	}

	@Override
	public Void caseStatementPrint(StatementPrint stmt) {
		for (CgExpression expr : stmt.getArgs()) {
			doSwitch(expr);

			Type type = instantiator.computeType(entity, expr);
			if (type != null && type.isVoid()) {
				error("Type mismatch: cannot print void", expr, null, ERR_TYPE_MISMATCH);
			}
		}

		return DONE;
	}

	@Override
	public Void caseStatementReturn(StatementReturn stmt) {
		Variable function = getContainerOfType(stmt, Variable.class);
		Type target = instantiator.computeType(entity, function);

		CgExpression value = stmt.getValue();
		if (value == null) {
			if (target != null && !target.isVoid()) {
				error("This method must return a result of type "
						+ new TypePrinter().toString(target), stmt, null, ERR_TYPE_MISMATCH);
			}
		} else {
			checkAssign(target, instantiator.computeType(entity, value), stmt,
					Literals.STATEMENT_RETURN__VALUE, INSIGNIFICANT_INDEX);
		}

		return DONE;
	}

	@Override
	public Void caseStatementVariable(StatementVariable stmt) {
		int index = 0;
		for (Variable variable : stmt.getVariables()) {
			Type typeVar = instantiator.computeType(entity, variable);
			EObject value = variable.getValue();
			if (value != null) {
				// type check of value
				doSwitch(value);

				// check assignment
				checkAssignImplicitBool(typeVar, instantiator.computeType(entity, value), stmt,
						Literals.STATEMENT_VARIABLE__VARIABLES, index);
			}
			index++;
		}

		return DONE;
	}

	@Override
	public Void caseStatementWrite(StatementWrite stmt) {
		Type typePort = instantiator.computeType(entity, stmt.getPort());
		Type typeExpr = instantiator.computeType(entity, stmt.getValue());
		checkAssignImplicitBool(typePort, typeExpr, stmt, Literals.STATEMENT_WRITE__VALUE,
				INSIGNIFICANT_INDEX);

		return visit(this, stmt.getValue());
	}

	@Override
	public Void caseTask(final Task task) {
		visit(TypeChecker.this, CgUtil.getFunctions(task));
		return DONE;
	}

	private void checkArrayAccess(EObject source, Type type, EList<CgExpression> indexes) {
		if (indexes.isEmpty()) {
			// no indexes, nothing to check
			return;
		}

		if (type == null) {
			// no valid type, nothing to check at this point
			return;
		}

		int actual = indexes.size();
		int dimensions = Typer.getNumDimensions(type);
		if (actual < dimensions) {
			// there are not enough indexes
			error("Type mismatch: cannot convert from array to scalar", source, null,
					ERR_TYPE_MISMATCH);
		} else if (actual > dimensions + 1) {
			// there are too many indexes
			error("Type mismatch: cannot convert from number to array", source, null,
					ERR_TYPE_MISMATCH);
		} else if (actual == dimensions + 1) {
			// bit selection
			checkBitSelect(type, indexes.get(actual - 1));
		}
	}

	public void checkBitSelect(ExpressionVariable expr) {
		Type type = instantiator.computeType(entity, expr.getSource());
		checkArrayAccess(expr, type, expr.getIndexes());
	}

	/**
	 * Checks bit selection on a variable with the given type and at the given index is valid.
	 * 
	 * @param type
	 *            type of the variable
	 * @param index
	 *            index as an expression
	 */
	private void checkBitSelect(Type type, CgExpression index) {
		Object value = instantiator.evaluate(entity, index);
		if (!ValueUtil.isInt(value)) {
			error("Type mismatch: cannot convert value to constant integer in bit selection", index,
					null, ERR_TYPE_MISMATCH);
			return;
		}

		// Range<BigInteger> rangeActual = Ranges.singleton((BigInteger) value);
		// Range<BigInteger> rangeExpected = Ranges.closedOpen(BigInteger.ZERO,
		// BigInteger.valueOf(((TypeInt) type).getSize()));
		// if (rangeExpected.intersection(rangeActual).isEmpty()) {
		// error("Type mismatch: bit-selection index is outside of range", index, null,
		// ERR_TYPE_MISMATCH);
		// }
	}

	/**
	 * When the operator is /, %, &lt;&lt;, &gt;&gt;, and the expression is used outside of state
	 * variable initialization, checks that the right operand of the given binary expression is
	 * constant.
	 * 
	 * @param expr
	 */
	public void checkExprBinaryHasConstantRightOperand(ExpressionBinary expr) {
		Variable var = getContainerOfType(expr, Variable.class);
		if (var != null && var.eContainer() instanceof VarDecl) {
			// use of /, %, <<, >> allowed in initialization of state variables
			return;
		}

		String op = expr.getOperator();
		if ("/".equals(op) || "%".equals(op)) {
			Object value = instantiator.evaluate(entity, expr.getRight());
			if (value == null || !ValueUtil.isInt(value)) {
				error("The right operand of operator " + op + " must be constant", expr, null,
						ERR_DIV_MOD_NOT_CONST_POW_Of_TWO);
				return;
			}

			int v = ((BigInteger) value).intValue();
			if (!ValueUtil.isPowerOfTwo(v)) {
				error("The right operand of operator " + op
						+ " must be a power of two and greater than zero", expr, null,
						ERR_DIV_MOD_NOT_CONST_POW_Of_TWO);
			}
		} else if ("<<".equals(op) || ">>".equals(op)) {
			Object value = instantiator.evaluate(entity, expr.getRight());
			if (value == null || !ValueUtil.isInt(value)) {
				error("The right operand of operator " + op + " must be constant", expr, null,
						ERR_EXPECTED_CONST);
			}
		}
	}

	@Check
	public void checkIdle(StatementIdle idle) {
		CgExpression numCycles = idle.getNumCycles();
		Object value = instantiator.evaluate(entity, numCycles);
		if (!ValueUtil.isInt(value)) {
			error("Illegal idle: the number of cycles must be a compile-time constant integer",
					numCycles, null, ERR_EXPECTED_CONST);
		} else if (!ValueUtil.isTrue(ValueUtil.gt(value, ZERO))) {
			error("Illegal idle: the number of cycles must be greater than zero", numCycles, null,
					ERR_EXPECTED_CONST);
		}
	}

	private void checkParameters(Variable function, ExpressionVariable call) {
		EList<CgExpression> arguments = call.getParameters();
		Iterable<Type> types = Iterables.transform(arguments, new Function<CgExpression, Type>() {
			@Override
			public Type apply(CgExpression expression) {
				return instantiator.computeType(entity, expression);
			}
		});

		EList<Variable> parameters = function.getParameters();
		if (parameters.size() == arguments.size()) {
			Iterator<Variable> itV = parameters.iterator();
			Iterator<Type> itT = types.iterator();
			boolean hasErrors = false;
			while (itV.hasNext() && itT.hasNext()) {
				Type target = instantiator.computeType(entity, itV.next());
				Type source = itT.next();
				if (target == null || source == null) {
					continue;
				} else if (!TypeUtil.canAssign(source, target)) {
					hasErrors = true;
					break;
				}
			}

			if (!hasErrors) {
				return;
			}
		}

		Iterable<String> typeStr = Iterables.transform(types, new Function<Type, String>() {
			@Override
			public String apply(Type type) {
				return new TypePrinter().toString(type);
			}
		});

		error("The method " + new CgPrinter().toString(function)
				+ " is not applicable for the arguments (" + Joiner.on(", ").join(typeStr) + ")",
				call, null, ERR_TYPE_MISMATCH);
	}

	// private Range<BigInteger> getRange(CgExpression expr, Type type) {
	// Object value = Evaluator.getValue(expr);
	// if (ValueUtil.isInt(value)) {
	// return Ranges.singleton((BigInteger) value);
	// } else {
	// return getRange(type);
	// }
	// }

	// private Range<BigInteger> getRange(Type type) {
	// if (type.isInt()) {
	// TypeInt typeInt = (TypeInt) type;
	// int size = typeInt.getSize();
	// if (typeInt.isSigned()) {
	// BigInteger lower = ONE.shiftLeft(size - 1).negate();
	// BigInteger upper = ONE.shiftLeft(size - 1).subtract(ONE);
	// return Ranges.closed(lower, upper);
	// } else {
	// BigInteger lower = ZERO;
	// BigInteger upper = ONE.shiftLeft(size).subtract(ONE);
	// return Ranges.closed(lower, upper);
	// }
	// }
	// return null;
	// }

}
