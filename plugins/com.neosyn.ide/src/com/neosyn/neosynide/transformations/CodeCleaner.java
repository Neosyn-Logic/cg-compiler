/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstPhi;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.ir.transform.PhiRemoval;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * An IR clean-up pass run to a fixpoint over each procedure: it inlines single-use
 * assignments and scalar loads into their uses (folding boolean guards as it goes via
 * {@link ConstantFolder}), drops instructions whose target is then unused, and removes
 * emptied blocks and dead locals. Shrinks the IR before HDL/bytecode emission.
 */
public class CodeCleaner extends AbstractIrVisitor {

	private boolean changed;

	@Override
	public Void caseBlockBasic(BlockBasic block) {
		visit(block.getInstructions());

		if (block.getInstructions().isEmpty()) {
			delete(block);
		}
		return DONE;
	}

	@Override
	public Void caseBlockIf(BlockIf blockIf) {
		doSwitch(blockIf.getJoinBlock());
		visit(blockIf.getThenBlocks());
		visit(blockIf.getElseBlocks());

		Expression condition = blockIf.getCondition();
		if (ConstantFolder.isTrue(condition) || ConstantFolder.isFalse(condition)) {
			foldConstantIf(blockIf, ConstantFolder.isTrue(condition));
		} else if (blockIf.getThenBlocks().isEmpty() && blockIf.getElseBlocks().isEmpty()
				&& blockIf.getJoinBlock() == null) {
			delete(blockIf);
		}

		return DONE;
	}

	/**
	 * Replaces an if whose condition folded to a constant by the branch that runs (TRAPS T116:
	 * the HDL printed `if (1'b1)` / `if (to_boolean('1'))` around it, and the branch that can
	 * never run below it). Each join phi becomes an assignment of the taken branch's value --
	 * the assignment {@link PhiRemoval} would have made -- and the other branch is deleted with
	 * the condition, updating def/use chains.
	 */
	private void foldConstantIf(BlockIf blockIf, boolean taken) {
		int index = taken ? 0 : 1;
		List<Block> replacement = new ArrayList<>(taken ? blockIf.getThenBlocks() : blockIf.getElseBlocks());
		BlockBasic join = blockIf.getJoinBlock();
		if (join != null) {
			for (Instruction inst : new ArrayList<>(join.getInstructions())) {
				if (inst.isInstPhi()) {
					InstPhi phi = (InstPhi) inst;
					InstAssign assign = IrFactory.eINSTANCE.createInstAssign(phi.getTarget().getVariable(),
							PhiRemoval.valueOf(phi, index));
					join.getInstructions().add(join.indexOf(phi), assign);
					delete(phi);
				}
			}
			replacement.add(join);
		}

		List<Block> blocks = EcoreHelper.getContainingList(blockIf);
		blocks.addAll(blocks.indexOf(blockIf) + 1, replacement);
		delete(blockIf);
	}

	@Override
	public Void caseBlockWhile(BlockWhile blockWhile) {
		BlockBasic join = blockWhile.getJoinBlock();
		visit(blockWhile.getBlocks());
		doSwitch(join);

		if (blockWhile.getBlocks().isEmpty()) {
			// if the join's phi target are all used only within this while, delete it
			for (Instruction inst : join.getInstructions()) {
				if (inst.isInstPhi()) {
					InstPhi phi = (InstPhi) inst;
					for (Use use : phi.getTarget().getVariable().getUses()) {
						if (!EcoreUtil.isAncestor(blockWhile, use)) {
							return DONE;
						}
					}
				}
			}

			delete(blockWhile);
		}

		return DONE;
	}

	@Override
	public Void caseInstAssign(InstAssign assign) {
		Var target = assign.getTarget().getVariable();
		Expression value = assign.getValue();
		List<Use> uses = target.getUses();
		if (uses.size() >= 2 && value.isExprBinary()) {
			// do not expand arithmetic expressions used in at least 2 places
			return DONE;
		}
		if (!uses.isEmpty() && tooDeep(value, uses)) {
			// Keep this assignment as a named temporary: inlining it would nest the use deeper
			// than MAX_DEPTH. An unrolled 1024-step accumulation (`s = s + i`) otherwise became ONE
			// expression 1024 deep, and the recursive passes after this one overflowed the stack
			// -- VHDL from 768 steps, Verilog at 1024 (TRAPS T125). A temporary every MAX_DEPTH
			// levels is also what a person would write.
			return DONE;
		}

		if (!uses.isEmpty()) {
			for (Use use : uses) {
				Expression expr = IrUtil.copy(value);
				EcoreUtil.replace(use.eContainer(), expr);
				tryFoldExpression(expr);
			}
			target.getUses().clear();
		}

		// nobody uses the target variable after this instruction/block
		// we can safely remove
		clean(target, assign);

		return DONE;
	}

