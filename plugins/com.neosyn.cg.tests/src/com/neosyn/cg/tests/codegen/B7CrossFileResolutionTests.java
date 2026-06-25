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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

/**
 * Regression tests for B7-deep: cross-file IR-gen NPEs in the LSP path.
 *
 * Two NPE families used to fire when a task referenced something defined in a
 * sibling .cg file:
 *
 * <ul>
 *   <li><b>Cross-file typedef</b>: a task declaring a state variable of a
 *       typedef'd type defined in a sibling bundle (e.g. {@code count_t} in a
 *       sibling Definitions.cg) hit {@code Type.isArray()} / {@code isBool()}
 *       NPE because the Bundle was never processed by SkeletonMaker before the
 *       task was transformed. Root cause: {@code HdlGenerationHandler} didn't
 *       pre-load the resource set, so the typedef proxy stayed unresolved.
 *   <li><b>Cross-instance port property</b>: an inline task in a network using
 *       {@code instance.port.read} (v1 sugar for {@code .read()}) hit
 *       {@code Variable.getBody()} NPE in {@code AbstractCycleScheduler}
 *       because the proxy port couldn't resolve when the resource set was
 *       sparse.
 * </ul>
 *
 * Fix: {@code IrGenerationHandler.preloadAndResolve} extracted as a reusable
 * helper and called by {@code HdlGenerationHandler.generate} before per-file
 * IR generation, plus a lazy bundle instantiation in {@code InstantiatorImpl
 * .getMapping} so the cross-file Bundle is processed on first typedef query.
 *
 * Skipped when the standalone LSP jar isn't built — same convention as
 * {@link IrPathDriftTests}.
 */
public class B7CrossFileResolutionTests {

