/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import com.neosyn.models.dpn.Argument;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.impl.IrResourceFactoryImpl;

/**
 * SINGLE SOURCE OF TRUTH for resolving built-in entity port types.
 *
 * <h2>Background</h2>
 * Built-in entities like {@code std.mem.SinglePortRAM} have parameterized port widths:
 * <ul>
 *   <li>{@code address} port width = ceil(log2(size)) bits</li>
 *   <li>{@code data} and {@code q} port widths = width bits</li>
 * </ul>
 *
 * When the IR is loaded from classpath, the port type has {@code size=-1} as a
 * placeholder. This class resolves the actual width based on instance arguments.
 *
 * <h2>Usage</h2>
 * <pre>
 * // During instantiation:
 * Type resolvedType = BuiltinPortTypeResolver.resolvePortType(
 *     "std.mem.SinglePortRAM", "address", sizeArg, widthArg);
 *
 * // Or with instance arguments directly:
 * Type resolvedType = BuiltinPortTypeResolver.resolvePortType(
 *     instance, port);
 * </pre>
 *
 * <h2>Supported Built-in Entities</h2>
 * <ul>
 *   <li>std.mem.SinglePortRAM - address, data, q ports</li>
 *   <li>std.mem.DualPortRAM - addressA/B, dataA/B, qA/B ports</li>
 *   <li>std.mem.PseudoDualPortRAM - same as DualPortRAM</li>
 *   <li>std.fifo.SynchronousFIFO - din, dout, full, empty, etc.</li>
 *   <li>std.fifo.AsynchronousFIFO - same as SynchronousFIFO</li>
 * </ul>
 */
public final class BuiltinPortTypeResolver {

	/** Known built-in entity prefixes */
	private static final String[] BUILTIN_PREFIXES = {
		"std.mem.",
		"std.fifo.",
		"std.lib."
	};

	/** Thread-safe cache for loaded built-in entities */
	private static final Map<String, Entity> entityCache = new HashMap<>();

	// Private constructor - utility class
	private BuiltinPortTypeResolver() {
	}

