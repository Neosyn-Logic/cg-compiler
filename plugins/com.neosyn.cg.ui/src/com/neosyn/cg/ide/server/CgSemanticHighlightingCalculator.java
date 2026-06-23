/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.VarRef;

/**
 * Semantic-highlighting calculator for C⏚ (editor polish, step 1.4).
 *
 * <p>Adds the semantic distinctions a plain TextMate grammar can't make,
 * because they require name resolution:
 * <ul>
 *   <li>entity names (task / network / bundle / struct / enum) → {@code type};</li>
 *   <li>enum literals → {@code enumMember};</li>
 *   <li>port names — at the declaration <em>and</em> at every reference site
 *       (read {@code a.read()} and write {@code q.write()}) → {@code property},
 *       which is what makes a port visually distinct from a plain local.</li>
 * </ul>
 *
 * <p>Keywords, comments and literals are left to the TextMate grammar; emitting
 * them here too would be redundant. Bound in {@code CgIdeModule} over the
 * interface's {@code @ImplementedBy(DefaultSemanticHighlightingCalculator)}; the
 * server already advertises {@code semanticTokensProvider} with the standard
 * legend, so no capability change is needed.
 */
public class CgSemanticHighlightingCalculator implements ISemanticHighlightingCalculator {

	@Override
	public void provideHighlightingFor(XtextResource resource,
			IHighlightedPositionAcceptor acceptor, CancelIndicator cancelIndicator) {
		if (resource == null || resource.getContents().isEmpty()) {
			return;
		}
		EObject root = resource.getContents().get(0);

		for (CgEntity entity : EcoreUtil2.eAllOfType(root, CgEntity.class)) {
			highlightName(entity, acceptor, SemanticTokenTypes.Type);
		}
		for (Struct struct : EcoreUtil2.eAllOfType(root, Struct.class)) {
			highlightName(struct, acceptor, SemanticTokenTypes.Type);
		}
		for (com.neosyn.cg.cg.Enum enm : EcoreUtil2.eAllOfType(root, com.neosyn.cg.cg.Enum.class)) {
			highlightName(enm, acceptor, SemanticTokenTypes.Type);
			for (Variable literal : enm.getLiterals()) {
				highlightName(literal, acceptor, SemanticTokenTypes.EnumMember);
			}
		}
		for (PortDef port : EcoreUtil2.eAllOfType(root, PortDef.class)) {
			if (port.getVar() != null) {
				highlightName(port.getVar(), acceptor, SemanticTokenTypes.Property);
			}
		}
		for (ExpressionVariable expr : EcoreUtil2.eAllOfType(root, ExpressionVariable.class)) {
			highlightPortReference(expr.getSource(), acceptor);
		}
		for (StatementWrite write : EcoreUtil2.eAllOfType(root, StatementWrite.class)) {
			highlightPortReference(write.getPort(), acceptor);
		}
	}

	private void highlightName(EObject obj, IHighlightedPositionAcceptor acceptor, String tokenType) {
		List<INode> nodes = NodeModelUtils.findNodesForFeature(obj, CgPackage.Literals.NAMED__NAME);
		if (!nodes.isEmpty()) {
			INode n = nodes.get(0);
			// getOffset()/getLength() exclude leading/trailing hidden tokens
			// (whitespace, comments) — getTotal* would tag the leading space too.
			acceptor.addPosition(n.getOffset(), n.getLength(), tokenType);
		}
	}

	private void highlightPortReference(VarRef source, IHighlightedPositionAcceptor acceptor) {
		if (source == null) {
			return;
		}
		Variable var = source.getVariable();
		if (var == null || var.eIsProxy()) {
			return;
		}
		if (EcoreUtil2.getContainerOfType(var, PortDecl.class) == null) {
			return;
		}
		List<INode> nodes = NodeModelUtils.findNodesForFeature(source,
				CgPackage.Literals.VAR_REF__OBJECTS);
		if (!nodes.isEmpty()) {
			INode n = nodes.get(0);
			acceptor.addPosition(n.getOffset(), n.getLength(), SemanticTokenTypes.Property);
		}
	}
}
