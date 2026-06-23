/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.impl;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;

/**
 * This class defines an XMI-based resource factory.
 * 

 * 
 */
public class IrResourceFactoryImpl extends ResourceFactoryImpl {
	
	public IrResourceFactoryImpl() {
		super();
	}

	@Override
	public Resource createResource(URI uri) {
		IrResourceImpl result = new IrResourceImpl(uri);
		return result;
	}

}
