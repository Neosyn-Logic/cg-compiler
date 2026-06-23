/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.resource;

import java.util.Collection;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescription.Delta;
import org.eclipse.xtext.resource.IResourceDescription.Manager.AllChangeAware;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.impl.DefaultResourceDescriptionManager;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.instantiation.IInstantiator;

/**
 * This class describes a resource description manager.
 * 

 * 
 */
public class CgResourceDescriptionManager extends DefaultResourceDescriptionManager implements
		AllChangeAware {

	@Inject
	private IInstantiator instantiator;

	@Override
	public boolean isAffectedByAny(Collection<Delta> deltas, IResourceDescription candidate,
			IResourceDescriptions context) throws IllegalArgumentException {
		for (Delta delta : deltas) {
			IResourceDescription resDesc = delta.getNew();
			if (resDesc == null) {
				// ignore deleted/closed resources
				continue;
			}

			if (!Iterables.isEmpty(candidate.getExportedObjectsByType(Literals.BUNDLE))) {
				// a candidate is a bundle, is it loaded by the deltas?
				if (isAffected(getImportedNames(resDesc), candidate)) {
					return true;
				}
			}

			// check instantiator to see if necessary to revalidate specialized sub-entities
			for (IEObjectDescription objDesc : resDesc.getExportedObjectsByType(Literals.NETWORK)) {
				CgEntity entity = instantiator.getEntity(objDesc.getEObjectURI());
				if (entity != null) {
					Network network = (Network) entity;
					if (isAffected(network, candidate)) {
						return true;
					}
				}
			}
		}

		return isAffected(deltas, candidate, context);
	}

	private boolean isAffected(Network network, IResourceDescription candidate) {
		for (Inst inst : network.getInstances()) {
			Instantiable entity = inst.getEntity();
			if (entity != null) {
				URI uri = EcoreUtil.getURI(entity);
				if (candidate.getURI().equals(uri.trimFragment())) {
					// candidate is being instantiated
					if (instantiator.isSpecialized(uri)) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
