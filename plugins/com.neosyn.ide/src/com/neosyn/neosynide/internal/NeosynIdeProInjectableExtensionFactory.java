/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal;

import org.osgi.framework.Bundle;

import com.neosyn.core.InjectableExtensionFactory;
import com.neosyn.neosynide.NeosynIde;

/**
 * This class defines a factory that injects objects. This needs to be redefined here to use this
 * bundle to load classes defined in this plug-in.
 * 

 * 
 */
public class NeosynIdeProInjectableExtensionFactory extends InjectableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return NeosynIde.getBundle();
	}

}
