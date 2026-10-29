/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static com.neosyn.cg.tests.LspTestSupport.requireLspJar;
import static com.neosyn.cg.tests.LspTestSupport.writeUtf8;
import static com.neosyn.cg.tests.LspTestSupport.deleteRecursive;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

/**
 * TRAPS T133: std.fifo.AsynchronousFIFO must generate HDL that builds. Its file instantiates helper
 * modules (the FIFO controllers, a RAM) that generate never wrote, so iverilog and GHDL failed on
 * every design using it while generate said "Success". Its built-in IR now lists them, and the
 * generator copies a built-in's dependencies with it. The simulator has no model of this FIFO, so
 * these tests build the HDL only.
 */
public class AsyncFifoBuildTests {

	private static final String DESIGN = "package p;\n"
			+ "network UsesAsyncFifo {\n"
			+ "    properties { clocks: [\"wclk\", \"rclk\"] }\n"
			+ "    out sync ready u8 dout;\n"
			+ "    prod = new task {\n"
			+ "        properties { clock: \"wclk\" }\n"
			+ "        out sync ready u8 v; u8 n;\n"
			+ "        void loop() { v.write(n); n++; }\n"
			+ "    };\n"
			+ "    fifo = new std.fifo.AsynchronousFIFO({ clocks: [\"wclk\", \"rclk\"], size: 16, width: 8 });\n"
			+ "    fifo.reads(prod.v);\n"
			+ "    this.reads(fifo.dout);\n"
			+ "}\n";

	@Test
	public void asyncFifoDesign_elaboratesInVerilog() throws Exception {
		Path lspJar = requireLspJar();
		Assume.assumeTrue("iverilog not installed", onPath("iverilog"));
		Path projectDir = writeDesign("async-fifo-v-");
		try {
			Path out = projectDir.resolve("out");
			String log = runLspGenerate(lspJar, projectDir, out, "verilog");
			Path gen = out.resolve("verilog-gen");
			assertTrue("the FIFO controllers must be written\n" + log,
					Files.isRegularFile(gen.resolve("com/neosyn/fifo/FIFO_Write_Controller.v"))
							&& Files.isRegularFile(gen.resolve("com/neosyn/fifo/FIFO_Read_Controller.v")));
			List<String> cmd = new ArrayList<>(List.of("iverilog", "-g2012", "-t", "null", "-s", "UsesAsyncFifo"));
			try (Stream<Path> walk = Files.walk(gen)) {
				cmd.addAll(walk.filter(f -> f.toString().endsWith(".v")).map(Path::toString)
						.collect(Collectors.toList()));
			}
			String built = run(cmd, out);
			assertTrue("TRAPS T133: a design using AsynchronousFIFO must elaborate\n" + built, lastExit == 0);
		} finally {
			deleteRecursive(projectDir);
		}
	}

	private static Path writeDesign(String prefix) throws IOException {
		Path projectDir = Files.createTempDirectory(prefix);
		Path pkgDir = projectDir.resolve("src/p");
		Files.createDirectories(pkgDir);
		writeProject(projectDir, "async-fifo");
		writeUtf8(pkgDir.resolve("UsesAsyncFifo.cg"), DESIGN);
		return projectDir;
	}

	// --- helpers ---

	private static boolean onPath(String tool) {
		for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
			if (Files.isExecutable(Paths.get(dir, tool))) {
				return true;
			}
		}
		return false;
	}

	private static int lastExit;

	private static String run(List<String> command, Path cwd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.directory(cwd.toFile());
		pb.redirectErrorStream(true);
		// a failed assert calls $stop, which drops vvp to a prompt that reads stdin
		pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		Process p = pb.start();
		if (!p.waitFor(120, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			fail("timed out: " + command);
		}
		lastExit = p.exitValue();
		return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	// --- helpers ---

	private static void writeProject(Path projectDir, String name) throws IOException {
		writeUtf8(projectDir.resolve(".project"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<projectDescription><name>" + name + "</name></projectDescription>\n");
	}

	private static String runLspGenerate(Path lspJar, Path entry, Path outputDir, String target)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-jar", lspJar.toString(),
				"generate", entry.toAbsolutePath().toString(),
				"--target", target,
				"--output", outputDir.toAbsolutePath().toString());
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.redirectErrorStream(true);
		Process p = pb.start();
		boolean done = p.waitFor(120, TimeUnit.SECONDS);
		if (!done) {
			p.destroyForcibly();
			fail("LSP generate timed out after 120s");
		}
		return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}
}
