/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import org.eclipse.emf.common.util.URI;

import com.neosyn.cg.cg.CgEntity;

/**
 * This class holds information about a Cx entity, its specialized name and URI of the corresponding
 * IR resource.
 * 

 *
 */
public class EntityInfo {

	private final CgEntity cxEntity;

	private final String name;

	private final boolean specialized;

	private final URI uri;

	public EntityInfo(CgEntity cxEntity, String name, URI uri, boolean specialized) {
		this.cxEntity = cxEntity;
		this.name = name;
		this.uri = uri;
		this.specialized = specialized;
	}

	public CgEntity getCxEntity() {
		return cxEntity;
	}

	public String getName() {
		return name;
	}

	public URI getURI() {
		return uri;
	}

	public boolean isSpecialized() {
		return specialized;
	}

	@Override
	public String toString() {
		return name + " " + uri;
	}

}
