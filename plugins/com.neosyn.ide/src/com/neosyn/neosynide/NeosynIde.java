/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * This class defines the Neosyn IDE plug-in.
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

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		NeosynIde.context = bundleContext;
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		NeosynIde.context = null;
	}

}
