/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.neosyn.core.IFileWriter;
import com.neosyn.core.NeosynCore;
import com.neosyn.core.util.CoreUtil;

/**
 * This class defines an implementation of a IFileWriter based on the Eclipse IFile class. The name
 * of the file must be relative to a project.
 * 

 * 
 */
public class EclipseFileWriter implements IFileWriter {

	private IProject project;

	/**
	 * Taken from org.eclipse.xtext.builder.EclipseResourceFileSystemAccess2, (c) 2011 itemis AG
	 * 
	 * @param container
	 * @throws CoreException
	 */
	private void ensureExists(IContainer container) throws CoreException {
		if (container.exists()) {
			return;
		} else if (container instanceof IFolder) {
			ensureExists(container.getParent());
			((IFolder) container).create(true, true, null);
		}
	}

	@Override
	public boolean exists(String fileName) {
		IFile file = project.getFile(fileName);
		return file.exists();
	}

	@Override
	public String getAbsolutePath(String fileName) {
		return project.getFile(fileName).getLocation().toString();
	}

	@Override
	public void remove(String fileName) {
		IFile file = project.getFile(fileName);
		try {
			file.delete(true, null);
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}

	@Override
	public void setOutputFolder(String projectName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		project = root.getProject(projectName);
	}

	@Override
	public void write(String fileName, CharSequence sequence) {
		if (sequence == null) {
			return;
		}

		String contents = sequence.toString();
		InputStream source = new ByteArrayInputStream(contents.getBytes());
		write(fileName, source);
	}

	@Override
	public void write(String fileName, InputStream source) {
		IFile file = project.getFile(fileName);
		try {
			CoreUtil.ensureCaseConsistency(file.getFullPath());

			if (file.exists()) {
				file.setContents(source, true, true, null);
			} else {
				ensureExists(file.getParent());
				file.create(source, true, null);
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}

}
