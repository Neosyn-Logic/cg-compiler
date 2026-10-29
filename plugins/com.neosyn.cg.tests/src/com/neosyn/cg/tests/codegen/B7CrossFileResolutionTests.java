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
import static com.neosyn.cg.tests.LspTestSupport.writeUtf8;
import static com.neosyn.cg.tests.LspTestSupport.deleteRecursive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

	/**
	 * TRAPS T157: two files defining the same entity (same package and name) were accepted without a
	 * word, and one copy was ignored. generate must now stop and name the other file.
	 */
	@Test
	public void sameEntityInTwoFiles_isRefusedNamingBoth() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("t157-dup-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/t157");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>t157-dup</name></projectDescription>\n");
			Path first = pkgDir.resolve("Counter.cg");
			writeUtf8(first,
					"package com.neosyn.test.t157;\n"
					+ "task Counter { out u8 count; u8 c; void loop() { count.write(c); c++; } }\n");
			writeUtf8(pkgDir.resolve("Extra.cg"),
					"package com.neosyn.test.t157;\n"
					+ "task Counter { out u8 count; void loop() { count.write(7); } }\n");

			String stderr = runLspGenerate(lspJar, first, projectDir.resolve("out"));

			assertTrue("generate must name the other file: " + stderr,
					stderr.contains("is also defined in Extra.cg"));
			assertTrue("...and must refuse to build: " + stderr,
					stderr.contains("Cannot generate HDL"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * TRAPS T170: `generate Top.cg` checked blocking errors in Top.cg only, then emitted every entity
	 * of the project -- a sibling with an impossible cast came out as Verilog behind "Success!". The
	 * file form now refuses, naming the sibling, as `generate .` always did.
	 */
	@Test
	public void blockingErrorInASibling_refusesTheFileForm() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("t170-sibling-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/t170");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>t170-sibling</name></projectDescription>\n");
			Path top = pkgDir.resolve("Top.cg");
			writeUtf8(top,
					"package com.neosyn.test.t170;\n"
					+ "task Top { out u8 count; u8 c; void loop() { count.write(c); c++; } }\n");
			writeUtf8(pkgDir.resolve("Broken.cg"),
					"package com.neosyn.test.t170;\n"
					+ "task Broken { in bool a; out u8 b; void loop() { b.write((u8) a.read()); } }\n");

			String stderr = runLspGenerate(lspJar, top, projectDir.resolve("out"));

			assertTrue("generate must refuse and name the sibling: " + stderr,
					stderr.contains("Cannot generate HDL") && stderr.contains("Broken.cg"));
			assertFalse("...and must not emit the sibling: " + stderr, stderr.contains("Success!"));
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
			// The clear message now comes from the LINKER, not from an exception thrown deep in
			// the IR pipeline, and it is strictly better: it names the entity AND carries a file
			// and line, which the transform-layer "Unresolved reference" never did. The IR
			// transform no longer runs at all on a source the front end has rejected, so that
			// text is gone by design. The intent is unchanged: a clear error, not a crash.
			assertTrue("Should name the unresolved entity, with a location: " + stderr,
					stderr.contains("Couldn't resolve reference to Instantiable"));
			assertTrue("...and must still name the entity that failed: " + stderr,
					stderr.contains("NoSuchEntity"));
			assertFalse("...and no transform-layer error may compete with it: " + stderr,
					stderr.contains("Transform error"));
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
	/**
	 * Guards the SHIPPED codegen path. The direct held-producer sync-ready fix (S154) is gated on
	 * {@code StreamLinkMarker}, applied via {@code ICodeGenerator.prepare()}. That hook is called by
	 * {@code fullBuild} and the codegen test harness — but the LSP {@code generate} command
	 * ({@code HdlGenerationHandler}) originally did NOT call it, so the fix passed every unit test
	 * yet was absent from the product (it emitted the old {@code ready = !valid} handshake that
	 * drops values under backpressure). This test exercises the real LSP jar end-to-end and fails
	 * if the marking is ever dropped from the product path again — a green harness is not enough.
	 */
	@Test
	public void lspGenerate_appliesDirectHeldStreamHandshake() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		Path projectDir = Files.createTempDirectory("held-handshake-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/heldstream");
			Files.createDirectories(pkgDir);
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>held-handshake</name></projectDescription>\n");
			// A direct held link: a producer task wired straight to a consumer task (no FIFO),
			// consumer applies backpressure via idle() — the exact pattern the S154 fix targets.
			Path netCg = pkgDir.resolve("TestHeld.cg");
			writeUtf8(netCg,
					"package com.neosyn.test.heldstream;\n"
					+ "network TestHeld {\n"
					+ "    source = new task {\n"
					+ "        out sync ready u4 dout;\n"
					+ "        void setup() { dout.write(4); dout.write(5); dout.write(6); }\n"
					+ "    };\n"
					+ "    expected = new task {\n"
					+ "        void setup() {\n"
					+ "            assert(source.dout.read() == 4); idle(1);\n"
					+ "            assert(source.dout.read() == 5); idle(1);\n"
					+ "            assert(source.dout.read() == 6);\n"
					+ "        }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, netCg, outputDir);

			Path genDir = outputDir.resolve("verilog-gen/com/neosyn/test/heldstream");
			Path expectedV = genDir.resolve("TestHeld_expected.v");
			Path sourceV = genDir.resolve("TestHeld_source.v");
			assertTrue("Should generate TestHeld_expected.v: " + stderr, Files.isRegularFile(expectedV));
			assertTrue("Should generate TestHeld_source.v: " + stderr, Files.isRegularFile(sourceV));

			String expected = new String(Files.readAllBytes(expectedV), StandardCharsets.UTF_8);
			assertTrue("LSP generate path must apply the direct-held-stream marking: the consumer's "
					+ "read states must assert ready = 1'b1, not the registered-FIFO ready = !valid "
					+ "pulse that drops a value against a held producer. If this fails, "
					+ "HdlGenerationHandler stopped calling ICodeGenerator.prepare().",
					expected.contains("source_dout_ready = 1'b1"));

			String source = new String(Files.readAllBytes(sourceV), StandardCharsets.UTF_8);
			assertTrue("Held producer must hold its FSM on (dout_ready || !dout_valid)",
					source.contains("if (dout_ready || !dout_valid)"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * A file-scope declaration is private to its file. Another file of the same package that
	 * uses it must get the linker's "X cannot be resolved" at the use -- in EITHER load order.
	 * <p>
	 * It used to be exported to the index as {@code <package>.<member>}, so the same-package
	 * implicit import resolved it from every file whenever the defining file happened to be
	 * loaded, and the back end then failed in a way that depended on the order: a
	 * NullPointerException ({@code ResourceSet.getLoadOptions()}), "Call to function 'twice'
	 * that has no compiled body", or "Unresolved reference" -- never with a file or line, and
	 * even when the file being checked was the correct one (user report, 2026-09-28).
	 */
	@Test
	public void fileScopeDeclaration_isPrivateToItsFile_inEitherLoadOrder() throws Exception {
		Path workspaceRoot = findWorkspaceRoot();
		Path lspJar = workspaceRoot.resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));

		// {defining file, using file}: the directory is walked in name order, so these two
		// put the definition before and after the use.
		String[][] orders = { { "ATwice.cg", "ZUsesTwice.cg" }, { "ZTwice.cg", "AUsesTwice.cg" } };
		for (String[] order : orders) {
			Path projectDir = Files.createTempDirectory("b7-filescope-");
			try {
				writeUtf8(projectDir.resolve(order[0]),
						"package com.neosyn.test.b7filescope;\n"
						+ "const u8 twice(u8 a) { return (u8) (a << 1); }\n"
						+ "const u8 SHARED = 7;\n"
						+ "task Definer {\n"
						+ "    in push u8 a;\n"
						+ "    out push u8 y;\n"
						+ "    void loop() { y.write((u8) (twice(a.read()) + SHARED)); }\n"
						+ "}\n");
				writeUtf8(projectDir.resolve(order[1]),
						"package com.neosyn.test.b7filescope;\n"
						+ "task User {\n"
						+ "    in push u8 a;\n"
						+ "    out push u8 y;\n"
						+ "    void loop() { y.write(twice(a.read())); }\n"
						+ "}\n");

				String out = runLspGenerateIr(lspJar, projectDir);
				String where = order[1] + " after " + order[0] + ": " + out;
				assertTrue("the use must be reported at its file and line, " + where,
						out.contains(order[1] + ":5: twice cannot be resolved"));
				// ...and say WHY: the name exists, at the top of the other file, where it is
				// private (a user read the bare message as a typo it could not find)
				assertTrue("the error must name the file that declares it, " + where,
						out.contains("declared at the top of " + order[0]));
				assertTrue("...and the way to share it, " + where, out.contains("put it in a bundle"));
				assertFalse("no transform-layer error may compete with it, " + where,
						out.contains("Transform error"));
				assertFalse("no crash, " + where, out.contains("NullPointerException"));

				// Control: the defining file, alone, still uses its own declarations.
				Files.delete(projectDir.resolve(order[1]));
				String alone = runLspGenerateIr(lspJar, projectDir);
				assertTrue("file scope must still work inside its own file: " + alone,
						alone.contains("Success!"));
			} finally {
				deleteRecursive(projectDir);
			}
		}
	}

	/**
	 * TRAPS T182: {@code generate-ir <file>} walked only the file's own directory, so a reference
	 * into a PARENT package never resolved (Ethernet's MacTop.cg -> com.neosyn.net.FrameDriver; 144
	 * of 246 core files failed). The editor runs this on every save.
	 */
	@Test
	public void generateIrOnAFile_resolvesAParentPackage() throws Exception {
		Path lspJar = findWorkspaceRoot().resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue("LSP jar not built at " + lspJar, Files.isRegularFile(lspJar));
		Path projectDir = Files.createTempDirectory("b7-parentpkg-");
		try {
			Path pkg = projectDir.resolve("src/com/neosyn/test/b7up");
			Files.createDirectories(pkg.resolve("sub"));
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>b7-parentpkg</name></projectDescription>\n");
			writeUtf8(pkg.resolve("Defs.cg"), "package com.neosyn.test.b7up;\n"
					+ "bundle Defs {\n    const u8 STEP = 3;\n}\n");
			Path user = pkg.resolve("sub/User.cg");
			writeUtf8(user, "package com.neosyn.test.b7up.sub;\n"
					+ "task User {\n"
					+ "    import com.neosyn.test.b7up.Defs.*;\n"
					+ "    in push u8 a; out push u8 y;\n"
					+ "    void loop() { y.write((u8) (a.read() + STEP)); }\n"
					+ "}\n");
			String out = runLspGenerateIr(lspJar, user);
			assertFalse("the parent package must resolve: " + out, out.contains("Couldn't resolve"));
			assertTrue("generate-ir on the file must succeed: " + out, out.contains("Success!"));
		} finally {
			deleteRecursive(projectDir);
		}

		// Control, the agent kit's shape: a loose file with no .project and no src/ -- its own
		// directory only, nothing above it is walked.
		Path loose = Files.createTempDirectory("b7-loose-");
		try {
			Path f = loose.resolve("Echo.cg");
			writeUtf8(f, "package p;\ntask Echo { in push u8 a; out push u8 y; void loop() { y.write(a.read()); } }\n");
			String out = runLspGenerateIr(lspJar, f);
			assertTrue("a loose file must still compile on its own: " + out, out.contains("Success!"));
		} finally {
			deleteRecursive(loose);
		}
	}

	/** {@code generate-ir <dir>}: what cg_check with package_dir runs. */
	private static String runLspGenerateIr(Path lspJar, Path projectDir)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-jar", lspJar.toString(),
				"generate-ir", projectDir.toAbsolutePath().toString());
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.redirectErrorStream(true);
		pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
		Process p = pb.start();
		boolean done = p.waitFor(120, TimeUnit.SECONDS);
		if (!done) {
			p.destroyForcibly();
			fail("LSP generate-ir timed out after 120s");
		}
		return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

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

	private static String runLspSimulate(Path lspJar, Path entryCgFile, String entity)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-jar", lspJar.toString(),
				"simulate", entryCgFile.toAbsolutePath().toString(),
				"--entity", entity);
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.redirectErrorStream(true);
		Process p = pb.start();
		boolean done = p.waitFor(120, TimeUnit.SECONDS);
		if (!done) {
			p.destroyForcibly();
			fail("LSP simulate timed out after 120s");
		}
		return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

}
