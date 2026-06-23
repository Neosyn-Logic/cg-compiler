/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import static com.neosyn.cg.CgConstants.PROP_AVAILABLE;
import static com.neosyn.cg.CgConstants.PROP_READY;
import static com.neosyn.cg.internal.TransformerUtil.getStartLine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Enter;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Leave;
import com.neosyn.cg.cg.StatementGoto;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Goto;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Var;

/**
 * This class transforms AST statements into IR instructions and/or nodes at the actor level.
 * 

 * 
 */
public class ActorTransformer extends FunctionTransformer {

	private Deque<Integer> lines;

	/**
	 * Creates a new actor transformer with the given actor.
	 * 
	 * @param actor
	 *            actor
	 */
	public ActorTransformer(IInstantiator instantiator, Actor actor) {
		super(new ActorBuilder(instantiator, actor));
		lines = new ArrayDeque<>();
	}

	@Override
	public EObject caseEnter(Enter enter) {
		Variable function = enter.getFunction().getVariable();
		int lineNumber = enter.getLineNumber();
		getBuilder().updateLineInfo(lineNumber);

		// all transitions will now correspond to this line
		if (getBuilder().line == null) {
			getBuilder().line = lineNumber;
		} else {
			lines.addFirst(getBuilder().line);
		}

		// transform arguments
		List<CgExpression> arguments = enter.getParameters();
		Iterator<CgExpression> it = arguments.iterator();
		for (Variable variable : function.getParameters()) {
			Var var = builder.transformLocal(variable);
			builder.getProcedure().getLocals().add(var);

			CgExpression value = it.next();
			builder.storeExpr(getBuilder().line, var, null, value);
		}

		// no need to include void function
		// this has been done by the cycle scheduler
		return null;
	}

	@Override
	public Expression caseExpressionVariable(ExpressionVariable expression) {
		VarRef ref = expression.getSource();
		Variable variable = ref.getVariable();
		if (!CgUtil.isPort(variable)) {
			return super.caseExpressionVariable(expression);
		}

		int lineNumber = getStartLine(expression);

		// translate expression to load from port
		Port port = getBuilder().getPort(ref);
		if (port == null) {
			throw new RuntimeException("Port not found for variable: " + variable.getName()
				+ " (ref objects: " + ref.getObjects().size() + ")");
		}
		String prop = expression.getPropertyName();
		Var source, target;
		if (PROP_AVAILABLE.equals(prop) || PROP_READY.equals(prop)) {
			source = port.getAdditionalInputs().get(0);

			String targetName = "is_" + variable.getName() + "_" + prop;
			target = builder.createLocal(lineNumber, ir.createTypeBool(), targetName);
		} else {
			source = port;
			target = builder.createLocal(lineNumber, port.getType(), variable.getName());
		}

		builder.add(ir.createInstLoad(lineNumber, target, source));
		return ir.createExprVar(target);
	}

	@Override
	public EObject caseLeave(Leave leave) {
		// restore previous line behavior
		getBuilder().line = lines.pollFirst();
		return null;
	}

	@Override
	public EObject caseStatementGoto(StatementGoto stmt) {
		Goto gotoInstr = DpnFactory.eINSTANCE.createGoto();
		gotoInstr.setTarget(stmt.getTarget());
		builder.add(gotoInstr);

		return null;
	}

	@Override
	public EObject caseStatementIdle(StatementIdle idle) {
		hookBefore(idle);
		return null;
	}

	@Override
	public EObject caseStatementWrite(StatementWrite write) {
		// check if this write should be ignored (if it is translated as part of the scheduler)
		if (getBuilder().ignoreWrites()) {
			return null;
		}

		hookBefore(write);

		// Tier 2.2: writing a whole struct to a struct port lowers field-wise.
		java.util.Map<String, Port> portFields = builder.getStructPortFields(write.getPort());
		if (portFields != null) {
			Variable srcVar = IrBuilder.asWholeStructValue(write.getValue());
			Struct struct = srcVar != null ? IrBuilder.asStructType(srcVar) : null;
			if (struct != null) {
				builder.storeStructToPort(getStartLine(write), portFields, srcVar, struct);
				return null;
			}
		}

		Port port = getBuilder().getPort(write.getPort());
		if (port == null) {
			VarRef writePort = write.getPort();
			String portDesc = writePort != null && writePort.getObjects() != null && !writePort.getObjects().isEmpty()
				? writePort.getObjects().get(writePort.getObjects().size() - 1).getName()
				: "unknown";
			throw new RuntimeException("Port not found for write: " + portDesc);
		}
		Expression expr = builder.transformExpr(write.getValue(), port.getType());
		InstStore store = ir.createInstStore(getStartLine(write), port, expr);
		builder.add(store);

		// for additional output signals
		for (Var signal : port.getAdditionalOutputs()) {
			store = ir.createInstStore(getStartLine(write), signal, ir.createExprBool(true));
			builder.add(store);
		}

		return null;
	}

	private ActorBuilder getBuilder() {
		return (ActorBuilder) builder;
	}

	@Override
	protected void hookBefore(EObject eObject) {
		int lineNumber = getStartLine(eObject);
		getBuilder().updateLineInfo(lineNumber);
	}

	public void visit() {
		ActorBuilder builder = getBuilder();
		FSM fsm = builder.getActor().getFsm();
		for (Transition transition : fsm.getTransitions()) {
			builder.visitTransition(transition);
		}
	}

}
