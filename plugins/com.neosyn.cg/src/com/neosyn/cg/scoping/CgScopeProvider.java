/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import static com.neosyn.cg.CgConstants.DIR_IN;
import static com.neosyn.cg.CgConstants.DIR_OUT;
import static com.neosyn.cg.CgConstants.TYPE_READS;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;
import org.eclipse.xtext.scoping.impl.SimpleScope;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.Connect;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.Statement;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;

/**
 * This class contains custom scoping description.
 * 
 */
public class CgScopeProvider extends AbstractDeclarativeScopeProvider {

	private Iterable<IEObjectDescription> getFieldDescs(final Struct struct, final String varName) {
		// Field names are computed as the full dotted path ("varName.fieldName",
		// "varName.outer.inner") so they line up with each segment of a VarRef
		// like `p.lo` or, for nested structs (Tier 2.3), `outer.lo.a`. The
		// per-segment lookup matches the prefix up to that segment, so emitting
		// every nested path here resolves any depth. Intermediate (struct-typed)
		// fields are emitted too, so `outer.lo` resolves to the inner struct.
		List<IEObjectDescription> descs = new ArrayList<IEObjectDescription>();
		collectFieldDescs(struct, QualifiedName.create(varName),
			new java.util.LinkedHashSet<Struct>(), descs);
		return descs;
	}

	private void collectFieldDescs(Struct struct, QualifiedName prefix,
			java.util.Set<Struct> active, List<IEObjectDescription> out) {
		if (struct == null || !active.add(struct)) {
			return; // null or a cycle — stop descending (errStructCycle rejects it)
		}
		for (Variable field : struct.getFields()) {
			if (field.getName() == null) {
				continue;
			}
			QualifiedName name = prefix.append(field.getName());
			out.add(new EObjectDescription(name, field, null));
			collectFieldDescs(asStruct(CgUtil.getType(field)), name, active, out);
		}
		active.remove(struct);
	}

	/**
	 * Enum literal descriptions for the second segment of a qualified
	 * VarRef like `state_t.IDLE`. Names are computed as "enumName.literal".
	 * See .claude/L2_ENUM_DESIGN.md §5.
	 */
	private Iterable<IEObjectDescription> getLiteralDescs(final com.neosyn.cg.cg.Enum enm,
			final String enumName) {
		return Iterables.transform(
			Iterables.filter(enm.getLiterals(), l -> l.getName() != null),
			new Function<Variable, IEObjectDescription>() {
				@Override
				public IEObjectDescription apply(Variable lit) {
					QualifiedName name = QualifiedName.create(enumName, lit.getName());
					return new EObjectDescription(name, lit, null);
				}
			});
	}

	/**
	 * All enum literals (across every enum) declared on the given entity,
	 * exposed by their bare name. Used to make `s = IDLE` resolve when
	 * `state_t` declares IDLE. Collisions on bare names are user-visible
	 * (use a qualified `state_t.IDLE` to disambiguate).
	 */
	private Iterable<Variable> getEnumLiterals(CgEntity entity) {
		List<Variable> literals = new ArrayList<Variable>();
		for (com.neosyn.cg.cg.Enum enm : entity.getEnums()) {
			for (Variable lit : enm.getLiterals()) {
				if (lit.getName() != null) {
					literals.add(lit);
				}
			}
		}
		return literals;
	}

	/** Delegates to the canonical {@link CgUtil#asStruct(CgType)} resolver. */
	private static Struct asStruct(CgType type) {
		return CgUtil.asStruct(type);
	}

	private Iterable<IEObjectDescription> getPortDescs(final Inst inst, String direction) {
		Iterable<PortDecl> portDecls;
		Task task = inst.getTask();
		if (task == null) {
			Instantiable entity = inst.getEntity();
			if (entity == null) {
				return ImmutableSet.of();
			}
			portDecls = entity.getPortDecls();
		} else {
			portDecls = task.getPortDecls();
		}

		// names are computed as "instance.port"
		// Skip instances or ports with null names (anonymous inline tasks)
		String instName = inst.getName();
		if (instName == null) {
			return ImmutableSet.of();
		}
		Iterable<Variable> ports = CgUtil.getPorts(portDecls, direction);
		return Iterables.transform(
			Iterables.filter(ports, p -> p.getName() != null),
			new Function<Variable, IEObjectDescription>() {
				@Override
				public IEObjectDescription apply(Variable port) {
					QualifiedName name = QualifiedName.create(instName, port.getName());
					return new EObjectDescription(name, port, null);
				}
			});
	}

