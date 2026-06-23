/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.layout;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.PlatformObject;

/**
 * This class defines an abstract tree element, adaptable to IResource.
 * 

 *
 */
public abstract class AbstractTreeElement extends PlatformObject implements ITreeElement {

	private IResource resource;

	public AbstractTreeElement(IResource resource) {
		this.resource = resource;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof AbstractTreeElement)) {
			return false;
		}
		AbstractTreeElement other = (AbstractTreeElement) obj;
		return resource.equals(other.resource);
	}

	@Override
	public IResource getResource() {
		return resource;
	}

	@Override
	public int hashCode() {
		return resource.hashCode();
	}

	@Override
	public boolean isPackage() {
		return false;
	}

	@Override
	public boolean isSourceFolder() {
		return false;
	}

}
