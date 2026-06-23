/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import static com.neosyn.cg.CgConstants.NAME_SIZEOF;
import static com.neosyn.cg.CgConstants.PROP_LENGTH;
import static com.neosyn.cg.internal.TransformerUtil.getStartLine;
import static com.neosyn.models.ir.OpBinary.BITAND;
import static com.neosyn.models.ir.OpBinary.DIV;
import static com.neosyn.models.ir.OpBinary.MOD;
import static com.neosyn.models.ir.OpBinary.SHIFT_LEFT;
import static com.neosyn.models.ir.OpBinary.SHIFT_RIGHT;
import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.ExpressionBinary;
import com.neosyn.cg.cg.ExpressionBoolean;
import com.neosyn.cg.cg.ExpressionCast;
import com.neosyn.cg.cg.ExpressionFloat;
import com.neosyn.cg.cg.ExpressionIf;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionString;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Statement;
import com.neosyn.cg.cg.StatementAssert;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementBreak;
import com.neosyn.cg.cg.StatementContinue;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLabeled;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementPrint;
import com.neosyn.cg.cg.StatementReturn;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.AstUtil;
import com.neosyn.cg.internal.services.Typer;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstCall;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.ir.util.ValueUtil;

/**
 * This class transforms Cx statement into IR blocks and instructions.
 * 

 * @see IrBuilder
 */
public class FunctionTransformer extends CgSwitch<EObject>implements Transformer {

	protected static final IrFactory ir = IrFactory.eINSTANCE;

	protected final IrBuilder builder;

	/**
	 * Creates a new function transformer with the given entity.
	 * 
	 * @param entity
	 *            IR entity being created
	 */
	public FunctionTransformer(IInstantiator instantiator, Entity entity) {
		this(new IrBuilder(instantiator, entity));
	}

	/**
	 * Creates a new FunctionTransformer with the given IR builder, and set its expression
	 * transformer to <code>this</code>.
	 * 
	 * @param builder
	 *            IR builder
	 */
	protected FunctionTransformer(IrBuilder builder) {
		this.builder = builder;
		builder.setTransformer(this);
	}

	@Override
	public EObject caseBlock(Block block) {
		for (Statement statement : block.getStmts()) {
			doSwitch(statement);
		}

		return null;
	}

	@Override
	public Expression caseExpressionBinary(ExpressionBinary expression) {
		OpBinary op = OpBinary.getOperator(expression.getOperator());
		Expression e1 = transformExpr(expression.getLeft());
		Expression e2;

		if (op == DIV || op == MOD || op == SHIFT_LEFT || op == SHIFT_RIGHT) {
			// No hardware divider / variable shifter is emitted: /, %, <<, >> need
			// a compile-time-constant right operand, and / and % additionally need
			// a power of two (div -> shift, mod -> mask). Reject anything else with
			// a clear message instead of NPE-ing on the cast below or silently
			// miscompiling (e.g. `x % 3` -> `x & 2`).
			Object value = builder.instantiator.evaluate(builder.entity, expression.getRight());
			if (!(value instanceof BigInteger)) {
				throw new IllegalArgumentException("the right operand of '" + expression.getOperator()
						+ "' must be a compile-time constant"
						+ " (no hardware divider/variable-shifter is generated)");
			}
			BigInteger n = (BigInteger) value;
			boolean powerOfTwo = n.signum() > 0 && n.bitCount() == 1;

			if (op == OpBinary.DIV) {
				if (!powerOfTwo) {
					throw new IllegalArgumentException("division by " + n
							+ " is not synthesizable; the divisor must be a power of two");
				}
				// div n <=> right shift by log2(n)
				op = SHIFT_RIGHT;
				e2 = ir.createExprInt(n.bitLength() - 1);
			} else if (op == MOD) {
				if (!powerOfTwo) {
					throw new IllegalArgumentException("modulo by " + n
							+ " is not synthesizable; the divisor must be a power of two");
				}
				// mod n <=> & (n - 1)
				op = BITAND;
				e2 = ir.createExprInt(n.subtract(ONE));
			} else /* if (op == SHIFT_LEFT || op == SHIFT_RIGHT) */ {
				// shifts must have a constant second operand
				e2 = ir.createExprInt(n);
			}
		} else {
			// default case: transform expression
			e2 = transformExpr(expression.getRight());
		}

		return ir.createExprBinary(e1, op, e2);
	}

