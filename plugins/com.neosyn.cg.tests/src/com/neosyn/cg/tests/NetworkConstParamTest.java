/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;

/**
 * Networks must be parameterizable, exactly like tasks.
 *
 * A network is the COMPOSITION unit: if only leaf tasks take const parameters,
 * then nothing built out of them is parameterizable — no parameterized pipeline,
 * stack, or composite of any kind. Verilog parameterizes modules and VHDL
 * parameterizes entities, and in both that is the same construct you compose
 * with. This gap stayed invisible because every std.* built-in that uses const
 * params (SinglePortRAM, SynchronousFIFO, SynchronizerMux) is a {@code task};
 * the first composite design to need it (AES-128/192/256, whose TopAes and
 * Expander are networks) could not express {@code new TopAes({Nk: 8, Nr: 14})}.
 *
 * Note a bundle is NOT the workaround: bundles already hold constants, but a
 * bundle constant is one GLOBAL value — it gives you one AES, not a 128 and a
 * 256 in the same design. What is required is PER-INSTANCE parameterization,
 * which is an {@code Instantiable} concern that Task and Network both inherit.
 *
 * Built incrementally; this class grows a step at a time:
 *   1. a network can DECLARE a const param                        (this file)
 *   2. the const is usable inside the network body
 *   3. `new Child({W: W})` forwards a parent const to a child instance
 *   4. `<...>` formal-parameter sugar reaches parity with Task
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class NetworkConstParamTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	/**
	 * Baseline: a task const param parses. Guards the harness itself.
	 *
	 * Note {@code decls} holds functions as well as variables, so a task with a
	 * const and a setup() has two — assert on the const by name, not on the count.
	 */
	@Test
	public void taskConstParamParses() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"task Leaf {",
				"  const int W = 4;",
				"  out push u8 v;",
				"  void setup() { v.write(W); }",
				"}"));
		assertNotNull(m);
		assertTrue("unexpected parse errors: " + m.eResource().getErrors(),
				m.eResource().getErrors().isEmpty());
		Task t = (Task) m.getEntities().get(0);
		assertTrue("the const should land in decls", declaresVariable(t.getDecls(), "W"));
	}

	/** True if any VarDecl in the list declares a variable of the given name. */
	private static boolean declaresVariable(Iterable<? extends EObject> decls, String name) {
		for (EObject d : decls) {
			for (EObject child : d.eContents()) {
				if (child instanceof Variable && name.equals(((Variable) child).getName())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Step 1: a NETWORK const param must parse, same as a task's. */
	@Test
	public void networkConstParamParses() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"network Mid {",
				"  const int W = 4;",
				"  out push u8 v;",
				"}"));
		assertNotNull(m);
		assertTrue("a network must accept a const param, but: " + m.eResource().getErrors(),
				m.eResource().getErrors().isEmpty());
		Network n = (Network) m.getEntities().get(0);
		assertTrue("the const should land in decls", declaresVariable(n.getDecls(), "W"));
	}

	/**
	 * Step 2: the const must be USABLE, not merely declarable.
	 *
	 * A port width is the realistic case — it is what `const int width` does for
	 * every std.* built-in, and what AES needs for Nk/Nr. Declaring a const that
	 * cannot be referenced would be a hollow feature.
	 */
	@Test
	public void networkConstIsUsableAsPortWidth() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"network Mid {",
				"  const int W = 12;",
				"  out push uint<W> v;",
				"}"));
		assertNotNull(m);
		assertTrue("a network const must be referenceable from the body: "
				+ m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		Network n = (Network) m.getEntities().get(0);
		assertTrue(declaresVariable(n.getDecls(), "W"));
	}

	/**
	 * Step 3: a parent's const must FORWARD into a child instance argument.
	 *
	 * This is the whole point of the feature. Without it a network can hold a
	 * parameter but cannot pass it down, so a composite still cannot be
	 * parameterized — `new TopAes({Nk: 8})` has to reach Expander and LoadKey.
	 *
	 * This test pins the SYNTAX only, and syntax is not the claim that matters:
	 * it went green for a whole increment while instantiation still NPEd
	 * underneath (Evaluator.caseExpressionVariable — isGlobal() is true for a
	 * network's entity-scoped const, but the instantiator only ever mapped a
	 * Task's decls, so getMapping() returned null). The VALUE is proven by the
	 * GenNetworkParamTest fixture, which forwards two levels and asserts what
	 * comes out, on both the bytecode and Verilog backends. Do not let this
	 * test stand in for that one.
	 */
	@Test
	public void networkConstForwardsToChildInstance() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"task Leaf {",
				"  const int W = 4;",
				"  out push u8 v;",
				"  void setup() { v.write(W); }",
				"}",
				"network Mid {",
				"  const int W = 9;",
				"  out push u8 v;",
				"  leaf = new Leaf({W: W});",
				"  this.reads(leaf.v);",
				"}"));
		assertNotNull(m);
		assertTrue("a parent const must forward to a child instance arg: "
				+ m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		Network n = (Network) m.getEntities().get(1);
		assertEquals(1, n.getInstances().size());
	}

	/**
	 * Step 4: `<...>` formal-parameter sugar reaches parity with Task.
	 *
	 * Syntax only — the VALUE is proven by the GenNetworkSugarTest fixture, and
	 * that distinction is not academic here: this syntax parsed for an entire
	 * increment while `new SugarMid<6>()` silently handed the child its DEFAULT
	 * instead of 6, because a `<...>` formal is not global and so took a
	 * different Evaluator path than a body const.
	 */
	@Test
	public void networkGenericParamSugarParses() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"network Mid<int W = 8> {",
				"  out push uint<W> v;",
				"}"));
		assertNotNull(m);
		assertTrue("a network must accept `<...>` formals like a task: "
				+ m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		Network n = (Network) m.getEntities().get(0);
		assertEquals(1, n.getParams().size());
		assertEquals("W", n.getParams().get(0).getName());
	}

	/** A network const must coexist with instances and connects, in any order. */
	@Test
	public void networkConstParamCoexistsWithInstances() throws Exception {
		Module m = parseHelper.parse(String.join("\n",
				"package com.example;",
				"task Leaf {",
				"  const int W = 4;",
				"  out push u8 v;",
				"  void setup() { v.write(W); }",
				"}",
				"network Mid {",
				"  const int W = 8;",
				"  out push u8 v;",
				"  leaf = new Leaf();",
				"  this.reads(leaf.v);",
				"}"));
		assertNotNull(m);
		assertTrue("unexpected parse errors: " + m.eResource().getErrors(),
				m.eResource().getErrors().isEmpty());
		Network n = (Network) m.getEntities().get(1);
		assertTrue(declaresVariable(n.getDecls(), "W"));
		assertEquals(1, n.getInstances().size());
		assertEquals(1, n.getConnects().size());
	}
}
