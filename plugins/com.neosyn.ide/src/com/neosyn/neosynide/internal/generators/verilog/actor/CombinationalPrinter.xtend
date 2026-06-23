/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.actor

import com.neosyn.models.dpn.Actor
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import com.neosyn.core.IPathResolver

/**
 * This class generates the combinational process for an actor.
 * 


 */
class CombinationalPrinter extends ProcessPrinter {

	new(Namer namer, IPathResolver pathResolver) {
		super(namer, pathResolver)
	}

	/**
	 * Print a combinational process.
	 */
	def printProcess(Actor actor, boolean combinational) {
		printProcess(actor, combinational, false)
	}

	/**
	 * Print a combinational process.
	 * @param skipFsm if true, skip FSM and print actions directly (for single-state FSMs)
	 */
	def printProcess(Actor actor, boolean combinational, boolean skipFsm) {
		val signals = new ArrayList
		if (combinational) {
			actor.inputs.forEach[port|
				signals += namer.getName(port)
				signals += port.additionalInputs.map[name]
			]
		}

		// workaround for empty sensitivity list
		if (signals.empty) {
			signals += '*'
		}

		'''
		always @(«signals.join(' or ')») begin
		  «resetPortFlags(actor.inputs, true)»
		  «resetPorts(actor.outputs, true)»
		  «IF actor.hasFsm && !skipFsm»
		  «printFsm(actor.fsm, true)»
		  «ELSE»
		  «printActions(actor.actions, true, combinational)»
		  «ENDIF»
		end
		'''
	}

}
