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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;

import com.google.inject.Injector;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.ide.CgIdeSetup;
import com.neosyn.cg.ide.server.CgCodeActionService;
import com.neosyn.cg.tests.AbstractCxTest;
import com.neosyn.cg.tests.CustomCgInjectorProvider;

/**
 * Service-level tests for {@link CgCodeActionService} (editor polish step 1.3):
 * deprecated interface-keyword migration on port declarations.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class CgCodeActionTests extends AbstractCxTest {

	private final CgCodeActionService service = new CgCodeActionService();

	private List<CodeAction> actionsOverWholeFile() throws Exception {
		Module module = getModule("codeaction/DeprecatedKeywords.cg");
		XtextResource resource = (XtextResource) module.eResource();
		assertTrue("fixture must parse: " + resource.getErrors(),
			resource.getErrors().isEmpty());
		String uri = resource.getURI().toString();

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(uri));
		params.setRange(new Range(new Position(0, 0), new Position(100, 0)));
		params.setContext(new CodeActionContext(java.util.Collections.emptyList()));

		ICodeActionService2.Options options = new ICodeActionService2.Options();
		options.setResource(resource);
		options.setURI(uri);
		options.setCodeActionParams(params);
		options.setCancelIndicator(CancelIndicator.NullImpl);

		return service.getCodeActions(options).stream()
			.filter(Either::isRight).map(Either::getRight).collect(Collectors.toList());
	}

	@Test
	public void offersMigrationForEachDeprecatedKeyword() throws Exception {
		List<CodeAction> actions = actionsOverWholeFile();
		List<String> titles = actions.stream().map(CodeAction::getTitle).collect(Collectors.toList());
		assertEquals("expected one action per deprecated port, got: " + titles, 3, actions.size());
		assertTrue(titles.toString().contains("'sync' with 'push'"));
		assertTrue(titles.toString().contains("'sync ready' with 'stream'"));
		assertTrue(titles.toString().contains("'sync ack' with 'confirm'"));
	}

	@Test
	public void editsReplaceWithModernKeyword() throws Exception {
		List<CodeAction> actions = actionsOverWholeFile();
		for (CodeAction a : actions) {
			List<TextEdit> edits = a.getEdit().getChanges().values().iterator().next();
			assertEquals("one edit per action", 1, edits.size());
			String nt = edits.get(0).getNewText();
			assertTrue("replacement should be a modern keyword, got: " + nt,
				nt.equals("push") || nt.equals("stream") || nt.equals("confirm"));
		}
	}

	@Test
	public void respectsRequestedRange() throws Exception {
		// A range on line 0 (the package line) covers no deprecated port → no actions.
		Module module = getModule("codeaction/DeprecatedKeywords.cg");
		XtextResource resource = (XtextResource) module.eResource();
		String uri = resource.getURI().toString();
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(uri));
		params.setRange(new Range(new Position(0, 0), new Position(0, 0)));
		params.setContext(new CodeActionContext(java.util.Collections.emptyList()));
		ICodeActionService2.Options options = new ICodeActionService2.Options();
		options.setResource(resource);
		options.setURI(uri);
		options.setCodeActionParams(params);

		assertTrue("no deprecated ports on the package line",
			service.getCodeActions(options).isEmpty());
	}

	@Test
	public void lspInjectorBindsCgCodeActionService() {
		Injector ide = new CgIdeSetup().createInjector();
		ICodeActionService2 bound = ide.getInstance(ICodeActionService2.class);
		assertTrue("LSP injector should bind CgCodeActionService, got "
			+ bound.getClass().getName(), bound instanceof CgCodeActionService);
	}
}
