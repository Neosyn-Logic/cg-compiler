/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertFalse;
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
 * TRAPS T147: a Verilog scheduler function must take the signals it reads as inputs.
 *
 * <p>A scheduler that is not inlined is printed as a function and called from the combinational
 * process, {@code always @(*)}. IEEE 1364 makes {@code @(*)} sensitive to a function's arguments
 * only, and the call used to pass a constant dummy alone -- so when a counter in the read
 * condition changed, the process did not re-run, {@code ready} stayed stale in every
 * event-driven simulator, and a FIFO reader gated by that counter never popped: it read the
 * first value, then 0 for every value after it. Synthesis was right; simulation was not.
 *
 * <p>Skipped when the LSP jar isn't built or iverilog is not installed.
 */
public class SchedulerSensitivityTests {

	private static final String COUNTED_READ = "package p;\n"
			+ "network CountedRead {\n"
			+ "    properties { test: { terminate: \"cons.finished\" } }\n"
			+ "    fifo = new std.fifo.SynchronousFIFO({ size: 8, width: 8 });\n"
			+ "    prod = new task { u8 v; void loop() { if (v < 40) { fifo.din.write(v); v = (u8) (v + 1); } } };\n"
			+ "    cons = new task {\n"
			+ "        bool finished; bool bad; u8 n; u2 k;\n"
			+ "        void loop() {\n"
			+ "            k = (u2) (k + 1);\n"
			+ "            if (k == 0 && n < 40) {\n"
			+ "                u8 b = fifo.dout.read();\n"
			+ "                if (b != n) { bad = true; print(\"T147 byte \", n, \" got \", b, \"\\n\"); }\n"
			+ "                n = (u8) (n + 1);\n"
			+ "            }\n"
			+ "            if (!finished && n == 40) { print(\"T147 done bad=\", bad, \"\\n\"); assert(!bad); finished = true; }\n"
			+ "        }\n"
			+ "    };\n"
			+ "}\n";

	@Test
	public void counterGatedFifoReader_readsEveryValueInVerilog() throws Exception {
		Path lspJar = requireLspJar();
		Assume.assumeTrue("iverilog not installed", onPath("iverilog") && onPath("vvp"));
		Path projectDir = Files.createTempDirectory("sched-sens-");
		try {
			Path pkgDir = projectDir.resolve("src/p");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "sched-sens");
			writeUtf8(pkgDir.resolve("CountedRead.cg"), COUNTED_READ);

			Path outputDir = projectDir.resolve("out");
			String log = runLspGenerate(lspJar, projectDir, outputDir, "verilog");
			// the behaviour: every value, in order, under an event-driven simulator
			Path gen = outputDir.resolve("verilog-gen");
			Path tb;
			List<String> files;
			List<String> dirs;
			try (Stream<Path> walk = Files.walk(outputDir)) {
				tb = walk.filter(f -> f.getFileName().toString().equals("CountedRead.tb.v")).findFirst()
						.orElseThrow(() -> new AssertionError("no testbench: " + log));
			}
			try (Stream<Path> walk = Files.walk(gen)) {
				files = walk.filter(f -> f.toString().endsWith(".v") && !f.getFileName().toString().equals("CountedRead.v"))
						.map(Path::toString).collect(Collectors.toList());
			}
			try (Stream<Path> walk = Files.walk(gen)) {
				dirs = walk.filter(Files::isDirectory).map(d -> "-I" + d).collect(Collectors.toList());
			}
			List<String> compile = new ArrayList<>(List.of("iverilog", "-g2012", "-s", "CountedRead_tb", "-o", "t.vvp"));
			compile.addAll(dirs);
			compile.add(tb.toString());
			compile.addAll(files);
			String built = run(compile, outputDir);
			assertTrue("iverilog failed\n" + built, lastExit == 0);
			String sim = run(List.of("vvp", "-n", "t.vvp"), outputDir);
			assertFalse("TRAPS T147: the reader got a wrong value\n" + sim, sim.contains("T147 byte"));
			assertTrue("the run must reach its end and check it\n" + sim,
					sim.contains("T147 done bad=0") && sim.contains("[check]"));

			// and why: the call passes what the function reads
			String cons = readGenerated(outputDir, "CountedRead_cons.v", log);
			assertTrue("the scheduler call must pass the signals its function reads, not the dummy alone\n" + cons,
					cons.contains("(_sched_dummy, k, fifo_dout_valid, n)"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	private static boolean onPath(String tool) {
		for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
			if (Files.isExecutable(Paths.get(dir, tool))) {
				return true;
			}
		}
		return false;
	}

	private static String readGenerated(Path outputDir, String name, String log) throws IOException {
		try (Stream<Path> walk = Files.walk(outputDir)) {
			Path f = walk.filter(p -> p.getFileName().toString().equals(name)).findFirst()
					.orElseThrow(() -> new AssertionError(name + " not generated: " + log));
			return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
		}
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
