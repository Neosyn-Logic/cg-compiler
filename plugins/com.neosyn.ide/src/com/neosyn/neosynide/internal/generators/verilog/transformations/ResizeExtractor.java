/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.math.BigInteger;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.State;
import org.eclipse.emf.ecore.EObject;

import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractExpressionTransformer;
import com.neosyn.models.ir.transform.UniqueNameComputer;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.EcoreHelper;

/**
 * Hoists {@link ExprResize} nodes out of larger expressions into their own temporary
 * register assignments, so the emitted Verilog resizes a named value once instead of
 * nesting bit-slices inside a bigger expression — which reads better and avoids lint
 * noise. Each temporary is named after the value it holds (see {@link #nameFor}).
 */
public class ResizeExtractor extends AbstractExpressionTransformer {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	private UniqueNameComputer nameComputer;

	/** Longest name spelled from an expression before falling back to the variable it feeds. */
	private static final int MAX_NAME = 24;

	@Override
	public Expression caseExprResize(ExprResize resize) {
		resize = (ExprResize) super.caseExprResize(resize);

		Expression expr = resize.getExpr();
		Type type = TypeUtil.getType(expr);
		if (!type.isInt()) {
			return resize;
		}

		TypeInt ti = (TypeInt) type;

		int targetSize = resize.getTargetSize();
		int sourceSize = ti.getSize();

		// (d) Drop a redundant low-bits mask we are about to truncate away:
		// trunc(N, x & C) == trunc(N, x) when C's low N bits are all 1, since
		// the truncation keeps only those low N bits anyway. Removes the
		// `& 0xFF`-style mask the cast pipeline emits before a narrowing cast.
		if (targetSize < sourceSize) {
			Expression unmasked = stripRedundantLowMask(expr, targetSize);
			if (unmasked != expr) {
				expr = unmasked;
				type = TypeUtil.getType(expr);
				if (!type.isInt()) {
					return resize;
				}
				ti = (TypeInt) type;
				sourceSize = ti.getSize();
			}
		}

		if (sourceSize < targetSize) {
			return extract(ti.isSigned() ? "sext" : "zext", ti, targetSize, expr);
		} else if (targetSize < sourceSize) {
			if (assignmentTruncates(resize, targetSize, expr)) {
				return expr;
			}
			return extract("trunc", ti, targetSize, expr);
		} else {
			// unnecessary resize, ignore it and replace by expr
			return expr;
		}
	}

	/**
	 * True when the truncation can be left to the assignment itself: `i <= i + 4'h1;` instead of
	 * `i_plus_1 = (i + 4'h1); i <= i_plus_1[3 : 0];` in its own named block (TRAPS T116, ~1500
	 * sites). Exact only when all of these hold:
	 * <ul>
	 * <li>the truncation is the whole value of an assignment whose target is exactly
	 * {@code targetSize} wide -- the assignment keeps the same low bits;</li>
	 * <li>the expression uses only {@code + - *}, bitwise {@code & | ^ ~} and unary minus: the low N
	 * bits of those depend only on the low N bits of their operands, so evaluating at the target's
	 * width (Verilog sizes an expression by its context) gives the bits the wider temporary held.
	 * NOT a right shift, a division or a remainder, whose operand's upper bits reach the low ones;
	 * a comparison is a leaf -- its operands are sized among themselves, not by the context;</li>
	 * <li>no leaf is wider than the target, so nothing is truncated implicitly and no width lint
	 * is added (the temporary's own `tmp9 = a8 + b8` WIDTHEXPAND goes away).</li>
	 * </ul>
	 */
	private boolean assignmentTruncates(ExprResize resize, int targetSize, Expression expr) {
		EObject cter = resize.eContainer();
		Var target;
		if (cter instanceof InstAssign && ((InstAssign) cter).getValue() == resize) {
			target = ((InstAssign) cter).getTarget().getVariable();
		} else if (cter instanceof InstStore && ((InstStore) cter).getValue() == resize) {
			target = ((InstStore) cter).getTarget().getVariable();
		} else {
			return false;
		}
		Type type = target.getType().isArray() ? ((com.neosyn.models.ir.TypeArray) target.getType()).getElementType()
				: target.getType();
		return type.isInt() && ((TypeInt) type).getSize() == targetSize && isModular(expr, targetSize, 0);
	}

