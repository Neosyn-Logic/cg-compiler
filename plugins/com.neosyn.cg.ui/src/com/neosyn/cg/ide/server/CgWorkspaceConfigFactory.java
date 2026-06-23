/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.ide.server;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.xtext.ide.server.MultiRootWorkspaceConfigFactory;
import org.eclipse.xtext.workspace.FileProjectConfig;
import org.eclipse.xtext.workspace.IProjectConfig;
import org.eclipse.xtext.workspace.IWorkspaceConfig;

/**
 * Workspace config factory that makes the EDITOR's Xtext linker resolve
 * cross-project imports declared via {@code cg.deps} — e.g. a SoC project that
 * {@code import}s {@code com.neosyn.intel8088.CPU} from a sibling project
 * directory. Without this the editor reports
 * "Couldn't resolve reference to Named 'com.neosyn.intel8088.CPU'" even though
 * the CLI build path (simulate/generate) already resolves it.
 *
 * <p>Each project's transitive {@code cg.deps} roots
 * ({@link ServerUtils#resolveSourceRoots(String)}) are added as extra SOURCE
 * FOLDERS of that SAME project (not as separate projects). One project means one
 * index container, so the dependency's {@code .cg} files are mutually visible to
 * the importing project's resources — matching what the flat CLI resource set
 * already does. Adding them as separate projects would NOT work: Xtext scopes
 * cross-resource visibility per project container, and {@code FileProjectConfig}
 * has no inter-project dependency declaration.</p>
 *
 * <p>Failure to augment leaves the stock (workspace-folders-only) config intact,
 * so this can never make base indexing worse than stock Xtext.</p>
 */
public class CgWorkspaceConfigFactory extends MultiRootWorkspaceConfigFactory {

	@Override
	public IWorkspaceConfig getWorkspaceConfig(List<WorkspaceFolder> workspaceFolders) {
		IWorkspaceConfig config = super.getWorkspaceConfig(workspaceFolders);
		try {
			for (IProjectConfig pc : config.getProjects()) {
				if (pc instanceof FileProjectConfig) {
					addDepSourceFolders((FileProjectConfig) pc);
				}
			}
		} catch (Exception | LinkageError e) {
			System.err.println("[CgLanguageServer] cg.deps editor indexing disabled (" + e + ")");
		}
		return config;
	}

	/**
	 * Adds each transitive {@code cg.deps} dependency root of the given project as
	 * an extra absolute-path source folder of that project.
	 */
	private void addDepSourceFolders(FileProjectConfig project) {
		URI base = project.getPath();
		if (base == null || !base.isFile()) {
			return;
		}
		String root = base.toFileString();
		// resolveSourceRoots returns [primary, dep1, dep2, ...] (primary first);
		// the primary is already the project's own "." source folder.
		List<String> roots = ServerUtils.resolveSourceRoots(root);
		for (int i = 1; i < roots.size(); i++) {
			String dep = roots.get(i);
			// An absolute path resolves to itself against the project base, so the
			// source folder points at the sibling dependency project directory.
			project.addSourceFolder(dep);
			System.err.println("[CgLanguageServer] cg.deps: indexing dependency root "
					+ dep + " into project " + project.getName());
		}
	}
}
