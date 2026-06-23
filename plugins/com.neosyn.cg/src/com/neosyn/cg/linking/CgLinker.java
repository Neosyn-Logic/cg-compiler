/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.linking;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.diagnostics.IDiagnosticConsumer;
import org.eclipse.xtext.linking.lazy.LazyLinker;

import com.neosyn.cg.cg.ExpressionVariable;

/**
 * This class extends the lazy linker to clear the property variables in ExpressionVariable.
 * 

 *
 */
public class CgLinker extends LazyLinker {

	@Override
	protected void beforeModelLinked(EObject model, IDiagnosticConsumer diagnosticsConsumer) {
		super.beforeModelLinked(model, diagnosticsConsumer);

		Resource resource = model.eResource();
		TreeIterator<EObject> it = EcoreUtil.getAllContents(resource, false);
		while (it.hasNext()) {
			EObject eObject = it.next();
			if (eObject instanceof ExpressionVariable) {
				ExpressionVariable expr = (ExpressionVariable) eObject;
				if (expr.getProperty() != null) {
					expr.setProperty(null);
				}
			}
		}
	}

}
