/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import static com.neosyn.core.ICoreConstants.FILE_EXT_CX;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.AbstractScope;

import com.neosyn.cg.cg.Module;

/**
 * Provides scoping for built-in C-Ground (CG) components like memory, FIFOs, and synchronizers.
 *
 * <h2>Purpose</h2>
 * This scope allows user code to reference built-in entities like:
 * <ul>
 *   <li>{@code std.mem.SinglePortRAM} - Single-port RAM memory</li>
 *   <li>{@code std.mem.DualPortRAM} - Dual-port RAM memory</li>
 *   <li>{@code std.fifo.SynchronousFIFO} - Synchronous FIFO buffer</li>
 *   <li>{@code std.lib.SynchronizerFF} - Flip-flop synchronizer</li>
 *   <li>etc.</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * This class is used by {@link CgGlobalScopeProvider} as the parent scope for all
 * global scope lookups. When Xtext tries to resolve a cross-reference like
 * {@code new std.mem.SinglePortRAM(...)}, this scope is queried.
 *
 * <h2>Two Resolution Modes</h2>
 * <ol>
 *   <li><b>Eclipse IDE Mode (platform URIs)</b>: When running inside Eclipse,
 *       built-in entities are loaded from the plugin's model/ folder using
 *       {@code platform:/plugin/com.neosyn.cg/model/Mem.cg} URIs. This works
 *       because the OSGi runtime can resolve plugin resources.</li>
 *
 *   <li><b>Standalone LSP Mode (classpath fallback)</b>: When running as a standalone
 *       LSP server (e.g., in VS Code), platform URIs don't work. Instead, we load
 *       the .cg files from the JAR's classpath at {@code builtin-cg/Mem.cg} etc.
 *       The .cg files are parsed by Xtext to produce proper Cx model objects
 *       (Task/Network that extend Instantiable).</li>
 * </ol>
 *
 * <h2>Why .cg Files Instead of .ir Files?</h2>
 * Initially, we tried loading pre-compiled .ir files (IR = Intermediate Representation).
 * However, .ir files contain DPN model objects (Actor), while the Xtext grammar expects
 * Cx model objects (Task/Network extending Instantiable). Loading .cg files ensures
 * Xtext parses them into the correct model type that the linker expects.
 *
 * <h2>Caching</h2>
 * <ul>
 *   <li>{@link #builtinCache} - Caches resolved entities by qualified name</li>
 *   <li>{@link #cgResourceCache} - Caches loaded .cg resources to avoid re-parsing</li>
 * </ul>
 *
 * <h2>Adding New Built-in Entities</h2>
 * To add a new built-in entity:
 * <ol>
 *   <li>Create the .cg file in {@code plugins/com.neosyn.cg/model/}</li>
 *   <li>Add entries to {@link #uriMap}, {@link #cgPathMap}, and {@link #cgIndexMap} in the static block</li>
 *   <li>Update {@code releng/lsp-server/pom.xml} to include the new .cg file in builtin-cg/</li>
 *   <li>Rebuild the LSP server JAR</li>
 * </ol>
 *
 * @see CgGlobalScopeProvider - Uses this as parent scope
 * @see com.neosyn.cg.cg.Instantiable - The interface that Task/Network implement
 */
public class ComponentScope extends AbstractScope {

	// ==================== STATIC CONFIGURATION ====================

	/**
	 * Maps fully-qualified entity names to platform plugin URIs.
	 * Used in Eclipse IDE mode where platform:/ URIs work.
	 *
	 * Example: "std.mem.SinglePortRAM" -> platform:/plugin/com.neosyn.cg/model/Mem.cg#//@entities.0
	 */
	private static final Map<String, URI> uriMap = new HashMap<>();

	/**
	 * Maps fully-qualified entity names to classpath paths for .cg files.
	 * Used in standalone LSP mode as fallback when platform URIs fail.
	 *
	 * Example: "std.mem.SinglePortRAM" -> "builtin-cg/Mem.cg"
	 */
	private static final Map<String, String> cgPathMap = new HashMap<>();

	/**
	 * Maps fully-qualified entity names to their index within the Module's entities list.
	 * Each .cg file contains a Module with multiple entities (tasks/networks).
	 *
	 * Example: "std.mem.SinglePortRAM" -> 0 (first entity in Mem.cg)
	 *          "std.mem.DualPortRAM" -> 1 (second entity in Mem.cg)
	 */
	private static final Map<String, Integer> cgIndexMap = new HashMap<>();

	/**
	 * Cache for resolved built-in entities, keyed by qualified name.
	 * Avoids repeated lookups/parsing for the same entity.
	 */
	private static final Map<String, EObject> builtinCache = new HashMap<>();

