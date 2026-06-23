/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.cg.CgConstants.PROP_AVAILABLE;
import static com.neosyn.cg.CgConstants.PROP_READ;
import static com.neosyn.cg.CgConstants.PROP_READY;
import static com.neosyn.cg.validation.IssueCodes.ERR_LOCAL_NOT_INITIALIZED;
import static com.neosyn.cg.validation.IssueCodes.ERR_MULTIPLE_READS;
import static com.neosyn.cg.validation.IssueCodes.ERR_NO_SIDE_EFFECTS;
import static com.neosyn.cg.validation.IssueCodes.WARN_CROSS_INSTANCE_STATE_ACCESS;
import static com.neosyn.models.util.SwitchUtil.check;
import static org.eclipse.xtext.validation.CheckType.NORMAL;

import java.util.List;

import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.services.BoolCxSwitch;
import com.neosyn.models.dpn.Direction;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.util.Executable;

/**
 * This class defines a validator for Cx expressions.
 * 

 * 
 */
public class ExpressionValidator extends AbstractDeclarativeValidator {

	/**
	 * This class defines a visitor that returns true when a function called is not constant.
	 * 
	
	 * 
	 */
	private static class ConstantCallSwitch extends BoolCxSwitch {

		@Override
		public Boolean caseExpressionVariable(ExpressionVariable expr) {
			Variable variable = expr.getSource().getVariable();
			if (CgUtil.isFunctionNotConstant(variable)) {
				return true;
			}

			return super.caseExpressionVariable(expr);
		}

	}

	@Inject
	private IInstantiator instantiator;

	@Check
	public void checkBreak(com.neosyn.cg.cg.StatementBreak stmt) {
		if (!isInsideLoop(stmt)) {
			error("'break' can only appear inside a loop", stmt, null,
					com.neosyn.cg.validation.IssueCodes.ERR_LOOP_CTRL_OUTSIDE_LOOP);
		}
	}

	@Check
	public void checkContinue(com.neosyn.cg.cg.StatementContinue stmt) {
		if (!isInsideLoop(stmt)) {
			error("'continue' can only appear inside a loop", stmt, null,
					com.neosyn.cg.validation.IssueCodes.ERR_LOOP_CTRL_OUTSIDE_LOOP);
		}
	}

	/** Walks the containment chain for an enclosing {@code for}/{@code while} loop. */
	private static boolean isInsideLoop(org.eclipse.emf.ecore.EObject stmt) {
		for (org.eclipse.emf.ecore.EObject c = stmt.eContainer(); c != null; c = c.eContainer()) {
			if (c instanceof StatementLoop) {
				return true;
			}
		}
		return false;
	}

	@Check
	public void checkCondition(Branch stmt) {
		checkFunctionCalls(stmt.getCondition());
	}

	@Check
	public void checkCondition(StatementLoop stmt) {
		checkFunctionCalls(stmt.getCondition());
	}

	private void checkFunctionCalls(CgExpression condition) {
		if (check(new ConstantCallSwitch(), condition)) {
			error("Scheduling: this expression cannot call functions with side effects", condition,
					null, ERR_NO_SIDE_EFFECTS);
		}
	}

	@Check
	public void checkLocalVariableUse(ExpressionVariable expr) {
		Variable variable = expr.getSource().getVariable();
		if (!CgUtil.isLocal(variable)) {
			return;
		}

		boolean isArray = !variable.getDimensions().isEmpty();
		if (!isArray && !variable.isInitialized()) {
			error("The local variable '" + variable.getName() + "' may not have been initialized",
					expr, Literals.EXPRESSION_VARIABLE__SOURCE, ERR_LOCAL_NOT_INITIALIZED);
		}
	}

	@Check(NORMAL)
	public void checkMultipleReads(final CgExpression expr) {
		// Checks that there are at most one read per port in the expression. Otherwise indicate an
		// error.
		Task task = EcoreUtil2.getContainerOfType(expr, Task.class);
		if (task == null) {
			return;
		}

		instantiator.forEachMapping(task, new Executable<Entity>() {
			@Override
			public void exec(Entity entity) {
				Multiset<Port> portsRead = LinkedHashMultiset.create();
				Multiset<Port> portsAvailable = LinkedHashMultiset.create();
				computePortSets(entity, portsAvailable, portsRead, expr);

				boolean hasMultipleReads = false;
				for (Entry<Port> entry : portsRead.entrySet()) {
					hasMultipleReads |= entry.getCount() > 1;
				}

				if (hasMultipleReads) {
					error("Port error: cannot have more than one read per port in expression", expr,
							null, ERR_MULTIPLE_READS);
				}
			}
		});
	}

	@Check(NORMAL)
	public void checkPort(final ExpressionVariable expr) {
		Variable variable = expr.getSource().getVariable();
		if (CgUtil.isPort(variable)) {
			Task task = EcoreUtil2.getContainerOfType(expr, Task.class);
			if (task == null) {
				return;
			}

			// checks that the given reference to a port variable has the proper semantics.
			instantiator.forEachMapping(task, new Executable<Entity>() {
				@Override
				public void exec(Entity entity) {
					Port port = instantiator.getPort(entity, expr.getSource());
					if (port != null) {
						checkPortExpression(expr, port);
					}
				}
			});
		}
	}

