/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.common;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.util.BuiltinPortTypeResolver;

/**
 * Bytecode-specific entity loading utilities.
 *
 * <p>This class provides bytecode generation-specific methods for handling
 * built-in entities. Core built-in entity detection and loading is delegated
 * to {@link BuiltinPortTypeResolver} (single source of truth).</p>
 *
 * <p>Bytecode-specific methods include:</p>
 * <ul>
 *   <li>{@link #getBuiltinJavaClassName(String)} - Convert entity type to JVM class name</li>
 *   <li>{@link #getClassNameForInstance(Instance)} - Get class name for bytecode generation</li>
 *   <li>{@link #getTypeNameForInstance(Instance)} - Get JVM type descriptor</li>
 * </ul>
 */
public class EntityLoader {

	/**
	 * Clears the built-in entity cache. Call this at the start of a new compilation session.
	 */
	public static void clearCache() {
		BuiltinPortTypeResolver.clearCache();
	}

	/**
	 * Checks if an instance references a built-in entity (std.* package).
	 *
	 * @param instance the instance to check
	 * @return true if this is a built-in entity instance
	 */
	public static boolean isBuiltinEntity(Instance instance) {
		String entityType = BuiltinPortTypeResolver.getEntityTypeFromInstance(instance);
		if (entityType != null && BuiltinPortTypeResolver.isBuiltinType(entityType)) {
			return true;
		}

		Entity entity = instance.getEntity();
		if (entity != null && entity.getName() != null && BuiltinPortTypeResolver.isBuiltinType(entity.getName())) {
			return true;
		}

		return false;
	}

	/**
	 * Checks if a type name is a built-in entity type.
	 * Delegates to {@link BuiltinPortTypeResolver#isBuiltinType(String)}.
	 *
	 * @param typeName the fully qualified type name
	 * @return true if this is a built-in type
	 */
	public static boolean isBuiltinEntityType(String typeName) {
		return BuiltinPortTypeResolver.isBuiltinType(typeName);
	}

	/**
	 * Gets the entity type from instance properties.
	 * Delegates to {@link BuiltinPortTypeResolver#getEntityTypeFromInstance(Instance)}.
	 *
	 * @param instance the instance
	 * @return the entity type string, or null if not present
	 */
	public static String getEntityTypeFromInstance(Instance instance) {
		return BuiltinPortTypeResolver.getEntityTypeFromInstance(instance);
	}

	/**
	 * Gets the Java class name for a built-in entity type.
	 * Built-in entities use pre-compiled Java classes from the runtime.
	 *
	 * @param entityType the entity type (e.g., "std.mem.SinglePortRAM")
	 * @return the Java class name (e.g., "std/mem/SinglePortRAM")
	 */
	public static String getBuiltinJavaClassName(String entityType) {
		if (entityType == null) {
			return null;
		}
		return entityType.replace('.', '/');
	}

	/**
	 * Gets the entity for a given instance. If the instance's entity is null or a proxy,
	 * attempts to load it from the classpath using the entityType property.
	 *
	 * @param instance the instance to get the entity for
	 * @return the entity, never null
	 * @throws IllegalArgumentException if the entity cannot be loaded
	 */
	public static Entity getEntityForInstance(Instance instance) {
		Entity entity = BuiltinPortTypeResolver.getEntityForInstance(instance);
		if (entity != null) {
			return entity;
		}

		// Throw with detailed message for bytecode generation
		String entityType = BuiltinPortTypeResolver.getEntityTypeFromInstance(instance);
		if (entityType != null) {
			throw new IllegalArgumentException("Failed to load built-in entity '" + entityType
					+ "' for instance '" + instance.getName() + "'");
		}
		throw new IllegalArgumentException("Instance '" + instance.getName()
				+ "' has null entity and no entityType in properties");
	}

	/**
	 * Loads a built-in entity from the classpath.
	 * Delegates to {@link BuiltinPortTypeResolver#loadBuiltinEntity(String)}.
	 *
	 * @param typeName fully qualified entity name (e.g., "std.mem.SinglePortRAM")
	 * @return the loaded entity, or null if not found
	 */
	public static Entity loadBuiltinEntity(String typeName) {
		return BuiltinPortTypeResolver.loadBuiltinEntity(typeName);
	}

	/**
	 * Returns the class name for an instance, handling built-in entities.
	 *
	 * @param instance the instance
	 * @return the fully qualified class name
	 */
	public static String getClassNameForInstance(Instance instance) {
		Entity entity = instance.getEntity();

		// For built-in entities, use the entityType from properties
		if (entity == null || entity.eIsProxy()) {
			com.google.gson.JsonObject props = instance.getProperties();
			if (props != null && props.has("entityType")) {
				return props.get("entityType").getAsString().replace('.', '/');
			}
		}

		// Fall back to entity name
		if (entity != null && entity.getName() != null) {
			return entity.getName().replace('.', '/');
		}

		throw new IllegalArgumentException("Cannot determine class name for instance: " + instance.getName());
	}

	/**
	 * Returns the type name for an instance in JVM format.
	 *
	 * @param instance the instance
	 * @return the type name (e.g., "Lstd/mem/SinglePortRAM;")
	 */
	public static String getTypeNameForInstance(Instance instance) {
		return "L" + getClassNameForInstance(instance) + ";";
	}
}
