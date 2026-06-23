/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import org.eclipse.xtext.linking.impl.ImportedNamesAdapter;

import com.google.inject.Provider;

/**
 * This class defines a provider for {@link ImportedNamesAdapter} to use the Cx-specific version.
 * 

 *
 */
public class CgImportedNamesAdapterProvider implements Provider<ImportedNamesAdapter> {

	@Override
	public ImportedNamesAdapter get() {
		return new CgImportedNamesAdapter();
	}

}
