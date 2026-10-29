/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_LOOP_DEPRECATED;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static com.neosyn.cg.CgConstants.NAME_SETUP_DEPRECATED;
import static com.neosyn.cg.validation.IssueCodes.ERR_CONFIRM_UNSUPPORTED;
import static com.neosyn.cg.validation.IssueCodes.ERR_PORT_NO_TYPE;
import static com.neosyn.cg.validation.IssueCodes.ERR_DUPLICATE_DECLARATIONS;
import static com.neosyn.cg.validation.IssueCodes.ERR_DUPLICATE_ENTITY;
import static com.neosyn.cg.validation.IssueCodes.ERR_DUPLICATE_PROPERTIES;
import static com.neosyn.cg.validation.IssueCodes.ERR_ENTRY_FUNCTION_BAD_TYPE;
import static com.neosyn.cg.validation.IssueCodes.ERR_EXPECTED_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_ILLEGAL_FENCE;
import static com.neosyn.cg.validation.IssueCodes.ERR_RETURN_SKIPS_WORK;
import static com.neosyn.cg.validation.IssueCodes.ERR_SIDE_EFFECTS_FUNCTION;
import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_ONE_BIT;
import static com.neosyn.cg.validation.IssueCodes.ERR_VAR_DECL;
import static com.neosyn.cg.validation.IssueCodes.SHOULD_REPLACE_INTERFACE_KEYWORD;
import static com.neosyn.cg.validation.IssueCodes.SHOULD_REPLACE_NAME;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.impl.ResourceDescriptionsProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.validation.EValidatorRegistrar;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.ExpressionInteger;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.MultiPortDecl;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.Statement;
import com.neosyn.cg.cg.StatementReturn;
import com.neosyn.cg.cg.StatementAssign;
import com.neosyn.cg.cg.StatementFence;
import com.neosyn.cg.cg.StatementIdle;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.cg.StatementLoop;
import com.neosyn.cg.cg.StatementVariable;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.TypeDecl;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.Variable;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.cg.internal.services.BoolCxSwitch;

/**
 * This class defines a structural validator.
 * 
 */
public class StructuralValidator extends AbstractDeclarativeValidator {

	/**
	 * This class defines a visitor that checks if a value has side-effects, which is the case if it
	 * references any variable that is not constant (this includes functions and ports).
	 * 
	 */
	private static class ValueVisitor extends BoolCxSwitch {

		@Override
		public Boolean caseExpressionVariable(ExpressionVariable expr) {
			Variable variable = expr.getSource().getVariable();
			if (!CgUtil.isConstant(variable)) {
				// any reference to a port and non-constant function
				return true;
			}

			return super.caseExpressionVariable(expr);
		}

	}

	@Inject
	private IQualifiedNameProvider nameProvider;

	@Inject
	private IScopeProvider scopeProvider;

	@Inject
	private ResourceDescriptionsProvider descriptionsProvider;

	// @Inject
	// private Typer typer;

	@Check
	public void checkArrayMultiDimPowerOfTwo(Variable variable) {
		if (CgUtil.isPort(variable)) {
			return;
		}

		// TODO we need an entity here, this check should be moved elsewhere
		// Type type = typer.getType(variable);
		// if (type == null) {
		// return;
		// }
		//
		// int dimensions = Typer.getNumDimensions(type);
		// if (dimensions >= 2) {
		// for (int dim : ((TypeArray) type).getDimensions()) {
		// if (!ValueUtil.isPowerOfTwo(dim)) {
		// error("Multi-dimensional arrays must have dimensions that are power-of-two",
		// variable, Literals.VARIABLE__DIMENSIONS,
		// ERR_ARRAY_MULTI_NON_POWER_OF_TWO);
		// }
		// }
		// }
	}

