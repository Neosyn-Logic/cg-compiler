/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.internal.builder;

import static com.neosyn.core.ICoreConstants.FOLDER_CLASSES;
import static com.neosyn.core.ICoreConstants.FOLDER_IR;
import static com.neosyn.core.ICoreConstants.FOLDER_SIM;
import static com.neosyn.core.ICoreConstants.FOLDER_TESTBENCH;
import static com.neosyn.models.util.EcoreHelper.getEObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.ecore.resource.ResourceSet;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.NeosynCore;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.util.EcoreHelper;

/**
 * This class defines the Neosyn builder.
 *

 *
 */
public class NeosynBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "com.neosyn.core.builder";

	@Inject
	@Named("Java")
	private ICodeGenerator javaGenerator;

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) {
		IProject project = getProject();

		// clean markers that might be set if code generators are not configured properly
		try {
			project.deleteMarkers(IMarker.PROBLEM, false, 0);
		} catch (CoreException e) {
			NeosynCore.log(e);
		}

		// visit project to collect resources
		FileResourceVisitor visitor = new FileResourceVisitor();
		collectResources(kind, visitor);

		// load all derived files (actors and networks)
		ResourceSet set = EcoreHelper.newResourceSet();
		List<Entity> entities = new ArrayList<>();
		List<IFile> derived = visitor.getDerived();
		for (IFile file : derived) {
			Entity entity = getEObject(set, file, Entity.class);
			if (entity != null) {
				entities.add(entity);
			}
		}

		// get generator for the project
		List<IFile> removed = visitor.getRemoved();
		javaGenerator.setOutputFolder(project.getName());
		generateCode(javaGenerator, kind, entities, removed, monitor);

		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		// clean up build state
		final IProject project = getProject();

		// delete files in .ir folder
		SubMonitor subMonitor = SubMonitor.convert(monitor, 6);
		deleteFiles(project, FOLDER_IR, subMonitor.newChild(1));

		deleteFiles(project, FOLDER_SIM + '/' + FOLDER_CLASSES, subMonitor.newChild(1));

		// clean up "-gen" folders
		deleteFiles(project, "verilog-gen", subMonitor.newChild(1));
		deleteFiles(project, "vhdl-gen", subMonitor.newChild(1));
		deleteFiles(project, FOLDER_TESTBENCH, subMonitor.newChild(1));
	}

	/**
	 * Visits the current project or delta with the given visitor to collect resources to build.
	 * 
	 * @param kind
	 * @param visitor
	 */
	private void collectResources(int kind, FileResourceVisitor visitor) {
		IProject project = getProject();
		try {
			switch (kind) {
			case FULL_BUILD:
				project.accept(visitor);
				break;

			case AUTO_BUILD:
			case INCREMENTAL_BUILD:
				IResourceDelta delta = getDelta(getProject());
				if (delta != null) {
					delta.accept(visitor);
				}
				break;
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}

	/**
	 * Removes all the files in the folder with the given name in the given project.
	 * 
	 * @param project
	 *            a project
	 * @param name
	 *            name of the folder to clean
	 */
	private void deleteFiles(IProject project, String name, IProgressMonitor monitor) {
		IFolder folder = project.getFolder(new Path(name));
		if (!folder.exists()) {
			return;
		}

		try {
			// first refresh so that everything can be removed by delete
			folder.refreshLocal(IResource.DEPTH_INFINITE, null);

			// find members and delete them
			IResource[] members = folder.members();
			SubMonitor subMonitor = SubMonitor.convert(monitor, members.length);
			for (IResource member : members) {
				member.delete(true, subMonitor);
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}

	/**
	 * Uses the given generator to generate code for each file in the given list.
	 * 
	 * @param generator
	 *            a generator
	 * @param entities
	 *            a list of entities
	 * @param monitor
	 *            a monitor
	 */
	private void generateCode(ICodeGenerator generator, int kind, List<Entity> entities,
			List<IFile> removed, IProgressMonitor monitor) {
		// removes generated files correspond to removed .ir files
		for (IFile file : removed) {
			IProject project = file.getProject();
			IFolder irFolder = project.getFolder(FOLDER_IR);
			IPath path = CoreUtil.getRelative(irFolder, file);

			String name = path.removeFileExtension().toString().replace('/', '.');
			generator.remove(name);
		}

		final String taskName = "Generating " + generator.getName() + " ";
		final int size = entities.size();
		SubMonitor subMonitor = SubMonitor.convert(monitor, taskName, size);

		int i = 1;
		for (Entity entity : entities) {
			if (subMonitor.isCanceled()) {
				break;
			}

			String name = entity.getName();
			subMonitor.subTask(taskName + " for " + name + " (" + i + " of " + size + ")");
			subMonitor.newChild(1);
			try {
				generator.transform(entity);
				generator.print(entity);
			} catch (Exception e) {
				NeosynCore.log(e);
			}
			i++;
		}

		subMonitor.done();

		// for full builds (following a clean) we copy support files to the output folder
		if (kind == FULL_BUILD) {
			generator.copyLibraries();
		}
	}

}
