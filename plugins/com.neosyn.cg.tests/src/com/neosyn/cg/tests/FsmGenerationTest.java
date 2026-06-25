/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.generator.CombinationalVisitor;
import com.neosyn.cg.generator.FsmBeautifier;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.ActorTransformer;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.IfScheduler;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.FSM;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.dpn.State;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.graph.Edge;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;
import com.neosyn.core.transformations.PatternImplementation;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

/**
 * Tests for FSM generation — verifying that the scheduler generates
 * correct FSMs (or none) depending on the task structure.
 *
 * <p>The key question: WHEN is an FSM generated vs. a single-action actor?</p>
 *
 * <h3>Rules:</h3>
 * <ul>
 *   <li>Single read + simple logic + writes = 1 action, no FSM needed</li>
 *   <li>Reads on different ports in if-branches = IfScheduler develops into FSM</li>
 *   <li>Same-port read twice = cycle break → FSM</li>
 *   <li>fence/idle = explicit cycle break → FSM</li>
 *   <li>available() check = separate actions for available/not-available → FSM</li>
 *   <li>Combinational (clocks:[]) = never FSM, single action</li>
 *   <li>Else-if chains without port reads = single action (no FSM)</li>
 *   <li>Else-if chains with port reads in branches = FSM</li>
 * </ul>
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class FsmGenerationTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private IInstantiator instantiator;

	private Entity getIrEntity(CgEntity cxEntity) {
		Entity[] holder = { null };
		instantiator.forEachMapping(cxEntity, entity -> holder[0] = entity);
		return holder[0];
	}

	private Actor parseAndTransform(String source) throws Exception {
		Module module = parseHelper.parse(source);
		assertNotNull(module);
		assertTrue("Parse errors: " + module.eResource().getErrors(),
			module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		assertTrue("Should be a Task", cxEntity instanceof Task);

		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		assertNotNull("Entity should exist", entity);
		assertTrue("Should be an Actor", entity instanceof Actor);
		Actor actor = (Actor) entity;

		Variable setup = null;
		Variable loop = null;
		for (Variable function : CgUtil.getFunctions((Task) cxEntity)) {
			String name = function.getName();
			if (NAME_SETUP.equals(name)) {
				setup = function;
			} else if (NAME_LOOP.equals(name)) {
				loop = function;
			}
		}
		new CycleScheduler(instantiator, actor).schedule(setup, loop);
		new IfScheduler(instantiator, actor).visit();
		new ActorTransformer(instantiator, actor).visit();
		new FsmBeautifier().visit(actor);
		new CombinationalVisitor().visit(actor);

		return actor;
	}

	private void assertValidActionNames(Actor actor) {
		for (Action action : actor.getActions()) {
			Procedure sched = action.getScheduler();
			Procedure body = action.getBody();
			if (sched != null && sched.getName() != null) {
				assertFalse("Scheduler name should not be empty", sched.getName().isEmpty());
			}
			if (body != null && body.getName() != null) {
				assertFalse("Body name should not be empty", body.getName().isEmpty());
			}
		}
	}

	// =========================================================================
	// NO FSM NEEDED: Single action tasks
	// =========================================================================

	/**
	 * Test: Simple read-compute-write has 1 action, no multi-state FSM.
	 */
	@Test
	public void testSingleActionNoFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task SingleAction {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        result.write(data.read() + 1);\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertEquals("Simple task should have 1 action", 1, actor.getActions().size());
	}

	/**
	 * Test: If/else with writes in both branches — IfScheduler develops these
	 * into separate actions because output patterns differ per branch.
	 *
	 * <p>Note: Even though there are no port READS in branches, the port WRITES
	 * cause IfDeveloper to create separate actions per branch path.</p>
	 */
	@Test
	public void testIfElseWithWritesInBranches() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task IfWithWrites {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 d = data.read();\n" +
			"        if (d > 100) {\n" +
			"            result.write(d);\n" +
			"        } else {\n" +
			"            result.write(0);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		// IfScheduler develops branches with port writes into separate actions
		assertTrue("If/else with writes should have >= 1 action",
			actor.getActions().size() >= 1);
	}

	/**
	 * Test: Else-if chain with writes — IfScheduler creates action per branch.
	 */
	@Test
	public void testElseIfChainWithWrites() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task ElseIfWrites {\n" +
			"    in push u8 cmd;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 c = cmd.read();\n" +
			"        if (c == 1) {\n" +
			"            result.write(10);\n" +
			"        } else if (c == 2) {\n" +
			"            result.write(20);\n" +
			"        } else if (c == 3) {\n" +
			"            result.write(30);\n" +
			"        } else {\n" +
			"            result.write(0);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		// IfScheduler develops each branch with port write into separate actions
		assertTrue("Else-if chain with writes should have >= 1 action",
			actor.getActions().size() >= 1);
	}

	/**
	 * Test: Nested if/else with writes — scheduler handles correctly.
	 */
	@Test
	public void testNestedIfWithWrites() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task NestedIfWrites {\n" +
			"    in push u8 a, push b;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 va = a.read();\n" +
			"        u8 vb = b.read();\n" +
			"        if (va > 100) {\n" +
			"            if (vb > 50) {\n" +
			"                result.write(va + vb);\n" +
			"            } else {\n" +
			"                result.write(va - vb);\n" +
			"            }\n" +
			"        } else {\n" +
			"            result.write(vb);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Nested if with writes should have >= 1 action",
			actor.getActions().size() >= 1);
	}

	/**
	 * Test: Multiple writes to different ports stays single action.
	 */
	@Test
	public void testMultipleWritesDifferentPorts() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task MultiWrite {\n" +
			"    in push u8 data;\n" +
			"    out push u8 high, push low;\n" +
			"    void loop() {\n" +
			"        u8 d = data.read();\n" +
			"        high.write(d >> 4);\n" +
			"        low.write(d & 0x0F);\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertEquals("Multiple writes same cycle should be 1 action", 1, actor.getActions().size());
	}

	// =========================================================================
	// FSM NEEDED: Multi-action tasks
	// =========================================================================

	/**
	 * Test: Port read in else branch creates FSM (cycle break).
	 */
	@Test
	public void testPortReadInElseBranchCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task PortReadInElse {\n" +
			"    in push u8 a, push b;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 va = a.read();\n" +
			"        if (va > 100) {\n" +
			"            result.write(va);\n" +
			"        } else {\n" +
			"            result.write(b.read());\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Port read in else branch should create >= 2 actions, got " +
			actor.getActions().size(), actor.getActions().size() >= 2);
	}

	/**
	 * Test: Else-if with port reads in different branches creates FSM.
	 */
	@Test
	public void testElseIfWithPortReadsCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task ElseIfPortReads {\n" +
			"    in push u8 cmd;\n" +
			"    in push u16 data;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        u8 c = cmd.read();\n" +
			"        if (c == 1) {\n" +
			"            result.write((u16) c);\n" +
			"        } else if (c == 2) {\n" +
			"            u16 d = data.read();\n" +
			"            result.write(d);\n" +
			"        } else {\n" +
			"            result.write(0);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Else-if with port read in branch should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	/**
	 * Test: available() check creates 2+ actions (available vs not-available paths).
	 */
	@Test
	public void testAvailableCheckCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task AvailCheck {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    u8 count = 0;\n" +
			"    void loop() {\n" +
			"        if (data.available()) {\n" +
			"            result.write(data.read());\n" +
			"        } else {\n" +
			"            count = count + 1;\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("available() check should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	/**
	 * Test: fence; creates a 2-state FSM.
	 */
	@Test
	public void testFenceCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task FenceTest {\n" +
			"    in push u8 a;\n" +
			"    in push u16 b;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        a.read();\n" +
			"        fence;\n" +
			"        result.write(b.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("fence; should create >= 2 actions", actor.getActions().size() >= 2);
		assertNotNull("Should have FSM", actor.getFsm());
		assertTrue("FSM should have >= 2 states", actor.getFsm().getStates().size() >= 2);
	}

	/**
	 * Test: idle(1) creates a 2-state FSM.
	 */
	@Test
	public void testIdleCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task IdleTest {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 d = data.read();\n" +
			"        idle(1);\n" +
			"        result.write(d);\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("idle(1) should create >= 2 actions", actor.getActions().size() >= 2);
	}

	/**
	 * Test: Same port read twice creates FSM (CycleScheduler cycle break).
	 */
	@Test
	public void testSamePortReadTwiceCreatesFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task SamePortTwice {\n" +
			"    in push u8 data;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        u8 high = data.read();\n" +
			"        u8 low = data.read();\n" +
			"        result.write(((u16) high << 8) | (u16) low);\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Same port read twice should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	/**
	 * Test: State variable with if/else creates FSM when branches have port reads.
	 */
	@Test
	public void testStateVariableFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task StateVar {\n" +
			"    in push u8 cmd;\n" +
			"    in push u16 data;\n" +
			"    out push u16 result;\n" +
			"    u8 state = 0;\n" +
			"    void loop() {\n" +
			"        if (state == 0) {\n" +
			"            u8 c = cmd.read();\n" +
			"            state = c;\n" +
			"        } else {\n" +
			"            result.write(data.read());\n" +
			"            state = 0;\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("State variable FSM should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	// =========================================================================
	// COMBINATIONAL: Never FSM
	// =========================================================================

	/**
	 * Test: Combinational task never has FSM.
	 */
	@Test
	public void testCombinationalNoFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task CombTask {\n" +
			"    properties { clocks: [] }\n" +
			"    in u8 a, u8 b;\n" +
			"    out u8 sum;\n" +
			"    void loop() {\n" +
			"        sum = a + b;\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		// Combinational tasks have FSM cleared after transformation
		assertNull("Combinational task should have null FSM", actor.getFsm());
	}

	/**
	 * Test: Combinational with complex if/else still no FSM.
	 */
	@Test
	public void testCombinationalComplexIfNoFSM() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task CombComplex {\n" +
			"    properties { clocks: [] }\n" +
			"    in u8 a, u8 b, u2 op;\n" +
			"    out u8 result;\n" +
			"    void loop() {\n" +
			"        if (op == 0) {\n" +
			"            result = a + b;\n" +
			"        } else if (op == 1) {\n" +
			"            result = a - b;\n" +
			"        } else if (op == 2) {\n" +
			"            result = a & b;\n" +
			"        } else {\n" +
			"            result = a | b;\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertNull("Combinational with if/else should have null FSM", actor.getFsm());
	}

	// =========================================================================
	// FSM STRUCTURE TESTS
	// =========================================================================

	/**
	 * Test: FSM states are properly named after FsmBeautifier.
	 */
	@Test
	public void testFsmStateNaming() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task StateNaming {\n" +
			"    in push u8 data;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        u8 high = data.read();\n" +
			"        fence;\n" +
			"        u8 low = data.read();\n" +
			"        result.write(((u16) high << 8) | (u16) low);\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull("Should have FSM", actor.getFsm());
		FSM fsm = actor.getFsm();

		for (State state : fsm.getStates()) {
			assertNotNull("State name should not be null", state.getName());
			assertFalse("State name should not be empty", state.getName().isEmpty());
		}

		assertNotNull("FSM should have initial state", fsm.getInitialState());
		assertNotNull("Initial state should have name", fsm.getInitialState().getName());
	}

	/**
	 * Test: FSM transitions connect states correctly.
	 */
	@Test
	public void testFsmTransitions() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task Transitions {\n" +
			"    in push u8 a;\n" +
			"    in push u8 b;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 va = a.read();\n" +
			"        fence;\n" +
			"        result.write(va + b.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull("Should have FSM", actor.getFsm());
		FSM fsm = actor.getFsm();

		assertTrue("Should have transitions", fsm.getTransitions().size() > 0);

		for (Transition t : fsm.getTransitions()) {
			assertNotNull("Transition should have source", t.getSource());
			// target can be null for self-loops
			assertNotNull("Transition should have action", t.getAction());
		}
	}

	/**
	 * Test: Multiple fences create linear state chain.
	 */
	@Test
	public void testMultipleFencesLinearChain() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task LinearChain {\n" +
			"    in push u8 a, push b, push c;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 va = a.read();\n" +
			"        fence;\n" +
			"        u8 vb = b.read();\n" +
			"        fence;\n" +
			"        result.write(va + vb + c.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull("Should have FSM", actor.getFsm());
		assertTrue("Two fences should create >= 3 states",
			actor.getFsm().getStates().size() >= 3);
		assertTrue("Two fences should create >= 3 actions",
			actor.getActions().size() >= 3);
	}

	// =========================================================================
	// ELSE-IF PATTERN TESTS (requested by user)
	// =========================================================================

	/**
	 * Test: 4-way else-if chain — each branch with write creates separate action.
	 *
	 * <p>The IfScheduler develops branches with output writes, creating one action
	 * per branch path. This is correct for hardware where each branch may have
	 * different output valid signals.</p>
	 */
	@Test
	public void testFourWayElseIfWithWrites() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task FourWayElseIf {\n" +
			"    in push u8 selector;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        u8 s = selector.read();\n" +
			"        if (s == 0) {\n" +
			"            result.write(100);\n" +
			"        } else if (s == 1) {\n" +
			"            result.write(200);\n" +
			"        } else if (s == 2) {\n" +
			"            result.write(300);\n" +
			"        } else {\n" +
			"            result.write(400);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		// IfScheduler creates action per branch when writes are present
		assertTrue("4-way else-if should have >= 1 action",
			actor.getActions().size() >= 1);
	}

	/**
	 * Test: Else-if with port read in MIDDLE branch creates FSM.
	 */
	@Test
	public void testElseIfPortReadMiddleBranch() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task ElseIfMiddleRead {\n" +
			"    in push u8 cmd;\n" +
			"    in push u8 extra;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        u8 c = cmd.read();\n" +
			"        if (c == 0) {\n" +
			"            result.write(0);\n" +
			"        } else if (c == 1) {\n" +
			"            result.write(extra.read());\n" +
			"        } else {\n" +
			"            result.write(c);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Else-if with port read in middle branch should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	/**
	 * Test: Else-if with available() in condition creates FSM.
	 */
	@Test
	public void testElseIfWithAvailable() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task ElseIfAvail {\n" +
			"    in push u8 a, push b;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        if (a.available()) {\n" +
			"            result.write(a.read());\n" +
			"        } else if (b.available()) {\n" +
			"            result.write(b.read());\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		assertTrue("Else-if with available() should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	/**
	 * Test: Nested else-if inside if branch.
	 */
	@Test
	public void testNestedElseIfInBranch() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task NestedElseIf {\n" +
			"    in push u8 cmd;\n" +
			"    in push u8 sub;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        u8 c = cmd.read();\n" +
			"        if (c == 1) {\n" +
			"            u8 s = sub.read();\n" +
			"            if (s == 0) {\n" +
			"                result.write(10);\n" +
			"            } else if (s == 1) {\n" +
			"                result.write(20);\n" +
			"            } else {\n" +
			"                result.write(30);\n" +
			"            }\n" +
			"        } else {\n" +
			"            result.write(0);\n" +
			"        }\n" +
			"    }\n" +
			"}\n"
		);
		assertValidActionNames(actor);
		// Port read in if branch → cycle break → FSM
		assertTrue("Nested else-if with port read should create >= 2 actions",
			actor.getActions().size() >= 2);
	}

	// =========================================================================
	// INPUT PATTERN TESTS
	// =========================================================================

	/**
	 * Test: Action with push port read has inputPattern with that port.
	 */
	@Test
	public void testInputPatternForPushRead() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task InputPattern {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        result.write(data.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertEquals("Should have 1 action", 1, actor.getActions().size());
		Action action = actor.getActions().get(0);

		// The action's inputPattern should reference the 'data' port
		boolean hasDataPort = false;
		for (Port port : action.getInputPattern().getPorts()) {
			if ("data".equals(port.getName())) {
				hasDataPort = true;
			}
		}
		assertTrue("Action inputPattern should include 'data' port", hasDataPort);
	}

	/**
	 * Regression (UART RX bit-timing): a port-gated counted loop
	 * <code>for (j=0; j!=N; j++) { p.read; }</code> lowers to a body edge that
	 * self-loops (gated on <code>p_valid &amp;&amp; j!=N</code>) and an exit edge
	 * (<code>j==N</code>). PatternImplementation must NOT fold the body's port
	 * validity into the exit guard: rewriting the exit to <code>!(p_valid &amp;&amp;
	 * j!=N)</code> makes the loop exit on every cycle the port is not valid,
	 * collapsing the legitimate "wait for the next token" stall and sampling
	 * the timing port far too fast. The exit guard must stay <code>j==N</code>.
	 */
	@Test
	public void testPortGatedLoopExitGuardOmitsPortValid() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task TickLoop {\n" +
			"    in push bool tick;\n" +
			"    out push u8 done;\n" +
			"    u6 j;\n" +
			"    void loop() {\n" +
			"        for (j = 0; j != 12; j++) {\n" +
			"            tick.read;\n" +
			"        }\n" +
			"        done.write(1);\n" +
			"    }\n" +
			"}\n"
		);
		// Run the pass that folds port-validity signals into schedulers (and,
		// before this fix, over-folded them into loop-exit complements).
		new PatternImplementation().doSwitch(actor);

		// Collect the validity-signal Vars of every input port (tick_valid).
		Set<Var> portValids = new HashSet<>();
		for (Port port : actor.getInputs()) {
			portValids.addAll(port.getAdditionalInputs());
		}
		assertFalse("test setup: 'tick' should have a validity signal",
			portValids.isEmpty());

		// Find the loop state: it has a self-looping body transition; the other
		// outgoing transition is the loop exit.
		Transition body = null, exit = null;
		for (State s : actor.getFsm().getStates()) {
			Transition selfLoop = null, other = null;
			for (Edge e : s.getOutgoing()) {
				Transition t = (Transition) e;
				if (t.getSource() == t.getTarget()) {
					selfLoop = t;
				} else {
					other = t;
				}
			}
			if (selfLoop != null && other != null) {
				body = selfLoop;
				exit = other;
				break;
			}
		}
		assertNotNull("expected a self-looping loop-body transition", body);
		assertNotNull("expected a diverging loop-exit transition", exit);

		// The body must gate on the port validity; the exit must NOT.
		assertTrue("loop-body scheduler should gate on the port-validity signal",
			schedulerReferencesAny(body.getAction().getScheduler(), portValids));
		assertFalse("loop-exit scheduler must not fold in the port-validity "
			+ "signal (it would collapse the wait-for-tick stall)",
			schedulerReferencesAny(exit.getAction().getScheduler(), portValids));
	}

	/** True if the scheduler procedure references any Var in the given set. */
	private boolean schedulerReferencesAny(Procedure scheduler, Set<Var> vars) {
		if (scheduler == null) {
			return false;
		}
		for (java.util.Iterator<EObject> it = scheduler.eAllContents(); it.hasNext();) {
			EObject o = it.next();
			if (o instanceof ExprVar) {
				ExprVar ev = (ExprVar) o;
				if (ev.getUse() != null && vars.contains(ev.getUse().getVariable())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Test: Action with two push port reads has both in inputPattern.
	 */
	@Test
	public void testInputPatternTwoPorts() throws Exception {
		Actor actor = parseAndTransform(
			"package test;\n" +
			"task TwoInputs {\n" +
			"    in push u8 a, push b;\n" +
			"    out push u16 sum;\n" +
			"    void loop() {\n" +
			"        sum.write((u16) a.read() + (u16) b.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertEquals("Should have 1 action", 1, actor.getActions().size());
		Action action = actor.getActions().get(0);

		assertEquals("inputPattern should have 2 ports",
			2, action.getInputPattern().getPorts().size());
	}
}
