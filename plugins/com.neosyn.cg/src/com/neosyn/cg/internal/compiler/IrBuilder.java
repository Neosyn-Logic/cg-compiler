/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.compiler;

import static com.neosyn.cg.internal.TransformerUtil.getStartLine;
import static com.neosyn.cg.internal.TransformerUtil.isFalse;
import static com.neosyn.cg.internal.TransformerUtil.isOne;
import static com.neosyn.cg.internal.TransformerUtil.isTrue;
import static com.neosyn.cg.internal.TransformerUtil.isZero;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.ExpressionList;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.services.Typer;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * This class defines an IR builder. The build state is composed of the current entity, procedure,
 * blocks, etc.
 * 

 * 
 */
public class IrBuilder {

	protected static final IrFactory ir = IrFactory.eINSTANCE;

	private List<Block> blocks;

	private final Deque<List<Block>> deque;

	protected final Entity entity;

	private final Set<String> existingSet;

	protected final IInstantiator instantiator;

	private Map<Variable, Var> localMap;

	/**
	 * Maps a struct-typed local Variable to a per-field name → IR Var lookup.
	 * Populated by {@link #transformStructLocal(Variable, Struct)} when the
	 * compiler encounters `Pair p;` and synthesises `p$lo`, `p$hi`, etc.
	 * See .claude/L1_STRUCT_DESIGN.md §7.
	 */
	private final Map<Variable, Map<String, Var>> structFieldMap = new HashMap<>();

	/**
	 * current procedure
	 */
	private Procedure procedure;

	protected Transformer transformer;

	/**
	 * Creates a new function transformer with the given entity.
	 * 
	 * @param entity
	 *            target IR entity
	 */
	public IrBuilder(IInstantiator instantiator, Entity entity) {
		this.instantiator = instantiator;
		this.entity = entity;

		deque = new ArrayDeque<>();
		localMap = new HashMap<>();

		existingSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		existingSet.add(entity.getSimpleName());
		Iterables.addAll(existingSet, Iterables.transform(
				Iterables.concat(entity.getInputs(), entity.getOutputs(), entity.getVariables()),
				new Function<Var, String>() {
					@Override
					public String apply(Var var) {
						return var.getName();
					}
				}));
	}

	final void add(Block block) {
		blocks.add(block);
	}

	public final void add(Instruction instruction) {
		IrUtil.getLast(blocks).add(instruction);
	}

	final void addAll(Collection<Block> blocks) {
		this.blocks.addAll(blocks);
	}

	/**
	 * Creates a new temporary local variable based on the given hint. The local variable is
	 * guaranteed to have a unique name.
	 * 
	 * @param lineNumber
	 *            line number
	 * @param type
	 *            type
	 * @param hint
	 *            suggestion for a name
	 * @return a new local variable
	 */
	public Var createLocal(int lineNumber, Type type, String hint) {
		Var var = ir.createVar(lineNumber, type, getUniqueName(hint), true, 0);
		procedure.getLocals().add(var);
		return var;
	}

	/**
	 * Returns the IR variable that corresponds to the given Cx variable.
	 * 
	 * @param variable
	 *            a Cx variable
	 * @return the IR variable that corresponds to the given variable
	 */
	final Var getMapping(Variable variable) {
		Var var = localMap.get(variable);
		if (var == null) {
			var = instantiator.getMapping(entity, variable);
		}
		return var;
	}

	public final Procedure getProcedure() {
		return procedure;
	}

	/**
	 * Returns the IR procedure that corresponds to the given Cx function.
	 * 
	 * @param function
	 *            a Cx function
	 * @return the IR procedure that corresponds to the given function
	 */
	final Procedure getProcedure(Variable function) {
		return instantiator.getMapping(entity, function);
	}

	private String getUniqueName(String hint) {
		String name = hint;
		int i = 0;
		while (existingSet.contains(name) || procedure.getLocal(name) != null) {
			name = hint + "_" + i;
			i++;
		}

		return name;
	}