	/**
	 * An entity is defined in one file only (TRAPS T157). Two files declaring the same package and
	 * name were accepted without a word: one copy was used, by load order, and an edit to the other
	 * was ignored -- SHA256's TopShaStep1.cg carried a second `network TestShaStep1`. Files are
	 * compared by canonical path, because one file can be loaded under two URIs (T155).
	 */
	@Check
	public void checkEntityDefinedOnce(Module module) {
		if (module.eResource() == null) {
			return;
		}
		URI here = module.eResource().getURI();
		IResourceDescriptions index = descriptionsProvider.getResourceDescriptions(module.eResource());
		for (CgEntity entity : module.getEntities()) {
			if (entity.getName() == null) {
				continue;
			}
			QualifiedName name = nameProvider.getFullyQualifiedName(entity);
			if (name == null) {
				continue;
			}
			for (IEObjectDescription desc : index.getExportedObjects(Literals.CG_ENTITY, name, false)) {
				URI other = desc.getEObjectURI().trimFragment();
				if (!sameFile(here, other)) {
					error("'" + entity.getName() + "' is also defined in " + other.lastSegment()
							+ ", in the same package: only one of the two is used. Rename or remove one.",
							entity, Literals.NAMED__NAME, ERR_DUPLICATE_ENTITY);
					break;
				}
			}
		}
	}

	private static boolean sameFile(URI a, URI b) {
		if (a.equals(b)) {
			return true;
		}
		if (a.isFile() && b.isFile()) {
			try {
				return new File(a.toFileString()).getCanonicalPath()
						.equals(new File(b.toFileString()).getCanonicalPath());
			} catch (IOException e) {
				return false;
			}
		}
		// a platform:/ or other URI against a file one: compare what is left of the paths
		return a.lastSegment() != null && a.lastSegment().equals(b.lastSegment())
				&& a.segmentCount() > 1 && b.segmentCount() > 1
				&& a.segment(a.segmentCount() - 2).equals(b.segment(b.segmentCount() - 2));
	}

	/**
	 * At most one {@code properties} block per entity.
	 *
	 * <p>The block may appear anywhere among an entity's members, which is deliberate: pinning it
	 * to the first slot made the natural spelling — ports first, then the test block — a parse
	 * error, and that was the most common failure observed when models write C⏚. The cost of
	 * that freedom is that nothing syntactic prevents a SECOND block, and because
	 * {@code properties} is single-valued the last one silently wins: an earlier, correct
	 * {@code test:} fixture is discarded with no diagnostic at all. A fixture that looks present
	 * and never runs is the worst failure mode this compiler has, so it is an error.
	 *
	 * <p>The duplicate cannot be seen in the AST — by then the overwrite has already happened and
	 * only one {@code Obj} remains. It is recovered from the NODE MODEL, which keeps every token.
	 * Nested anonymous tasks have their own blocks, so each keyword is attributed to its own
	 * semantic element before being counted.
	 */
	@Check
	public void checkSingleProperties(Instantiable entity) {
		INode node = NodeModelUtils.findActualNodeFor(entity);
		if (node == null) {
			return;
		}

		int count = 0;
		for (ILeafNode leaf : node.getLeafNodes()) {
			if (!"properties".equals(leaf.getText())) {
				continue;
			}
			// attribute the keyword to its OWN entity, so a nested `task { properties ... }`
			// inside a network is not counted against the network
			if (NodeModelUtils.findActualSemanticObjectFor(leaf) == entity) {
				count++;
			}
		}

		if (count > 1) {
			error("An entity may declare at most one 'properties' block; this one has " + count
					+ ". The last block silently overrides the earlier ones, so an earlier "
					+ "test fixture would never run. Merge them into a single block.",
					entity, null, ERR_DUPLICATE_PROPERTIES);
		}
	}

	@Check
	public void checkAssign(StatementAssign stmt) {
		Variable variable = stmt.getTarget().getSource().getVariable();
		if (CgUtil.isPort(variable) && stmt.getOp() != null) {
			error("Port error: a port cannot be assigned. Use the write function instead.", stmt,
					Literals.STATEMENT_ASSIGN__TARGET);
		}
	}

