/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import static com.neosyn.models.ir.IrPackage.Literals.PROCEDURE__PARAMETERS;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstPhi;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * This class removes phi assignments and transforms them to copies.
 * 
 */
public class PhiRemoval extends AbstractIrVisitor {

	private class PhiRemover extends AbstractIrVisitor {

		@Override
		public Void caseInstPhi(InstPhi phi) {
			delete(phi);
			return DONE;
		}

	}

	private List<Var> localsToRemove;

	private int phiIndex;

	private BlockBasic targetBlock;

	@Override
	public Void caseBlockBasic(BlockBasic block) {
		return visit(block.getInstructions());
	}

	@Override
	public Void caseBlockIf(BlockIf block) {
		BlockBasic join = block.getJoinBlock();
		targetBlock = IrUtil.getLast(block.getThenBlocks());
		phiIndex = 0;
		doSwitch(join);

		targetBlock = IrUtil.getLast(block.getElseBlocks());
		phiIndex = 1;
		doSwitch(join);
		new PhiRemover().doSwitch(join);

		visit(block.getThenBlocks());
		return visit(block.getElseBlocks());
	}

	@Override
	public Void caseBlockWhile(BlockWhile block) {
		List<Block> blocks = EcoreHelper.getContainingList(block);
		// the block before the while.
		int indexBlock = blocks.indexOf(block);
		if (indexBlock > 0) {
			Block previousBlock = blocks.get(indexBlock - 1);
			if (previousBlock.isBlockBasic()) {
				targetBlock = (BlockBasic) previousBlock;
			} else {
				targetBlock = IrFactory.eINSTANCE.createBlockBasic();
				blocks.add(indexBlock, targetBlock);
			}
		} else {
			targetBlock = IrFactory.eINSTANCE.createBlockBasic();
			blocks.add(indexBlock, targetBlock);
		}

		BlockBasic join = block.getJoinBlock();
		phiIndex = 0;
		doSwitch(join);

		// last block of the while
		targetBlock = IrUtil.getLast(block.getBlocks());
		phiIndex = 1;
		doSwitch(join);
		new PhiRemover().doSwitch(join);

		// visit inner blocks
		return visit(block.getBlocks());
	}

	@Override
	public Void caseInstPhi(InstPhi phi) {
		Var target = phi.getTarget().getVariable();
		Expression sourceExpr = phi.getValues().get(phiIndex);

		// if source is a local variable with index = 0, we remove it from the
		// procedure (valueOf translates it to 0)
		if (isExprVarZero(sourceExpr)) {
			localsToRemove.add(((ExprVar) sourceExpr).getUse().getVariable());
		}

		InstAssign assign = IrFactory.eINSTANCE.createInstAssign(target, valueOf(phi, phiIndex));
		targetBlock.add(assign);

		return DONE;
	}

	/**
	 * The value {@code phi} takes from its predecessor {@code index} (an if: 0 = then, 1 = else;
	 * a while: 0 = before the loop, 1 = the loop body), as a new expression to assign. A local
	 * never defined on that path (SSA index 0, not a parameter) reads as zero -- {@code false}
	 * for a bool. Shared with CodeCleaner, which folds an if whose condition is a constant and
	 * must turn its phis into exactly the assignments this pass would have made.
	 */
	public static Expression valueOf(InstPhi phi, int index) {
		Expression sourceExpr = phi.getValues().get(index);
		if (isExprVarZero(sourceExpr)) {
			if (phi.getTarget().getVariable().getType().isBool()) {
				return IrFactory.eINSTANCE.createExprBool(false);
			}
			return IrFactory.eINSTANCE.createExprInt(0);
		}
		return IrUtil.copy(sourceExpr);
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		localsToRemove = new ArrayList<Var>();

		zeroUndefinedReads(procedure);
		super.caseProcedure(procedure);

		for (Var local : localsToRemove) {
			procedure.getLocals().remove(local);
		}

		return DONE;
	}

	/**
	 * The same rule as {@link #valueOf} for a read that does not go through a phi. A local written
	 * one bit at a time and never initialised ({@code u4 n; n[0] = x;}) becomes
	 * {@code n_2 = (n | 1)} over a start value {@code n} that nothing defines: the simulator reads
	 * it as 0, but the HDL declared it and never drove it, so every bit the source did not write
	 * was X in Verilog and 'U' in VHDL (TRAPS T120). Such a read is the literal it stands for, and
	 * the local goes. Scalars only, and only a local with no definition at all.
	 *
	 * <p>Only a read that is still IN the procedure counts. Earlier passes leave stale uses behind
	 * on detached expressions -- every scheduler's {@code cond} has them -- and a local whose uses
	 * are not all live reads is left exactly as it was (removing those dead locals is a separate
	 * change: it lets every scheduler inline).
	 */
	private void zeroUndefinedReads(Procedure procedure) {
		for (Var local : procedure.getLocals()) {
			if (local.getIndex() != 0 || !local.getDefs().isEmpty() || local.getUses().isEmpty()
					|| !(local.getType().isInt() || local.getType().isBool())) {
				continue;
			}
			boolean allLive = true;
			for (Use use : local.getUses()) {
				allLive &= use.eContainer() instanceof ExprVar && EcoreUtil.isAncestor(procedure, use);
			}
			if (!allLive) {
				continue;
			}
			for (Use use : new ArrayList<>(local.getUses())) {
				Expression zero = local.getType().isBool() ? IrFactory.eINSTANCE.createExprBool(false)
						: IrFactory.eINSTANCE.createExprInt(0);
				EcoreUtil.replace(use.eContainer(), zero);
				local.getUses().remove(use);
			}
			localsToRemove.add(local);
		}
	}

	private static boolean isExprVarZero(Expression sourceExpr) {
		if (sourceExpr.isExprVar()) {
			Var source = ((ExprVar) sourceExpr).getUse().getVariable();
			return source.getIndex() == 0 && source.isLocal()
					&& source.eContainingFeature() != PROCEDURE__PARAMETERS;
		}

		return false;
	}

}
