/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.cg.cg.Module;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * TRAPS T120. {@code u4 p; p[0] = x[3];} reads a start value {@code p} that the source never set.
 * The simulator reads it as 0 (the rule PhiRemoval applies to a local never set on a path); the HDL
 * declared {@code p} and never drove it, so the three bits the source does not write were X in
 * Verilog and 'U' in VHDL. The Verilog check compares with {@code ===}, so an X fails it rather
 * than slipping past.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class PartialBitsLocalTest extends AbstractVerilogCodegenTest {

	private static final String FIXTURE = "com/neosyn/test/codegen/PartialBitsLocal.cg";

	private static final String DIR = "verilog-gen/com/neosyn/test/codegen/";

	@Test
	public void unwrittenBitsReadZeroInVerilog() throws Exception {
		Assume.assumeTrue("iverilog not available", available("iverilog", "-V"));
		generateNetworks(FIXTURE, "com.neosyn.test.codegen.PartialBitsLocal");
		// bit 3 of the input lands in bit 0; the other three bits must be 0, not X
		String eight = simulate("4'h8");
		assertTrue("bits the source never wrote must read 0, not X:\n" + eight, eight.contains("RESULT ok 0001"));
		String seven = simulate("4'h7");
		assertTrue("bits the source never wrote must read 0, not X:\n" + seven, seven.contains("RESULT ok 0000"));

		String dut = read(DIR + "PartialBitsLocal_dut.v");
		assertFalse("the never-set start value is not declared any more:\n" + dut, dut.contains("reg [3 : 0] p;"));
	}

	private String simulate(String value) throws Exception {
		File tb = new File(outputPath, "partial_bits_tb.v");
		Files.write(tb.toPath(), String.join("\n",
				"`timescale 1ns/1ps",
				"module tb;",
				"  reg clock = 0, reset_n = 0;",
				"  wire [3:0] part; wire part_valid;",
				"  PartialBitsLocal top(.clock(clock), .reset_n(reset_n), .v(" + value + "), .v_valid(1'b1),",
				"    .part(part), .part_valid(part_valid));",
				"  always #5 clock = ~clock;",
				"  initial begin #22 reset_n = 1; end",
				"  always @(posedge clock) if (part_valid === 1'b1) begin",
				// two displays, not a %s of a ternary: iverilog pads the string to the longest option
				"    if (^part === 1'bx) $display(\"RESULT unknown %b\", part); else $display(\"RESULT ok %b\", part);",
				"    $finish;",
				"  end",
				"  initial begin #2000 $display(\"RESULT TIMEOUT\"); $finish; end",
				"endmodule", "").getBytes(StandardCharsets.UTF_8));
		File vvp = new File(outputPath, "partial_bits.vvp");
		vvp.delete();
		String dir = new File(outputPath, DIR).getPath();
		String build = run(Arrays.asList("iverilog", "-g2012", "-o", vvp.getPath(), "-s", "tb", "-y", dir, "-I", dir,
				tb.getPath(), new File(dir, "PartialBitsLocal.v").getPath()));
		assertTrue("the design must elaborate:\n" + build, vvp.isFile());
		String sim = run(Arrays.asList("vvp", "-n", vvp.getPath()));
		assertTrue("the run reaches a verdict:\n" + sim, sim.contains("RESULT ok") || sim.contains("RESULT unknown"));
		return sim;
	}

	private String read(String relative) throws Exception {
		return new String(Files.readAllBytes(Paths.get(outputPath, relative)), StandardCharsets.UTF_8);
	}

	private static boolean available(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Runs a command with stdin closed, 120 s bound. */
	private static String run(List<String> command) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		Process p = pb.start();
		byte[] out = p.getInputStream().readAllBytes();
		if (!p.waitFor(120, TimeUnit.SECONDS)) {
			p.destroyForcibly();
		}
		return new String(out, StandardCharsets.UTF_8);
	}
}
