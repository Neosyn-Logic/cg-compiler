/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.validation;

import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_LOOP_DEPRECATED;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static com.neosyn.cg.CgConstants.NAME_SETUP_DEPRECATED;
import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;
import static org.eclipse.xtext.validation.CheckType.NORMAL;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.util.Triple;
import org.eclipse.xtext.util.Tuples;
import org.eclipse.xtext.validation.Check;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.neosyn.core.NeosynCore;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.Import;
import com.neosyn.cg.cg.Imported;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionUnary;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.ErrorMarker;
import com.neosyn.cg.internal.scheduler.CycleDetector;
import com.neosyn.cg.internal.validation.NetworkChecker;
import com.neosyn.cg.internal.validation.TypeChecker;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.util.Executable;

/**
 * This class defines a validator for Cx source files.
 * 

 * 
 */
public class CgValidator extends AbstractCgValidator {

	/**
	 * stores already-registered issues (copy/paste from Xtext). Useful when checking specialized
	 * code, so that any specific error shows up for only one configuration.
	 */
	private Set<Triple<EObject, EStructuralFeature, String>> accepted;

	@Inject
	private IInstantiator instantiator;

	@Override
	public void acceptError(String message, EObject object, EStructuralFeature feature, int index,
			String code, String... issueData) {
		if (accepted.add(Tuples.create(object, feature, message))) {
			super.acceptError(message, object, feature, index, code, issueData);
		}
	}

	@Override
	public void acceptInfo(String message, EObject object, EStructuralFeature feature, int index,
			String code, String... issueData) {
		if (accepted.add(Tuples.create(object, feature, message))) {
			super.acceptInfo(message, object, feature, index, code, issueData);
		}
	}

	@Override
	public void acceptWarning(String message, EObject object, EStructuralFeature feature, int index,
			String code, String... issueData) {
		if (accepted.add(Tuples.create(object, feature, message))) {
			super.acceptWarning(message, object, feature, index, code, issueData);
		}
	}

	/** Delegates to the canonical {@link CgUtil#asStruct(CgType)} resolver. */
	private static Struct asStruct(CgType type) {
		return CgUtil.asStruct(type);
	}

	/**
	 * L3 Generics iter #1 — validate the arguments of a named instantiation
	 * {@code new Foo({K: v})}. Each key must name a {@code const} parameter of
	 * the target task, or a reserved framework key
	 * ({@link CgUtil#RESERVED_INSTANTIATION_KEYS}: clock / clocks / reset). An
	 * unknown key is silently dropped by the instantiator today, so a typo like
	 * {@code {Wdith: 16}} quietly uses the default — flag it. Only tasks declare
	 * const parameters; a network has none. See .claude/L3_GENERICS_DESIGN.md.
	 */
	@Check
	public void checkInstantiationArguments(Inst inst) {
		Instantiable entity = inst.getEntity();
		Obj args = inst.getArguments();
		if (entity == null || entity.eIsProxy()) {
			return; // anonymous task or unresolved entity
		}

		// iter #2: positional type-args `new Foo<a, b>()` must not outnumber the
		// formal params. Fewer is fine (the rest keep their defaults); more have
		// nothing to bind to.
		int nFormals = entity.getParams().size();
		if (inst.getTypeArgs().size() > nFormals) {
			error("Too many type arguments: '" + entity.getName() + "' declares "
					+ nFormals + " parameter(s) but " + inst.getTypeArgs().size()
					+ " were given.", inst, Literals.INST__TYPE_ARGS,
					IssueCodes.ERR_GENERIC_TYPEARG_ARITY);
		}

		if (args == null) {
			return; // no named arguments to check
		}

		// Index the target's entity-level variables by name. iter #2 angle-bracket
		// formals live in getParams() (any Instantiable); body consts live in
		// getDecls() (only Task). Both are nameable in `new Foo({K: v})`.
		Map<String, Variable> vars = new HashMap<>();
		for (Variable v : entity.getParams()) {
			if (v.getName() != null) {
				vars.put(v.getName(), v);
			}
		}
		if (entity instanceof Task) {
			for (VarDecl decl : ((Task) entity).getDecls()) {
				for (Variable v : decl.getVariables()) {
					if (v.getName() != null) {
						vars.put(v.getName(), v);
					}
				}
			}
		}

		for (Pair pair : args.getMembers()) {
			String key = pair.getKey();
			if (key == null || CgUtil.RESERVED_INSTANTIATION_KEYS.contains(key)) {
				continue; // reserved framework key (clock / clocks / reset)
			}
			Variable variable = vars.get(key);
			if (variable == null) {
				error("'" + key + "' is not a parameter of '" + entity.getName()
						+ "'. Instantiation arguments must name a const declared in the"
						+ " task (or a reserved key: clock, clocks, reset).", pair,
						Literals.PAIR__KEY, IssueCodes.ERR_GENERIC_ARG_UNKNOWN);
			} else if (!CgUtil.isConstant(variable)) {
				error("'" + key + "' is not a const and cannot be set at"
						+ " instantiation.", pair, Literals.PAIR__KEY,
						IssueCodes.ERR_GENERIC_ARG_NOT_CONST);
			}
		}
	}

