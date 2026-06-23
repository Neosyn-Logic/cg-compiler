/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg;

import static com.neosyn.core.ICoreConstants.FILE_EXT_IR;
import static com.neosyn.core.ICoreConstants.FOLDER_IR;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;

/**
 * This class describes a special URI mapper that returns a valid absolute URI to a target .ir file
 * from a .cg URI.
 * 

 * 
 */
public class UriComputer extends ExtensibleURIConverterImpl {

	/**
	 * the unique instance of this mapper.
	 */
	public static final UriComputer INSTANCE = new UriComputer();

	private UriComputer() {
	}

	/**
	 * Computes the URI to a .ir file based on the context, uri, and name of the entity.
	 * 
	 * @param name
	 *            name of the entity
	 * @param uri
	 *            uri of a .cg resource
	 * @param context
	 *            URI to a module (may be <code>null</code>)
	 * @return a new absolute URI
	 */
	public URI computeUri(String name, URI uri, URI context) {
		URI result;
		if (uri.isPlatformResource()) {
			result = URI.createPlatformResourceURI(uri.segment(1) + "/" + FOLDER_IR, false);
		} else if (uri.isFile()) {
			URI trimmedURI = uri.trimFragment().trimQuery();
			URI normalized = getInternalURIMap().getURI(trimmedURI);
			result = keepPrefix(normalized, uri);
		} else if (uri.isPlatformPlugin()) {
			// return URI in the right place based on the context
			return computeUri(name, context, null);
		} else {
			result = uri;
		}

		result = result.appendSegments(name.split("\\.")).appendFileExtension(FILE_EXT_IR);

		return normalize(result);
	}

	private URI keepPrefix(URI normalized, URI uri) {
		String[] uriSegs = uri.segments();
		String[] normSegs = normalized.segments();
		int index = normSegs.length - 1;
		for (int i = uriSegs.length - 1; i >= 0 && index >= 0; i--, index--) {
			if (!uriSegs[i].equals(normSegs[index])) {
				break;
			}
		}
		return normalized.trimSegments(normSegs.length - index - 1);
	}

}