	private boolean isModular(Expression expr, int width, int depth) {
		if (depth > 32) {
			return false;
		}
		if (expr instanceof ExprBinary) {
			ExprBinary bin = (ExprBinary) expr;
			switch (bin.getOp()) {
			case PLUS: case MINUS: case TIMES: case BITAND: case BITOR: case BITXOR:
				return isModular(bin.getE1(), width, depth + 1) && isModular(bin.getE2(), width, depth + 1);
			default:
				return bin.getOp().isComparison() && fits(bin, 1);
			}
		}
		if (expr instanceof ExprUnary) {
			ExprUnary un = (ExprUnary) expr;
			return (un.getOp() == OpUnary.BITNOT || un.getOp() == OpUnary.MINUS) && isModular(un.getExpr(), width, depth + 1);
		}
		if (expr instanceof ExprVar || expr instanceof ExprInt || expr instanceof ExprTypeConv) {
			return fits(expr, width);
		}
		return false;
	}

	/** The leaf is at most {@code width} bits. */
	private static boolean fits(Expression expr, int width) {
		Type type = TypeUtil.getType(expr);
		return type != null && (type.isBool() || (type.isInt() && ((TypeInt) type).getSize() <= width));
	}

	/**
	 * If {@code expr} is a bitwise-AND with a constant whose low {@code keptBits}
	 * bits are all 1, return the other (non-constant) operand — the mask cannot
	 * affect a value that is about to be truncated to {@code keptBits} bits.
	 * Otherwise returns {@code expr} unchanged.
	 */
	private Expression stripRedundantLowMask(Expression expr, int keptBits) {
		if (!(expr instanceof ExprBinary)) {
			return expr;
		}
		ExprBinary bin = (ExprBinary) expr;
		if (bin.getOp() != OpBinary.BITAND) {
			return expr;
		}
		if (isLowBitsAllSet(bin.getE2(), keptBits)) {
			return bin.getE1();
		}
		if (isLowBitsAllSet(bin.getE1(), keptBits)) {
			return bin.getE2();
		}
		return expr;
	}

	/** True if {@code e} is a non-negative int literal with its low {@code n} bits all set. */
	private boolean isLowBitsAllSet(Expression e, int n) {
		if (!(e instanceof ExprInt)) {
			return false;
		}
		BigInteger value = ((ExprInt) e).getValue();
		if (value.signum() < 0) {
			// Negative literal (e.g. a sign-extended all-ones mask) — be
			// conservative and leave it alone.
			return false;
		}
		BigInteger lowMask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
		return value.and(lowMask).equals(lowMask);
	}

	/**
	 * A name that says what the temporary HOLDS: {@code i + 1} is {@code i_plus_1}, {@code srcIp >> 16}
	 * is {@code srcIp_shr_16}, {@code (ttl - 1) << 8} is {@code ttl_minus_1_shl_8}. An expression too
	 * involved to spell in {@value #MAX_NAME} characters takes the name of the variable it is
	 * assigned to plus {@code _value} ({@code header_value}), failing that {@code value}.
	 *
	 * <p>Decision S186: temporaries are named after the value they hold. They used to be named after
	 * constellations ({@code orion}, {@code lyra}, ...) -- 5911 declarations across the IP cores that
	 * told a reader nothing.
	 */
	private static String nameFor(Expression expr, Instruction inst) {
		String spelled = spell(expr, 3);
		if (spelled != null && spelled.length() <= MAX_NAME) {
			// a bare constant spells as a number, and an identifier cannot start with a digit
			return identifier(spelled);
		}
		String target = null;
		if (inst instanceof InstStore) {
			target = ((InstStore) inst).getTarget().getVariable().getName();
		} else if (inst instanceof InstAssign) {
			target = ((InstAssign) inst).getTarget().getVariable().getName();
		}
		return target != null ? identifier(target + "_value") : "value";
	}

