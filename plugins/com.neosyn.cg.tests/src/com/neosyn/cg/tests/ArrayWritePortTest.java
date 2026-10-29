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
 * TRAPS T45. An array read and written in the same action -- a register file -- was copied whole
 * into a local array and back in every such action, and written from the task's asynchronous-reset
 * process; either one alone keeps synthesis from mapping it to RAM, so it became flip-flops and
 * multiplexers (the RISC-V core's 32 x 32 register file and two 8-Kbit memories among them).
 * ArrayWritePort gives such an array one registered write port; the checks below are that it is
 * done (text), that nothing a design observes changes (simulation), and that it is now a RAM
 * (Yosys). The checker's own model reads an element back after writing it, so it must keep the
 * old form -- the fixture's control that the transformation is not applied where it is not exact.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class ArrayWritePortTest extends AbstractVerilogCodegenTest {

	private static final String DIR = "verilog-gen/com/neosyn/test/codegen/";

	@Test
	public void arrayWrittenOncePerActionGetsOneWritePort() throws Exception {
		generate();
		String dut = read(DIR + "ArrayWritePort_dut.v");
		assertTrue("one clock-only write of the array:\n" + dut, dut.contains("regs[regs_waddr] <= regs_wdata;"));
		assertFalse("no whole-array local copy:\n" + dut, dut.contains("local_regs"));
		String chk = read(DIR + "ArrayWritePort_chk.v");
		assertTrue("an array read after its write keeps the local copy:\n" + chk, chk.contains("local_model"));
		assertFalse("...and gets no write port:\n" + chk, chk.contains("model_we"));
	}

	@Test
	public void writePortChangesNothingTheDesignObserves() throws Exception {
		Assume.assumeTrue("iverilog not available", available("iverilog", "-V"));
		generate();
		File tb = new File(outputPath, "array_write_port_tb.v");
		Files.write(tb.toPath(), String.join("\n",
				"`timescale 1ns/1ps",
				"module tb;",
				"  reg clock = 0, reset_n = 0;",
				"  ArrayWritePort top(.clock(clock), .reset_n(reset_n));",
				"  always #5 clock = ~clock;",
				"  initial begin #22 reset_n = 1; end",
				"  always @(posedge clock) if (top.chk.finished) begin $display(\"RESULT finished\"); $finish; end",
				"  initial begin #20000 $display(\"RESULT TIMEOUT\"); $finish; end",
				"endmodule", "").getBytes(StandardCharsets.UTF_8));
		File vvp = new File(outputPath, "array_write_port.vvp");
		String dir = new File(outputPath, DIR).getPath();
		String build = run(Arrays.asList("iverilog", "-g2012", "-o", vvp.getPath(), "-s", "tb", "-y", dir, "-I", dir,
				tb.getPath(), new File(dir, "ArrayWritePort.v").getPath()));
		assertTrue("the design must elaborate:\n" + build, vvp.isFile());
		String sim = run(Arrays.asList("vvp", "-n", vvp.getPath()));
		assertFalse("every check passes:\n" + sim, sim.contains("Assertion failed"));
		assertTrue("the checker reaches its last step, with the values of every back end:\n" + sim,
				sim.contains("RESULT finished") && sim.contains("last pair 1cb 34d"));
	}

	@Test
	public void portedArrayIsMappedToRam() throws Exception {
		Assume.assumeTrue("yosys not available", available("yosys", "-V"));
		generate();
		File dut = new File(outputPath, DIR + "ArrayWritePort_dut.v");
		String log = run(Arrays.asList("yosys", "-p",
				"read_verilog -sv " + dut.getPath() + "; synth_xilinx -top ArrayWritePort_dut -flatten"));
		assertFalse("the array must not be broken into registers:\n" + log, log.contains("list of registers"));
		assertTrue("the array must be mapped to a RAM:\n" + log, log.contains("mapping memory ArrayWritePort_dut.regs via"));
	}

	/**
	 * An array read at a constant index (AES's {@code win[Nk - 1]}): Verilog ports it (Yosys still
	 * maps it to RAM), VHDL leaves it -- GHDL's synthesis keeps no memory with a constant-address
	 * read, so a port would only add registers.
	 */
	@Test
	public void arrayReadAtAConstantIndexIsPortedInVerilog() throws Exception {
		generateNetworks("com/neosyn/test/codegen/ArrayConstantRead.cg", "com.neosyn.test.codegen.ArrayConstantRead");
		String v = read(DIR + "ArrayConstantRead_tab.v");
		assertTrue("Verilog: a write port:\n" + v, v.contains("cells[cells_waddr] <= cells_wdata;"));
	}

	private void generate() throws Exception {
		generateNetworks("com/neosyn/test/codegen/ArrayWritePort.cg", "com.neosyn.test.codegen.ArrayWritePort");
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
