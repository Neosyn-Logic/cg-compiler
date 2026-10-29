/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IReferenceDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.impl.ResourceDescriptionsProvider;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgPackage.Literals;

/**
 * Loads the "top" entities of a resource set — those never instantiated by any other entity
 * (computed as all instantiable/bundle URIs minus the URIs referenced by an {@code Inst.entity}).
 * These are the roots the instantiator elaborates from.
 */
public class TopEntitiesLoader {

	@Inject
	private IResourceDescription.Manager manager;

	@Inject
	private ResourceDescriptionsProvider provider;

	/**
	 * If the given qualified name is the name of a bundle, or the name of a variable or type
	 * defined by a bundle, adds the bundle to the <code>bundleUris</code> set.
	 * 
	 * @param bundleUris
	 *            a set of bundle URIs
	 * @param descs
	 *            an IResourceDescriptions
	 * @param name
	 *            a qualified name
	 */
	private void addBundle(Set<URI> bundleUris, IResourceDescriptions descs, QualifiedName name) {
		Iterable<IEObjectDescription> objDescs;
		objDescs = descs.getExportedObjects(EcorePackage.Literals.EOBJECT, name, false);
		for (IEObjectDescription objDesc : objDescs) {
			EClass type = objDesc.getEClass();
			if (type == Literals.BUNDLE) {
				bundleUris.add(objDesc.getEObjectURI());
			} else if (type == Literals.VARIABLE || type == Literals.TYPEDEF) {
				addBundle(bundleUris, descs, name.skipLast(1));
			}
		}
	}

	/**
	 * Collects the URIs of all resource descriptions that are available.
	 *
	 * @param resourceSet
	 *            resource set
	 * @return a set of URIs
	 */
	private Collection<URI> collectAllURIs(ResourceSet resourceSet) {
		Set<URI> allUris = Sets.newLinkedHashSet();

		IResourceDescriptions resourceDescriptions = provider.getResourceDescriptions(resourceSet);

		// collect bundles first, because we need to map their typedef declarations
		EClass type = Literals.BUNDLE;
		for (IEObjectDescription objDesc : resourceDescriptions.getExportedObjectsByType(type)) {
			// we normalize the URI because URIs of reference descriptions are normalized too
			// note that 'normalized' by EMF means from resource to plugin
			URI uri = resourceSet.getURIConverter().normalize(objDesc.getEObjectURI());
			allUris.add(uri);
		}

		// collect instantiable entities
		type = Literals.INSTANTIABLE;
		for (IEObjectDescription objDesc : resourceDescriptions.getExportedObjectsByType(type)) {
			// we normalize the URI because URIs of reference descriptions are normalized too
			// note that 'normalized' by EMF means from resource to plugin
			URI uri = resourceSet.getURIConverter().normalize(objDesc.getEObjectURI());

			// filters out objects whose URI is platform:/plugin (they can never be 'top' URIs)
			if (!uri.isPlatformPlugin()) {
				allUris.add(uri);
			}
		}

		return allUris;
	}

	public Iterable<Bundle> loadBundles(ResourceSet resourceSet, CgEntity entity) {
		Set<Bundle> bundles = Sets.newLinkedHashSet();
		Resource resource = entity.eResource();
		if (resource != null) {
			// The file's own IMPLICIT declaration bundle first. Top-level const/typedef/
			// struct/enum parse into an unnamed Bundle (grammar rule ImplicitBundle) that
			// is reached by SCOPE rather than by import, so it never shows up in
			// getImportedNames() and the global index holds no qualified name for it --
			// the loop below cannot find it. Without this its members have no IR Var and
			// the first use dies on a null Var deep in IR generation.
			for (EObject root : resource.getContents()) {
				if (!(root instanceof Module)) {
					continue;
				}
				for (CgEntity candidate : ((Module) root).getEntities()) {
					if (candidate instanceof Bundle && ((Bundle) candidate).getName() == null
							&& candidate != entity) {
						nameImplicitBundle((Bundle) candidate, resource);
						bundles.add((Bundle) candidate);
					}
				}
			}

			IResourceDescription resDesc = manager.getResourceDescription(resource);
			IResourceDescriptions descs = provider.getResourceDescriptions(resourceSet);

			// URIs of bundles
			Set<URI> bundleUris = Sets.newHashSet();
			for (QualifiedName name : resDesc.getImportedNames()) {
				addBundle(bundleUris, descs, name);
			}

			// load bundles
			for (URI uri : bundleUris) {
				Bundle bundle = (Bundle) resourceSet.getEObject(uri, true);
				if (bundle != entity) {
					bundles.add(bundle);
				}
			}
		}

		return bundles;
	}

