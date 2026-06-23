/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.generator;

import static com.google.common.collect.Iterables.all;
import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.google.gson.JsonElement;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.StoreOnceTransformation;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.Void;

/**
 * This class splits code between combinational and body procedures depending on the type of ports
 * written (combinational or not).
 * 

 *
 */
public class CombinationalVisitor {

	private static class CodeRemover extends SideEffectRemover {

		private boolean combinational;

		public CodeRemover(boolean combinational) {
			this.combinational = combinational;
		}

		@Override
		protected void delete(EObject eObject) {
			if (combinational) {
				// only deletes prints, gotos, etc. in combinational procedure
				IrUtil.delete(eObject);
			}
		}

		@Override
		public Void caseInstStore(InstStore store) {
			Var target = store.getTarget().getVariable();
			Port port;
			if (target instanceof Port) {
				port = (Port) target;
			} else {
				EObject cter = target.eContainer();
				if (cter instanceof Port) {
					port = (Port) cter;
				} else {
					// store target is not a port or port signal, delegates to super
					return super.caseInstStore(store);
				}
			}

			if (combinational == port.isSynchronous()) {
				// deletes synchronous ports in combinational procedure
				// and non-synchronous ports in body procedure
				IrUtil.delete(store);
			}

			return DONE;
		}

	}

	/**
	 * Checks if an actor is combinational.
	 * Detection methods:
	 * 1. properties { clocks: [] } - empty clocks array
	 * 2. properties { type: "combinational" } - explicit type
	 */
	private boolean isCombinational(Actor actor) {
		// Check for empty clocks array
		JsonElement clocks = actor.getProperties().get(PROP_CLOCKS);
		if (clocks != null && clocks.isJsonArray() && clocks.getAsJsonArray().isEmpty()) {
			return true;
		}

		// Check for type: "combinational"
		JsonElement type = actor.getProperties().get("type");
		if (type != null && type.isJsonPrimitive() && "combinational".equals(type.getAsString())) {
			return true;
		}

		return false;
	}

	public void visit(Actor actor) {
		boolean combinational = isCombinational(actor);
		if (combinational) {
			// makes all ports asynchronous
			for (Port port : actor.getOutputs()) {
				port.setSynchronous(false);
			}
		} else if (all(actor.getOutputs(), port -> port.isSynchronous())) {
			// actor not combinational, and no combinational ports, nothing to do
			return;
		}

		for (Action action : actor.getActions()) {
			if (combinational) {
				// easy: all the body becomes combinational
				Procedure comb = action.getCombinational();
				action.setCombinational(action.getBody());
				action.setBody(comb);
			} else {
				List<Port> combPorts = new ArrayList<>();
				for (Port port : action.getOutputPattern().getPorts()) {
					if (!port.isSynchronous()) {
						combPorts.add(port);
					}
				}

				// if action does not write to a combinational port, ignore
				if (combPorts.isEmpty()) {
					continue;
				}

				String name = action.getCombinational().getName();
				action.setCombinational(IrUtil.copy(action.getBody()));
				action.getCombinational().setName(name);

				// removes stores to combinational ports in body
				new CodeRemover(false).doSwitch(action.getBody());

				// removes side effects except stores to combinational ports in combinational proc
				new StoreOnceTransformation().doSwitch(action.getCombinational());
				new CodeRemover(true).doSwitch(action.getCombinational());
			}
		}
	}

}
