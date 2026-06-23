/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.models.ir.IrFactory.eINSTANCE;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstCall;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractExpressionTransformer;
import com.neosyn.models.ir.transform.AbstractIrVisitor;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines a module transformation that visits all expressions in an actor/unit.
 * 

 * 
 */
public class ExpressionTransformation extends ProcedureTransformation {

	/**
	 * This class defines a visitor that visits all expressions in blocks, instructions, and initial
	 * values of variables.
	 * 
	
	 * 
	 */
	private static class ExpressionVisitor extends AbstractIrVisitor {

		private AbstractExpressionTransformer transformer;

		public ExpressionVisitor(AbstractExpressionTransformer transformer) {
			this.transformer = transformer;
		}

		@Override
		public Void caseBlockIf(BlockIf block) {
			block.setCondition(visitExpr(eINSTANCE.createTypeBool(), block.getCondition()));

			visit(block.getThenBlocks());
			visit(block.getElseBlocks());
			return doSwitch(block.getJoinBlock());
		}

		@Override
		public Void caseBlockWhile(BlockWhile block) {
			block.setCondition(visitExpr(eINSTANCE.createTypeBool(), block.getCondition()));

			visit(block.getBlocks());
			doSwitch(block.getJoinBlock());
			return DONE;
		}

		@Override
		public Void caseInstAssign(InstAssign assign) {
			Type type = assign.getTarget().getVariable().getType();
			assign.setValue(visitExpr(type, assign.getValue()));
			return DONE;
		}

		@Override
		public Void caseInstCall(InstCall call) {
			Iterable<? extends Type> types;
			if (call.isAssert()) {
				types = ImmutableSet.of(eINSTANCE.createTypeBool());
			} else if (call.isPrint()) {
				// create a list rather than an iterable to avoid concurrent modifications later
				List<Type> list = new ArrayList<>();
				for (Expression expr : call.getArguments()) {
					list.add(TypeUtil.getType(expr));
				}
				types = list;
			} else {
				List<Var> parameters = call.getProcedure().getParameters();
				types = Iterables.transform(parameters, new Function<Var, Type>() {
					@Override
					public Type apply(Var variable) {
						return variable.getType();
					}
				});
			}
			transformer.visitExprList(types, call.getArguments());
			return DONE;
		}

		@Override
		public Void caseInstLoad(InstLoad load) {
			if (!load.getIndexes().isEmpty()) {
				Type type = load.getSource().getVariable().getType();
				visitIndexes(type, load.getIndexes());
			}
			return DONE;
		}

		@Override
		public Void caseInstReturn(InstReturn instReturn) {
			final Expression value = instReturn.getValue();
			if (value != null) {
				instReturn.setValue(visitExpr(procedure.getReturnType(), value));
			}
			return DONE;
		}

		@Override
		public Void caseInstStore(InstStore store) {
			Var variable = store.getTarget().getVariable();
			// Skip if variable is an unresolved proxy (e.g., built-in entity port)
			if (variable == null || variable.eIsProxy()) {
				return DONE;
			}
			Type type = variable.getType();
			// Skip if type is null (unresolved reference)
			if (type == null) {
				return DONE;
			}
			if (!store.getIndexes().isEmpty()) {
				visitIndexes(type, store.getIndexes());
			}

			store.setValue(visitExpr(type, store.getValue()));
			return DONE;
		}

		@Override
		public Void caseProcedure(Procedure procedure) {
			this.procedure = procedure;
			transformer.setProcedure(procedure);
			return visit(procedure.getBlocks());
		}

		@Override
		public Void caseVar(Var variable) {
			Expression value = variable.getInitialValue();
			if (value != null) {
				variable.setInitialValue(visitExpr(variable.getType(), value));
			}
			return DONE;
		}

		private Expression visitExpr(Type target, Expression expression) {
			Type type;
			if (target.isArray()) {
				type = ((TypeArray) target).getElementType();
			} else {
				type = target;
			}
			return transformer.visitExpr(type, expression);
		}

		private void visitIndexes(Type type, EList<Expression> indexes) {
			if (type.isArray()) {
				EList<Integer> dimensions = ((TypeArray) type).getDimensions();
				Iterable<Type> types = Iterables.transform(dimensions,
						new Function<Integer, Type>() {
							@Override
							public Type apply(Integer size) {
								return eINSTANCE.createTypeInt(TypeUtil.getSize(size - 1), false);
							}
						});
				transformer.visitExprList(types, indexes);
			}
		}

	}

	public ExpressionTransformation(AbstractExpressionTransformer transformer) {
		super(new ExpressionVisitor(transformer));
	}

	@Override
	public Void caseEntity(Entity entity) {
		for (Var var : entity.getVariables()) {
			irVisitor.doSwitch(var);
		}

		for (Procedure procedure : entity.getProcedures()) {
			visitProcedure(procedure);
		}

		return DONE;
	}

	protected AbstractExpressionTransformer getTransformer() {
		return ((ExpressionVisitor) irVisitor).transformer;
	}

}