	/**
	 * Tier 2.3 — nested struct fields are supported (they flatten recursively to
	 * leaf primitives). The only remaining hazard is a recursive definition: a
	 * struct that contains itself directly or transitively would flatten
	 * infinitely, so reject it with a clear error on the offending field.
	 */
	@Check
	public void checkStructNotRecursive(Struct struct) {
		for (Variable field : struct.getFields()) {
			Struct fieldStruct = asStruct(field.getType());
			if (fieldStruct == null) {
				continue;
			}
			if (fieldStruct == struct
					|| structReaches(fieldStruct, struct, new java.util.LinkedHashSet<Struct>())) {
				error("Recursive struct definition: field '" + field.getName() + "' makes '"
						+ struct.getName() + "' contain itself directly or transitively.",
						field, Literals.VARIABLE__TYPE, -1, IssueCodes.ERR_STRUCT_CYCLE);
			}
		}
	}

	/** True if {@code from} can reach {@code target} by descending struct-typed
	 *  fields. {@code visited} guards against cycles among unrelated structs. */
	private static boolean structReaches(Struct from, Struct target, java.util.Set<Struct> visited) {
		if (from == target) {
			return true;
		}
		if (!visited.add(from)) {
			return false;
		}
		for (Variable field : from.getFields()) {
			Struct next = asStruct(field.getType());
			if (next != null && structReaches(next, target, visited)) {
				return true;
			}
		}
		return false;
	}

	// Tier 2.4: struct-typed ports now support non-bare interfaces
	// (push/stream/confirm). The first flattened field carries the shared
	// handshake (valid/ready/ack) and the rest are bare data — see
	// SkeletonMaker#transformPort — so the prior bare-only sentinel
	// (errStructAsPortType) has been retired.

