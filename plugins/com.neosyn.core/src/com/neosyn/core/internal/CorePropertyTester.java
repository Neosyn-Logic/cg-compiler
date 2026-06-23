/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.internal;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IResource;

import com.neosyn.core.layout.ProjectLayout;

/**
 * This class defines a property tester.
 * 

 * 
 */
public class CorePropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		IResource resource = (IResource) receiver;
		if ("isPackage".equals(property)) {
			return ProjectLayout.isPackage(resource);
		} else if ("isSourceFolder".equals(property)) {
			return ProjectLayout.isSourceFolder(resource);
		}

		return false;
	}

}
