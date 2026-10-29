/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.transform;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.neosyn.core.transformations.Transformation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.Void;

/**
 * Adds the combinational {@code ready}-signal assignments for an actor's stream input
 * ports: in each state, a port's {@code ready} is asserted for the action(s) that consume
 * it (gated by {@code !stall}), and a default action deasserts it otherwise so a stalled
 * consumer never advances its producer. For a registered producer read that transitions
 * away from the port, {@code ready} is pulsed only while waiting (valid == 0) so exactly
 * one element is taken. Runs before {@link AddBufferedInputs}, which relies on these
 * assignments to know which ports each action consumes.
 */
public class AddReadyAssignments extends Transformation {

	private static final DpnFactory dpn = DpnFactory.eINSTANCE;

	private static final IrFactory ir = IrFactory.eINSTANCE;

	private Var stall;

	/**
	 * When true (Verilog backend only), a consumer input port that {@link StreamLinkMarker} tagged
	 * as fed by a direct held-task producer gets the standard {@code ready = !stall} handshake
	 * instead of the registered-producer {@code ready = !stall && !valid} pulse. Verilog and VHDL
	 * pass true; the bytecode backend passes false and keeps the historical behaviour (its DPN
	 * scheduler abstracts handshake timing).
	 */
	private final boolean heldStreamHandshake;

	/** Actor currently being transformed (for reading its {@link StreamLinkMarker} marks). */
	private Actor currentActor;

	public AddReadyAssignments() {
		this(false);
	}

	public AddReadyAssignments(boolean heldStreamHandshake) {
		super(null);
		this.heldStreamHandshake = heldStreamHandshake;
	}

	/**
	 * True if {@code port} is a consumer input on a direct held-producer stream link (and the
	 * held handshake is enabled), meaning its {@code ready} must NOT be gated by {@code !valid}.
	 */
	private boolean isDirectHeldInput(Port port) {
		return heldStreamHandshake && StreamLinkMarker.isMarked(currentActor,
				StreamLinkMarker.PROP_DIRECT_HELD_INPUTS, port.getName());
	}

	/**
	 * For a direct held-task RELAY, the "output slot free" condition — {@code AND} over its direct
	 * held output ports of {@code (out_ready || !out_valid)}. Returned as the input-port ready of a
	 * relay so it only accepts upstream data when it can forward it. Returns {@code null} if the
	 * actor has no direct held output (a pure consumer keeps its normal ready logic).
	 */
	private Expression directHeldOutputSlotFree() {
		Expression cond = null;
		for (Port out : dpn.getReadyPorts(currentActor.getOutputs())) {
			if (!StreamLinkMarker.isMarked(currentActor,
					StreamLinkMarker.PROP_DIRECT_HELD_OUTPUTS, out.getName())) {
				continue;
			}
			Var outReady = out.getAdditionalInputs().get(0);
			Var outValid = out.getAdditionalOutputs().get(0);
			Expression slotFree = ir.or(ir.createExprVar(outReady), ir.not(outValid));
			cond = cond == null ? slotFree : ir.and(cond, slotFree);
		}
		return cond;
	}

	/**
	 * The ready of a direct held input that {@code action} consumes. A held producer counts the
	 * value as taken on {@code valid && ready}, so ready must be high EXACTLY when the action can
	 * fire -- high while it cannot, and the producer moves on from a value nobody read; low while
	 * it does, and the producer keeps presenting a value that was read, which is read again.
	 *
	 * <p>A held-path producer (one ready output, itself a direct held link) gates its WHOLE
	 * scheduler on that output's slot, so every action waits on it: {@link
	 * #directHeldOutputSlotFree()}. A stall-path actor gates each action only on the slots of the
	 * stream outputs IT writes (the printers' per-action guard, TRAPS T57), so its ready is that
	 * same per-action set -- ANDing every output instead let the action fire with ready low while
	 * another output waited, and the relay read one value three times (TRAPS T115). No ready
	 * output, or none written by this action: nothing to wait for.
	 */
	private Expression directHeldInputReady(Action action) {
		List<Port> readyOuts = new java.util.ArrayList<>();
		dpn.getReadyPorts(currentActor.getOutputs()).forEach(readyOuts::add);
		if (readyOuts.size() == 1 && StreamLinkMarker.isMarked(currentActor,
				StreamLinkMarker.PROP_DIRECT_HELD_OUTPUTS, readyOuts.get(0).getName())) {
			return directHeldOutputSlotFree();
		}
		Expression cond = null;
		for (Port out : readyOuts) {
			if (!action.getOutputPattern().getPorts().contains(out)) {
				continue;
			}
			Var outReady = out.getAdditionalInputs().get(0);
			Var outValid = out.getAdditionalOutputs().get(0);
			Expression slotFree = ir.or(ir.createExprVar(outReady), ir.not(outValid));
			cond = cond == null ? slotFree : ir.and(cond, slotFree);
		}
		return cond != null ? cond : ir.createExprBool(true);
	}

