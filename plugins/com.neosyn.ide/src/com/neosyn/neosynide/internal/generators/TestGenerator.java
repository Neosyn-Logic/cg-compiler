/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import static com.neosyn.core.IProperties.ACTIVE_LOW;
import static com.neosyn.core.IProperties.DEFAULT_CLOCK;
import static com.neosyn.core.IProperties.PROP_ACTIVE;
import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.core.IProperties.PROP_EMPTY;
import static com.neosyn.core.IProperties.PROP_NAME;
import static com.neosyn.core.IProperties.PROP_RESETS;
import static com.neosyn.core.IProperties.PROP_SYNTHETIC;
import static com.neosyn.core.IProperties.PROP_TEST;
import static com.neosyn.core.IProperties.PROP_TYPE;
import static com.neosyn.core.IProperties.RESET_ASYNCHRONOUS;
import static com.neosyn.models.ir.util.IrUtil.array;
import static com.neosyn.models.ir.util.IrUtil.obj;
import static java.lang.Math.max;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.xtext.xbase.lib.StringExtensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Goto;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstCall;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;

/**
 * Builds the simulation harness for a test network: it synthesises the stimulus and
 * expected-output actors that drive and check the design under test (marked
 * {@code PROP_SYNTHETIC + PROP_EMPTY}, later compiled by {@code ExternalActorCompiler} for
 * Fast Sim and by the testbench printers for HDL) from the network's {@code test} property.
 */
public class TestGenerator {

	/**
	 * df = Dpn Factory
	 */
	private DpnFactory df = DpnFactory.eINSTANCE;

	private IrFactory ir = IrFactory.eINSTANCE;

	private void addAssert(Action action, Var port, Expression value) {
		Expression assertExpr = ir.createExprBinary(ir.createExprVar(port), OpBinary.EQ, value);
		InstCall inst = ir.createInstCall();
		inst.setAssert(true);
		inst.getArguments().add(assertExpr);
		action.getBody().getLast().add(inst);
	}

	/**
	 * A port's test vectors as one flat list, and for a {@code framed} port the frame boundary of
	 * each (TRAPS T58). The syntax: each inner list is one frame, `last` on its final
	 * value -- `a: [[1, 2, 3], [4, 5]]`; a flat list on a framed port is ONE frame. A null entry
	 * drives or checks nothing, so `last` goes on the frame's last NON-null value.
	 * EntityPropertiesChecker has already refused nested lists on any other port.
	 */
	private static final class Vectors {

		final List<JsonElement> values = new ArrayList<>();

		/** null unless the port is framed */
		final List<Boolean> lasts;

		Vectors(Port port, JsonArray json) {
			lasts = port.isFramed() ? new ArrayList<>() : null;
			if (lasts == null) {
				json.forEach(values::add);
				return;
			}
			boolean nested = json.size() > 0 && json.get(0).isJsonArray();
			if (!nested) {
				addFrame(json);
				return;
			}
			for (JsonElement frame : json) {
				addFrame(frame.getAsJsonArray());
			}
		}

		private void addFrame(JsonArray frame) {
			int end = frame.size() - 1;
			while (end >= 0 && frame.get(end).isJsonNull()) {
				end--;
			}
			for (int i = 0; i < frame.size(); i++) {
				values.add(frame.get(i));
				lasts.add(i == end);
			}
		}

		int size() {
			return values.size();
		}
	}

	private void addLastPrint(Action action, Port port, int vector, boolean expected) {
		InstCall inst = ir.createInstCall();
		inst.setPrint(true);
		inst.getArguments().add(ir.createExprString("port " + port.getName() + ".last [vector "
				+ vector + "] expected " + expected + " -> "));
		inst.getArguments().add(ir.createExprVar(port.getLast()));
		action.getBody().getLast().add(inst);
	}