	/**
	 * Creates a new target scalar variable, and loads the given source into it. Do not perform bit
	 * selection though.
	 * 
	 * @param lineNumber
	 *            line number
	 * @param source
	 *            source variable
	 * @param indexes
	 *            indexes
	 * @return the new target scalar variable
	 */
	final Var loadVariable(int lineNumber, Var source, List<CgExpression> indexes) {
		Type type = source.getType();
		int dimensions = Typer.getNumDimensions(type);

		// creates local target variable (will be cleaned up later if necessary)
		Type varType;
		if (type.isArray()) {
			varType = ((TypeArray) type).getElementType();
		} else {
			varType = type;
		}
		Var target = createLocal(lineNumber, varType, source.getName());

		// loads (but do not perform bit selection)
		List<CgExpression> subIndexes = indexes.subList(0, dimensions);
		List<Expression> expressions = transformIndexes(type, subIndexes);

		InstLoad load = ir.createInstLoad(lineNumber, target, source, expressions);
		add(load);

		return target;
	}

	/**
	 * Restores the value of blocks that was previously saved.
	 */
	public final void restoreBlocks() {
		blocks = deque.pollFirst();
	}

	/**
	 * Saves the current value of 'blocks'.
	 */
	public final void saveBlocks() {
		if (blocks != null) {
			deque.addFirst(blocks);
		}
	}

	final void setBlocks(List<Block> blocks) {
		this.blocks = blocks;
	}

	public final void setProcedure(Procedure procedure) {
		this.procedure = procedure;
		if (procedure == null) {
			this.blocks = null;
		} else {
			this.blocks = procedure.getBlocks();
		}
	}

	/**
	 * Sets the current procedure of this builder from the given function.
	 * 
	 * @param function
	 */
	final void setProcedure(Variable function) {
		procedure = instantiator.getMapping(entity, function);
		blocks = procedure.getBlocks();
	}

	public void setTransformer(Transformer transformer) {
		this.transformer = transformer;
	}

	/**
	 * Depending on the IR expression that value evaluates to, this method:
	 * <ul>
	 * <li>calls {@link #storeBitSet(int, Var, List, Var, int)} if value equals "true" or is an
	 * integer != 0.</li>
	 * <li>calls {@link #storeBitClear(int, Var, List, Var, int)} if value equals "false" or "0".
	 * </li>
	 * <li>otherwise it creates an if block with storeBitSet in the then block, and storeBitClear in
	 * the else block.</li>
	 * </ul>
	 * 
	 * @param lineNumber
	 *            line number
	 * @param target
	 *            target variable
	 * @param indexes
	 *            indexes
	 * @param local
	 *            local variable
	 * @param index
	 *            index of the bit to set
	 * @param value
	 *            value of the bit
	 */
	private void storeBit(int lineNumber, Var target, List<Expression> indexes, Var local,
			int index, Expression expr) {
		// get value
		if (isTrue(expr)) {
			storeBitSet(lineNumber, target, indexes, local, index);
		} else if (isFalse(expr)) {
			storeBitClear(lineNumber, target, indexes, local, index);
		} else {
			saveBlocks();

			// create and add block
			BlockIf block = ir.createBlockIf();
			block.setJoinBlock(ir.createBlockBasic());
			block.setLineNumber(lineNumber);

			block.setCondition(expr);
			add(block);

			// "then" block: set bit
			setBlocks(block.getThenBlocks());
			storeBitSet(lineNumber, target, indexes, local, index);

			// "else" block: clear bit
			setBlocks(block.getElseBlocks());
			storeBitClear(lineNumber, target, indexes, local, index);

			restoreBlocks();
		}
	}

	/**
	 * Creates Store(target, indexes, local & 0b110111) (the index of the '0' is given by the index
	 * variable).
	 * 
	 * @param lineNumber
	 *            line number
	 * @param target
	 *            target variable
	 * @param indexes
	 *            indexes
	 * @param local
	 *            local variable (loaded from the target)
	 * @param index
	 *            index of the bit to set
	 */
	private void storeBitClear(int lineNumber, Var target, List<Expression> indexes, Var local,
			int index) {
		Type type = local.getType();
		int size = ((TypeInt) type).getSize();

		BigInteger mask = ONE.shiftLeft(size).subtract(ONE).clearBit(index);
		Expression value = ir.createExprBinary(ir.createExprVar(local), OpBinary.BITAND,
				ir.createExprInt(mask));

		InstStore store = ir.createInstStore(lineNumber, target, indexes, value);
		add(store);
	}

