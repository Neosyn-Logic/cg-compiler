/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.dpn.util.DpnSwitch;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprBool;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.OpUnary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.Void;

/**
 * This class defines a transformation that replaces input patterns with tests on ports' additional
 * signals in schedulers, and output patterns with assignments to ports' additional signals.
 *
 */
public class PatternImplementation extends DpnSwitch<Void> {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	@Override
	public Void caseActor(Actor actor) {
		// Snapshot each action's original scheduler expression (fully inlined)
		// before mutating any scheduler. Used below to restore complementarity
		// between actions whose original schedulers were negations of each other.
		Map<Action, Expression> originalSchedulers = new LinkedHashMap<>();
		for (Action action : actor.getActions()) {
			Expression flat = inlineScheduler(action.getScheduler());
			if (flat != null) {
				originalSchedulers.put(action, flat);
			}
		}

		for (Action action : actor.getActions()) {
			updateScheduler(action.getScheduler(), action.getInputPattern().getPorts());
		}

		// Map each action to the FSM transition it labels, so the complement
		// rewrite below can tell a same-state if/else partition from a
		// port-gated wait loop (see the loop-exit guard below).
		Map<Action, Transition> actionToTransition = new HashMap<>();
		if (actor.getFsm() != null) {
			for (Transition t : actor.getFsm().getTransitions()) {
				if (t.getAction() != null) {
					actionToTransition.put(t.getAction(), t);
				}
			}
		}

		// If action B's original scheduler was !A.original (where A is another
		// action in the same actor), then B is the else-branch of A. Adding
		// port-validity checks to A above can leave a region uncovered
		// (A.cond false but B.cond=!A.original also false), so rewrite B.cond
		// to be !A.new to preserve the partition.
		for (Action b : actor.getActions()) {
			Expression bOrig = originalSchedulers.get(b);
			if (!(bOrig instanceof ExprUnary)) {
				continue;
			}
			ExprUnary unary = (ExprUnary) bOrig;
			if (unary.getOp() != OpUnary.LOGIC_NOT) {
				continue;
			}
			Expression inner = unary.getExpr();
			for (Action a : actor.getActions()) {
				if (a == b) {
					continue;
				}
				Expression aOrig = originalSchedulers.get(a);
				if (aOrig != null && exprEquals(inner, aOrig)) {
					// A port-gated counted loop lowers to a body edge A that
					// self-loops (re-tests the same data condition each cycle,
					// gated on a timing port it reads) and an exit edge B that
					// leaves to a different state. Here !A.new = !(port_valid &&
					// cond) would make the loop EXIT on every cycle the port is
					// not valid — collapsing the legitimate "wait for the next
					// tick" stall. Keep B's guard as its own loop-termination
					// condition (!cond, i.e. j==N). A same-state if/else (HalfRF:
					// both A and B re-enter the SAME loop state) is NOT a wait
					// loop — there the partition must cover every input, so the
					// rewrite still applies.
					Transition ta = actionToTransition.get(a);
					Transition tb = actionToTransition.get(b);
					if (ta != null && tb != null && ta.getSource() == ta.getTarget()
							&& tb.getTarget() != ta.getSource()) {
						break;
					}
					InstReturn bRet = getReturn(b.getScheduler());
					// Re-inline A's updated scheduler so the replacement refers to
					// externally-visible port validity Vars (not A's local `cond`).
					Expression aNewInlined = inlineScheduler(a.getScheduler());
					if (bRet != null && aNewInlined != null) {
						// Preserve any port-validity gating derived from B's own
						// inputPattern (added by updateScheduler above). Without this
						// prefix, B would fire when ports it reads aren't valid — e.g.
						// an action that reads `dest` unconditionally would lose its
						// dest_valid gate when we replace its return with !A.new.
						Expression bPortValids = buildPortValidsExpr(b.getInputPattern().getPorts());
						Expression notANew = ir.createExprUnary(OpUnary.LOGIC_NOT, aNewInlined);
						Expression newReturn = bPortValids == null
								? notANew
								: ir.createExprBinary(bPortValids, OpBinary.LOGIC_AND, notANew);
						bRet.setValue(newReturn);
					}
					break;
				}
			}
		}

		return DONE;
	}

