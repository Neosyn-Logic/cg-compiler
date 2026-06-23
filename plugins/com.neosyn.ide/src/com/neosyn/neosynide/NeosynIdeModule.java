/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide;

import org.eclipse.core.runtime.Platform;
import org.eclipse.xtend2.lib.StringConcatenation;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.IExporter;
import com.neosyn.core.IFileWriter;
import com.neosyn.neosynide.internal.EclipseFileWriter;
import com.neosyn.neosynide.internal.NativeFileWriter;
import com.neosyn.neosynide.internal.exporters.DiamondExporter;
import com.neosyn.neosynide.internal.exporters.QuartusExporter;
import com.neosyn.neosynide.internal.exporters.VerilatorExporter;
import com.neosyn.neosynide.internal.exporters.VivadoExporter;
import com.neosyn.neosynide.internal.exporters.VsimExporter;
import com.neosyn.neosynide.internal.exporters.YosysExporter;
import com.neosyn.neosynide.internal.generators.verilog.VerilogCodeGenerator;

/**
 * This class defines the module for Neosyn IDE.
 * 

 *
 */
public class NeosynIdeModule extends AbstractModule {

	public static final String JAVA = "Java";

	public static final String VERILOG = "Verilog";

	public static final String VHDL = "VHDL";

	public NeosynIdeModule() {
		// set line separator to \n
		String oldLineSeparator = System.setProperty("line.separator", "\n");

		// load StringConcatenation class, which uses the line.separator property to initialize
		// DEFAULT_LINE_DELIMITER
		// Force class loading by accessing the field (avoid System.out which corrupts LSP protocol)
		@SuppressWarnings("unused")
		String delimiter = StringConcatenation.DEFAULT_LINE_DELIMITER;

		// restore line separator
		System.setProperty("line.separator", oldLineSeparator);
	}

	@Override
	protected void configure() {
		// file writers
		if (Platform.isRunning()) {
			bind(IFileWriter.class).to(EclipseFileWriter.class);
		} else {
			bind(IFileWriter.class).to(NativeFileWriter.class);
		}

		// exporters - Commercial tools
		bind(IExporter.class).annotatedWith(Names.named("Altera")).to(QuartusExporter.class);
		bind(IExporter.class).annotatedWith(Names.named("Lattice")).to(DiamondExporter.class);
		bind(IExporter.class).annotatedWith(Names.named("Xilinx")).to(VivadoExporter.class);
		bind(IExporter.class).annotatedWith(Names.named("Modelsim")).to(VsimExporter.class);

		// exporters - Open-source tools
		bind(IExporter.class).annotatedWith(Names.named("Yosys")).to(YosysExporter.class);
		bind(IExporter.class).annotatedWith(Names.named("Verilator")).to(VerilatorExporter.class);

		// Verilog code generator (the open-source backend)
		bind(ICodeGenerator.class).annotatedWith(Names.named(VERILOG))
				.to(VerilogCodeGenerator.class);
	}

}
