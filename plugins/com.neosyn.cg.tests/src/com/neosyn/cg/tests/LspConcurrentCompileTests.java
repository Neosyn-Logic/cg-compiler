/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static com.neosyn.cg.tests.LspTestSupport.requireLspJar;
import static com.neosyn.cg.tests.LspTestSupport.writeUtf8;
import static com.neosyn.cg.tests.LspTestSupport.deleteRecursive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * TRAPS T78 (S182): concurrent language-server requests corrupted each other's compile.
 *
 * <p>The instantiator is a {@code @Singleton} whose mappings every compile replaces, and the server ran its
 * custom requests on pool threads with no lock. The IDE's FSM view requests {@code neosyn/getFsm} whenever an
 * editor is activated or edited; one arriving during {@code neosyn/generateIR} wiped the mappings of the compile
 * in progress: AES failed IR generation in the IDE with "Port not found for write" while the CLI stayed clean.
 * A headless reproduction on AES failed 8 runs out of 8 with getFsm running, 0 out of 8 without.
 *
 * <p>This test drives the built jar over stdio exactly that way, on a fixture of its own: generateIR alone must be
 * clean (the control), then every round of generateIR with getFsm looping beside it must be clean too.
 *
 * <p>Skipped when the standalone LSP jar isn't built.
 */
public class LspConcurrentCompileTests {

	private static final String PKG = "src/com/neosyn/lspconcurrent/";
	private static final int NETWORKS = 24;
	private static final int ROUNDS = 6;

	@Test
	public void generateIrStaysCleanWhileFsmViewRequestsRun() throws Exception {
		Path lspJar = requireLspJar();
		Path projectDir = Files.createTempDirectory("lsp-concurrent-compile-");
		LspClient lsp = null;
		try {
			writeFixture(projectDir);
			lsp = new LspClient(lspJar, projectDir);
			JsonObject init = new JsonObject();
			init.add("processId", JsonNull.INSTANCE);
			init.addProperty("rootUri", projectDir.toUri().toString());
			init.add("capabilities", new JsonObject());
			lsp.request("initialize", init, 180);
			lsp.notify("initialized", new JsonObject());

			JsonObject generateIr = new JsonObject();
			generateIr.addProperty("projectUri", projectDir.toUri().toString());

			// Control: alone, the fixture compiles cleanly -- otherwise the rounds below prove nothing.
			JsonObject alone = lsp.request("neosyn/generateIR", generateIr, 300);
			assertEquals("control: generateIR alone must report no errors -- the fixture itself is broken\n" + alone,
					0, errors(alone).size());
			int files = distinctFiles(alone);
			assertTrue("control: generateIR alone wrote only " + files + " IR file(s)\n" + alone, files > NETWORKS);

			JsonObject getFsm = new JsonObject();
			getFsm.addProperty("uri", projectDir.resolve(PKG + "Gcd.cg").toUri().toString());

			StringBuilder failures = new StringBuilder();
			int fsmRequests = 0;
			for (int round = 1; round <= ROUNDS; round++) {
				AtomicBoolean generating = new AtomicBoolean(true);
				AtomicInteger requests = new AtomicInteger();
				final LspClient client = lsp;
				Thread fsmView = new Thread(() -> {
					while (generating.get()) {
						try {
							client.request("neosyn/getFsm", getFsm, 120);
							requests.incrementAndGet();
						} catch (Exception e) {
							return;
						}
					}
				}, "fsm-view");
				fsmView.start();
				Thread.sleep(50); // the first getFsm is in flight before generateIR starts
				JsonObject result = lsp.request("neosyn/generateIR", generateIr, 300);
				generating.set(false);
				fsmView.join(130_000);
				fsmRequests += requests.get();

				JsonArray errs = errors(result);
				int roundFiles = distinctFiles(result);
				if (errs.size() != 0 || roundFiles != files) {
					failures.append("- round ").append(round).append(": ").append(errs.size()).append(" error(s), ")
							.append(roundFiles).append(" distinct IR file(s) (alone: ").append(files).append(')');
					for (int i = 0; i < Math.min(3, errs.size()); i++) {
						String e = errs.get(i).getAsString();
						failures.append("\n    ").append(e.length() > 160 ? e.substring(0, 160) : e);
					}
					failures.append('\n');
				}
			}
			assertTrue("no neosyn/getFsm request completed during the rounds -- the test proves nothing",
					fsmRequests > 0);
			if (failures.length() > 0) {
				fail("TRAPS T78: neosyn/generateIR was corrupted by concurrent neosyn/getFsm requests (" + fsmRequests
						+ " getFsm)\n" + failures);
			}
		} finally {
			if (lsp != null) {
				lsp.close();
			}
			deleteRecursive(projectDir);
		}
	}