	@Override
	public Expression caseExpressionBoolean(ExpressionBoolean expression) {
		return ir.createExprBool(expression.isValue());
	}

	@Override
	public Expression caseExpressionCast(ExpressionCast expression) {
		Type targetType = builder.instantiator.computeType(builder.entity, expression.getType());

		CgExpression subExpr = expression.getExpression();
		Type sourceType = builder.instantiator.computeType(builder.entity, subExpr);
		Expression expr = transformExpr(subExpr);

		return ir.cast(targetType, sourceType, expr);
	}

	@Override
	public Expression caseExpressionFloat(ExpressionFloat expression) {
		return ir.createExprFloat(expression.getValue());
	}

	@Override
	public Expression caseExpressionIf(ExpressionIf expression) {
		builder.saveBlocks();

		// create block
		BlockIf block = ir.createBlockIf();
		block.setJoinBlock(ir.createBlockBasic());
		int lineNumber = getStartLine(expression);
		block.setLineNumber(lineNumber);

		// translate condition
		Expression condition = transformExpr(expression.getCondition());
		block.setCondition(condition);

		// add block (must be done after condition has been transformed)
		builder.add(block);

		// update target if necessary
		Type type = builder.instantiator.computeType(builder.entity, expression);
		Var target = builder.createLocal(lineNumber, type, "tmp_if");

		// transforms "then" and "else" expressions
		builder.setBlocks(block.getThenBlocks());
		builder.storeExpr(lineNumber, target, null, expression.getThen());

		builder.setBlocks(block.getElseBlocks());
		builder.storeExpr(lineNumber, target, null, expression.getElse());
		builder.restoreBlocks();

		// return expr
		return ir.createExprVar(target);
	}

	@Override
	public Expression caseExpressionInteger(ExpressionInteger expression) {
		return ir.createExprInt(expression.getValue());
	}

	@Override
	public Expression caseExpressionString(ExpressionString expression) {
		return ir.createExprString(expression.getValue());
	}

	@Override
	public Expression caseExpressionUnary(ExpressionUnary expression) {
		if (NAME_SIZEOF.equals(expression.getOperator())) {
			Object value = builder.instantiator.evaluate(builder.entity, expression);
			return ir.createExprInt(ValueUtil.getInt(value));
		}

		OpUnary op = OpUnary.getOperator(expression.getOperator());
		CgExpression subExpr = expression.getExpression();
		switch (op) {
		case MINUS:
			// replace ExprUnary(-, n) by ExprInt(-n)
			if (subExpr instanceof ExpressionInteger) {
				ExpressionInteger exprInt = (ExpressionInteger) subExpr;
				return ir.createExprInt(exprInt.getValue().negate());
			}

		default:
			// fall-through for other expressions
			Expression expr = transformExpr(subExpr);
			return ir.createExprUnary(op, expr);
		}
	}

	@Override
	public Expression caseExpressionVariable(ExpressionVariable expression) {
		Variable variable = expression.getSource().getVariable();
		if (CgUtil.isFunction(variable)) {
			return translateCall(expression);
		}

		// L2 — enum literal access (`IDLE` or `state_t.IDLE`). The literal
		// is a Variable whose container is the Enum; it has no IR Var.
		// Lower to ExprInt of the literal's effective value (iter #2 honours
		// explicit `LIT = N` values). See .claude/L2_ENUM_DESIGN.md §6.
		if (variable != null && variable.eContainer() instanceof com.neosyn.cg.cg.Enum) {
			return ir.createExprInt(
				CgUtil.getEnumLiteralValue(builder.instantiator, builder.entity, variable));
		}

		String prop = expression.getPropertyName();
		if (PROP_LENGTH.equals(prop)) {
			Object value = builder.instantiator.evaluate(builder.entity, expression);
			return ir.createExprInt((BigInteger) value);
		}

		Var source = builder.getMapping(variable);
		if (source == null) {
			// Struct field access (`p.lo`): `CgUtil.getVariable` returns the
			// StructField (shared between struct instances of the same type)
			// which has no direct IR mapping. Resolve through the per-parent
			// flattened map instead. See .claude/L1_STRUCT_DESIGN.md §7.
			source = builder.getStructFieldVar(expression.getSource());
		}
		if (source == null && !expression.getMembers().isEmpty()) {
			// Tier 2.3: `ps[i].lo` / `ps[i].lo.a` — member access after an index.
			// Resolve the flattened leaf array Var; the index below selects the
			// element.
			source = builder.getStructMemberVar(expression);
		}
		if (source == null) {
			// variable was null (unresolved cross-ref) or had no IR mapping —
			// typically when an Inst entity didn't resolve (`new arith.gcd.Gcd();`
			// with no import) so the network's instance has null entity and the
			// dependent port lookup yields no Var. Surface a useful error
			// instead of NPE-ing on source.getType().
			String refText = describeRef(expression);
			throw new IllegalStateException("Unresolved reference '" + refText
					+ "' — check that the Inst entity is imported or fully qualified");
		}
		Type type = source.getType();
		int dimensions = Typer.getNumDimensions(type);

		// loads variable (do not perform bit selection though)
		int lineNumber = getStartLine(expression);
		Var target = builder.loadVariable(lineNumber, source, expression.getIndexes());

		// bit selection
		ExprVar exprVar = ir.createExprVar(target);
		if (dimensions < expression.getIndexes().size()) {
			CgExpression exprIndex = expression.getIndexes().get(dimensions);
			int index = builder.instantiator.evaluateInt(builder.entity, exprIndex);
			ExprInt mask = ir.createExprInt(ONE.shiftLeft(index));
			ExprBinary exprBin = ir.createExprBinary(exprVar, OpBinary.BITAND, mask);

			ExprInt shift = ir.createExprInt(0);
			return ir.createExprBinary(exprBin, OpBinary.NE, shift);
		} else {
			return exprVar;
		}
	}

