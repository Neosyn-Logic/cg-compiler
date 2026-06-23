/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.preferences;

/**
 * This interface defines global preference constants.
 * 

 *
 */
public interface IPreferenceConstants {

	/**
	 * path to folder that contains the "pnmainc" executable
	 */
	String PREF_DIAMOND_BIN = "diamond.bin";

	/**
	 * path to Diamond (contains "bin" folder)
	 */
	String PREF_DIAMOND_PATH = "diamond.path";

	/**
	 * license key
	 */
	String PREF_LICENSE_KEY = "key";

	/**
	 * login for license
	 */
	String PREF_LICENSE_LOGIN = "login";

	/**
	 * path of folder that contains the "modelsim" and "vsim" executables
	 */
	String PREF_MODELSIM_BIN = "modelsim.bin";

	/**
	 * path to Modelsim folder (contains "modelsim.ini" file)
	 */
	String PREF_MODELSIM_PATH = "modelsim.path";

	/**
	 * path to folder that contains the "vivado" script
	 */
	String PREF_VIVADO_BIN = "vivado.bin";

	/**
	 * path to Vivado (contains "bin" folder)
	 */
	String PREF_VIVADO_PATH = "vivado.path";
	
	/**
	 * path to folder that contains the "Nios II Command Shell" script
	 */
	String PREF_QUARTUS_BIN = "quartus.nios2eds";

	/**
	 * path to Quartus Prime (contains "nios2eds" and "quartus" folders)
	 */
	String PREF_QUARTUS_PATH = "quartus.path";
	
	/**
	 * path to the customer license
	 */
	String PREF_LICENSE_PATH = "license.path";

	/**
	 * path to folder that contains the "yosys" executable
	 */
	String PREF_YOSYS_BIN = "yosys.bin";

	/**
	 * path to folder that contains the "verilator" executable
	 */
	String PREF_VERILATOR_BIN = "verilator.bin";
}
