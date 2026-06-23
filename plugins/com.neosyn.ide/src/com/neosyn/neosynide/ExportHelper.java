/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide;

import static com.neosyn.models.util.SwitchUtil.CASCADE;
import static com.neosyn.models.util.SwitchUtil.DONE;
import static org.eclipse.emf.ecore.util.EcoreUtil.getRootContainer;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.IFileWriter;
import com.neosyn.core.NeosynCore;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.dpn.util.DpnSwitch;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines a path computer.
 * 

 * 
 */
public class ExportHelper {

	/**
	 * This class computes an order for compilation of entities.
	 * 
	
	 * 
	 */
	private static class NetworkVisitor extends DpnSwitch<Void> {

		private final Set<Entity> entities;

		private IProject project;

		private final Map<EObject, IProject> projectMap;

		public NetworkVisitor(IProject project, Map<EObject, IProject> projectMap) {
			entities = new LinkedHashSet<Entity>();
			this.project = project;
			this.projectMap = projectMap;
		}

		@Override
		public Void caseDPN(DPN network) {
			for (Instance instance : network.getInstances()) {
				Entity entity = instance.getEntity();
				if (entity instanceof DPN) {
					DPN subNetwork = (DPN) entity;

					// update project for this network and its children
					IProject oldProject = project;
					project = subNetwork.getFile().getProject();
					doSwitch(subNetwork);
					project = oldProject;
				} else {
					doSwitch(entity);
				}
			}

			return CASCADE;
		}

		@Override
		public Void caseEntity(Entity entity) {
			entities.add(entity);
			projectMap.put(entity, project);
			return DONE;
		}

		public final Set<Entity> getEntities() {
			return entities;
		}

	}

	/**
	 * Returns the list of projects that are in the build path of this project (includes this
	 * project).
	 * 
	 * @param project
	 *            a project
	 * @return list of projects
	 */
	public static Collection<IProject> getBuildPath(IProject project) {
		Set<IProject> projects = new LinkedHashSet<>();
		getBuildPath(projects, project);
		return projects;
	}

	private static void getBuildPath(Set<IProject> projects, IProject project) {
		try {
			for (IProject required : project.getReferencedProjects()) {
				getBuildPath(projects, required);
			}
		} catch (CoreException e) {
			NeosynCore.log(e);
		}

		projects.add(project);
	}

	/**
	 * date formatted as a String
	 */
	private final String date;

	private final Entity entity;

	private final IFolder folder;

	private final ICodeGenerator generator;

	private List<String> includes;

	private final String model;

	private final IProject project;

	private final Map<EObject, IProject> projectMap;

	private List<String> unitPaths;

	public ExportHelper(Entity entity, String path, String model) {
		this.entity = entity;
		this.project = entity.getFile().getProject();
		this.model = model;

		projectMap = new HashMap<>();
		folder = project.getFolder(path);
		generator = NeosynCore.getGenerator(project);

		// format date
		DateFormat format = new SimpleDateFormat("HH:mm:ss MMMM dd, yyyy", Locale.US);
		date = format.format(new Date());
	}

	private void addIncludes(List<Unit> units) {
		Set<String> paths = new LinkedHashSet<>();

		for (Unit unit : units) {
			IProject project = projectMap.get(unit);
			IContainer cter = project.getFile(generator.computePath(unit)).getParent();
			IPath path = CoreUtil.getRelative(folder, cter);
			paths.add(path.toString());
		}

		this.includes = new ArrayList<>(paths);
	}

	private void addPaths(List<String> paths, Collection<? extends Entity> entities) {
		for (Entity entity : entities) {
			addPaths(paths, entity);
		}
	}

	private void addPaths(List<String> paths, Entity entity) {
		IProject project = projectMap.get(entity);
		Iterable<String> names = CoreUtil.getFileList(entity);
		if (CoreUtil.isExternal(entity)) {
			// for external entities, names are paths relative to current file
			for (String fileName : names) {
				// resolve dependencies and file locations relative to entity's path
				// note if fileName is absolute, it will remain absolute
				Path entityPath = Paths.get(computePath(project, entity.getName()));
				Path path = entityPath.getParent().resolve(Paths.get(fileName));
				paths.add(path.normalize().toString().replace('\\', '/'));
			}
		} else {
			// for normal and built-in entities, names are qualified names of entities
			for (String name : names) {
				paths.add(computePath(project, name));
			}
		}
	}

	/**
	 * Returns a path that is relative to the current folder from the given qualified name belonging
	 * to the given project.
	 * 
	 * @param project
	 *            a project
	 * @param name
	 *            qualified name of an entity
	 * @return file name relative to the current folder
	 */
	private String computePath(IProject project, String name) {
		String path = generator.computePath(IrUtil.getFile(name));
		IFile target = project.getFile(path);
		return CoreUtil.getRelative(folder, target).toString();
	}

	/**
	 * Computes the paths of built-in entities, units, actors, networks (in the proper order) to be
	 * compiled.
	 * 
	 */
	public List<String> computePathList() {
		List<String> paths = new ArrayList<>();

		// first add root dependencies
		final String language = generator.getName();
		for (String root : generator.getLibraries()) {
			paths.add(computePath(project, root));
		}

		// collect all entities
		NetworkVisitor visitor = new NetworkVisitor(project, projectMap);
		visitor.doSwitch(entity);

		// find units
		List<Unit> units = findUnits(visitor.getEntities());

		// headers in Verilog must not be compiled/part of a project
		// because they are only valid when included by tasks/networks
		if ("Verilog".equals(language)) {
			addIncludes(units);
			unitPaths = new ArrayList<>();
			addPaths(unitPaths, units);
		} else {
			addPaths(paths, units);
		}

		// adds entities
		addPaths(paths, visitor.getEntities());

		return paths;
	}

	public String computePathTb() {
		return generator.computePathTb(entity);
	}

	private List<Unit> findUnits(Collection<Entity> entities) {
		List<Unit> units = new ArrayList<>();
		for (Entity entity : entities) {
			Map<EObject, Collection<Setting>> map = EcoreUtil.ExternalCrossReferencer.find(entity);
			for (Collection<Setting> settings : map.values()) {
				for (Setting setting : settings) {
					Object object = setting.get(true);
					if (object instanceof EObject) {
						EObject cter = getRootContainer((EObject) object);
						if (cter instanceof Unit) {
							Unit unit = (Unit) cter;
							if (!units.contains(unit)) {
								units.add(unit);

								IProject project = unit.getFile().getProject();
								projectMap.put(unit, project);
							}
						}
					}
				}
			}
		}

		return units;
	}

	public String getDate() {
		return date;
	}

	/**
	 * Returns the include path for this project's build path as a list of strings.
	 * <code>computePathList</code> must have been called first.
	 * 
	 * @return a list of strings
	 */
	public List<String> getIncludePath() {
		return includes;
	}

	public String getLanguage() {
		return generator.getName();
	}

	public String getModel() {
		return model;
	}

	/**
	 * Returns the list of paths of units. Only meaningful for Verilog.
	 * 
	 * @return a list of paths
	 */
	public List<String> getUnitPaths() {
		return unitPaths == null ? Collections.emptyList() : unitPaths;
	}

	public void preservingWrite(String fileName, CharSequence sequence) {
		IFileWriter writer = generator.getFileWriter();
		if (!writer.exists(fileName)) {
			writer.write(fileName, sequence);
		}
	}

	public void write(String fileName, CharSequence sequence) {
		generator.getFileWriter().write(fileName, sequence);
	}

	public void write(String fileName, InputStream source) {
		generator.getFileWriter().write(fileName, source);
	}

}
