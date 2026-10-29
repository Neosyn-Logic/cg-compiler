/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests.codegen;

import static org.junit.Assert.assertEquals;
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
 * M3 (model friendliness, 2026-09-30): a line the parser derailed reports only its first parse error.
 * `in stream u8 in;` gave the right message ("`in` is a reserved word") plus two cascade errors
 * on the same line -- "extraneous input ')'" and "read cannot be resolved" -- and a model treats
 * all three as real. Uses `generate-ir`, the command the agent kit's cg_check runs.
 *
 * <p>Skipped when the LSP jar isn't built.
 */
public class FirstErrorPerLineTests {

	@Test
	public void aDerailedLineReportsOnlyItsFirstError() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("first-error-");
		try {
			Path pkgDir = projectDir.resolve("src/p");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "first-error");
			Path cg = pkgDir.resolve("T.cg");
			writeUtf8(cg, "package p;\n"
					+ "task T { in stream u8 in; out stream u8 y; void loop() { y.write(in.read()); } }\n");
			String out = run(List.of("java", "-jar", lspJar.toString(), "generate-ir", cg.toString()), projectDir);
			long onLine2 = out.lines().filter(l -> l.contains("T.cg:2:")).count();
			assertTrue("the real error must be reported\n" + out, out.contains("`in` is a reserved word"));
			assertEquals("M3: one error for the derailed line, not its cascade\n" + out, 1, onLine2);
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * TRAPS T180 (user report 2026-10-21): bools cast and added into a u4 made IR generation crash
	 * with a NullPointerException, and generate, generate-ir (= cg_check) printed that crash FIRST,
	 * ahead of the real "Type mismatch" errors -- a model read the crash as its error.
	 */
	@Test
	public void aTransformCrashIsNotPrintedBeforeTheBlockingErrors() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("crash-first-");
		try {
			Path pkgDir = projectDir.resolve("src/p");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "crash-first");
			Path cg = pkgDir.resolve("C.cg");
			writeUtf8(cg, T180_FIXTURE);
			String gen = run(List.of("java", "-jar", lspJar.toString(), "generate", cg.toString(),
					"--output", projectDir.resolve("out").toString()), projectDir);
			String ir = run(List.of("java", "-jar", lspJar.toString(), "generate-ir", cg.toString()), projectDir);
			for (String out : List.of(gen, ir)) {
				String first = out.lines().filter(l -> l.startsWith("[neosyn]")).findFirst().orElse("");
				assertTrue("the first diagnostic must be the type error\n" + out,
						first.contains("C.cg:") && first.contains("Type mismatch"));
				assertFalse("no Java crash may be printed\n" + out, out.contains("NullPointerException"));
			}
			assertTrue("generate must stop on the errors\n" + gen, gen.contains("Cannot generate HDL"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	// The crash is `1 - rowp` (a bool in arithmetic, TRAPS T181); the blocking error is the cast on
	// the same line. Measured on jar 7b31ffc: both commands printed the NPE first. A cast of a plain
	// bool, without the `1 - rowp`, does not crash and would make this test pass with the bug present.
	static final String T180_FIXTURE = "package p;\n"
			+ "task C {\n"
			+ "    in stream bool a; out stream u4 n;\n"
			+ "    bool buf[2][4]; bool rowp;\n"
			+ "    void loop() {\n"
			+ "        bool m = a.read();\n"
			+ "        u4 cnt = (u4) (buf[1 - rowp][1]); n.write(cnt);\n"
			+ "    }\n"
			+ "}\n";

	/**
	 * TRAPS T69: the indexes of a READ were never checked -- only assignment targets were -- so
	 * three indexes on a one-dimensional array and bit 99 of a u16 compiled without a word.
	 */
	@Test
	public void indexesOfAReadAreChecked() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("read-indexes-");
		try {
			Path pkgDir = projectDir.resolve("src/p");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "read-indexes");
			writeUtf8(pkgDir.resolve("P.cg"), "package p;\n"
					+ "task P { out push u8 y; void loop() { u8 a[2]; y.write(a[0][1][2]); } }\n");
			writeUtf8(pkgDir.resolve("Q.cg"), "package p;\n"
					+ "task Q { in push u16 v; out push bool y; void loop() { u16 w = v.read(); y.write(w[99]); } }\n");
			// A FILE target: generate-ir runs the full @Check pass on its target file only; a project
			// directory gets syntax and linking (IrGenerationHandler -- per-file checks hung simulate).
			String p = run(List.of("java", "-jar", lspJar.toString(), "generate-ir",
					pkgDir.resolve("P.cg").toString()), projectDir);
			assertTrue("too many indexes on a read must be reported\n" + p,
					p.contains("P.cg:2:") && p.contains("cannot convert from number to array"));
			String q = run(List.of("java", "-jar", lspJar.toString(), "generate-ir",
					pkgDir.resolve("Q.cg").toString()), projectDir);
			assertTrue("a bit past the end must be reported\n" + q,
					q.contains("Q.cg:2:") && q.contains("bit 99 does not exist in a 16-bit value"));
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
