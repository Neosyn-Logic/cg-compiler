/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import static com.neosyn.models.ir.util.IrUtil.getNameSSA;
import static com.neosyn.models.util.SwitchUtil.DONE;

import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.Void;

/**
 * This class defines an IR transformation that renames local variables when
 * using SSA.
 * 

 * 
 */
public class SSAVariableRenamer extends AbstractIrVisitor {

	@Override
	public Void caseProcedure(Procedure procedure) {
		for (Var local : procedure.getLocals()) {
			local.setName(getNameSSA(local));
		}

		return DONE;
	}

}