	private IScope getScope(IScope outer, VarRef ref, String direction) {
		// When Xtext resolves the i-th object in a VarRef, objects[0..i-1] are
		// already resolved. We only reach this branch for objects[1..], so
		// objects[0] is non-proxy when the user wrote `inst.port` or
		// `structVar.field`. After the first dot, the only valid completions
		// / resolutions are the instance's own ports (for an Inst) or the
		// struct's fields (for a struct-typed Variable) — drop `outer` as a
		// fallback so workspace-wide symbols don't leak into proposals.
		Named named = ((InternalEList<Named>) ref.getObjects()).basicGet(0);
		if (!named.eIsProxy()) {
			if (named instanceof Inst) {
				return new SimpleScope(IScope.NULLSCOPE,
					getPortDescs((Inst) named, direction));
			}
			if (named instanceof Variable) {
				Variable var = (Variable) named;
				Struct struct = asStruct(CgUtil.getType(var));
				if (struct != null) {
					return new SimpleScope(IScope.NULLSCOPE,
						getFieldDescs(struct, var.getName()));
				}
			}
			if (named instanceof com.neosyn.cg.cg.Enum) {
				// Qualified literal access: `state_t.IDLE`. Drop outer so workspace
				// symbols don't leak in.
				com.neosyn.cg.cg.Enum enm = (com.neosyn.cg.cg.Enum) named;
				return new SimpleScope(IScope.NULLSCOPE,
					getLiteralDescs(enm, enm.getName()));
			}
		}

		return outer;
	}

	/**
	 * Returns the scope for a variable referenced inside a bundle. Returns the scope of global
	 * variables.
	 */
	public IScope scope_VarRef_objects(Bundle bundle, EReference reference) {
		Iterable<Variable> variables = CgUtil.getVariables(bundle);
		IScope outer = delegateGetScope(bundle, reference);
		// Bare enum literals + enum types (so `IDLE` and `state_t.IDLE` both resolve).
		IScope withEnums = Scopes.scopeFor(getEnumLiterals(bundle),
			Scopes.scopeFor(bundle.getEnums(), outer));
		return Scopes.scopeFor(variables, withEnums);
	}

	/**
	 * Returns the scope for a variable referenced inside a task. Returns the scope of global
	 * variables.
	 */
	public IScope scope_VarRef_objects(Module module, EReference reference) {
		return delegateGetScope(module, reference);
	}

	/**
	 * Returns the scope for a variable referenced inside a network.
	 *
	 * A network's own instances — declared (`x = new Foo()`) or inline
	 * (`x = new task {…}`) — must be resolvable by a VarRef (e.g.
	 * `reads(sourceFrame.speed)`) regardless of whether this resource is the
	 * primary one being linked. Relying on {@link #delegateGetScope} (the
	 * global / imported-namespace index) alone left sibling-instance references
	 * unresolved whenever the network was loaded as a SECONDARY resource — e.g.
	 * project-wide IR generation anchored on another file reported
	 * "sourceFrame cannot be resolved" for a network that resolves cleanly when
	 * it is itself the anchor. Adding the instances to the local scope makes
	 * resolution self-contained and anchor-independent.
	 */
	public IScope scope_VarRef_objects(Network network, EReference reference) {
		IScope outer = delegateGetScope(network, reference);
		return Scopes.scopeFor(network.getInstances(), outer);
	}

	/**
	 * Returns the scope for a variable referenced inside a statement.
	 */
	public IScope scope_VarRef_objects(Statement statement, EReference reference) {
		List<Variable> variables = new ArrayList<Variable>();

		// go up until we find a function, collecting local variables along the way
		EObject cter = statement;
		while (cter != null) {
			EObject last = cter;
			cter = cter.eContainer();

			if (cter instanceof Block) {
				Block block = (Block) cter;
				List<Statement> stmts = block.getStmts();
				int index = ECollections.indexOf(stmts, last, 0);

				// includes the current statement in the scope
				ListIterator<Statement> it = stmts.listIterator(index + 1);
				while (it.hasPrevious()) {
					Statement stmt = it.previous();
					if (stmt instanceof StatementVariable) {
						variables.addAll(((StatementVariable) stmt).getVariables());
					}
				}
			} else if (cter instanceof Variable) {
				// got up to the containing function
				break;
			} else if (cter instanceof StatementLoop) {
				// specific case for a loop, if it declares a variable take it into account
				StatementLoop loop = (StatementLoop) cter;
				Statement init = loop.getInit();
				if (init instanceof StatementVariable) {
					variables.addAll(((StatementVariable) init).getVariables());
				}
			}
		}

		// build scope (from outer to inner)
		IScope outer = getScope(cter, reference);
		if (variables.isEmpty()) {
			return outer;
		}
		return Scopes.scopeFor(variables, outer);
	}