	/** Spells {@code e} as an identifier, or null when it cannot (too deep, or an unhandled form). */
	private static String spell(Expression e, int depth) {
		if (e instanceof ExprVar) {
			Var v = ((ExprVar) e).getUse().getVariable();
			return v == null || v.getName() == null ? null : identifier(v.getName());
		}
		if (e instanceof ExprInt) {
			BigInteger n = ((ExprInt) e).getValue();
			return n.signum() < 0 ? "neg" + n.negate() : n.toString();
		}
		if (depth <= 0) {
			return null;
		}
		if (e instanceof ExprResize) {
			return spell(((ExprResize) e).getExpr(), depth);
		}
		if (e instanceof ExprTypeConv) {
			return spell(((ExprTypeConv) e).getExpr(), depth);
		}
		if (e instanceof ExprUnary) {
			ExprUnary u = (ExprUnary) e;
			String x = spell(u.getExpr(), depth - 1);
			String op = u.getOp() == OpUnary.MINUS ? "neg" : u.getOp() == OpUnary.BITNOT ? "not" : "lnot";
			return x == null ? null : identifier(op + "_" + x);
		}
		if (e instanceof ExprBinary) {
			ExprBinary b = (ExprBinary) e;
			String op = word(b.getOp());
			Expression left = b.getE1();
			Expression right = b.getE2();
			// a constant reads better last when the order does not matter: `length_plus_20`
			if (commutative(b.getOp()) && left instanceof ExprInt && !(right instanceof ExprInt)) {
				Expression t = left;
				left = right;
				right = t;
			}
			String l = spell(left, depth - 1);
			String r = spell(right, depth - 1);
			return op == null || l == null || r == null ? null : identifier(l + "_" + op + "_" + r);
		}
		return null;
	}

	private static String word(OpBinary op) {
		switch (op) {
		case PLUS: return "plus";
		case MINUS: return "minus";
		case TIMES: return "times";
		case DIV: case DIV_INT: return "div";
		case MOD: return "mod";
		case SHIFT_LEFT: return "shl";
		case SHIFT_RIGHT: return "shr";
		case BITAND: return "and";
		case BITOR: return "or";
		case BITXOR: return "xor";
		case LOGIC_AND: return "land";
		case LOGIC_OR: return "lor";
		case EQ: return "eq";
		case NE: return "ne";
		case LT: return "lt";
		case LE: return "le";
		case GT: return "gt";
		case GE: return "ge";
		default: return null;
		}
	}

	private static boolean commutative(OpBinary op) {
		switch (op) {
		case PLUS: case TIMES: case BITAND: case BITOR: case BITXOR: case LOGIC_AND: case LOGIC_OR:
		case EQ: case NE:
			return true;
		default:
			return false;
		}
	}

	/** A Verilog identifier cannot start with a digit. */
	private static String identifier(String name) {
		return !name.isEmpty() && Character.isDigit(name.charAt(0)) ? "n_" + name : name;
	}

	private Expression extract(String operation, TypeInt source, int targetSize, Expression expr) {
		if (!expr.isExprVar()) {
			// must extract expr into a holding variable before resizing it
			Type type = ir.createTypeInt(source.getSize(), source.isSigned());
			Instruction inst = EcoreHelper.getContainerOfType(expr, Instruction.class);
			String name = nameComputer.getUniqueName(nameFor(expr, inst));
			Var resizeTmp = ir.newTempLocalVariable(getProcedure(), type, name);

			if (inst == null) {
				Block block = EcoreHelper.getContainerOfType(expr, Block.class);
				List<Block> blocks = EcoreHelper.getContainingList(block);
				BlockBasic blockBasic = ir.createBlockBasic();
				blocks.add(blocks.indexOf(block), blockBasic);
				blockBasic.add(ir.createInstAssign(resizeTmp, expr));
			} else {
				BlockBasic block = (BlockBasic) inst.eContainer();
				block.add(block.indexOf(inst), ir.createInstAssign(resizeTmp, expr));
			}

			expr = ir.createExprVar(resizeTmp);
		}

		ExprTypeConv truncated = ir.convert(operation + "." + targetSize, expr);
		truncated.setComputedType(ir.createTypeInt(targetSize, source.isSigned()));
		return truncated;
	}

	@Override
	public void setProcedure(Procedure procedure) {
		if (nameComputer == null) {
			// Every name the module can refer to, not just its state variables: a temporary is
			// declared inside a named block, where it SHADOWS any outer name spelled the same.
			// Measured: `$signed(b)` spelled `b` shadowed the port `b` in Divide, and four IP
			// gates failed. Ports and their valid/ready, every procedure's locals and parameters,
			// function names, FSM states.
			Entity entity = EcoreHelper.getContainerOfType(procedure, Entity.class);
			Set<String> taken = new HashSet<>();
			for (Iterator<EObject> it = entity.eAllContents(); it.hasNext();) {
				EObject o = it.next();
				if (o instanceof Var) {
					taken.add(((Var) o).getName());
				} else if (o instanceof Procedure) {
					taken.add(((Procedure) o).getName());
				} else if (o instanceof State) {
					taken.add(((State) o).getName());
				}
			}
			taken.remove(null);
			nameComputer = new UniqueNameComputer(taken);
		}

		super.setProcedure(procedure);
	}

}