	/**
	 * Creates Store(target, indexes, local | 0b001000) (the index of the '1' is given by the index
	 * variable).
	 * 
	 * @param lineNumber
	 *            line number
	 * @param target
	 *            target variable
	 * @param indexes
	 *            indexes
	 * @param local
	 *            local variable (loaded from the target)
	 * @param index
	 *            index of the bit to set
	 */
	private void storeBitSet(int lineNumber, Var target, List<Expression> indexes, Var local,
			int index) {
		BigInteger mask = ZERO.setBit(index);
		Expression value = ir.createExprBinary(ir.createExprVar(local), OpBinary.BITOR,
				ir.createExprInt(mask));

		InstStore store = ir.createInstStore(lineNumber, target, indexes, value);
		add(store);
	}

	/**
	 * Creates a store to the given target variable, with the given indexes, and the given value.
	 * 
	 * @param lineNumber
	 *            line number
	 * @param target
	 *            target IR variable
	 * @param indexes
	 *            list of IR expressions (may be <code>null</code>)
	 * @param value
	 *            Cx value
	 */
	public final void storeExpr(int lineNumber, Var target, List<CgExpression> indexes,
			CgExpression value) {
		Type type = target.getType();
		boolean hasIndexes = indexes != null && !indexes.isEmpty();
		int dimensions = Typer.getNumDimensions(type);
		boolean storeBit = hasIndexes && dimensions < indexes.size();

		// transform expression (with implicit cast to boolean)
		if (storeBit) {
			type = ir.createTypeBool();
		} else if (hasIndexes) {
			type = ((TypeArray) type).getElementType();
		}
		Expression expr = transformExpr(value, type);

		// bit store
		if (storeBit) {
			// loads variable (but do not perform bit selection)
			Var local = loadVariable(lineNumber, target, indexes);

			// select indexes (but not bit selection)
			List<CgExpression> subIndexes = indexes.subList(0, dimensions);
			List<Expression> expressions = transformIndexes(target.getType(), subIndexes);

			// select bit index
			CgExpression exprIndex = indexes.get(indexes.size() - 1);
			int index = instantiator.evaluateInt(entity, exprIndex);

			// store the bit
			storeBit(lineNumber, target, expressions, local, index, expr);
			return;
		}

		// transform indexes
		List<Expression> expressions = hasIndexes ? transformIndexes(target.getType(), indexes)
				: Collections.<Expression> emptyList();

		// "normal" store
		InstStore store = ir.createInstStore(lineNumber, target, expressions, expr);
		add(store);
	}

	/**
	 * Stores an array-literal initializer (<code>a = {e0, e1, ...}</code>) as one
	 * indexed store per element, recursing into nested <code>{...}</code> for
	 * multi-dimensional arrays. Used for local array variables (task-level state
	 * vars take their initial value via the instantiator instead).
	 */
	public final void storeArrayLiteral(int lineNumber, Var target, ExpressionList list) {
		storeArrayLiteralRec(lineNumber, target, list, new ArrayList<Expression>());
	}

	private void storeArrayLiteralRec(int lineNumber, Var target, ExpressionList list,
			List<Expression> prefix) {
		List<CgExpression> elements = list.getValues();
		for (int i = 0; i < elements.size(); i++) {
			CgExpression element = elements.get(i);
			List<Expression> indexes = new ArrayList<>(prefix);
			indexes.add(ir.createExprInt(i));
			if (element instanceof ExpressionList) {
				storeArrayLiteralRec(lineNumber, target, (ExpressionList) element, indexes);
			} else {
				Type elementType = target.getType();
				for (int d = 0; d < indexes.size() && elementType instanceof TypeArray; d++) {
					elementType = ((TypeArray) elementType).getElementType();
				}
				Expression value = transformExpr(element, elementType);
				add(ir.createInstStore(lineNumber, target, indexes, value));
			}
		}
	}

