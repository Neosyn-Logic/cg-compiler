/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.generator;

import static com.neosyn.models.util.SwitchUtil.DONE;

import com.neosyn.models.dpn.Goto;
import com.neosyn.models.ir.InstCall;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.Void;

/**
 * This class defines an IR transformation that removes stores, prints and asserts in scheduler.
 * 
 * <p>In the generated HDL a scheduler procedure is evaluated EVERY cycle, to decide whether its
 * action fires, and a developed {@code if} copies the whole branch into it -- statements included, not only the
 * condition. A side effect left there runs whether or not the guard holds. For an assert that
 * means checking a port nobody has written yet: {@code if (x.available()) { assert(x.read() == 7); }}
 * failed on the first cycle in both HDLs while the bytecode simulator passed. The action body
 * keeps its own copy, so the check still runs exactly when the action fires.
 */
public class SideEffectRemover extends AbstractIrVisitor {

	@Override
	public Void caseInstCall(InstCall call) {
		if (call.isPrint() || call.isAssert()) {
			delete(call);
		}

		return DONE;
	}

	@Override
	public Void caseInstruction(Instruction inst) {
		if (inst instanceof Goto) {
			delete(inst);
		}
		return DONE;
	}

	@Override
	public Void caseInstStore(InstStore store) {
		delete(store);
		return DONE;
	}

}
