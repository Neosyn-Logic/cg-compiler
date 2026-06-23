/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.linking;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.diagnostics.DiagnosticMessage;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.linking.impl.LinkingDiagnosticMessageProvider;

import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.VarRef;

/**
 * This class provides custom messages for linking diagnostics.
 * 

 * 
 */
public class CgLinkingDiagnosticMessageProvider extends LinkingDiagnosticMessageProvider {

	@Override
	public DiagnosticMessage getUnresolvedProxyMessage(final ILinkingDiagnosticContext context) {
		EObject element = context.getContext();
		String link = context.getLinkText();
		if (element instanceof CgEntity) {
			return new DiagnosticMessage(link + " cannot be imported", Severity.ERROR, null);
		} else if (element instanceof VarRef) {
			return new DiagnosticMessage(link + " cannot be resolved", Severity.ERROR, null);
		}
		return super.getUnresolvedProxyMessage(context);
	}

}
