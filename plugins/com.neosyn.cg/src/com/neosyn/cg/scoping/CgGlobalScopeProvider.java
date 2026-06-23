/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.DefaultGlobalScopeProvider;

import com.google.common.base.Predicate;

/**
 * This class extends the default global scope provider with a parent scope that knows about
 * built-in components.
 * 

 * 
 */
public class CgGlobalScopeProvider extends DefaultGlobalScopeProvider {

	@Override
	protected IScope getScope(final Resource resource, boolean ignoreCase, EClass type,
			Predicate<IEObjectDescription> filter) {
		ComponentScope parent = new ComponentScope(IScope.NULLSCOPE, resource.getResourceSet());
		return getScope(parent, resource, ignoreCase, type, filter);
	}

}