	/**
	 * L1 sentinel — whole-struct values may only flow through a same-typed
	 * whole-struct copy in v1 (Tier 2.1: {@code Pair p = other;} and
	 * {@code p2 = p;}). Flag any other use of a one-segment struct-typed
	 * reference (port write, return, function argument, etc.). Field access
	 * (`p.lo`) is two segments and remains valid. Declarations without an
	 * initializer (`Pair p;`) use VarDecl/StateVar, not ExpressionVariable,
	 * so they are unaffected. See L1_STRUCT_DESIGN.md §6.
	 */
	@Check
	public void checkStructWholeValueUse(ExpressionVariable expr) {
		VarRef source = expr.getSource();
		if (source == null || source.getObjects().isEmpty()) {
			return;
		}
		// Tier 2.3: `ps[i].lo` / `ps[i].lo.a` — member-access continuation after
		// an index. The value is the LAST member: a leaf primitive (fine) or a
		// whole sub-struct (`ps[i].lo`, lo : Inner) which v1 rejects. This takes
		// precedence over the objects-based checks below because the leaf object
		// (`ps`) is a struct but the actual value is the member.
		if (!expr.getMembers().isEmpty()) {
			Named lastMember = expr.getMembers().get(expr.getMembers().size() - 1);
			if (lastMember instanceof Variable && !lastMember.eIsProxy()
					&& asStruct(CgUtil.getType((Variable) lastMember)) != null) {
				error("Whole sub-struct values are not supported in v1; access individual "
						+ "leaf fields with `.fieldName`.",
						expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
						IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
			}
			return;
		}
		// Tier 2.3: a ref rooted at a struct local/state var whose last segment
		// is itself a struct-typed field (`outer.lo`, lo : Inner) is a whole
		// sub-struct value. Leaf-field access (`outer.lo.a`) is fine; whole-OUTER
		// copy goes through the one-segment path below. Sub-struct values are not
		// supported in v1 — reject cleanly rather than miscompile. The root must
		// be a struct variable so instance.port refs (`processor.out_pair`) are
		// untouched.
		if (source.getObjects().size() > 1) {
			Named root = source.getObjects().get(0);
			Named last = source.getObjects().get(source.getObjects().size() - 1);
			boolean rootIsStructVar = root instanceof Variable && !root.eIsProxy()
					&& asStruct(CgUtil.getType((Variable) root)) != null;
			if (rootIsStructVar && last instanceof Variable && !last.eIsProxy()
					&& asStruct(CgUtil.getType((Variable) last)) != null) {
				error("Whole sub-struct values are not supported in v1; access individual "
						+ "leaf fields with `.fieldName`.",
						expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
						IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
			}
			return;
		}
		Named named = source.getObjects().get(0);
		if (named == null || named.eIsProxy() || !(named instanceof Variable)) {
			return;
		}
		Variable var = (Variable) named;
		if (asStruct(CgUtil.getType(var)) == null) {
			return;
		}

		// Tier 2.3: a struct-array element used as a whole value (`ps[i]`,
		// `Pair p = ps[i];`) is not supported in v1 — access its fields with
		// `ps[i].fieldName`. (Member access `ps[i].lo` returned above.)
		if (!expr.getIndexes().isEmpty()) {
			error("Whole struct-array elements are not supported as values in v1; "
					+ "access fields with `[i].fieldName`.",
					expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
					IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
			return;
		}

		// Tier 2.1: a whole-struct copy between same-typed structs is supported.
		// `expr` may be either side of the copy — the value (`... = expr`) or
		// the assignment target (`expr = ...`, which is itself an
		// ExpressionVariable). Classify the enclosing statement and compare the
		// two sides' struct types.
		EObject container = expr.eContainer();
		Struct targetStruct = null;
		Struct valueStruct = null;
		boolean inCopy = false;

		if (container instanceof Variable && ((Variable) container).getValue() == expr) {
			// `T dst = expr;` — struct-typed local/state initializer.
			inCopy = true;
			targetStruct = asStruct(CgUtil.getType((Variable) container));
			valueStruct = asStruct(CgUtil.getType(var));
		} else if (container instanceof StatementAssign) {
			StatementAssign assign = (StatementAssign) container;
			Variable targetVar = isWholeStructAssignTarget(assign) ? structVarOf(
					assign.getTarget().getSource()) : null;
			Variable valueVar = assign.getValue() instanceof ExpressionVariable
					? structVarOf(((ExpressionVariable) assign.getValue()).getSource())
					: null;
			if (targetVar != null && valueVar != null
					&& (assign.getTarget() == expr || assign.getValue() == expr)) {
				inCopy = true;
				targetStruct = asStruct(CgUtil.getType(targetVar));
				valueStruct = asStruct(CgUtil.getType(valueVar));
			}
		}

		if (inCopy) {
			if (targetStruct != null && targetStruct == valueStruct) {
				return; // supported same-type whole-struct copy
			}
			error("Cannot copy a whole struct between different struct types.",
					expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
					IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
			return;
		}

		// Tier 2.2: `port.write(p)` to a struct port of the same type is
		// supported (lowers field-wise). `expr` is the write value `p`.
		if (container instanceof StatementWrite) {
			StatementWrite w = (StatementWrite) container;
			Variable portVar = w.getValue() == expr && w.getPort() != null
					? w.getPort().getVariable() : null;
			Struct portStruct = portVar != null ? asStruct(CgUtil.getType(portVar)) : null;
			if (portStruct != null) {
				if (portStruct != asStruct(CgUtil.getType(var))) {
					error("Cannot write a whole struct to a port of a different "
							+ "struct type.",
							expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
							IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
				}
				return; // supported same-type whole-struct port write
			}
		}

		error("Whole-struct values are only supported in a whole-struct copy "
				+ "(`q = p;`) or struct-port read/write in v1; elsewhere, access "
				+ "individual fields with `.fieldName`.",
				expr, Literals.EXPRESSION_VARIABLE__SOURCE, -1,
				IssueCodes.ERR_STRUCT_WHOLE_VALUE_USE);
	}

	/**
	 * True if {@code assign} is a plain {@code =} assignment whose target is a
	 * one-segment, un-indexed reference — the shape of a whole-struct
	 * reassignment ({@code p2 = p;}). Mirrors the guard in
	 * FunctionTransformer#caseStatementAssign so validator and generator agree.
	 */
	private static boolean isWholeStructAssignTarget(StatementAssign assign) {
		if (!"=".equals(assign.getOp()) || assign.getTarget() == null
				|| assign.getTarget().getSource() == null) {
			return false;
		}
		return assign.getTarget().getSource().getObjects().size() == 1
				&& (assign.getTarget().getIndexes() == null
						|| assign.getTarget().getIndexes().isEmpty());
	}

	/** The one-segment {@link Variable} a VarRef points at, or null. */
	private static Variable structVarOf(VarRef ref) {
		if (ref == null || ref.getObjects().size() != 1) {
			return null;
		}
		Named n = ref.getObjects().get(0);
		if (!(n instanceof Variable) || n.eIsProxy()) {
			return null;
		}
		return (Variable) n;
	}

	/**
	 * L2 iter #2 sentinels — range and duplicate-value checks for enum literals.
	 * Walks the literal list in declaration order, evaluating each via a small
	 * inline folder (ExpressionInteger + unary minus only — anything more
	 * complex bails out, no false positives). See .claude/L2_ENUM_DESIGN.md §7.
	 */
	@Check
	public void checkEnum(com.neosyn.cg.cg.Enum enm) {
		Integer width = getDeclaredUnsignedWidth(enm);
		BigInteger maxAllowed = (width != null)
				? BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE)
				: null;
		Map<BigInteger, Variable> seen = new HashMap<>();
		BigInteger next = BigInteger.ZERO;
		for (Variable lit : enm.getLiterals()) {
			CgExpression explicit = (CgExpression) lit.getValue();
			BigInteger v;
			if (explicit != null) {
				BigInteger evaluated = tryEvalInt(explicit);
				if (evaluated == null) {
					// non-trivial expression — bail rather than risk false positives.
					return;
				}
				v = evaluated;
			} else {
				v = next;
			}
			if (v.signum() < 0 || (maxAllowed != null && v.compareTo(maxAllowed) > 0)) {
				String range = "0.." + (maxAllowed != null ? maxAllowed.toString() : "?");
				error("Enum literal '" + lit.getName() + "' value " + v
						+ " is out of range for the underlying type (" + range + ").",
						lit, Literals.NAMED__NAME, -1,
						IssueCodes.ERR_ENUM_LITERAL_OUT_OF_RANGE);
			}
			Variable prior = seen.get(v);
			if (prior != null) {
				error("Enum literal '" + lit.getName() + "' duplicates the effective value "
						+ v + " of '" + prior.getName() + "'.",
						lit, Literals.NAMED__NAME, -1,
						IssueCodes.ERR_ENUM_LITERAL_DUPLICATE_VALUE);
			} else {
				seen.put(v, lit);
			}
			next = v.add(BigInteger.ONE);
		}
	}

	/**
	 * If the enum's underlying is an unsigned TypeDecl with a known width
	 * (e.g. `u4`, `u8`), return that width. Returns null for signed types
	 * or types we can't statically size — the range sentinel becomes a no-op
	 * in those cases, which is the conservative default.
	 */
	private static Integer getDeclaredUnsignedWidth(com.neosyn.cg.cg.Enum enm) {
		CgType u = enm.getUnderlying();
		if (!(u instanceof TypeDecl)) {
			return null;
		}
		TypeDecl td = (TypeDecl) u;
		if (td.isSigned() || "bool".equals(td.getSpec()) || "float".equals(td.getSpec())) {
			return null;
		}
		String spec = td.getSpec();
		if (spec == null || spec.isEmpty() || spec.charAt(0) != 'u') {
			return null;
		}
		String rest = spec.substring(1);
		if (rest.isEmpty() || !Character.isDigit(rest.charAt(0))) {
			return null;
		}
		try {
			return Integer.parseInt(rest);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Tiny const folder for enum literal values. Handles ExpressionInteger
	 *  and unary minus on ExpressionInteger. Everything else returns null. */
	private static BigInteger tryEvalInt(CgExpression expr) {
		if (expr instanceof ExpressionInteger) {
			return ((ExpressionInteger) expr).getValue();
		}
		if (expr instanceof ExpressionUnary) {
			ExpressionUnary u = (ExpressionUnary) expr;
			if ("-".equals(u.getOperator())) {
				BigInteger sub = tryEvalInt(u.getExpression());
				return sub == null ? null : sub.negate();
			}
			if ("+".equals(u.getOperator())) {
				return tryEvalInt(u.getExpression());
			}
		}
		return null;
	}

	/**
	 * Reports bundles that participate in an import cycle. Two bundles that import
	 * each other (directly or transitively) form a dependency cycle; the
	 * instantiator loads imported bundles recursively, so such a cycle would
	 * recurse until a {@link StackOverflowError}. The instantiator now breaks the
	 * recursion defensively, and this check surfaces the cause as a normal
	 * validation error. See IssueCodes#ERR_CYCLIC_IMPORT.
	 *
	 * <p>Only bundle→bundle edges are followed, mirroring the instantiator's
	 * {@code loadBundles} which loads {@link Bundle}s exclusively.
	 */
	@Check
	public void checkNoCyclicImport(Bundle bundle) {
		if (importReachesBundle(bundle, bundle, new HashSet<CgEntity>())) {
			error("Cyclic bundle import detected: this bundle imports another bundle that "
					+ "(directly or transitively) imports it back. Break the cycle by removing "
					+ "one direction of the import.",
					bundle, Literals.NAMED__NAME, -1, IssueCodes.ERR_CYCLIC_IMPORT);
		}
	}

	/**
	 * Depth-first search over bundle import edges starting from {@code current},
	 * looking for a path back to {@code start}. Only bundle nodes are traversed.
	 */
	private boolean importReachesBundle(Bundle start, CgEntity current, Set<CgEntity> visited) {
		for (CgEntity dep : importDependencies(current)) {
			if (dep == start) {
				return true;
			}
			if (dep instanceof Bundle && visited.add(dep)
					&& importReachesBundle(start, dep, visited)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the set of entities that {@code entity} depends on through import
	 * statements, considering both the entity's own imports and its enclosing
	 * module's imports (module-level imports apply to all entities in the file).
	 */
	private Set<CgEntity> importDependencies(CgEntity entity) {
		Set<CgEntity> deps = new HashSet<>();
		collectImportTargets(entity.getImports(), entity, deps);
		Module module = getContainerOfType(entity, Module.class);
		if (module != null) {
			collectImportTargets(module.getImports(), entity, deps);
		}
		return deps;
	}

	private void collectImportTargets(List<Import> imports, CgEntity self, Set<CgEntity> deps) {
		for (Import imp : imports) {
			for (Imported imported : imp.getImported()) {
				Named target = imported.getType();
				if (target == null || target.eIsProxy()) {
					continue;
				}
				CgEntity owner = (target instanceof CgEntity)
						? (CgEntity) target
						: getContainerOfType(target, CgEntity.class);
				if (owner != null && owner != self) {
					deps.add(owner);
				}
			}
		}
	}

	/**
	 * Checks if a local variable shadows a port name.
	 * Note: This @Check method works during unit tests and command-line compilation,
	 * but doesn't run in the VS Code LSP context. A TypeScript-based
	 * PortShadowingDiagnosticsProvider handles the VS Code case.
	 */
	@Check
	public void checkLocalVariableShadowsPort(Variable variable) {
		if (!CgUtil.isLocal(variable)) {
			return;
		}

		String varName = variable.getName();
		if (varName == null) {
			return;
		}

		Task task = getContainerOfType(variable, Task.class);
		if (task == null) {
			return;
		}

		for (Variable port : CgUtil.getPorts(task.getPortDecls())) {
			if (varName.equals(port.getName())) {
				String direction = CgUtil.getDirection(port);
				error("Local variable '" + varName + "' shadows " + direction + " port with same name. "
						+ "Rename the local variable to avoid this conflict.", variable,
						Literals.NAMED__NAME, -1, IssueCodes.ERR_LOCAL_SHADOWS_PORT);
				return;
			}
		}
	}

	@Check(NORMAL)
	public void checkModule(Module module) {
		accepted = Sets.newHashSet();

		// updates the instantiator to reflect changes in this module
		// this method only performs an actual update if the instantiator is out of date
		try {
			instantiator.update(module);
		} catch (Exception e) {
			// log exceptions
			NeosynCore.log(e);
		}

		// for each entity of the module
		final NetworkChecker networkChecker = new NetworkChecker(this, instantiator);
		for (final CgEntity cxEntity : module.getEntities()) {
			instantiator.forEachMapping(cxEntity, new Executable<Entity>() {
				@Override
				public void exec(Entity entity) {
					if (entity instanceof DPN) {
						// check connectivity
						Network network = (Network) cxEntity;
						DPN dpn = (DPN) entity;
						networkChecker.checkDPN(network, dpn);
					} else if (entity instanceof Actor) {
						checkTask((Task) cxEntity, (Actor) entity);
					}

					// check types
					new TypeChecker(CgValidator.this, instantiator, entity).doSwitch(cxEntity);

					if (cxEntity instanceof Instantiable) {
						printErrors((Instantiable) cxEntity);
					}
				}
			});
		}
	}

	private void checkTask(Task task, Actor actor) {
		Variable loop = CgUtil.getFunction(task, NAME_LOOP);
		if (loop == null) {
			loop = CgUtil.getFunction(task, NAME_LOOP_DEPRECATED);
		}

		Variable setup = CgUtil.getFunction(task, NAME_SETUP);
		if (setup == null) {
			setup = CgUtil.getFunction(task, NAME_SETUP_DEPRECATED);
		}

		if (actor.getProperties().getAsJsonArray(PROP_CLOCKS).size() == 0) {
			validateCombinational(task, setup, loop);
		}
	}

	private void printErrors(Instantiable entity) {
		for (ErrorMarker error : entity.getErrors()) {
			acceptError(error.getMessage(), error.getSource(), error.getFeature(), error.getIndex(),
					null);
		}
	}

	/**
	 * Validates the given task.
	 * 
	 * @param module
	 *            a module with a combinational main function
	 * @param scope
	 *            scope of functions
	 */
	private void validateCombinational(Task task, Variable setup, final Variable loop) {
		for (Variable variable : CgUtil.getVariables(task)) {
			if (!CgUtil.isFunction(variable) && !CgUtil.isConstant(variable)) {
				String message = "A combinational task cannot declare state variables";
				error(message, variable, Literals.NAMED__NAME, -1);
				return;
			}
		}

		if (setup != null) {
			String message = "A combinational task cannot have a 'setup' function";
			error(message, setup, Literals.NAMED__NAME, -1);
		}

		if (loop != null) {
			instantiator.forEachMapping(task, new Executable<Entity>() {
				@Override
				public void exec(Entity entity) {
					if (new CycleDetector(instantiator, (Actor) entity).hasCycleBreaks(loop)) {
						String message = "A combinational task must not have cycle breaks";
						error(message, loop, null, -1);
					}
				}
			});
		}
	}

}
