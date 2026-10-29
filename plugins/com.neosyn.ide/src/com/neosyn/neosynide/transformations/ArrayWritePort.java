/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neosyn.core.transformations.Transformation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.BlockIf;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprBool;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.ExprUnary;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.EcoreHelper;
import com.neosyn.models.util.Void;

/**
 * Gives a task array ONE registered write port, so synthesis can map it to RAM (TRAPS T45).
 *
 * <p>Measured on the Bus AXI-Lite slave (Yosys synth_xilinx): an array written inside an action
 * never became a RAM, for two reasons that BOTH had to go -- every action that reads and writes it
 * copied the whole array in and out (StoreOnceTransformation's local copy), and the write sat in
 * the task's asynchronous-reset process, where a RAM write port cannot be. With one write per
 * cycle in a clock-only process the slave went from 255 LUT / 328 FF to 121 / 108 plus 6 LUTRAM.
 *
 * <p>For a qualifying array {@code a}, three state variables are added and every access rewritten:
 * <pre>
 *   a[i] = v;      becomes   a_we = true; a_waddr = i; a_wdata = v;
 *   t = a[j];      becomes   t = a[j]; if (a_we &amp;&amp; a_waddr == j) t = a_wdata;
 * </pre>
 * The Verilog printer then writes {@code a[a_waddr] <= a_wdata} from its own
 * {@code always @(posedge clock)} block when {@code a_we}, and defaults {@code a_we} to 0 every
 * cycle. The write lands one clock later than before; the bypass on every read returns the value
 * just written until then, so what every read returns is unchanged cycle for cycle. The port
 * registers are read directly (not loaded), so the store-once pass leaves their stores as plain
 * non-blocking assignments and a write that is not taken leaves {@code a_we} at its default.
 *
 * <p>An array qualifies only when that rewrite is exact: one dimension; every access indexed;
 * written only in action bodies, at most once per procedure and never inside a loop; and never
 * read AFTER its write in the same procedure (that read must see the new value, which is what the
 * local copy is for). Anything else is left to the existing code, which is correct, only large.
 *
 * <p>Both HDL back ends (Verilog first, `246ac1e`; VHDL next); the bytecode
 * simulator is untouched. The arrays are recorded in the actor's {@link #PROP_RAM_PORTS} property
 * for the printers. VHDL used to RESET arrays; a ported one is not reset (a RAM cannot be) and gets
 * the time-zero value 0 instead, as Verilog's zero-fill does.
 */
public class ArrayWritePort extends Transformation {

	/** Actor property: one {@code {array, we, waddr, wdata}} object per ported array. */
	public static final String PROP_RAM_PORTS = "ramPorts";

	private static final IrFactory ir = IrFactory.eINSTANCE;

	/**
	 * VHDL only: leave alone an array with a read whose index is fixed at elaboration. Measured on
	 * AES (`win[Nk - 1]`): GHDL's synthesis keeps no memory with a constant-address read, so the
	 * port only added registers (TopAes 1791 -> 1883 LUT); with that read made variable GHDL built
	 * a RAM (1706). Yosys reading the Verilog maps the same array to RAM either way.
	 */
	private final boolean skipConstantIndexReads;

	public ArrayWritePort() {
		this(false);
	}

	public ArrayWritePort(boolean skipConstantIndexReads) {
		super(null);
		this.skipConstantIndexReads = skipConstantIndexReads;
	}

	@Override
	public Void caseActor(Actor actor) {
		JsonArray clocks = actor.getProperties().getAsJsonArray(PROP_CLOCKS);
		if (clocks == null || clocks.size() == 0) {
			return DONE; // a combinational task has no clocked process to take a write port
		}

		List<Procedure> bodies = new ArrayList<>();
		List<Procedure> procedures = new ArrayList<>();
		for (Action action : actor.getActions()) {
			bodies.add(action.getBody());
			procedures.addAll(Arrays.asList(action.getBody(), action.getCombinational(), action.getScheduler()));
		}
		procedures.addAll(actor.getProcedures());

		JsonArray ports = new JsonArray();
		for (Var array : new ArrayList<>(actor.getVariables())) {
			if (qualifies(array, bodies, procedures)) {
				ports.add(port(actor, array));
			}
		}
		if (ports.size() > 0) {
			actor.getProperties().add(PROP_RAM_PORTS, ports);
		}
		return DONE;
	}

