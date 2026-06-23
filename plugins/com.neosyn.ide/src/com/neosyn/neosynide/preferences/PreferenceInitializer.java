/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.preferences;

import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_MODELSIM_BIN;
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_MODELSIM_PATH;
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_VIVADO_BIN;
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_VIVADO_PATH;
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_QUARTUS_BIN;
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_QUARTUS_PATH;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import com.neosyn.core.util.CoreUtil;
import com.neosyn.neosynide.NeosynIde;

/**
 * This class defines the preference initializer for the com.neosyn.neosynide
 * plug-in.
 * 

 * 
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		// initialize modelsim path from PATH environment variable
		final String vsim = CoreUtil.getExecutable("vsim");
		String Modelsim = System.getenv("Path");
		if (Modelsim != null) {
		Optional<Path> path = Arrays.stream(System.getenv("Path").split(File.pathSeparator))
				.map(candidate -> Paths.get(candidate, vsim)).filter(Files::isExecutable).findFirst();
		if (path.isPresent()) {
			Path bin = path.get().getParent();
			IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(NeosynIde.PLUGIN_ID);
			prefs.put(PREF_MODELSIM_BIN, bin.toString());
			prefs.put(PREF_MODELSIM_PATH, bin.getParent().toString());
		}
		}

		// initialize Vivado path from XILINX_VIVADO environment variable
		String vivado = System.getenv("XILINX_VIVADO");
		if (vivado != null) {
			Path bin = Paths.get(vivado.trim(), "bin");
			if (Files.isDirectory(bin)) {
				IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(NeosynIde.PLUGIN_ID);
				prefs.put(PREF_VIVADO_BIN, bin.toString());
				prefs.put(PREF_VIVADO_PATH, bin.getParent().toString());
			}
		}

		// initialize Altera path from SOPC_KIT_NIOS2 environment variable
		String altera = System.getenv("SOPC_KIT_NIOS2");
		if (altera != null) {
			Path bin = Paths.get(altera.trim());
			if (Files.isDirectory(bin)) {
				IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(NeosynIde.PLUGIN_ID);
				prefs.put(PREF_QUARTUS_BIN, bin.toString());
				prefs.put(PREF_QUARTUS_PATH, bin.getParent().toString());
			}
		}
	}

}
