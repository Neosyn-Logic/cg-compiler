/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

/**
 * Cross-file go-to-definition and find-references tests (Track C step C3).
 *
 * The LSP advertises both capabilities (see
 * {@code memory/project_lsp_capabilities.md}) via Xtext's default services.
 * These tests exercise the underlying cross-reference resolution and the
 * resource-set-wide reference scan that the LSP handlers ultimately call,
 * on a two-file fixture that mirrors a realistic VS Code session:
 * {@code RefDefProducer.cg} (defining task) and {@code RefDefConsumer.cg}
 * (importing + instantiating it).
 *
 * Definition is verified by following the {@code Inst.entity} cross-reference
 * and asserting the resolved target sits in the producer file. References
 * are verified by walking the resource set and collecting every
 * {@link EObject} whose features point at the target — both inbound
 * cross-refs and same-file uses must show up.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgGoToDefinitionTests extends AbstractCxTest {

	private static final String PRODUCER = "refdef/RefDefProducer.cg";
	private static final String CONSUMER = "refdef/RefDefConsumer.cg";

	@Test
	public void definition_instCrossFile_resolvesToProducerFile() throws Exception {
		Module producer = getModule(PRODUCER);
		assertOk(producer);
		Module consumer = getModule(CONSUMER);
		assertOk(consumer);

		Network network = findEntity(consumer, Network.class, "RefDefNetwork");
		Inst prodInst = findInstance(network, "prod");

		Instantiable target = prodInst.getEntity();
		if (target == null) {
			target = prodInst.getTask();
		}
		assertNotNull("Inst.entity / Inst.task did not resolve", target);
		assertFalse("cross-ref target is a proxy", ((EObject) target).eIsProxy());

		URI targetUri = EcoreUtil.getURI(target).trimFragment();
		URI producerUri = producer.eResource().getURI();
		assertEquals("go-to-definition should land in the producer file",
				producerUri.lastSegment(), targetUri.lastSegment());

		assertEquals("resolved target should be the RefDefProducer task",
				"RefDefProducer", ((CgEntity) target).getName());
	}

	@Test
	public void references_producerTask_includesConsumerSite() throws Exception {
		Module producer = getModule(PRODUCER);
		assertOk(producer);
		Module consumer = getModule(CONSUMER);
		assertOk(consumer);

		Task producerTask = findEntity(producer, Task.class, "RefDefProducer");
		List<EObject> refs = findReferencesTo(producerTask);

		assertFalse("expected references to RefDefProducer, found none", refs.isEmpty());

		Set<String> sourceFiles = new TreeSet<String>();
		for (EObject ref : refs) {
			sourceFiles.add(ref.eResource().getURI().lastSegment());
		}
		assertTrue(
				"expected RefDefConsumer.cg in references, got " + sourceFiles,
				sourceFiles.contains("RefDefConsumer.cg"));
	}

	@Test
	public void references_intraFilePort_findsLocalUses() throws Exception {
		Module producer = getModule(PRODUCER);
		assertOk(producer);

		Task producerTask = findEntity(producer, Task.class, "RefDefProducer");
		EObject portCounter = findPortOrLocalVar(producerTask, "counter");
		assertNotNull("counter not found", portCounter);

		List<EObject> refs = findReferencesTo(portCounter);
		assertFalse("expected intra-file references to `counter`, found none",
				refs.isEmpty());
	}

	// --- helpers ---------------------------------------------------------

	private <T extends CgEntity> T findEntity(Module module, Class<T> type, String name) {
		for (CgEntity entity : module.getEntities()) {
			if (type.isInstance(entity) && name.equals(entity.getName())) {
				return type.cast(entity);
			}
		}
		throw new AssertionError(
				"Entity not found: " + type.getSimpleName() + " " + name
						+ " in " + module.eResource().getURI());
	}

	private Inst findInstance(Network network, String name) {
		for (Inst inst : network.getInstances()) {
			if (name.equals(inst.getName())) {
				return inst;
			}
		}
		throw new AssertionError("Inst not found: " + name);
	}

	private EObject findPortOrLocalVar(Task task, String name) {
		for (EObject e : EcoreUtil2.eAllContentsAsList(task)) {
			if (e instanceof com.neosyn.cg.cg.Named) {
				String n = ((com.neosyn.cg.cg.Named) e).getName();
				if (name.equals(n)) {
					return e;
				}
			}
		}
		return null;
	}

	private List<EObject> findReferencesTo(EObject target) {
		List<EObject> result = new ArrayList<EObject>();
		Set<EObject> seen = new HashSet<EObject>();
		for (Resource resource : new ArrayList<Resource>(resourceSet.getResources())) {
			for (EObject root : resource.getContents()) {
				collectReferences(root, target, result, seen);
			}
		}
		return result;
	}

	private static void collectReferences(EObject node, EObject target,
			List<EObject> result, Set<EObject> seen) {
		for (EObject candidate : EcoreUtil2.eAllContentsAsList(node)) {
			if (seen.contains(candidate)) {
				continue;
			}
			seen.add(candidate);
			for (EObject crossRef : candidate.eCrossReferences()) {
				if (crossRef == target) {
					result.add(candidate);
					break;
				}
			}
		}
		for (EObject crossRef : node.eCrossReferences()) {
			if (crossRef == target && !result.contains(node)) {
				result.add(node);
				break;
			}
		}
	}
}