	@Test
	public void crossFileTypedef_doesNotNPE() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("b7-typedef-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/b7typedef");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>b7-typedef</name></projectDescription>\n");
			writeUtf8(pkgDir.resolve("Defs.cg"),
					"package com.neosyn.test.b7typedef;\n"
					+ "bundle Defs {\n"
					+ "    typedef u9 count_t;\n"
					+ "}\n");
			Path counterCg = pkgDir.resolve("CounterB7.cg");
			writeUtf8(counterCg,
					"package com.neosyn.test.b7typedef;\n"
					+ "task CounterB7 {\n"
					+ "    import com.neosyn.test.b7typedef.Defs.*;\n"
					+ "    out count_t count;\n"
					+ "    count_t count_i;\n"
					+ "    void loop() {\n"
					+ "        count.write(count_i);\n"
					+ "        count_i++;\n"
					+ "    }\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, counterCg, outputDir);

			assertFalse("Should not see Transform error for CounterB7: " + stderr,
					stderr.contains("Transform error in com.neosyn.test.b7typedef.CounterB7"));
			assertFalse("Should not see NullPointerException: " + stderr,
					stderr.contains("NullPointerException"));
			assertTrue("Should generate CounterB7.v",
					Files.isRegularFile(outputDir.resolve(
							"verilog-gen/com/neosyn/test/b7typedef/CounterB7.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	@Test
	public void crossInstancePortRead_doesNotNPE() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("b7-portread-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/b7portread");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>b7-portread</name></projectDescription>\n");
			writeUtf8(pkgDir.resolve("ProducerB7.cg"),
					"package com.neosyn.test.b7portread;\n"
					+ "task ProducerB7 {\n"
					+ "    out u16 data;\n"
					+ "    void setup() { data.write(42); }\n"
					+ "}\n");
			Path topCg = pkgDir.resolve("TopB7.cg");
			writeUtf8(topCg,
					"package com.neosyn.test.b7portread;\n"
					+ "network TopB7 {\n"
					+ "    import com.neosyn.test.b7portread.ProducerB7;\n"
					+ "    producer = new ProducerB7();\n"
					+ "    consumer = new task {\n"
					+ "        void setup() { assert(producer.data.read == 42); }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, topCg, outputDir);

			assertFalse("Should not see NullPointerException: " + stderr,
					stderr.contains("NullPointerException"));
			assertFalse("Should not see Variable.getBody error: " + stderr,
					stderr.contains("Variable.getBody()"));
			assertTrue("Should generate TopB7_consumer.v",
					Files.isRegularFile(outputDir.resolve(
							"verilog-gen/com/neosyn/test/b7portread/TopB7_consumer.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	@Test
	public void unresolvedInstEntity_givesClearError_notNPE() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("b7-unresolved-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/b7unresolved");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>b7-unresolved</name></projectDescription>\n");
			writeUtf8(pkgDir.resolve("WorkerB7.cg"),
					"package com.neosyn.test.b7unresolved;\n"
					+ "task WorkerB7 {\n"
					+ "    out u16 z;\n"
					+ "    void setup() { z.write(16); }\n"
					+ "}\n");
			// `nonexistent.pkg.NoSuchEntity` is genuinely unresolvable — no
			// such file in the project, no matching implicit-package-import
			// prefix. Used to NPE deep in the IR pipeline; now must give a
			// clear IllegalStateException at the call site.
			Path topCg = pkgDir.resolve("TopB7.cg");
			writeUtf8(topCg,
					"package com.neosyn.test.b7unresolved;\n"
					+ "network TopB7 {\n"
					+ "    consumer = new task {\n"
					+ "        void setup() { assert(w.z.read == 16); }\n"
					+ "    };\n"
					+ "    w = new nonexistent.pkg.NoSuchEntity();\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, topCg, outputDir);

			assertFalse("Should not see NullPointerException: " + stderr,
					stderr.contains("NullPointerException"));
			assertTrue("Should see a clear Unresolved-reference message: " + stderr,
					stderr.contains("Unresolved reference"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * A network's own instances must resolve in {@code reads(inst.port)} even
	 * when the network is a SECONDARY resource (generation anchored on another
	 * file). The network-scope provider used to delegate entirely to the global
	 * index, so a sibling network reported "prod cannot be resolved" unless it
	 * was itself the anchor (e.g. project-wide IR gen of the Ethernet tests).
	 */
	@Test
	public void siblingNetworkInstanceReads_resolveWhenAnchoredElsewhere() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("scope-network-reads-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/scopereads");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>scope-reads</name></projectDescription>\n");
			// The anchor we point `generate` at — a trivial standalone task.
			Path anchorCg = pkgDir.resolve("Anchor.cg");
			writeUtf8(anchorCg,
					"package com.neosyn.test.scopereads;\n"
					+ "task Anchor {\n"
					+ "    out u8 a;\n"
					+ "    void setup() { a.write(1); }\n"
					+ "}\n");
			writeUtf8(pkgDir.resolve("Prod.cg"),
					"package com.neosyn.test.scopereads;\n"
					+ "task Prod {\n"
					+ "    out u8 outp;\n"
					+ "    void setup() { outp.write(7); }\n"
					+ "}\n");
			// Sibling network whose reads() references its own instances. It is a
			// SECONDARY resource when generation is anchored on Anchor.cg.
			writeUtf8(pkgDir.resolve("SibNet.cg"),
					"package com.neosyn.test.scopereads;\n"
					+ "network SibNet {\n"
					+ "    import com.neosyn.test.scopereads.Prod;\n"
					+ "    prod = new Prod();\n"
					+ "    sink = new task {\n"
					+ "        in u8 inp;\n"
					+ "        void loop() { inp.read; }\n"
					+ "    };\n"
					+ "    sink.reads(prod.outp);\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, anchorCg, outputDir);

			assertFalse("Sibling network instance refs must resolve even when "
					+ "anchored on another file: " + stderr,
					stderr.contains("cannot be resolved"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * Runs the LSP `generate` command pointed at a specific .cg file (so
	 * {@code findProjectRoot} walks up from the file's package dir to the
	 * marker {@code src/} and lands on {@code projectDir}, instead of starting
	 * a {@code Files.walk} from the parent of a bare directory path — which
	 * trips on system-private dirs under /tmp).
	 */
	private static String runLspGenerate(Path lspJar, Path entryCgFile, Path outputDir)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-jar", lspJar.toString(),
				"generate", entryCgFile.toAbsolutePath().toString(),
				"--target", "verilog",
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

	private static Path findWorkspaceRoot() {
		Path cwd = Paths.get("").toAbsolutePath();
		for (Path candidate = cwd; candidate != null; candidate = candidate.getParent()) {
			if (Files.isDirectory(candidate.resolve("plugins/com.neosyn.cg.tests"))
					&& Files.isDirectory(candidate.resolve("releng"))) {
				return candidate;
			}
		}
		throw new IllegalStateException(
				"Could not locate neosyn-studio workspace root from " + cwd);
	}

	private static void writeUtf8(Path path, String content) throws IOException {
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
	}

	private static void deleteRecursive(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
			walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
				try { Files.delete(p); } catch (IOException ignored) {}
			});
		}
	}
}
