/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import static com.neosyn.core.ICoreConstants.FOLDER_TESTBENCH;
import static com.neosyn.core.ICoreConstants.SUFFIX_GEN;
import static com.neosyn.core.IProperties.IMPL_BUILTIN;
import static com.neosyn.core.IProperties.PROP_TYPE;
import static com.neosyn.models.ir.util.IrUtil.getFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.IFileWriter;
import com.neosyn.core.IPathResolver;
import com.neosyn.core.NeosynCore;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.neosynide.internal.generators.common.EntityLoader;

/**
 * This class defines an abstract generator that uses Xtend for templates.
 * 

 * 
 */
public abstract class AbstractGenerator implements ICodeGenerator, IPathResolver {

	@Inject
	protected IFileWriter writer;

	/**
	 * Returns the path to the given file name in the library.
	 * 
	 * @param fileName
	 *            a file name (without extension)
	 * @return <code>"/src/" + fileName</code>
	 */
	protected String computeLibPath(String fileName) {
		final String gen = getName().toLowerCase();
		return "/lib/" + gen + "/src/" + fileName + '.' + getFileExtension();
	}

	@Override
	public String computePath(Entity entity) {
		return computePath(getFile(entity.getName()), getFileExtension());
	}

	@Override
	public final String computePath(String name) {
		return computePath(name, getFileExtension());
	}

	/**
	 * Returns a path composed of this generator's name (possibly with a '-gen' suffix, depending on
	 * generators), the given file name and file extension.
	 * 
	 * @param fileName
	 *            name of a file
	 * @param fileExt
	 *            file extension
	 * @return a path
	 */
	protected String computePath(String fileName, String fileExt) {
		String name = getName().toLowerCase();
		return name + SUFFIX_GEN + '/' + fileName + '.' + fileExt;
	}

	@Override
	public String computePathTb(Entity entity) {
		String name = getFile(entity.getName()) + ".tb";
		return FOLDER_TESTBENCH + '/' + name + '.' + getFileExtension();
	}

	/**
	 * Copy the built-in entity and its dependencies to the target folder.
	 * 
	 * @param entity
	 *            an entity
	 */
	private void copyBuiltinEntity(Entity entity) {
		for (String name : CoreUtil.getFileList(entity)) {
			copyBuiltinFile(name, false);
		}
	}

	/**
	 * Copy the file that matches the given qualified name to the target folder.
	 *
	 * @param name
	 *            qualified name of an entity
	 */
	protected void copyBuiltinFile(String name, boolean force) {
		final String fileName = IrUtil.getFile(name);
		String path = computePath(fileName);
		if (force || !writer.exists(path)) {
			final String pathName = computeLibPath(fileName);
			InputStream is = null;
			try {
				// First try Eclipse platform URI (works in Eclipse RCP)
				is = NeosynCore.openStream(pathName);
			} catch (IOException e) {
				// Platform URI failed, try classpath (works in standalone LSP mode)
				is = getClass().getResourceAsStream(pathName);
				if (is == null) {
					debugLog("[copyBuiltinFile] Failed to load: " + pathName);
				}
			}
			if (is != null) {
				try {
					writer.write(path, is);
					debugLog("[copyBuiltinFile] Copied: " + pathName + " -> " + path);
				} finally {
					try { is.close(); } catch (IOException e) { /* ignore */ }
				}
			}
		}
	}

	@Override
	public void copyLibraries() {
		for (String library : getLibraries()) {
			copyBuiltinFile(library, true);
		}
	}

	/**
	 * Copies a built-in entity's HDL file to the output folder.
	 * Called by DpnPrinter when it encounters a built-in entity reference (std.mem.*, std.fifo.*, etc.)
	 *
	 * @param entityTypeName the fully qualified built-in entity name (e.g., "std.mem.SinglePortRAM")
	 */
	@Override
	public void copyBuiltinEntityFile(String entityTypeName) {
		if (entityTypeName != null) {
			copyBuiltinFile(entityTypeName, false);
			debugLog("[copyBuiltinEntityFile] Copied built-in entity: " + entityTypeName);
		}
	}

	protected final DPN createTestDpn(Entity entity) {
		DPN dpn = new TestGenerator().createDpn(entity);
		for (Instance instance : dpn.getInstances()) {
			if (instance.getEntity() != entity) {
				transform(instance.getEntity());
			}
		}
		transform(dpn);
		return dpn;
	}

