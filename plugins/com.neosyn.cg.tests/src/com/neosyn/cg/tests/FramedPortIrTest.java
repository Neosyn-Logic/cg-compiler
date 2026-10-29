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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
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
import com.neosyn.cg.internal.compiler.FramedLastCheck;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.IfScheduler;
import com.neosyn.core.transformations.PatternImplementation;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.ExprBool;
import com.neosyn.models.ir.ExprVar;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Var;

/**
 * TRAPS T58 slice 2b: a {@code framed} stream port carries its frame boundary into the model and the
 * IR. {@code din} gets {@code din_last} beside {@code din_valid}; {@code dout.write(v, last)} stores
 * the written flag into {@code dout_last}; {@code din.last} loads {@code din_last}; and no action
 * waits on {@code last}, which is data, not a handshake.
 *
 * <p>Runs the source-to-IR pipeline directly (as {@link FsmGenerationTest} does): {@code generate}
 * still refuses framed ports until the back ends carry {@code last} (slices 2c-2e).
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class FramedPortIrTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private IInstantiator instantiator;

	private static final String RELAY = "package test;\n"
			+ "task Relay {\n"
			+ "    in framed u8 a;\n"
			+ "    out framed u8 y;\n"
			+ "    void loop() {\n"
			+ "        u8 v = a.read();\n"
			+ "        y.write(v, a.last);\n"
			+ "    }\n"
			+ "}\n";

	@Test
	public void framedPorts_carryLastAfterTheHandshake() throws Exception {
		Actor actor = parseAndTransform(RELAY);
		Port a = actor.getInputs().get(0);
		Port y = actor.getOutputs().get(0);

		assertEquals("input sidebands: valid first, then last", List.of("a_valid", "a_last"),
				names(a.getAdditionalInputs()));
		assertEquals(List.of("a_ready"), names(a.getAdditionalOutputs()));
		assertEquals("output sidebands: valid first, then last", List.of("y_valid", "y_last"),
				names(y.getAdditionalOutputs()));
		assertEquals(List.of("y_ready"), names(y.getAdditionalInputs()));
		assertTrue(a.isFramed());
		assertTrue(y.isFramed());
	}

	@Test
	public void writeStoresTheReadBoundary_andRaisesValid() throws Exception {
		Actor actor = parseAndTransform(RELAY);
		Port a = actor.getInputs().get(0);
		Port y = actor.getOutputs().get(0);

		// `a.last` is a load of the a_last wire
		Var loaded = null;
		for (InstLoad load : all(actor, InstLoad.class)) {
			if (load.getSource().getVariable() == a.getLast()) {
				loaded = load.getTarget().getVariable();
			}
		}
		assertNotNull("`a.last` must load a_last", loaded);

		boolean validRaised = false;
		boolean lastStored = false;
		for (InstStore store : all(actor, InstStore.class)) {
			Var target = store.getTarget().getVariable();
			if (target == y.getAdditionalOutputs().get(0)) {
				validRaised = store.getValue() instanceof ExprBool && ((ExprBool) store.getValue()).isValue();
			} else if (target == y.getLast()) {
				assertFalse("y_last must get the written flag, never a constant",
						store.getValue() instanceof ExprBool);
				assertTrue("y_last must get the value loaded from a_last",
						store.getValue() instanceof ExprVar
								&& ((ExprVar) store.getValue()).getUse().getVariable() == loaded);
				lastStored = true;
			}
		}
		assertTrue("the write raises y_valid", validRaised);
		assertTrue("the write stores y_last", lastStored);
	}

	@Test
	public void noActionWaitsOnLast() throws Exception {
		Actor actor = parseAndTransform(RELAY);
		new PatternImplementation().doSwitch(actor);
		Port a = actor.getInputs().get(0);

		boolean waitsOnValid = false;
		// every action, not only FSM transitions: a one-state task keeps its action on the actor
		for (Action action : all(actor, Action.class)) {
			if (action.getScheduler() == null) {
				continue;
			}
			for (ExprVar ev : all(action.getScheduler(), ExprVar.class)) {
				Var v = ev.getUse() == null ? null : ev.getUse().getVariable();
				assertFalse("a scheduler must not wait on a_last (it is data)", v == a.getLast());
				waitsOnValid |= v == a.getAdditionalInputs().get(0);
			}
		}
		assertTrue("control: the read still waits on a_valid", waitsOnValid);
	}

	@Test
	public void eachElementOfAFramedArrayPortIsFramed() throws Exception {
		Actor actor = parseAndTransform("package test;\n"
				+ "task Two {\n"
				+ "    in framed u8 a[2];\n"
				+ "    out framed u8 y;\n"
				+ "    void loop() {\n"
				+ "        u8 v = a[1].read();\n"
				+ "        y.write(v, a[1].last);\n"
				+ "    }\n"
				+ "}\n");
		assertEquals(2, actor.getInputs().size());
		for (Port element : actor.getInputs()) {
			assertEquals(List.of(element.getName() + "_valid", element.getName() + "_last"),
					names(element.getAdditionalInputs()));
		}
	}

	/** Control: an unframed stream is unchanged -- one handshake signal each way, no `last`. */
	@Test
	public void unframedStream_hasNoLast() throws Exception {
		Actor actor = parseAndTransform("package test;\n"
				+ "task Plain {\n"
				+ "    in stream u8 a;\n"
				+ "    out stream u8 y;\n"
				+ "    void loop() { y.write(a.read()); }\n"
				+ "}\n");
		Port a = actor.getInputs().get(0);
		Port y = actor.getOutputs().get(0);
		assertEquals(List.of("a_valid"), names(a.getAdditionalInputs()));
		assertEquals(List.of("y_valid"), names(y.getAdditionalOutputs()));
		assertNull(a.getLast());
		assertNull(y.getLast());
		assertFalse(a.isFramed());
	}

	// --- the `.last` warning (decided 2026-10-01: keep AXI TLAST semantics, warn on misuse) ------

	private List<String> lastWarnings(String loopBody) throws Exception {
		return FramedLastCheck.lastOutsideRead(parseAndTransform("package test;\n"
				+ "task W {\n"
				+ "    in framed u8 a;\n"
				+ "    out framed u8 y;\n"
				+ "    void loop() {\n"
				+ loopBody
				+ "    }\n"
				+ "}\n"));
	}

	@Test
	public void lastWithItsRead_isNoWarning() throws Exception {
		assertEquals(List.of(), FramedLastCheck.lastOutsideRead(parseAndTransform(RELAY)));
	}

	/** Cycle, not statement order: `.last` before the read in the same cycle is the read value's. */
	@Test
	public void lastBeforeItsReadInTheSameCycle_isNoWarning() throws Exception {
		assertEquals(List.of(), lastWarnings("        bool e = a.last;\n        u8 v = a.read();\n"
				+ "        y.write(v, e);\n"));
	}

	/** The idiom the documentation will give. */
	@Test
	public void theMoreIdiom_isNoWarning() throws Exception {
		assertEquals(List.of(), lastWarnings("        bool more = true;\n"
				+ "        while (more) {\n            u8 v = a.read();\n            more = !a.last;\n"
				+ "            y.write(v, !more);\n        }\n"));
	}

	@Test
	public void lastAfterAFence_isAWarningAtItsLine() throws Exception {
		List<String> w = lastWarnings("        u8 v = a.read();\n        fence;\n        y.write(v, a.last);\n");
		assertEquals(w.toString(), 1, w.size());
		assertTrue(w.get(0), w.get(0).contains(":8: `a.last` is read in a cycle that does not read `a`"));
	}

	/**
	 * The idiom first planned for T58: the loop EXIT decides on `a.last` in a cycle that reads
	 * nothing, i.e. on a wire that need not be valid.
	 */
	@Test
	public void whileNotLast_isAWarning() throws Exception {
		List<String> w = lastWarnings("        while (!a.last) {\n            u8 v = a.read();\n"
				+ "            y.write(v, false);\n        }\n");
		assertFalse("the exit cycle reads a.last without reading a", w.isEmpty());
	}

	/**
	 * TRAPS T150 (Ethernet TestFrameEcho, UDP Test_RxUdpPaced): a later `if` on state the
	 * reading branch changes, with a loop inside, makes every guard replay that branch,
	 * `l = a.last` included -- also in the guard of the cycle that does not read `a`, where `l`
	 * goes nowhere -- the copied `assert` that reads it is never run by a guard. No warning.
	 * (Without the `while` the copy is not made: no reproduction.)
	 */
	@Test
	public void lastCopiedIntoTheGuardOfTheNonReadingBranch_isNoWarning() throws Exception {
		assertEquals(List.of(), FramedLastCheck.lastOutsideRead(parseAndTransform("package test;\n"
				+ "task W {\n"
				+ "    in framed u8 a;\n"
				+ "    out framed u8 y;\n"
				+ "    u4 n;\n"
				+ "    u5 m;\n"
				+ "    bool olast[8];\n"
				+ "    void loop() {\n"
				+ "        if (a.available()) {\n"
				+ "            u8 v = a.read();\n"
				+ "            bool l = a.last;\n"
				+ "            olast[n] = l;\n"
				+ "            assert(l == (n == 7));\n"
				+ "            y.write(v, l);\n"
				+ "            n = (u4) (n + 1);\n"
				+ "        }\n"
				+ "        if (n == 8) {\n"
				+ "            u32 k = 0;\n"
				+ "            while (k < 8) { m = (u5) (m + 1); k = k + 1; }\n"
				+ "            n = 0;\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n")));
	}

	/** The same shape, but the later condition depends on `last`: that guard does decide on it. */
	@Test
	public void lastDecidingALaterConditionOutsideTheRead_isAWarning() throws Exception {
		List<String> w = FramedLastCheck.lastOutsideRead(parseAndTransform("package test;\n"
				+ "task W {\n"
				+ "    in framed u8 a;\n"
				+ "    out framed u8 y;\n"
				+ "    u4 n;\n"
				+ "    u5 m;\n"
				+ "    void loop() {\n"
				+ "        if (a.available()) {\n"
				+ "            u8 v = a.read();\n"
				+ "            y.write(v, false);\n"
				+ "            n = (u4) (n + 1);\n"
				+ "        }\n"
				+ "        if (a.last) {\n"
				+ "            u32 k = 0;\n"
				+ "            while (k < 8) { m = (u5) (m + 1); k = k + 1; }\n"
				+ "            n = 0;\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n"));
		assertFalse("the non-reading cycle decides on a.last", w.isEmpty());
	}

	// --- helpers -----------------------------------------------------------------------------

	private static List<String> names(List<Var> vars) {
		List<String> names = new ArrayList<>();
		for (Var v : vars) {
			names.add(v.getName());
		}
		return names;
	}

	private static <T> List<T> all(EObject root, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Iterator<EObject> it = root.eAllContents(); it.hasNext();) {
			EObject o = it.next();
			if (type.isInstance(o)) {
				found.add(type.cast(o));
			}
		}
		return found;
	}

	private Actor parseAndTransform(String source) throws Exception {
		Module module = parseHelper.parse(source);
		assertNotNull(module);
		assertTrue("Parse errors: " + module.eResource().getErrors(), module.eResource().getErrors().isEmpty());
		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity[] holder = { null };
		instantiator.forEachMapping(cxEntity, entity -> holder[0] = entity);
		assertTrue("Should be an Actor", holder[0] instanceof Actor);
		Actor actor = (Actor) holder[0];

		Variable setup = null;
		Variable loop = null;
		for (Variable function : CgUtil.getFunctions((Task) cxEntity)) {
			if (NAME_SETUP.equals(function.getName())) {
				setup = function;
			} else if (NAME_LOOP.equals(function.getName())) {
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
}
