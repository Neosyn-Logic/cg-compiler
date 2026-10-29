/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstCall;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.ExpressionPrinter;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.EcoreHelper;

/**
 * The text of an {@code assert} as the user wrote it, and where it is -- shared by the three back ends so a
 * failing check reads the same in the simulator, Verilog and VHDL (TRAPS T64, T175).
 */
public final class AssertText {

	private AssertText() {
	}

	/**
	 * The text of an assert's expression or operand as the user wrote it, where the IR differs: an
	 * array element read is a temporary loaded just before the check (`E[i]` becomes `E_0`), so the
	 * message named `E_0` whatever element failed (TRAPS T64). A temporary defined by one indexed load
	 * prints as that load, `E[i]`; a comparison prints its two operands that way.
	 */
	public static String text(Expression expr) {
		return text(expr, 0);
	}

	private static String text(Expression expr, int depth) {
		if (depth > 8) {
			return new ExpressionPrinter().toString(expr);
		}
		if (expr.isExprBinary()) {
			ExprBinary bin = (ExprBinary) expr;
			return operand(bin.getE1(), depth) + " " + bin.getOp().getText() + " " + operand(bin.getE2(), depth);
		}
		if (expr instanceof ExprUnary) {
			ExprUnary un = (ExprUnary) expr;
			return un.getOp().getText() + operand(un.getExpr(), depth);
		}
		// width conversions the IR adds around an index or an operand: not in the source
		if (expr instanceof ExprResize) {
			return text(((ExprResize) expr).getExpr(), depth + 1);
		}
		if (expr instanceof ExprTypeConv) {
			return text(((ExprTypeConv) expr).getExpr(), depth + 1);
		}
		if (expr instanceof ExprVar && ((ExprVar) expr).getUse() != null) {
			Var var = ((ExprVar) expr).getUse().getVariable();
			if (var != null && var.isLocal() && var.getDefs().size() == 1) {
				EObject def = var.getDefs().get(0).eContainer();
				if (def instanceof InstLoad) {
					InstLoad load = (InstLoad) def;
					if (load.getSource() != null && load.getSource().getVariable() != null
							&& (!load.getIndexes().isEmpty()
									|| isCompilerCopy(var, load.getSource().getVariable()))) {
						// `E_0` prints as `E[i]`; `i_2`, the compiler's copy of `i`, as `i`. A local the USER
						// named (`u8 v = dout.read();`) keeps its name.
						StringBuilder sb = new StringBuilder(load.getSource().getVariable().getName());
						for (Expression index : load.getIndexes()) {
							sb.append('[').append(text(index, depth + 1)).append(']');
						}
						return sb.toString();
					}
				} else if (def instanceof InstAssign) {
					// A COPY made by the compiler (`i_1 = $unsigned(i)`) prints as what it copies. A computed
					// temporary keeps its name: the IR cannot tell it from a variable the user named.
					Expression value = ((InstAssign) def).getValue();
					Expression bare = value;
					while (bare instanceof ExprResize || bare instanceof ExprTypeConv) {
						bare = bare instanceof ExprResize ? ((ExprResize) bare).getExpr() : ((ExprTypeConv) bare).getExpr();
					}
					if (bare instanceof ExprVar && ((ExprVar) bare).getUse() != null
							&& isCompilerCopy(var, ((ExprVar) bare).getUse().getVariable())) {
						return text(bare, depth + 1);
					}
				}
			}
		}
		return new ExpressionPrinter().toString(expr);
	}

	/**
	 * The compiler names its copy of a variable `<name>_<n>` (`i_1`, `i_2`); a local the user declared keeps
	 * the user's name. Only a copy is printed as what it copies.
	 */
	private static boolean isCompilerCopy(Var local, Var source) {
		return source != null && local.getName() != null && source.getName() != null
				&& local.getName().matches(java.util.regex.Pattern.quote(source.getName()) + "_[0-9]+");
	}

	/** An operand of a binary or unary operator: parenthesised when it is itself an operation. */
	private static String operand(Expression e, int depth) {
		String t = text(e, depth + 1);
		Expression bare = e;
		while (bare instanceof ExprResize || bare instanceof ExprTypeConv) {
			bare = bare instanceof ExprResize ? ((ExprResize) bare).getExpr() : ((ExprTypeConv) bare).getExpr();
		}
		return bare.isExprBinary() ? "(" + t + ")" : t;
	}

	/** {@code <File>.cg:<line> in <task>: }, leaving out whatever the IR does not know. */
	public static String where(InstCall call) {
		Entity entity = EcoreHelper.getContainerOfType(call, Entity.class);
		if (entity == null) {
			return "";
		}
		StringBuilder where = new StringBuilder();
		String file = entity.getFileName();
		if (file != null && !file.isEmpty()) {
			where.append(file.substring(Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1));
			if (call.getLineNumber() > 0) {
				where.append(':').append(call.getLineNumber());
			}
			where.append(' ');
		}
		return where.append("in ").append(entity.getSimpleName()).append(": ").toString();
	}

	/** An integer comparison, whose two operand values a failure message can print. */
	public static boolean isIntComparison(Expression expr) {
		if (!expr.isExprBinary() || !((ExprBinary) expr).getOp().isComparison()) {
			return false;
		}
		ExprBinary cmp = (ExprBinary) expr;
		Type left = TypeUtil.getType(cmp.getE1());
		Type right = TypeUtil.getType(cmp.getE2());
		return left != null && right != null && left.isInt() && right.isInt();
	}

}