	/**
	 * Returns the scope for a variable referenced inside a statement.
	 */
	public IScope scope_VarRef_objects(StatementLoop loop, EReference reference) {
		IScope outer = scope_VarRef_objects((Statement) loop, reference);

		Statement init = loop.getInit();
		if (init instanceof StatementVariable) {
			StatementVariable stmt = (StatementVariable) init;
			return Scopes.scopeFor(stmt.getVariables(), outer);
		}
		return outer;
	}

	/**
	 * Returns the scope for a variable referenced inside a task. Returns the scope of global
	 * variables.
	 */
	public IScope scope_VarRef_objects(Task task, EReference reference) {
		Iterable<Variable> variables = CgUtil.getVariables(task);
		Iterable<Variable> ports = CgUtil.getPorts(task.getPortDecls());
		IScope outer = delegateGetScope(task, reference);
		// Bare enum literals + enum types (so `IDLE` and `state_t.IDLE` both resolve).
		// Layered outermost so local variables / ports / params shadow on collision.
		IScope withEnums = Scopes.scopeFor(getEnumLiterals(task),
			Scopes.scopeFor(task.getEnums(), outer));
		return Scopes.scopeFor(variables, Scopes.scopeFor(ports, withEnums));
	}

	/**
	 * Returns the scope for a variable referenced inside a function.
	 */
	public IScope scope_VarRef_objects(Variable function, EReference reference) {
		IScope outer = getScope(function.eContainer(), reference);
		return Scopes.scopeFor(function.getParameters(), outer);
	}

	/**
	 * Scope for the member-access continuation of an indexed reference, e.g. the
	 * `.lo` (and `.lo.a`) in `ps[i].lo` / `ps[i].lo.a` for arrays of struct
	 * (Tier 2.3). Each member is resolved against the struct type of the
	 * preceding element: the indexed source-leaf variable for the first member,
	 * or the previously-resolved member's field type otherwise. Members are
	 * resolved left-to-right, so when member i is being linked, members[0..i-1]
	 * are already non-proxy. Names are bare field names (single ID per member).
	 */
	public IScope scope_ExpressionVariable_members(ExpressionVariable ev, EReference reference) {
		@SuppressWarnings("unchecked")
		InternalEList<Named> members = (InternalEList<Named>) ev.getMembers();
		Named lastResolved = null;
		for (int i = 0; i < members.size(); i++) {
			Named m = members.basicGet(i);
			if (m == null || m.eIsProxy()) {
				break;
			}
			lastResolved = m;
		}

		Struct base = null;
		if (lastResolved instanceof Variable) {
			base = asStruct(CgUtil.getType((Variable) lastResolved));
		} else if (ev.getSource() != null) {
			Variable leaf = ev.getSource().getVariable();
			if (leaf != null && !leaf.eIsProxy()) {
				base = asStruct(CgUtil.getType(leaf));
			}
		}
		if (base == null) {
			return IScope.NULLSCOPE;
		}
		return Scopes.scopeFor(Iterables.filter(base.getFields(), f -> f.getName() != null));
	}

	/**
	 * Returns the scope for a variable referenced inside an expression. If used with the
	 * 'available' or 'read' property, returns the scope of input ports. Otherwise, resolves the
	 * scope with the expression's container.
	 */
	public IScope scope_VarRef_objects(VarRef ref, EReference reference) {
		IScope outer = getScope(ref.eContainer(), reference);

		EObject cter = ref.eContainer();
		EStructuralFeature feature = ref.eContainingFeature();
		if (feature == Literals.CONNECT__PORTS) {
			Connect connect = (Connect) cter;
			String direction = TYPE_READS.equals(connect.getType()) ? DIR_OUT : DIR_IN;

			return getScope(outer, ref, direction);
		} else if (feature == Literals.STATEMENT_WRITE__PORT) {
			return getScope(outer, ref, DIR_IN);
		} else /* if (feature == Literals.EXPRESSION_VARIABLE__SOURCE) */ {
			return getScope(outer, ref, null);
		}
	}

}
