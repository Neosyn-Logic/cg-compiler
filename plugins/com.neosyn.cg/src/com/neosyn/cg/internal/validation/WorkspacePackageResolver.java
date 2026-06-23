/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.internal.validation;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;

import com.neosyn.core.layout.ITreeElement;
import com.neosyn.core.layout.ProjectLayout;

/**
 * Isolates the Eclipse-workspace ({@code org.eclipse.core.resources}) lookups
 * used by the legacy {@code platform:/} package check in
 * {@link StructuralValidator#checkPackage}.
 *
 * <p>Kept in a separate class on purpose: the VS Code-only standalone language
 * server jar does <b>not</b> bundle {@code org.eclipse.core.resources} (Eclipse
 * RCP was retired in Session 111). If {@code StructuralValidator} referenced the
 * Eclipse Platform types directly, the whole {@code CgValidator} composite would
 * fail to instantiate with {@link NoClassDefFoundError} the moment Xtext tries
 * to collect its {@code @Check} methods — silently disabling <em>every</em>
 * declarative validator on both the CLI ({@code generate}/{@code simulate}) and
 * the standalone LSP. By confining the platform references here, this class is
 * only ever class-loaded when a {@code platform:/} URI is actually validated,
 * which never happens in the shipped product (file: URIs only). The compiler
 * build still links it against the full target platform.
 */
public final class WorkspacePackageResolver {

	private WorkspacePackageResolver() {
	}

	/**
	 * Resolve the package name the workspace folder layout expects for a module
	 * stored at the given {@code platform:/} URI.
	 *
	 * @param platformUri a {@code platform:/} resource URI
	 * @return the expected package name, or {@code null} if it cannot be
	 *         determined (resource not found, or the containing folder is not a
	 *         package)
	 */
	public static String expectedPackage(URI platformUri) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IResource resource = workspace.getRoot().findMember(platformUri.toPlatformString(true));
		if (resource == null) {
			return null;
		}
		ITreeElement element = ProjectLayout.getTreeElement(resource.getParent());
		if (element != null && element.isPackage()) {
			return element.getName();
		}
		return null;
	}
}