	@Override
	public EObject caseStatementAssert(StatementAssert stmtAssert) {
		hookBefore(stmtAssert);

		int lineNumber = getStartLine(stmtAssert);
		Expression condition = transformExpr(stmtAssert.getCondition());

		InstCall call = ir.createInstCall(lineNumber, null, null, Arrays.asList(condition));
		call.setAssert(true);
		builder.add(call);

		return null;
	}

	@Override
	public EObject caseStatementAssign(StatementAssign assign) {
		hookBefore(assign);

		if (assign.getOp() == null) {
			// no target
			transformExpr(assign.getValue());
		} else {
			// whole-struct assignment `p2 = p;` → field-wise copy (Tier 2.1).
			// Only a one-segment struct target with no indexes; `p.lo = ...`
			// (two segments) falls through to the scalar field-store path.
			Variable targetStructVar = IrBuilder.asWholeStructVar(
					assign.getTarget().getSource());
			boolean noIndexes = assign.getTarget().getIndexes() == null
					|| assign.getTarget().getIndexes().isEmpty();
			if (targetStructVar != null && noIndexes && "=".equals(assign.getOp())) {
				Struct struct = IrBuilder.asStructType(targetStructVar);
				// `q = port.read();` → field-wise load from the struct port.
				if (assign.getValue() instanceof ExpressionVariable) {
					java.util.Map<String, com.neosyn.models.dpn.Port> portFields =
							builder.getStructPortFields(
									((ExpressionVariable) assign.getValue()).getSource());
					if (portFields != null) {
						builder.loadStructFromPort(getStartLine(assign), targetStructVar,
								portFields, struct);
						return null;
					}
				}
				// `q = other;` → field-wise copy.
				Variable srcStruct = IrBuilder.asWholeStructValue(assign.getValue());
				if (srcStruct != null
						&& builder.copyStruct(getStartLine(assign), targetStructVar,
								srcStruct, struct)) {
					return null;
				}
			}

			// get target — handle `p.lo = ...` via the struct-field map first.
			// See .claude/L1_STRUCT_DESIGN.md §7.
			Variable variable = assign.getTarget().getSource().getVariable();
			Var target = builder.getMapping(variable);
			if (target == null) {
				target = builder.getStructFieldVar(assign.getTarget().getSource());
			}
			if (target == null && !assign.getTarget().getMembers().isEmpty()) {
				// Tier 2.3: `ps[i].lo = ...` — store into the flattened leaf array
				// element (the index on the target selects the element below).
				target = builder.getStructMemberVar(assign.getTarget());
			}

			// transform value
			int lineNumber = getStartLine(assign);
			CgExpression value = AstUtil.getAssignValue(assign);
			builder.storeExpr(lineNumber, target, assign.getTarget().getIndexes(), value);
		}

		return null;
	}