	/**
	 * Prints the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	protected abstract void doPrint(Entity entity);

	/**
	 * Prints the testbench for the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	protected abstract void doPrintTestbench(Entity entity);

	@Override
	public void fullBuild(Entity entity) {
		copyLibraries();
		for (Entity ent : DpnFactory.eINSTANCE.collectEntities(entity)) {
			transform(ent);
			print(ent);
		}

		Set<Unit> units = new LinkedHashSet<>();
		ResourceSet set = entity.eResource().getResourceSet();
		for (Resource resource : set.getResources()) {
			Entity ent = (Entity) resource.getContents().get(0);
			if (ent instanceof Unit) {
				units.add((Unit) ent);
			}
		}

		for (Unit unit : units) {
			transform(unit);
			print(unit);
		}

		printTestbench(entity);
	}

	@Override
	public final IFileWriter getFileWriter() {
		return writer;
	}

	@Override
	public String getFullPath(Entity entity) {
		return writer.getAbsolutePath(computePath(entity));
	}

	@Override
	public Iterable<String> getLibraries() {
		// by default a generator has no libraries
		return ImmutableList.of();
	}

	/** Enable verbose bytecode debug logging. Set -Dneosyn.debug.bytecode=true to enable. */
	private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(
			System.getProperty("neosyn.debug.bytecode", "false"));

	private void debugLog(String msg) {
		if (!DEBUG_ENABLED) return;
		try {
			java.nio.file.Files.write(
				java.nio.file.Paths.get(System.getProperty("user.home"), "neosyn-bytecode-debug.log"),
				(java.time.LocalDateTime.now() + " " + msg + "\n").getBytes(),
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.APPEND
			);
		} catch (Exception e) { /* ignore */ }
	}

	@Override
	public void print(Entity entity) {
		debugLog("[AbstractGen] print() called for: " + entity.getName());
		JsonObject implementation = CoreUtil.getImplementation(entity);
		debugLog("[AbstractGen] implementation: " + implementation);
		if (implementation == null) {
			// default case: print entity
			if (entity instanceof DPN) {
				DPN dpn = (DPN) entity;
				debugLog("[AbstractGen] Entity is DPN with " + dpn.getInstances().size() + " instances");
				for (Instance instance : dpn.getInstances()) {
					Entity ent = instance.getEntity();
					debugLog("[AbstractGen]   Instance: " + instance.getName() + " -> entity: " + (ent != null ? ent.getName() : "NULL") + " isProxy: " + (ent != null && ent.eIsProxy()));
					if (ent == null || ent.eIsProxy()) {
						// Instance entity not resolved - this is normal for DPNs loaded
						// from individual .ir files where cross-references aren't linked.
						// The DpnPrinter can still generate wiring from the DPN's graph.
						String entityType = EntityLoader.getEntityTypeFromInstance(instance);
						if (entityType != null && EntityLoader.isBuiltinEntityType(entityType)) {
							debugLog("[AbstractGen] Instance '" + instance.getName() + "' is a built-in entity: " + entityType);
						} else {
							debugLog("[AbstractGen] Instance '" + instance.getName() + "' has unresolved entity: " + entityType + " (continuing anyway)");
						}
					}
				}
			}
			debugLog("[AbstractGen] Calling doPrint...");
			doPrint(entity);
		} else {
			JsonElement type = implementation.get(PROP_TYPE);
			if (IMPL_BUILTIN.equals(type)) {
				// if type is 'builtin' copy entity and dependencies to target folder
				debugLog("[AbstractGen] Copying builtin entity");
				copyBuiltinEntity(entity);
			}
		}
	}

	@Override
	public void printTestbench(Entity entity) {
		if (entity instanceof Unit) {
			return;
		}

		JsonObject implementation = CoreUtil.getImplementation(entity);
		if (implementation == null) {
			// only print testbench for entities without 'implementation' property
			doPrintTestbench(entity);
		}
	}

	@Override
	public void remove(String name) {
		writer.remove(computePath(IrUtil.getFile(name)));
	}

	@Override
	public final void setOutputFolder(String folder) {
		writer.setOutputFolder(folder);
	}

	protected boolean isEnabled() {
		return false;
	}

}