	private void updateScheduler(Procedure scheduler, List<Port> ports) {
		InstReturn ret = getReturn(scheduler);
		if (ret == null) {
			return;
		}
		Expression expr = buildPortValidsExpr(ports);
		if (expr != null) {
			if (EcoreUtil.equals(ir.createExprBool(true), ret.getValue())) {
				ret.setValue(expr);
			} else {
				ret.setValue(ir.createExprBinary(expr, OpBinary.LOGIC_AND, ret.getValue()));
			}
		}
	}

	/**
	 * AND-combine every additional input (validity signal) of the given ports.
	 * Returns null if no ports contribute any signal.
	 */
	private Expression buildPortValidsExpr(List<Port> ports) {
		Expression expr = null;
		for (Port port : ports) {
			for (Var signal : port.getAdditionalInputs()) {
				if (expr == null) {
					expr = ir.createExprVar(signal);
				} else {
					expr = ir.createExprBinary(expr, OpBinary.LOGIC_AND, ir.createExprVar(signal));
				}
			}
		}
		return expr;
	}

	private InstReturn getReturn(Procedure scheduler) {
		if (scheduler == null || scheduler.getLast() == null
				|| scheduler.getLast().getInstructions().isEmpty()) {
			return null;
		}
		Instruction last = scheduler.getLast().lastListIterator().previous();
		return last instanceof InstReturn ? (InstReturn) last : null;
	}

	private Expression getReturnValue(Procedure scheduler) {
		InstReturn ret = getReturn(scheduler);
		return ret == null ? null : ret.getValue();
	}

	/**
	 * Returns the fully inlined return expression of a scheduler procedure, with
	 * InstLoads/InstAssigns substituted into the InstReturn value. Returns null
	 * when the procedure has no InstReturn or the structure cannot be inlined.
	 * The returned expression is freshly constructed and safe to mutate; any
	 * ExprVar leaves reference Vars outside the scheduler (e.g. port validity
	 * signals) so the caller can identify which port they came from.
	 */
	private Expression inlineScheduler(Procedure scheduler) {
		if (scheduler == null || scheduler.getBlocks() == null) {
			return null;
		}
		Map<Var, Expression> env = new HashMap<>();
		Expression retValue = null;
		for (Block block : scheduler.getBlocks()) {
			if (!(block instanceof BlockBasic)) {
				continue;
			}
			for (Instruction inst : ((BlockBasic) block).getInstructions()) {
				if (inst instanceof InstLoad) {
					InstLoad load = (InstLoad) inst;
					if (load.getIndexes().isEmpty()) {
						Var target = load.getTarget().getVariable();
						Var source = load.getSource().getVariable();
						env.put(target, ir.createExprVar(source));
					}
				} else if (inst instanceof InstAssign) {
					InstAssign assign = (InstAssign) inst;
					Expression value = substitute(assign.getValue(), env);
					env.put(assign.getTarget().getVariable(), value);
				} else if (inst instanceof InstReturn) {
					Expression value = ((InstReturn) inst).getValue();
					if (value != null) {
						retValue = substitute(value, env);
					}
				}
			}
		}
		return retValue;
	}

	/**
	 * Build a fresh expression tree from <code>expr</code>, replacing each
	 * ExprVar whose Var is a key in <code>env</code> with a clone of the
	 * corresponding mapped expression. The input is never mutated.
	 */
	private Expression substitute(Expression expr, Map<Var, Expression> env) {
		if (expr == null) {
			return null;
		}
		if (expr instanceof ExprVar) {
			ExprVar ev = (ExprVar) expr;
			Var var = ev.getUse() == null ? null : ev.getUse().getVariable();
			if (var != null) {
				Expression replacement = env.get(var);
				if (replacement != null) {
					return cloneExpr(replacement);
				}
			}
			return cloneExpr(expr);
		}
		if (expr instanceof ExprUnary) {
			ExprUnary u = (ExprUnary) expr;
			return ir.createExprUnary(u.getOp(), substitute(u.getExpr(), env));
		}
		if (expr instanceof ExprBinary) {
			ExprBinary b = (ExprBinary) expr;
			return ir.createExprBinary(substitute(b.getE1(), env), b.getOp(),
					substitute(b.getE2(), env));
		}
		if (expr instanceof ExprTypeConv) {
			ExprTypeConv t = (ExprTypeConv) expr;
			ExprTypeConv r = ir.createExprTypeConv();
			r.setTypeName(t.getTypeName());
			r.setExpr(substitute(t.getExpr(), env));
			return r;
		}
		if (expr instanceof ExprResize) {
			ExprResize rs = (ExprResize) expr;
			ExprResize r = ir.createExprResize();
			r.setTargetSize(rs.getTargetSize());
			r.setExpr(substitute(rs.getExpr(), env));
			return r;
		}
		return cloneExpr(expr);
	}

