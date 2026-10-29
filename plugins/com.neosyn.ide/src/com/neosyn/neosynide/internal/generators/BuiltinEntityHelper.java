/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.neosyn.core.IPathResolver;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Type;
import com.neosyn.models.util.BuiltinPortTypeResolver;

/**
 * Helper class for handling built-in entities during HDL generation.
 *
 * Built-in entities are pre-defined components in the std.* packages:
 * - std.mem.*  : Memory components (SinglePortRAM, DualPortRAM, etc.)
 * - std.fifo.* : FIFO components (SynchronousFIFO, AsynchronousFIFO)
 * - std.lib.*  : Library components (SynchronizerMux, etc.)
 *
 * These entities may have null or proxy EMF references when the source
 * project doesn't have direct access to the built-in definitions.
 * This helper resolves them from classpath and copies HDL files to output.
 */
public class BuiltinEntityHelper {

	/**
	 * Static cache of resolved port types.
	 * Key format: "entityName:portName" (e.g., "test.SimpleMemTest_simpleTester:ram_q")
	 * This cache is populated during DPN processing and used when printing actors.
	 */
	private static final Map<String, Type> resolvedPortTypeCache = new HashMap<>();

	/** Tracks which built-in entities were used (for reporting/debugging) */
	private final Set<String> builtinEntitiesUsed = new HashSet<>();

	/** Path resolver for copying HDL files */
	private final IPathResolver pathResolver;

	/**
	 * Creates a helper with a path resolver for file copying.
	 *
	 * @param pathResolver the path resolver (may be null if no file copying needed)
	 */
	public BuiltinEntityHelper(IPathResolver pathResolver) {
		this.pathResolver = pathResolver;
	}

	/**
	 * Clears the tracking set. Call at the start of each DPN generation.
	 */
	public void clear() {
		builtinEntitiesUsed.clear();
	}

	/**
	 * Caches a resolved port type for later use when printing actors.
	 *
	 * @param entityName the full entity name (e.g., "test.SimpleMemTest_simpleTester")
	 * @param portName the port name (e.g., "ram_q")
	 * @param type the resolved type
	 */
	public static void cacheResolvedPortType(String entityName, String portName, Type type) {
		if (entityName != null && portName != null && type != null) {
			String key = entityName + ":" + portName;
			resolvedPortTypeCache.put(key, type);
		}
	}

	/**
	 * Gets a cached resolved port type.
	 *
	 * @param entityName the full entity name
	 * @param portName the port name
	 * @return the resolved type, or null if not cached
	 */
	public static Type getCachedPortType(String entityName, String portName) {
		if (entityName == null || portName == null) {
			return null;
		}
		String key = entityName + ":" + portName;
		return resolvedPortTypeCache.get(key);
	}

	/**
	 * Clears all cached port types.
	 * Call at the start of a new generation session.
	 */
	public static void clearCache() {
		resolvedPortTypeCache.clear();
	}

	/**
	 * Returns the set of built-in entity types used since last clear().
	 */
	public Set<String> getBuiltinEntitiesUsed() {
		return builtinEntitiesUsed;
	}

	/**
	 * Checks if a type name is a built-in entity type.
	 * Delegates to BuiltinPortTypeResolver (single source of truth).
	 */
	public static boolean isBuiltinType(String typeName) {
		return BuiltinPortTypeResolver.isBuiltinType(typeName);
	}

	/**
	 * Gets the entityType property from an instance.
	 * Delegates to BuiltinPortTypeResolver (single source of truth).
	 *
	 * @param instance the instance
	 * @return the entity type string, or null if not present
	 */
	public static String getEntityType(Instance instance) {
		return BuiltinPortTypeResolver.getEntityTypeFromInstance(instance);
	}

	/**
	 * Extracts the simple name (last segment) from a fully qualified type name.
	 * Delegates to BuiltinPortTypeResolver (single source of truth).
	 *
	 * @param typeName e.g., "std.mem.SinglePortRAM"
	 * @return e.g., "SinglePortRAM"
	 */
	public static String getSimpleName(String typeName) {
		return BuiltinPortTypeResolver.getSimpleName(typeName);
	}

	/**
	 * Gets the entity for an instance, loading from classpath if necessary.
	 * Also triggers HDL file copying for built-in entities.
	 *
	 * @param instance the instance to resolve
	 * @return the resolved entity
	 * @throws IllegalStateException if entity cannot be resolved
	 */
	public Entity getEntity(Instance instance) {
		Entity entity = instance.getEntity();

		// Return if entity is valid
		if (isValidEntity(entity)) {
			return entity;
		}

		// Try to resolve from entityType property
		String entityType = getEntityType(instance);
		if (entityType != null) {
			if (isBuiltinType(entityType)) {
				handleBuiltinEntity(entityType);
				return loadEntityFromClasspath(instance, entityType);
			}

			// For user-defined entities, search the resource set by name
			Entity found = findEntityInResourceSet(instance, entityType);
			if (found != null) {
				return found;
			}
		}

		throw new IllegalStateException(
			"Instance '" + instance.getName() + "' has null entity and no entityType in properties");
	}

