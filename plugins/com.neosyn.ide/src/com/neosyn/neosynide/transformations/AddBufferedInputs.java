/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.neosyn.models.ir.OpBinary.LOGIC_OR;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.common.collect.Iterables;
import com.neosyn.core.transformations.Transformation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * Gives each ready-handshaked input port a 1-deep skid buffer so a consumer can hold a
 * value that arrived while it was stalled. Every read of such a port is rewritten to
 * {@code internal_port_valid ? internal_port : port} (the buffered copy when present,
 * else the live wire), and each action clears {@code internal_port_valid} for exactly
 * the ports it consumes (see {@link #caseActor}). The buffer register itself and its
 * load are emitted by the Verilog {@code SynchronousPrinter}; this pass only rewrites
 * the IR references and the valid-clears.
 */
public class AddBufferedInputs extends Transformation {

	private static final DpnFactory dpn = DpnFactory.eINSTANCE;

	private static final IrFactory ir = IrFactory.eINSTANCE;

	private Actor actor;

	private Map<Var, Var> internalSignals;

	private List<Port> ports;

	public AddBufferedInputs() {
		super(null);
		internalSignals = new HashMap<>();
	}

	@Override
	public Void caseActor(Actor actor) {
		Iterable<Port> readyOutputs = dpn.getReadyPorts(actor.getOutputs());
		if (Iterables.isEmpty(readyOutputs)) {
			return DONE;
		}

		// compute buffered inputs
		// use naive assumption that all ready input ports need to be buffered
		Iterable<Port> readyInputs = dpn.getReadyPorts(actor.getInputs());
		Iterables.addAll(actor.getBufferedInputs(), readyInputs);

		// set port visitor's actor field
		this.actor = actor;
		this.ports = actor.getBufferedInputs();
		for (Port port : actor.getBufferedInputs()) {
			visitUses(port);
			for (Var var : port.getAdditionalInputs()) {
				if (var == port.getLast()) {
					// TRAPS T58: the frame boundary is DATA, buffered with its value --
					// `internal_valid ? internal_last : last`, never `internal_last || last`
					visitLastUses(port);
				} else {
					visitUses(var);
				}
			}
			// declared even when nothing reads the value (`tok.read();` discards it): both HDL
			// printers always capture it, and an undeclared register did not compile (TRAPS T162)
			getInternal(port);
			if (port.getLast() != null) {
				// declared even when nothing reads `.last`: the printer always captures it
				getInternal(port.getLast());
			}
		}

		// update references to buffered ready input ports
		// Clear internal_valid for exactly the ports THIS action consumes a token
		// from — otherwise the 1-deep skid buffer keeps re-serving the last byte.
		// Two ways an action consumes a port:
		//   (a) it reads the value into its body (a Use of the port/valid signal).
		//       IfDeveloper creates actions with an empty inputPattern that still
		//       read ports this way, so a body-Use scan is required.
		//   (b) it asserts the port's `ready` in its combinational (added earlier by
		//       AddReadyAssignments) but DISCARDS the value — e.g. `frameIn.read;`
		//       in FrameEcho's dest-address discard loop. There is no body Use, yet
		//       the handshake advances the producer, so the buffer must be cleared
		//       too or the next action reads a stale byte (the FrameEcho src/dest
		//       off-by-one). This is a subset of inputPattern, so it will NOT clear
		//       ports the action merely lists but does not consume (the BusController
		//       eu_addr_valid regression that ruled out plain inputPattern clearing).
		for (Action action : actor.getActions()) {
			List<Port> usedPorts = getBufferedPortsUsedIn(action.getBody(), ports);
			List<Port> pattern = action.getInputPattern() == null ? new ArrayList<>()
					: action.getInputPattern().getPorts();
			for (Port port : getReadyAssertedPorts(action.getCombinational(), ports)) {
				// Only a port in THIS action's pattern is consumed. The default action also
				// stores `ready = !stall && !valid` -- back-pressure, ready only while the buffer
				// is EMPTY -- and clearing the buffer there threw away a held value whenever the
				// default action ran instead of the reader, e.g. while a relay waited for its
				// output to be taken (TRAPS T142).
				if (!pattern.contains(port)) {
					continue;
				}
				if (!usedPorts.contains(port)) {
					usedPorts.add(port);
				}
			}
			// The skid-buffer clear is emitted into the action's SYNC body; an action
			// with no body (e.g. a purely combinational one) has nowhere to place it.
			if (!usedPorts.isEmpty() && action.getBody() != null) {
				resetInternalValid(action.getBody(), usedPorts);
			}
		}

		return DONE;
	}

	@Override
	public Void caseEntity(Entity entity) {
		return DONE;
	}

	/**
	 * Returns a new expression <code>internal_port_valid ? internal_port : expr</code> where
	 * <code>expr</code> is an ExprVar of the port.
	 * 
	 * @param valid
	 * @param port
	 * @param expr
	 * @return
	 */
	private Expression createExprPort(Var valid, Var port, Expression expr) {
		return ir.createExprTernary(getInternal(valid), getInternal(port), expr);
	}

	/**
	 * Returns the internal signal for the given port or port additional input. Creates it if it
	 * does not exist yet.
	 * 
	 * @param var
	 *            a port or a port additional input
	 * @return an expression referencing the internal signal
	 */
	private Expression getInternal(Var var) {
		Var internal = internalSignals.get(var);
		if (internal == null) {
			internal = ir.createVar(var.getType(), "internal_" + var.getName(), true);
			actor.getVariables().add(internal);
			internalSignals.put(var, internal);
		}
		return ir.createExprVar(internal);
	}

	/**
	 * Returns the subset of buffered ports that are actually referenced (used) in the given
	 * procedure. A port is considered "used" if the procedure contains a Use of the port itself
	 * or of its {@code last} (the frame boundary travels with the value).
	 *
	 * <p>NOT a Use of its valid signal alone (TRAPS T149): {@code more = din.available;} PEEKS, and
	 * clearing the skid buffer there threw the value away -- UDP_Send's make_packet waited with
	 * {@code while (start && !din.available) {}}, the exit action cleared the buffer holding the
	 * first payload byte, and every UDP reply went out empty in the simulator, which had already
	 * taken that byte from the producer. (In the HDL the producer was still holding it, by luck.)
	 */
	private List<Port> getBufferedPortsUsedIn(Procedure body, List<Port> bufferedPorts) {
		if (body == null) {
			return new ArrayList<>();
		}

		// Collect all Vars referenced by Uses in this procedure
		Set<Var> referencedVars = new HashSet<>();
		for (Use use : EcoreHelper.getObjects(body, Use.class)) {
			Var var = use.getVariable();
			if (var != null) {
				referencedVars.add(var);
			}
		}

		// Find which buffered ports are referenced
		List<Port> usedPorts = new ArrayList<>();
		for (Port port : bufferedPorts) {
			if (referencedVars.contains(port)) {
				usedPorts.add(port);
				continue;
			}
			if (port.getLast() != null && referencedVars.contains(port.getLast())) {
				usedPorts.add(port);
			}
		}
		return usedPorts;
	}

	/**
	 * Returns the subset of buffered ports whose {@code ready} signal is asserted (assigned) in the
	 * given combinational procedure. AddReadyAssignments — which runs before this transformation —
	 * emits a store to a port's ready var exactly when the action consumes a token from that port,
	 * including reads whose value is discarded (e.g. {@code frameIn.read;}). Such a consumed port
	 * has no body {@link Use}, so it must be picked up here to have its skid buffer cleared.
	 *
	 * @param combinational
	 *            an action's combinational procedure (may be {@code null})
	 * @param bufferedPorts
	 *            the actor's buffered input ports
	 * @return the buffered ports this action asserts ready for
	 */
	private List<Port> getReadyAssertedPorts(Procedure combinational, List<Port> bufferedPorts) {
		if (combinational == null) {
			return new ArrayList<>();
		}

		// Collect all Vars assigned (defined) in the combinational procedure.
		Set<Var> assignedVars = new HashSet<>();
		for (Def def : EcoreHelper.getObjects(combinational, Def.class)) {
			Var var = def.getVariable();
			if (var != null) {
				assignedVars.add(var);
			}
		}

		List<Port> readyPorts = new ArrayList<>();
		for (Port port : bufferedPorts) {
			// The ready signal is the port's first additional output (see AddReadyAssignments).
			List<Var> readyOutputs = port.getAdditionalOutputs();
			if (!readyOutputs.isEmpty() && assignedVars.contains(readyOutputs.get(0))) {
				readyPorts.add(port);
			}
		}
		return readyPorts;
	}

	/**
	 * Resets the internal "valid" flag for all given buffered input ports in the given action body.
	 * 
	 * @param body
	 *            body of an action
	 * @param inputs
	 *            list of ports
	 */
	public void resetInternalValid(Procedure body, List<Port> inputs) {
		BlockBasic block = body.getLast();

		for (Port port : inputs) {
			if (ports.contains(port)) {
				Var valid = internalSignals.get(port.getAdditionalInputs().get(0));
				block.add(ir.createInstStore(0, valid, ir.createExprBool(false)));
				// Note: do NOT clear the original port's valid flag (source_dout_valid).
				// Input ports are wires in Verilog and cannot be assigned.
				// The ready/valid protocol ensures the producer deasserts valid
				// after seeing ready — no manual clearing needed.
			}
		}
	}

	/**
	 * Replaces each use of the given buffered input <code>port</code> by a ternary expression of
	 * the form: <code>internal_port_valid ? internal_port : port</code>.
	 * 
	 * @param port
	 */
	private void visitUses(Port port) {
		for (Use use : new ArrayList<>(port.getUses())) {
			if (!(use.eContainer() instanceof Expression)) {
				continue;
			}
			Var valid = port.getAdditionalInputs().get(0);
			Expression expr = (Expression) use.eContainer();

			EObject cter = expr.eContainer();
			if (cter == null) {
				// expr was detached from the IR tree by an earlier transform
				// (multi-cycle if-conversion / wide-multiply lowering in a
				// streaming-reduction actor) but its Use back-reference lingered.
				// A detached expression is never emitted, so there is nothing to
				// rewrite — skip it. Mirrors the identical guard in
				// visitUses(Var): eContainingFeature() is null exactly when
				// eContainer() is null, so an unguarded feature.isMany() below
				// NPE'd. This bit a 3-input-stream reduction actor (two inputs
				// feeding a `*`, a third read conditionally) — check-clean, IR
				// serialized fine, but this codegen transform crashed both the
				// bytecode and Verilog backends on the null containing feature.
				continue;
			}
			EStructuralFeature feature = expr.eContainingFeature();
			if (feature.isMany()) {
				// list (happens rarely, only in function calls)
				List<Expression> list = EcoreHelper.getContainingList(expr);
				int index = list.indexOf(expr);
				list.add(index, createExprPort(valid, port, expr));
			} else {
				// scalar (most common case)
				cter.eSet(feature, createExprPort(valid, port, expr));
			}
		}
	}

	/**
	 * Replaces each use of a framed port's {@code last} by
	 * {@code internal_valid ? internal_last : last}: when the skid buffer holds a value, the
	 * boundary that came with THAT value (TRAPS T58).
	 */
	private void visitLastUses(Port port) {
		Var last = port.getLast();
		Var valid = port.getAdditionalInputs().get(0);
		for (Use use : new ArrayList<>(last.getUses())) {
			if (use.eContainer() instanceof InstLoad) {
				// `l = din.last` is still a LOAD where no SSA ran on the bodies (the bytecode
				// pipeline); the HDL pipelines have made it an expression by now. Read it from
				// the skid buffer too, or a held value gets the boundary of the NEXT value on the
				// wire.
				InstLoad load = (InstLoad) use.eContainer();
				if (load.getIndexes().isEmpty() && load.getTarget() != null) {
					InstAssign assign = ir.createInstAssign(load.getTarget().getVariable(),
							createExprPort(valid, last, ir.createExprVar(last)));
					assign.setLineNumber(load.getLineNumber());
					EcoreUtil.replace(load, assign);
				}
				continue;
			}
			if (!(use.eContainer() instanceof Expression)) {
				continue;
			}
			Expression expr = (Expression) use.eContainer();
			EObject cter = expr.eContainer();
			if (cter == null) {
				continue; // detached, never emitted (see visitUses(Var))
			}
			EStructuralFeature feature = expr.eContainingFeature();
			if (feature.isMany()) {
				List<Expression> list = EcoreHelper.getContainingList(expr);
				list.add(list.indexOf(expr), createExprPort(valid, last, expr));
			} else {
				cter.eSet(feature, createExprPort(valid, last, expr));
			}
		}
	}

	private void visitUses(Var var) {
		for (Use use : new ArrayList<>(var.getUses())) {
			if (use.eContainer() instanceof InstLoad) {
				// TRAPS T149: `more = din.available` is still a LOAD where no SSA ran on the
				// bodies (the bytecode pipeline), as visitLastUses found for `last`. Left as a
				// load of the port's valid it said "nothing" while the skid buffer held the value
				// (moved there, and the port's valid cleared, at the top of the cycle): UDP_Send's
				// make_packet then sent every reply with an empty payload, in the simulator only.
				InstLoad load = (InstLoad) use.eContainer();
				if (load.getIndexes().isEmpty() && load.getTarget() != null) {
					InstAssign assign = ir.createInstAssign(load.getTarget().getVariable(),
							ir.createExprBinary(getInternal(var), LOGIC_OR, ir.createExprVar(var)));
					assign.setLineNumber(load.getLineNumber());
					EcoreUtil.replace(load, assign);
				}
				continue;
			}
			if (!(use.eContainer() instanceof Expression)) {
				continue;
			}
			Expression expr = (Expression) use.eContainer();
			EObject cter = expr.eContainer();
			if (cter == null) {
				// expr was detached from the IR tree by an earlier transform but
				// its Use back-reference lingered. A detached expression is never
				// emitted, so there is nothing to rewrite — skip it (mirrors the
				// scalar path in visitUses(Port)). This is what bit FrameEcho /
				// AppendCRC: an unguarded cter.eSet NPE'd here and aborted the
				// whole entity's HDL emit, so no .v was produced.
				continue;
			}
			EStructuralFeature feature = expr.eContainingFeature();
			if (feature.isMany()) {
				// list (rare — only inside function calls); mirror visitUses(Port)
				List<Expression> list = EcoreHelper.getContainingList(expr);
				int index = list.indexOf(expr);
				list.add(index, ir.createExprBinary(getInternal(var), LOGIC_OR, expr));
			} else {
				cter.eSet(feature, ir.createExprBinary(getInternal(var), LOGIC_OR, expr));
			}
		}
	}

}