	private boolean qualifies(Var array, List<Procedure> bodies, List<Procedure> procedures) {
		if (!array.isAssignable() || !array.getType().isArray()
				|| ((TypeArray) array.getType()).getDimensions().size() != 1) {
			return false;
		}
		// every reference is an indexed load, an indexed store, or a whole-array copy that is only
		// ever read by index -- nothing else touches it
		for (Use use : array.getUses()) {
			if (detached(use)) {
				continue;
			}
			if (!(use.eContainer() instanceof InstLoad)) {
				return false;
			}
			InstLoad load = (InstLoad) use.eContainer();
			if (load.getIndexes().isEmpty() ? !isIndexedOnlyCopy(load, bodies) : load.getIndexes().size() != 1) {
				return false;
			}
			if (skipConstantIndexReads && readsAConstantIndex(load)) {
				return false;
			}
		}
		boolean written = false;
		for (Def def : array.getDefs()) {
			if (detached(def)) {
				continue;
			}
			if (!(def.eContainer() instanceof InstStore) || ((InstStore) def.eContainer()).getIndexes().size() != 1) {
				return false;
			}
			written = true;
		}
		if (!written) {
			return false; // a table nothing writes is a ROM; not this pass's business
		}

		for (Procedure procedure : procedures) {
			if (procedure == null) {
				continue;
			}
			List<Instruction> order = new ArrayList<>();
			flatten(procedure.getBlocks(), order);
			int stores = 0;
			for (Instruction inst : order) {
				if (inst instanceof InstStore && ((InstStore) inst).getTarget().getVariable() == array) {
					if (!bodies.contains(procedure) || ++stores > 1 || inLoop(inst)) {
						return false;
					}
				} else if (inst instanceof InstLoad && ((InstLoad) inst).getSource().getVariable() == array && stores > 0) {
					return false; // read after the write: must see the new value
				}
			}
		}
		return true;
	}

	/** Instructions in PROGRAM order: then, else, join. (EMF's own order is alphabetical.) */
	private void flatten(List<Block> blocks, List<Instruction> order) {
		for (Block block : blocks) {
			if (block instanceof BlockBasic) {
				order.addAll(((BlockBasic) block).getInstructions());
			} else if (block instanceof BlockIf) {
				BlockIf blockIf = (BlockIf) block;
				flatten(blockIf.getThenBlocks(), order);
				flatten(blockIf.getElseBlocks(), order);
				if (blockIf.getJoinBlock() != null) {
					order.addAll(blockIf.getJoinBlock().getInstructions());
				}
			} else if (block instanceof BlockWhile) {
				BlockWhile blockWhile = (BlockWhile) block;
				flatten(blockWhile.getBlocks(), order);
				if (blockWhile.getJoinBlock() != null) {
					order.addAll(blockWhile.getJoinBlock().getInstructions());
				}
			}
		}
	}

