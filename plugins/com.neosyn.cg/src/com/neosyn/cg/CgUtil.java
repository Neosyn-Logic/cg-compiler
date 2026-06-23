/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg;

import static com.neosyn.cg.CgConstants.DIR_IN;
import static com.neosyn.cg.cg.CgPackage.Literals.VARIABLE__PARAMETERS;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;
import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.MultiPortDecl;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.services.LoopSwitch;
import com.neosyn.cg.internal.services.ScheduleModifierSwitch;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.util.Void;

/**
 * This class defines utility functions for analysis of Cx.
 * 

 * 
 */
public class CgUtil {

	private static class PortSwitch extends CgSwitch<Void> {

		private List<Variable> variables;

		public PortSwitch() {
			variables = new ArrayList<Variable>();
		}

		@Override
		public Void caseMultiPortDecl(MultiPortDecl decl) {
			return visit(this, decl.getDecls());
		}

		@Override
		public Void casePortDef(PortDef portDef) {
			variables.add(portDef.getVar());
			return DONE;
		}

		@Override
		public Void caseSinglePortDecl(SinglePortDecl decl) {
			return visit(this, decl.getPorts());
		}

		public Iterable<Variable> getVariables() {
			return variables;
		}

	}

	/**
	 * This class defines a switch that visits a function to find out if it has side effects. A side
	 * effect is any action on a port, any cycle modifier, and any assignment to state variables.
	 * 
	
	 * 
	 */
	public static class SideEffectSwitch extends ScheduleModifierSwitch {

		@Override
		public Boolean caseStatementAssign(StatementAssign stmt) {
			ExpressionVariable target = stmt.getTarget();
			Variable variable = getVariable(target.getSource());
			if (isGlobal(variable) && stmt.getOp() != null) {
				// target of this assignment is a state variable => side effect
				return true;
			}

			return super.caseStatementAssign(stmt);
		}

	}

	private static Function<VarDecl, Iterable<Variable>> funVarDecl = new Function<VarDecl, Iterable<Variable>>() {
		@Override
		public Iterable<Variable> apply(VarDecl varDecl) {
			return varDecl.getVariables();
		}
	};

	/**
	 * Puts all variables of the given bundle in the given types map.
	 * 
	 * @param typeMap
	 *            map from name to typedef
	 * @param bundle
	 *            a bundle
	 */
	private static void fillTypesMap(Map<String, Typedef> typeMap, Bundle bundle) {
		Bundle extended = bundle.getExtends();
		if (extended != null) {
			fillTypesMap(typeMap, extended);
		}

		for (Typedef typedef : bundle.getTypes()) {
			typeMap.put(typedef.getName(), typedef);
		}
	}

	/**
	 * Puts all variables of the given bundle in the given variables map.
	 * 
	 * @param varMap
	 *            map from name to variable
	 * @param bundle
	 *            a bundle
	 */
	private static void fillVariablesMap(Map<String, Variable> varMap, Bundle bundle) {
		Bundle extended = bundle.getExtends();
		if (extended != null) {
			fillVariablesMap(varMap, extended);
		}

		for (VarDecl decl : bundle.getDecls()) {
			for (Variable variable : decl.getVariables()) {
				varMap.put(variable.getName(), variable);
			}
		}
	}

	/**
	 * Finds the last port declaration before <code>portDef</code> that satisfies the given
	 * predicate.
	 * 
	 * @param portDef
	 *            a port definition
	 * @param predicate
	 *            a predicate
	 * @return a port definition that satisfies the given predicate
	 */
	private static PortDef find(PortDef portDef, Predicate<PortDef> predicate) {
		SinglePortDecl portDecl = (SinglePortDecl) portDef.eContainer();
		List<PortDef> defs = portDecl.getPorts();

		int index = ECollections.indexOf(defs, portDef, 0);
		ListIterator<PortDef> it = defs.listIterator(index + 1);
		while (it.hasPrevious()) {
			PortDef previous = it.previous();
			if (predicate.apply(previous)) {
				return previous;
			}
		}
		return null;
	}

	/**
	 * Returns the direction of the given port.
	 * 
	 * @param port
	 *            a port variable
	 * @return the direction of the given port
	 */
	public static String getDirection(Variable port) {
		EObject cter = port.eContainer();
		if (cter == null) {
			return null;
		}

		SinglePortDecl decl = (SinglePortDecl) cter.eContainer();
		if (decl == null) {
			return null;
		}
		return decl.getDirection();
	}