	private void addPrint(Action action, Port port, int vector, JsonPrimitive expected) {
		// One self-contained, machine-parseable line per checked port per vector:
		//   port <name> [vector <i>] expected <exp> -> <actual>
		// so a reader (human OR an LLM self-correcting from a failed run) sees the
		// port, which vector diverged, the expected value, and the actual value the
		// design produced — the actual is appended by the runtime print of the port.
		InstCall inst = ir.createInstCall();
		inst.setPrint(true);
		inst.getArguments().add(ir.createExprString("port " + port.getName() + " [vector "
				+ vector + "] expected " + expected.getAsString() + " -> "));
		inst.getArguments().add(ir.createExprVar(port));
		action.getBody().getLast().add(inst);
	}

	/**
	 * Add a new transition from source in the given FSM using the given action.
	 * 
	 * @param fsm
	 * @param source
	 * @param action
	 * @return the newly-created target state
	 */
	private State addTransition(FSM fsm, State source, Action action) {
		String name = "s" + (Integer.parseInt(source.getName().substring(1)) + 1);

		State target = df.createState(name);
		fsm.add(target);

		// add return true to scheduler
		InstReturn inst = ir.createInstReturn(ir.createExprBool(true));
		action.getScheduler().getLast().add(inst);

		// add goto target to body
		Goto stGoto = DpnFactory.eINSTANCE.createGoto();
		stGoto.setTarget(target);
		IrUtil.getLast(action.getBody().getBlocks()).add(stGoto);

		// add action to actor
		Actor actor = (Actor) fsm.eContainer();
		actor.getActions().add(action);
		action.getBody().setName("action_" + source.getName());
		action.getCombinational().setName("comb_" + source.getName());
		action.getScheduler().setName("isSchedulable_" + action.getBody().getName());

		// add transition referencing action
		Transition transition = df.createTransition(source, target);
		fsm.add(transition);
		transition.setAction(action);

		return target;
	}

	/**
	 * <p>
	 * Wraps the given entity in a network with two instances "stimulus" and "expected". The "stimulus"
	 * instance writes stimulus to <code>entity</code>, and the "expected" instance reads results from
	 * <code>entity</code> and asserts they match expected values.
	 * </p>
	 * 
	 * <p>
	 * The network returned has the same name as the entity.
	 * </p>
	 * 
	 * @param entity
	 * @return
	 */
	public DPN createDpn(Entity entity) {
		DPN dpn = df.createDPN();
		dpn.init();
		dpn.setFileName(entity.getFileName());
		dpn.setName(entity.getName());
		this.dutHasStreamPorts = hasStreamPorts(entity);

		// just to get access to resource set
		entity.eResource().getContents().add(dpn);

		// add dut instance
		String name = StringExtensions.toFirstLower(entity.getSimpleName());
		Instance dut = df.createInstance(name, entity);
		dpn.add(dut);

		setProperties(dut, entity);
		setProperties(dpn, entity);

		// here we go!
		createStimulus(dpn, dut);
		createExpected(dpn, dut);

		return dpn;
	}

	/** True when the DUT has stream ports (valid/ready handshake manages timing). */
	private boolean dutHasStreamPorts;

