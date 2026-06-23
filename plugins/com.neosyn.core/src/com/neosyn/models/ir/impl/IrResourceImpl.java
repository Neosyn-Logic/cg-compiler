/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.impl;

import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

/**
 * This class defines a resource implementation for the Df model which is used to serialize
 * to/deserialize from XDF.
 * 
 * @author Synflow team (now Neosyn)
 * 
 */
public class IrResourceImpl extends XMIResourceImpl {

	public IrResourceImpl() {
		initResource();
	}

	public IrResourceImpl(URI uri) {
		super(uri);
		initResource();
	}

	private void initResource() {
		setOptions();
		setEncoding("UTF-8");
	}

	private void setOptions() {
		getDefaultSaveOptions().put(OPTION_USE_ENCODED_ATTRIBUTE_STYLE, Boolean.TRUE);
		getDefaultSaveOptions().put(OPTION_LINE_WIDTH, 80);

		setOptions(getDefaultLoadOptions());
		setOptions(getDefaultSaveOptions());
	}

	private void setOptions(Map<Object, Object> options) {
		options.put(OPTION_URI_HANDLER, new URIHandlerImpl.PlatformSchemeAware());
	}

}
