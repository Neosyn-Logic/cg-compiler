/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.core.IFileWriter;

/**
 * Test-specific Guice module that provides minimal bindings needed for tests.
 * This module doesn't depend on Eclipse Platform runtime components.
 */
public class TestNeosynModule extends AbstractModule {

	public static final String VERILOG = "Verilog";

	@Override
	protected void configure() {
		// Bind file writer to test implementation
		bind(IFileWriter.class).to(TestFileWriter.class);

		// Bind the open-source Verilog generator (the only backend in this repo).
		try {
			Class<?> verilogGen = Class.forName("com.neosyn.neosynide.internal.generators.verilog.VerilogCodeGenerator");
			bind(ICodeGenerator.class).annotatedWith(Names.named(VERILOG)).to(verilogGen.asSubclass(ICodeGenerator.class));
		} catch (ClassNotFoundException e) {
			// Generator not available in test environment
		}
	}

	/**
	 * Simple file writer implementation for tests.
	 */
	public static class TestFileWriter implements IFileWriter {
		private java.nio.file.Path outputFolder;

		public TestFileWriter() {
			try {
				this.outputFolder = java.nio.file.Files.createTempDirectory("neosyn-test");
			} catch (java.io.IOException e) {
				this.outputFolder = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "neosyn-test");
			}
		}

		@Override
		public boolean exists(String fileName) {
			return java.nio.file.Files.exists(outputFolder.resolve(fileName));
		}

		@Override
		public String getAbsolutePath(String fileName) {
			return outputFolder.resolve(fileName).toAbsolutePath().toString().replace('\\', '/');
		}

		@Override
		public void remove(String fileName) {
			try {
				java.nio.file.Files.deleteIfExists(outputFolder.resolve(fileName));
			} catch (java.io.IOException e) {
				// Ignore
			}
		}

		@Override
		public void setOutputFolder(String folder) {
			this.outputFolder = java.nio.file.Path.of(folder);
			try {
				java.nio.file.Files.createDirectories(this.outputFolder);
			} catch (java.io.IOException e) {
				// Ignore
			}
		}

		@Override
		public void write(String fileName, CharSequence contents) {
			try {
				java.nio.file.Path file = outputFolder.resolve(fileName);
				java.nio.file.Files.createDirectories(file.getParent());
				java.nio.file.Files.writeString(file, contents);
			} catch (java.io.IOException e) {
				throw new RuntimeException("Failed to write test file: " + fileName, e);
			}
		}

		@Override
		public void write(String fileName, java.io.InputStream source) {
			try {
				java.nio.file.Path file = outputFolder.resolve(fileName);
				java.nio.file.Files.createDirectories(file.getParent());
				java.nio.file.Files.copy(source, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.io.IOException e) {
				throw new RuntimeException("Failed to write test file: " + fileName, e);
			}
		}
	}
}