	/**
	 * Transforms the given expression, and cast to boolean if <code>target</code> is a boolean
	 * type.
	 * 
	 * @param expression
	 *            an AST expression
	 * @param target
	 *            target type
	 * @return an IR expression
	 */
	protected Expression transformExpr(CgExpression expression, Type target) {
		Expression expr = transformer.transformExpr(expression);
		if (target.isBool()) {
			Type type = TypeUtil.getType(expr);
			if (isOne(expr)) {
				return ir.createExprBool(true);
			} else if (isZero(expr)) {
				return ir.createExprBool(false);
			} else if (!type.isBool()) {
				return ir.createExprBinary(expr, OpBinary.NE, ir.createExprInt(0));
			}
		}

		return expr;
	}

	/**
	 * Transforms the given AST expressions to a list of IR expressions. In the process nodes may be
	 * created and added to the current {@link #procedure}, since many expressions are expressed
	 * with IR statements.
	 * 
	 * @param expressions
	 *            a list of AST expressions
	 * @return a list of IR expressions
	 */
	final List<Expression> transformExpressions(List<CgExpression> expressions) {
		int length = expressions.size();
		List<Expression> irExpressions = new ArrayList<Expression>(length);
		for (CgExpression expression : expressions) {
			irExpressions.add(transformer.transformExpr(expression));
		}

		return irExpressions;
	}

	/**
	 * Transforms the given AST expressions to a list of IR expressions and convert them to
	 * unsigned.
	 * 
	 * @param expressions
	 *            a list of AST expressions
	 * @return a list of IR expressions
	 */
	private List<Expression> transformIndexes(Type type, List<CgExpression> expressions) {
		List<Expression> irExpressions = transformExpressions(expressions);
		List<Expression> casted = new ArrayList<>(irExpressions.size());

		// cast indexes (only if type is an array => there are indexes to cast)
		if (type.isArray()) {
			Iterator<Integer> itD = ((TypeArray) type).getDimensions().iterator();

			// cast indexes
			for (Expression index : irExpressions) {
				int size = itD.next();
				int amount = TypeUtil.getSize(size - 1);
				casted.add(ir.castToUnsigned(amount, index));
			}
		}
		return casted;
	}

	/**
	 * Transforms the given Cx local variable/parameter to a new IR Var.
	 *
	 * @param variable
	 *            a variable
	 * @return an IR Var
	 */
	public Var transformLocal(Variable variable) {
		int lineNumber = getStartLine(variable);
		Type type = instantiator.computeType(entity, variable);
		String name = getUniqueName(variable.getName());
		boolean assignable = !CgUtil.isConstant(variable);

		// create local variable with the given name
		Var var = ir.createVar(lineNumber, type, name, assignable);
		localMap.put(variable, var);
		return var;
	}

	/**
	 * Flattens a struct-typed local declaration into N scalar IR Vars (one per
	 * field), names them with `$` mangling (`p$lo`, `p$hi`), and registers
	 * them in {@link #structFieldMap} so subsequent field accesses
	 * (`p.lo = 3`) resolve via {@link #getStructFieldVar(VarRef)}. Adds each
	 * synthesised Var to the current procedure's locals.
	 *
	 * <p>v1 supports scalar fields only — the design memo (§2) lists the
	 * deferred cases.
	 */
	public void transformStructLocal(Variable structVar, Struct struct) {
		int lineNumber = getStartLine(structVar);
		List<CgExpression> dimensions = structVar.getDimensions();
		Map<String, Var> fieldVars = new HashMap<>();
		for (StructLeaf leaf : leafFields(struct)) {
			// Tier 2.3: for an array of struct (`Pair ps[4]`) each leaf becomes
			// an array of the struct var's dimensions (`ps$lo[4]`); element
			// access `ps[i].lo` then indexes the leaf array.
			Type type = applyStructArrayDims(instantiator, entity,
					instantiator.computeType(entity, leaf.field), dimensions);
			String name = getUniqueName(structVar.getName() + "$" + leaf.path);
			Var var = ir.createVar(lineNumber, type, name, true);
			procedure.getLocals().add(var);
			fieldVars.put(leaf.path, var);
		}
		structFieldMap.put(structVar, fieldVars);
	}

