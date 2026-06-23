/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.layout;

import static com.neosyn.core.ICoreConstants.FILE_EXT_CX;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.google.common.base.Joiner;
import com.neosyn.core.NeosynCore;

/**
 * This class defines a package in the project tree.
 * 

 *
 */
public class Package extends AbstractTreeElement {

	private String name;

	public Package(IResource resource) {
		super(resource);
	}

	public Object[] getFiles() {
		IFolder folder = (IFolder) getResource();
		List<IFile> files = new ArrayList<>();
		try {
			for (IResource member : folder.members()) {
				if (member.getType() == IResource.FILE) {
					files.add((IFile) member);
				}
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
		return files.toArray();
	}

	@Override
	public String getName() {
		if (name == null) {
			IPath path = getResource().getFullPath();
			String[] segments = path.removeFirstSegments(2).segments();
			name = Joiner.on('.').join(segments);
		}
		return name;
	}

	public SourceFolder getSourceFolder() {
		return ProjectLayout.getSourceFolder(getResource().getProject());
	}

	public boolean isEmpty() {
		for (Object obj : getFiles()) {
			IFile file = (IFile) obj;
			if (FILE_EXT_CX.equals(file.getFileExtension())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isPackage() {
		return true;
	}

	@Override
	public String toString() {
		return "package " + getName();
	}

}