	/**
	 * Returns the file name of this module.
	 * 
	 * @param module
	 *            a module
	 * @return the module's file name
	 */
	public static String getFileName(Module module) {
		URI uri = module.eResource().getURI();
		if (uri.isPlatform()) {
			return uri.toPlatformString(true);
		} else {
			return uri.path();
		}
	}

	/**
	 * Returns the function in the given module that has the given name, or <code>null</code>.
	 * 
	 * @param module
	 *            a module
	 * @param name
	 *            name of a function
	 * @return a function, or <code>null</code>
	 */
	public static Variable getFunction(Task task, final String name) {
		return Iterables.find(getFunctions(task), new Predicate<Variable>() {
			@Override
			public boolean apply(Variable variable) {
				return name.equals(variable.getName());
			}
		}, null);
	}

	/**
	 * Returns an iterable of variables from an iterable of port declarations.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @return an iterable of variables that represent ports
	 */
	public static Iterable<Variable> getFunctions(Task task) {
		Iterable<VarDecl> varDecls = task.getDecls();
		return Iterables.filter(Iterables.concat(Iterables.transform(varDecls, funVarDecl)),
				new Predicate<Variable>() {
					@Override
					public boolean apply(Variable variable) {
						return CgUtil.isFunction(variable);
					}
				});
	}

	/**
	 * Returns the interface type (bare, push, stream, confirm) of the given port variable.
	 *
	 * New keywords (preferred):
	 * - push: valid-only, fire-and-forget (maps to SYNC)
	 * - stream: flow control with backpressure (maps to SYNC_READY)
	 * - confirm: delivery confirmation (maps to SYNC_ACK)
	 *
	 * Old keywords (deprecated):
	 * - sync: maps to SYNC (use push instead)
	 * - sync ready: maps to SYNC_READY (use stream instead)
	 * - sync ack: maps to SYNC_ACK (use confirm instead)
	 *
	 * @param port
	 *            a variable that belongs to a port definition
	 * @return an interface type
	 */
	public static InterfaceType getInterface(Variable port) {
		PortDef def = (PortDef) port.eContainer();
		EObject cter = def.eContainer().eContainer();
		MultiPortDecl decl = cter instanceof MultiPortDecl ? (MultiPortDecl) cter : null;

		// NEW: confirm -> SYNC_ACK
		if (def.isConfirm() || def.isConfirmOld() || decl != null && decl.isConfirm()) {
			return InterfaceType.SYNC_ACK;
		}
		// OLD: ack -> SYNC_ACK (deprecated)
		if (def.isAck() || def.isAckOld() || decl != null && decl.isAck()) {
			return InterfaceType.SYNC_ACK;
		}

		// NEW: stream -> SYNC_READY
		if (def.isStream() || def.isStreamOld() || decl != null && decl.isStream()) {
			return InterfaceType.SYNC_READY;
		}
		// OLD: ready -> SYNC_READY (deprecated)
		if (def.isReady() || def.isReadyOld() || decl != null && decl.isReady()) {
			return InterfaceType.SYNC_READY;
		}

		// NEW: push -> SYNC
		if (def.isPush() || def.isPushOld() || decl != null && decl.isPush()) {
			return InterfaceType.SYNC;
		}
		// OLD: sync -> SYNC (deprecated)
		if (def.isSync() || def.isSyncOld() || decl != null && decl.isSync()) {
			return InterfaceType.SYNC;
		}

		// If this port inherits its type from a previous port declaration,
		// also inherit the interface attribute from that previous port.
		// This fixes: "in push u3 reg, r_m;" where r_m should also be push.
		if (port.getType() == null) {
			PortDef syncDef = find(def, new Predicate<PortDef>() {
				@Override
				public boolean apply(PortDef p) {
					// Find the first previous PortDef that has a type and interface
					return p.getVar().getType() != null && hasInterface(p);
				}
			});
			if (syncDef != null) {
				// confirm/ack -> SYNC_ACK
				if (syncDef.isConfirm() || syncDef.isConfirmOld() || syncDef.isAck() || syncDef.isAckOld()) {
					return InterfaceType.SYNC_ACK;
				}
				// stream/ready -> SYNC_READY
				if (syncDef.isStream() || syncDef.isStreamOld() || syncDef.isReady() || syncDef.isReadyOld()) {
					return InterfaceType.SYNC_READY;
				}
				// push/sync -> SYNC
				return InterfaceType.SYNC;
			}
		}

		return InterfaceType.BARE;
	}

