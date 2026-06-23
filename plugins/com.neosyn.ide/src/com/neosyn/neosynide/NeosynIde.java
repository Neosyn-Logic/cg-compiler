/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide;

import static org.eclipse.core.runtime.Platform.getPreferencesService;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;

import com.neosyn.core.NeosynCore;

/**
 * This class defines the Neosyn IDE plug-in.
 * 

 * 
 */
public class NeosynIde implements BundleActivator {

	private static BundleContext context;

	public static final String PLUGIN_ID = "com.neosyn.neosynide";

	/**
	 * Returns the bundle associated with this plug-in.
	 * 
	 * @return the bundle associated with this plug-in
	 */
	public static Bundle getBundle() {
		return context.getBundle();
	}

	/**
	 * Return the value stored in the preference store for the given key. If the key is not defined
	 * then return the specified default value.
	 * 
	 * @param key
	 *            the name of the preference
	 * @param defaultValue
	 *            the value to use if the preference is not defined
	 * @return the value of the preference or the given default value
	 */
	public static boolean getPreference(String key, boolean defaultValue) {
		return getPreferencesService().getBoolean(PLUGIN_ID, key, defaultValue, null);
	}

	/**
	 * Return the value stored in the preference store for the given key. If the key is not defined
	 * then return the specified default value.
	 * 
	 * @param key
	 *            the name of the preference
	 * @param defaultValue
	 *            the value to use if the preference is not defined
	 * @return the value of the preference or the given default value
	 */
	public static String getPreference(String key, String defaultValue) {
		return getPreferencesService().getString(PLUGIN_ID, key, defaultValue, null);
	}

	/**
	 * Sets the value of the given key in the preference store.
	 * 
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 */
	public static void setPreference(String key, boolean value) {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PLUGIN_ID);
		prefs.putBoolean(key, value);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			NeosynCore.log(e);
		}
	}

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		NeosynIde.context = bundleContext;
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		NeosynIde.context = null;
	}

}
