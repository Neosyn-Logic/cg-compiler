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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.resource.XtextResource;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;

import com.neosyn.cg.cg.Module;
import com.neosyn.cg.ide.server.CgInlayHintService;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

/**
 * Service-level tests for {@link CgInlayHintService} (editor polish E1, iter #1).
 *
 * Parses tests/inlay/InlayFixture.cg and asserts the inlay-hint service emits a
 * ghost type annotation for off-line port references and nothing for locals.
 * The service is dependency-free, so a plain instance suffices (no injector).
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgInlayHintTests extends AbstractCxTest {

	private final CgInlayHintService service = new CgInlayHintService();

	private List<InlayHint> hints() throws Exception {
		Module module = getModule("inlay/InlayFixture.cg");
		XtextResource resource = (XtextResource) module.eResource();
		assertTrue("fixture must parse: " + resource.getErrors(),
			resource.getErrors().isEmpty());
		return service.computeInlayHints(resource);
	}

	@Test
	public void emitsPortTypeHints() throws Exception {
		List<InlayHint> hints = hints();
		assertTrue("expected a `: u8` hint for port a, got: " + labels(hints),
			hints.stream().anyMatch(h -> label(h).contains("u8")));
		assertTrue("expected a `: u16` hint for port q, got: " + labels(hints),
			hints.stream().anyMatch(h -> label(h).contains("u16")));
	}

	@Test
	public void hintsAreTypeKindAndColonPrefixed() throws Exception {
		List<InlayHint> hints = hints();
		assertTrue("expected at least one hint", !hints.isEmpty());
		for (InlayHint h : hints) {
			assertEquals("hints should be Type kind", InlayHintKind.Type, h.getKind());
			assertTrue("hint label should start with ': ', got: " + label(h),
				label(h).startsWith(": "));
		}
	}

	@Test
	public void doesNotHintLocalVariables() throws Exception {
		// The fixture's only non-port-typed token rendered would be `u8` on the
		// local — but locals are out of scope, so every hint must be u8 (port a)
		// or u16 (port q); none should be anchored to the `local` identifier.
		// Concretely: exactly two hints (one per off-line port reference).
		List<InlayHint> hints = hints();
		assertEquals("expected exactly two port hints (a, q), got: " + labels(hints),
			2, hints.size());
	}

	@Test
	public void positionAtComputesLineAndColumn() {
		// Unit-check the offset->Position math independent of Xtext.
		Position p = CgInlayHintService.positionAt("ab\ncde\nf", 5);
		assertEquals(1, p.getLine());
		assertEquals(2, p.getCharacter());
	}

	private static String label(InlayHint h) {
		return h.getLabel().isLeft() ? h.getLabel().getLeft() : "<parts>";
	}

	private static String labels(List<InlayHint> hints) {
		return hints.stream().map(CgInlayHintTests::label).reduce("", (a, b) -> a + " | " + b);
	}
}
