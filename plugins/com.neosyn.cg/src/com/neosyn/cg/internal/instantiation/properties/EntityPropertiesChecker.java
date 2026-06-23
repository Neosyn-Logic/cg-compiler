/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import static com.neosyn.core.IProperties.ACTIVE_HIGH;
import static com.neosyn.core.IProperties.ACTIVE_LOW;
import static com.neosyn.core.IProperties.DEFAULT_CLOCK;
import static com.neosyn.core.IProperties.PROP_ACTIVE;
import static com.neosyn.core.IProperties.PROP_CLOCKS;
import static com.neosyn.core.IProperties.PROP_NAME;
import static com.neosyn.core.IProperties.PROP_RESETS;
import static com.neosyn.core.IProperties.PROP_TEST;
import static com.neosyn.core.IProperties.RESET_ASYNCHRONOUS;
import static com.neosyn.core.IProperties.RESET_SYNCHRONOUS;
import static com.neosyn.cg.CgConstants.PROP_TYPE;
import static com.neosyn.cg.CgConstants.TYPE_COMBINATIONAL;
import static com.neosyn.models.ir.util.IrUtil.array;
import static com.neosyn.models.ir.util.IrUtil.obj;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a properties checker for an entity.
 * 

 * 
 */
public class EntityPropertiesChecker extends PropertiesChecker {

	public EntityPropertiesChecker(IJsonErrorHandler handler) {
		super(handler);
	}

	private void checkClocksDeclared(JsonObject properties) {
		applyShortcut(properties, ABBR_CLOCK);

		// if there are no clocks, or if they are not valid, use default clock
		JsonElement clocks = properties.get(PROP_CLOCKS);
		if (clocks == null || !checkClockArray(clocks)) {
			properties.add(PROP_CLOCKS, array(DEFAULT_CLOCK));
		}
	}

	/**
	 * Checks the given properties of the <code>instantiable</code>.
	 * 
	 * @param instantiable
	 *            a Cx instantiable
	 * @param properties
	 *            JSON properties
	 */
	public void checkProperties(Instantiable instantiable, JsonObject properties) {
		checkTest(instantiable, properties.get(PROP_TEST));

		JsonElement type = properties.get(PROP_TYPE);
		if (type != null) {
			if (type.isJsonPrimitive()) {
				JsonPrimitive entityType = properties.getAsJsonPrimitive(PROP_TYPE);
				if (TYPE_COMBINATIONAL.equals(entityType)) {
					// set an empty list of clocks and resets
					properties.add(PROP_CLOCKS, array());
					properties.add(PROP_RESETS, array());
					return;
				} else {
					handler.addError(entityType,
							"the only valid value of type is \"combinational\", ignored.");
				}
			} else {
				handler.addError(type, "type must be a string");
			}
		}

		checkClocksDeclared(properties);
		checkResetDeclared(properties);
	}

	/**
	 * Checks that the "resets" property is properly declared.
	 * 
	 * @param properties
	 *            properties
	 */
	private void checkResetDeclared(JsonObject properties) {
		applyShortcut(properties, ABBR_RESET);

		// if there are no resets, or if they are not valid, use default clock
		JsonElement resets = properties.get(PROP_RESETS);
		if (resets == null || !resets.isJsonArray()) {
			resets = array(obj());
			properties.add(PROP_RESETS, resets);
		}

		for (JsonElement reset : resets.getAsJsonArray()) {
			if (reset.isJsonObject()) {
				JsonObject resetObj = reset.getAsJsonObject();

				JsonPrimitive type = resetObj.getAsJsonPrimitive(PROP_TYPE);
				if (type == null
						|| !RESET_ASYNCHRONOUS.equals(type) && !RESET_SYNCHRONOUS.equals(type)) {
					// default is asynchronous reset
					resetObj.add(PROP_TYPE, RESET_ASYNCHRONOUS);
				}

				JsonPrimitive active = resetObj.getAsJsonPrimitive(PROP_ACTIVE);
				if (active == null || !ACTIVE_HIGH.equals(active) && !ACTIVE_LOW.equals(active)) {
					// default is active low reset
					resetObj.add(PROP_ACTIVE, ACTIVE_LOW);
				}

				if (!resetObj.has(PROP_NAME)) {
					// compute default name
					if (ACTIVE_LOW.equals(resetObj.getAsJsonPrimitive(PROP_ACTIVE))) {
						resetObj.addProperty(PROP_NAME, "reset_n");
					} else {
						resetObj.addProperty(PROP_NAME, "reset");
					}
				}
			}
		}
	}

	/**
	 * Checks the "test" property. We use an instantiable because the entity is not translated when
	 * we do this check (in the skeleton maker).
	 * 
	 * @param instantiable
	 *            instantiable
	 * @param test
	 *            test element
	 */
	private void checkTest(Instantiable instantiable, JsonElement test) {
		if (test == null) {
			return;
		}

		if (!test.isJsonObject()) {
			// test: true is valid for marking a network as a test entity
			// Only add error if it's not a boolean
			if (!test.isJsonPrimitive() || !test.getAsJsonPrimitive().isBoolean()) {
				handler.addError(test, "test must be an object or boolean");
			}
			return;
		}

		JsonObject objTest = test.getAsJsonObject();

		// "terminate" is a special key for simulation termination, not a port name
		if (objTest.has("terminate")) {
			return;
		}

		Set<String> ports = new HashSet<>();
		for (Variable port : CgUtil.getPorts(instantiable.getPortDecls())) {
			String name = port.getName();
			ports.add(name);

			if (!objTest.has(name)) {
				handler.addError(objTest, "missing test values for port \"" + name + "\"");
				continue;
			}

			JsonElement values = objTest.get(name);
			if (!values.isJsonArray()) {
				handler.addError(objTest, "test values for port \"" + name + "\" must be an array");
				continue;
			}
		}

		for (Entry<String, JsonElement> entry : objTest.entrySet()) {
			String name = entry.getKey();
			if (!ports.contains(name)) {
				handler.addError(objTest, "unknown port \"" + name + "\"");
			}
		}
	}

}
