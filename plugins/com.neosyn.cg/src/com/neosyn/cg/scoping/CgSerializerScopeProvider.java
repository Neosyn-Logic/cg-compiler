/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import static com.neosyn.cg.cg.CgPackage.Literals.VAR_REF__OBJECTS;
import static com.neosyn.cg.cg.CgPackage.Literals.INST__ENTITY;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.impl.SimpleScope;
import org.eclipse.xtext.scoping.impl.SingletonScope;

import com.google.inject.Inject;
import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.VarRef;

/**
 * This class defines a scope provider for use when serializing cross references, because the
 * default scope provider provides qualified name, and this is not what Xtext's default reference
 * updater seems to expect. Also this is how Xtend does it, so I suppose this is the "official" way
 * :-/
 * 

 *
 */
public class CgSerializerScopeProvider implements IScopeProvider {

	@Inject
	private IScopeProvider delegate;

	private IScope createInstSerializationScope(Inst inst) {
		Instantiable instantiable = inst.getEntity();
		String name = instantiable.getName();
		return new SingletonScope(EObjectDescription.create(name, instantiable), IScope.NULLSCOPE);
	}

	private IScope createReferenceSerializationScope(VarRef ref) {
		List<IEObjectDescription> descriptions = new ArrayList<>();
		for (Named named : ref.getObjects()) {
			if (!named.eIsProxy() && !(named.eContainer() instanceof ExpressionVariable)) {
				descriptions.add(EObjectDescription.create(named.getName(), named));
			}
		}

		return new SimpleScope(descriptions);
	}

	@Override
	public IScope getScope(EObject context, EReference reference) {
		if (reference == VAR_REF__OBJECTS) {
			IScope result = createReferenceSerializationScope((VarRef) context);
			return result;
		} else if (reference == INST__ENTITY) {
			IScope result = createInstSerializationScope((Inst) context);
			return result;
		}
		return delegate.getScope(context, reference);
	}

}