	/**
	 * Returns true if the given port definition has any interface modifier.
	 */
	private static boolean hasInterface(PortDef p) {
		return p.isPush() || p.isPushOld() || p.isSync() || p.isSyncOld()
			|| p.isStream() || p.isStreamOld() || p.isReady() || p.isReadyOld()
			|| p.isConfirm() || p.isConfirmOld() || p.isAck() || p.isAckOld();
	}

	/**
	 * Returns an iterable of variables from an iterable of port declarations.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @return an iterable of variables that represent ports
	 */
	public static Iterable<Variable> getPorts(Iterable<PortDecl> portDecls) {
		PortSwitch portSwitch = new PortSwitch();
		visit(portSwitch, portDecls);
		return portSwitch.getVariables();
	}

	/**
	 * Returns all ports that match the given direction.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @param direction
	 *            a direction
	 * @return an iterable of port variables
	 */
	public static Iterable<Variable> getPorts(Iterable<PortDecl> portDecls,
			final String direction) {
		Iterable<Variable> ports = getPorts(portDecls);
		if (direction == null) {
			return ports;
		}

		return Iterables.filter(ports, new Predicate<Variable>() {
			@Override
			public boolean apply(Variable port) {
				return port.getName() != null && direction.equals(getDirection(port));
			}
		});
	}

	/**
	 * Returns the target of the expression, which is defined as the first object in the containment
	 * hierarchy that is not an expression.
	 * 
	 * @param expression
	 *            an expression
	 * @return an EObject
	 */
	public static EObject getTarget(CgExpression expression) {
		EObject result = expression;
		do {
			result = result.eContainer();
		} while (result instanceof CgExpression);
		return result;
	}

	/**
	 * Returns the type of the given variable. If the variable itself does not have a type, and it
	 * is contained in a local or state variable declaration, the type of its container is returned.
	 * Otherwise this method returns <code>null</code> (should never happen).
	 * 
	 * @param variable
	 *            a variable
	 * @return type of the variable
	 */
	public static CgType getType(Variable variable) {
		CgType type = variable.getType();
		if (type != null) {
			// parameters of a function are variables with a type
			return type;
		}

		EObject cter = variable.eContainer();
		if (cter instanceof StatementVariable) {
			return ((StatementVariable) cter).getType();
		} else if (cter instanceof VarDecl) {
			return ((VarDecl) cter).getType();
		} else if (cter instanceof PortDef) {
			PortDef decl = find((PortDef) cter, new Predicate<PortDef>() {
				@Override
				public boolean apply(PortDef PortDef) {
					return PortDef.getVar().getType() != null;
				}
			});

			if (decl != null) {
				return decl.getVar().getType();
			}
		}

		// cter will be null if variable is a proxy
		// happens when an expression/statement references a variable
		// that is not defined in its scope
		return null;
	}

	/**
	 * Instantiation-argument keys reserved by the framework (clock-domain and
	 * reset assignment), e.g. {@code new Foo({clock: "clk_a"})}. These are
	 * consumed by the properties system rather than bound to a {@code const}
	 * parameter, so the generics validator must not flag them as unknown.
	 * Single source of truth — {@code InstantiationContext} references this too.
	 */
	public static final java.util.Set<String> RESERVED_INSTANTIATION_KEYS =
			java.util.Collections.unmodifiableSet(new java.util.HashSet<>(
					java.util.Arrays.asList("clock", "clocks", "reset")));

	/**
	 * If {@code type} is a {@link com.neosyn.cg.cg.TypeRef} resolving directly to
	 * a {@link com.neosyn.cg.cg.Struct}, return that Struct; otherwise null.
	 * Walks one level (no Typedef→Struct chain), which is sufficient for the v1
	 * struct surface (.claude/L1_STRUCT_DESIGN.md §1).
	 *
	 * <p>This is the single canonical struct resolver. Scoping
	 * ({@code CgScopeProvider}), validation ({@code CgValidator}) and IR-gen
	 * ({@code IrBuilder#asStructType}) all delegate here so the
	 * {@code TypeRef → Struct} rule lives in exactly one place.
	 */
	public static com.neosyn.cg.cg.Struct asStruct(CgType type) {
		if (type instanceof com.neosyn.cg.cg.TypeRef) {
			com.neosyn.cg.cg.NamedType typeDef = ((com.neosyn.cg.cg.TypeRef) type).getTypeDef();
			if (typeDef instanceof com.neosyn.cg.cg.Struct) {
				return (com.neosyn.cg.cg.Struct) typeDef;
			}
		}
		return null;
	}

