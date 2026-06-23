/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal;

import java.util.List;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EObject;

/**
 * This class defines an adapter that records the original object from which a copy is made.
 * 

 * 
 */
public class CopyOf extends AdapterImpl implements Adapter {

	/**
	 * If the given object is a copy of an original object (it has a CopyOf adapter), returns the
	 * original object. Otherwise, returns the given object.
	 * 
	 * @param eObject
	 *            an EObject
	 * @return another EObject if the one given was a copy, or <code>eObject</code> itself otherwise
	 */
	public static final <T extends EObject> T getOriginal(T eObject) {
		List<Adapter> adapters = eObject.eAdapters();
		for (int i = 0; i < adapters.size(); i++) {
			Adapter adapter = adapters.get(i);
			if (adapter instanceof CopyOf) {
				CopyOf copyOf = (CopyOf) adapter;

				@SuppressWarnings("unchecked")
				T original = (T) copyOf.getOriginal();
				return original;
			}
		}
		return eObject;
	}

	private final EObject original;

	public CopyOf(EObject eObject) {
		// it turns out that eObject is often a copy of another object
		// (especially when invoked from IfDeveloper and CycleScheduler)
		// to avoid a multiple (possibly long) copy-of chain, we look for the original object
		// (and if eObject is already an original object, getOriginal will simply return it)
		// also, this way we don't have to make getOriginal recursive
		this.original = getOriginal(eObject);
	}

	public EObject getOriginal() {
		return original;
	}

}
