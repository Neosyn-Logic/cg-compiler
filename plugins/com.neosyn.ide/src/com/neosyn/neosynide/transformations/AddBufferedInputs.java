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

import com.google.common.collect.Iterables;
import com.neosyn.core.transformations.Transformation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * This class updates references to ports and additional signals to use generated wire signals
 * instead.
 * 

 *
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
				visitUses(var);
			}
		}

		// update references to buffered ready input ports
		// Only clear valid for ports actually referenced in each action's body.
		// IfDeveloper creates actions with empty inputPattern that still read ports,
		// but we must NOT clear valid for ports that this action doesn't touch,
		// or other actions won't see the valid flag when they need it.
		for (Action action : actor.getActions()) {
			List<Port> usedPorts = getBufferedPortsUsedIn(action.getBody(), ports);
			if (!usedPorts.isEmpty()) {
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
	private Expression createExprPort(Var valid, Port port, Expression expr) {
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
	 * or any of its additional inputs (e.g., the valid signal).
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
			// Also check additional inputs (valid signal)
			for (Var additionalInput : port.getAdditionalInputs()) {
				if (referencedVars.contains(additionalInput)) {
					usedPorts.add(port);
					break;
				}
			}
		}
		return usedPorts;
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

			EStructuralFeature feature = expr.eContainingFeature();
			if (feature.isMany()) {
				// list (happens rarely, only in function calls)
				List<Expression> list = EcoreHelper.getContainingList(expr);
				int index = list.indexOf(expr);
				list.add(index, createExprPort(valid, port, expr));
			} else {
				// scalar (most common case)
				EObject cter = expr.eContainer();
				cter.eSet(feature, createExprPort(valid, port, expr));
			}
		}
	}

	private void visitUses(Var var) {
		for (Use use : new ArrayList<>(var.getUses())) {
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
