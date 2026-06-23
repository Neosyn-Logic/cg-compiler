/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import static com.neosyn.models.ir.util.IrUtil.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This class defines an abstract properties checker that is subclassed by concrete implementations
 * to check entities and instances respectively.
 * 

 * 
 */
public abstract class PropertiesChecker {

	/**
	 * {clock: "name"} accepted as synonym for {clocks: ["name"]}
	 */
	protected static final String ABBR_CLOCK = "clock";

	/**
	 * {reset: {...}} accepted as synonym for {resets: [{...}]}
	 */
	protected static final String ABBR_RESET = "reset";

	protected final IJsonErrorHandler handler;

	protected PropertiesChecker(IJsonErrorHandler handler) {
		this.handler = handler;
	}

	/**
	 * If the given properties define {prop: ...}, and no "props" property, transform to {props:
	 * [...]}.
	 * 
	 * @param properties
	 * @return the single value, or <code>null</code>
	 */
	protected final JsonElement applyShortcut(JsonObject properties, String single) {
		JsonElement value = properties.remove(single);
		if (value == null) {
			// no single value, return
			return null;
		}

		String plural = single + "s";
		if (properties.has(plural)) {
			// both "prop" and "props" exist, show error and ignore "prop"
			handler.addError(value,
					"\"" + single + "\" and \"" + plural + "\" are mutually exclusive");
			return null;
		}

		if (value.isJsonNull()) {
			// {prop: null} becomes {props: []}
			properties.add(plural, array());
		} else {
			// prop is valid, {prop: ...} becomes {props: [...]}
			properties.add(plural, array(value));
		}

		return value;
	}

	/**
	 * Check the "clocks" array.
	 * 
	 * @param clocks
	 *            an array of clock names
	 * @return <code>true</code> if it is valid
	 */
	protected boolean checkClockArray(JsonElement clocks) {
		boolean isValid;
		if (clocks.isJsonArray()) {
			isValid = true;
			JsonArray clocksArray = clocks.getAsJsonArray();
			for (JsonElement clock : clocksArray) {
				if (!clock.isJsonPrimitive() || !clock.getAsJsonPrimitive().isString()) {
					isValid = false;
					break;
				}
			}
		} else {
			isValid = false;
		}

		if (!isValid) {
			handler.addError(clocks, "\"clocks\" must be an array of clock names");
		}
		return isValid;
	}

}
