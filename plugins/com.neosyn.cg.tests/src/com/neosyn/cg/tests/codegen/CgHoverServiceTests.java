/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.xtext.testing.InjectWith;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.ide.server.CgHoverService;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

import org.eclipse.xtext.testing.XtextRunner;

/**
 * Round-trip tests for {@link CgHoverService}: parse a fixture, walk to
 * specific AST nodes, ask the hover service for content, assert the markdown
 * matches expectations.
 *
 * The dispatch is exercised in-process; the JSON-RPC framing on top is the
 * upstream Xtext LanguageServer's responsibility (tested by Xtext itself).
 * Our concern here is that {@link CgHoverService#getContents} produces the
 * right markdown for each EObject type in C⏚.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgHoverServiceTests extends AbstractCxTest {

	private final CgHoverService service = new CgHoverService();

	@Test
	public void hover_inputPushPort_marksPushDirectionType() throws Exception {
		Variable a = findPort("HoverTask", "a");
		String hover = service.getContents(a);
		assertTrue("hover missing direction `in`: " + hover, hover.contains("in"));
		assertTrue("hover missing protocol `push`: " + hover, hover.contains("push"));
		assertTrue("hover missing type `u8`: " + hover, hover.contains("u8"));
		assertTrue("hover not a Port description: " + hover, hover.contains("Port"));
	}

	@Test
	public void hover_outputBarePort_marksBareDirectionType() throws Exception {
		Variable b = findPort("HoverTask", "b");
		String hover = service.getContents(b);
		assertTrue("hover missing direction `out`: " + hover, hover.contains("out"));
		assertTrue("hover missing protocol `bare`: " + hover, hover.contains("bare"));
		assertTrue("hover missing type `u8`: " + hover, hover.contains("u8"));
	}

	@Test
	public void hover_localVariable_marksVariableAndType() throws Exception {
		Variable counter = findLocalVariable("HoverTask", "counter");
		String hover = service.getContents(counter);
		assertTrue("hover missing label `Variable`: " + hover, hover.contains("Variable"));
		assertTrue("hover missing type `u8`: " + hover, hover.contains("u8"));
		assertTrue("hover missing name `counter`: " + hover, hover.contains("counter"));
	}

	@Test
	public void hover_task_marksTaskAndPortCount() throws Exception {
		Task task = findEntity(Task.class, "HoverTask");
		String hover = service.getContents(task);
		assertTrue("hover missing `task`: " + hover, hover.contains("task"));
		assertTrue("hover missing `HoverTask`: " + hover, hover.contains("HoverTask"));
		assertTrue("hover missing port count `2`: " + hover, hover.contains("2 port"));
	}

	@Test
	public void hover_network_marksNetworkAndInstanceCount() throws Exception {
		Network network = findEntity(Network.class, "HoverNetwork");
		String hover = service.getContents(network);
		assertTrue("hover missing `network`: " + hover, hover.contains("network"));
		assertTrue("hover missing `HoverNetwork`: " + hover, hover.contains("HoverNetwork"));
		assertTrue("hover missing instance count `2`: " + hover, hover.contains("2 instance"));
	}

	@Test
	public void hover_instance_marksInstanceAndTarget() throws Exception {
		Network network = findEntity(Network.class, "HoverNetwork");
		Inst inst = null;
		for (Inst candidate : network.getInstances()) {
			if ("ht".equals(candidate.getName())) {
				inst = candidate;
				break;
			}
		}
		assertNotNull("inst `ht` not found in HoverNetwork", inst);
		String hover = service.getContents(inst);
		assertTrue("hover missing `Instance`: " + hover, hover.contains("Instance"));
		assertTrue("hover missing target `HoverTask`: " + hover, hover.contains("HoverTask"));
		assertTrue("hover missing inst name `ht`: " + hover, hover.contains("ht"));
	}

	@Test
	public void hover_bundle_marksBundle() throws Exception {
		Bundle bundle = findEntity(Bundle.class, "HoverBundle");
		String hover = service.getContents(bundle);
		assertTrue("hover missing `bundle`: " + hover, hover.contains("bundle"));
		assertTrue("hover missing `HoverBundle`: " + hover, hover.contains("HoverBundle"));
	}

	@Test
	public void hover_returnsMarkdownKind() {
		String kind = service.getKind(null);
		assertNotNull(kind);
		assertTrue("expected markdown kind, got: " + kind, kind.contains("markdown"));
	}

	// --- helpers ---------------------------------------------------------

	private Module loadFixture() throws Exception {
		Module module = getModule("hover/HoverFixture.cg");
		assertOk(module);
		return module;
	}

	private <T extends CgEntity> T findEntity(Class<T> type, String name) throws Exception {
		Module module = loadFixture();
		for (CgEntity entity : module.getEntities()) {
			if (type.isInstance(entity) && name.equals(entity.getName())) {
				return type.cast(entity);
			}
		}
		throw new AssertionError(
				"Entity not found: " + type.getSimpleName() + " " + name);
	}

	private Variable findPort(String taskName, String portName) throws Exception {
		Task task = findEntity(Task.class, taskName);
		for (PortDecl portDecl : task.getPortDecls()) {
			SinglePortDecl single = portDecl instanceof SinglePortDecl
					? (SinglePortDecl) portDecl : null;
			if (single == null) {
				continue;
			}
			for (PortDef def : single.getPorts()) {
				Variable v = def.getVar();
				if (v != null && portName.equals(v.getName())) {
					return v;
				}
			}
		}
		throw new AssertionError(
				"Port not found: " + taskName + "." + portName);
	}

	private Variable findLocalVariable(String taskName, String varName) throws Exception {
		Task task = findEntity(Task.class, taskName);
		for (VarDecl decl : task.getDecls()) {
			for (Variable v : decl.getVariables()) {
				if (v != null && varName.equals(v.getName())) {
					return v;
				}
			}
		}
		throw new AssertionError(
				"Local variable not found: " + taskName + "." + varName);
	}
}
