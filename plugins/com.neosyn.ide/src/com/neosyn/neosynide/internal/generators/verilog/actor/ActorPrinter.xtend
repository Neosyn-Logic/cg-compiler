/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.actor

import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.Action
import com.neosyn.models.dpn.Actor
import com.neosyn.models.dpn.FSM
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.internal.generators.verilog.VerilogIrPrinter
import java.util.ArrayList

import static com.neosyn.core.IProperties.PROP_CLOCKS

import static extension com.neosyn.neosynide.internal.generators.GeneratorExtensions.isInlinable

/**
 * This class prints the inside of a module for an actor: functions, tasks, states, processes.
 * 


 */
class ActorPrinter {

	val VerilogIrPrinter irPrinter
	
	val IPathResolver pathResolver

	val protected Namer namer

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		irPrinter = new VerilogIrPrinter(namer, pathResolver)
		this.pathResolver = pathResolver
	}

	var int schedulerCounter

	def private printSchedulerFunctions(Actor actor) {
		schedulerCounter = 0
		'''
		«FOR action : actor.actions»
			«IF !action.scheduler.inlinable»
				«printSchedulerFunction(action)»
			«ENDIF»
		«ENDFOR»
		'''
	}

	/**
	 * Returns true if the FSM has only one state with self-loop transitions.
	 * Such FSMs can be simplified to just if-else branches for combinational tasks.
	 */
	def private isSingleStateFsm(FSM fsm) {
		fsm !== null && fsm.states.size == 1
	}

	def printActor(Actor actor) {
		val combinational = actor.properties.getAsJsonArray(PROP_CLOCKS).empty
		val readyInputPorts = actor.inputs.filter[port|port.interface.syncReady]
		val readyOutputPorts = actor.outputs.filter[port|port.interface.syncReady].toList

		// print a combinational process if the actor is combinational, or has sync ready inputs, or has asynchronous outputs
		val printCombinational = combinational || !readyInputPorts.empty || actor.outputs.exists[!synchronous]

		// For combinational tasks with single-state FSMs, skip FSM and treat as simple if-else
		val skipFsm = combinational && isSingleStateFsm(actor.fsm)

		'''
		/**
		 * State variables
		 */
		«FOR stateVar : actor.variables»
		«irPrinter.printStateVar(actor, stateVar)»
		«ENDFOR»

		«IF !actor.procedures.empty»

		/**
		 * Functions
		 */
		«FOR proc : actor.procedures»
		«irPrinter.printFunction(proc)»
		«ENDFOR»
		«ENDIF»

		«IF actor.hasFsm && !skipFsm»

		/**
		 * FSM
		 */
		«printStates(actor.fsm)»
		«ENDIF»

		«IF !skipFsm»
		«printSchedulerFunctions(actor)»
		«ENDIF»

		«IF printCombinational»
		/**
		 * Combinational process
		 */
		«new CombinationalPrinter(namer, pathResolver).printProcess(actor, combinational, skipFsm)»
		«ENDIF»

		«IF !combinational»
		/**
		 * Synchronous process
		 */
		«new SynchronousPrinter(namer, pathResolver).printProcess(actor, readyOutputPorts)»
		«ENDIF»
		'''
	}

	def private printSchedulerFunction(Action action) {
		// Generate unique names for empty action/scheduler names using counter
		val idx = schedulerCounter++
		val actionName = if (action.name.nullOrEmpty) "action_" + idx else action.name
		var schedulerName = action.scheduler.name
		if (schedulerName.nullOrEmpty) {
			schedulerName = "isSchedulable_" + actionName
			// Store the generated name so ProcessPrinter uses the same one
			action.scheduler.setName(schedulerName)
		}
		'''
		// Scheduler of «actionName» (line «action.scheduler.lineNumber»)
		function «schedulerName»(input _dummy);
		  «FOR variable : action.scheduler.locals»
		  	reg «irPrinter.doSwitch(variable)»;
		  «ENDFOR»
		begin
		  «irPrinter.doSwitch(action.scheduler.blocks)»
		end
		endfunction

		'''
	}

	def private printState(String name, int i, int size) {
		var value = Integer.toBinaryString(i)
		val n = Math.max(0, size - value.length)
		'''localparam «name» = «size»'b«if (n > 0) String.format("%0" + n + "d", 0)»«value»;'''
	}

	def private printStates(FSM fsm) {
		val states = fsm.states
		val size = TypeUtil.getSize(states.size - 1)
		val parameters = new ArrayList<CharSequence>

		// Generate default state names for empty/null state names
		states.forEach[state, i|
			val stateName = if (state.name.nullOrEmpty) "s_" + i else state.name
			parameters += printState(stateName, i, size)
		]

		'''
		reg «IF size > 1 »[«size - 1» : 0] «ENDIF»FSM;

		«parameters.join('\n')»
		'''
	}

}
