/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.osgi.framework.Bundle;

/**
 * This class defines a factory that injects objects.
 * 

 * 
 */
public class InjectableExtensionFactory implements IExecutableExtension,
		IExecutableExtensionFactory {

	private String clazzName;

	private IConfigurationElement config;

	@Override
	public Object create() throws CoreException {
		try {
			Class<?> clazz = getBundle().loadClass(clazzName);

			// if class found, inject and return result
			Object result = NeosynCore.getDefault().getInstance(clazz);
			if (result instanceof IExecutableExtension) {
				((IExecutableExtension) result).setInitializationData(config, null, null);
			}
			return result;
		} catch (ClassNotFoundException e) {
		}

		// could not find class in this bundle, return null
		return null;
	}

	/**
	 * Returns the bundle to use to load classes.
	 * 
	 * @return the bundle to use to load classes
	 */
	protected Bundle getBundle() {
		return NeosynCore.getBundle();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		if (data instanceof String) {
			clazzName = (String) data;
		} else if (data instanceof Map<?, ?>) {
			clazzName = ((Map<String, String>) data).get("className");
		}
		this.config = config;
	}

}
