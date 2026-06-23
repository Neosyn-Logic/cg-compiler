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
import com.neosyn.models.dpn.Port
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import java.util.List

import static com.neosyn.core.IProperties.ACTIVE_LOW
import static com.neosyn.core.IProperties.PROP_ACTIVE
import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_RESETS
import static com.neosyn.core.IProperties.PROP_TYPE
import static com.neosyn.core.IProperties.RESET_ASYNCHRONOUS
import com.neosyn.core.IPathResolver

/**
 * This class generates the synchronous process for an actor.
 * 


 */
class SynchronousPrinter extends ProcessPrinter {

	new(Namer namer, IPathResolver pathResolver) {
		super(namer, pathResolver)
	}

	/**
	 * Print the synchronous process which contains the body of the tasks
	 */	
	def printProcess(Actor actor, List<Port> readyOutputPorts) {
		val sensitivity = new ArrayList<CharSequence>
		val resets = actor.properties.getAsJsonArray(PROP_RESETS)
		var String resetName = null
		var negateReset = false

		val hasReset = resets.size > 0
		if (hasReset) {
			val resetObj = resets.get(0).asJsonObject

			negateReset = ACTIVE_LOW.equals(resetObj.get(PROP_ACTIVE))
			resetName = resetObj.getAsJsonPrimitive('name').asString

			if (RESET_ASYNCHRONOUS.equals(resetObj.get(PROP_TYPE))) {
				val active = if (negateReset) 'negedge' else 'posedge'
				sensitivity.add(active + ' ' + resetName)
			}
		}

		// only tasks with clocks use this method, so we know we have one clock
		sensitivity.add('posedge ' + actor.properties.getAsJsonArray(PROP_CLOCKS).head.asString)

		'''
		always @(«sensitivity.join(' or ')») begin // body of «actor.simpleName»
		  «IF hasReset»
		  if («IF negateReset»~«ENDIF»«resetName») begin
		    «resetStateVars(actor)»
		    «resetPorts(actor.outputs, false)»
		    «IF actor.hasFsm»
		    FSM <= «IF actor.fsm.initialState.name.nullOrEmpty»s_0«ELSE»«actor.fsm.initialState.name»«ENDIF»;
		    «ENDIF»
		  end else begin
		    «printSynchronousStuff(actor, readyOutputPorts)»
		  end
		  «ELSE /* no reset */»
		  «printSynchronousStuff(actor, readyOutputPorts)»
		  «ENDIF»
		end
		'''
	}

	def private printSynchronousStuff(Actor actor, List<Port> readyOutputPorts) {
		val seq = if (actor.hasFsm) printFsm(actor.fsm, false) else printActions(actor.actions, false)

		'''
		«FOR port : actor.bufferedInputs»
		if («port.additionalInputs.map[name].join()») begin
		  internal_«port.name»_valid <= 1'b1;
		  internal_«port.name» <= «port.name»;
		end

		«ENDFOR»
		«resetPortFlags(actor.outputs.filter[synchronous], false)»

		«IF readyOutputPorts.empty»
		«seq»
		«ELSE»
		if (stall) begin
		  «FOR signal : readyOutputPorts.map[additionalOutputs.map[name].join]»
		  «signal» <= 1'b1;
		  «ENDFOR»
		  if («readyOutputPorts.map([additionalInputs.map[name].join]).join(' && ')») begin
		    stall <= 1'b0;
		  end
		end
		// Always run the action scheduler so non-stream inputs (e.g. enqueues into a
		// queue with backpressured stream output) are not dropped while stalled.
		// Action guards ensure consume actions only fire when ready is asserted.
		«seq»
		«ENDIF»
		'''
	}

	def private resetStateVars(Actor actor)
		'''
		«FOR variable : actor.variables»
			«IF variable.assignable && !variable.type.array»
				«namer.getName(variable)» <= «irPrinter.printInitialValue(variable)»;
			«ENDIF»
		«ENDFOR»
		'''

}