	/**
	 * Rejects a {@code return} in a {@code void} function that would skip work.
	 *
	 * <p>{@code return} belongs at the end of a constant function; {@code setup} and
	 * {@code loop} are void and return implicitly. An early one is not part of the language, but
	 * the grammar accepted it and the backends then disagreed: the bytecode compiler emits a real
	 * return, so the simulator stops: the HDL printers emit nothing at all for a value-less
	 * return, so the guard becomes an empty {@code if} and every statement it was meant to skip
	 * runs anyway. Simulation and silicon disagree, silently, with the fast backend right.
	 *
	 * <p>Only a return that can actually skip something is an error. A return in tail position —
	 * last in the body, or last inside a branch that is itself last — behaves identically on both
	 * backends, so it is left alone rather than nagged about.
	 *
	 * @see IssueCodes#ERR_RETURN_SKIPS_WORK
	 */
	@Check
	public void checkReturnSkipsWork(StatementReturn stmt) {
		if (stmt.getValue() != null) {
			// a value-returning return belongs to a typed function; unrelated to this defect
			return;
		}
		Variable function = EcoreUtil2.getContainerOfType(stmt, Variable.class);
		if (function == null || !(function.eContainer() instanceof VarDecl)
				|| !((VarDecl) function.eContainer()).isVoid()) {
			return;
		}
		if (isInTailPosition(stmt)) {
			return;
		}
		error("`return` cannot be used here: it would skip the rest of `" + function.getName()
				+ "`, and only the simulator honours that — the HDL backends drop it and run the "
				+ "skipped statements anyway. Invert the guard instead: put the work inside "
				+ "`if (<condition>) { ... }`.", stmt, null, ERR_RETURN_SKIPS_WORK);
	}

	/**
	 * Whether nothing would be skipped by returning here — the statement is last in its block,
	 * and that block is the function body or is itself in tail position.
	 */
	private boolean isInTailPosition(EObject node) {
		EObject parent = node.eContainer();
		if (parent == null) {
			return false;
		}
		if (parent instanceof Variable) {
			// reached the function body: nothing further can be skipped
			return true;
		}
		if (!(parent instanceof Block)) {
			// a wrapper that occupies no statement slot of its own -- `Branch` sits between a
			// block and its `if`, and a labelled statement wraps its own. Whether anything is
			// skipped depends on where the WRAPPER sits, so keep walking.
			return isInTailPosition(parent);
		}
		List<Statement> stmts = ((Block) parent).getStmts();
		if (stmts.isEmpty() || stmts.get(stmts.size() - 1) != node) {
			return false;
		}
		return isInTailPosition(parent);
	}

	@Check
	public void checkDuplicateDeclarations(Variable variable) {
		EObject context = variable.eContainer();
		if (variable.getName() == null || context instanceof ExpressionVariable) {
			// ignore variables with a null name and synthetic variables (created by the linker)
			// name is null when the variable declaration is incomplete
			return;
		}

		QualifiedName name;
		if (context instanceof StatementVariable) {
			// local variable
			name = QualifiedName.create(variable.getName());
		} else {
			// not a local variable
			context = EcoreUtil2.getContainerOfType(variable, Module.class);
			name = nameProvider.getFullyQualifiedName(variable);
		}

		IScope scope = scopeProvider.getScope(context, Literals.VAR_REF__OBJECTS);
		Iterable<IEObjectDescription> it = scope.getElements(name);
		int n = Iterables.size(it);

		if (n > 1) {
			error("Duplicate variable declaration '" + variable.getName() + "'", variable,
					Literals.NAMED__NAME, ERR_DUPLICATE_DECLARATIONS);
		}
	}

	/**
	 * A value-returning function must be declared {@code const}: a non-const
	 * value-returning function never gets an IR body, so a CALL to it fails far
	 * away with the cryptic "no compiled body" error. Flag it clearly at the
	 * declaration instead. (Functions in bundles/networks are implicitly const,
	 * so {@link CgUtil#isConstant} already exempts them.)
	 */
	@Check
	public void checkValueReturningFunctionIsConst(Variable variable) {
		if (!CgUtil.isFunction(variable) || !(variable.eContainer() instanceof VarDecl)) {
			return;
		}
		if (!CgUtil.isVoid(variable) && !CgUtil.isConstant(variable)) {
			error("A value-returning function must be declared 'const' "
					+ "(otherwise it has no compiled body and calls to it fail).",
					variable, Literals.NAMED__NAME);
		}
	}