	/**
	 * Wraps an element type in a {@link TypeArray} with the given
	 * (struct-variable) dimensions, mirroring {@code Typer}'s array handling.
	 * Returns the element type unchanged when there are no dimensions.
	 */
	public static Type applyStructArrayDims(IInstantiator instantiator, Entity entity,
			Type elementType, List<CgExpression> dimensions) {
		if (dimensions == null || dimensions.isEmpty()) {
			return elementType;
		}
		TypeArray array = ir.createTypeArray();
		array.setElementType(elementType);
		for (CgExpression dim : dimensions) {
			array.getDimensions().add(instantiator.evaluateInt(entity, dim));
		}
		return array;
	}

	/**
	 * Seeds this builder's struct-field lookup with pre-built field maps — used
	 * for struct STATE variables (bug #11), which {@code SkeletonMaker} flattens
	 * into persistent entity Vars before the body transform runs. Once
	 * imported, `state.field` access / whole-struct copy resolve them exactly
	 * like a struct local flattened by {@link #transformStructLocal}.
	 */
	protected void importStructFields(Map<Variable, Map<String, Var>> fields) {
		structFieldMap.putAll(fields);
	}

	/**
	 * Resolves the flattened leaf IR Var for the member-access continuation of
	 * an indexed struct reference — the `ps$lo` (or `ps$lo$a`) behind
	 * {@code ps[i].lo} / {@code ps[i].lo.a} (Tier 2.3). The source's leaf
	 * variable is the flattened struct local; the {@code members} field names,
	 * {@code $}-joined, form the leaf key. The caller applies the index
	 * expressions. Returns null if not a flattened struct member access.
	 */
	public Var getStructMemberVar(ExpressionVariable expr) {
		VarRef source = expr.getSource();
		if (source == null || expr.getMembers().isEmpty()) {
			return null;
		}
		Variable structVar = source.getVariable();
		if (structVar == null || structVar.eIsProxy()) {
			return null;
		}
		Map<String, Var> fieldVars = structFieldMap.get(structVar);
		if (fieldVars == null) {
			return null;
		}
		StringBuilder path = new StringBuilder();
		for (Named m : expr.getMembers()) {
			if (!(m instanceof Variable) || m.eIsProxy()) {
				return null;
			}
			if (path.length() > 0) {
				path.append('$');
			}
			path.append(((Variable) m).getName());
		}
		return fieldVars.get(path.toString());
	}

	/**
	 * If {@code ref} is a two-segment VarRef where the first segment is a
	 * struct-typed Variable and the second is a struct field, return the
	 * IR Var for that field. Otherwise return null.
	 *
	 * <p>Used by FunctionTransformer to route `p.lo` (or, for nested structs,
	 * `outer.lo.a`) through the flattened `p$lo` / `outer$lo$a` IR Var instead
	 * of the (nonexistent) whole-struct mapping. Segments after the root are
	 * joined with `$` to form the leaf key; an intermediate (struct-typed) path
	 * such as `outer.lo` is not a leaf and returns null so the caller falls
	 * back.
	 */
	public Var getStructFieldVar(VarRef ref) {
		List<Named> objects = ref.getObjects();
		if (objects.size() < 2) {
			return null;
		}
		Named parent = objects.get(0);
		if (!(parent instanceof Variable) || parent.eIsProxy()) {
			return null;
		}
		Map<String, Var> fieldVars = structFieldMap.get(parent);
		if (fieldVars == null) {
			return null;
		}
		StringBuilder path = new StringBuilder();
		for (int i = 1; i < objects.size(); i++) {
			Named seg = objects.get(i);
			if (!(seg instanceof Variable) || seg.eIsProxy()) {
				return null;
			}
			if (path.length() > 0) {
				path.append('$');
			}
			path.append(((Variable) seg).getName());
		}
		return fieldVars.get(path.toString());
	}