	/**
	 * Returns true if any action on a transition leaving {@code state} reads
	 * {@code port} in its input pattern — i.e. the consumer continues draining
	 * the same stream port in the next state.
	 */
	private boolean stateReadsPort(State state, Port port) {
		if (state == null) {
			return false;
		}
		for (Edge outgoing : state.getOutgoing()) {
			if (outgoing == null) {
				continue;
			}
			Action action = ((Transition) outgoing).getAction();
			if (action != null && action.getInputPattern().getPorts().contains(port)) {
				return true;
			}
		}
		return false;
	}

	private void addCombinationalAssignments(FSM fsm, List<Action> actions) {
		for (State state : fsm.getStates()) {
			if (state == null) {
				continue;
			}
			Iterable<Port> ports = computeReadyPorts(state);

			for (Edge outgoing : state.getOutgoing()) {
				if (outgoing == null) {
					continue;
				}
				Transition transition = (Transition) outgoing;
				Action action = transition.getAction();
				if (action == null || action.getCombinational() == null) {
					continue;
				}
				BlockBasic block = action.getCombinational().getLast();

				// Only set ready=true for ports that THIS action actually reads.
				// Stall actions (e.g. halted, !exec_ready) don't read stream ports
				// and must NOT assert ready, otherwise the producer advances its FIFO
				// and data is lost while the consumer is stalled.
				Set<Port> actionPorts = new LinkedHashSet<>(action.getInputPattern().getPorts());
				for (Port port : ports) {
					if (actionPorts.contains(port)) {
						Var ready = port.getAdditionalOutputs().get(0);

						// Direct held-task RELAY: this actor consumes `port` (a direct held link)
						// AND produces one. Its input ready must reflect its own downstream
						// backpressure — assert only when the output slot is free — so backpressure
						// propagates combinationally through a ready chain. Overrides the stall form.
						Expression relayReady = isDirectHeldInput(port) ? directHeldInputReady(action) : null;
						if (relayReady != null) {
							block.add(ir.createInstStore(ready, relayReady));
							continue;
						}

						Expression readyValue = stall != null ? ir.not(stall) : ir.createExprBool(true);
						// Registered-producer drain: if this read transitions AWAY from
						// reading `port` (the target state does not read it again), the
						// consumer takes a SINGLE element, so it must pulse ready —
						// assert it only while WAITING (valid==0) and deassert on the
						// capture cycle. A registered producer such as
						// std.fifo.SynchronousFIFO pops one element per cycle that ready
						// is high, so holding ready through the capture cycle pops a
						// second element the consumer never takes — it is lost (this is
						// what dropped every UART byte after the first). When the target
						// state keeps reading the same port (a continuous/burst drain),
						// keep ready asserted for full throughput.
						//
						// EXCEPTION: a direct held-task producer (StreamLinkMarker) is NOT
						// registered — it presents valid WITH the data and holds it, so the
						// consumer must assert ready on the transfer cycle. Gating it by !valid
						// would deadlock (valid&&ready never coincide) and drop the value.
						State target = transition.getTarget();
						if (target != null && !stateReadsPort(target, port) && !isDirectHeldInput(port)) {
							Var valid = port.getAdditionalInputs().get(0);
							readyValue = ir.and(readyValue, ir.not(valid));
						}
						block.add(ir.createInstStore(ready, readyValue));
					}
				}
			}

			Action defaultAction = createDefaultAction("defaultAction_" + state.getName(), ports);
			actions.add(defaultAction);

			Transition transition = DpnFactory.eINSTANCE.createTransition(state, null);
			transition.setAction(defaultAction);
		}
	}

	/**
	 * Adds combinational assignments to the list of actions in an actor with no FSM.
	 * 
	 * @param actions
	 *            a list of actions
	 */
	private void addCombinationalAssignments(List<Action> actions) {
		Iterable<Port> ports = computeReadyPorts(actions);
		for (Action action : actions) {
			BlockBasic block = action.getCombinational().getLast();

			// Only assert ready for the ports THIS action actually consumes (its
			// inputPattern) — mirrors the FSM path above. Without this guard a
			// CONDITIONAL stream read (`if (cnt == 0) x.read();`) asserted ready in
			// every action, so the actions that don't read the port still advanced
			// its producer and AddBufferedInputs (which keys off the ready-asserted
			// ports) cleared its skid buffer — the port's value was dropped and the
			// next conditional read got a STALE one. A port not in this action's
			// pattern falls through to the default action's ready (assert-while-
			// waiting), so a held value is captured but never double-consumed.
			Set<Port> actionPorts = new LinkedHashSet<>(action.getInputPattern().getPorts());
			for (Port port : ports) {
				if (!actionPorts.contains(port)) {
					continue;
				}
				Var ready = port.getAdditionalOutputs().get(0);
				// Direct held-task RELAY (see the FSM path): a no-FSM loop relay (a single-action
				// loop { out.write(f(in.read())) }) must gate its input ready on its own output slot
				// being free, so backpressure propagates through the chain.
				Expression relayReady = isDirectHeldInput(port) ? directHeldInputReady(action) : null;
				// When stall is null (no stream outputs), always assert ready
				Expression readyValue = relayReady != null ? relayReady
						: (stall != null ? ir.not(stall) : ir.createExprBool(true));
				block.add(ir.createInstStore(ready, readyValue));
			}
		}

		actions.add(createDefaultAction("defaultAction", ports));
	}