	@Check
	public void checkFence(StatementFence fence) {
		Block compound = (Block) fence.eContainer();
		List<Statement> stmts = compound.getStmts();
		int index = stmts.indexOf(fence);
		boolean illegal = false;
		if (index == 0 || index == stmts.size() - 1) {
			// first or last => illegal
			illegal = true;
		} else {
			Statement previous = stmts.get(index - 1);
			if (previous instanceof StatementFence) {
				// fence before a fence => illegal
				illegal = true;
			} else {
				Statement next = stmts.get(index + 1);
				if ((previous instanceof StatementIdle || next instanceof StatementIdle)
						|| (previous instanceof StatementIf)
						|| (previous instanceof StatementLoop || next instanceof StatementLoop)) {
					// fence before/after idle, if, loop => illegal
					illegal = true;
				}
			}
		}

		if (illegal) {
			error("Illegal fence: a fence must be placed between two statements.", fence, null,
					ERR_ILLEGAL_FENCE);
		}
	}

	@Check
	public void checkFunction(Variable variable) {
		if (CgUtil.isFunction(variable)) {
			// functions declared as constant must not have side effects
			if (CgUtil.isConstant(variable) && CgUtil.hasSideEffects(variable)) {
				error("Constant function '" + variable.getName() + "' cannot have side effects",
						variable, Literals.NAMED__NAME, ERR_SIDE_EFFECTS_FUNCTION);
			}

			// functions declared as constant must not have side effects
			if (!CgUtil.isConstant(variable) && !CgUtil.isVoid(variable)) {
				error("Function '" + variable.getName()
						+ "' returns a result and must be declared const", variable,
						Literals.NAMED__NAME, ERR_SIDE_EFFECTS_FUNCTION);
			}
		}
	}

	@Check
	public void checkPackage(Module module) {
		String packageName = module.getPackage();
		URI uri = module.eResource().getURI();
		// The expected-package check only applies to Eclipse-workspace
		// (platform:/) resources. The Eclipse-platform lookup is isolated in
		// WorkspacePackageResolver so this validator carries no static reference
		// to org.eclipse.core.resources (absent from the VS Code-only standalone
		// jar). In the shipped product URIs are always file:, so this branch —
		// and that class — is never reached at runtime.
		if (uri.isPlatform()) {
			String expected = WorkspacePackageResolver.expectedPackage(uri);
			if (expected != null && !packageName.equals(expected)) {
				error("The declared package \"" + packageName
						+ "\" does not match the expected package \"" + expected + "\"",
						module, Literals.MODULE__PACKAGE, INSIGNIFICANT_INDEX,
						SHOULD_REPLACE_NAME, packageName, expected);
			}
		}
	}

	@Check
	public void checkPortDecl(SinglePortDecl decl) {
		// A derailed parse RECOVERS by inventing nodes, and the nodes it invents here are
		// port declarations: `int<32> out = x.read();` inside `loop()` (where `out` is a
		// reserved word) came back as two SinglePortDecls, so this check fired twice and
		// told the user their PORTS were untyped, pointing at lines inside the function
		// body. The ports were fine. The check was right about nodes that should not exist.
		//
		// Semantic findings on a file that did not parse are not trustworthy in general,
		// and a fabricated one is worse than silence: it buys a search in the wrong place.
		// The file cannot compile until the syntax error is fixed anyway, and once it is,
		// a genuinely typeless port is reported normally.
		//
		// Scoped to this check on purpose rather than made a blanket rule for the
		// validator: this is the one observed to fire on recovery nodes, and suppressing
		// every semantic check on any syntax error is a much larger behavioural change than
		// the evidence supports.
		if (decl.eResource() != null && !decl.eResource().getErrors().isEmpty()) {
			return;
		}

		if (!decl.getPorts().isEmpty()) {
			PortDef def = decl.getPorts().get(0);
			if (def.getVar().getType() == null) {
				// Carries an issue code so it can BLOCK. Without one it was reported as a
				// non-blocking `warning:`, generate ran the emitter on a malformed port,
				// NPE'd, and skipped the entity behind "Partial success" + exit 0 (T51).
				error("Port declaration: this port must have a type", def.getVar(),
						Literals.NAMED__NAME, INSIGNIFICANT_INDEX, ERR_PORT_NO_TYPE);
			}
		}
	}

