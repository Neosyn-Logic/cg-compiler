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
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Var;
import com.neosyn.neosynide.transformations.AddBufferedInputs;

/**
 * Unit tests for {@link AddBufferedInputs} transformation.
 *
 * <p>Tests verify the guard conditions and non-activation behavior of
 * AddBufferedInputs. The full activation tests (selective valid clearing,
 * internal signal creation) require the real compilation pipeline which
 * creates additional input/output signals via SkeletonMaker. The standalone
 * instantiator (ParseHelper) does NOT create these signals.</p>
 *
 * <p>Guard condition tests verify that AddBufferedInputs correctly:
 * <ul>
 *   <li>Does NOT activate when no stream outputs exist</li>
 *   <li>Does NOT crash on non-Actor entities (bundles)</li>
 *   <li>Does NOT buffer push inputs (only stream inputs)</li>
 *   <li>Does NOT buffer when only outputs are stream</li>
 *   <li>Does NOT modify actors with no stream ports at all</li>
 * </ul>
 *
 * <p>For the selective valid clearing behavior (the BusController regression),
 * see the code in {@link AddBufferedInputs#getBufferedPortsUsedIn} which uses
 * {@code EcoreHelper.getObjects(body, Use.class)} to determine which ports
 * each action references.</p>
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class AddBufferedInputsTest {

	private static final DpnFactory dpn = DpnFactory.eINSTANCE;

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private IInstantiator instantiator;

	private Entity getIrEntity(CgEntity cxEntity) {
		Entity[] holder = { null };
		instantiator.forEachMapping(cxEntity, entity -> holder[0] = entity);
		return holder[0];
	}

	private void transformTask(Actor actor, Task task) {
		Variable setup = null;
		Variable loop = null;
		for (Variable function : CgUtil.getFunctions(task)) {
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
	}

	// =========================================================================
	// Guard condition: no stream outputs = no buffering
	// =========================================================================

	/**
	 * Test: AddBufferedInputs does NOT activate when there are no stream outputs.
	 * All outputs are push → no buffering needed.
	 */
	@Test
	public void testNoBufferingWithoutStreamOutputs() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task NoPushOutputs {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        result.write(data.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		new AddBufferedInputs().doSwitch(actor);

		assertTrue("Should have no buffered inputs when no stream outputs",
			actor.getBufferedInputs().isEmpty());

		for (Var var : actor.getVariables()) {
			assertFalse("Should not have internal_ variables, found: " + var.getName(),
				var.getName() != null && var.getName().startsWith("internal_"));
		}
	}

	/**
	 * Test: Entity without any actions (e.g., bundle) does not crash.
	 */
	@Test
	public void testEntityWithNoActions() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"bundle EmptyBundle {\n" +
			"    const u8 VALUE = 42;\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		assertNotNull("Entity should exist", entity);

		// Should not crash on non-Actor entities
		new AddBufferedInputs().doSwitch(entity);
	}

	// =========================================================================
	// Guard condition: only stream inputs get buffered
	// =========================================================================

	/**
	 * Test: When outputs are stream but inputs are bare/push,
	 * no inputs get buffered (only SYNC_READY inputs are buffered).
	 * The standalone instantiator creates all ports as BARE by default.
	 */
	@Test
	public void testNoBufferingWithBareInputs() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task BareInputs {\n" +
			"    in u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        result.write(data);\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		// Even if we manually set outputs as stream, bare inputs should NOT be buffered
		for (Port port : actor.getOutputs()) {
			port.setInterface(InterfaceType.SYNC_READY);
		}

		new AddBufferedInputs().doSwitch(actor);

		assertTrue("Bare inputs should NOT be buffered",
			actor.getBufferedInputs().isEmpty());
	}

	/**
	 * Test: Verifies that getReadyPorts correctly filters by interface type.
	 * Only SYNC_READY ports should pass the filter.
	 */
	@Test
	public void testGetReadyPortsFilter() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task FilterTest {\n" +
			"    in push u8 a;\n" +
			"    in push u16 b;\n" +
			"    out push u16 result;\n" +
			"    void loop() {\n" +
			"        result.write(b.read() + (u16) a.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		// Default: all ports are BARE → getReadyPorts returns empty
		Iterable<Port> readyInputs = dpn.getReadyPorts(actor.getInputs());
		assertFalse("BARE ports should not pass getReadyPorts filter",
			readyInputs.iterator().hasNext());

		// Set one port to SYNC (push) → still not SYNC_READY
		for (Port port : actor.getInputs()) {
			if ("a".equals(port.getName())) {
				port.setInterface(InterfaceType.SYNC);
			}
		}
		readyInputs = dpn.getReadyPorts(actor.getInputs());
		assertFalse("SYNC (push) ports should not pass getReadyPorts filter",
			readyInputs.iterator().hasNext());

		// Set one port to SYNC_READY (stream) → should pass
		for (Port port : actor.getInputs()) {
			if ("b".equals(port.getName())) {
				port.setInterface(InterfaceType.SYNC_READY);
			}
		}
		readyInputs = dpn.getReadyPorts(actor.getInputs());
		assertTrue("SYNC_READY (stream) ports should pass getReadyPorts filter",
			readyInputs.iterator().hasNext());

		int count = 0;
		for (Port p : readyInputs) {
			assertEquals("Filtered port should be 'b'", "b", p.getName());
			count++;
		}
		assertEquals("Should have exactly 1 SYNC_READY port", 1, count);
	}

	/**
	 * Test: SYNC_ACK (confirm) ports are NOT buffered.
	 * Only SYNC_READY (stream) ports should be buffered.
	 */
	@Test
	public void testConfirmPortsNotBuffered() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task ConfirmIO {\n" +
			"    in push u8 data;\n" +
			"    out push u8 result;\n" +
			"    void loop() {\n" +
			"        result.write(data.read());\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		// Set all ports as SYNC_ACK (confirm)
		for (Port port : actor.getInputs()) {
			port.setInterface(InterfaceType.SYNC_ACK);
		}
		for (Port port : actor.getOutputs()) {
			port.setInterface(InterfaceType.SYNC_ACK);
		}

		new AddBufferedInputs().doSwitch(actor);

		// SYNC_ACK should NOT be buffered (getReadyPorts filters for isSyncReady)
		assertTrue("SYNC_ACK inputs should NOT be buffered",
			actor.getBufferedInputs().isEmpty());
	}

	// =========================================================================
	// Transformation pipeline integrity tests
	// =========================================================================

	/**
	 * Test: Multi-action task without stream ports produces clean FSM.
	 * Verifies AddBufferedInputs is safe as no-op.
	 */
	@Test
	public void testMultiActionNoStreamSafe() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task MultiAction {\n" +
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
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		int actionsBefore = actor.getActions().size();
		assertTrue("Should have >= 2 actions before buffering", actionsBefore >= 2);

		// No stream ports → AddBufferedInputs should be a no-op
		new AddBufferedInputs().doSwitch(actor);

		assertEquals("Actions count should be unchanged after no-op AddBufferedInputs",
			actionsBefore, actor.getActions().size());
		assertTrue("Should have no buffered inputs",
			actor.getBufferedInputs().isEmpty());
	}

	/**
	 * Test: Combinational task is safe with AddBufferedInputs.
	 */
	@Test
	public void testCombinationalSafe() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task CombSafe {\n" +
			"    properties { clocks: [] }\n" +
			"    in u8 a, u8 b;\n" +
			"    out u8 result;\n" +
			"    void loop() {\n" +
			"        result = a + b;\n" +
			"    }\n" +
			"}\n"
		);
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		// Should be a no-op (no stream ports)
		new AddBufferedInputs().doSwitch(actor);

		assertTrue("Combinational task should have no buffered inputs",
			actor.getBufferedInputs().isEmpty());
	}

	/**
	 * Test: Fence-separated task without stream ports is safe.
	 */
	@Test
	public void testFenceTaskNoStreamSafe() throws Exception {
		Module module = parseHelper.parse(
			"package test;\n" +
			"task FenceNoStream {\n" +
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
		assertNotNull(module);
		assertTrue(module.eResource().getErrors().isEmpty());

		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity entity = getIrEntity(cxEntity);
		Actor actor = (Actor) entity;
		transformTask(actor, (Task) cxEntity);

		assertTrue("Fence should create >= 2 actions", actor.getActions().size() >= 2);

		// No stream ports → safe no-op
		new AddBufferedInputs().doSwitch(actor);

		assertTrue("Should have no buffered inputs without stream ports",
			actor.getBufferedInputs().isEmpty());
	}
}