	@Override
	public EObject caseStatementIf(StatementIf stmtIf) {
		hookBefore(stmtIf);

		builder.saveBlocks();

		// transforms all branches (including 'else' branch)
		for (Branch stmt : stmtIf.getBranches()) {
			CgExpression condition = stmt.getCondition();
			if (condition == null) {
				// 'else' branch
				doSwitch(stmt.getBody());
			} else {
				// create If block
				BlockIf node = ir.createBlockIf();
				node.setJoinBlock(ir.createBlockBasic());
				node.setLineNumber(getStartLine(stmtIf));
				node.setCondition(transformExpr(condition));

				// add If to blocks
				builder.add(node);

				// transforms body in the "then" blocks
				builder.setBlocks(node.getThenBlocks());
				doSwitch(stmt.getBody());

				// next branch/else will be appended to the "else" blocks
				builder.setBlocks(node.getElseBlocks());
			}
		}

		builder.restoreBlocks();

		return null;
	}

	@Override
	public EObject caseStatementLabeled(StatementLabeled stmt) {
		return doSwitch(stmt.getStmt());
	}

	@Override
	public EObject caseStatementBreak(StatementBreak stmt) {
		// The scheduler decomposes a loop with break/continue into FSM states
		// (routing break -> loop exit, continue -> loop header) BEFORE this
		// transform runs, so a break should never survive to here. If one does,
		// the loop was not FSM-lowered — an internal scheduler bug. Fail loudly
		// rather than miscompile: a silent-wrong loop exit is the worst trap.
		throw new IllegalArgumentException("internal: 'break' reached IR generation un-lowered"
				+ " (the enclosing loop was not FSM-lowered by the scheduler)");
	}

	@Override
	public EObject caseStatementContinue(StatementContinue stmt) {
		throw new IllegalArgumentException("internal: 'continue' reached IR generation un-lowered"
				+ " (the enclosing loop was not FSM-lowered by the scheduler)");
	}

	/** Hard cap on the number of iterations we will unroll a constant loop into. */
	private static final int MAX_UNROLL = 1024;

	@Override
	public EObject caseStatementLoop(StatementLoop stmtFor) {
		hookBefore(stmtFor);

		// A `for` with a constant trip count is unrolled into straight-line code
		// so it lowers to synthesizable Verilog. Anything not provably constant
		// (data-dependent bound/step, `while`, oversized trip count) falls back
		// to the BlockWhile emission below. See project_cg_limitations.md #1.
		if (tryUnrollConstantLoop(stmtFor)) {
			return null;
		}

		// translate init
		doSwitch(stmtFor.getInit());

		builder.saveBlocks();

		// to track the instructions created when condition was transformed
		List<com.neosyn.models.ir.Block> initNodes = new ArrayList<>();
		builder.setBlocks(initNodes);

		// create the while
		BlockWhile nodeWhile = ir.createBlockWhile();
		nodeWhile.setJoinBlock(ir.createBlockBasic());
		int lineNumber = getStartLine(stmtFor);
		nodeWhile.setLineNumber(lineNumber);

		// transform condition
		Expression condition = transformExpr(stmtFor.getCondition());
		nodeWhile.setCondition(condition);

		// transform body and after
		builder.setBlocks(nodeWhile.getBlocks());
		doSwitch(stmtFor.getBody());
		doSwitch(stmtFor.getAfter());

		// copy instructions
		nodeWhile.getBlocks().addAll(IrUtil.copy(initNodes));

		builder.restoreBlocks();

		// add init nodes and while
		builder.addAll(initNodes);
		builder.add(nodeWhile);

		return null;
	}