	private Iterable<CgEntity> loadEntities(ResourceSet resourceSet, Collection<URI> topUris) {
		IResourceDescriptions resourceDescriptions = provider.getResourceDescriptions(resourceSet);

		// loads objects from topUris
		// note that URIs in topUris must be normalized in the Xtext sense for this to work
		// (platform:/plugin mapped to platform:/resource)
		List<CgEntity> entities = new ArrayList<>(topUris.size());
		for (URI uri : topUris) {
			URI uriRes = uri.trimFragment();
			IResourceDescription resDesc = resourceDescriptions.getResourceDescription(uriRes);
			EClass type = Literals.CG_ENTITY;
			for (IEObjectDescription objDesc : resDesc.getExportedObjectsByType(type)) {
				if (uri.equals(objDesc.getEObjectURI())) {
					EObject resolved = EcoreUtil.resolve(objDesc.getEObjectOrProxy(), resourceSet);
					entities.add((CgEntity) resolved);
				}
			}
		}
		return entities;
	}

	/**
	 * Finds all CgEntity objects that are at the top of the hierarchy. Computed as the set of URIs
	 * of all entities, minus the set of URIs of entities that are instantiated. Currently the
	 * collection this method returns includes bundles.
	 *
	 * @param resourceSet
	 *            a resource set from which we obtain an IResourceDescriptions object and that we
	 *            use for solving proxies
	 * @return an iterable over CgEntity
	 */
	public Iterable<CgEntity> loadTopEntities(ResourceSet resourceSet) {
		Collection<URI> allUris = collectAllURIs(resourceSet);
		// a top entity is one that is never instantiated by another
		Collection<URI> topUris = removeInstantiated(resourceSet, allUris);
		return loadEntities(resourceSet, topUris);
	}

	private Collection<URI> removeInstantiated(ResourceSet resourceSet, Collection<URI> allUris) {
		Collection<URI> topUris = allUris;
		IResourceDescriptions resourceDescriptions = provider.getResourceDescriptions(resourceSet);

		// remove all entities that are instantiated
		// we use the manager to get an IResourceDescription because
		// ResourceDescriptionsProvider may return CopiedResourceDescriptions
		// which do not have reference descriptions
		for (IResourceDescription resDesc : resourceDescriptions.getAllResourceDescriptions()) {
			URI uri = resDesc.getURI();
			Resource resource;
			try {
				resource = resourceSet.getResource(uri, true);
			} catch (WrappedException e) {
				// resource can't be created/loaded, just skip
				continue;
			}

			resDesc = manager.getResourceDescription(resource);
			for (IReferenceDescription refDesc : resDesc.getReferenceDescriptions()) {
				if (refDesc.getEReference() == Literals.INST__ENTITY) {
					URI uriInstantiable = refDesc.getTargetEObjectUri();
					topUris.remove(uriInstantiable);
				}
			}
		}
		return topUris;
	}


	/**
	 * Names the implicit declaration bundle after its file, e.g. {@code Foo.cg} ->
	 * {@code Foo_pkg}.
	 * <p>
	 * The name is an implementation detail -- these members are referenced unqualified,
	 * by scope -- but everything downstream keys off it: the entity mapper builds the IR
	 * entity name from it, and a bundle emits a file of localparams named after itself.
	 * A null name dies later on a null Var, far from the cause.
	 * <p>
	 * The {@code _pkg} suffix avoids the common collision: a file named {@code Foo.cg}
	 * usually declares {@code task Foo}, so naming the bundle plain {@code Foo} would
	 * clash with the entity beside it.
	 */
	private void nameImplicitBundle(Bundle bundle, Resource resource) {
		if (bundle.getName() != null) {
			return;
		}
		String file = resource.getURI().lastSegment();
		int dot = file.lastIndexOf('.');
		String base = dot > 0 ? file.substring(0, dot) : file;
		bundle.setName(base + "_pkg");
	}

}