	/**
	 * Finds a user-defined entity in the same resource set by matching entityType name.
	 * Used when DPN instances reference other entities that were loaded as separate .ir files.
	 */
	private Entity findEntityInResourceSet(Instance instance, String entityType) {
		org.eclipse.emf.ecore.resource.Resource resource = instance.eResource();
		if (resource == null) {
			return null;
		}
		org.eclipse.emf.ecore.resource.ResourceSet resourceSet = resource.getResourceSet();
		if (resourceSet == null) {
			return null;
		}

		for (org.eclipse.emf.ecore.resource.Resource res : resourceSet.getResources()) {
			if (!res.getContents().isEmpty()) {
				org.eclipse.emf.ecore.EObject obj = res.getContents().get(0);
				if (obj instanceof Entity) {
					Entity ent = (Entity) obj;
					String simpleName = ent.getSimpleName();
					String fullName = ent.getName();
					if (entityType.equals(simpleName) || entityType.equals(fullName)) {
						return ent;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gets the module name for instantiation.
	 * For built-in entities with null/proxy refs, uses entityType.
	 *
	 * @param instance the instance
	 * @return the module/entity name for HDL instantiation
	 * @throws IllegalStateException if name cannot be determined
	 */
	public String getModuleName(Instance instance) {
		Entity entity = instance.getEntity();

		// Return simpleName if entity is valid
		if (entity != null && !entity.eIsProxy() && entity.getSimpleName() != null) {
			return entity.getSimpleName();
		}

		// Fall back to entityType
		String entityType = getEntityType(instance);
		if (entityType != null) {
			handleBuiltinEntity(entityType);
			return getSimpleName(entityType);
		}

		throw new IllegalStateException(
			"Cannot determine module name for instance: " + instance.getName());
	}

	/**
	 * Checks if an entity reference is valid (non-null, not proxy, has name).
	 */
	private boolean isValidEntity(Entity entity) {
		return entity != null && !entity.eIsProxy() && entity.getName() != null;
	}

	/**
	 * Handles a built-in entity: tracks it and copies HDL file.
	 */
	private void handleBuiltinEntity(String entityType) {
		if (isBuiltinType(entityType)) {
			builtinEntitiesUsed.add(entityType);
			if (pathResolver != null) {
				pathResolver.copyBuiltinEntityFile(entityType);
			}
		}
	}

	/**
	 * Loads an entity from classpath using EntityLoader.
	 */
	private Entity loadEntityFromClasspath(Instance instance, String entityType) {
		// Delegate to EntityLoader for actual loading
		return com.neosyn.neosynide.internal.generators.common.EntityLoader
			.getEntityForInstance(instance);
	}

	/**
	 * Gets the resolved port type for a built-in entity port.
	 * Delegates to BuiltinPortTypeResolver (single source of truth).
	 *
	 * @param instance the instance (with arguments like size, width)
	 * @param port the port whose type needs resolving
	 * @return a Type with resolved width, or the original port type if not built-in
	 */
	public Type getResolvedPortType(Instance instance, Port port) {
		return BuiltinPortTypeResolver.resolvePortType(instance, port);
	}

	// ---------------------------------------------------------------- instantiation
	// A built-in instance carries UNRESOLVED ports: the IR references them as EMF
	// proxies whose name is null, because the design never had the built-in's own IR
	// in scope. Anything that prints an instantiation therefore has to go back to the
	// classpath IR to recover the names -- and the generic values, because the IR
	// Arguments are proxies too and the real values live in the instance properties.
	//
	// This lives HERE, not in a backend, because it is a property of the IR and not of
	// any output language. It was written for Verilog in S149 and the VHDL backend
	// never got it, which is why every RAM/FIFO/synchronizer network emitted port maps
	// with EMPTY formals (`=> readerWriter_ram_address`) and would not analyse at all.

	private static final Pattern PORT_FRAGMENT = Pattern.compile("@(inputs|outputs)\\.(\\d+)");

	/** RAM/FIFO built-ins take an address width derived from `size`, never passed. */
	private static final Set<String> SIZED_BUILTINS = Set.of("SinglePortRAM", "DualPortRAM",
			"PseudoDualPortRAM", "SynchronousFIFO", "AsynchronousFIFO");

	/**
	 * Resolves a proxy port of a built-in entity to the real port, which carries the
	 * name and interface. Returns {@code null} when {@code port} is not such a proxy,
	 * so callers can keep the port they already have.
	 */
	public static Port resolveBuiltinPort(Port port) {
		if (port == null || !port.eIsProxy()) {
			return null;
		}
		URI proxyUri = ((InternalEObject) port).eProxyURI();
		if (proxyUri == null || proxyUri.fragment() == null) {
			return null;
		}
		Matcher matcher = PORT_FRAGMENT.matcher(proxyUri.fragment());
		if (!matcher.find()) {
			return null;
		}
		String entityType = resolveEntityTypeFromUri(proxyUri.toString());
		if (entityType == null) {
			return null;
		}
		Entity entity = BuiltinPortTypeResolver.loadBuiltinEntity(entityType);
		if (entity == null) {
			return null;
		}
		java.util.List<Port> ports = "inputs".equals(matcher.group(1)) ? entity.getInputs()
				: entity.getOutputs();
		int index = Integer.parseInt(matcher.group(2));
		return index < ports.size() ? ports.get(index) : null;
	}

	/**
	 * Entity type name from a classpath proxy URI, e.g.
	 * {@code classpath:/builtin-ir/std/mem/SinglePortRAM.ir#//@outputs.0} ->
	 * {@code std.mem.SinglePortRAM}.
	 */
	public static String resolveEntityTypeFromUri(String uri) {
		if (uri == null) {
			return null;
		}
		int idx = uri.indexOf("builtin-ir/");
		if (idx < 0) {
			return null;
		}
		String suffix = uri.substring(idx + "builtin-ir/".length());
		int irIdx = suffix.indexOf(".ir");
		return irIdx < 0 ? null : suffix.substring(0, irIdx).replace('/', '.');
	}

	/**
	 * The generic/parameter values for a built-in instance, in declaration order.
	 * <p>
	 * Read from the instance PROPERTIES, not from its IR Arguments: those hold proxy
	 * variables with null names, which is exactly how both backends ended up emitting
	 * a nameless formal. Every scalar numeric/boolean property is a parameter (size,
	 * width, stages, writeShiftMode, ...); clocks, resets and implementation are
	 * objects and drop out of the primitive test, and entityType is a string.
	 * <p>
	 * Callers format the values themselves, since a boolean is {@code 1}/{@code 0} in
	 * Verilog and {@code true}/{@code false} in VHDL.
	 *
	 * @return an ordered name -> value map, empty when there is nothing to pass
	 */
	public static Map<String, JsonPrimitive> getBuiltinParameters(Instance instance) {
		Map<String, JsonPrimitive> params = new LinkedHashMap<>();
		JsonObject props = instance.getProperties();
		if (props == null) {
			return params;
		}
		for (String key : props.keySet()) {
			JsonElement value = props.get(key);
			if (value.isJsonPrimitive()) {
				JsonPrimitive prim = value.getAsJsonPrimitive();
				if (prim.isNumber() || prim.isBoolean()) {
					params.put(key, prim);
				}
			}
		}
		String simpleName = getSimpleName(getEntityType(instance));
		if (SIZED_BUILTINS.contains(simpleName) && props.has("size") && !props.has("depth")) {
			int depth = BuiltinPortTypeResolver.ceilLog2(props.get("size").getAsInt());
			params.put("depth", new JsonPrimitive(depth));
		}
		return params;
	}

	/**
	 * The endpoints driven by {@code instance}'s output port named {@code portName},
	 * matched BY NAME rather than by object identity.
	 * <p>
	 * The usual {@code network.getOutgoing(new Endpoint(instance, port))} lookup
	 * compares port objects, and for a built-in the connection's source port is a
	 * proxy while the port being asked about came from the classpath IR -- two
	 * different objects for the same port, so the lookup finds nothing and the output
	 * is mapped to {@code open}. That is worse than a syntax error: the file analyses
	 * and elaborates, and the consumer silently reads an undriven signal.
	 */
	public static List<Endpoint> findOutgoingByPortName(Instance instance, String portName) {
		List<Endpoint> endpoints = new ArrayList<>();
		if (portName == null) {
			return endpoints;
		}
		for (Object edge : instance.getOutgoing()) {
			Connection connection = (Connection) edge;
			Port sourcePort = connection.getSourcePort();
			if (sourcePort == null) {
				continue;
			}
			String sourceName = sourcePort.getName();
			if (sourceName == null) {
				Port resolved = resolveBuiltinPort(sourcePort);
				sourceName = resolved == null ? null : resolved.getName();
			}
			if (portName.equals(sourceName)) {
				endpoints.add(connection.getTargetEndpoint());
			}
		}
		return endpoints;
	}
}
