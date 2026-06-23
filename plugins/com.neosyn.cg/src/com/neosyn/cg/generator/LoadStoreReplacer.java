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

import java.util.ArrayList;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.util.Void;

/**
 * Replaces loads of local variables and ports by direct references (use), and replaces stores to
 * scalar local variables by assignments.
 * 

 * 
 */
public class LoadStoreReplacer extends AbstractIrVisitor {

	@Override
	public Void caseInstLoad(InstLoad load) {
		Var source = load.getSource().getVariable();
		if ((source.isLocal() || source instanceof Port) && load.getIndexes().isEmpty()) {
			// a load of a global variable, or with indexes
			// must not be replaced

			// Forward-and-remove only when this load is the target local's SOLE
			// definition. Forwarding rewrites the target's *uses* (reads) to the
			// source but not its *defs* (stores); if the local is reassigned
			// later — e.g. a struct field read from a port and then written,
			// `Outer o = in_o.read(); o.f = ...;` — it has another def, and
			// removing the var would strand that store's target (empty target →
			// broken codegen, bug #12). Keep the load + local in that case.
			Var target = load.getTarget().getVariable();
			if (target.getDefs().size() > 1) {
				return DONE;
			}

			// replace uses of target by source
			for (Use use : new ArrayList<>(target.getUses())) {
				use.setVariable(source);
			}

			// remove target
			EcoreUtil.remove(target);

			delete(load);
		}

		return DONE;
	}

	@Override
	public Void caseInstStore(InstStore store) {
		Var target = store.getTarget().getVariable();
		if (!target.isLocal() || !store.getIndexes().isEmpty()) {
			// a store to a global variable, or with indexes
			// must not be replaced by an assign
			return DONE;
		}

		// create assign
		InstAssign assign = IrFactory.eINSTANCE.createInstAssign();
		assign.setTarget(store.getTarget());
		assign.setValue(store.getValue());

		// replace store by assign
		replace(store, assign);

		return DONE;
	}

}
