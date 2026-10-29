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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * TRAPS T66 (S181 re-measure): a block-local temporary of a COMBINATIONAL block must not infer a latch.
 *
 * <p>The Verilog printer declares an action's locals inside a named block. In a combinational block a
 * temporary assigned on one path only -- RISC-V ALU's {@code orion = (av + bv)}, AES MixColumns'
 * {@code transform_ret_1} -- keeps its value on the others, which every synthesizer reports as an
 * inferred latch: 80 of them in RISC-V, Intel-8088 and AES. Each scalar local now gets a zero default
 * right after its declaration. Shape of RISC-V's ALU.
 *
 * <p>Skipped when the LSP jar isn't built; the Yosys check skips itself without Yosys.
 */
public class VerilogCombinationalDefaultsTests {

	private static final Pattern REG_LOCAL = Pattern.compile("^\\s*reg\\s+(?:signed\\s+)?(?:\\[[^\\]]*\\]\\s*)?(\\w+)\\s*;",
			Pattern.MULTILINE);

	@Test
	public void combinationalTemporaries_haveDefaults_andInferNoLatch() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("comb-defaults-");
		try {
			Path pkg = projectDir.resolve("src/com/neosyn/test/combdefaults");
			Files.createDirectories(pkg);
			writeProject(projectDir, "comb-defaults");
			writeUtf8(pkg.resolve("Alu.cg"),
					"package com.neosyn.test.combdefaults;\n"
					+ "task Alu {\n"
					+ "    properties { type: \"combinational\" }\n"
					+ "    in u2 op;\n"
					+ "    in u8 a, b;\n"
					+ "    out u8 y;\n"
					+ "    void loop() {\n"
					+ "        u2 o = op.read();\n"
					+ "        u8 av = a.read();\n"
					+ "        u8 bv = b.read();\n"
					+ "        if (o == 0) {\n"
					+ "            y.write((u8) (av + bv));\n"
					+ "        } else if (o == 1) {\n"
					+ "            y.write((u8) (av - bv));\n"
					+ "        } else {\n"
					+ "            y.write((u8) (av ^ bv));\n"
					+ "        }\n"
					+ "    }\n"
					+ "}\n");

			Path out = projectDir.resolve("out");
			String log = runLspGenerate(lspJar, projectDir, out, "verilog");
			Path alu = out.resolve("verilog-gen/com/neosyn/test/combdefaults/Alu.v");
			assertTrue("Alu.v should be generated: " + log, Files.isRegularFile(alu));
			String v = new String(Files.readAllBytes(alu), StandardCharsets.UTF_8);

			// The named block of the combinational process, up to its first `if`.
			Matcher block = Pattern.compile("always @\\((?![^)]*edge)[^)]*\\) begin(.*?)\\bif \\(", Pattern.DOTALL).matcher(v);
			assertTrue("no combinational always block with an if in it -- the fixture proves nothing\n" + v, block.find());
			String head = block.group(1);
			List<String> locals = new ArrayList<>();
			for (Matcher m = REG_LOCAL.matcher(head); m.find();) {
				locals.add(m.group(1));
			}
			StringBuilder failures = new StringBuilder();
			if (locals.isEmpty()) {
				failures.append("the combinational block declares no block-local reg -- the fixture proves nothing\n");
			}
			for (String local : locals) {
				if (!Pattern.compile("^\\s*" + Pattern.quote(local) + " = 0;", Pattern.MULTILINE).matcher(head).find()) {
					failures.append("local ").append(local).append(" has no default before the first if\n");
				}
			}

			if (toolAvailable("yosys", "-V")) {
				// No -q: it suppresses the very "Latch inferred" log lines this looks for, so the check could never fail.
				String y = run(List.of("yosys", "-p", "read_verilog " + alu + "; hierarchy -top Alu; proc"), projectDir);
				if (y.contains("Latch inferred")) {
					failures.append("Yosys still infers a latch:\n").append(y);
				}
			}
			if (failures.length() > 0) {
				fail("TRAPS T66\n" + failures + "--- Alu.v ---\n" + v);
			}
		} finally {
			deleteRecursive(projectDir);
		}
	}

	private static String run(List<String> command, Path cwd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(cwd.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		if (!p.waitFor(120, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			fail("timed out: " + command);
		}
		return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	private static boolean toolAvailable(String tool, String versionFlag) {
		try {
			Process p = new ProcessBuilder(tool, versionFlag).redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
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
