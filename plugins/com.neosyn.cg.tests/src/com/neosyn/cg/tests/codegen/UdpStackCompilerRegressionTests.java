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
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Regression tests for the compiler bugs uncovered while reviving the
 * silicon-validated UDP/IP stack (all fired through the LSP {@code generate}
 * path on otherwise-valid designs):
 *
 * <ul>
 *   <li><b>Same-package instance-port read</b>: a network reading a sibling
 *       instance's output port (e.g. {@code cksum.computed}) failed with
 *       "Unresolved reference" ONLY when the network lived in the SAME package
 *       as the instantiated entity. Root cause: the entity's {@code new Foo()}
 *       link is a transient proxy during lazy linking of the member reference,
 *       so the instance-port scope came back empty and {@code CgLinkingService}
 *       fabricated a bogus {@code .computed} <em>property</em> (like {@code .read}
 *       / {@code .available}) that permanently mis-bound the port. Fixed by not
 *       fabricating a property when the previous segment is an instance whose
 *       entity is still unresolved, so the reference re-links once the proxy
 *       resolves.</li>
 *   <li><b>Built-in single-clock entity NPE</b>: instantiating
 *       {@code std.mem.SinglePortRAM} / {@code std.fifo.SynchronousFIFO} threw a
 *       {@code NullPointerException} ("keys is null") in the clock-properties
 *       checker because their built-in IR declared no {@code clocks}. Fixed by
 *       declaring their single clock (like the dual-port RAMs already do) and
 *       null-guarding the instance clock/reset association.</li>
 * </ul>
 *
 * Skipped when the standalone LSP jar isn't built — same convention as
 * {@link B7CrossFileResolutionTests}.
 */
public class UdpStackCompilerRegressionTests {

	/**
	 * A network in the SAME package as the instantiated entity must resolve a
	 * read of that instance's output port. This is the {@code cksum.computed}
	 * pattern from {@code MakeIPPacket} / {@code IpTop} in the UDP/IP stack.
	 */
	@Test
	public void samePackageInstancePortRead_resolves() throws Exception {
		Path lspJar = requireLspJar();

		Path projectDir = Files.createTempDirectory("samepkg-portread-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/samepkg");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "samepkg-portread");