	/**
	 * Deep clone that preserves cross-references to external Vars (unlike
	 * EcoreUtil.copy, which nulls out non-containment refs to objects outside
	 * the copied subtree).
	 */
	private Expression cloneExpr(Expression expr) {
		if (expr == null) {
			return null;
		}
		if (expr instanceof ExprVar) {
			ExprVar ev = (ExprVar) expr;
			Var var = ev.getUse() == null ? null : ev.getUse().getVariable();
			return var == null ? ir.createExprVar() : ir.createExprVar(var);
		}
		if (expr instanceof ExprUnary) {
			ExprUnary u = (ExprUnary) expr;
			return ir.createExprUnary(u.getOp(), cloneExpr(u.getExpr()));
		}
		if (expr instanceof ExprBinary) {
			ExprBinary b = (ExprBinary) expr;
			return ir.createExprBinary(cloneExpr(b.getE1()), b.getOp(), cloneExpr(b.getE2()));
		}
		if (expr instanceof ExprBool) {
			return ir.createExprBool(((ExprBool) expr).isValue());
		}
		if (expr instanceof ExprInt) {
			ExprInt i = (ExprInt) expr;
			return ir.createExprInt(i.getValue());
		}
		if (expr instanceof ExprTypeConv) {
			ExprTypeConv t = (ExprTypeConv) expr;
			ExprTypeConv r = ir.createExprTypeConv();
			r.setTypeName(t.getTypeName());
			r.setExpr(cloneExpr(t.getExpr()));
			return r;
		}
		if (expr instanceof ExprResize) {
			ExprResize rs = (ExprResize) expr;
			ExprResize r = ir.createExprResize();
			r.setTargetSize(rs.getTargetSize());
			r.setExpr(cloneExpr(rs.getExpr()));
			return r;
		}
		// Fallback for expression types we don't clone explicitly: return as-is.
		// Structural equality comparison elsewhere tolerates this since these
		// shapes shouldn't appear as scheduler return values in practice.
		return expr;
	}

	/**
	 * Structural equality for scheduler expressions that tolerates cross-refs
	 * to external Vars (compares Var identity for ExprVar leaves).
	 */
	private boolean exprEquals(Expression a, Expression b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a.getClass() != b.getClass()) {
			return false;
		}
		if (a instanceof ExprVar) {
			Var va = ((ExprVar) a).getUse() == null ? null : ((ExprVar) a).getUse().getVariable();
			Var vb = ((ExprVar) b).getUse() == null ? null : ((ExprVar) b).getUse().getVariable();
			return va == vb;
		}
		if (a instanceof ExprUnary) {
			ExprUnary ua = (ExprUnary) a;
			ExprUnary ub = (ExprUnary) b;
			return ua.getOp() == ub.getOp() && exprEquals(ua.getExpr(), ub.getExpr());
		}
		if (a instanceof ExprBinary) {
			ExprBinary ba = (ExprBinary) a;
			ExprBinary bb = (ExprBinary) b;
			return ba.getOp() == bb.getOp()
					&& exprEquals(ba.getE1(), bb.getE1())
					&& exprEquals(ba.getE2(), bb.getE2());
		}
		if (a instanceof ExprBool) {
			return ((ExprBool) a).isValue() == ((ExprBool) b).isValue();
		}
		if (a instanceof ExprInt) {
			return ((ExprInt) a).getValue().equals(((ExprInt) b).getValue());
		}
		return EcoreUtil.equals(a, b);
	}

}
