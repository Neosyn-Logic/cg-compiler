/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;

import com.google.inject.Injector;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.ide.CgIdeSetup;
import com.neosyn.cg.ide.server.CgSemanticHighlightingCalculator;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

/**
 * Service-level tests for {@link CgSemanticHighlightingCalculator} (editor
 * polish step 1.4). Reuses tests/folding/FoldingFixture.cg (entities, an enum,
 * ports) and asserts the calculator tags the right source tokens.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgSemanticTokenTests extends AbstractCxTest {

	/** Records every addPosition call as (text, tokenType). */
	private static final class Capture implements IHighlightedPositionAcceptor {
		final List<String[]> records = new ArrayList<>();
		final String text;

		Capture(String text) {
			this.text = text;
		}

		@Override
		public void addPosition(int offset, int length, String... ids) {
			String tok = text.substring(offset, offset + length);
			records.add(new String[] { tok, ids.length > 0 ? ids[0] : "" });
		}

		List<String> textsOfType(String tokenType) {
			List<String> out = new ArrayList<>();
			for (String[] r : records) {
				if (r[1].equals(tokenType)) {
					out.add(r[0]);
				}
			}
			return out;
		}
	}

	private Capture highlight() throws Exception {
		Module module = getModule("folding/FoldingFixture.cg");
		XtextResource resource = (XtextResource) module.eResource();
		assertTrue("fixture must parse: " + resource.getErrors(),
			resource.getErrors().isEmpty());
		String text = resource.getParseResult().getRootNode().getText();
		Capture capture = new Capture(text);
		new CgSemanticHighlightingCalculator()
			.provideHighlightingFor(resource, capture, CancelIndicator.NullImpl);
		return capture;
	}

	@Test
	public void tagsEntityNamesAsType() throws Exception {
		List<String> types = highlight().textsOfType(SemanticTokenTypes.Type);
		assertTrue("expected bundle/task/network/struct/enum names as type, got: " + types,
			types.contains("Defs") && types.contains("Worker") && types.contains("Top")
				&& types.contains("Pair") && types.contains("Color"));
	}

	@Test
	public void tagsEnumLiteralsAsEnumMember() throws Exception {
		List<String> members = highlight().textsOfType(SemanticTokenTypes.EnumMember);
		assertTrue("expected RED/GREEN/BLUE as enumMember, got: " + members,
			members.contains("RED") && members.contains("GREEN") && members.contains("BLUE"));
	}

	@Test
	public void tagsPortsAsProperty() throws Exception {
		List<String> props = highlight().textsOfType(SemanticTokenTypes.Property);
		// `a` and `q` appear both at declaration and at the read/write use sites.
		assertTrue("expected port names a and q as property, got: " + props,
			props.contains("a") && props.contains("q"));
	}

	@Test
	public void lspInjectorBindsCgCalculator() {
		Injector ide = new CgIdeSetup().createInjector();
		ISemanticHighlightingCalculator bound =
			ide.getInstance(ISemanticHighlightingCalculator.class);
		assertTrue("LSP injector should bind CgSemanticHighlightingCalculator, got "
			+ bound.getClass().getName(), bound instanceof CgSemanticHighlightingCalculator);
	}
}
