/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.layout;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.neosyn.core.NeosynCore;
import com.neosyn.core.NeosynNature;

/**
 * This class defines helper methods to get the layout of a project.
 * 

 *
 */
public class ProjectLayout {

	public static final String FOLDER_SRC = "src";

	/**
	 * Returns the direct children of the given project as an array containing either source folder
	 * or resources.
	 * 
	 * @param project
	 * @return an array of objects
	 */
	public static Object[] getChildren(IProject project) {
		if (!project.isAccessible()) {
			return new Object[0];
		}

		List<Object> children = new ArrayList<>();
		try {
			for (IResource resource : project.members()) {
				ITreeElement element = getTreeElement(resource);
				if (element == null) {
					children.add(resource);
				} else {
					children.add(element);
				}
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
		return children.toArray();
	}

	/**
	 * Returns the source folder of the given project, or <code>null</code>.
	 * 
	 * @param project
	 *            a project
	 * @return a source folder, may be <code>null</code> if not accessible or does not exist
	 */
	public static SourceFolder getSourceFolder(IProject project) {
		try {
			if (project.isAccessible() && project.hasNature(NeosynNature.NATURE_ID)) {
				IFolder folder = project.getFolder(FOLDER_SRC);
				if (folder.exists()) {
					return new SourceFolder(folder);
				}
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}

		return null;
	}

	/**
	 * Returns a tree element for the given resource.
	 * 
	 * @param resource
	 * @return a tree element; may be <code>null</code>
	 */
	public static ITreeElement getTreeElement(IResource resource) {
		if (resource.getType() != IResource.FOLDER) {
			return null;
		}

		IProject project = resource.getProject();
		SourceFolder sourceFolder = getSourceFolder(project);
		if (sourceFolder == null) {
			// project is not accessible, is not Neosyn, or has no existing source folder
			return null;
		}

		// if root folder is the source folder, we can return a valid tree element
		IPath path = resource.getFullPath();
		boolean isRootFolder = path.segmentCount() == 2;
		IResource rootFolder = isRootFolder ? resource : project.getFolder(path.segment(1));
		if (rootFolder.equals(sourceFolder.getResource())) {
			return isRootFolder ? sourceFolder : new Package(resource);
		}

		return null;
	}

	/**
	 * Returns <code>true</code> if the given resource is a package located inside a source folder.
	 * 
	 * @param resource
	 *            a resource
	 * @return a boolean indicating if the resource is a package
	 */
	public static boolean isPackage(IResource resource) {
		ITreeElement element = getTreeElement(resource);
		return element != null && element.isPackage();
	}

	/**
	 * Returns <code>true</code> if the given resource is a source folder.
	 * 
	 * @param resource
	 *            a resource
	 * @return a boolean indicating if the resource is a source folder
	 */
	public static boolean isSourceFolder(IResource resource) {
		ITreeElement element = getTreeElement(resource);
		return element != null && element.isSourceFolder();
	}

}