	@Check
	public void checkStateVariable(Variable variable) {
		// this is only for global variables (not local, not functions)
		if (!CgUtil.isGlobal(variable) || CgUtil.isFunction(variable)) {
			return;
		}

		// check dimensions
		for (CgExpression dim : variable.getDimensions()) {
			boolean hasSideEffects = new ValueVisitor().doSwitch(dim);
			if (hasSideEffects) {
				error("This expression is not a compile-time constant", dim, null,
						ERR_EXPECTED_CONST);
			}
		}

		// set flag "module is actor"
		Instantiable entity = EcoreUtil2.getContainerOfType(variable, Instantiable.class);

		// check initial value
		if (!checkStateVarValue(entity != null, variable)) {
			return;
		}

		// check type of value is compatible with type of state variable
		// TODO do it differently so we don't have to compute the type of arrays
		// Value value = (Value) variable.getValue();
		// Type typeExpr = ValueUtil.getType(Evaluator.getValue(value));
		// new TypeChecker(getMessageAcceptor()).checkAssign(variable, variable, typeExpr);

		// in a header, a state variable is implicitly constant
	}

	private boolean checkStateVarValue(boolean isActor, Variable variable) {
		CgExpression value = variable.getValue();
		if (value == null) {
			if (!isActor) {
				// in a header, a state variable must have an initial value
				error("The variable " + variable.getName() + " must have "
						+ "an initial value because it is defined in a header", variable, null,
						ERR_VAR_DECL);
				return false;
			}

			// a variable declared as "const" must have an initial value
			if (CgUtil.isConstant(variable)) {
				error("The variable " + variable.getName() + " must have "
						+ "an initial value because it is declared constant", variable, null,
						ERR_VAR_DECL);
			}

			return false;
		}

		// check if value has side-effects
		boolean hasSideEffects = new ValueVisitor().doSwitch(value);
		if (hasSideEffects) {
			error("The initial value of the variable '" + variable.getName()
					+ "' is not a compile-time constant", value, null, ERR_EXPECTED_CONST);
			return false;
		}
		return true;
	}

	@Check
	public void checkTask(Task task) {
		Variable function = CgUtil.getFunction(task, NAME_LOOP);
		if (function == null) {
			function = CgUtil.getFunction(task, NAME_LOOP_DEPRECATED);
			if (function == null) {
				return;
			}
		}

		Variable loop = function;
		if (!CgUtil.isVoid(loop)) {
			String message = "The 'loop' function must have type void";
			error(message, loop, Literals.NAMED__NAME, ERR_ENTRY_FUNCTION_BAD_TYPE);
		}

		function = CgUtil.getFunction(task, NAME_SETUP);
		if (function == null) {
			function = CgUtil.getFunction(task, NAME_SETUP_DEPRECATED);
		}

		Variable setup = function;
		if (setup != null && !CgUtil.isVoid(setup)) {
			String message = "The 'setup' function must have type void";
			error(message, setup, Literals.NAMED__NAME, ERR_ENTRY_FUNCTION_BAD_TYPE);
		}
	}