	/**
	 * Variable-typed convenience over the canonical
	 * {@link CgUtil#asStruct(CgType)}: if {@code variable}'s type resolves
	 * through a TypeRef to a {@link Struct}, return that Struct, else null.
	 */
	public static Struct asStructType(Variable variable) {
		return CgUtil.asStruct(CgUtil.getType(variable));
	}

	/**
	 * A leaf (primitive) field of a possibly-nested struct, paired with the
	 * {@code $}-mangled path from the struct root to that leaf — e.g.
	 * {@code "lo$a"} for {@code outer.lo.a}, or just {@code "lo"} for a
	 * non-nested field. Produced by {@link #leafFields(Struct)}.
	 */
	public static final class StructLeaf {
		/** {@code $}-joined path from the struct root to this leaf field. */
		public final String path;
		/** The primitive field Variable (for type computation / naming). */
		public final Variable field;

		StructLeaf(String path, Variable field) {
			this.path = path;
			this.field = field;
		}
	}

	/**
	 * Recursively flattens {@code struct} to its leaf primitive fields in
	 * declaration order, depth-first. A field whose type is itself a struct is
	 * expanded in place, its sub-fields prefixed with the field name + {@code $}
	 * (Tier 2.3 nested structs). The order is deterministic, as IrPathDriftTests
	 * requires. Cycle-safe: a struct that transitively contains itself is
	 * truncated at the repeat — the validator rejects such definitions
	 * ({@code errStructCycle}), but IR-gen must not stack-overflow if ever run on
	 * an unvalidated model.
	 */
	public static List<StructLeaf> leafFields(Struct struct) {
		List<StructLeaf> leaves = new ArrayList<>();
		collectLeafFields(struct, "", new java.util.LinkedHashSet<Struct>(), leaves);
		return leaves;
	}

	private static void collectLeafFields(Struct struct, String prefix,
			Set<Struct> active, List<StructLeaf> out) {
		if (struct == null || !active.add(struct)) {
			return; // null or a cycle — stop descending
		}
		for (Variable field : struct.getFields()) {
			if (field.getName() == null) {
				continue;
			}
			String path = prefix.isEmpty() ? field.getName() : prefix + "$" + field.getName();
			Struct nested = asStructType(field);
			if (nested != null) {
				collectLeafFields(nested, path, active, out);
			} else {
				out.add(new StructLeaf(path, field));
			}
		}
		active.remove(struct);
	}

	/**
	 * If {@code ref} is a one-segment reference to a struct-typed
	 * {@link Variable} (a whole-struct value such as {@code p} in
	 * {@code p2 = p;}), return that Variable; otherwise null. Two-segment
	 * refs ({@code p.lo}) are field accesses, not whole-struct values, and
	 * return null. Used by {@link FunctionTransformer} to detect the
	 * Tier 2.1 whole-struct copy/assign case.
	 */
	public static Variable asWholeStructVar(VarRef ref) {
		if (ref == null || ref.getObjects().size() != 1) {
			return null;
		}
		Named named = ref.getObjects().get(0);
		if (!(named instanceof Variable) || named.eIsProxy()) {
			return null;
		}
		Variable var = (Variable) named;
		return asStructType(var) != null ? var : null;
	}

	/**
	 * If {@code expr} is a one-segment reference to a struct-typed variable
	 * (the RHS of a whole-struct copy, e.g. {@code other} in
	 * {@code Pair p = other;}), return that source Variable; otherwise null.
	 */
	public static Variable asWholeStructValue(CgExpression expr) {
		if (!(expr instanceof ExpressionVariable)) {
			return null;
		}
		ExpressionVariable ev = (ExpressionVariable) expr;
		// An indexed array element (`ps[i]`) or a member-access continuation
		// (`ps[i].lo`) is not a whole-struct value — treating it as a copy
		// source would drop the index/member and miscompile.
		if (!ev.getIndexes().isEmpty() || !ev.getMembers().isEmpty()) {
			return null;
		}
		return asWholeStructVar(ev.getSource());
	}

