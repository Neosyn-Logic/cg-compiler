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
import static com.neosyn.cg.validation.IssueCodes.ERR_DUPLICATE_DECLARATIONS;
import static com.neosyn.cg.validation.IssueCodes.ERR_ENTRY_FUNCTION_BAD_TYPE;
import static com.neosyn.cg.validation.IssueCodes.ERR_EXPECTED_CONST;
import static com.neosyn.cg.validation.IssueCodes.ERR_ILLEGAL_FENCE;
import static com.neosyn.cg.validation.IssueCodes.ERR_SIDE_EFFECTS_FUNCTION;
import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_ONE_BIT;
import static com.neosyn.cg.validation.IssueCodes.ERR_VAR_DECL;
import static com.neosyn.cg.validation.IssueCodes.SHOULD_REPLACE_NAME;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.Statement;
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
import com.neosyn.cg.internal.services.BoolCxSwitch;

/**
 * This class defines a structural validator.
 * 

 * 
 */
public class StructuralValidator extends AbstractDeclarativeValidator {

	/**
	 * This class defines a visitor that checks if a value has side-effects, which is the case if it
	 * references any variable that is not constant (this includes functions and ports).
	 * 
	
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

	@Check
	public void checkAssign(StatementAssign stmt) {
		Variable variable = stmt.getTarget().getSource().getVariable();
		if (CgUtil.isPort(variable) && stmt.getOp() != null) {
			error("Port error: a port cannot be assigned. Use the write function instead.", stmt,
					Literals.STATEMENT_ASSIGN__TARGET);
		}
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
		if (!decl.getPorts().isEmpty()) {
			PortDef def = decl.getPorts().get(0);
			if (def.getVar().getType() == null) {
				error("Port declaration: this port must have a type", def.getVar(),
						Literals.NAMED__NAME);
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

	@Check
	public void checkTypeDecl(TypeDecl type) {
		String spec = type.getSpec();
		if ("i1".equals(spec) || "u1".equals(spec)) {
			error("Integer types must be at least two bits large, use bool to declare a single-bit variable",
					type, null, ERR_TYPE_ONE_BIT);
		}
	}

	@Override
	public void register(EValidatorRegistrar registrar) {
		// do nothing: packages are already registered by CgJavaValidator
	}

}
