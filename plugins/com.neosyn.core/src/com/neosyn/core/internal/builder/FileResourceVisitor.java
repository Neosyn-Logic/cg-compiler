/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.internal.builder;

import static com.neosyn.core.ICoreConstants.FILE_EXT_CX;
import static com.neosyn.core.ICoreConstants.FILE_EXT_IR;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;

/**
 * This class defines a file resource visitor.
 * 

 * 
 */
public class FileResourceVisitor implements IResourceVisitor, IResourceDeltaVisitor {

	private final List<IFile> derived;

	private final List<IFile> removed;

	private final List<IFile> sources;

	public FileResourceVisitor() {
		derived = new ArrayList<>();
		removed = new ArrayList<>();
		sources = new ArrayList<>();
	}

	public List<IFile> getDerived() {
		return derived;
	}

	public List<IFile> getRemoved() {
		return removed;
	}

	public List<IFile> getSources() {
		return sources;
	}

	@Override
	public boolean visit(IResource resource) {
		visitResource(resource);
		return true;
	}

	@Override
	public boolean visit(IResourceDelta delta) {
		int kind = delta.getKind();
		if (kind == IResourceDelta.ADDED || kind == IResourceDelta.CHANGED) {
			visitResource(delta.getResource());
		} else if (kind == IResourceDelta.REMOVED) {
			IResource resource = delta.getResource();
			if (resource.getType() == IResource.FILE) {
				IFile file = (IFile) resource;
				String fileExt = resource.getFileExtension();

				if (fileExt != null) {
					switch (fileExt) {
					case FILE_EXT_IR:
						removed.add(file);
					}
				}
			}
		}

		return true;
	}

	/**
	 * Visits a resource and adds the given resource to the appropriate list (if any).
	 * 
	 * @param resource
	 *            the resource being added/changed or visited
	 */
	private void visitResource(IResource resource) {
		if (resource.getType() == IResource.FILE) {
			IFile file = (IFile) resource;
			String fileExt = resource.getFileExtension();

			if (fileExt != null) {
				switch (fileExt) {
				case FILE_EXT_CX:
					sources.add(file);
					break;
				case FILE_EXT_IR:
					derived.add(file);
					break;
				}
			}
		}
	}

}
