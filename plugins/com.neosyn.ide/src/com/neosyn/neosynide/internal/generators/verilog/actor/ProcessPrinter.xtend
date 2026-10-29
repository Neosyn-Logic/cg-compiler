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
import com.neosyn.models.ir.TypeArray
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

	/**
	 * Optional extra term ANDed into each action's scheduler test, keyed on the action.
	 * TRAPS T57: a produce action must not fire while a stream output it writes is still
	 * presenting a value the consumer has not taken, or it overwrites and destroys it.
	 * Set by the synchronous printer for the stall path only; null everywhere else, so
	 * combinational actors and the direct held-producer path are untouched.
	 */
	var protected (Action)=>String extraActionGuard = null

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
		// (test, procedure) for each branch; a null test is unconditional and ends the chain, since
		// nothing after it can run
		val branches = new java.util.ArrayList<Pair<String, Procedure>>
		for (action : actions) {
			val base = if (skipSchedulerTest) null else printSchedulerTest(action)
			val extra = if (extraActionGuard === null) null else extraActionGuard.apply(action)
			val test =
				if (extra.nullOrEmpty) base
				else if (base === null) extra
				else base + " && " + extra
			branches += test -> (if (combinational) action.combinational else action.body)
			if (test === null) {
				// stop: an unconditional branch hides everything after it
				return printChain(trimTrailingEmpty(branches), combinational)
			}
		}
		printChain(trimTrailingEmpty(branches), combinational)
	}

	/**
	 * Drops EMPTY branches from the end of an if/else-if chain: they change nothing. An empty branch
	 * before a non-empty one stays -- in a priority chain it stops the later ones from firing.
	 * (TRAPS T116: `begin // line 68 end`, `end else begin // line 0 end` -- 1400 of them.)
	 */
	def private trimTrailingEmpty(List<Pair<String, Procedure>> branches) {
		var n = branches.size
		while (n > 0 && branches.get(n - 1).value.isEmptyBody) {
			n--
		}
		branches.subList(0, n)
	}

	def private boolean isEmptyBody(Procedure procedure) {
		procedure === null || procedure.locals.empty
			&& procedure.blocks.forall[b | b instanceof com.neosyn.models.ir.BlockBasic
				&& (b as com.neosyn.models.ir.BlockBasic).instructions.empty]
	}

	/** Prints the chain; null when there is nothing left to print. */
	def private CharSequence printChain(List<Pair<String, Procedure>> branches, boolean combinational) {
		var CharSequence acc = null
		for (branch : branches) {
			if (branch.key === null && acc === null && branch.value.locals.empty) {
				// An unconditional action standing alone: its statements, without a bare
				// `begin // line N ... end` around them -- every caller prints the chain inside an
				// enclosing begin/end already (TRAPS T116, 2144 sites). A block that declares
				// temporaries keeps its named begin/end.
				return printStatements(branch.value)
			}
			val body = printBody(branch.value, combinational)
			if (branch.key === null) {
				return '''«IF acc !== null»«acc» else «ENDIF»«body»'''
			}
			acc = '''«IF acc !== null»«acc» else «ENDIF»if («branch.key») «body»'''
		}
		acc
	}

	/**
	 * @param combinational whether the body is printed in a combinational block. There, every scalar
	 *        local gets a zero DEFAULT right after its declaration. A temporary assigned on one path
	 *        only -- `transform_ret_1` in AES's MixColumns, assigned in the `else` and read only
	 *        there -- otherwise keeps its value on the other paths, which every synthesizer reports as
	 *        an inferred latch (TRAPS T66: 80 in RISC-V, Intel-8088 and AES). The default changes no
	 *        value a path reads. A CLOCKED body gets none: there a local read before it is written
	 *        is a register, and a default would change what it holds.
	 */
	def private printBody(Procedure procedure, boolean combinational) {
		irPrinter.resetWriteForwarding()
		val hasLocals = !procedure.locals.empty
		// Generate a unique block name if procedure has locals but empty name
		val blockName = if (procedure.name.nullOrEmpty) "block_" + procedure.lineNumber else procedure.name

		'''
		begin«IF hasLocals» : «blockName»«ENDIF»«IF procedure.lineNumber != 0» // line «procedure.lineNumber»«ENDIF»
		  «IF hasLocals»
		  «FOR variable : procedure.locals»
		  reg «irPrinter.doSwitch(variable)»;
		  «ENDFOR»
		  «IF combinational»
		  «FOR variable : procedure.locals.filter[!(type instanceof TypeArray)]»
		  «namer.getName(variable)» = 0;
		  «ENDFOR»
		  «ENDIF»

		  «ENDIF»
		  «irPrinter.doSwitch(procedure.blocks)»
		end'''
	}

	/** The statements of a body that declares nothing, its source line as a comment above them. */
	def private printStatements(Procedure procedure) {
		irPrinter.resetWriteForwarding()
		'''
		«IF procedure.lineNumber != 0»
		// line «procedure.lineNumber»
		«ENDIF»
		«irPrinter.doSwitch(procedure.blocks)»'''
	}

	/**
	 * Whether the state register has encodings that are not states -- the ones a safe state (F7)
	 * exists for. The register is {@code TypeUtil.getSize(states.size - 1)} bits wide, so there
	 * are unused values unless the state count is exactly a power of two.
	 */
	def protected final boolean unusedEncodings(FSM fsm) {
		val states = fsm.states.size
		states > 0 && states < (1 << TypeUtil.getSize(states - 1))
	}

	def protected final printFsm(FSM fsm, boolean combinational) {
		var stateIndex = 0
		val items = new java.util.ArrayList<CharSequence>
		for (state : fsm.states) {
			val label = if (state.name.nullOrEmpty) "s_" + stateIndex++ else state.name
			val chain = printActions(state.outgoing.map[edge|(edge as Transition).action], combinational)
			// In the combinational process a state with nothing to do needs no item: the defaults at
			// the top of the process already hold. The clocked process keeps it -- there a missing
			// item would fall to `default: $stop`.
			if (chain !== null || !combinational) {
				items += '''
				«label»: begin
				  «chain»
				end
				'''
			}
		}
		if (items.empty && combinational) {
			return ''''''
		}
		'''
		case (FSM)
		  «FOR item : items»
		  «item»

		  «ENDFOR»
		  «IF !combinational && unusedEncodings(fsm)»
		  // Safe state: this register holds encodings that are not states, and without a default
		  // synthesis treats them as don't-care -- an upset bit could park the FSM there for good.
		  default: begin
		    // synthesis translate_off
		    $stop;
		    // synthesis translate_on
		    FSM <= «IF fsm.initialState.name.nullOrEmpty»s_0«ELSE»«fsm.initialState.name»«ENDIF»;
		  end
		  «ELSEIF !combinational»
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
			// TRAPS T91: the argument is the wire ActorPrinter.printActor declares, not a literal. With (1'b0)
			// every argument was constant, so Yosys evaluated the call as a constant function and refused
			// one that calls a function on a module signal (UDP/Ethernet CheckFrame_crc.v:44).
			// TRAPS T147: and every signal the function reads, so `always @(*)` is sensitive to them
			// (SchedulerInputs). The function's inputs carry the same names.
			'''«schedulerName»(_sched_dummy«FOR variable : SchedulerInputs.of(action)», «namer.getName(variable)»«ENDFOR»)'''
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
