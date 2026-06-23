/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.cg.CgConstants.DIR_IN;
import static com.neosyn.cg.CgConstants.DIR_OUT;
import static org.eclipse.xtext.validation.ValidationMessageAcceptor.INSIGNIFICANT_INDEX;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.util.TypeUtil;

import com.neosyn.cg.validation.IssueCodes;

/**
 * This class defines a checker for networks.
 * 

 * 
 */

public class NetworkChecker extends Checker {

	private final IInstantiator instantiator;

	public NetworkChecker(ValidationMessageAcceptor acceptor, IInstantiator instantiator) {
		super(acceptor);
		this.instantiator = instantiator;
	}

	/**
	 * Checks the clock domains of the given DPN.
	 * 
	 * @param network
	 *            source network. Used for error reporting.
	 * @param dpn
	 *            DPN
	 */
	private void checkClockDomains(Network network, DPN dpn) {
		JsonArray clocks = dpn.getProperties().getAsJsonArray(PROP_CLOCKS);
		if (clocks.size() < 2) {
			return;
		}

		ClockDomainComputer cdc = new ClockDomainComputer();
		for (Edge edge : dpn.getGraph().getEdges()) {
			Connection connection = (Connection) edge;

			Endpoint source = connection.getSourceEndpoint();
			Endpoint target = connection.getTargetEndpoint();
			if (source.hasInstance() && target.hasInstance()) {
				Instance srcInst = source.getInstance();
				Instance tgtInst = target.getInstance();
				if (cdc.isCombinational(srcInst) || cdc.isCombinational(tgtInst)) {
					continue;
				}

				String sourceClock = cdc.getClockDomain(source);
				String srcClkName;
				if (sourceClock == null) {
					srcClkName = "(unknown source)";
				} else {
					srcClkName = "(clock '" + sourceClock + "')";
				}

				String targetClock = cdc.getClockDomain(target);
				String tgtClkName;
				if (targetClock == null) {
					tgtClkName = "(unknown target)";
				} else {
					tgtClkName = "(clock '" + targetClock + "')";
				}

				if (!Objects.equal(sourceClock, targetClock)) {
					error("Clock domain: illegal crossing from '" + srcInst.getName() + "."
							+ source.getPort().getName() + "' " + srcClkName + " to '"
							+ tgtInst.getName() + "." + target.getPort().getName() + "' "
							+ tgtClkName, network, Literals.NAMED__NAME, INSIGNIFICANT_INDEX);
				}
			}
		}
	}

	/**
	 * Checks the connectivity of the given DPN.
	 * 
	 * @param network
	 *            source network. Used for error reporting.
	 * @param dpn
	 *            DPN
	 */
	private void checkConnectivity(Network network, DPN dpn) {
		for (Variable variable : CgUtil.getPorts(network.getPortDecls(), DIR_OUT)) {
			Port port = instantiator.getMapping(dpn, variable);
			int num = dpn.getNumIncoming(port);
			if (num == 0) {
				error("Connectivity: unconnected output port '" + port.getName() + "'", variable,
						Literals.NAMED__NAME, INSIGNIFICANT_INDEX);
			} else if (num > 1) {
				error("Connectivity: output port '" + port.getName()
						+ "' is connected too many times (expected 1, actual " + num + ")",
						variable, Literals.NAMED__NAME, INSIGNIFICANT_INDEX);
			}
		}

		for (Inst inst : network.getInstances()) {
			Instance instance = instantiator.getMapping(dpn, inst);
			Instantiable entity = inst.getEntity();
			if (entity == null) {
				entity = inst.getTask();
			}

			for (Variable variable : CgUtil.getPorts(entity.getPortDecls(), DIR_IN)) {
				Port port = instantiator.getMapping(instance.getEntity(), variable);
				int num = dpn.getNumIncoming(instance, port);

				EObject source = inst.getTask() == null ? inst : variable;

				if (num == 0) {
					error("Connectivity: unconnected input port '" + port.getName() + "'", source,
							Literals.NAMED__NAME, INSIGNIFICANT_INDEX);
				} else if (num > 1) {
					error("Connectivity: input port '" + port.getName()
							+ "' is connected too many times (expected 1, actual " + num + ")",
							source, Literals.NAMED__NAME, INSIGNIFICANT_INDEX);
				}
			}
		}
	}

	/**
	 * Checks that every connection wires two ports of the same data width. The
	 * DPN is already monomorphized, so a generic-width port (e.g.
	 * {@code out uint<W> o}) carries its concrete per-instance type here. A
	 * mismatch is reported rather than silently zero-extended/truncated by the
	 * bytecode sim and Verilog backend. See .claude/L3_GENERICS_DESIGN.md §3.4.
	 *
	 * @param network
	 *            source network. Used for error reporting.
	 * @param dpn
	 *            DPN
	 */
	private void checkPortWidths(Network network, DPN dpn) {
		for (Edge edge : dpn.getGraph().getEdges()) {
			Connection connection = (Connection) edge;

			Endpoint source = connection.getSourceEndpoint();
			Endpoint target = connection.getTargetEndpoint();
			Port srcPort = source.getPort();
			Port tgtPort = target.getPort();
			if (srcPort == null || tgtPort == null) {
				continue;
			}

			Type srcType = srcPort.getType();
			Type tgtType = tgtPort.getType();
			if (srcType == null || tgtType == null) {
				continue;
			}

			int srcWidth = TypeUtil.getSize(srcType);
			int tgtWidth = TypeUtil.getSize(tgtType);
			if (srcWidth != tgtWidth) {
				error("Connection: width mismatch from '" + endpointName(source) + "' ("
						+ srcWidth + "-bit) to '" + endpointName(target) + "' (" + tgtWidth
						+ "-bit). Connected ports must have the same width.", network,
						Literals.NAMED__NAME, IssueCodes.ERR_PORT_WIDTH_MISMATCH);
			}
		}
	}

	private static String endpointName(Endpoint endpoint) {
		if (endpoint.hasInstance()) {
			return endpoint.getInstance().getName() + "." + endpoint.getPort().getName();
		}
		return endpoint.getPort().getName();
	}

	public void checkDPN(Network network, DPN dpn) {
		checkConnectivity(network, dpn);
		checkPortWidths(network, dpn);
		checkClockDomains(network, dpn);
	}

}
