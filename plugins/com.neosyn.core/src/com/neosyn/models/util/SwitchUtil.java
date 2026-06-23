/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.util;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Switch;

/**
 * This class defines utility stuff for EMF-switch based code transformations.
 * 

 * 
 */
public class SwitchUtil {

	/**
	 * to use in cascading switch
	 */
	public static final Void CASCADE = null;

	/**
	 * to use for non-cascading switch;
	 */
	public static final Void DONE = new Void();

	/**
	 * Checks the given objects with the given EMF switch, and returns <code>true</code> as soon as
	 * the {@link Switch#doSwitch(EObject)} method returns true. Otherwise returns false. If an
	 * object is null, returns false.
	 * 
	 * @param emfSwitch
	 *            an EMF switch
	 * @param eObjects
	 *            a varargs array of objects
	 * @return a boolean
	 */
	public static boolean check(Switch<Boolean> emfSwitch, EObject... eObjects) {
		for (EObject eObject : eObjects) {
			if (doSwitchBoolean(emfSwitch, eObject)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks the given objects with the given EMF switch, and returns <code>true</code> as soon as
	 * the {@link Switch#doSwitch(EObject)} method returns true. Otherwise returns false. If an
	 * object is null, returns false.
	 * 
	 * @param emfSwitch
	 *            an EMF switch
	 * @param eObjects
	 *            an iterable of objects
	 * @return a boolean
	 */
	public static boolean check(Switch<Boolean> emfSwitch, Iterable<? extends EObject> eObjects) {
		for (EObject eObject : eObjects) {
			if (doSwitchBoolean(emfSwitch, eObject)) {
				return true;
			}
		}
		return false;
	}

	private static boolean doSwitchBoolean(Switch<Boolean> emfSwitch, EObject eObject) {
		if (eObject == null) {
			return false;
		}
		return emfSwitch.doSwitch(eObject);
	}

	public static Void visit(Switch<Void> emfSwitch, EObject... eObjects) {
		for (EObject eObject : eObjects) {
			if (eObject != null) {
				emfSwitch.doSwitch(eObject);
			}
		}
		return DONE;
	}

	public static Void visit(Switch<Void> emfSwitch, Iterable<? extends EObject> eObjects) {
		for (EObject eObject : eObjects) {
			if (eObject != null) {
				emfSwitch.doSwitch(eObject);
			}
		}
		return DONE;
	}

}
