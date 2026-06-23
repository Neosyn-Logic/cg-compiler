/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.cg.CgUtil.isPort;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.IrBuilder;
import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Direction;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.util.BuiltinPortTypeResolver;
import com.neosyn.models.util.Void;

/**
 * This class visits and replaces references to implicit ports by actual ports.
 * 

 * 
 */
public class ImplicitConnector extends VoidCxSwitch {

	private DPN dpn;

	@Inject
	private ConnectorHelper helper;

	private Instance instance;

	@Inject
	private IInstantiator instantiator;

	/**
	 * A map whose keys are Instance or DPN, and whose values are ports that can be written to
	 * (input ports for instance, output ports for DPN).
	 */
	private Multimap<EObject, Port> portMap;

	@Override
	public Void caseExpressionVariable(ExpressionVariable expr) {
		VarRef ref = expr.getSource();
		if (isPort(ref.getVariable())) {
			visitPort(ref);
		}

		return super.caseExpressionVariable(expr);
	}

	@Override
	public Void caseInst(Inst inst) {
		instance = instantiator.getMapping(dpn, inst);
		visit(this, inst.getTask());
		instance = null;
		return DONE;
	}

	@Override
	public Void caseStatementWrite(StatementWrite stmt) {
		// visit value first
		super.caseStatementWrite(stmt);

		visitPort(stmt.getPort());
		return DONE;
	}

	@Override
	public Void caseTask(Task task) {
		// must implement caseTask because it is not in VoidCxSwitch
		return visit(this, CgUtil.getVariables(task));
	}

	/**
	 * Connects the given network.
	 * 
	 * @param portMap
	 * @param network
	 * @param dpn
	 */
	public void connect(Multimap<EObject, Port> portMap, Network network, DPN dpn) {
		this.portMap = portMap;
		this.dpn = dpn;

		visit(this, network.getInstances());

		this.dpn = null;
		this.portMap = null;
	}

	/**
	 * Creates a new port from the given parameters, and adds a connection to the DPN associated
	 * with the network containing the instance.
	 * 
	 * @param link
	 *            link
	 * @param instance
	 *            an instance
	 * @param otherPort
	 *            IR port
	 * @param ref
	 *            reference to the port
	 * @return a new IR port
	 */
	private Port getConnectedPort(String link, Instance instance, Endpoint otherEndPoint) {
		boolean isDpnPort = !otherEndPoint.hasInstance();

		// create connection
		DPN dpn = instance.getDPN();
		Port otherPort = otherEndPoint.getPort();
		boolean isOtherInput = otherPort.getDirection() == Direction.INPUT;

		// compute otherEndPoint and portName
		String portName;
		if (isDpnPort) {
			// this port is defined in this instance or containing netwok
			portName = dpn.getSimpleName() + "_" + otherPort.getName();
		} else {
			// this port is defined by another instance
			portName = otherEndPoint.getInstance().getName() + "_" + otherPort.getName();

			// if this is an input port, remove it from the port map
			if (isOtherInput) {
				portMap.remove(otherEndPoint.getInstance(), otherPort);
			}
		}

		// create port with the right direction (same if in DPN, reversed if in other instance)
		boolean isThisInput = isDpnPort ? isOtherInput : !isOtherInput;
		Entity entity = instance.getEntity();

		// Resolve parameterized port types for built-in entities
		// Uses BuiltinPortTypeResolver (single source of truth)
		Type portType = otherPort.getType();
		if (!isDpnPort && portType instanceof TypeInt && ((TypeInt) portType).getSize() == -1) {
			portType = BuiltinPortTypeResolver.resolvePortType(otherEndPoint.getInstance(), otherPort);
		}

		Port thisPort = DpnFactory.eINSTANCE.createPort(portType, portName,
				otherPort.getInterface(), isThisInput ? entity.getInputs() : entity.getOutputs());

		// add connection to graph
		Connection conn;
		Endpoint thisEndPoint = new Endpoint(instance, thisPort);
		if (thisPort.getDirection() == Direction.INPUT) {
			conn = DpnFactory.eINSTANCE.createConnection(otherEndPoint, thisEndPoint);
		} else {
			conn = DpnFactory.eINSTANCE.createConnection(thisEndPoint, otherEndPoint);
		}
		dpn.getGraph().add(conn);

		return thisPort;
	}

	private void visitPort(VarRef ref) {
		Variable cxPort = ref.getVariable();

		// Tier 2.2: a struct-typed port is flattened to N scalar field ports.
		// Wire each field port (cross-task), or no-op if owned by this entity.
		Struct struct = IrBuilder.asStructType(cxPort);
		if (struct != null) {
			visitStructPort(ref, cxPort, struct);
			return;
		}

		Port port = instantiator.getMapping(instance.getEntity(), cxPort);
		if (port == null) {
			// reference is to another instance's port
			INode node = NodeModelUtils.getNode(ref);
			final String link = NodeModelUtils.getTokenText(node);

			Endpoint otherEndpoint = helper.getEndpoint(dpn, ref);
			if (otherEndpoint == null) {
				// may happen if link refers to a non-existent port
				return;
			}

			port = instantiator.getMapping(instance.getEntity(), otherEndpoint);
			if (port == null) {
				// we add a port to this entity and connect it to the other instance
				port = getConnectedPort(link, instance, otherEndpoint);
				instantiator.putMapping(instance.getEntity(), otherEndpoint, port);
			}
		}

		instantiator.putMapping(instance.getEntity(), ref, port);
	}

	/**
	 * Tier 2.2 — wires a reference to a struct-typed port. If the port is owned
	 * by this entity it is already flattened (no-op). For a cross-task
	 * reference ({@code processor.out_pair}) this creates one local field port
	 * per struct field on the referencing entity, connected to the producer's
	 * matching field port, and registers them under the port variable so the
	 * read/write lowering resolves them via {@code getStructPortFields}.
	 */
	private void visitStructPort(VarRef ref, Variable cxPort, Struct struct) {
		Entity thisEntity = instance.getEntity();

		// owned by this entity, or already wired on a previous visit of this ref
		if (instantiator.getStructPortFields(thisEntity, ref) != null) {
			return;
		}

		if (ref.getObjects().size() < 2 || !(ref.getObjects().get(0) instanceof Inst)) {
			return;
		}
		Inst inst = (Inst) ref.getObjects().get(0);
		Instance otherInstance = instantiator.getMapping(dpn, inst);
		if (otherInstance == null) {
			return;
		}
		java.util.Map<String, Port> producerFields =
				instantiator.getStructPortFields(otherInstance.getEntity(), ref);
		if (producerFields == null) {
			return;
		}

		INode node = NodeModelUtils.getNode(ref);
		String link = node == null ? cxPort.getName() : NodeModelUtils.getTokenText(node);
		for (IrBuilder.StructLeaf leaf : IrBuilder.leafFields(struct)) {
			Port otherFieldPort = producerFields.get(leaf.path);
			if (otherFieldPort == null) {
				continue;
			}
			Endpoint otherEndpoint = new Endpoint(otherInstance, otherFieldPort);
			Port thisFieldPort = getConnectedPort(link, instance, otherEndpoint);
			instantiator.putStructPort(thisEntity, cxPort, leaf.path, thisFieldPort);
		}
	}

}