	/**
	 * Adds a store to the stall variable at the end of the body of each action that has ready
	 * output ports in its output pattern.
	 * 
	 * @param actions
	 *            a list of actions
	 */
	private void addStallAssignment(List<Action> actions) {
		for (Action action : actions) {
			Expression condition = null;
			for (Port port : dpn.getReadyPorts(action.getOutputPattern().getPorts())) {
				Var ready = port.getAdditionalInputs().get(0);
				condition = ir.or(condition, ir.not(ready));
			}

			// if any of the ready output ports is not ready, stall
			if (condition != null) {
				BlockBasic last = action.getBody().getLast();
				last.add(ir.createInstStore(0, stall, condition));
			}
		}
	}

	@Override
	public Void caseActor(Actor actor) {
		currentActor = actor;
		Iterable<Port> readyInputs = dpn.getReadyPorts(actor.getInputs());
		Iterable<Port> readyOutputs = dpn.getReadyPorts(actor.getOutputs());
		if (isEmpty(readyInputs) && isEmpty(readyOutputs)) {
			return DONE;
		}

		if (!isEmpty(readyOutputs)) {
			// add stall register
			stall = ir.createVar(ir.createTypeBool(), "stall", true);
			actor.getVariables().add(stall);

			updateValidAssignments(readyOutputs);

			// assign stall at the end of the body of each action that writes to ready outputs
			addStallAssignment(actor.getActions());
		}

		// add combinational assignments to ready signals
		FSM fsm = actor.getFsm();
		if (fsm == null) {
			addCombinationalAssignments(actor.getActions());
		} else {
			addCombinationalAssignments(fsm, actor.getActions());
		}

		return DONE;
	}

	private Iterable<Port> computeReadyPorts(Iterable<Action> actions) {
		Set<Port> ports = new LinkedHashSet<>();
		for (Action action : actions) {
			for (Port port : action.getInputPattern().getPorts()) {
				if (port.getInterface().isSyncReady()) {
					ports.add(port);
				}
			}
		}
		return ports;
	}

	private Iterable<Port> computeReadyPorts(State state) {
		if (state == null || state.getOutgoing() == null) {
			return java.util.Collections.emptyList();
		}
		return computeReadyPorts(
				transform(state.getOutgoing(), edge -> edge == null ? null : ((Transition) edge).getAction()));
	}

	/**
	 * Creates a default action.
	 * 
	 * @param name
	 * @param ports
	 * @return
	 */
	private Action createDefaultAction(String name, Iterable<Port> ports) {
		Action dummy = DpnFactory.eINSTANCE.createActionNop();
		dummy.setName(name);
		dummy.getBody().setName(name);
		dummy.getScheduler().setName("isSchedulable_" + name);
		dummy.getCombinational().setName("comb_" + name);

		dummy.getScheduler().getLast().add(ir.createInstReturn(ir.createExprBool(true)));

		BlockBasic block = dummy.getCombinational().getLast();

		for (Port port : ports) {
			Var ready = port.getAdditionalOutputs().get(0);
			Var valid = port.getAdditionalInputs().get(0);
			block.add(ir.createInstStore(ready, ir.and(ir.not(stall), ir.not(valid))));
		}

		return dummy;
	}

	/**
	 * Keeps InstStore(valid, true) as-is for ready output ports. The stall mechanism handles
	 * the case where ready=0: stall is set, and the stall path in SynchronousPrinter keeps
	 * valid=1 until ready goes high.
	 *
	 * Previously this replaced valid=true with valid=ready, which caused a 1-cycle valid glitch
	 * that broke stream FIFOs (consumer saw valid=0 for 1 cycle, lost data).
	 *
	 * @param ports
	 *            ready output ports
	 */
	private void updateValidAssignments(Iterable<Port> ports) {
		// No-op: keep valid <= 1'b1 as generated by the compiler.
		// The stall mechanism (stall <= !ready) handles backpressure.
	}

}