	/**
	 * Attempts to unroll a constant-bound {@code for} loop into straight-line IR.
	 * The init value, the condition and the step must all evaluate to compile-time
	 * constants (the loop variable being the only "moving" symbol), the trip count
	 * must be at most {@link #MAX_UNROLL}, and the body must not reassign the loop
	 * variable. On success the loop variable is declared (via the original init),
	 * then for each iteration it is bound to the next constant value and the body is
	 * re-emitted. Returns {@code false} (leaving the IR untouched) when any of these
	 * conditions does not hold, so the caller falls back to the {@code while} lowering.
	 */
	private boolean tryUnrollConstantLoop(StatementLoop stmtFor) {
		// `while` loops reuse StatementLoop with no init/after — not unrollable here.
		if (stmtFor.getInit() == null || stmtFor.getAfter() == null
				|| stmtFor.getCondition() == null || stmtFor.getBody() == null) {
			return false;
		}

		// A loop whose subtree contains a break/continue must be FSM-lowered by the
		// scheduler (which owns break/continue), not unrolled here — unrolling would
		// re-emit the control statement straight into FunctionTransformer, which has
		// no lowering for it. The scheduler already forces such loops non-simple, so
		// this is defence in depth.
		if (CgUtil.loopSubtreeHasLoopControl(stmtFor)) {
			return false;
		}

		// identify the loop variable and its constant start value from the init
		Variable loopVar;
		BigInteger start;
		if (stmtFor.getInit() instanceof StatementVariable) {
			StatementVariable sv = (StatementVariable) stmtFor.getInit();
			if (sv.getVariables().size() != 1) {
				return false;
			}
			loopVar = sv.getVariables().get(0);
			start = asInt(evalConst(loopVar.getValue(), null, null));
		} else if (stmtFor.getInit() instanceof StatementAssign) {
			StatementAssign sa = (StatementAssign) stmtFor.getInit();
			if (!"=".equals(sa.getOp()) || !sa.getTarget().getIndexes().isEmpty()) {
				return false;
			}
			loopVar = sa.getTarget().getSource().getVariable();
			start = asInt(evalConst(sa.getValue(), null, null));
		} else {
			return false;
		}
		if (loopVar == null || start == null) {
			return false;
		}

		// the step must assign the loop variable; reduce it to a value expression
		StatementAssign after = stmtFor.getAfter();
		if (after.getTarget() == null
				|| after.getTarget().getSource().getVariable() != loopVar
				|| !after.getTarget().getIndexes().isEmpty()) {
			return false;
		}
		CgExpression stepExpr = AstUtil.getAssignValue(after);

		// a loop whose body rewrites the index can't be safely unrolled
		if (bodyWritesVariable(stmtFor.getBody(), loopVar)) {
			return false;
		}

		// evaluate the trip values entirely at compile time
		List<BigInteger> values = new ArrayList<>();
		BigInteger cur = start;
		while (true) {
			Object cond = evalConst(stmtFor.getCondition(), loopVar, cur);
			if (!(cond instanceof Boolean)) {
				return false; // condition not provably constant → give up
			}
			if (!((Boolean) cond)) {
				break;
			}
			if (values.size() >= MAX_UNROLL) {
				return false; // too large (or non-terminating) → fall back to while
			}
			values.add(cur);
			BigInteger next = asInt(evalConst(stepExpr, loopVar, cur));
			if (next == null) {
				return false; // step not provably constant → give up
			}
			cur = next;
		}

		// commit: declare the loop variable, then emit the body once per iteration
		// with the index bound to its constant value.
		doSwitch(stmtFor.getInit());
		Var loopVarIr = builder.getMapping(loopVar);
		if (loopVarIr == null) {
			return false;
		}
		int lineNumber = getStartLine(stmtFor);
		for (BigInteger value : values) {
			ExpressionInteger lit = CgFactory.eINSTANCE.createExpressionInteger();
			lit.setValue(value);
			builder.storeExpr(lineNumber, loopVarIr, Collections.emptyList(), lit);
			doSwitch(stmtFor.getBody());
		}
		return true;
	}

