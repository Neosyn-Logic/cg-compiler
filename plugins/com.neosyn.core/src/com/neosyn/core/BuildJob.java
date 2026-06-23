/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

/**
 * This class defines a build job.
 * 

 * 
 */
public class BuildJob extends Job {

	private final IProject project;

	public BuildJob(IProject project) {
		super("Build " + project.getName());
		this.project = project;
	}

	@Override
	public boolean belongsTo(Object family) {
		return ResourcesPlugin.FAMILY_MANUAL_BUILD == family;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}

		try {
			SubMonitor subMonitor = SubMonitor.convert(monitor, "Building " + project.getName(), 2);
			project.build(IncrementalProjectBuilder.CLEAN_BUILD, subMonitor);
		} catch (CoreException e) {
			return e.getStatus();
		} finally {
			monitor.done();
		}
		return Status.OK_STATUS;
	}

}
