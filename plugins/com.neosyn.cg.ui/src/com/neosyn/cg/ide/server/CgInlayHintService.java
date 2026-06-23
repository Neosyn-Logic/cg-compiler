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
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.VarRef;

/**
 * Inlay-hint provider for C⏚ (editor polish E1, iter #1).
 *
 * <p>Emits a ghost type annotation ({@code : u8}, {@code : int<16>}) after a
 * <em>port</em> identifier reference — the {@code a} in {@code a.read()} and the
 * {@code q} in {@code q.write(...)}. The hint shows only when the port's
 * declaration is <strong>not on the same line</strong> as the use; on the
 * declaration line the type is already visible, so a hint there is pure noise.
 *
 * <p>Iter #1 scope is deliberately narrow (ports only): locals carry their type
 * at a usually-nearby declaration, whereas a port referenced deep inside
 * {@code loop()} is declared far away in the port section. Schedule cycle-count
 * hints (need partial codegen) and generic-position hints (need L3) are out of
 * scope; C⏚ has no FSM syntax, so the S118 "FSM state count" hint does not
 * exist.
 *
 * <p>Reads (port access in expressions) are {@link ExpressionVariable}s; writes
 * are {@link StatementWrite}s — both carry a {@link VarRef} we annotate. Pure
 * function of the parsed resource; tested directly by {@code CgInlayHintTests}.
 * {@code CgLanguageServerImpl#inlayHint} adapts the result to LSP4J and
 * advertises the capability.
 */
public class CgInlayHintService {

	/** Computes the inlay hints for the whole resource (eager, no range filter). */
	public List<InlayHint> computeInlayHints(XtextResource resource) {
		List<InlayHint> hints = new ArrayList<>();
		if (resource == null || resource.getContents().isEmpty()) {
			return hints;
		}
		String text = resource.getParseResult() != null
				? resource.getParseResult().getRootNode().getText() : null;
		if (text == null) {
			return hints;
		}
		EObject root = resource.getContents().get(0);
		for (ExpressionVariable expr : EcoreUtil2.eAllOfType(root, ExpressionVariable.class)) {
			addHint(hints, expr.getSource(), text);
		}
		for (StatementWrite write : EcoreUtil2.eAllOfType(root, StatementWrite.class)) {
			addHint(hints, write.getPort(), text);
		}
		return hints;
	}

	private void addHint(List<InlayHint> hints, VarRef source, String text) {
		if (source == null) {
			return;
		}
		Variable var = source.getVariable();
		if (var == null || var.eIsProxy()) {
			return;
		}
		// Ports only (iter #1).
		if (EcoreUtil2.getContainerOfType(var, PortDecl.class) == null) {
			return;
		}
		ICompositeNode declNode = NodeModelUtils.getNode(var);
		INode useNode = firstSegmentNode(source);
		if (declNode == null || useNode == null) {
			return;
		}
		// Same-line visibility rule: skip when the declaration shares the use's
		// line (the type is already on screen there).
		if (declNode.getStartLine() == useNode.getStartLine()) {
			return;
		}
		String label = ": " + typeText(CgUtil.getType(var));
		// getOffset()/getLength() exclude trailing hidden tokens, so the hint
		// sits right after the identifier rather than after following whitespace.
		int endOffset = useNode.getOffset() + useNode.getLength();
		InlayHint hint = new InlayHint(positionAt(text, endOffset),
				org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(label));
		hint.setKind(InlayHintKind.Type);
		hint.setPaddingLeft(Boolean.FALSE);
		hints.add(hint);
	}

	/**
	 * Node of the first reference segment <em>at the use site</em> (the {@code a}
	 * in {@code a.read()}). We must use {@code findNodesForFeature} rather than
	 * {@code getNode(getObjects().get(0))}: the objects are resolved cross-refs,
	 * so their node is the <em>declaration</em>, not the use.
	 */
	private INode firstSegmentNode(VarRef source) {
		List<INode> nodes = NodeModelUtils.findNodesForFeature(source,
				CgPackage.Literals.VAR_REF__OBJECTS);
		if (!nodes.isEmpty()) {
			return nodes.get(0);
		}
		return NodeModelUtils.getNode(source);
	}

	/** Source token text of a type node (e.g. "u8", "int<16>"); "void" when null. */
	private static String typeText(CgType type) {
		if (type == null) {
			return "void";
		}
		ICompositeNode node = NodeModelUtils.findActualNodeFor(type);
		if (node == null) {
			return type.eClass().getName();
		}
		String text = NodeModelUtils.getTokenText(node).trim();
		return text.isEmpty() ? type.eClass().getName() : text;
	}

	/** 0-based LSP {@link Position} for a character offset into {@code text}. */
	public static Position positionAt(String text, int offset) {
		int clamped = Math.max(0, Math.min(offset, text.length()));
		int line = 0;
		int lineStart = 0;
		for (int i = 0; i < clamped; i++) {
			if (text.charAt(i) == '\n') {
				line++;
				lineStart = i + 1;
			}
		}
		return new Position(line, clamped - lineStart);
	}
}