	@Override
	public Void caseInstLoad(InstLoad load) {
		Var target = load.getTarget().getVariable();
		if (!target.getType().isArray() && load.getIndexes().isEmpty()) {
			// only replace if load of scalar
			Var source = load.getSource().getVariable();
			for (Use use : target.getUses()) {
				Expression expr = IrFactory.eINSTANCE.createExprVar(source);
				EcoreUtil.replace(use.eContainer(), expr);
			}
			target.getUses().clear();
		}

		if (target.getUses().isEmpty()) {
			// nobody uses the target variable after this instruction/block
			// we can safely remove
			clean(target, load);
		}

		return DONE;
	}

	@Override
	public Void caseInstPhi(InstPhi phi) {
		Var target = phi.getTarget().getVariable();
		if (!target.isUsed()) {
			clean(target, phi);
		}

		return DONE;
	}

	@Override
	public Void caseInstStore(InstStore store) {
		return DONE;
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		changed = true;
		while (changed) {
			changed = false;
			visit(procedure.getBlocks());
		}

		// last: removes locals not used by any instruction
		Iterator<Var> it = procedure.getLocals().iterator();
		while (it.hasNext()) {
			Var local = it.next();
			if (!local.isDefined() && !local.isUsed()) {
				it.remove();
			}
		}

		return DONE;
	}

	/**
	 * How deep an inlined expression may nest. Well below where the recursive expression passes
	 * ran out of stack (VHDL from about 768 levels), and above anything the example designs
	 * produce: at 64 the sold RISC-V core's HDL changed (a temporary kept, every later name
	 * renumbered); at 128 every example is byte-identical. Measured 2026-09-29.
	 */
	private static final int MAX_DEPTH = 128;

	/** Would inlining {@code value} at any of {@code uses} nest an expression deeper than MAX_DEPTH? */
	private static boolean tooDeep(Expression value, List<Use> uses) {
		int depth = depth(value);
		for (Use use : uses) {
			int nesting = 0;
			for (EObject c = use.eContainer().eContainer(); c instanceof Expression; c = c.eContainer()) {
				nesting++;
			}
			if (nesting + depth > MAX_DEPTH) {
				return true;
			}
		}
		return false;
	}

	/** Levels of expression nesting in {@code expr}; a leaf is 1. */
	private static int depth(Expression expr) {
		int max = 0;
		for (EObject child : expr.eContents()) {
			if (child instanceof Expression) {
				max = Math.max(max, depth((Expression) child));
			}
		}
		return max + 1;
	}

	private void clean(Var target, Instruction instruction) {
		delete(instruction);
		EcoreUtil.remove(target);
	}

	@Override
	protected void delete(EObject eObject) {
		super.delete(eObject);
		changed = true;
	}

	private void tryFoldExpression(Expression expr) {
		EObject cter = expr.eContainer();
		while (cter instanceof Expression) {
			expr = (Expression) cter;
			cter = expr.eContainer();
		}

		Expression constant = new ConstantFolder().doSwitch(expr);
		if (constant != expr) {
			// sets new expression, in place: a call's arguments are a LIST feature, and eSet on it
			// wants a whole collection -- `bool b = false; assert(!b);` crashed generate (TRAPS T164)
			EcoreUtil.replace(expr, constant);

			// removes old expression
			delete(expr);
		}
	}

	@Override
	protected <T extends EObject> Void visit(EList<T> objects) {
		int i = objects.size() - 1;
		while (i >= 0) {
			int size = objects.size();
			if (size == 0) {
				break;
			}

			T object = objects.get(i);
			doSwitch(object);
			if (objects.size() <= size) {
				// either there are the same number of objects
				// or one was removed
				i--;
			}
		}
		return DONE;
	}

}
