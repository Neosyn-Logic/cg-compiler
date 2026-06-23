/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import static com.neosyn.models.util.SwitchUtil.DONE;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.util.IrSwitch;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.Void;

/**
 * This abstract class implements a no-op visitor on IR procedures, blocks,
 * instructions, and (if visitFull is <code>true</code>) expressions. This class
 * should be extended by classes that implement intra-procedural IR visitors and
 * transformations.
 * 

 * @since 1.2
 */
public abstract class AbstractIrVisitor extends IrSwitch<Void> {
	
	/**
	 * current procedure being visited
	 */
	protected Procedure procedure;

	@Override
	public Void caseBlockBasic(BlockBasic block) {
		return visit(block.getInstructions());
	}

	@Override
	public Void caseBlockIf(BlockIf blockIf) {
		visit(blockIf.getThenBlocks());
		visit(blockIf.getElseBlocks());
		doSwitch(blockIf.getJoinBlock());
		return DONE;
	}

	@Override
	public Void caseBlockWhile(BlockWhile blockWhile) {
		visit(blockWhile.getBlocks());
		doSwitch(blockWhile.getJoinBlock());
		return DONE;
	}

	@Override
	public Void caseExprBinary(ExprBinary expr) {
		doSwitch(expr.getE1());
		doSwitch(expr.getE2());
		return DONE;
	}

	@Override
	public Void caseExprUnary(ExprUnary expr) {
		doSwitch(expr.getExpr());
		return DONE;
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		this.procedure = procedure;
		return visit(procedure.getBlocks());
	}

	protected void delete(EObject eObject) {
		IrUtil.delete(eObject);
	}

	@Override
	public final Void doSwitch(EObject eObject) {
		if (eObject == null) {
			return null;
		}
		return doSwitch(eObject.eClass(), eObject);
	}

	@Override
	public Void doSwitch(int classifierID, EObject eObject) {
		// just so we can use it in DfVisitor
		return super.doSwitch(classifierID, eObject);
	}

	@Override
	public boolean isSwitchFor(EPackage ePackage) {
		// just so we can use it in DfVisitor
		return super.isSwitchFor(ePackage);
	}

	protected void replace(Instruction instr, Instruction by) {
		BlockBasic block = (BlockBasic) instr.eContainer();
		int index = block.indexOf(instr);
		block.getInstructions().set(index, by);
	}

	protected <T extends EObject> Void visit(EList<T> objects) {
		int i = 0; 
		while (i < objects.size()) {
			T object = objects.get(i);
			int size = objects.size();
			doSwitch(object);
			if (size == objects.size() && object == objects.get(i)) {
				i++;
			}
		}
		return DONE;
	}

}
