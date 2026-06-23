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
 * This class transforms an actor to add combinational assignments to "ready" signals, and updates
 * port references for
 * 
 * @author Synflow team (now Neosyn)
 *
 */
public class AddReadyAssignments extends Transformation {

	private static final DpnFactory dpn = DpnFactory.eINSTANCE;

	private static final IrFactory ir = IrFactory.eINSTANCE;

	private Var stall;

	public AddReadyAssignments() {
		super(null);
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
						State target = transition.getTarget();
						if (target != null && !stateReadsPort(target, port)) {
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

			for (Port port : ports) {
				Var ready = port.getAdditionalOutputs().get(0);
				// When stall is null (no stream outputs), always assert ready
				Expression readyValue = stall != null ? ir.not(stall) : ir.createExprBool(true);
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