	/**
	 * Evaluates {@code expr} to a compile-time constant. The single variable
	 * {@code loopVar} (when non-null) resolves to {@code loopVal}; every other
	 * variable must itself be a constant (resolved through the instantiator).
	 * Returns a {@link BigInteger} or {@link Boolean}, or {@code null} when the
	 * expression is not a compile-time constant.
	 */
	private Object evalConst(CgExpression expr, Variable loopVar, BigInteger loopVal) {
		if (expr == null) {
			return null;
		}
		if (expr instanceof ExpressionInteger) {
			return ((ExpressionInteger) expr).getValue();
		}
		if (expr instanceof ExpressionBoolean) {
			return ((ExpressionBoolean) expr).isValue();
		}
		if (expr instanceof ExpressionVariable) {
			ExpressionVariable ev = (ExpressionVariable) expr;
			Variable v = ev.getSource() == null ? null : ev.getSource().getVariable();
			boolean bare = ev.getIndexes().isEmpty() && ev.getMembers().isEmpty()
					&& ev.getParameters().isEmpty() && ev.getPropertyName() == null;
			if (loopVar != null && v == loopVar && bare) {
				return loopVal;
			}
			return builder.instantiator.evaluate(builder.entity, expr);
		}
		if (expr instanceof ExpressionUnary) {
			ExpressionUnary u = (ExpressionUnary) expr;
			Object value = evalConst(u.getExpression(), loopVar, loopVal);
			if (value == null) {
				return null;
			}
			OpUnary op = OpUnary.getOperator(u.getOperator());
			return ValueUtil.compute(op, value);
		}
		if (expr instanceof ExpressionBinary) {
			ExpressionBinary b = (ExpressionBinary) expr;
			Object left = evalConst(b.getLeft(), loopVar, loopVal);
			Object right = evalConst(b.getRight(), loopVar, loopVal);
			if (left == null || right == null) {
				return null;
			}
			OpBinary op = OpBinary.getOperator(b.getOperator());
			return ValueUtil.compute(left, op, right);
		}
		if (expr instanceof ExpressionIf) {
			ExpressionIf eif = (ExpressionIf) expr;
			Object c = evalConst(eif.getCondition(), loopVar, loopVal);
			if (!(c instanceof Boolean)) {
				return null;
			}
			return evalConst(((Boolean) c) ? eif.getThen() : eif.getElse(), loopVar, loopVal);
		}
		if (expr instanceof ExpressionCast) {
			// the synthetic step expression (`k++` → `(T)(k + 1)`) wraps the
			// arithmetic in a cast; evaluate through it. The trip-count cap below
			// guards against an unmodelled wrap turning into a runaway unroll.
			return evalConst(((ExpressionCast) expr).getExpression(), loopVar, loopVal);
		}
		// anything else (function call, list, …) — try a pure-constant evaluation
		return builder.instantiator.evaluate(builder.entity, expr);
	}

	private static BigInteger asInt(Object value) {
		return value instanceof BigInteger ? (BigInteger) value : null;
	}