	private static JsonArray errors(JsonObject result) {
		JsonElement e = result.get("errors");
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	private static int distinctFiles(JsonObject result) {
		JsonElement e = result.get("generatedFiles");
		Set<String> files = new HashSet<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement f : e.getAsJsonArray()) {
				files.add(f.getAsString());
			}
		}
		return files.size();
	}

	/** Producer, Consumer and GCD as in tests/golden; NETWORKS copies of the golden InlineTaskNetwork. */
	private static void writeFixture(Path projectDir) throws IOException {
		Path pkg = Files.createDirectories(projectDir.resolve(PKG));
		writeUtf8(projectDir.resolve(".project"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<projectDescription><name>lsp-concurrent</name></projectDescription>\n");
		writeUtf8(pkg.resolve("Producer.cg"),
				"package com.neosyn.lspconcurrent;\n"
				+ "task Producer {\n"
				+ "\tout u8 data;\n"
				+ "\tu8 value = 0;\n"
				+ "\tvoid loop() {\n"
				+ "\t\tdata.write(value);\n"
				+ "\t\tvalue++;\n"
				+ "\t}\n"
				+ "}\n");
		writeUtf8(pkg.resolve("Consumer.cg"),
				"package com.neosyn.lspconcurrent;\n"
				+ "task Consumer {\n"
				+ "\tin u8 data;\n"
				+ "\tout u16 sum;\n"
				+ "\tu16 total = 0;\n"
				+ "\tvoid loop() {\n"
				+ "\t\ttotal = (u16)(total + data.read());\n"
				+ "\t\tsum.write(total);\n"
				+ "\t}\n"
				+ "}\n");
		for (int i = 1; i <= NETWORKS; i++) {
			String name = String.format("Net%02d", i);
			writeUtf8(pkg.resolve(name + ".cg"),
					"package com.neosyn.lspconcurrent;\n"
					+ "network " + name + " {\n"
					+ "\tproperties { test: true }\n"
					+ "\tsource = new Producer();\n"
					+ "\tdoubler = new task {\n"
					+ "\t\tin u8 input;\n"
					+ "\t\tout u8 output;\n"
					+ "\t\tvoid loop() {\n"
					+ "\t\t\toutput.write((u8)(input.read() * 2));\n"
					+ "\t\t}\n"
					+ "\t};\n"
					+ "\tsink = new Consumer();\n"
					+ "\tdoubler.reads(source.data);\n"
					+ "\tsink.reads(doubler.output);\n"
					+ "}\n");
		}
		writeUtf8(pkg.resolve("Gcd.cg"),
				"package com.neosyn.lspconcurrent;\n"
				+ "task Gcd {\n"
				+ "\tin u8 a, b;\n"
				+ "\tout u8 result;\n"
				+ "\tvoid loop() {\n"
				+ "\t\tu8 x = a.read();\n"
				+ "\t\tu8 y = b.read();\n"
				+ "\t\twhile (x != y) {\n"
				+ "\t\t\tif (x > y) {\n"
				+ "\t\t\t\tx = (u8)(x - y);\n"
				+ "\t\t\t} else {\n"
				+ "\t\t\t\ty = (u8)(y - x);\n"
				+ "\t\t\t}\n"
				+ "\t\t}\n"
				+ "\t\tresult.write(x);\n"
				+ "\t}\n"
				+ "}\n");
	}
}