			// Entity and reader live in the SAME package (com.neosyn.test.samepkg).
			writeUtf8(pkgDir.resolve("Cksum.cg"),
					"package com.neosyn.test.samepkg;\n"
					+ "task Cksum {\n"
					+ "    in push u16 word, bool done; out u16 computed;\n"
					+ "    u16 acc;\n"
					+ "    void loop() {\n"
					+ "        if (done.read()) { acc = 0; }\n"
					+ "        else { acc += word.read(); computed.write(~acc); }\n"
					+ "    }\n"
					+ "}\n");
			Path readerCg = pkgDir.resolve("Reader.cg");
			writeUtf8(readerCg,
					"package com.neosyn.test.samepkg;\n"
					+ "network Reader {\n"
					+ "    out u8 packet (push);\n"
					+ "    cksum = new Cksum();\n"
					+ "    cksum.reads(t.sum, t.done);\n"
					+ "    t = new task {\n"
					+ "        out u16 sum (push), bool done;\n"
					+ "        void loop() {\n"
					+ "            sum.write(1); done.write(true);\n"
					+ "            packet.write(cksum.computed >> 8);\n"
					+ "        }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, readerCg, outputDir);

			assertFalse("Should not report an unresolved reference for cksum.computed: " + stderr,
					stderr.contains("Unresolved reference"));
			assertFalse("Should not see NullPointerException: " + stderr,
					stderr.contains("NullPointerException"));
			assertTrue("Should generate Reader_t.v: " + stderr,
					Files.isRegularFile(outputDir.resolve(
							"verilog-gen/com/neosyn/test/samepkg/Reader_t.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * Control for {@link #samePackageInstancePortRead_resolves()}: the identical
	 * read from a DIFFERENT package (with an explicit import) must still resolve —
	 * i.e. the fix does not break the pre-existing cross-package path.
	 */
	@Test
	public void crossPackageInstancePortRead_stillResolves() throws Exception {
		Path lspJar = requireLspJar();

		Path projectDir = Files.createTempDirectory("crosspkg-portread-");
		try {
			Path entPkg = projectDir.resolve("src/com/neosyn/test/xpkg/ent");
			Path rdrPkg = projectDir.resolve("src/com/neosyn/test/xpkg/rdr");
			Files.createDirectories(entPkg);
			Files.createDirectories(rdrPkg);
			writeProject(projectDir, "crosspkg-portread");

			writeUtf8(entPkg.resolve("Cksum.cg"),
					"package com.neosyn.test.xpkg.ent;\n"
					+ "task Cksum {\n"
					+ "    in push u16 word, bool done; out u16 computed;\n"
					+ "    u16 acc;\n"
					+ "    void loop() {\n"
					+ "        if (done.read()) { acc = 0; }\n"
					+ "        else { acc += word.read(); computed.write(~acc); }\n"
					+ "    }\n"
					+ "}\n");
			Path readerCg = rdrPkg.resolve("Reader.cg");
			writeUtf8(readerCg,
					"package com.neosyn.test.xpkg.rdr;\n"
					+ "network Reader {\n"
					+ "    import com.neosyn.test.xpkg.ent.Cksum;\n"
					+ "    out u8 packet (push);\n"
					+ "    cksum = new Cksum();\n"
					+ "    cksum.reads(t.sum, t.done);\n"
					+ "    t = new task {\n"
					+ "        out u16 sum (push), bool done;\n"
					+ "        void loop() {\n"
					+ "            sum.write(1); done.write(true);\n"
					+ "            packet.write(cksum.computed >> 8);\n"
					+ "        }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, readerCg, outputDir);

			assertFalse("Should not report an unresolved reference for cksum.computed: " + stderr,
					stderr.contains("Unresolved reference"));
			assertTrue("Should generate Reader_t.v: " + stderr,
					Files.isRegularFile(outputDir.resolve(
							"verilog-gen/com/neosyn/test/xpkg/rdr/Reader_t.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * Instantiating a {@code std.mem.SinglePortRAM} must not NPE in the
	 * clock-properties checker (its built-in IR declares a single clock).
	 */
	@Test
	public void singlePortRamInstance_doesNotNpeOnClocks() throws Exception {
		assertBuiltinInstantiates("SinglePortRAM",
				"    ram = new std.mem.SinglePortRAM({size: 256, width: 8});\n"
				+ "    ram.reads(driver.addr);\n");
	}

	/**
	 * Instantiating a {@code std.fifo.SynchronousFIFO} must not NPE in the
	 * clock-properties checker (its built-in IR declares a single clock).
	 */
	@Test
	public void synchronousFifoInstance_doesNotNpeOnClocks() throws Exception {
		assertBuiltinInstantiates("SynchronousFIFO",
				"    fifo = new std.fifo.SynchronousFIFO({size: 16, width: 8});\n"
				+ "    fifo.reads(driver.addr);\n");
	}

	/**
	 * A built-in entity that instantiates another built-in must EMIT that
	 * dependency's Verilog module. {@code std.lib.SynchronizerMux} instantiates
	 * {@code std.lib.SynchronizerFF}; its built-in IR must declare the dependency
	 * so the HDL backend copies SynchronizerFF.v — otherwise iverilog fails with
	 * "Unknown module type: SynchronizerFF" (which blocked the full UDP-over-
	 * Ethernet stack, whose PHY uses the synchronizer for clock-domain crossing).
	 */
	@Test
	public void synchronizerMux_emitsSynchronizerFFDependency() throws Exception {
		Path lspJar = requireLspJar();

		Path projectDir = Files.createTempDirectory("syncmux-dep-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/syncmux");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "syncmux-dep");

			Path topCg = pkgDir.resolve("SmTop.cg");
			writeUtf8(topCg,
					"package com.neosyn.test.syncmux;\n"
					+ "network SmTop {\n"
					+ "    out u6 q (push);\n"
					+ "    sync_mux = new std.lib.SynchronizerMux({width: 6, stages: 2});\n"
					+ "    sync_mux.reads(driver.data);\n"
					+ "    driver = new task {\n"
					+ "        out u6 data;\n"
					+ "        void loop() { data.write(42); }\n"
					+ "    };\n"
					+ "    reader = new task {\n"
					+ "        void loop() { q.write(sync_mux.dout); }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, topCg, outputDir);

			assertFalse("Should not see NullPointerException: " + stderr,
					stderr.contains("NullPointerException"));
			assertTrue("SynchronizerMux.v should be emitted: " + stderr,
					Files.isRegularFile(outputDir.resolve("verilog-gen/std/lib/SynchronizerMux.v")));
			assertTrue("SynchronizerFF.v (SynchronizerMux's declared dependency) should be emitted: " + stderr,
					Files.isRegularFile(outputDir.resolve("verilog-gen/std/lib/SynchronizerFF.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	/**
	 * A parameterized built-in must be instantiated WITH its {@code #(...)}
	 * parameters in the generated Verilog. Most built-in IRs declare no
	 * {@code <variables>}, so the DPN printer must read the parameters from the
	 * instance properties — otherwise the module falls back to its defaults
	 * (width 0), the FIFO's din/dout collapse to ~2 bits and the data path is
	 * silently corrupt (iverilog "Port expects 2 bits, got 8"). Regression for
	 * the whole FIFO/RAM/Synchronizer/DDR family via SynchronousFIFO.
	 */
	@Test
	public void synchronousFifo_emitsWidthAndDepthParameters() throws Exception {
		Path lspJar = requireLspJar();

		Path projectDir = Files.createTempDirectory("fifo-param-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/fifoparam");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "fifo-param");

			Path topCg = pkgDir.resolve("FTop.cg");
			writeUtf8(topCg,
					"package com.neosyn.test.fifoparam;\n"
					+ "network FTop {\n"
					+ "    out u8 q (push);\n"
					+ "    fifo = new std.fifo.SynchronousFIFO({size: 32, width: 8});\n"
					+ "    fifo.reads(driver.d);\n"
					+ "    driver = new task { out u8 d; void loop() { d.write(7); } };\n"
					+ "    reader = new task { void loop() { q.write(fifo.dout); } };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, topCg, outputDir);

			Path ftopV = outputDir.resolve("verilog-gen/com/neosyn/test/fifoparam/FTop.v");
			assertTrue("FTop.v should be generated: " + stderr, Files.isRegularFile(ftopV));
			String v = new String(Files.readAllBytes(ftopV), StandardCharsets.UTF_8);

			assertTrue("SynchronousFIFO must be parameterized with #(...): " + v,
					v.contains("SynchronousFIFO #("));
			assertTrue("FIFO width parameter must propagate as 8: " + v,
					v.contains(".width(8)"));
			assertTrue("FIFO depth parameter must be derived (ceilLog2(32)=5): " + v,
					v.contains(".depth(5)"));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	private void assertBuiltinInstantiates(String label, String instanceDecls) throws Exception {
		Path lspJar = requireLspJar();

		Path projectDir = Files.createTempDirectory("builtin-clock-");
		try {
			Path pkgDir = projectDir.resolve("src/com/neosyn/test/builtinclock");
			Files.createDirectories(pkgDir);
			writeProject(projectDir, "builtin-clock");

			Path topCg = pkgDir.resolve("BuiltinTop.cg");
			writeUtf8(topCg,
					"package com.neosyn.test.builtinclock;\n"
					+ "network BuiltinTop {\n"
					+ instanceDecls
					+ "    driver = new task {\n"
					+ "        out u8 addr (combinational);\n"
					+ "        u8 i;\n"
					+ "        void loop() { addr.write(i); i++; }\n"
					+ "    };\n"
					+ "}\n");

			Path outputDir = projectDir.resolve("out");
			String stderr = runLspGenerate(lspJar, topCg, outputDir);

			assertFalse(label + ": should not NPE on clock properties: " + stderr,
					stderr.contains("NullPointerException"));
			assertFalse(label + ": should not report a null clocks key: " + stderr,
					stderr.contains("keys is null"));
			assertTrue(label + ": should generate BuiltinTop.v: " + stderr,
					Files.isRegularFile(outputDir.resolve(
							"verilog-gen/com/neosyn/test/builtinclock/BuiltinTop.v")));
		} finally {
			deleteRecursive(projectDir);
		}
	}

	// --- helpers ---

	private static void writeProject(Path projectDir, String name) throws IOException {
		writeUtf8(projectDir.resolve(".project"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<projectDescription><name>" + name + "</name></projectDescription>\n");
	}

	private static String runLspGenerate(Path lspJar, Path entryCgFile, Path outputDir)
			throws IOException, InterruptedException {
		return runLspGenerate(lspJar, entryCgFile, outputDir, "verilog");
	}

	private static String runLspGenerate(Path lspJar, Path entryCgFile, Path outputDir, String target)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-jar", lspJar.toString(),
				"generate", entryCgFile.toAbsolutePath().toString(),
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
