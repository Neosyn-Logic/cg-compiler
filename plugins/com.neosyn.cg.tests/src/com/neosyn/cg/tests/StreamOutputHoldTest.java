/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

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
 * TRAPS T117. A task's stream output that leaves the design is read by a consumer the compiler
 * does not generate, and such a consumer may lower {@code ready} on any cycle. The rule for a
 * source (AXI-Stream's among others): once {@code valid} is up it stays up, with the same data,
 * until the cycle {@code valid && ready}. The generated code held a value only while its
 * {@code stall} flag was set, and the flag was computed from {@code ready} at the WRITE -- so a
 * value written while {@code ready} was high and presented while it was low was dropped. Values
 * 0, 3, 5, 8, 10, ... were lost under the pattern below.
 *
 * <p>The generated testbenches never lower {@code ready} that way, so no gate could see it. This
 * test brings its own consumer; the result line is only printed when it finishes.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class StreamOutputHoldTest extends AbstractVerilogCodegenTest {

	private static final String TB = String.join("\n",
			"`timescale 1ns/1ps",
			"module tb;",
			"  reg clock = 0, reset_n = 0;",
			"  wire [7:0] dout; wire dout_valid; reg dout_ready = 0;",
			"  ExternalReady dut(.clock(clock), .reset_n(reset_n), .dout(dout), .dout_valid(dout_valid),",
			"    .dout_ready(dout_ready));",
			"  always #5 clock = ~clock;",
			"  reg [31:0] cyc = 0; reg [7:0] expect_n = 0; integer got = 0, errors = 0;",
			"  reg pend = 0; reg [7:0] pend_data;",
			"  wire [2:0] ph = cyc % 7;",
			"  // ready low on 3 of every 7 cycles, irregularly placed",
			"  always @(negedge clock) dout_ready <= !(ph == 1 || ph == 2 || ph == 5);",
			"  always @(posedge clock) if (reset_n) begin",
			"    cyc <= cyc + 1;",
			"    if (pend && !dout_valid) begin errors = errors + 1;",
			"      $display(\"VALID DROPPED without a transfer at cycle %0d (data %0d)\", cyc, pend_data); end",
			"    if (pend && dout_valid && dout != pend_data) begin errors = errors + 1;",
			"      $display(\"DATA CHANGED while waiting at cycle %0d\", cyc); end",
			"    if (dout_valid && dout_ready) begin",
			"      if (dout !== expect_n) begin errors = errors + 1;",
			"        $display(\"TRANSFER %0d: got %0d, expected %0d\", got, dout, expect_n); end",
			"      expect_n = dout + 1; got = got + 1; pend = 0;",
			"    end else if (dout_valid) begin pend = 1; pend_data = dout; end",
			"    else pend = 0;",
			"    if (got == 60) begin $display(\"RESULT transfers=%0d errors=%0d\", got, errors); $finish; end",
			"    if (cyc > 2000) begin $display(\"RESULT TIMEOUT transfers=%0d\", got); $finish; end",
			"  end",
			"  initial begin #22 reset_n = 1; end",
			"endmodule",
			"");

	@Test
	public void streamOutputHoldsUntilTakenByAnExternalConsumer() throws Exception {
		Assume.assumeTrue("iverilog not available", available("iverilog", "-V"));

		generateNetworks("com/neosyn/test/codegen/ExternalReady.cg", "com.neosyn.test.codegen.ExternalReady");
		File dir = new File(outputPath, "verilog-gen/com/neosyn/test/codegen");
		File tb = new File(outputPath, "external_ready_tb.v");
		Files.write(tb.toPath(), TB.getBytes(StandardCharsets.UTF_8));
		File vvp = new File(outputPath, "external_ready.vvp");

		String build = run(Arrays.asList("iverilog", "-g2012", "-o", vvp.getPath(), "-s", "tb", tb.getPath(),
				new File(dir, "ExternalReady.v").getPath(), new File(dir, "ExternalReady_src.v").getPath()));
		assertTrue("the design and the testbench must elaborate:\n" + build, vvp.isFile());

		String sim = run(Arrays.asList("vvp", vvp.getPath()));
		assertTrue("the consumer must finish its 60 transfers with no violation:\n" + sim,
				sim.contains("RESULT transfers=60 errors=0"));
	}

	private static boolean available(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Runs a command with stdin closed (vvp's $stop would otherwise wait on it), 60 s bound. */
	private static String run(List<String> command) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		Process p = pb.start();
		byte[] out = p.getInputStream().readAllBytes();
		if (!p.waitFor(60, TimeUnit.SECONDS)) {
			p.destroyForcibly();
		}
		return new String(out, StandardCharsets.UTF_8);
	}
}
