/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.IFileWriter;
import com.neosyn.neosynide.internal.NativeFileWriter;
import com.neosyn.neosynide.internal.generators.verilog.VerilogCodeGenerator;

/**
 * Guice module that provides HDL generator bindings for standalone LSP mode.
 *
 * This module is used instead of NeosynIdeModule when running the language
 * server outside of Eclipse. It provides the same generator bindings but
 * uses NativeFileWriter instead of EclipseFileWriter.
 */
public class StandaloneGeneratorModule extends AbstractModule {

    public static final String VERILOG = "Verilog";
    public static final String VHDL = "VHDL";
    public static final String JAVA = "Java";

    @Override
    protected void configure() {
        // File writer for standalone mode (no Eclipse workspace)
        bind(IFileWriter.class).to(NativeFileWriter.class);

        // Verilog code generator (the open-source backend)
        bind(ICodeGenerator.class).annotatedWith(Names.named(VERILOG))
                .to(VerilogCodeGenerator.class);
    }
}
