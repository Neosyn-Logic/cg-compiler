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
import static com.neosyn.cg.tests.LspTestSupport.findWorkspaceRoot;
import static com.neosyn.cg.tests.LspTestSupport.requireLspJar;
import static com.neosyn.cg.tests.LspTestSupport.writeUtf8;
import static com.neosyn.cg.tests.LspTestSupport.deleteRecursive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

/**
 * TRAPS T57 remainder: a task that reads std.math.Divide / Multiply results and PAUSES between
 * reads ({@code read(); idle(3);}) lost every other result on BOTH back ends -- 1, 3, 5 ... from
 * the divider. The RTL held its result until valid && ready, but the compiler did not list the two
 * as held built-ins, so the reader kept the registered {@code ready = !valid} handshake; and the
 * simulator's models retired a result on {@code ready} alone, which a busy reader leaves up.
 * Fixtures: tools/qa/repro/T57-builtins. Skipped when the LSP jar isn't built; the Verilog leg is
 * skipped without iverilog.
 */
public class HeldBuiltinStreamTests {

	@Test
	public void pausingReaderOfDivide_getsEveryResult() throws Exception {
		check("Test_T57Div", "T57 Divide 12/12");
	}

	@Test
	public void pausingReaderOfMultiply_getsEveryResult() throws Exception {
		check("Test_T57Mul", "T57 Multiply 12/12");
	}

	private static void check(String top, String done) throws Exception {
		Path lspJar = requireLspJar();
		Path fixture = findWorkspaceRoot().resolve("tools/qa/repro/T57-builtins/" + top + ".cg");
		Assume.assumeTrue("fixture missing: " + fixture, Files.isRegularFile(fixture));
		Path projectDir = Files.createTempDirectory("held-builtin-");
		try {
			Path pkgDir = projectDir.resolve("src/t57");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "held-builtin");
			Path cg = pkgDir.resolve(top + ".cg");
			Files.copy(fixture, cg);

			String sim = run(List.of("java", "-jar", lspJar.toString(), "simulate", cg.toString()), projectDir);
			assertFalse("TRAPS T57: the simulator dropped a result\n" + sim, sim.contains("Assertion failed"));
			assertTrue("TRAPS T57: simulator\n" + sim, sim.contains(done));

			if (!iverilogAvailable()) {
				return;
			}
			Path out = projectDir.resolve("out");
			String log = runLspGenerate(lspJar, projectDir, out, "verilog");
			List<String> cmd = new ArrayList<>(List.of("iverilog", "-g2012", "-s", top + "_tb",
					"-o", projectDir.resolve("sim.vvp").toString()));
			try (Stream<Path> walk = Files.walk(out)) {
				// the top's .tb.v redeclares the top module, so its verilog-gen copy is left out, and
				// only the top's own testbench is built
				cmd.addAll(walk.map(Path::toString).filter(p -> p.endsWith(".v"))
						.filter(p -> !p.endsWith("/verilog-gen/t57/" + top + ".v"))
						.filter(p -> !p.contains("/testbench/") || p.endsWith("/" + top + ".tb.v"))
						.sorted().collect(Collectors.toList()));
			}
			String build = run(cmd, projectDir);
			assertTrue("iverilog build\n" + log + build, lastExit == 0);
			String v = run(List.of("vvp", "-n", projectDir.resolve("sim.vvp").toString()), projectDir);
			assertFalse("TRAPS T57: the Verilog dropped a result\n" + v, v.contains("Assertion failed"));
			assertTrue("TRAPS T57: Verilog\n" + v, v.contains(done));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	private static boolean iverilogAvailable() {
		try {
			Process p = new ProcessBuilder("iverilog", "-V").redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			return p.waitFor(20, TimeUnit.SECONDS);
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private static int lastExit;

	private static String run(List<String> command, Path cwd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.directory(cwd.toFile());
		pb.redirectErrorStream(true);
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