	/**
	 * Cache for loaded .cg resources, keyed by classpath path.
	 * Multiple entities from the same .cg file share the same Resource.
	 */
	private static final Map<String, Resource> cgResourceCache = new HashMap<>();

	/*
	 * Static initializer: Register all built-in entities.
	 *
	 * IMPORTANT: The index (i) must match the order of entities in each .cg file!
	 * If you reorder entities in a .cg file, update the indices here.
	 */
	static {
		// ========== std.fifo package ==========
		// Source: plugins/com.neosyn.cg/model/Fifo.cg
		// Contains: SynchronousFIFO (index 0), AsynchronousFIFO (index 1)
		int i = 0;
		registerBuiltin("std.fifo.SynchronousFIFO", "Fifo", "builtin-cg/Fifo.cg", i++);
		registerBuiltin("std.fifo.AsynchronousFIFO", "Fifo", "builtin-cg/Fifo.cg", i++);

		// ========== std.lib package ==========
		// Source: plugins/com.neosyn.cg/model/Lib.cg
		// Contains: SynchronizerFF, SynchronizerMux, MuxDDR, DemuxDDR (indices 0-3)
		i = 0;
		registerBuiltin("std.lib.SynchronizerFF", "Lib", "builtin-cg/Lib.cg", i++);
		registerBuiltin("std.lib.SynchronizerMux", "Lib", "builtin-cg/Lib.cg", i++);
		registerBuiltin("std.lib.MuxDDR", "Lib", "builtin-cg/Lib.cg", i++);
		registerBuiltin("std.lib.DemuxDDR", "Lib", "builtin-cg/Lib.cg", i++);

		// ========== std.mem package ==========
		// Source: plugins/com.neosyn.cg/model/Mem.cg
		// Contains: SinglePortRAM, DualPortRAM, PseudoDualPortRAM (indices 0-2)
		i = 0;
		registerBuiltin("std.mem.SinglePortRAM", "Mem", "builtin-cg/Mem.cg", i++);
		registerBuiltin("std.mem.DualPortRAM", "Mem", "builtin-cg/Mem.cg", i++);
		registerBuiltin("std.mem.PseudoDualPortRAM", "Mem", "builtin-cg/Mem.cg", i++);
	}

	/**
	 * Helper to register a built-in entity in all three maps.
	 *
	 * @param qualifiedName Full entity name (e.g., "std.mem.SinglePortRAM")
	 * @param modelFileName Base name of .cg file without extension (e.g., "Mem")
	 * @param classpathPath Path in JAR for standalone mode (e.g., "builtin-cg/Mem.cg")
	 * @param entityIndex Index of entity in Module.getEntities() list
	 */
	private static void registerBuiltin(String qualifiedName, String modelFileName, String classpathPath, int entityIndex) {
		uriMap.put(qualifiedName, createPlatformURI(modelFileName, entityIndex));
		cgPathMap.put(qualifiedName, classpathPath);
		cgIndexMap.put(qualifiedName, entityIndex);
	}

	/**
	 * Creates a platform plugin URI for Eclipse IDE mode.
	 *
	 * @param modelFileName Base name of .cg file (e.g., "Mem")
	 * @param entityIndex Index of entity in Module.getEntities()
	 * @return URI like platform:/plugin/com.neosyn.cg/model/Mem.cg#//@entities.0
	 */
	private static URI createPlatformURI(String modelFileName, int entityIndex) {
		// FILE_EXT_CX is "cg" - the C-Ground file extension
		String pathName = "com.neosyn.cg/model/" + modelFileName + "." + FILE_EXT_CX;
		URI uri = URI.createPlatformPluginURI(pathName, true);
		// Fragment points to the entity at the specified index
		return uri.appendFragment("//@entities." + entityIndex);
	}

	// ==================== INSTANCE FIELDS ====================

	/** The resource set used to load resources */
	private ResourceSet resourceSet;

	// ==================== DEBUG LOGGING ====================

	/**
	 * Debug log file path. Set to user's home directory for easy access.
	 * Log file: ~/neosyn-scope-debug.log
	 */
	/** Enable verbose scope debug logging. Set -Dneosyn.debug.scope=true to enable. */
	private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(
			System.getProperty("neosyn.debug.scope", "false"));

	private static final String DEBUG_LOG = System.getProperty("user.home") + "/neosyn-scope-debug.log";

	private static void debugLog(String msg) {
		if (!DEBUG_ENABLED) return;
		try (PrintWriter pw = new PrintWriter(new FileWriter(DEBUG_LOG, true))) {
			pw.println("[" + java.time.LocalDateTime.now() + "] " + msg);
		} catch (IOException e) {
			// Silently ignore logging errors
		}
	}

