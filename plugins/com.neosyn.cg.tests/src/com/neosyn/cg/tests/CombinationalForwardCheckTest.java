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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

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
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.ActorTransformer;
import com.neosyn.cg.internal.compiler.CombinationalForwardCheck;
import com.neosyn.cg.internal.scheduler.CombinationalScheduler;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Entity;

/**
 * TRAPS T160: a combinational task that forwards a handshaked input without `available` presents
 * its output as valid every cycle -- the Ethernet MAC's ReceiveData did, and no frame ever got
 * through (T158). The warning, its line, and the shapes that must stay silent.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class CombinationalForwardCheckTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private IInstantiator instantiator;

	private List<String> warnings(String ports, String loopBody) throws Exception {
		Module module = parseHelper.parse("package test;\n"
				+ "task W {\n"
				+ "    properties { type: \"combinational\" }\n"
				+ ports
				+ "    void loop() {\n"
				+ loopBody
				+ "    }\n"
				+ "}\n");
		assertNotNull(module);
		assertTrue("Parse errors: " + module.eResource().getErrors(), module.eResource().getErrors().isEmpty());
		CgEntity cxEntity = module.getEntities().get(0);
		instantiator.update(module);
		Entity[] holder = { null };
		instantiator.forEachMapping(cxEntity, entity -> holder[0] = entity);
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
		new CombinationalScheduler(instantiator, actor).schedule(setup, loop);
		new ActorTransformer(instantiator, actor).visit();
		return CombinationalForwardCheck.unguardedForwards(actor);
	}

	/** ReceiveData's joinData before cores 23f2d81. */
	@Test
	public void unguardedForwardOfAPush_isAWarningAtTheWrite() throws Exception {
		List<String> w = warnings("    in push u8 a; out push u8 y;\n", "        y.write(a.read);\n");
		assertEquals(w.toString(), 1, w.size());
		assertTrue(w.get(0), w.get(0).contains(":6: `y` is written in a combinational task"));
		assertTrue(w.get(0), w.get(0).contains("after reading `a` without checking `available`"));
	}

	@Test
	public void forwardGuardedByAvailable_isNoWarning() throws Exception {
		assertEquals(List.of(), warnings("    in push u8 a; out push u8 y;\n",
				"        if (a.available) { y.write(a.read); }\n"));
	}

	/** LineBuffer.feed_line2: a bare input (a RAM's q) has no valid to check. */
	@Test
	public void forwardOfABareInput_isNoWarning() throws Exception {
		assertEquals(List.of(), warnings("    in u8 q; out push u8 y;\n", "        y.write(q.read);\n"));
	}

	/** A bare output carries no valid: writing it every cycle is what it is for. */
	@Test
	public void forwardIntoABareOutput_isNoWarning() throws Exception {
		assertEquals(List.of(), warnings("    in push u8 a; out u8 y;\n", "        y.write(a.read);\n"));
	}
}
