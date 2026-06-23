/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.debug;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.debug.IStratumBreakpointSupport;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.XtextResource;

import com.neosyn.cg.cg.Statement;

/**
 * This class implements breakpoint support for Cx based on Xbase's breakpoint
 * support class.
 * 
 * @author Sven Efftinge - Initial contribution and API
 support for Cx
 */
@SuppressWarnings("restriction")
public class CgStratumBreakpointSupport implements IStratumBreakpointSupport {

	public boolean isValidLineForBreakPoint(XtextResource resource, int line) {
		IParseResult parseResult = resource.getParseResult();
		if (parseResult == null)
			return false;
		ICompositeNode node = parseResult.getRootNode();
		return isValidLineForBreakpoint(node, line);
	}

	protected boolean isValidLineForBreakpoint(ICompositeNode node, int line) {
		for (INode n : node.getChildren()) {
			if (n.getStartLine() <= line && n.getEndLine() >= line) {
				EObject eObject = n.getSemanticElement();
				if (eObject instanceof Statement) {
					return true;
				}
				if (n instanceof ICompositeNode
						&& isValidLineForBreakpoint((ICompositeNode) n, line)) {
					return true;
				}
			}
			if (n.getStartLine() > line) {
				return false;
			}
		}
		return false;
	}

}
