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
 * TRAPS T31: {@code c.write(1); u8 v = r.read();} where {@code c} goes to an instance and
 * {@code r} comes back from the same instance was fused into ONE action whose guard waits for the
 * reply before the request is sent. {@code generate} said "Success!" and {@code simulate} printed
 * nothing until a 10-million-cycle timeout. It is now refused, naming both ports and the fix; the
 * same design with {@code fence;} between them still generates.
 *
 * <p>Skipped when the LSP jar isn't built.
 */
public class RequestReplyDeadlockTests {

	private static String design(String between) {
		return "package com.neosyn.test.reqrep;\n"
				+ "network R_HandshakeLoop {\n"
				+ "    properties { test: { terminate: \"seq.finished\" } }\n"
				+ "    dut = new task {\n"
				+ "        in stream u8 c;  out push u8 r;\n"
				+ "        void loop() { r.write((u8) (c.read() + 1)); }\n"
				+ "    };\n"
				+ "    seq = new task {\n"
				+ "        out stream u8 c;  in push u8 r;\n"
				+ "        bool finished = false;\n"
				+ "        void setup() {\n"
				+ "            c.write(1);\n"
				+ between
				+ "            u8 v = r.read();\n"
				+ "            print(\"v=\", v, \"\\n\");\n"
				+ "            finished = true;\n"
				+ "        }\n"
				+ "    };\n"
				+ "    dut.reads(seq.c);\n"
				+ "    seq.reads(dut.r);\n"
				+ "}\n";
	}

	private static String generate(String source) throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("request-reply-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/reqrep");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "request-reply");
			writeUtf8(pkgDir.resolve("R_HandshakeLoop.cg"), source);
			return runLspGenerate(lspJar, projectDir, projectDir.resolve("out"), "verilog");
		} finally {
			deleteRecursive(projectDir);
		}
	}

	@Test
	public void requestAndReplyInOneCycle_isRefused() throws Exception {
		String log = generate(design(""));
		assertTrue("TRAPS T31: the deadlock must be named, with its line and the fix\n" + log,
				log.contains("R_HandshakeLoop.cg:13:") && log.contains("never completes")
						&& log.contains("fence;"));
		assertFalse("TRAPS T31: generate must not report success\n" + log, log.contains("Success!"));
	}

	@Test
	public void withAFence_itStillGenerates() throws Exception {
		String log = generate(design("            fence;\n"));
		assertFalse("a fenced request/reply is correct and must not be flagged\n" + log,
				log.contains("never completes"));
		assertTrue(log, log.contains("Success!"));
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
