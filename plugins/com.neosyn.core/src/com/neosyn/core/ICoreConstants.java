/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

/**
 * This interface defines useful well-known constants.
 * 

 * 
 */
public interface ICoreConstants {

	String FILE_EXT_CX = "cg";

	String FILE_EXT_IR = "ir";

	String FOLDER_CLASSES = "classes";

	/**
	 * name of the folder where IR files are generated
	 */
	String FOLDER_IR = ".ir";

	/**
	 * name of the folder where simulation files are generated
	 */
	String FOLDER_SIM = "sim";

	/**
	 * name of the "testbench" folder
	 */
	String FOLDER_TESTBENCH = "testbench";

	/**
	 * project property for current generator
	 */
	String PROP_GENERATOR = NeosynCore.PLUGIN_ID + ".generator";

	/**
	 * suffix of folders for generated files
	 */
	String SUFFIX_GEN = "-gen";

}
