/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import static com.neosyn.core.IProperties.IMPL_BUILTIN;
import static com.neosyn.core.IProperties.IMPL_EXTERNAL;
import static com.neosyn.core.IProperties.PROP_TYPE;
import static com.neosyn.models.ir.IrFactory.eINSTANCE;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.Argument;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.Var;
import com.neosyn.models.util.BuiltinPortTypeResolver;
import com.neosyn.core.util.DebugLogger;
import static com.neosyn.core.util.DebugLogger.Category.SPECIALIZER;

/**
 * This class specializes an entity based on the properties of an instance.
 * 

 *
 */
public class Specializer {

	private static final String ENTITY_TYPE = "entityType";

	private IJsonErrorHandler errorHandler;

	public Specializer(IJsonErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	private void addArguments(Entity entity, Instance instance) {
		JsonObject properties = instance.getProperties();
		DebugLogger.log(SPECIALIZER, "addArguments for instance: " + instance.getName());
		DebugLogger.log(SPECIALIZER, "  Properties: " + properties);
		DebugLogger.log(SPECIALIZER, "  Entity variables: " + entity.getVariables().size());
		for (Var variable : entity.getVariables()) {
			DebugLogger.log(SPECIALIZER, "  Variable: " + variable.getName() + " assignable=" + variable.isAssignable());
			if (!variable.isAssignable()) {
				String name = variable.getName();
				JsonElement json = properties.get(name);
				DebugLogger.log(SPECIALIZER, "    JSON for " + name + ": " + json);
				Expression value = json == null ? variable.getInitialValue() : transformJson(json);
				DebugLogger.log(SPECIALIZER, "    Value: " + value);
				if (value == null) {
					DebugLogger.log(SPECIALIZER, "    ERROR: null value for " + name);
					errorHandler.addError(json == null ? properties : json,
							"Instantiation: invalid value for constant '" + name + "'");
				} else {
					Argument argument = DpnFactory.eINSTANCE.createArgument(variable, value);
					instance.getArguments().add(argument);
					DebugLogger.log(SPECIALIZER, "    Added argument: " + name);
				}
			}
		}
		DebugLogger.log(SPECIALIZER, "  Total arguments added: " + instance.getArguments().size());
	}

	/**
	 * Returns an IR expression from the given JSON element.
	 * 
	 * @param json
	 *            a JSON element (should be a primitive)
	 * @return an expression, or <code>null</code>
	 */
	public Expression transformJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			JsonPrimitive primitive = json.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				return eINSTANCE.createExprBool(primitive.getAsBoolean());
			} else if (primitive.isNumber()) {
				return eINSTANCE.createExprInt(primitive.getAsBigInteger());
			} else if (primitive.isString()) {
				return eINSTANCE.createExprString(primitive.getAsString());
			}
		}
		return null;
	}

	public void visitArguments(Instance instance) {
		DebugLogger.log(SPECIALIZER, "visitArguments for: " + instance.getName());
		Entity entity = instance.getEntity();
		DebugLogger.log(SPECIALIZER, "  instance.getEntity(): " + (entity != null ? entity.getName() : "null"));

		// If entity is null, try to load from classpath using entityType property
		// BUT only for actual built-in types (std.mem.*, std.fifo.*, std.lib.*)
		if (entity == null) {
			JsonObject props = instance.getProperties();
			DebugLogger.log(SPECIALIZER, "  Properties: " + props);
			if (props != null && props.has(ENTITY_TYPE)) {
				String typeName = props.get(ENTITY_TYPE).getAsString();
				DebugLogger.log(SPECIALIZER, "  entityType: " + typeName);
				// Only load from builtin-ir for actual built-in types
				// Project entities should be resolved through normal EMF resolution
				if (BuiltinPortTypeResolver.isBuiltinType(typeName)) {
					entity = BuiltinPortTypeResolver.loadBuiltinEntity(typeName);
				} else {
					DebugLogger.log(SPECIALIZER, "  Not a built-in type, skipping builtin-ir load");
				}
			}
		}

		if (entity == null) {
			DebugLogger.log(SPECIALIZER, "  Entity still null, returning");
			return;
		}

		JsonObject impl = CoreUtil.getImplementation(entity);
		DebugLogger.log(SPECIALIZER, "  Implementation: " + impl);
		if (impl != null) {
			JsonElement type = impl.get(PROP_TYPE);
			DebugLogger.log(SPECIALIZER, "  Implementation type: " + type);
			if (IMPL_BUILTIN.equals(type) || IMPL_EXTERNAL.equals(type)) {
				addArguments(entity, instance);
			} else {
				DebugLogger.log(SPECIALIZER, "  Type is not builtin or external");
			}
		} else {
			DebugLogger.log(SPECIALIZER, "  No implementation found");
		}
	}

}