	// ==================== CONSTRUCTOR ====================

	/**
	 * Creates a new ComponentScope.
	 *
	 * @param parent Parent scope (typically IScope.NULLSCOPE)
	 * @param resourceSet The resource set for loading resources
	 */
	public ComponentScope(IScope parent, ResourceSet resourceSet) {
		super(parent, false);
		this.resourceSet = resourceSet;
	}

	// ==================== SCOPE INTERFACE IMPLEMENTATION ====================

	/**
	 * Returns all local elements. We don't enumerate built-ins proactively,
	 * so this returns an empty set. Elements are resolved on-demand via
	 * {@link #getSingleElement(QualifiedName)}.
	 */
	@Override
	protected Iterable<IEObjectDescription> getAllLocalElements() {
		return Collections.emptySet();
	}

	/**
	 * Returns elements matching the given qualified name.
	 * Since built-in names are unique, this delegates to getSingleElement.
	 */
	@Override
	public Iterable<IEObjectDescription> getElements(QualifiedName name) {
		IEObjectDescription result = getSingleElement(name);
		if (result != null)
			return singleton(result);
		return emptySet();
	}

	/**
	 * Main entry point for resolving a built-in entity by qualified name.
	 *
	 * Resolution strategy:
	 * 1. Check cache first (fast path)
	 * 2. Try platform URI (Eclipse IDE mode)
	 * 3. Fall back to classpath loading (standalone LSP mode)
	 *
	 * @param name Qualified name like "std.mem.SinglePortRAM"
	 * @return Description wrapping the resolved entity, or null if not a built-in
	 */
	@Override
	public IEObjectDescription getSingleElement(QualifiedName name) {
		String nameStr = name.toString();
		debugLog("getSingleElement called for: " + nameStr);

		// ===== STEP 1: Check cache =====
		EObject cached = builtinCache.get(nameStr);
		if (cached != null) {
			debugLog("Found in cache: " + nameStr);
			return new EObjectDescription(name, cached, null);
		}

		// ===== STEP 2: Check if this is a known built-in =====
		URI uri = uriMap.get(nameStr);
		debugLog("URI lookup for " + nameStr + ": " + (uri != null ? uri.toString() : "NOT FOUND"));

		if (uri == null) {
			// Not a built-in entity - let other scopes handle it
			debugLog("Returning null for: " + nameStr);
			return null;
		}

		// ===== STEP 3: Try platform URI (Eclipse IDE mode) =====
		EObject eObject = null;
		try {
			debugLog("Trying platform URI: " + uri);
			eObject = resourceSet.getEObject(uri, true);
			debugLog("Platform URI result: " + (eObject != null ? eObject.getClass().getSimpleName() : "null"));
		} catch (Exception e) {
			// Platform URI failed - this is expected in standalone LSP mode
			// The platform: protocol isn't available outside Eclipse
			debugLog("Platform URI failed for " + nameStr + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
		}

		if (eObject != null) {
			// Platform URI worked (Eclipse IDE mode)
			builtinCache.put(nameStr, eObject);
			return new EObjectDescription(name, eObject, null);
		}

		// ===== STEP 4: Fall back to classpath loading (standalone LSP mode) =====
		debugLog("Trying classpath fallback for: " + nameStr);
		EObject fallbackEntity = loadFromClasspath(nameStr);
		if (fallbackEntity != null) {
			debugLog("Classpath fallback SUCCESS for: " + nameStr);
			builtinCache.put(nameStr, fallbackEntity);
			return new EObjectDescription(name, fallbackEntity, null);
		} else {
			debugLog("Classpath fallback FAILED for: " + nameStr);
		}

		debugLog("Returning null for: " + nameStr);
		return null;
	}

	// ==================== CLASSPATH LOADING (STANDALONE MODE) ====================

	/**
	 * Loads a built-in entity from .cg files bundled in the JAR's classpath.
	 * This is the fallback mechanism for standalone LSP mode.
	 *
	 * <h3>How it works:</h3>
	 * <ol>
	 *   <li>Look up the .cg file path and entity index from the maps</li>
	 *   <li>Load the .cg file from classpath (e.g., builtin-cg/Mem.cg)</li>
	 *   <li>Xtext parses the .cg file into a Module containing Task/Network entities</li>
	 *   <li>Extract the entity at the specified index</li>
	 * </ol>
	 *
	 * <h3>Why .cg files?</h3>
	 * We must load .cg files (not .ir files) because:
	 * <ul>
	 *   <li>.cg files are parsed by Xtext into Cx model objects (Task/Network)</li>
	 *   <li>.ir files contain DPN model objects (Actor) which have a different type</li>
	 *   <li>The grammar expects Instantiable (supertype of Task/Network), not Actor</li>
	 * </ul>
	 *
	 * @param entityName Fully qualified entity name (e.g., "std.mem.SinglePortRAM")
	 * @return The loaded entity (Task or Network), or null if loading fails
	 */
	private EObject loadFromClasspath(String entityName) {
		debugLog("loadFromClasspath called for: " + entityName);

		// Look up the .cg file path and entity index
		String cgPath = cgPathMap.get(entityName);
		Integer entityIndex = cgIndexMap.get(entityName);
		if (cgPath == null || entityIndex == null) {
			debugLog("No .cg path mapping for: " + entityName);
			return null;
		}
		debugLog(".cg path: " + cgPath + ", index: " + entityIndex);

		try {
			// Check if we already loaded and cached this .cg resource
			Resource resource = cgResourceCache.get(cgPath);

			if (resource == null) {
				// Need to load the .cg file from classpath
				resource = loadCgResourceFromClasspath(cgPath);
			} else {
				debugLog("Using cached resource for: " + cgPath);
			}

			// Extract the entity from the loaded Module
			return extractEntityFromResource(resource, entityIndex);

		} catch (Exception e) {
			debugLog("Failed to load built-in .cg: " + cgPath + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Loads a .cg resource from the JAR's classpath.
	 *
	 * @param cgPath Classpath path (e.g., "builtin-cg/Mem.cg")
	 * @return The loaded Resource, or null if not found
	 * @throws IOException If loading fails
	 */
	private Resource loadCgResourceFromClasspath(String cgPath) throws IOException {
		ClassLoader cl = getClass().getClassLoader();
		debugLog("ClassLoader: " + cl.getClass().getName());

		// Try to get input stream from classpath
		InputStream is = cl.getResourceAsStream(cgPath);
		if (is == null) {
			// Some classloaders need a leading slash
			debugLog("First attempt failed, trying with leading slash");
			is = cl.getResourceAsStream("/" + cgPath);
		}

		if (is == null) {
			debugLog("Built-in .cg not found in classpath: " + cgPath);
			return null;
		}

		debugLog("InputStream obtained successfully for: " + cgPath);
		try {
			// Create a synthetic URI with .cg extension
			// The extension is important: Xtext uses it to find the right parser
			URI syntheticUri = URI.createURI("classpath:/" + cgPath);
			debugLog("Synthetic URI: " + syntheticUri);

			// Check if already in resource set (shouldn't happen, but be safe)
			Resource resource = resourceSet.getResource(syntheticUri, false);
			if (resource != null) {
				return resource;
			}

			// Create new resource and load from input stream
			// Xtext will automatically use CgParser because of the .cg extension
			resource = resourceSet.createResource(syntheticUri);
			debugLog("Created resource: " + (resource != null ? resource.getClass().getSimpleName() : "null"));

			if (resource != null) {
				resource.load(is, null);
				debugLog("Resource loaded, contents size: " + resource.getContents().size());

				// Log any parse errors (useful for debugging)
				if (!resource.getErrors().isEmpty()) {
					debugLog("Resource has errors: " + resource.getErrors().size());
					for (Resource.Diagnostic d : resource.getErrors()) {
						debugLog("  Error: " + d.getMessage());
					}
				}

				// Cache for future use
				cgResourceCache.put(cgPath, resource);
			}

			return resource;
		} finally {
			is.close();
		}
	}

	/**
	 * Extracts an entity from a loaded .cg resource at the specified index.
	 *
	 * @param resource The loaded resource containing a Module
	 * @param entityIndex Index of the entity in Module.getEntities()
	 * @return The entity (Task or Network), or null if not found
	 */
	private EObject extractEntityFromResource(Resource resource, int entityIndex) {
		if (resource == null || resource.getContents().isEmpty()) {
			return null;
		}

		EObject root = resource.getContents().get(0);
		debugLog("Root object type: " + root.getClass().getSimpleName());

		// The root should be a Module (from the Cx grammar)
		if (root instanceof Module) {
			Module module = (Module) root;

			if (entityIndex < module.getEntities().size()) {
				EObject entity = module.getEntities().get(entityIndex);
				debugLog("Found entity at index " + entityIndex + ": " + entity.getClass().getSimpleName());
				return entity;
			} else {
				debugLog("Entity index " + entityIndex + " out of bounds (module has " +
				         module.getEntities().size() + " entities)");
			}
		} else {
			debugLog("Root is not a Module: " + root.getClass().getName());
		}

		return null;
	}
}
