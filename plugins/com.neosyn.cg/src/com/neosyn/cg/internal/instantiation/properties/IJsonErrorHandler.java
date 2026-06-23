/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import com.google.gson.JsonElement;

/**
 * This interface defines a method to report an error with a given JSON element.
 * 

 *
 */
public interface IJsonErrorHandler {

	/**
	 * Adds an error message caused by the given JSON element.
	 * 
	 * @param element
	 *            the JSON element
	 * @param message
	 *            a message
	 */
	void addError(JsonElement element, String message);

}
