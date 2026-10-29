/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.linking;

import java.util.ArrayList;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.diagnostics.DiagnosticMessage;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.linking.impl.LinkingDiagnosticMessageProvider;

import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Enum;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Typedef;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.resource.CgResourceStrategy;

/**
 * This class provides custom messages for linking diagnostics.
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
			String file = declaredAtTopOfAnotherFile(element, link);
			if (file != null) {
				return new DiagnosticMessage(link + " cannot be resolved: it is declared at the top of "
						+ file + ", and a declaration there is visible only in that file. To share it,"
						+ " put it in a bundle and import it", Severity.ERROR, null);
			}
			return new DiagnosticMessage(link + " cannot be resolved", Severity.ERROR, null);
		}
		return super.getUnresolvedProxyMessage(context);
	}

	/**
	 * The name of another loaded file of the same package that declares {@code name} at file
	 * scope, or null. Such a declaration is private to its file (TRAPS T123), and without saying
	 * so the error reads like a typo -- a user was told "no compiled body" and then plain
	 * "cannot be resolved" for a function it could see in the next file.
	 */
	private static String declaredAtTopOfAnotherFile(EObject element, String name) {
		Resource own = element.eResource();
		if (own == null || own.getResourceSet() == null || own.getContents().isEmpty()
				|| !(own.getContents().get(0) instanceof Module)) {
			return null;
		}
		String pkg = ((Module) own.getContents().get(0)).getPackage();
		for (Resource other : new ArrayList<>(own.getResourceSet().getResources())) {
			if (other == own || other.getContents().isEmpty()
					|| !(other.getContents().get(0) instanceof Module)) {
				continue;
			}
			Module module = (Module) other.getContents().get(0);
			if (pkg == null || !pkg.equals(module.getPackage())) {
				continue;
			}
			for (CgEntity entity : module.getEntities()) {
				if (entity instanceof Bundle && CgResourceStrategy.isImplicitBundle((Bundle) entity)
						&& declares((Bundle) entity, name)) {
					return other.getURI().lastSegment();
				}
			}
		}
		return null;
	}

	private static boolean declares(Bundle bundle, String name) {
		for (Typedef t : bundle.getTypes()) {
			if (name.equals(t.getName())) {
				return true;
			}
		}
		for (Struct t : bundle.getStructs()) {
			if (name.equals(t.getName())) {
				return true;
			}
		}
		for (Enum t : bundle.getEnums()) {
			if (name.equals(t.getName())) {
				return true;
			}
		}
		for (VarDecl decl : bundle.getDecls()) {
			for (Variable v : decl.getVariables()) {
				if (name.equals(v.getName())) {
					return true;
				}
			}
		}
		return false;
	}

}