	private static boolean inLoop(Instruction inst) {
		for (Object o = inst.eContainer(); o != null; o = ((org.eclipse.emf.ecore.EObject) o).eContainer()) {
			if (o instanceof BlockWhile) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Measured: every task whose `if` is developed into several actions carries uses of its arrays
	 * that belong to no procedure at all -- ExprVar objects with no container, still registered on
	 * the variable. They cannot reach the generated code, so they do not count.
	 */
	private static boolean detached(org.eclipse.emf.ecore.EObject ref) {
		return EcoreHelper.getContainerOfType(ref, Procedure.class) == null;
	}

	/**
	 * A scheduler function starts by copying the state it reads into locals -- for an array, the
	 * WHOLE array ({@code local_regs = regs}), later read by index. When that local is assigned
	 * nowhere else and read only by index, each read is a read of the array itself.
	 */
	private static boolean isIndexedOnlyCopy(InstLoad load, List<Procedure> bodies) {
		Var local = load.getTarget().getVariable();
		if (bodies.contains(EcoreHelper.getContainerOfType(load, Procedure.class)) || !local.isLocal()
				|| local.getDefs().size() != 1) {
			return false;
		}
		for (Use use : local.getUses()) {
			if (!(use.eContainer() instanceof InstLoad) || ((InstLoad) use.eContainer()).getIndexes().size() != 1) {
				return false;
			}
		}
		return true;
	}

	/** True if a read of the array (direct, or through a scheduler's copy) has a constant index. */
	private static boolean readsAConstantIndex(InstLoad load) {
		if (!load.getIndexes().isEmpty()) {
			return isConstant(load.getIndexes().get(0), 0);
		}
		for (Use use : load.getTarget().getVariable().getUses()) {
			if (isConstant(((InstLoad) use.eContainer()).getIndexes().get(0), 0)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Fixed at elaboration: literals, constants and generics (not assignable AND carrying a value --
	 * an input port is not assignable either, but has none), and operators over them. A local
	 * assigned exactly once from such an expression, or loaded once from such a scalar (the way a
	 * scheduler reads `Nk` into `Nk_0`), counts too.
	 */
	private static boolean isConstant(Expression expr, int depth) {
		if (depth > 16) {
			return false;
		}
		if (expr instanceof ExprInt || expr instanceof ExprBool) {
			return true;
		}
		if (expr instanceof ExprVar) {
			return isConstant(((ExprVar) expr).getUse().getVariable(), depth);
		}
		if (expr instanceof ExprBinary) {
			return isConstant(((ExprBinary) expr).getE1(), depth + 1) && isConstant(((ExprBinary) expr).getE2(), depth + 1);
		}
		if (expr instanceof ExprUnary) {
			return isConstant(((ExprUnary) expr).getExpr(), depth + 1);
		}
		if (expr instanceof ExprResize) {
			return isConstant(((ExprResize) expr).getExpr(), depth + 1);
		}
		if (expr instanceof ExprTypeConv) {
			return isConstant(((ExprTypeConv) expr).getExpr(), depth + 1);
		}
		return false;
	}

	private static boolean isConstant(Var v, int depth) {
		if (!v.isAssignable()) {
			return v.getInitialValue() != null && !v.getType().isArray();
		}
		if (!v.isLocal() || v.getDefs().size() != 1) {
			return false;
		}
		Object def = v.getDefs().get(0).eContainer();
		if (def instanceof InstAssign) {
			return isConstant(((InstAssign) def).getValue(), depth + 1);
		}
		if (def instanceof InstLoad && ((InstLoad) def).getIndexes().isEmpty()) {
			return isConstant(((InstLoad) def).getSource().getVariable(), depth + 1);
		}
		return false;
	}

	private JsonObject port(Actor actor, Var array) {
		TypeArray type = (TypeArray) array.getType();
		int depth = type.getDimensions().get(0);
		int line = array.getLineNumber();
		Var we = ir.createVar(line, ir.createTypeBool(), freshName(actor, array.getName() + "_we"), true);
		Var waddr = ir.createVar(line, ir.createTypeInt(TypeUtil.getSize(Math.max(depth - 1, 1)), false),
				freshName(actor, array.getName() + "_waddr"), true);
		Var wdata = ir.createVar(line, IrUtil.copy(type.getElementType()), freshName(actor, array.getName() + "_wdata"), true);
		int at = actor.getVariables().indexOf(array) + 1;
		actor.getVariables().addAll(at, Arrays.asList(we, waddr, wdata));

		// a scheduler's whole-array copy: its indexed reads become reads of the array, then it goes
		for (Use use : new ArrayList<>(array.getUses())) {
			if (!detached(use) && ((InstLoad) use.eContainer()).getIndexes().isEmpty()) {
				InstLoad copy = (InstLoad) use.eContainer();
				Var local = copy.getTarget().getVariable();
				for (Use read : new ArrayList<>(local.getUses())) {
					read.setVariable(array);
				}
				IrUtil.delete(copy);
				org.eclipse.emf.ecore.util.EcoreUtil.remove(local);
			}
		}

		for (Def def : new ArrayList<>(array.getDefs())) {
			if (detached(def)) {
				continue;
			}
			InstStore store = (InstStore) def.eContainer();
			BlockBasic block = (BlockBasic) store.eContainer();
			int line2 = store.getLineNumber();
			block.getInstructions().addAll(block.indexOf(store), Arrays.<Instruction> asList(
					ir.createInstStore(line2, we, ir.createExprBool(true)),
					ir.createInstStore(line2, waddr, IrUtil.copy(store.getIndexes().get(0))),
					ir.createInstStore(line2, wdata, IrUtil.copy(store.getValue()))));
			IrUtil.delete(store);
		}

		// Every read gets the bypass as an `if` after the load -- the shape the front end gives a
		// source ternary. (An ExprTernary is new to the Verilog passes: none descends into one, so a
		// cast in the copied index reached the printer unextracted and generation failed.)
		for (Use use : new ArrayList<>(array.getUses())) {
			if (detached(use)) {
				continue;
			}
			InstLoad load = (InstLoad) use.eContainer();
			Var target = load.getTarget().getVariable();
			BlockBasic block = (BlockBasic) load.eContainer();
			List<Block> blocks = EcoreHelper.getContainingList(block);

			BlockBasic after = ir.createBlockBasic();
			List<Instruction> rest = block.getInstructions();
			after.getInstructions().addAll(new ArrayList<>(rest.subList(rest.indexOf(load) + 1, rest.size())));

			BlockIf bypass = ir.createBlockIf();
			bypass.setJoinBlock(ir.createBlockBasic());
			bypass.setLineNumber(load.getLineNumber());
			bypass.setCondition(ir.createExprBinary(ir.createExprVar(we), OpBinary.LOGIC_AND,
					ir.createExprBinary(ir.createExprVar(waddr), OpBinary.EQ, IrUtil.copy(load.getIndexes().get(0)))));
			BlockBasic then = ir.createBlockBasic();
			InstAssign forward = ir.createInstAssign(target, ir.createExprVar(wdata));
			forward.setLineNumber(load.getLineNumber());
			then.add(forward);
			bypass.getThenBlocks().add(then);

			int pos = blocks.indexOf(block);
			blocks.add(pos + 1, bypass);
			blocks.add(pos + 2, after);
		}

		JsonObject port = new JsonObject();
		port.addProperty("array", array.getName());
		port.addProperty("we", we.getName());
		port.addProperty("waddr", waddr.getName());
		port.addProperty("wdata", wdata.getName());
		return port;
	}

	/** One ported array and its three port registers, as the printer needs them. */
	public static final class RamPort {
		public final Var array, we, waddr, wdata;

		RamPort(Var array, Var we, Var waddr, Var wdata) {
			this.array = array;
			this.we = we;
			this.waddr = waddr;
			this.wdata = wdata;
		}
	}

	/** The arrays of {@code actor} this pass gave a write port; empty if none. */
	public static List<RamPort> ports(Actor actor) {
		List<RamPort> result = new ArrayList<>();
		JsonArray ports = actor.getProperties().getAsJsonArray(PROP_RAM_PORTS);
		if (ports == null) {
			return result;
		}
		for (int i = 0; i < ports.size(); i++) {
			JsonObject p = ports.get(i).getAsJsonObject();
			result.add(new RamPort(var(actor, p, "array"), var(actor, p, "we"), var(actor, p, "waddr"),
					var(actor, p, "wdata")));
		}
		return result;
	}

	private static Var var(Actor actor, JsonObject port, String key) {
		String name = port.get(key).getAsString();
		for (Var v : actor.getVariables()) {
			if (v.getName().equals(name)) {
				return v;
			}
		}
		throw new IllegalStateException("ramPorts names a variable the actor does not have: " + name);
	}

	/** A state variable name no variable, port or procedure of the actor already uses. */
	private static String freshName(Actor actor, String hint) {
		// compared without case: VHDL folds it, so `a_we` and `A_WE` would be one declaration
		Set<String> taken = new HashSet<>();
		for (Var v : actor.getVariables()) {
			taken.add(v.getName().toLowerCase());
		}
		for (Port p : actor.getInputs()) {
			taken.add(p.getName().toLowerCase());
		}
		for (Port p : actor.getOutputs()) {
			taken.add(p.getName().toLowerCase());
		}
		for (Procedure p : actor.getProcedures()) {
			taken.add(p.getName().toLowerCase());
		}
		String name = hint;
		for (int i = 1; taken.contains(name.toLowerCase()); i++) {
			name = hint + "_" + i;
		}
		return name;
	}

}
