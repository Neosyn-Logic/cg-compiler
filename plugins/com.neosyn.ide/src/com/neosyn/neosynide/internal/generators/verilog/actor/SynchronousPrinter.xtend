/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.actor

import com.neosyn.models.dpn.Action
import com.neosyn.models.dpn.Actor
import com.neosyn.models.dpn.Port
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.transformations.ArrayWritePort
import com.neosyn.neosynide.transformations.StreamLinkMarker
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

	/**
	 * Emits the per-cycle body of a synchronous actor: buffered-input latches, output-flag
	 * resets, the stream stall handshake, and the action scheduler.
	 *
	 * <p><b>Each stream output holds its OWN valid until that output is taken</b>:
	 * {@code valid <= valid && !ready}, every cycle, whatever {@code stall} says. Re-asserting all
	 * of them ({@code <valid> <= 1'b1}) raised valid on channels the action never wrote, and gating
	 * the release on every ready deadlocked the task against a consumer legitimately holding one
	 * of them low (TRAPS T7). Holding them only WHILE {@code stall} lost values: every action that
	 * writes a stream output sets {@code stall} from its own ports alone, so writing {@code b}
	 * cleared the hold on an {@code a} still waiting, and {@code a} vanished unaccepted (TRAPS
	 * T115). It also dropped a value whose consumer was ready when it was written but not when it
	 * was presented. Per output, neither can happen: a valid falls only on a transfer.
	 * {@code stall} no longer holds anything; it only says an output is waiting, for the input
	 * {@code ready} of AddReadyAssignments.
	 *
	 * <p>The shape is not exotic: it is every request/response block that answers on one of two
	 * channels -- AXI's B and R among them, where a transaction produces one beat or the other and
	 * never both. The whole {@code cg-ip-cores/Bus} core is built around avoiding it.
	 *
	 * <p>With ONE stream output it differs from the stall-flag hold in two places, both where the
	 * old form was wrong: at the release edge the old form kept {@code valid} high for one more
	 * cycle ({@code valid <= valid} while {@code stall}), re-presenting a value already taken; and
	 * a value written while {@code ready} was high, but not taken on the next cycle, was dropped
	 * because {@code stall} had been cleared at the write.
	 */
	def private printSynchronousStuff(Actor actor, List<Port> readyOutputPorts) {
		// TRAPS T57, the per-action half. On the stall path only, each action additionally
		// requires the slot of every back-pressured stream output IT WRITES to be free. An
		// action that writes none -- a `push` port, an input drained into a queue -- is
		// unaffected, which is why this is per action and not a gate on the whole scheduler.
		val stallPath = !readyOutputPorts.empty && !isDirectHeldProducer(actor, readyOutputPorts)
		if (stallPath) {
			extraActionGuard = [Action a | slotFreeFor(a, readyOutputPorts)]
		}
		val seq = if (actor.hasFsm) printFsm(actor.fsm, false) else printActions(actor.actions, false)
		extraActionGuard = null

		'''
		«FOR ram : ArrayWritePort.ports(actor)»
		«namer.getName(ram.we)» <= 1'b0; // «namer.getName(ram.array)» is written only in a cycle that sets its port
		«ENDFOR»
		«FOR port : actor.bufferedInputs»
		if («port.additionalInputs.head.name») begin
		  «namer.getSafeName("internal_" + port.name + "_valid")» <= 1'b1;
		  «namer.getSafeName("internal_" + port.name)» <= «namer.getName(port)»;
		  «IF port.last !== null»
		  «namer.getSafeName("internal_" + port.last.name)» <= «namer.getName(port.last)»; // the frame boundary stays with its value (TRAPS T58)
		  «ENDIF»
		end

		«ENDFOR»
		«IF isDirectHeldProducer(actor, readyOutputPorts)»
		«val port = readyOutputPorts.head»
		«val validSig = port.additionalOutputs.head.name»
		«val readySig = port.additionalInputs.head.name»
		// Reset the flags of every OTHER synchronous output; the direct held output keeps its
		// valid (see below), so it must not be defaulted to 0 here.
		«resetPortFlags(actor.outputs.filter[synchronous].filter[o | o !== port], false)»
		// Direct held-producer link (StreamLinkMarker): present the value and HOLD it until the
		// consumer takes it (ready). `«validSig»`/`«port.name»` are registered, so they lag the FSM
		// by a cycle; advance the scheduler (loading the next value) ONLY when the current value is
		// accepted («readySig») or none is presented yet (!«validSig»). Otherwise every register
		// holds, so no value is dropped under consumer backpressure.
		if («readySig» || !«validSig») begin
		  «validSig» <= 1'b0;
		  «seq»
		end
		«ELSE»
		«resetPortFlags(actor.outputs.filter[synchronous].filter[o | !readyOutputPorts.contains(o)], false)»

		«IF readyOutputPorts.empty»
		«seq»
		«ELSE»
		// Each stream output stays presented until THAT output is taken (valid && ready).
		«FOR port : readyOutputPorts»
		«port.additionalOutputs.head.name» <= «port.additionalOutputs.head.name» && !«port.additionalInputs.head.name»;
		«ENDFOR»
		if (stall && «slotFree(readyOutputPorts)») begin
		  stall <= 1'b0;
		end
		// The scheduler runs every cycle. An action that writes a back-pressured stream output
		// additionally requires that output's slot to be free -- see its own guard below.
		«seq»
		«ENDIF»
		«ENDIF»
		'''
	}

	/**
	 * The slot-free term for the back-pressured stream outputs THIS action writes, or null when
	 * it writes none.
	 *
	 * <p>TRAPS T57. Without it a produce action fires while an output is still presenting a value
	 * the consumer has not taken, overwrites it, and the value is destroyed AT THE PRODUCER with
	 * nothing reporting a fault. Measured in the sold UDP core: {@code IP_Send} presented the
	 * first payload byte with {@code body_valid=1} while {@code body_ready=0} and overwrote it on
	 * the next cycle; the UDP protocol gate went 1453/1 to 1454/0 when this landed.
	 *
	 * <p>It is PER ACTION for a measured reason. Gating the whole scheduler on the same condition
	 * instead froze {@code IP_Send}'s header writes while {@code body} was stalled -- the header is
	 * a {@code push} port with no back-pressure of its own -- and the gate went 1453/1 to
	 * <b>1440/14</b> with the entire IP header undriven ({@code xx}). An action that writes no
	 * back-pressured output must stay free to run. `action.outputPattern.ports` is the same per-action set that
	 * AddReadyAssignments uses to decide which actions assign `stall`, so the guard and the
	 * stall can never disagree about which ports an action produces on.
	 */
	def private String slotFreeFor(Action action, List<Port> readyOutputPorts) {
		val written = readyOutputPorts.filter[p | action.outputPattern.ports.contains(p)].toList
		if (written.empty) null else slotFree(written)
	}

	/**
	 * "Every stream output slot is free": each ready output either presents nothing
	 * ({@code !valid}) or is being taken this cycle ({@code ready}). Used both to release the
	 * stall and to gate the action scheduler, so the two can never disagree — a produce action
	 * firing while an output is still unacknowledged is TRAPS T57, silent data loss.
	 */
	def private String slotFree(List<Port> readyOutputPorts) {
		readyOutputPorts.map[p | '''(!«p.additionalOutputs.head.name» || «p.additionalInputs.head.name»)'''].join(' && ')
	}

	/**
	 * True when the actor drives a SINGLE direct held-producer link (StreamLinkMarker): its only
	 * ready output feeds a task-to-task stream with no FIFO in between. Then we replace the
	 * registered-producer stall handshake with the standard hold-until-ready scheduler above.
	 *
	 * <p>Applies to both a pure held producer and a RELAY (an actor that also consumes a direct
	 * held input): the hold guard `if (out_ready || !out_valid)` gates the whole action scheduler,
	 * so a relay neither advances nor re-consumes while its output is backpressured. Its input ready
	 * is made to track the same "output slot free" condition in AddReadyAssignments, keeping the two
	 * sides in sync so backpressure propagates through a ready chain. Restricted to a single ready
	 * output (the general case).
	 */
	def private boolean isDirectHeldProducer(Actor actor, List<Port> readyOutputPorts) {
		StreamLinkMarker.isDirectHeldProducer(actor, readyOutputPorts)
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