	/**
	 * L2 — computes the effective value of an enum literal (iter #2: honours
	 * explicit `LIT = N` values with C-style "previous + 1" fill for implicit
	 * ones). Returns the literal's value as a BigInteger.
	 *
	 * <p>Walks the parent Enum's literal list in declaration order, evaluating
	 * each explicit value via the instantiator. Implicit literals (no `=
	 * expression`) take the running counter (which advances from previous +
	 * 1, starting at 0).
	 *
	 * <p>Returns {@link BigInteger#ZERO} as a defensive default if the
	 * literal is not a member of the parent Enum (shouldn't happen with a
	 * valid AST). Returns the running counter unchanged if an explicit value
	 * doesn't const-fold to a BigInteger — the validator will surface the
	 * underlying error separately.
	 */
	public static BigInteger getEnumLiteralValue(IInstantiator instantiator, Entity entity,
			Variable literal) {
		EObject parent = literal.eContainer();
		if (!(parent instanceof com.neosyn.cg.cg.Enum)) {
			return BigInteger.ZERO;
		}
		com.neosyn.cg.cg.Enum enm = (com.neosyn.cg.cg.Enum) parent;
		BigInteger next = BigInteger.ZERO;
		for (Variable l : enm.getLiterals()) {
			BigInteger v;
			CgExpression explicit = (CgExpression) l.getValue();
			if (explicit != null) {
				Object eval = instantiator.evaluate(entity, explicit);
				v = (eval instanceof BigInteger) ? (BigInteger) eval : next;
			} else {
				v = next;
			}
			if (l == literal) {
				return v;
			}
			next = v.add(BigInteger.ONE);
		}
		return BigInteger.ZERO;
	}

	/**
	 * Returns the types declared by the given bundle (and any bundle it extends).
	 *
	 * @param bundle
	 *            a bundle
	 * @return an iterable of typedefs
	 */
	public static Iterable<Typedef> getTypes(Bundle bundle) {
		Map<String, Typedef> typeMap = new LinkedHashMap<>();
		fillTypesMap(typeMap, bundle);
		return typeMap.values();
	}

	/**
	 * Returns the variable associated with the given VarRef.
	 * 
	 * @param ref
	 *            a VarRef
	 * @return a Variable
	 */
	public static Variable getVariable(VarRef ref) {
		ListIterator<Named> it = ref.getObjects().listIterator(ref.getObjects().size());
		while (it.hasPrevious()) {
			Named named = it.previous();
			if (named instanceof Variable && !(named.eContainer() instanceof ExpressionVariable)) {
				return (Variable) named;
			}
		}

		return null;
	}

	/**
	 * Returns the variables declared by the given bundle (and any bundle it extends).
	 * 
	 * @param bundle
	 *            a bundle
	 * @return an iterable of variables
	 */
	public static Iterable<Variable> getVariables(Bundle bundle) {
		Map<String, Variable> varMap = new LinkedHashMap<>();
		fillVariablesMap(varMap, bundle);
		return varMap.values();
	}

	/**
	 * Returns the variables declared by the given task.
	 * 
	 * @param task
	 *            a task
	 * @return a list of variables
	 */
	public static List<Variable> getVariables(Task task) {
		List<Variable> variables = new ArrayList<Variable>();
		// L3 Generics iter #2: angle-bracket formal params are implicitly-const
		// entity variables. Exposing them here puts them in the task's VAR_REF
		// scope so `uint<W>` references resolve and the monomorphization engine's
		// name-based value substitution (EntityMapper.setValues) finds them.
		variables.addAll(task.getParams());
		for (VarDecl vars : task.getDecls()) {
			variables.addAll(vars.getVariables());
		}
		return variables;
	}

	/**
	 * Returns true if the given object has side effects (performs reads, writes, or assigns to
	 * state variables).
	 * 
	 * @param object
	 *            an AST node (function, statement, expression)
	 * @return a boolean indicating if the given object has side effects
	 */
	public static boolean hasSideEffects(EObject object) {
		return new SideEffectSwitch().doSwitch(object);
	}