	/** Returns true if any assignment inside {@code body} targets {@code var}. */
	private static boolean bodyWritesVariable(EObject body, Variable var) {
		Iterator<EObject> it = EcoreUtil.getAllContents(body, true);
		while (it.hasNext()) {
			EObject obj = it.next();
			if (obj instanceof StatementAssign) {
				StatementAssign assign = (StatementAssign) obj;
				if (assign.getOp() != null && assign.getTarget() != null
						&& assign.getTarget().getSource() != null
						&& assign.getTarget().getSource().getVariable() == var) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public EObject caseStatementPrint(StatementPrint print) {
		hookBefore(print);

		int lineNumber = getStartLine(print);
		InstCall call = ir.createInstCall(lineNumber, null, null,
				builder.transformExpressions(print.getArgs()));
		call.setPrint(true);
		builder.add(call);

		return null;
	}

	@Override
	public EObject caseStatementReturn(StatementReturn stmtReturn) {
		hookBefore(stmtReturn);

		InstReturn instReturn = ir.createInstReturn(transformExpr(stmtReturn.getValue()));
		builder.add(instReturn);
		return instReturn;
	}

	@Override
	public EObject caseStatementVariable(StatementVariable stmtVar) {
		for (Variable variable : stmtVar.getVariables()) {
			doSwitch(variable);
		}

		return null;
	}

	@Override
	public EObject caseVariable(Variable variable) {
		if (CgUtil.isFunction(variable)) {
			// function
			builder.setProcedure(variable);
			Procedure procedure = builder.getProcedure();

			// Clear existing parameters/blocks to prevent duplication on re-transformation
			procedure.getParameters().clear();
			procedure.getBlocks().clear();

			// transform parameters
			for (Variable parameter : variable.getParameters()) {
				procedure.getParameters().add(builder.transformLocal(parameter));
			}

			// transform body
			doSwitch(variable.getBody());

			return procedure;
		} else {
			// local variable
			hookBefore(variable);

			// struct-typed local: flatten to N scalar field IR Vars (`p$lo`,
			// `p$hi`). A whole-struct initializer (`Pair p = other;`) lowers to
			// a field-wise copy (Tier 2.1) — see .claude/L1_STRUCT_DESIGN.md §2.
			Struct struct = IrBuilder.asStructType(variable);
			if (struct != null) {
				builder.transformStructLocal(variable, struct);
				CgExpression value = (CgExpression) variable.getValue();
				// `Pair q = port.read();` → field-wise load from the struct port
				// (checked first: handles cross-task multi-segment port refs).
				if (value instanceof ExpressionVariable) {
					java.util.Map<String, com.neosyn.models.dpn.Port> portFields =
							builder.getStructPortFields(((ExpressionVariable) value).getSource());
					if (portFields != null) {
						builder.loadStructFromPort(getStartLine(variable), variable, portFields,
								struct);
						return null;
					}
				}
				// `Pair q = other;` → field-wise copy.
				Variable srcStruct = IrBuilder.asWholeStructValue(value);
				if (srcStruct != null) {
					builder.copyStruct(getStartLine(variable), variable, srcStruct, struct);
				}
				return null;
			}

			// creates local variable and adds it to this procedure
			Var var = builder.transformLocal(variable);
			builder.getProcedure().getLocals().add(var);

			// assign a value (if any) to the variable
			CgExpression value = (CgExpression) variable.getValue();
			if (value instanceof ExpressionList) {
				// array-literal init `int<32> a[4] = {10, 20, 30, 40};` → one
				// indexed store per element.
				builder.storeArrayLiteral(var.getLineNumber(), var, (ExpressionList) value);
			} else if (value != null) {
				builder.storeExpr(var.getLineNumber(), var, null, value);
			}

			return null;
		}
	}

	@Override
	public EObject doSwitch(EObject eObject) {
		if (eObject == null) {
			return null;
		}
		return doSwitch(eObject.eClass(), eObject);
	}

	/**
	 * Hook called at the beginning of a statement/variable.
	 */
	protected void hookBefore(EObject eObject) {
		// only used by ActionTransformer
	}

	@Override
	public final Expression transformExpr(CgExpression expression) {
		return (Expression) doSwitch(expression);
	}

	/**
	 * Translates a call represented by the given expression to an IR InstCall.
	 * 
	 * @param expression
	 *            an expression variable referencing a function
	 * @return an ExprVar
	 */
	private Expression translateCall(ExpressionVariable expression) {
		Variable variable = expression.getSource().getVariable();

		// retrieve IR procedure
		Procedure proc = builder.getProcedure(variable);
		if (proc == null) {
			// The called function has no IR Procedure — typically because the
			// function body failed to compile (e.g. a validation error like
			// assigning to a const parameter), so it was never built. Surface a
			// useful error instead of NPE-ing on proc.getReturnType(). Mirrors
			// the unresolved-reference guard above.
			String refText = describeRef(expression);
			// Most common concrete cause: a value-returning function that isn't
			// declared `const` is never built, so the call has no body. Say so.
			if (variable != null && variable.eContainer() instanceof com.neosyn.cg.cg.VarDecl
					&& !CgUtil.isVoid(variable) && !CgUtil.isConstant(variable)) {
				throw new IllegalStateException("Call to value-returning function '" + refText
						+ "': a value-returning function must be declared 'const' "
						+ "(a non-const value-returning function has no compiled body)");
			}
			throw new IllegalStateException("Call to function '" + refText
					+ "' that has no compiled body — check the function for errors");
		}

		// transform parameters
		List<Expression> parameters = builder.transformExpressions(expression.getParameters());

		// add call
		int lineNumber = getStartLine(expression);
		// The result temp must NOT reuse the function's own name: the callee is
		// emitted as a Verilog `function` in the same module scope (directly for
		// same-entity functions, or via `include` for package functions like
		// MacPack.isEndOfFrame), so a local `reg isEndOfFrame` would shadow it
		// and the call `isEndOfFrame(...)` then fails iverilog elaboration with
		// "Object isEndOfFrame ... is not a function". Suffix the hint to avoid
		// the collision (createLocal still uniquifies against other locals).
		Var target = builder.createLocal(lineNumber, proc.getReturnType(), variable.getName() + "_ret");
		InstCall call = ir.createInstCall(lineNumber, target, proc, parameters);
		builder.add(call);

		// return expr
		return ir.createExprVar(target);
	}

	/**
	 * Joins the segment names of a VarRef expression into a dotted string for
	 * use in error messages. Avoids relying on Variable.getName() (which is
	 * null when the cross-ref is unresolved).
	 */
	private static String describeRef(ExpressionVariable expression) {
		StringBuilder sb = new StringBuilder();
		for (com.neosyn.cg.cg.Named n : expression.getSource().getObjects()) {
			if (sb.length() > 0) sb.append('.');
			String name = n != null ? n.getName() : null;
			sb.append(name != null ? name : "?");
		}
		return sb.toString();
	}

}
