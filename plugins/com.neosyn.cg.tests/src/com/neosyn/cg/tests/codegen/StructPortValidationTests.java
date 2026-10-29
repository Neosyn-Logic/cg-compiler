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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * TRAPS T130: validating a network with a STRUCT port threw NoClassDefFoundError inside the
 * validator. A struct port flattens to one IR port per field, so the Typer found no single port
 * for {@code req.write(z)} and called its Eclipse-workspace marker API, absent from the
 * standalone jar. The user saw only "warning: Error executing EValidator" -- and every check
 * after it on that object was skipped. SoC's TestBusProbeStruct hit it on every generate.
 *
 * <p>Skipped when the LSP jar isn't built.
 */
public class StructPortValidationTests {

	@Test
	public void structPortNetwork_validatesWithoutAnInternalError() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("struct-port-validation-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/structval");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "struct-port-validation");
			writeUtf8(pkgDir.resolve("StructLink.cg"), "package com.neosyn.test.structval;\n"
					+ "network StructLink {\n"
					+ "    struct Req { bool vld; u8 addr; }\n"
					+ "    m = new task {\n"
					+ "        out Req req;\n"
					+ "        u8 n;\n"
					+ "        void setup() { n = 0; }\n"
					+ "        void loop() { Req z; z.vld = true; z.addr = n; req.write(z); n = (u8) (n + 1); }\n"
					+ "    };\n"
					+ "    s = new task {\n"
					+ "        out u8 addr;\n"
					+ "        void loop() { Req r = m.req.read(); if (r.vld) { addr.write(r.addr); } }\n"
					+ "    };\n"
					+ "}\n");
			String log = runLspGenerate(lspJar, projectDir, projectDir.resolve("out"), "verilog");
			assertFalse("TRAPS T130: a validator threw on a struct port\n" + log,
					log.contains("Error executing EValidator"));
			assertTrue("the struct network must still generate\n" + log, log.contains("Success!"));
		} finally {
			deleteRecursive(projectDir);
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
