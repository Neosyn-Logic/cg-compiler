/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.formatting2;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.formatting.ILineSeparatorInformation;
import org.eclipse.xtext.formatting.IWhitespaceInformationProvider;

/**
 * This class defines a whitespace information provider that returns a line
 * separator that is always "\n", because using the default implementation does
 * not work when formatting files from a different platform...
 * 

 * 
 */
public class WhitespaceInfoProvider extends
		IWhitespaceInformationProvider.Default {

	@Override
	public ILineSeparatorInformation getLineSeparatorInformation(URI uri) {
		return new ILineSeparatorInformation() {
			public String getLineSeparator() {
				return "\n";
			}
		};
	}

}
