/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import static com.neosyn.cg.validation.IssueCodes.ERR_TEST_VECTORS;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a properties checker for an entity.
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
				handler.addError(test, "test must be an object or boolean", ERR_TEST_VECTORS);
			}
			return;
		}

		JsonObject objTest = test.getAsJsonObject();

		// "terminate" is a special key for simulation termination, not a port name
		if (objTest.has("terminate")) {
			return;
		}

		Set<String> ports = new HashSet<>();
		// Array ports whose size is not a literal: their elements are accepted by PREFIX,
		// because the size is only known once the dimension expression is evaluated and this
		// check runs before that (it takes the AST, not a translated entity).
		Set<String> openArrayPrefixes = new HashSet<>();
		for (Variable port : CgUtil.getPorts(instantiable.getPortDecls())) {
			String name = port.getName();

			// An ARRAY port carries no vectors of its own: it fans out to one port per
			// element, so the vectors belong to `W_0`..`W_N-1`.
			if (!port.getDimensions().isEmpty()) {
				int size = literalSize(port);
				if (size < 0) {
					openArrayPrefixes.add(name);
					continue;
				}
				for (int i = 0; i < size; i++) {
					String elementName = name + "_" + i;
					ports.add(elementName);
					if (!objTest.has(elementName)) {
						handler.addError(objTest, "missing test values for array port element \""
								+ elementName + "\"");
						continue;
					}
					if (!objTest.get(elementName).isJsonArray()) {
						handler.addError(objTest, "test values for array port element \""
								+ elementName + "\" must be an array", ERR_TEST_VECTORS);
					}
				}
				continue;
			}

			ports.add(name);

			if (!objTest.has(name)) {
				handler.addError(objTest, "missing test values for port \"" + name + "\"");
				continue;
			}

			JsonElement values = objTest.get(name);
			if (!values.isJsonArray()) {
				handler.addError(objTest, "test values for port \"" + name + "\" must be an array",
						ERR_TEST_VECTORS);
				continue;
			}
			checkFrames(objTest, name, values.getAsJsonArray(), CgUtil.isFramed(port));
		}

		for (Entry<String, JsonElement> entry : objTest.entrySet()) {
			String name = entry.getKey();
			if (!ports.contains(name) && !isElementOf(openArrayPrefixes, name)) {
				handler.addError(objTest, "unknown port \"" + name + "\"");
			}
		}
	}

	/**
	 * Frames in test vectors (TRAPS T58): on a `framed` port each inner list is one frame --
	 * `a: [[1, 2, 3], [4, 5]]` -- or a flat list is a single frame. Nested lists anywhere else,
	 * or a framed port mixing frames with plain values, are refused here, before TestGenerator.
	 */
	private void checkFrames(JsonObject objTest, String name, JsonArray values, boolean framed) {
		int frames = 0;
		for (JsonElement value : values) {
			if (value.isJsonArray()) {
				frames++;
				for (JsonElement inner : value.getAsJsonArray()) {
					if (inner.isJsonArray()) {
						handler.addError(objTest, "test values for port \"" + name
								+ "\": a frame is a list of values, not of lists", ERR_TEST_VECTORS);
						return;
					}
				}
			}
		}
		if (frames == 0) {
			return;
		}
		if (!framed) {
			handler.addError(objTest, "test values for port \"" + name + "\" contain lists, which mark"
					+ " frames; only a `framed` port has frames -- declare it `framed`, or give one flat list",
					ERR_TEST_VECTORS);
		} else if (frames != values.size()) {
			handler.addError(objTest, "test values for framed port \"" + name + "\" mix frames and single"
					+ " values: write every frame as a list, e.g. [[1, 2, 3], [4, 5]], or give one flat list"
					+ " (a single frame)", ERR_TEST_VECTORS);
		}
	}

	/**
	 * The declared size of an array port when the dimension is an integer literal, or -1
	 * when it is anything else (a `const`, an expression) -- this check runs on the AST,
	 * before dimension expressions can be evaluated.
	 */
	private int literalSize(Variable port) {
		com.neosyn.cg.cg.CgExpression dim = port.getDimensions().get(0);
		if (dim instanceof com.neosyn.cg.cg.ExpressionInteger) {
			java.math.BigInteger value = ((com.neosyn.cg.cg.ExpressionInteger) dim).getValue();
			if (value != null && value.signum() > 0) {
				return value.intValue();
			}
		}
		return -1;
	}

	/** True when {@code name} is `<prefix>_<digits>` for one of the given array ports. */
	private boolean isElementOf(Set<String> arrayPrefixes, String name) {
		int underscore = name.lastIndexOf('_');
		if (underscore <= 0 || underscore == name.length() - 1) {
			return false;
		}
		String prefix = name.substring(0, underscore);
		if (!arrayPrefixes.contains(prefix)) {
			return false;
		}
		for (int i = underscore + 1; i < name.length(); i++) {
			if (!Character.isDigit(name.charAt(i))) {
				return false;
			}
		}
		return true;
	}

}