	/**
	 * Checks if a type name is a built-in entity type.
	 *
	 * @param typeName the fully qualified type name (e.g., "std.mem.SinglePortRAM")
	 * @return true if this is a built-in entity type
	 */
	public static boolean isBuiltinType(String typeName) {
		if (typeName == null) {
			return false;
		}
		for (String prefix : BUILTIN_PREFIXES) {
			if (typeName.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extracts the simple name from a fully qualified type name.
	 *
	 * @param typeName e.g., "std.mem.SinglePortRAM"
	 * @return e.g., "SinglePortRAM"
	 */
	public static String getSimpleName(String typeName) {
		if (typeName == null) {
			return null;
		}
		int lastDot = typeName.lastIndexOf('.');
		return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
	}

	/**
	 * Resolves the port type for a built-in entity instance.
	 * Extracts size and width arguments from the instance and computes the actual port width.
	 *
	 * @param instance the built-in entity instance with arguments
	 * @param port the port whose type needs resolving
	 * @return a Type with resolved width, or the original port type if not resolvable
	 */
	public static Type resolvePortType(Instance instance, Port port) {
		if (port == null || port.getType() == null) {
			return null;
		}

		// Extract size and width arguments from instance
		int size = getArgumentValue(instance, "size", -1);
		int width = getArgumentValue(instance, "width", -1);

		// Get entity type from instance properties or entity name
		String entityType = getEntityTypeFromInstance(instance);

		return resolvePortType(entityType, port.getName(), port.getType(), size, width);
	}

	/**
	 * Resolves the port type given explicit parameters.
	 *
	 * @param entityType the entity type (e.g., "std.mem.SinglePortRAM")
	 * @param portName the port name (e.g., "address", "data", "q")
	 * @param originalType the original port type (may have size=-1)
	 * @param size the 'size' argument value (memory depth)
	 * @param width the 'width' argument value (data width in bits)
	 * @return a Type with resolved width, or the original type if not resolvable
	 */
	public static Type resolvePortType(String entityType, String portName, Type originalType, int size, int width) {
		// Only resolve if the type is a parameterized int (size=-1)
		if (!(originalType instanceof TypeInt)) {
			return originalType;
		}

		TypeInt typeInt = (TypeInt) originalType;

		// Calculate the actual port width implied by the instance's arguments.
		int resolvedWidth = resolvePortWidth(entityType, portName, size, width);

		// A known width-parameterized port WITH concrete instance arguments wins
		// over any size already on the port. Some built-ins (SynchronizerMux/FF)
		// bake their DEFAULT const width (e.g. 16) onto the port instead of the
		// -1 placeholder that RAMs/FIFOs use; without this, an instance's
		// `width: 3` was ignored and the connection wire came out 16 bits wide
		// (iverilog: "expects 3 bits, got 16 — padding 13 high bits").
		boolean haveArgs = width > 0 || size > 0;
		if (resolvedWidth > 0 && haveArgs) {
			TypeInt resolvedType = IrFactory.eINSTANCE.createTypeInt();
			resolvedType.setSize(resolvedWidth);
			resolvedType.setSigned(typeInt.isSigned());
			return resolvedType;
		}

		if (typeInt.getSize() != -1) {
			// Valid baked size and no usable args (e.g. instance relies on the
			// default width) — keep it.
			return originalType;
		}

		// size == -1 placeholder but nothing to resolve from: return as-is
		// (downstream guards handle the invalid width).
		return originalType;
	}

	/**
	 * Creates a new Port with resolved type.
	 * This is a convenience method that creates a copy of the port with the resolved type.
	 *
	 * @param instance the built-in entity instance
	 * @param port the port to resolve
	 * @return a new Port with resolved type, or the original port if not resolvable
	 */
	public static Port createResolvedPort(Instance instance, Port port) {
		Type resolvedType = resolvePortType(instance, port);

		// If type didn't change, return original
		if (resolvedType == port.getType()) {
			return port;
		}

		// Create a new port with resolved type
		Port resolvedPort = DpnFactory.eINSTANCE.createPort();
		resolvedPort.setName(port.getName());
		resolvedPort.setType(resolvedType);
		resolvedPort.setInterface(port.getInterface());
		return resolvedPort;
	}

	/**
	 * Resolves the port width for a specific built-in entity port.
	 *
	 * @param entityType the entity type (e.g., "std.mem.SinglePortRAM")
	 * @param portName the port name (e.g., "address", "data", "q")
	 * @param size the 'size' argument (memory depth)
	 * @param width the 'width' argument (data width in bits)
	 * @return the resolved width in bits, or 0 if unknown
	 */
	public static int resolvePortWidth(String entityType, String portName, int size, int width) {
		if (entityType == null || portName == null) {
			return 0;
		}

		String simpleName = getSimpleName(entityType);
		int depth = size > 0 ? ceilLog2(size) : 0;

		switch (simpleName) {
		case "SinglePortRAM":
		case "DualPortRAM":
		case "PseudoDualPortRAM":
			switch (portName) {
			case "address":
			case "addressA":
			case "addressB":
				return depth;
			case "data":
			case "dataA":
			case "dataB":
			case "q":
			case "qA":
			case "qB":
				return width;
			case "data_valid":
			case "write_enable":
			case "clock_enable":
				return 1;
			}
			break;

		case "SynchronousFIFO":
		case "AsynchronousFIFO":
			switch (portName) {
			case "din":
			case "dout":
				return width;
			case "full":
			case "empty":
			case "almost_full":
			case "almost_empty":
			case "read":
			case "write":
			case "valid":
				return 1;
			}
			break;

		case "SynchronizerMux":
		case "SynchronizerFF":
			switch (portName) {
			case "din":
			case "dout":
				return width > 0 ? width : 1;
			}
			break;

		case "MuxDDR":
		case "DemuxDDR":
			switch (portName) {
			case "din":
			case "dout":
			case "rising":
			case "falling":
				return width > 0 ? width : 1;
			}
			break;
		}

		return 0;
	}

	/**
	 * Gets an integer argument value from an instance.
	 *
	 * @param instance the instance
	 * @param argName the argument name ("size" or "width")
	 * @param defaultValue the default if not found
	 * @return the argument value or default
	 */
	public static int getArgumentValue(Instance instance, String argName, int defaultValue) {
		if (instance == null) {
			return defaultValue;
		}

		// Built-in instances carry their args as JSON properties
		// (e.g. `new std.lib.SynchronizerMux({width: 3})`), NOT as IR Arguments.
		// Check there first so a width/size override is actually honoured.
		com.google.gson.JsonObject props = instance.getProperties();
		if (props != null && props.has(argName)) {
			try {
				return props.get(argName).getAsInt();
			} catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
				// non-numeric property — fall through to the argument paths
			}
		}

		// First try using instance.getArgument() if available
		Argument arg = instance.getArgument(argName);
		if (arg != null && arg.getValue() instanceof ExprInt) {
			return ((ExprInt) arg.getValue()).getValue().intValue();
		}

		// Fall back to iterating over arguments
		for (Argument argument : instance.getArguments()) {
			Var variable = argument.getVariable();
			if (variable != null && argName.equals(variable.getName())) {
				if (argument.getValue() instanceof ExprInt) {
					return ((ExprInt) argument.getValue()).getValue().intValue();
				}
			}
		}

		return defaultValue;
	}

	/**
	 * Gets the entity type from an instance.
	 * First checks the 'entityType' property, then falls back to the entity name.
	 *
	 * @param instance the instance
	 * @return the entity type string, or null if not determinable
	 */
	public static String getEntityTypeFromInstance(Instance instance) {
		if (instance == null) {
			return null;
		}

		// Check properties first (for built-in entities)
		com.google.gson.JsonObject props = instance.getProperties();
		if (props != null && props.has("entityType")) {
			return props.get("entityType").getAsString();
		}

		// Fall back to entity name
		if (instance.getEntity() != null && !instance.getEntity().eIsProxy()) {
			return instance.getEntity().getName();
		}

		return null;
	}

	/**
	 * Computes ceiling of log base 2.
	 *
	 * @param n the value (typically memory depth)
	 * @return ceil(log2(n)), minimum 1
	 */
	public static int ceilLog2(int n) {
		if (n <= 1) {
			return 1;
		}
		return 32 - Integer.numberOfLeadingZeros(n - 1);
	}

	// =========================================================================
	// BUILT-IN ENTITY LOADING
	// Single source of truth for loading built-in entities from classpath
	// =========================================================================

	/**
	 * Loads a built-in entity from the classpath with caching.
	 * This is the SINGLE SOURCE OF TRUTH for loading built-in entities.
	 *
	 * <p>Built-in entities are shipped as pre-compiled IR files in the
	 * {@code builtin-ir/} directory inside the JAR.</p>
	 *
	 * @param typeName fully qualified entity name (e.g., "std.mem.SinglePortRAM")
	 * @return the loaded entity, or null if not found
	 */
	public static Entity loadBuiltinEntity(String typeName) {
		if (typeName == null || !isBuiltinType(typeName)) {
			return null;
		}

		// Check cache first (thread-safe read)
		synchronized (entityCache) {
			Entity cached = entityCache.get(typeName);
			if (cached != null) {
				return cached;
			}
		}

		// Load from classpath
		Entity entity = loadFromClasspath(typeName);

		// Cache the result (even null to avoid repeated load attempts)
		if (entity != null) {
			synchronized (entityCache) {
				entityCache.put(typeName, entity);
			}
		}

		return entity;
	}

	/**
	 * Gets an entity for an instance, loading from classpath if necessary.
	 * Returns the instance's entity if valid, otherwise loads from entityType property.
	 *
	 * @param instance the instance to get entity for
	 * @return the entity, or null if not resolvable
	 */
	public static Entity getEntityForInstance(Instance instance) {
		if (instance == null) {
			return null;
		}

		// Try the instance's entity first
		Entity entity = instance.getEntity();
		if (entity != null && !entity.eIsProxy() && entity.getName() != null) {
			return entity;
		}

		// Fall back to loading from entityType property
		String entityType = getEntityTypeFromInstance(instance);
		if (entityType != null && isBuiltinType(entityType)) {
			return loadBuiltinEntity(entityType);
		}

		return null;
	}

	/**
	 * Clears the entity cache. Call this when starting a new compilation session.
	 */
	public static void clearCache() {
		synchronized (entityCache) {
			entityCache.clear();
		}
	}

	/**
	 * Loads a built-in entity IR from classpath (no caching).
	 */
	private static Entity loadFromClasspath(String typeName) {
		// Convert entity name to classpath resource path
		// e.g., "std.mem.SinglePortRAM" -> "builtin-ir/std/mem/SinglePortRAM.ir"
		String resourcePath = "builtin-ir/" + typeName.replace('.', '/') + ".ir";

		InputStream is = BuiltinPortTypeResolver.class.getClassLoader().getResourceAsStream(resourcePath);
		if (is == null) {
			return null;
		}

		try {
			ResourceSet resourceSet = new ResourceSetImpl();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("ir", new IrResourceFactoryImpl());

			URI uri = URI.createURI("classpath:/" + resourcePath);
			Resource resource = resourceSet.createResource(uri);
			resource.load(is, null);

			if (!resource.getContents().isEmpty()) {
				Object obj = resource.getContents().get(0);
				if (obj instanceof Entity) {
					Entity entity = (Entity) obj;
					// Ensure properties is not null (IR files may not serialize empty properties)
					if (entity.getProperties() == null) {
						entity.setProperties(new com.google.gson.JsonObject());
					}
					return entity;
				}
			}
		} catch (IOException e) {
			// Silently fail - caller should handle null return
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				// Ignore close errors
			}
		}

		return null;
	}
}
