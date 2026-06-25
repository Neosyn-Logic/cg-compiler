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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.SortedSet;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.editor.folding.FoldingRange;
import org.eclipse.xtext.ide.editor.folding.IFoldingRangeProvider;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;

import com.google.inject.Injector;
import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Enum;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.ide.CgIdeSetup;
import com.neosyn.cg.ide.server.CgFoldingRangeProvider;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

/**
 * Service-level tests for {@link CgFoldingRangeProvider} (editor polish E3).
 *
 * Parses tests/folding/FoldingFixture.cg, runs the folding provider over the
 * resource, and asserts the structural blocks fold while noise (comments,
 * non-block nodes) does not. JSON-RPC framing is upstream Xtext's concern; we
 * test the provider directly, mirroring {@link CgHoverServiceTests}.
 *
 * <p>The provider is exercised by member-injecting a fresh instance (the test
 * injector doesn't wire {@code CgIdeModule}); a separate test confirms the real
 * LSP injector ({@link CgIdeSetup}) actually binds our provider over Xtext's
 * {@code @ImplementedBy} default. The fixture is intentionally not validated
 * (folding works off the parse tree; semantic validity is irrelevant) — we only
 * require it to parse without syntax errors.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgFoldingRangeTests extends AbstractCxTest {

	private CgFoldingRangeProvider provider() {
		CgFoldingRangeProvider provider = new CgFoldingRangeProvider();
		getInjector().injectMembers(provider);
		return provider;
	}

	private Module fixture() throws Exception {
		Module module = getModule("folding/FoldingFixture.cg");
		XtextResource resource = (XtextResource) module.eResource();
		assertTrue("fixture must parse without syntax errors: " + resource.getErrors(),
			resource.getErrors().isEmpty());
		return module;
	}

	private SortedSet<FoldingRange> fold(Module module) {
		XtextResource resource = (XtextResource) module.eResource();
		return provider().getFoldingRanges(resource, CancelIndicator.NullImpl);
	}

	@Test
	public void lspInjectorBindsCgFoldingRangeProvider() {
		// The real language-server injector (CgRuntimeModule + CgIdeModule) must
		// bind our provider over Xtext's @ImplementedBy(DefaultFoldingRangeProvider).
		Injector ide = new CgIdeSetup().createInjector();
		IFoldingRangeProvider bound = ide.getInstance(IFoldingRangeProvider.class);
		assertTrue("LSP injector should bind CgFoldingRangeProvider, got "
			+ bound.getClass().getName(), bound instanceof CgFoldingRangeProvider);
	}

	@Test
	public void foldsEachStructuralEntity() throws Exception {
		Module module = fixture();
		SortedSet<FoldingRange> folds = fold(module);

		assertCovered(folds, entity(module, Bundle.class, "Defs"), "bundle Defs");
		assertCovered(folds, entity(module, Task.class, "Worker"), "task Worker");
		assertCovered(folds, entity(module, Network.class, "Top"), "network Top");

		// Struct and Enum are nested members (not top-level CgEntity), so find
		// them by type. The fixture has exactly one of each.
		assertCovered(folds, only(module, Struct.class), "struct Pair");
		assertCovered(folds, only(module, Enum.class), "enum Color");
	}

	@Test
	public void foldsFunctionAndStatementBlocks() throws Exception {
		Module module = fixture();
		SortedSet<FoldingRange> folds = fold(module);

		// At least the loop body + the if-then + else blocks of Worker fold.
		long blockFolds = EcoreUtil2.eAllOfType(module, Block.class).stream()
			.filter(this::spansMultipleLines)
			.filter(b -> isCoveredBy(folds, b))
			.count();
		assertTrue("expected multi-line statement/function blocks to fold, got " + blockFolds,
			blockFolds >= 3);
	}

	@Test
	public void doesNotFoldTheBlockComment() throws Exception {
		Module module = fixture();
		SortedSet<FoldingRange> folds = fold(module);
		String text = ((XtextResource) module.eResource()).getParseResult()
			.getRootNode().getText();
		int commentStart = text.indexOf("/*");
		int commentEnd = text.indexOf("*/") + 2;
		assertTrue("fixture should contain a block comment", commentStart >= 0);

		for (FoldingRange f : folds) {
			boolean overlapsComment = f.getOffset() < commentEnd
				&& (f.getOffset() + f.getLength()) > commentStart;
			assertFalse("comment region must not be folded (offset " + f.getOffset() + ")",
				overlapsComment);
		}
	}

	// --- helpers ---------------------------------------------------------

	private <T extends CgEntity> T entity(Module module, Class<T> type, String name) {
		for (CgEntity e : module.getEntities()) {
			if (type.isInstance(e) && name.equals(e.getName())) {
				return type.cast(e);
			}
		}
		throw new AssertionError("entity not found: " + type.getSimpleName() + " " + name);
	}

	private <T extends EObject> T only(Module module, Class<T> type) {
		List<T> all = EcoreUtil2.eAllOfType(module, type);
		assertEquals("expected exactly one " + type.getSimpleName(), 1, all.size());
		return all.get(0);
	}

	private void assertCovered(SortedSet<FoldingRange> folds, EObject obj, String label) {
		assertTrue("expected a folding range for " + label, isCoveredBy(folds, obj));
	}

	/** True if some fold lies within the object's node region. */
	private boolean isCoveredBy(SortedSet<FoldingRange> folds, EObject obj) {
		ICompositeNode node = NodeModelUtils.getNode(obj);
		int start = node.getOffset();
		int end = node.getEndOffset();
		for (FoldingRange f : folds) {
			if (f.getOffset() >= start && (f.getOffset() + f.getLength()) <= end) {
				return true;
			}
		}
		return false;
	}

	private boolean spansMultipleLines(EObject obj) {
		ICompositeNode node = NodeModelUtils.getNode(obj);
		return node != null && node.getEndLine() > node.getStartLine();
	}
}