	public static boolean isConstant(Variable variable) {
		EObject cter = variable.eContainer();
		if (cter instanceof StatementVariable) {
			return ((StatementVariable) cter).isConstant();
		} else if (cter instanceof VarDecl) {
			VarDecl stateVars = (VarDecl) cter;
			if (stateVars.isConstant()) {
				return true;
			}

			// variables in bundles/networks are implicitly constant
			// the variable is thus constant unless contained in a task
			Task task = getContainerOfType(stateVars, Task.class);
			return task == null;
		} else {
			// function parameters and generic entity formal params are constant
			EStructuralFeature feature = variable.eContainingFeature();
			return feature == VARIABLE__PARAMETERS
					|| feature == com.neosyn.cg.cg.CgPackage.Literals.INSTANTIABLE__PARAMS;
		}
	}

	/**
	 * Returns true if this variable is a function, i.e. if it has a body.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunction(Variable variable) {
		// Null variable means the cross-ref to the underlying Variable couldn't
		// be resolved (e.g. a partial qualified name in `new arith.gcd.Gcd();`
		// that the scope provider didn't find). Treat as "not a function" so
		// downstream visitors fall through to property handling instead of
		// NPE-ing. The Inst-level diagnostic surfaces the real error.
		return variable != null && variable.getBody() != null;
	}

	/**
	 * Returns true if this variable is a constant function.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunctionConstant(Variable variable) {
		if (isFunction(variable)) {
			return isConstant(variable);
		}
		return false;
	}

	/**
	 * Returns true if this variable is a function and has side-effects.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunctionNotConstant(Variable variable) {
		if (isFunction(variable)) {
			return !isConstant(variable);
		}
		return false;
	}

	/**
	 * Returns true if the variable is global.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the variable is global
	 */
	public static boolean isGlobal(Variable variable) {
		return variable.eContainer() instanceof VarDecl;
	}

	/**
	 * Returns <code>true</code> if the given if statement can be translated as a simple 'if' IR
	 * statement, which is only the case when the statement cannot have any influence on the
	 * schedule.
	 * 
	 * @param stmt
	 *            if statement
	 * @return boolean indicating if the 'if' can be translated in a simple way or not
	 */
	public static boolean isIfSimple(StatementIf stmt) {
		return !new ScheduleModifierSwitch().doSwitch(stmt);
	}

	/**
	 * Returns <code>true</code> if the given port is an input port.
	 * 
	 * @param port
	 *            a port declaration
	 * @return <code>true</code> if the given port is an input port
	 */
	public static boolean isInput(Variable port) {
		return DIR_IN.equals(getDirection(port));
	}

	/**
	 * Returns true if the variable is a local variable declaration.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the variable is local
	 */
	public static boolean isLocal(Variable variable) {
		EObject cter = variable.eContainer();
		return cter instanceof StatementVariable;
	}

	/**
	 * Returns <code>true</code> if the given loop statement can be translated as a simple 'loop' IR
	 * statement, which is only the case when the loop cannot have any influence on the schedule and
	 * has a compile-time known number of iterations.
	 * 
	 * @param stmt
	 *            loop statement
	 * @return boolean indicating if the loop can be translated in a simple way or not
	 */
	public static boolean isLoopSimple(IInstantiator instantiator, Entity entity,
			StatementLoop stmt) {
		return !new LoopSwitch(instantiator, entity).doSwitch(stmt);
	}

	/**
	 * Returns <code>true</code> if a <code>break</code> or <code>continue</code>
	 * appears anywhere in the loop's subtree (including inside nested loops). Such
	 * a loop must be FSM-lowered by the scheduler — which routes break/continue to
	 * the loop exit/header — and must NOT be left to the constant-loop unroller,
	 * which has no lowering for them. Both the scheduler and the cycle detector
	 * treat such a loop as non-simple, and an enclosing <code>if</code> as
	 * multi-cycle, so the lowering runs with a live loop context.
	 */
	public static boolean loopSubtreeHasLoopControl(StatementLoop stmt) {
		for (java.util.Iterator<EObject> it = stmt.eAllContents(); it.hasNext();) {
			EObject o = it.next();
			if (o instanceof com.neosyn.cg.cg.StatementBreak
					|| o instanceof com.neosyn.cg.cg.StatementContinue) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns <code>true</code> if the given variable is a port.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the given variable is a port
	 */
	public static boolean isPort(Variable variable) {
		return variable != null && variable.eContainer() instanceof PortDef;
	}

	public static boolean isVarDecl(Variable variable) {
		return variable.eContainer() instanceof VarDecl;
	}

	/**
	 * Returns true if this function has "void" return type.
	 * 
	 * @param function
	 *            a function
	 * @return true if this function returns void
	 */
	public static boolean isVoid(Variable function) {
		VarDecl decl = (VarDecl) function.eContainer();
		return decl.isVoid();
	}

}
