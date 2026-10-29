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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TRAPS T121. {@code assert(c)} was printed as {@code if (~(c))}; on X that is an {@code if} on X,
 * which takes the else branch, so an assertion over an unknown value never failed. A design whose
 * checked output was X on every value passed its generated test. The input is driven from outside,
 * so the X does not depend on any other compiler defect: to X the assertion must fire, to 5 it must
 * not -- without the second run a check that always failed would pass this test.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogAssertOnUnknownTest extends AbstractVerilogCodegenTest {

	private static final String DIR = "verilog-gen/com/neosyn/test/codegen/";

	@Test
	public void assertionOnAnUnknownValueFails() throws Exception {
		Assume.assumeTrue("iverilog not available", available("iverilog", "-V"));
		generateNetworks("com/neosyn/test/codegen/AssertOnUnknown.cg", "com.neosyn.test.codegen.AssertOnUnknown");

		String x = simulate("8'bx");
		assertTrue("an assertion whose condition is X must fail:\n" + x, x.contains("Assertion failed"));
		assertFalse("...and stop the run:\n" + x, x.contains("RESULT finished"));

		String five = simulate("8'd5");
		assertFalse("the control: a condition that holds must not fail:\n" + five, five.contains("Assertion failed"));
		assertTrue("the control run reaches its end:\n" + five, five.contains("RESULT finished"));
	}

	private String simulate(String value) throws Exception {
		File tb = new File(outputPath, "assert_on_unknown_tb.v");
		Files.write(tb.toPath(), String.join("\n",
				"`timescale 1ns/1ps",
				"module tb;",
				"  reg clock = 0, reset_n = 0;",
				"  AssertOnUnknown top(.clock(clock), .reset_n(reset_n), .v(" + value + "), .v_valid(1'b1));",
				"  always #5 clock = ~clock;",
				"  initial begin #22 reset_n = 1; #100 $display(\"RESULT finished\"); $finish; end",
				"endmodule", "").getBytes(StandardCharsets.UTF_8));
		File vvp = new File(outputPath, "assert_on_unknown.vvp");
		vvp.delete();
		String dir = new File(outputPath, DIR).getPath();
		String build = run(Arrays.asList("iverilog", "-g2012", "-o", vvp.getPath(), "-s", "tb", "-y", dir, "-I", dir,
				tb.getPath(), new File(dir, "AssertOnUnknown.v").getPath()));
		assertTrue("the design must elaborate:\n" + build, vvp.isFile());
		return run(Arrays.asList("vvp", "-n", vvp.getPath()));
	}

	private static boolean available(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Runs a command with stdin closed (vvp's $stop would otherwise wait on it), 120 s bound. */
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