	private boolean hasStreamPorts(Entity entity) {
		for (Port port : entity.getInputs()) {
			if (port.getInterface() != null && port.getInterface().isSyncReady()) {
				return true;
			}
		}
		for (Port port : entity.getOutputs()) {
			if (port.getInterface() != null && port.getInterface().isSyncReady()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates an "expected" instance and connect the given DUT's outputs to its inputs.
	 * 
	 * @param dpn
	 *            wrapper DPN
	 * @param dut
	 *            Design Under Test
	 */
	private void createExpected(DPN dpn, Instance dut) {
		JsonArray clocks = dpn.getProperties().getAsJsonArray(PROP_CLOCKS);
		String clock = clocks.get(clocks.size() - 1).getAsString();
		Instance expected = createInstance(dpn, "expected", clock);

		Map<Port, Vectors> ports = new HashMap<>();
		JsonObject test = dut.getEntity().getProperties().getAsJsonObject(PROP_TEST);
		int numValues = 0;
		for (Port output : dut.getEntity().getOutputs()) {
			// Read the DUT output with the SAME interface it declares. A sync-ready
			// output keeps its valid/ready handshake so the checker WAITS for each
			// emitted value instead of sampling a fixed cycle — required when the DUT
			// emits at an irregular rate (e.g. a reduction that emits once per group),
			// where a fixed-cycle read would sample a held/stale value. (Previously
			// sync-ready/ack outputs were downgraded to push for single-task DUTs; for
			// an every-cycle emit the two are equivalent.)
			InterfaceType iface = output.getInterface();
			Port input = df.createPort(output.getType(), output.getName(), iface, output.isFramed(),
					expected.getEntity().getInputs());

			Endpoint source = new Endpoint(dut, output);
			Endpoint target = new Endpoint(expected, input);
			dpn.getGraph().add(df.createConnection(source, target));

			if (test != null) {
				JsonArray json = test.getAsJsonArray(input.getName());
				if (json != null) {
					Vectors values = new Vectors(input, json);
					numValues = max(numValues, values.size());
					ports.put(input, values);
				}
			}
		}

		if (numValues == 0) {
			expected.getEntity().getProperties().addProperty(PROP_EMPTY, true);
		}

		FSM fsm = createFsm(expected);
		State source = fsm.getInitialState();

		// Add an empty skip transition to account for the 1-cycle latency of synchronous
		// entities. Skip this for network DUTs with stream ports — stream handshake provides
		// natural synchronization via the valid/ready protocol, so the expected actor waits
		// for valid output data without needing a fixed skip cycle.
		if (!dutHasStreamPorts && dut.getEntity().getProperties().getAsJsonArray(PROP_CLOCKS).size() > 0) {
			Action action = df.createActionNop();
			source = addTransition(fsm, source, action);
		}

		// The checker says when it is done: a flag set by the action that checks the LAST vector,
		// and the synthetic network terminates on it. Without it the testbench generated from a
		// task's `test:` vectors checked every vector and then ran the clock until the harness
		// timed out -- iverilog could never report a pass (user report). The name must not
		// be one of the checker's ports, which are named after the DUT's outputs.
		Var allChecked = null;
		if (numValues > 0) {
			Set<String> taken = new HashSet<>();
			for (Port input : expected.getEntity().getInputs()) {
				taken.add(input.getName());
			}
			String name = "all_checked";
			for (int n = 1; taken.contains(name); n++) {
				name = "all_checked_" + n;
			}
			allChecked = ir.createVar(ir.createTypeBool(), name, true);
			allChecked.setInitialValue(ir.createExprBool(false));
			((Actor) expected.getEntity()).getVariables().add(allChecked);
		}

		for (int i = 0; i < numValues; i++) {
			Action action = df.createActionNop();
			for (Port input : expected.getEntity().getInputs()) {
				Vectors values = ports.get(input);
				if (values != null && i < values.size() && !values.values.get(i).isJsonNull()) {
					action.getInputPattern().add(input);

					JsonPrimitive primitive = values.values.get(i).getAsJsonPrimitive();
					Expression value = getExpression(input.getType(), primitive);
					addPrint(action, input, i, primitive);
					addAssert(action, input, value);
					if (values.lasts != null) {
						// the frame boundary is checked with its value (TRAPS T58)
						boolean last = values.lasts.get(i);
						addLastPrint(action, input, i, last);
						addAssert(action, input.getLast(), ir.createExprBool(last));
					}
				}
			}
			if (i == numValues - 1 && allChecked != null) {
				action.getBody().getLast().add(ir.createInstStore(allChecked, ir.createExprBool(true)));
			}

			source = addTransition(fsm, source, action);
		}
		if (allChecked != null) {
			JsonObject terminate = new JsonObject();
			terminate.addProperty("terminate", expected.getName() + "." + allChecked.getName());
			dpn.getProperties().add(PROP_TEST, terminate);
		}

		// Guarantee at least one action on the synthetic 'expected' actor. When
		// the DUT output is a stream port the skip-cycle action above is
		// suppressed (the valid/ready handshake provides timing), and when the
		// test block carries no values for it the value loop adds nothing — so
		// the actor would be left with zero actions, which crashes
		// ExternalActorCompiler / the HDL printers (NoSuchElement on the empty
		// action list). This happens e.g. when simulating a network whose child
		// tasks expose stream ports but have no `test:` arrays. Mirror the
		// empty-stimulus path: add a single "always true" no-op action.
		Actor actor = (Actor) expected.getEntity();
		if (actor.getActions().isEmpty()) {
			Action action = df.createActionNop();
			action.getScheduler().getLast().add(ir.createInstReturn(ir.createExprBool(true)));
			actor.getActions().add(action);
			action.getBody().setName("action_main");
			action.getCombinational().setName("comb_main");
			action.getScheduler().setName("isSchedulable_main");
		}
	}

	private FSM createFsm(Instance instance) {
		FSM fsm = df.createFSM();
		((Actor) instance.getEntity()).setFsm(fsm);

		State state = df.createState("s0");
		fsm.add(state);
		fsm.setInitialState(state);

		return fsm;
	}

	/**
	 * Creates an instance of a newly-created actor using the given name, and adds it to the dpn.
	 * 
	 * @param dpn
	 *            wrapper DPN
	 * @param name
	 *            name of the instance to create
	 * @return the new instance
	 */
	private Instance createInstance(DPN dpn, String name, String clock) {
		Actor actor = df.createActor();
		actor.setName(dpn.getPackage() + "." + dpn.getSimpleName() + "_" + name);
		actor.setFileName(dpn.getFileName());

		// set actor properties
		actor.setProperties(obj(PROP_CLOCKS, array(DEFAULT_CLOCK), PROP_RESETS,
				array(obj(PROP_TYPE, RESET_ASYNCHRONOUS, PROP_ACTIVE, ACTIVE_LOW, PROP_NAME, "reset_n")),
				PROP_SYNTHETIC, true));

		Instance instance = df.createInstance(name, actor);
		dpn.add(instance);

		// set instance properties
		instance.setProperties(obj(PROP_CLOCKS, obj("clock", clock), PROP_RESETS, obj("reset_n", "reset_n")));

		return instance;
	}

	/**
	 * Creates a "stimulus" instance and connect its outputs to the given DUT's inputs.
	 * 
	 * @param dpn
	 *            wrapper DPN
	 * @param dut
	 *            Design Under Test
	 */
	private void createStimulus(DPN dpn, Instance dut) {
		JsonArray clocks = dpn.getProperties().getAsJsonArray(PROP_CLOCKS);
		String clock = clocks.get(0).getAsString();
		JsonObject test = dut.getEntity().getProperties().getAsJsonObject(PROP_TEST);

		// ONE stimulus instance PER input port, each with its OWN FSM — rather than a
		// single lockstep FSM whose every state writes every port. Per-port so inputs
		// consumed at DIFFERENT rates advance INDEPENDENTLY: a stream read once per
		// group (`if (cnt == 0) bias.read()`) is presented exactly when the DUT takes
		// it, paced by that port's ready. The lockstep stimulus advanced all ports
		// together, so it presented a slow-rate input on the wrong cycle and the DUT
		// dropped it (a conditional-read reduction then read a stale value). For inputs
		// read every cycle — the common case — each per-port FSM still advances every
		// cycle, so the behaviour (and timing) is unchanged.
		for (Port input : dut.getEntity().getInputs()) {
			// Drive the stimulus output with the SAME interface the DUT input
			// declares. A sync-ready input keeps its ready handshake so the per-port
			// stimulus is PACED by the DUT actually taking each value — required for a
			// CONDITIONAL read (a value read once per group must be presented on the
			// cycle the DUT reads it, not every cycle). (Previously sync-ready/ack task
			// inputs were downgraded to push here, which drives every cycle regardless
			// of ready and so mis-paced conditional reads; for an every-cycle read the
			// two are equivalent because ready is asserted every cycle.)
			InterfaceType iface = input.getInterface();

			Instance stimulus = createInstance(dpn, "stimulus_" + input.getName(), clock);
			// associate this stimulus's reset with the shared reset_stimulus
			stimulus.getProperties().add(PROP_RESETS, obj("reset_n", "reset_stimulus"));

			Port output = df.createPort(input.getType(), input.getName(), iface, input.isFramed(),
					stimulus.getEntity().getOutputs());
			dpn.getGraph().add(df.createConnection(
					new Endpoint(stimulus, output), new Endpoint(dut, input)));

			JsonArray json = (test != null) ? test.getAsJsonArray(input.getName()) : null;
			if (json == null || json.size() == 0) {
				makeEmptyStimulus(stimulus);
				continue;
			}
			Vectors values = new Vectors(input, json);

			FSM fsm = createFsm(stimulus);
			State source = fsm.getInitialState();
			for (int i = 0; i < values.size(); i++) {
				Action action = df.createActionNop();
				BlockBasic block = action.getBody().getLast();

				// A null vector entry drives nothing this step (the FSM still advances).
				if (!values.values.get(i).isJsonNull()) {
					action.getOutputPattern().add(output);
					Expression value = getExpression(output.getType(), values.values.get(i).getAsJsonPrimitive());
					block.add(ir.createInstStore(0, output, value));
					storeSignals(block, output, values, i);
				}

				source = addTransition(fsm, source, action);
			}

			// Keep driving the LAST value, with valid asserted, instead of going quiet.
			//
			// The terminal state was empty, and `valid` is defaulted low every cycle, so
			// the stimulus stopped dead after N vectors. A PIPELINED dut cannot drain in
			// that time: in a two-stage network the first stage's output is registered, so
			// it reaches the second stage a cycle after the inputs that produced it -- by
			// which time the other stimulus has dropped its valid and the second stage
			// never fires for the last datum. The result was a network that checked N-1 of
			// N vectors (all of them CORRECT) and then hung forever, because `expected`
			// was still waiting for a value that could no longer arrive.
			//
			// Restricted to a DPN dut on purpose. A task dut has a single cycle of latency,
			// which the skip cycle in createExpected already covers, and every task fixture
			// passes today; holding there would drive extra values that nothing checks and
			// could change any fixture that counts its inputs.
			int end = values.size() - 1;
			if (dut.getEntity() instanceof DPN && !values.values.get(end).isJsonNull()) {
				Action hold = df.createActionNop();
				BlockBasic holdBlock = hold.getBody().getLast();
				hold.getOutputPattern().add(output);
				Expression last = getExpression(output.getType(),
						values.values.get(end).getAsJsonPrimitive());
				holdBlock.add(ir.createInstStore(0, output, last));
				storeSignals(holdBlock, output, values, end);
				addSelfTransition(fsm, source, hold);
			}
		}
	}

	/**
	 * Raises the handshake signals of a stimulus output for vector {@code i}; on a framed port
	 * {@code last} is not a handshake but the vector's frame boundary (TRAPS T58).
	 */
	private void storeSignals(BlockBasic block, Port output, Vectors values, int i) {
		for (Var signal : output.getAdditionalOutputs()) {
			boolean high = signal == output.getLast() ? values.lasts.get(i) : true;
			block.add(ir.createInstStore(0, signal, ir.createExprBool(high)));
		}
	}

	/**
	 * Like {@link #addTransition} but loops the state back to itself, so the action keeps
	 * firing rather than the FSM falling into an empty terminal state.
	 */
	private void addSelfTransition(FSM fsm, State state, Action action) {
		action.getScheduler().getLast().add(ir.createInstReturn(ir.createExprBool(true)));

		Goto stGoto = DpnFactory.eINSTANCE.createGoto();
		stGoto.setTarget(state);
		IrUtil.getLast(action.getBody().getBlocks()).add(stGoto);

		Actor actor = (Actor) fsm.eContainer();
		actor.getActions().add(action);
		action.getBody().setName("action_" + state.getName() + "_hold");
		action.getCombinational().setName("comb_" + state.getName() + "_hold");
		action.getScheduler().setName("isSchedulable_" + action.getBody().getName());

		Transition transition = df.createTransition(state, state);
		fsm.add(transition);
		transition.setAction(action);
	}

	/** Marks a stimulus instance as having no test values and gives it a single no-op action. */
	private void makeEmptyStimulus(Instance stimulus) {
		stimulus.getEntity().getProperties().addProperty(PROP_EMPTY, true);

		Action action = df.createActionNop();
		action.getScheduler().getLast().add(ir.createInstReturn(ir.createExprBool(true)));

		Actor actor = (Actor) stimulus.getEntity();
		actor.getActions().add(action);
		action.getBody().setName("action_main");
		action.getCombinational().setName("comb_main");
		action.getScheduler().setName("isSchedulable_main");
	}

	private Expression getExpression(Type target, JsonPrimitive primitive) {
		// Coerce the literal to the PORT's type so the generated stimulus-store
		// and expected-compare stay same-typed. A bool port written/checked with
		// numeric 0/1 vectors (the shape an LLM naturally writes for a flag), or an
		// int port with true/false, would otherwise build a mixed bool/int
		// comparison — which has no common type and NPEs the bytecode compiler.
		boolean targetIsBool = target != null && target.isBool();
		boolean targetIsInt = target != null && target.isInt();
		Expression expr;
		if (primitive.isBoolean()) {
			boolean b = primitive.getAsBoolean();
			expr = targetIsInt
					? ir.createExprInt(b ? BigInteger.ONE : BigInteger.ZERO)
					: ir.createExprBool(b);
		} else if (primitive.isNumber()) {
			expr = targetIsBool
					? ir.createExprBool(primitive.getAsBigInteger().signum() != 0)
					: ir.createExprInt(primitive.getAsBigInteger());
		} else if (primitive.isString()) {
			expr = ir.createExprString(primitive.getAsString());
		} else {
			return null;
		}

		return expr;
	}

	private void setProperties(DPN dpn, Entity entity) {
		JsonObject properties = new JsonObject();
		properties.add(PROP_SYNTHETIC, new JsonPrimitive(true));

		JsonArray clocks = new JsonArray();
		JsonArray clocksDeclared = entity.getProperties().getAsJsonArray(PROP_CLOCKS);
		if (clocksDeclared.size() > 0) {
			for (JsonElement clock : clocksDeclared) {
				clocks.add(clock);
			}
		} else {
			// DPN must always have a clock (even though DUT may not)
			clocks.add(DEFAULT_CLOCK);
		}
		properties.add(PROP_CLOCKS, clocks);

		// note: order of resets is important, stimulus needs to be reset first
		JsonArray resets = array(obj("name", "reset_stimulus"), obj("name", "reset_n"));
		properties.add(PROP_RESETS, resets);

		dpn.setProperties(properties);
	}

	private void setProperties(Instance dut, Entity entity) {
		JsonObject clocks = new JsonObject();
		JsonObject resetAssoc = new JsonObject();

		JsonArray clocksDeclared = entity.getProperties().getAsJsonArray(PROP_CLOCKS);
		if (clocksDeclared.size() > 0) {
			// if DUT has clocks
			for (JsonElement clock : clocksDeclared) {
				String clockName = clock.getAsString();
				clocks.add(clockName, new JsonPrimitive(clockName));
			}

			JsonArray resets = entity.getProperties().getAsJsonArray(PROP_RESETS);
			for (JsonElement reset : resets) {
				String resetName = reset.getAsJsonObject().getAsJsonPrimitive(PROP_NAME).getAsString();
				resetAssoc.add(resetName, new JsonPrimitive(resetName));
			}
		}

		dut.setProperties(obj(PROP_CLOCKS, clocks, PROP_RESETS, resetAssoc));
	}

}
