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
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * This class removes phi assignments and transforms them to copies.
 * 

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
		// procedure and translate the PHI by an assignment of 0 (zero) to
		// target. Otherwise, we just create an assignment target = source.
		Expression expr;
		if (isExprVarZero(sourceExpr)) {
			Var source = ((ExprVar) sourceExpr).getUse().getVariable();
			localsToRemove.add(source);
			if (target.getType().isBool()) {
				expr = IrFactory.eINSTANCE.createExprBool(false);
			} else {
				expr = IrFactory.eINSTANCE.createExprInt(0);
			}
		} else {
			expr = IrUtil.copy(sourceExpr);
		}

		InstAssign assign = IrFactory.eINSTANCE.createInstAssign(target, expr);
		targetBlock.add(assign);

		return DONE;
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		localsToRemove = new ArrayList<Var>();

		super.caseProcedure(procedure);

		for (Var local : localsToRemove) {
			procedure.getLocals().remove(local);
		}

		return DONE;
	}

	private boolean isExprVarZero(Expression sourceExpr) {
		if (sourceExpr.isExprVar()) {
			Var source = ((ExprVar) sourceExpr).getUse().getVariable();
			return source.getIndex() == 0 && source.isLocal()
					&& source.eContainingFeature() != PROCEDURE__PARAMETERS;
		}

		return false;
	}

}