	/**
	 * {@code confirm} (and the deprecated {@code sync ack} / {@code (ack)}) is refused (TRAPS T11,
	 * decided 2026-10-19). It is documented as valid + ack, but every back end declared the
	 * {@code _ack} input and never read it: valid was a one-cycle pulse, and a consumer not ready in
	 * that cycle lost the value while the port list looked like an acknowledged interface. Refused
	 * by INTERFACE, not by keyword, so a port inheriting {@code confirm} from a comma list is caught.
	 */
	@Check
	public void checkConfirmPort(SinglePortDecl decl) {
		for (PortDef def : decl.getPorts()) {
			Variable port = def.getVar();
			if (port != null && CgUtil.getInterface(port) == InterfaceType.SYNC_ACK) {
				error("`confirm` is not supported: its acknowledge was never read, so a value the consumer "
						+ "was not ready for was lost. Use `stream` (valid/ready: the value is held until it "
						+ "is taken) or `push` (valid for one cycle, no back-pressure)", port,
						Literals.NAMED__NAME, INSIGNIFICANT_INDEX, ERR_CONFIRM_UNSUPPORTED);
			}
		}
	}

	/**
	 * The deprecated interface spellings, reported by the compiler so the CLI and the agent kit see
	 * them too (TRAPS T10: only the VS Code extension said so, and `sync { }` was read as the grouped
	 * form of a BARE port -- it is `push`, and it adds a valid). Non-blocking: same meaning as the new
	 * spelling. `sync ack` / `(ack)` are not here: they are refused (checkConfirmPort).
	 */
	@Check
	public void checkDeprecatedInterfaceKeyword(PortDef def) {
		// `(sync combinational)` has no modern spelling (the grammar takes `combinational` only with
		// `sync`), so a warning would ask for something that cannot be written. Used by UDP_Send.
		if (def.isAckOld() || def.isAck() || def.isCombinational()) {
			return;
		}
		String message = null;
		if (def.isSyncOld() && def.isReadyOld() || def.isReady() || def.isReadyOld()) {
			message = "`sync ready` / `ready` is the deprecated spelling of `stream` (valid + ready): write `stream`";
		} else if (def.isSyncOld() || def.isSync()) {
			message = "`sync` is the deprecated spelling of `push`: write `push`. It is not a bare port -- it adds a"
					+ " `valid`, and the task waits for it";
		}
		if (message != null && def.getVar() != null) {
			warning(message, def.getVar(), Literals.NAMED__NAME, INSIGNIFICANT_INDEX,
					SHOULD_REPLACE_INTERFACE_KEYWORD);
		}
	}

	@Check
	public void checkDeprecatedInterfaceBlock(MultiPortDecl decl) {
		if (decl.isAck() || !decl.isSync() && !decl.isReady()) {
			return;
		}
		String message = decl.isReady()
				? "`sync ready { }` is the deprecated spelling of `stream { }` (valid + ready): write `stream`"
				: "`sync { }` is the deprecated spelling of `push { }`: write `push`. It does not group bare ports --"
						+ " every port inside gets a `valid`, and the task waits for its inputs";
		warning(message, decl, null, SHOULD_REPLACE_INTERFACE_KEYWORD);
	}

	@Check
	public void checkTypeDecl(TypeDecl type) {
		String spec = type.getSpec();
		// A LITERAL width of 1 in any spelling -- uint<1>, int<1>, unsigned<1>, signed<1>, unsigned int<1>
		// -- is the same one-bit integer as u1 (decided 2026-10-19: refused too). A width that is a
		// generic parameter is NOT judged here: it is known per instance, and a width-1 instance of a
		// generic (a 1-bit FIFO) is legitimate.
		boolean literalOneBit = type.getSize() instanceof ExpressionInteger
				&& BigInteger.ONE.equals(((ExpressionInteger) type.getSize()).getValue());
		if ("i1".equals(spec) || "u1".equals(spec) || literalOneBit) {
			error("Integer types must be at least two bits large, use bool to declare a single-bit variable",
					type, null, ERR_TYPE_ONE_BIT);
		}
	}

	@Override
	public void register(EValidatorRegistrar registrar) {
		// do nothing: packages are already registered by CgJavaValidator
	}

}
