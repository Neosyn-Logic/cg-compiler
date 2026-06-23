/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.cg.CgConstants.TYPE_READS;
import static com.neosyn.models.dpn.DpnFactory.eINSTANCE;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.neosyn.cg.cg.Connect;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.ErrorMarker;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.util.TypePrinter;

/**
 * This class defines an helper class that creates Connections.
 * 

 * 
 */
public class ExplicitConnector {

	private DPN dpn;

	@Inject
	private ConnectorHelper helper;

	@Inject
	private IInstantiator instantiator;

	/**
	 * A map whose keys are Instance or DPN, and whose values are ports that can be written to
	 * (input ports for instance, output ports for DPN).
	 */
	private Multimap<EObject, Port> portMap;

	private void addError(ErrorMarker marker) {
		Network network = (Network) marker.getSource().eContainer();
		network.getErrors().add(marker);
	}

	/**
	 * Checks the assignment from <code>typeSrc</code> to <code>typeTgt</code>.
	 * 
	 * @param sourceText
	 * @param typeSrc
	 * @param typeTgt
	 * @param source
	 * @param feature
	 * @param index
	 */
	private void checkAssign(String sourceText, Type typeSrc, Type typeTgt, Connect source,
			EStructuralFeature feature, int index) {
		if (typeSrc == null || typeTgt == null) {
			return;
		}

		if (!EcoreUtil.equals(typeSrc, typeTgt)) {
			addError(new ErrorMarker("Type mismatch: cannot convert " + sourceText + " from "
					+ new TypePrinter().toString(typeSrc) + " to "
					+ new TypePrinter().toString(typeTgt), source, feature, index));
		}
	}

	private void checkPortAssociation(Connect connect, int index, VarRef ref, Port sourcePort,
			Port targetPort) {
		// get port types
		Type srcType = sourcePort.getType();
		Type tgtType = targetPort.getType();

		// check assign
		INode node = NodeModelUtils.getNode(ref);
		String srcName = "'" + NodeModelUtils.getTokenText(node) + "'";
		checkAssign(srcName, srcType, tgtType, connect, Literals.CONNECT__PORTS, index);

		// check ports have the same interface type
		if (sourcePort.getInterface() != targetPort.getInterface()) {
			addError(new ErrorMarker("Port mismatch: incompatible interface type between "
					+ srcName + " and '" + targetPort.getName() + "'", connect,
					Literals.CONNECT__PORTS, index));
		}
	}

	public void connect(Multimap<EObject, Port> portMap, Network network, DPN dpn) {
		this.portMap = portMap;
		this.dpn = dpn;

		for (Connect connect : network.getConnects()) {
			makeConnection(connect);
		}

		this.dpn = null;
		this.portMap = null;
	}

	/**
	 * Translates the connect statement. By definition, "target" refers to the dpn/instance that
	 * appears on the left of the .reads/writes. "target port" refers to a port that belongs to that
	 * target.
	 * 
	 * @param connect
	 */
	private void makeConnection(Connect connect) {
		ConnectionInfo info = new ConnectionInfo(instantiator, portMap, dpn, connect);

		Iterator<Port> it = info.iterator();
		List<Port> targetPorts = new ArrayList<>();
		int index = 0;
		for (VarRef ref : connect.getPorts()) {
			if (index >= info.getNumPorts()) {
				if (TYPE_READS.equals(connect.getType())) {
					String kind = connect.isThis() ? "output" : "input";
					addError(new ErrorMarker("Connectivity: no more ports available, all " + kind
							+ " ports of '" + info.getName() + "' are already connected", connect));
				} else {
					addError(new ErrorMarker("Connectivity: too many ports given to '"
							+ info.getName() + ".writes', expected at most " + info.getNumPorts()
							+ ", got " + connect.getPorts().size(), connect));
				}
				break;
			}

			Port targetPort = it.next();
			targetPorts.add(targetPort);

			Endpoint targetEndpoint;
			if (connect.isThis()) {
				targetEndpoint = new Endpoint(dpn, targetPort);
			} else {
				Instance instance = info.getInstance();
				if (instance == null) {
					continue;
				}
				targetEndpoint = new Endpoint(instance, targetPort);
			}

			Endpoint sourceEndpoint = helper.getEndpoint(dpn, ref);
			if (sourceEndpoint == null) {
				continue;
			}

			// removes sourcePort from portMap
			// this is done for any combination of this/instance and reads/writes
			// for ports other than (instance, input) and (dpn, output) this is a no-op
			if (sourceEndpoint.hasInstance()) {
				portMap.remove(sourceEndpoint.getInstance(), sourceEndpoint.getPort());
			} else {
				portMap.remove(dpn, sourceEndpoint.getPort());
			}

			checkPortAssociation(connect, index, ref, sourceEndpoint.getPort(), targetPort);

			if (TYPE_READS.equals(connect.getType())) {
				dpn.getGraph().add(eINSTANCE.createConnection(sourceEndpoint, targetEndpoint));
			} else {
				dpn.getGraph().add(eINSTANCE.createConnection(targetEndpoint, sourceEndpoint));
			}

			index++;
		}

		// removes target ports accessed by "reads"
		if (TYPE_READS.equals(connect.getType())) {
			EObject key = connect.isThis() ? dpn : info.getInstance();
			for (Port port : targetPorts) {
				portMap.remove(key, port);
			}
		}
	}

}
