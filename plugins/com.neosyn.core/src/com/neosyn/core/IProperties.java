/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import com.google.gson.JsonPrimitive;

/**
 * This interface defines useful well-known constants.
 * 

 * 
 */
public interface IProperties {

	JsonPrimitive ACTIVE_HIGH = new JsonPrimitive("high");

	JsonPrimitive ACTIVE_LOW = new JsonPrimitive("low");

	JsonPrimitive DEFAULT_CLOCK = new JsonPrimitive("clock");

	/**
	 * implementation: builtin
	 */
	JsonPrimitive IMPL_BUILTIN = new JsonPrimitive("builtin");

	/**
	 * implementation: external
	 */
	JsonPrimitive IMPL_EXTERNAL = new JsonPrimitive("external");

	/**
	 * active: high, low
	 */
	String PROP_ACTIVE = "active";

	/**
	 * clocks
	 */
	String PROP_CLOCKS = "clocks";

	/**
	 * comments: an object whose keys are lines and values are comments at those lines
	 */
	String PROP_COMMENTS = "comments";

	/**
	 * copyright: copyright statement (before package declaration)
	 */
	String PROP_COPYRIGHT = "copyright";

	/**
	 * dependencies: a list of strings, where each string is either a path or class name
	 */
	String PROP_DEPENDENCIES = "dependencies";

	/**
	 * domains: an association between ports and clocks, or clocks and ports
	 */
	String PROP_DOMAINS = "domains";

	/**
	 * for synthetic entites, means there are no test values
	 */
	String PROP_EMPTY = "empty";

	/**
	 * in implementation, specifies the file in which the external entity is implemented
	 */
	String PROP_FILE = "file";

	/**
	 * implementation
	 */
	String PROP_IMPLEMENTATION = "implementation";

	/**
	 * imports object
	 */
	String PROP_IMPORTS = "imports";

	/**
	 * javadoc: documentation of current task/network
	 */
	String PROP_JAVADOC = "javadoc";

	/**
	 * name. Applies to: reset.
	 */
	String PROP_NAME = "name";

	/**
	 * reset signals.
	 */
	String PROP_RESETS = "resets";

	/**
	 * when true, means that the entity was created for a testbench.
	 */
	String PROP_SYNTHETIC = "synthetic";

	/**
	 * test property
	 */
	String PROP_TEST = "test";

	/**
	 * type: synchronous, asynchronous, combinational
	 */
	String PROP_TYPE = "type";

	/**
	 * reset type: asynchronous
	 */
	JsonPrimitive RESET_ASYNCHRONOUS = new JsonPrimitive("asynchronous");

	/**
	 * reset type: synchronous
	 */
	JsonPrimitive RESET_SYNCHRONOUS = new JsonPrimitive("synchronous");

}
