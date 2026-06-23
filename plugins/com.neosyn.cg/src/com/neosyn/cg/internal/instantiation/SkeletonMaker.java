/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.cg.internal.TransformerUtil.getStartLine;
import static com.neosyn.models.ir.IrFactory.eINSTANCE;
import static com.neosyn.models.ir.util.ValueUtil.isBool;
import static com.neosyn.models.ir.util.ValueUtil.isFloat;
import static com.neosyn.models.ir.util.ValueUtil.isInt;
import static com.neosyn.models.ir.util.ValueUtil.isList;
import static com.neosyn.models.ir.util.ValueUtil.isString;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;

import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.internal.compiler.IrBuilder;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.dpn.util.DpnSwitch;
import com.neosyn.models.ir.ExprList;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.Void;

/**
 * This class creates the skeleton of an IR entity: state variables, ports.
 * 

 *
 */
public class SkeletonMaker extends DpnSwitch<Void> {

	/**
	 * Converts a POJO into an Expression. Special case for strings, a string is converted to a list
	 * of ints.
	 * 
	 * @param value
	 *            a runtime value
	 * @return an IR expression
	 */
	public static Expression getExpression(Object value) {
		if (isBool(value)) {
			return eINSTANCE.createExprBool((Boolean) value);
		} else if (isFloat(value)) {
			return eINSTANCE.createExprFloat((BigDecimal) value);
		} else if (isInt(value)) {
			return eINSTANCE.createExprInt((BigInteger) value);
		} else if (isString(value)) {
			ExprList list = eINSTANCE.createExprList();
			String str = (String) value;
			for (int i = 0; i < str.length(); i++) {
				list.getValue().add(eINSTANCE.createExprInt(str.charAt(i)));
			}
			return list;
		} else if (isList(value)) {
			ExprList list = eINSTANCE.createExprList();
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				list.getValue().add(getExpression(Array.get(value, i)));
			}
			return list;
		} else {
			return null;
		}
	}

	private CgEntity cxEntity;

	@Inject
	private IInstantiator instantiator;

	@Override
	public Void caseActor(Actor actor) {
		Task task = (Task) cxEntity;
		translateTypesAndVars(actor, task.getTypes(), CgUtil.getVariables(task));
		translatePorts(actor, task);
		return DONE;
	}

	@Override
	public Void caseDPN(DPN dpn) {
		Network network = (Network) cxEntity;
		setFileAndLine(dpn, network);
		translateTypesAndVars(dpn, network.getTypes(), Collections.emptyList());
		translatePorts(dpn, network);
		return DONE;
	}

	@Override
	public Void caseUnit(Unit unit) {
		Bundle bundle = (Bundle) cxEntity;
		setFileAndLine(unit, bundle);
		translateTypesAndVars(unit, CgUtil.getTypes(bundle), CgUtil.getVariables(bundle));
		return DONE;
	}

	public void createSkeleton(CgEntity cxEntity, Entity entity) {
		this.cxEntity = cxEntity;
		try {
			doSwitch(entity);
		} finally {
			this.cxEntity = null;
		}
	}

	/**
	 * Sets filename and line number of IR entity from Cx entity.
	 * 
	 * @param entity
	 *            IR entity
	 * @param cxEntity
	 *            Cx entity
	 */
	private void setFileAndLine(Entity entity, CgEntity cxEntity) {
		// set file name
		Module module = EcoreUtil2.getContainerOfType(cxEntity, Module.class);
		String fileName = CgUtil.getFileName(module);
		entity.setFileName(fileName);

		// set line number
		int lineNumber = getStartLine(cxEntity);
		entity.setLineNumber(lineNumber);
	}

	private void transformPort(final Entity entity, final Variable port) {
		InterfaceType ifType = CgUtil.getInterface(port);
		String name = port.getName();
		List<Port> ports = CgUtil.isInput(port) ? entity.getInputs() : entity.getOutputs();
		boolean combinational = ((PortDef) port.eContainer()).isCombinational();

		// Tier 2.2: a struct-typed port fans out to N scalar field ports
		// (`p$lo`, `p$hi`), registered in the struct-port map so port reads /
		// writes lower field-wise. The validator restricts struct ports to the
		// bare interface in v1, so every field port is bare (no per-field
		// handshake) — atomic handshaking is a later iteration.
		Struct struct = IrBuilder.asStructType(port);
		if (struct != null) {
			// Tier 2.3: nested structs flatten recursively to leaf fields, so a
			// field key is the `$`-joined path (`lo$a`), matching the struct
			// locals created in IrBuilder#transformStructLocal.
			// Tier 2.4: for a non-bare interface (push/stream/confirm) only the
			// FIRST field carries the handshake (valid/ready/ack); the remaining
			// fields are bare data. One shared handshake then gates the whole
			// struct atomically. (For bare ports every field is bare, as before.)
			boolean firstField = true;
			for (IrBuilder.StructLeaf leaf : IrBuilder.leafFields(struct)) {
				Type fieldType = instantiator.computeType(entity, leaf.field);
				String fieldName = name + "$" + leaf.path;
				InterfaceType fieldIface = firstField ? ifType : InterfaceType.BARE;
				Port fieldPort = DpnFactory.eINSTANCE.createPort(fieldType, fieldName, fieldIface, ports);
				if (combinational) {
					fieldPort.setSynchronous(false);
				}
				instantiator.putStructPort(entity, port, leaf.path, fieldPort);
				firstField = false;
			}
			return;
		}

		Type type = instantiator.computeType(entity, port);
		Port dpnPort = DpnFactory.eINSTANCE.createPort(type, name, ifType, ports);
		if (combinational) {
			dpnPort.setSynchronous(false);
		}

		instantiator.putMapping(entity, port, dpnPort);
	}

	/**
	 * Translates the given Cx variable into an IR Procedure or Var.
	 * 
	 * @param variable
	 * @return
	 */
	private void transformVariable(Entity entity, Variable variable) {
		int lineNumber = getStartLine(variable);
		Type type = instantiator.computeType(entity, variable);
		String name = variable.getName();

		if (CgUtil.isFunction(variable)) {
			Procedure procedure = eINSTANCE.createProcedure(name, lineNumber, type);
			entity.getProcedures().add(procedure);
			instantiator.putMapping(entity, variable, procedure);
		} else {
			// bug #11: a struct-typed STATE var fans out to persistent leaf field
			// Vars (mirrors the struct-port fan-out in transformPort), registered
			// so `state.field` access resolves like a struct local. No single
			// whole-struct Var is created — struct vars are used field-wise or
			// copied, both of which route through the flattened field Vars.
			Struct struct = IrBuilder.asStructType(variable);
			if (struct != null) {
				transformStructStateVar(entity, variable, struct);
				return;
			}

			boolean assignable = !CgUtil.isConstant(variable);

			// retrieve initial value (may be null)
			Object value = instantiator.evaluate(entity, variable.getValue());
			Expression init = getExpression(value);

			// create var
			Var var = eINSTANCE.createVar(lineNumber, type, name, assignable, init);

			// add to variables list of containing entity
			entity.getVariables().add(var);
			instantiator.putMapping(entity, variable, var);
		}
	}

	/**
	 * Flattens a struct-typed STATE variable into one persistent leaf field Var
	 * per primitive leaf (recursively for nested structs; arrayed per the var's
	 * dimensions). Each leaf is added to the entity's variables and registered
	 * via {@link IInstantiator#putStructStateField} so that {@code ActorBuilder}
	 * can seed its struct-field lookup. Bug #11.
	 */
	private void transformStructStateVar(Entity entity, Variable structVar, Struct struct) {
		int lineNumber = getStartLine(structVar);
		boolean assignable = !CgUtil.isConstant(structVar);
		for (IrBuilder.StructLeaf leaf : IrBuilder.leafFields(struct)) {
			Type elementType = instantiator.computeType(entity, leaf.field);
			Type type = IrBuilder.applyStructArrayDims(instantiator, entity, elementType,
					structVar.getDimensions());
			String fieldName = structVar.getName() + "$" + leaf.path;
			Var var = eINSTANCE.createVar(lineNumber, type, fieldName, assignable, null);
			entity.getVariables().add(var);
			instantiator.putStructStateField(entity, structVar, leaf.path, var);
		}
	}

	private void translatePorts(Entity entity, Instantiable instantiable) {
		// transform ports
		for (Variable variable : CgUtil.getPorts(instantiable.getPortDecls())) {
			transformPort(entity, variable);
		}
	}

	private void translateTypesAndVars(Entity entity, Iterable<Typedef> types,
			Iterable<Variable> variables) {
		DependencySolver solver = new DependencySolver();

		// transform variables and constant functions
		for (Variable variable : variables) {
			if (CgUtil.isConstant(variable) || !CgUtil.isFunction(variable)) {
				solver.add(variable);
			}
		}

		solver.addAll(types);

		solver.computeOrder();

		for (EObject eObject : solver.getObjects()) {
			if (eObject instanceof Variable) {
				transformVariable(entity, (Variable) eObject);
			} else if (eObject instanceof Typedef) {
				Typedef typedef = (Typedef) eObject;
				Type type = instantiator.computeType(entity, typedef.getType());
				instantiator.putMapping(entity, typedef, type);
			}
		}
	}

}
