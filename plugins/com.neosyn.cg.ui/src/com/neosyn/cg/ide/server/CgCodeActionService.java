/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.PortDef;

/**
 * Code-action provider for C⏚ (editor polish, step 1.3).
 *
 * <p>Iter #1 offers one refactor: <strong>migrate a deprecated inline interface
 * keyword to its modern equivalent</strong> on a port declaration —
 * {@code sync}→{@code push}, {@code sync ready}→{@code stream},
 * {@code sync ack}→{@code confirm} (see C⏚ v2.0.0; CLAUDE.md). The action is
 * deliberately <em>not</em> tied to a validation diagnostic (no validator
 * currently flags the old keywords); it detects the {@code syncOld} grammar flag
 * directly and offers the rewrite for any deprecated port whose keyword falls in
 * the requested range.
 *
 * <p>Only the inline {@code PortDef} form is handled in iter #1; the rarer
 * {@code MultiPortDecl} block form is out of scope. Bound in {@code CgIdeModule}
 * via {@code bindICodeActionService2}; Xtext then advertises
 * {@code codeActionProvider}.
 */
public class CgCodeActionService implements ICodeActionService2 {

	@Override
	public List<Either<Command, CodeAction>> getCodeActions(Options options) {
		Resource resource = options.getResource();
		if (!(resource instanceof XtextResource) || resource.getContents().isEmpty()) {
			return Collections.emptyList();
		}
		XtextResource xtext = (XtextResource) resource;
		if (xtext.getParseResult() == null) {
			return Collections.emptyList();
		}
		String text = xtext.getParseResult().getRootNode().getText();
		Range requested = options.getCodeActionParams() != null
				? options.getCodeActionParams().getRange() : null;
		String uri = options.getURI();

		List<Either<Command, CodeAction>> actions = new ArrayList<>();
		EObject root = resource.getContents().get(0);
		for (PortDef port : EcoreUtil2.eAllOfType(root, PortDef.class)) {
			CodeAction action = keywordMigration(port, text, requested, uri);
			if (action != null) {
				actions.add(Either.forRight(action));
			}
		}
		return actions;
	}

	private CodeAction keywordMigration(PortDef port, String text, Range requested, String uri) {
		if (!port.isSyncOld()) {
			return null;
		}
		INode syncNode = firstNode(port, CgPackage.Literals.PORT_DEF__SYNC_OLD);
		if (syncNode == null) {
			return null;
		}
		// Determine the modern equivalent and the end of the span to replace.
		String oldText;
		String newKeyword;
		INode endNode;
		if (port.isReadyOld()) {
			oldText = "sync ready";
			newKeyword = "stream";
			endNode = firstNode(port, CgPackage.Literals.PORT_DEF__READY_OLD);
		} else if (port.isAckOld()) {
			oldText = "sync ack";
			newKeyword = "confirm";
			endNode = firstNode(port, CgPackage.Literals.PORT_DEF__ACK_OLD);
		} else {
			oldText = "sync";
			newKeyword = "push";
			endNode = syncNode;
		}
		if (endNode == null) {
			return null;
		}
		// getOffset()/getLength() exclude surrounding hidden tokens, so the edit
		// replaces exactly the keyword span (not the whitespace around it).
		int startOffset = syncNode.getOffset();
		int endOffset = endNode.getOffset() + endNode.getLength();
		Position start = CgInlayHintService.positionAt(text, startOffset);
		Position end = CgInlayHintService.positionAt(text, endOffset);

		// Only offer when the deprecated keyword's line is within the request.
		if (requested != null
				&& (start.getLine() < requested.getStart().getLine()
						|| start.getLine() > requested.getEnd().getLine())) {
			return null;
		}

		TextEdit edit = new TextEdit(new Range(start, end), newKeyword);
		WorkspaceEdit wsEdit = new WorkspaceEdit();
		wsEdit.setChanges(Map.of(uri, List.of(edit)));

		CodeAction action = new CodeAction("Replace deprecated '" + oldText + "' with '"
				+ newKeyword + "'");
		action.setKind(CodeActionKind.QuickFix);
		action.setEdit(wsEdit);
		return action;
	}

	private static INode firstNode(EObject obj, org.eclipse.emf.ecore.EStructuralFeature feature) {
		List<INode> nodes = NodeModelUtils.findNodesForFeature(obj, feature);
		return nodes.isEmpty() ? null : nodes.get(0);
	}
}
