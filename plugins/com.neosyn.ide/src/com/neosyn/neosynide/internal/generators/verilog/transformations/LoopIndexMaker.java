/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.google.common.collect.Lists;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * This class defines an IR visitor that adds synthetic variables to use in "for" loops to copy
 * arrays in procedures.
 * 

 *
 */
public class LoopIndexMaker extends AbstractIrVisitor {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	private Var index;

	private void addIndex(Procedure procedure, Var local) {
		InstLoad load = getFirst(local.getDefs(), InstLoad.class);
		if (load == null) {
			return;
		}

		if (index == null) {
			Type type = ir.createTypeInt(32, false);
			index = ir.newTempLocalVariable(procedure, type, "loop_idx");
		}

		load.getIndexes().add(ir.createExprVar(index));

		InstStore store = getFirst(Lists.reverse(local.getUses()), InstStore.class);
		if (store != null) {
			store.getIndexes().add(ir.createExprVar(index));
		}
	}

	private <T extends EObject> T getFirst(List<? extends EObject> eObjects, Class<T> clz) {
		Iterator<? extends EObject> it = eObjects.iterator();
		return it.hasNext() ? EcoreHelper.getContainerOfType(it.next(), clz) : null;
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		index = null;
		for (Var local : new ArrayList<>(procedure.getLocals())) {
			if (local.getType().isArray()) {
				addIndex(procedure, local);
			}
		}

		this.procedure = procedure;
		return visit(procedure.getBlocks());
	}

}