	/**
	 * Emits a field-wise scalar copy of a whole struct value: for every field
	 * of {@code struct} in declaration order (deterministic — required for
	 * IrPathDriftTests byte-equality), stores {@code src$field} into
	 * {@code dst$field}. Both variables must already be flattened in
	 * {@link #structFieldMap} (i.e. each went through
	 * {@link #transformStructLocal}). Returns {@code true} if the copy was
	 * emitted, {@code false} if either side is not a known struct local so
	 * the caller can fall back. v1 supports scalar fields only — see
	 * .claude/L1_STRUCT_DESIGN.md §2.
	 */
	public boolean copyStruct(int lineNumber, Variable dst, Variable src, Struct struct) {
		Map<String, Var> dstFields = structFieldMap.get(dst);
		Map<String, Var> srcFields = structFieldMap.get(src);
		if (dstFields == null || srcFields == null) {
			return false;
		}
		for (StructLeaf leaf : leafFields(struct)) {
			Var dstVar = dstFields.get(leaf.path);
			Var srcVar = srcFields.get(leaf.path);
			if (dstVar == null || srcVar == null) {
				continue;
			}
			InstStore store = ir.createInstStore(lineNumber, dstVar,
					Collections.<Expression> emptyList(), ir.createExprVar(srcVar));
			add(store);
		}
		return true;
	}

	/**
	 * The flattened field-name -> IR Var map of a struct local, or null. Tier
	 * 2.2 port read/write lowering pairs these against a struct port's field
	 * ports by name.
	 */
	public Map<String, Var> getStructFieldVars(Variable structVar) {
		return structFieldMap.get(structVar);
	}

	/**
	 * Resolves a struct-typed port reference to its ordered field-name -> IR
	 * Port map (same-task or cross-task), or null if {@code ref} is not a
	 * struct port. See InstantiatorImpl#getStructPortFields.
	 */
	public Map<String, Port> getStructPortFields(VarRef ref) {
		return instantiator.getStructPortFields(entity, ref);
	}

	/**
	 * Tier 2.2 — lowers `dst = port.read()` to a field-wise load: for each
	 * field of {@code struct} in declaration order, loads the struct port's
	 * matching field port into {@code dst$field}. Returns false if {@code dst}
	 * is not a flattened struct local.
	 */
	public boolean loadStructFromPort(int lineNumber, Variable dst,
			Map<String, Port> portFields, Struct struct) {
		Map<String, Var> dstFields = structFieldMap.get(dst);
		if (dstFields == null || portFields == null) {
			return false;
		}
		for (StructLeaf leaf : leafFields(struct)) {
			Var dstVar = dstFields.get(leaf.path);
			Port srcPort = portFields.get(leaf.path);
			if (dstVar == null || srcPort == null) {
				continue;
			}
			add(ir.createInstLoad(lineNumber, dstVar, srcPort));
		}
		return true;
	}

	/**
	 * Tier 2.2 — lowers `port.write(src)` to a field-wise store: for each field
	 * of {@code struct} in declaration order, stores {@code src$field} into the
	 * struct port's matching field port (plus the field port's valid signals,
	 * if any). Returns false if {@code src} is not a flattened struct local.
	 */
	public boolean storeStructToPort(int lineNumber, Map<String, Port> portFields,
			Variable src, Struct struct) {
		Map<String, Var> srcFields = structFieldMap.get(src);
		if (srcFields == null || portFields == null) {
			return false;
		}
		for (StructLeaf leaf : leafFields(struct)) {
			Var srcVar = srcFields.get(leaf.path);
			Port port = portFields.get(leaf.path);
			if (srcVar == null || port == null) {
				continue;
			}
			add(ir.createInstStore(lineNumber, port, ir.createExprVar(srcVar)));
			for (Var signal : port.getAdditionalOutputs()) {
				add(ir.createInstStore(lineNumber, signal, ir.createExprBool(true)));
			}
		}
		return true;
	}

}
