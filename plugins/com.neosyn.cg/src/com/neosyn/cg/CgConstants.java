/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg;

import com.google.gson.JsonPrimitive;

/**
 * This interface defines constants.
 * 

 * 
 */
public interface CgConstants {

	/**
	 * value of the direction attribute to indicate an input port
	 */
	String DIR_IN = "in";

	/**
	 * value of the direction attribute to indicate an output port
	 */
	String DIR_OUT = "out";

	/**
	 * name of the 'loop' special function.
	 */
	String NAME_LOOP = "loop";

	String NAME_LOOP_DEPRECATED = "run";

	/**
	 * name of the 'setup' special function.
	 */
	String NAME_SETUP = "setup";

	String NAME_SETUP_DEPRECATED = "init";

	String NAME_SIZEOF = "sizeof";

	String PROP_AVAILABLE = "available";

	String PROP_LENGTH = "length";

	String PROP_READ = "read";

	String PROP_READY = "ready";

	String PROP_TYPE = "type";

	JsonPrimitive TYPE_COMBINATIONAL = new JsonPrimitive("combinational");

	/**
	 * value of the type attribute to indicate a "reads" connection
	 */
	String TYPE_READS = "reads";

	/**
	 * value of the type attribute to indicate a "writes" connection
	 */
	String TYPE_WRITES = "writes";

}
