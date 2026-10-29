/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.services;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.neosyn.core.NeosynCore;

/**
 * The Typer's Eclipse-workspace problem marker, isolated here so the Typer itself carries no
 * reference to org.eclipse.core.resources -- absent from the standalone jar, where calling it threw
 * NoClassDefFoundError out of the validator (TRAPS T130). Only reached for platform: resources.
 */
final class WorkspaceMarkers {

	private WorkspaceMarkers() {
	}

	static void portProblem(String entityName, String portName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		try {
			root.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO);
			IMarker marker = root.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.MESSAGE, "There is a problem with " + entityName + ". The port "
					+ portName + " is unconnected, undifined or unknown");
		} catch (CoreException e) {
			NeosynCore.log(e);
		}
	}
}
