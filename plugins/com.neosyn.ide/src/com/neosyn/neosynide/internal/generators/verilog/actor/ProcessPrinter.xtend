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
import com.neosyn.models.dpn.FSM
import com.neosyn.models.dpn.Port
import com.neosyn.models.dpn.Transition
import com.neosyn.models.ir.Procedure
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.internal.generators.verilog.VerilogIrPrinter
import java.util.List

import static extension com.neosyn.neosynide.internal.generators.GeneratorExtensions.getExpression
import static extension com.neosyn.neosynide.internal.generators.GeneratorExtensions.isInlinable

/**
 * This class defines all functionality common to synchronous and combinational process printers.
 * 


 */
abstract class ProcessPrinter {

	val protected Namer namer

	val protected VerilogIrPrinter irPrinter

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		irPrinter = new VerilogIrPrinter(namer, pathResolver)
	}

	def protected final printActions(List<Action> actions, boolean combinational) {
		printActions(actions, combinational, false)
	}

	/**
	 * @param skipSchedulerTest if true, skip the scheduler test (for purely combinational actors
	 *        where the action always fires regardless of port validity)
	 */
	def protected final printActions(List<Action> actions, boolean combinational, boolean skipSchedulerTest) {
		var CharSequence acc = null
		for (action : actions) {
			val test = if (skipSchedulerTest) null else printSchedulerTest(action)
			val body = printBody(if (combinational) action.combinational else action.body)
			if (test === null) {
				return '''«IF acc !== null»«acc» else «ENDIF»«body»'''
			} else {
				acc = '''«IF acc !== null»«acc» else «ENDIF»if («test») «body»'''
			}
		}
		acc
	}

	def private printBody(Procedure procedure) {
		irPrinter.resetWriteForwarding()
		val hasLocals = !procedure.locals.empty
		// Generate a unique block name if procedure has locals but empty name
		val blockName = if (procedure.name.nullOrEmpty) "block_" + procedure.lineNumber else procedure.name

		'''
		begin«IF hasLocals» : «blockName»«ENDIF» // line «procedure.lineNumber»
		  «IF hasLocals»
		  «FOR variable : procedure.locals»
		  reg «irPrinter.doSwitch(variable)»;
		  «ENDFOR»

		  «ENDIF»
		  «irPrinter.doSwitch(procedure.blocks)»
		end'''
	}

	def protected final printFsm(FSM fsm, boolean combinational) {
		var stateIndex = 0
		'''
		case (FSM)
		  «FOR state : fsm.states»
		  «IF state.name.nullOrEmpty»s_«stateIndex++»«ELSE»«state.name»«ENDIF»: begin
		    «printActions(state.outgoing.map[edge|(edge as Transition).action], combinational)»
		  end

		  «ENDFOR»
		  «IF !combinational»
		  // synthesis translate_off
		  default: $stop;
		  // synthesis translate_on
		  «ENDIF»
		endcase
		'''
	}

	def private String printSchedulerCall(Action action) {
		if (action.scheduler.inlinable) {
			'''«irPrinter.doSwitch(action.scheduler.expression)»'''
		} else {
			// Name was set by ActorPrinter.printSchedulerFunction
			val schedulerName = if (action.scheduler.name.nullOrEmpty) "sched_" + action.scheduler.lineNumber else action.scheduler.name
			'''«schedulerName»(1'b0)'''
		}
	}

	def protected final printSchedulerTest(Action action) {
		val test = printSchedulerCall(action)
		if (test == "1'b1") null else test
	}

	/**
	 * Resets any additional output signals (if any) of the given ports.
	 * @see resetPortFlags(Port)
	 */
	def protected final resetPortFlags(Iterable<Port> ports, boolean combinational) {
		'''
		«FOR port : ports»
			«FOR signal : port.additionalOutputs»
				«signal.name» «IF !combinational»<«ENDIF»= 1'b0;
			«ENDFOR»
		«ENDFOR»
		'''
	}

	/**
	 * Resets the given ports.
	 */
	def protected final resetPorts(Iterable<Port> ports, boolean combinational) {
		val filtered = ports.filter[synchronous != combinational]

		'''
		«FOR port : filtered»
			«namer.getName(port)» «IF !combinational»<«ENDIF»= «TypeUtil.getSize(port.type)»'b0;
		«ENDFOR»
		«resetPortFlags(filtered, combinational)»
		'''
	}

}
