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
import static com.neosyn.cg.validation.IssueCodes.ERR_OPERATOR_UNDEFINED;
import static com.neosyn.cg.validation.IssueCodes.ERR_BOOL_ARITHMETIC;
import static com.neosyn.cg.validation.IssueCodes.ERR_CANNOT_ASSIGN_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_DIV_MOD_NOT_CONST_POW_Of_TWO;
import static com.neosyn.cg.validation.IssueCodes.ERR_EXPECTED_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_INIT_VALUE_OUT_OF_RANGE;
import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_MISMATCH;
import static com.neosyn.cg.validation.IssueCodes.ERR_CAST_IMPOSSIBLE;
import static com.neosyn.cg.validation.IssueCodes.WARN_SUM_WRAPS;
import static com.neosyn.cg.validation.IssueCodes.WARN_WIDE_MULTIPLY;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;
import static java.math.BigInteger.ZERO;
import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;
import static org.eclipse.xtext.validation.ValidationMessageAcceptor.INSIGNIFICANT_INDEX;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.neosyn.cg.CgConstants;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionList;
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
import com.neosyn.models.ir.TypeInt;
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

		// /, %, <<, >> require a constant right operand: a variable divisor / shift
		// amount has no runtime datapath here (a variable divisor must use the
		// std.math.Divide built-in). A constant / or % is lowered in the IR
		// transformer — a power of two to a shift/mask, any other positive divisor
		// to a reciprocal (magic-number) multiply.
		checkExprBinaryHasConstantRightOperand(expression);

		// get types
		Type t1 = instantiator.computeType(entity, e1);
		Type t2 = instantiator.computeType(entity, e2);
		if (t1 == null || t2 == null) {
			return DONE;
		}

		checkWideMultiply(op, e1, e2, t1, t2, expression);

		Type type = instantiator.computeType(entity, expression);
		boolean shiftOrMod = op == OpBinary.MOD || op == OpBinary.SHIFT_LEFT || op == OpBinary.SHIFT_RIGHT;
		if (type == null && (op.isArithmetic() || shiftOrMod)
				&& (t1 instanceof TypeBool || t2 instanceof TypeBool)) {
			// Blocking (T181): `1 - rowp` was only the warning below, and IR generation then crashed
			// on the untyped expression. Comparisons (`bool == 1`) keep the non-blocking code.
			error("The operator " + op.getText() + " is undefined for the argument types "
					+ new TypePrinter().toString(t1) + " and " + new TypePrinter().toString(t2)
					+ " — a bool is not a number; write `b ? 1 : 0` to get 1 or 0",
					expression, null, ERR_BOOL_ARITHMETIC);
		} else if (type == null) {
			// Carries a real issue code so the CLI can BLOCK on it. Without one it was
			// downgraded to an advisory warning and the untypable expression reached the
			// backend, which NPE'd and skipped the entity behind a "Partial success".
			error("The operator " + op.getText() + " is undefined for the argument types "
					+ new TypePrinter().toString(t1) + " and " + new TypePrinter().toString(t2),
					expression, null, ERR_OPERATOR_UNDEFINED);
		}


		return DONE;
	}

	@Override
	public Void caseExpressionCast(ExpressionCast cast) {
		CgExpression expression = cast.getExpression();
		Type source = instantiator.computeType(entity, expression);
		Type target = instantiator.computeType(entity, cast.getType());
		if (source != null && target != null && !TypeUtil.canCast(source, target)) {
			// Its own code, which blocks generate and simulate: nothing can be built from it, and
			// the Verilog printer crashed on the cast after this was shown as a mere warning (T26).
			String hint = source instanceof TypeBool && !(target instanceof TypeBool)
					? " \u2014 a bool is not a number; write `b ? 1 : 0` to get 1 or 0" : "";
			error("Type mismatch: cannot convert from " + new TypePrinter().toString(source)
					+ " to " + new TypePrinter().toString(target) + hint, cast, null,
					INSIGNIFICANT_INDEX, ERR_CAST_IMPOSSIBLE);
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
		// The index expressions themselves (T181): `buf[1 - rowp]` was never visited, so the bool
		// arithmetic in it drew no message at all and IR generation crashed on it.
		for (CgExpression index : expression.getIndexes()) {
			doSwitch(index);
		}

		Variable variable = expression.getSource().getVariable();
		if (CgUtil.isFunction(variable)) {
			checkParameters(variable, expression);
		} else {
			if (!expression.getParameters().isEmpty()) {
				error("Type mismatch: '" + variable.getName()
						+ "' is not a function and is given arguments", expression, null,
						ERR_TYPE_MISMATCH);
			}

			// The indexes of a READ: only assignment targets were checked, so `a[0][1][2]` on a
			// one-dimensional array and `w[99]` on a u16 compiled without a word (TRAPS T69).
			if (!expression.getIndexes().isEmpty()) {
				checkBitSelect(expression);
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
			// the target's indexes are expressions too (T181: `buf[1 - rowp] = m` went unchecked)
			for (CgExpression index : stmt.getTarget().getIndexes()) {
				doSwitch(index);
			}
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

		// check type. Assigning to a LOCAL variable of a declared width tolerates
		// integer narrowing (truncation to that width is the intent); interface
		// boundaries (write/return/connect) keep the strict check.
		CgExpression value = AstUtil.getAssignValue(stmt);
		Type valueType = instantiator.computeType(entity, value);
		checkAssignAllowTruncation(targetType, valueType, stmt,
				Literals.STATEMENT_ASSIGN__VALUE, INSIGNIFICANT_INDEX);
		warnIfSumWraps(targetType, valueType, value, stmt, Literals.STATEMENT_ASSIGN__VALUE,
				INSIGNIFICANT_INDEX);

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

	/**
	 * M1 (model friendliness, 2026-09-30): `u8 s = a + b + c + d;` compiled without a word and the
	 * sum wrapped at 8 bits -- the broken average that gave a user's pool2x2 58/77/20 for 80. Warn
	 * when a `+` or `*` of two NON-constant operands is stored into a narrower integer, naming the
	 * width that fits. `i = i + 1` (constant operand) and an explicit cast (the wrap is intended) stay
	 * silent.
	 */
	private void warnIfSumWraps(Type target, Type value, EObject expr, EObject source,
			EStructuralFeature feature, int index) {
		if (!(expr instanceof ExpressionBinary) || !(target instanceof TypeInt)) {
			return;
		}
		ExpressionBinary binary = (ExpressionBinary) expr;
		String op = binary.getOperator();
		if (!"+".equals(op) && !"*".equals(op)) {
			return;
		}
		if (isConstant(binary.getLeft()) || isConstant(binary.getRight())) {
			return;
		}
		// The VALUE range, not the type width: each `+` widens the type by a bit, but four u8 only
		// reach 1020 (10 bits, not 11), and `(i64) (i32) a * (i64) (i32) b` fits an i64 although the
		// type says 128 bits. Comparing widths flagged RISC-V's MULH and Sobel's kernel, both correct.
		BigInteger[] r = range(binary);
		if (r == null) {
			return;
		}
		BigInteger[] t = rangeOf((TypeInt) target);
		if (r[0].compareTo(t[0]) >= 0 && r[1].compareTo(t[1]) <= 0) {
			return;
		}
		boolean signed = r[0].signum() < 0;
		int bits = signed ? Math.max(r[1].bitLength(), r[0].negate().subtract(BigInteger.ONE).bitLength()) + 1
				: r[1].bitLength();
		String kind = "+".equals(op) ? "sum" : "product";
		warning("This " + kind + " can reach " + r[0] + ".." + r[1] + " but is stored in "
				+ new TypePrinter().toString(target) + " (" + t[0] + ".." + t[1] + ") -- it wraps before it is"
				+ " used. Declare the variable " + (signed ? "i" : "u") + bits + ", or cast it if the wrap is"
				+ " intended.", source, feature, index, WARN_SUM_WRAPS);
	}

	/** The value range of an expression, by interval arithmetic on the source; null when unknown. */
	private BigInteger[] range(CgExpression e) {
		try {
			Object c = instantiator.evaluate(entity, e);
			if (c instanceof BigInteger) {
				return new BigInteger[] { (BigInteger) c, (BigInteger) c };
			}
		} catch (RuntimeException ex) {
			// not a constant
		}
		if (e instanceof ExpressionCast) {
			Type cast = instantiator.computeType(entity, e);
			BigInteger[] inner = range(((ExpressionCast) e).getExpression());
			if (!(cast instanceof TypeInt)) {
				return null;
			}
			BigInteger[] t = rangeOf((TypeInt) cast);
			// widening keeps the inner range; a narrowing cast wraps on purpose, to the cast type's range
			return inner != null && inner[0].compareTo(t[0]) >= 0 && inner[1].compareTo(t[1]) <= 0 ? inner : t;
		}
		if (e instanceof ExpressionUnary && "-".equals(((ExpressionUnary) e).getOperator())) {
			BigInteger[] r = range(((ExpressionUnary) e).getExpression());
			return r == null ? null : new BigInteger[] { r[1].negate(), r[0].negate() };
		}
		if (e instanceof ExpressionBinary) {
			ExpressionBinary b = (ExpressionBinary) e;
			String op = b.getOperator();
			if ("+".equals(op) || "-".equals(op) || "*".equals(op)) {
				BigInteger[] l = range(b.getLeft());
				BigInteger[] r = range(b.getRight());
				if (l == null || r == null) {
					return null;
				}
				if ("+".equals(op)) {
					return new BigInteger[] { l[0].add(r[0]), l[1].add(r[1]) };
				}
				if ("-".equals(op)) {
					return new BigInteger[] { l[0].subtract(r[1]), l[1].subtract(r[0]) };
				}
				BigInteger[] p = { l[0].multiply(r[0]), l[0].multiply(r[1]), l[1].multiply(r[0]), l[1].multiply(r[1]) };
				BigInteger min = p[0], max = p[0];
				for (BigInteger v : p) {
					min = min.min(v);
					max = max.max(v);
				}
				return new BigInteger[] { min, max };
			}
		}
		Type t = instantiator.computeType(entity, e);
		return t instanceof TypeInt ? rangeOf((TypeInt) t) : null;
	}

	private static BigInteger[] rangeOf(TypeInt t) {
		int n = t.getSize();
		return t.isSigned()
				? new BigInteger[] { BigInteger.ONE.shiftLeft(n - 1).negate(), BigInteger.ONE.shiftLeft(n - 1).subtract(BigInteger.ONE) }
				: new BigInteger[] { BigInteger.ZERO, BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE) };
	}

	/**
	 * TRAPS T173: a state variable's initial value was never compared with its type (the check was a
	 * TODO in StructuralValidator). `u2 w = 50;` became 2 in every back end without a word, and a
	 * 5-value initialiser for a 2-element array crashed the simulator (ArrayIndexOutOfBounds) while
	 * the Verilog wrote past the array. Both are now blocking errors. A SHORTER initialiser is left
	 * alone. Run per instance (forEachMapping), so a generic width is known here.
	 */
	public void checkStateVariableValues(CgEntity cxEntity) {
		for (Variable variable : EcoreUtil2.eAllOfType(cxEntity, Variable.class)) {
			if (variable.getValue() == null || !CgUtil.isGlobal(variable) || CgUtil.isFunction(variable)) {
				continue;
			}
			Type type;
			try {
				type = instantiator.computeType(entity, variable);
			} catch (RuntimeException e) {
				continue;
			}
			checkInitialValue(variable, type, variable.getValue());
		}
	}

	private void checkInitialValue(Variable variable, Type type, CgExpression value) {
		if (type instanceof TypeArray && value instanceof ExpressionList) {
			TypeArray array = (TypeArray) type;
			List<CgExpression> values = ((ExpressionList) value).getValues();
			int length = array.getDimensions().get(0);
			if (values.size() > length) {
				error("The initial value of '" + variable.getName() + "' has " + values.size() + " values for "
						+ length + " elements", value, null, INSIGNIFICANT_INDEX, ERR_INIT_VALUE_OUT_OF_RANGE);
				return;
			}
			if (array.getDimensions().size() == 1) {
				for (CgExpression element : values) {
					checkInitialValue(variable, array.getElementType(), element);
				}
			}
			return;
		}
		if (!(type instanceof TypeInt)) {
			return;
		}
		Object evaluated;
		try {
			evaluated = instantiator.evaluate(entity, value);
		} catch (RuntimeException e) {
			return;
		}
		if (!(evaluated instanceof BigInteger)) {
			return;
		}
		TypeInt typeInt = (TypeInt) type;
		int size = typeInt.getSize();
		BigInteger v = (BigInteger) evaluated;
		BigInteger min = typeInt.isSigned() ? BigInteger.ONE.shiftLeft(size - 1).negate() : ZERO;
		BigInteger max = typeInt.isSigned() ? BigInteger.ONE.shiftLeft(size - 1).subtract(BigInteger.ONE)
				: BigInteger.ONE.shiftLeft(size).subtract(BigInteger.ONE);
		if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
			BigInteger stored = v.mod(BigInteger.ONE.shiftLeft(size));
			if (typeInt.isSigned() && stored.compareTo(max) > 0) {
				stored = stored.subtract(BigInteger.ONE.shiftLeft(size));
			}
			error("The initial value " + v + " does not fit " + new TypePrinter().toString(type) + " (" + min
					+ " to " + max + "): it would be stored as " + stored, value, null, INSIGNIFICANT_INDEX,
					ERR_INIT_VALUE_OUT_OF_RANGE);
		}
	}

	private boolean isConstant(CgExpression e) {
		try {
			return instantiator.evaluate(entity, e) instanceof BigInteger;
		} catch (RuntimeException ex) {
			return false;
		}
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

				// check assignment. A local declaration's width is the intent, so
				// initializing it with a wider integer truncates rather than errors.
				Type valueType = instantiator.computeType(entity, value);
				checkAssignAllowTruncation(typeVar, valueType, stmt,
						Literals.STATEMENT_VARIABLE__VARIABLES, index);
				warnIfSumWraps(typeVar, valueType, value, stmt, Literals.STATEMENT_VARIABLE__VARIABLES,
						index);
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

		return visit(this, stmt.getValue(), stmt.getLast());
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

		// The selected bit must exist (TRAPS T69: `w[99]` on a u16 was accepted). An array's
		// bit select applies to its element.
		Type scalar = type instanceof TypeArray ? ((TypeArray) type).getElementType() : type;
		if (scalar instanceof TypeInt) {
			BigInteger bit = (BigInteger) value;
			int size = ((TypeInt) scalar).getSize();
			if (bit.signum() < 0 || bit.compareTo(BigInteger.valueOf(size)) >= 0) {
				error("Type mismatch: bit " + bit + " does not exist in a " + size
						+ "-bit value (bits 0 to " + (size - 1) + ")", index, null, ERR_TYPE_MISMATCH);
			}
		}
	}

	/**
	 * When the operator is /, %, &lt;&lt;, &gt;&gt;, and the expression is used outside of state
	 * variable initialization, checks that the right operand of the given binary expression is
	 * constant.
	 * 
	 * @param expr
	 */
	/**
	 * Advises when {@code a * b} of two runtime operands is a wide combinational multiplier.
	 * {@code *} is single-cycle and maps to DSP tiles, but a product of two operands each wider
	 * than a DSP's 18-bit port spans more than one tile and can miss timing at a high clock. A
	 * constant operand folds to shift-adds, so only a genuine variable*variable product is flagged.
	 * This is a hint, not an error — the multiply is correct either way.
	 */
	private void checkWideMultiply(OpBinary op, CgExpression e1, CgExpression e2, Type t1, Type t2,
			ExpressionBinary expression) {
		if (op != OpBinary.TIMES || !(t1 instanceof TypeInt) || !(t2 instanceof TypeInt)) {
			return;
		}
		if (instantiator.evaluate(entity, e1) instanceof BigInteger
				|| instantiator.evaluate(entity, e2) instanceof BigInteger) {
			return;
		}
		int w1 = ((TypeInt) t1).getSize();
		int w2 = ((TypeInt) t2).getSize();
		if (w1 > 18 && w2 > 18) {
			warning("This " + w1 + "x" + w2 + " multiply is a single combinational multiplier "
					+ "(wider than one DSP tile); if it does not meet timing, use the "
					+ "std.math.Multiply built-in for a registered product, or pipeline it across "
					+ "stages.", expression, null, INSIGNIFICANT_INDEX, WARN_WIDE_MULTIPLY);
		}
	}

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
				error("The right operand of operator " + op
						+ " must be a compile-time constant"
						+ " (use the std.math.Divide built-in for a variable divisor)", expr, null,
						ERR_DIV_MOD_NOT_CONST_POW_Of_TWO);
				return;
			}

			// Any positive constant is synthesizable: a power of two lowers to a shift/mask,
			// every other divisor to a reciprocal multiply (see FunctionTransformer). Only zero
			// and negative divisors are rejected.
			if (((BigInteger) value).signum() <= 0) {
				error("The right operand of operator " + op + " must be a positive constant", expr,
						null, ERR_DIV_MOD_NOT_CONST_POW_Of_TWO);
			}
		} else if ("<<".equals(op) || ">>".equals(op)) {
			Object value = instantiator.evaluate(entity, expr.getRight());
			if (value == null || !ValueUtil.isInt(value)) {
				error("The right operand of operator " + op + " must be a compile-time constant "
						+ "(a variable shift amount is not directly synthesizable — mux the input "
						+ "across the constant power-of-two shifts, i.e. a barrel shifter)", expr,
						null, ERR_EXPECTED_CONST);
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

}