	@Check(NORMAL)
	public void checkCrossInstanceStateAccess(ExpressionVariable expr) {
		VarRef ref = expr.getSource();
		List<Named> objects = ref.getObjects();
		if (objects.size() < 2) {
			return;
		}

		// Check if first element is an instance and last element is a non-port variable
		Named first = objects.get(0);
		if (!(first instanceof Inst)) {
			return;
		}

		Variable variable = ref.getVariable();
		if (variable == null || CgUtil.isPort(variable)) {
			return;
		}

		// This is a cross-instance state variable access (e.g., bus.read_count)
		// Not supported in bytecode simulation - produces empty IR actions
		String instanceName = first.getName();
		String varName = variable.getName();
		warning("Cross-instance state variable access '" + instanceName + "." + varName
				+ "' is not supported in simulation. "
				+ "Use ports to communicate between instances instead.",
				expr, Literals.EXPRESSION_VARIABLE__SOURCE, WARN_CROSS_INSTANCE_STATE_ACCESS);
	}

	/**
	 * Checks the given expression that refers to an input port.
	 *
	 * @param expr
	 *            an expression variable
	 */
	private void checkPortExpression(ExpressionVariable expr, Port port) {
		String prop = expr.getPropertyName();
		InterfaceType iface = port.getInterface();
		if (PROP_AVAILABLE.equals(prop)) {
			if (port.getDirection() == Direction.OUTPUT || !iface.isSync()) {
				error("Port error: '" + PROP_AVAILABLE
						+ "' can only be used on 'sync' and 'ready' input ports", expr, null);
			}
		} else if (PROP_READ.equals(prop)) {
			if (port.getDirection() == Direction.OUTPUT) {
				error("Port error: '" + PROP_READ + "' can only be used on input ports", expr,
						null);
			}
		} else if (PROP_READY.equals(prop)) {
			if (port.getDirection() == Direction.INPUT || !iface.isSyncReady()) {
				error("Port error: '" + PROP_READY + "' can only be used on 'ready' output ports",
						expr, null);
			}
		} else if (port.getDirection() == Direction.OUTPUT) {
			error("Port error: an output port can only be accessed with write", expr, null);
		}

		if (!expr.getIndexes().isEmpty()) {
			error("Port error: an input port cannot be used with indexes", expr, null);
		}

		if (!expr.getParameters().isEmpty()) {
			error("Port error: the '" + prop + "' function does not accept arguments", expr, null);
		}
	}

	@Check(NORMAL)
	public void checkWrite(StatementWrite stmt) {
		Variable variable = stmt.getPort().getVariable();
		if (CgUtil.isPort(variable)) {
			Task task = EcoreUtil2.getContainerOfType(stmt, Task.class);

			// checks that the given reference to a port variable has the proper semantics.
			instantiator.forEachMapping(task, new Executable<Entity>() {
				@Override
				public void exec(Entity entity) {
					Port port = instantiator.getPort(entity, stmt.getPort());
					if (port != null && port.getDirection() != Direction.OUTPUT) {
						error("Port error: only output ports can only be accessed with write", stmt,
								null);
					}
				}
			});
		} else {
			error("Port error: only ports can be written.", stmt, null);
		}
	}

	/**
	 * Computes the two port sets: one containing ports that are available, the other one containing
	 * ports that are read.
	 * 
	 * @param available
	 *            a set in which ports available are put
	 * @param read
	 *            a set in which ports read are put
	 * @param condition
	 *            the condition to visit
	 */
	public void computePortSets(Entity entity, Multiset<Port> available, Multiset<Port> read,
			CgExpression condition) {
		List<ExpressionVariable> exprs;
		if (condition == null) {
			return;
		}

		exprs = EcoreUtil2.eAllOfType(condition, ExpressionVariable.class);
		for (ExpressionVariable expr : exprs) {
			VarRef ref = expr.getSource();
			Variable variable = ref.getVariable();
			if (CgUtil.isPort(variable)) {
				Port port = instantiator.getPort(entity, ref);
				String prop = expr.getPropertyName();
				if (PROP_AVAILABLE.equals(prop)) {
					available.add(port);
				} else if (PROP_READ.equals(prop)) {
					read.add(port);
				}
			}
		}
	}

	@Override
	public void register(EValidatorRegistrar registrar) {
		// do nothing: packages are already registered by CgJavaValidator
	}

	@Check
	public void setInitialized(StatementAssign stmt) {
		if (stmt.getValue() == null) {
			return;
		}
		VarRef ref = stmt.getTarget().getSource();
		Variable variable = ref.getVariable();

		// set variable as defined if the assignment has a value
		// MUST NOT USE variable.setDefined EVER BECAUSE IT WILL CLEAR THE INSTANTIATOR'S CACHE
		// don't use setInitialized(value != null)
		// because once a value has been defined, it must not be un-defined
		if (CgUtil.isLocal(variable)) {
			setInitialized(variable);
		}

		// A struct field write (`p.lo = ...`) initializes the base struct local
		// `p` — getVariable() resolves a multi-segment ref to the field, not the
		// base, so mark the base explicitly. Enables Tier 2.1 whole-struct
		// reads/copies of a field-initialized struct.
		if (ref.getObjects().size() >= 2) {
			Named base = ref.getObjects().get(0);
			if (base instanceof Variable && CgUtil.isLocal((Variable) base)) {
				setInitialized((Variable) base);
			}
		}
	}

	@Check
	public void setInitialized(StatementVariable stmt) {
		// set the 'defined' flag for each variable that has a value
		for (Variable variable : stmt.getVariables()) {
			if (variable.getValue() != null) {
				// MUST NOT USE variable.setDefined EVER
				setInitialized(variable);
			}
		}
	}

	/**
	 * Sets the 'initialized' field of variable to <code>true</code> without notifying adapters.
	 * Necessary so that resources stay cached.
	 * 
	 * @param variable
	 *            a variable
	 */
	private void setInitialized(Variable variable) {
		boolean deliver = variable.eDeliver();
		variable.eSetDeliver(false);
		variable.setInitialized(true);
		variable.eSetDeliver(deliver);
	}

}
