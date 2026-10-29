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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.cg.cg.Module;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * TRAPS T116. What a reader had to wade through in every generated task: `if to_boolean('1') then`
 * (a VHDL `if` that always holds, 2659 times), empty `begin // line N end` blocks and `else`
 * branches (about 1400), and `// line 0` comments on actions with no source line (1390). And an
 * `if` inside a body whose condition folded to a constant: 92 in each language (S187).
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class GeneratedNoiseTest extends AbstractVerilogCodegenTest {

	private static final Pattern EMPTY_BLOCK = Pattern.compile("begin(?: : \\w+)?(?: // line \\d+)?\\s*\\n\\s*end\\b");

	private static final Pattern CONSTANT_IF_V = Pattern.compile("^\\s*(?:end else )?if \\(1'b[01]\\)", Pattern.MULTILINE);

	@Test
	public void verilogHasNoEmptyBlocksOrLineZero() throws Exception {
		generateNetworks("com/neosyn/test/codegen/NoiseFree.cg", "com.neosyn.test.codegen.NoiseFree");
		String v = read("verilog-gen/com/neosyn/test/codegen/NoiseFree_txTask.v");
		assertTrue("fixture sanity: the task has an FSM", v.contains("case (FSM)"));
		assertFalse("empty begin/end block:\n" + v, EMPTY_BLOCK.matcher(v).find());
		assertFalse("`// line 0`:\n" + v, v.contains("// line 0"));
	}

	/** An if/elsif/while line that still converts: `to_std_logic((...))` in an ASSIGNMENT is fine. */
	private static final Pattern CONDITION_WITH_CONVERSION = Pattern.compile(
			"^\\s*(?:(?:els)?if|while) .*to_(?:boolean|std_logic)\\(", Pattern.MULTILINE);

	/**
	 * An `if` inside a body whose condition the compiler folded to a constant (RISC-V's
	 * `if (!mmio_stall)`) is printed as the branch that runs, with no test and no dead branch.
	 * Code lines only: the file header quotes the fixture's comment.
	 */
	@Test
	public void verilogHasNoConstantIf() throws Exception {
		generateNetworks("com/neosyn/test/codegen/ConstantIf.cg", "com.neosyn.test.codegen.ConstantIf");
		String v = read("verilog-gen/com/neosyn/test/codegen/ConstantIf_dut.v");
		assertTrue("fixture sanity: the task computes v + 2\n" + v, v.contains("8'h2)"));
		assertFalse("a test of a constant:\n" + v, CONSTANT_IF_V.matcher(v).find());
		assertFalse("the branch that never runs (v + 100):\n" + v, v.contains("8'h64"));
		assertFalse("the branch that never runs (counter = 99):\n" + v, v.contains("8'h63"));
	}

	/**
	 * The same in Verilog, for ITS keywords -- and `EXPECT`/`CHECKS` are not among them: Verilog
	 * is case-SENSITIVE, so an upper-cased constant was being escaped for no reason at all.
	 */
	@Test
	public void verilogRenamesKeywordsInsteadOfEscaping() throws Exception {
		generateNetworks("com/neosyn/test/codegen/ReservedNames.cg", "com.neosyn.test.codegen.ReservedNames");
		// comments stripped: the fixture's own doc comment SAYS `\reg `, and the header carries it
		String task = code(read("verilog-gen/com/neosyn/test/codegen/ReservedNames_bus.v"));
		String net = code(read("verilog-gen/com/neosyn/test/codegen/ReservedNames.v"));

		assertTrue("fixture sanity: the task has a skid buffer:\n" + task,
				task.contains("internal_ReservedNames_reg"));
		assertTrue("the port `reg` renamed:\n" + net, net.contains("input [7 : 0] reg_v"));
		assertTrue("the instance reads the port under its declared name:\n" + net,
				net.contains(".ReservedNames_reg(reg_v)"));
		assertTrue("`type` renamed past the declared `type_v`:\n" + task, task.contains("type_v2"));
		assertTrue("the register `packed` renamed:\n" + task, task.contains("packed_v"));
		assertTrue("an upper-cased name is not a Verilog keyword:\n" + task,
				task.contains("localparam [7 : 0] CHECKS"));

		// `body` and `mod` are VHDL keywords only, so Verilog keeps the author's names.
		assertTrue("`body` is no Verilog keyword and keeps its name:\n" + net,
				net.contains("output [7 : 0] body"));

		assertFalse("an escaped identifier is left:\n" + task, task.contains("\\"));
		assertFalse("an escaped identifier is left:\n" + net, net.contains("\\"));
	}

	/**
	 * TRAPS T118: a task at the top level keeps its own port names, and the skid buffer the
	 * Verilog backend writes by hand used to read the port RAW -- `internal_reg <= reg;` beside a
	 * port declared `\reg `. That is not an escaping problem but a SYNTAX error: iverilog stopped
	 * at "Type names are not valid expressions here" and the module did not compile at all.
	 */
	@Test
	public void verilogRenamesAKeywordPortOfATask() throws Exception {
		generateNetworks("com/neosyn/test/codegen/ReservedPorts.cg", "com.neosyn.test.codegen.ReservedPorts");
		String v = code(read("verilog-gen/com/neosyn/test/codegen/ReservedPorts.v"));

		assertTrue("fixture sanity: the task has a skid buffer:\n" + v, v.contains("internal_reg "));
		assertTrue("the port `reg` renamed:\n" + v, v.contains("input [7 : 0] reg_v"));
		assertTrue("the skid buffer reads the port by its declared name:\n" + v,
				v.contains("internal_reg <= reg_v;"));
		assertFalse("an escaped identifier is left:\n" + v, v.contains("\\"));
	}

	private static final Pattern INCREMENT = Pattern.compile(
			"^\\s*counter\\s*<= \\(counter \\+ 1\\);", Pattern.MULTILINE);

	/**
	 * F7: an FSM whose state register has encodings that are not states gets a synthesis-visible
	 * default that returns to the initial state. `$stop` stays, inside translate_off, so a
	 * simulation still stops where reaching it means the design is wrong.
	 */
	@Test
	public void verilogFsmHasASafeState() throws Exception {
		generateNetworks("com/neosyn/test/codegen/SafeState.cg", "com.neosyn.test.codegen.SafeState");
		String v = read("verilog-gen/com/neosyn/test/codegen/SafeState_dut.v");

		assertTrue("fixture sanity: three states in a two-bit register:\n" + v,
				v.contains("reg [1 : 0] FSM;") && v.contains("localparam third"));
		assertTrue("the default returns to the initial state:\n" + v, SAFE_STATE.matcher(v).find());
		assertTrue("$stop is kept for simulation:\n" + v, v.contains("$stop;"));
	}

	/** The other side: every encoding is a state, so a safe state would be dead code. */
	@Test
	public void verilogFsmWithNoSpareEncodingHasNoSafeState() throws Exception {
		generateNetworks("com/neosyn/test/codegen/NoiseFree.cg", "com.neosyn.test.codegen.NoiseFree");
		String v = read("verilog-gen/com/neosyn/test/codegen/NoiseFree_txTask.v");

		assertTrue("fixture sanity: four states in a two-bit register:\n" + v,
				v.contains("reg [1 : 0] FSM;") && v.contains("localparam sendData"));
		assertFalse("a safe state that can never be reached:\n" + v, SAFE_STATE.matcher(v).find());
	}

	/** `default:` opening a block that ends by assigning the state register. */
	private static final Pattern SAFE_STATE = Pattern.compile(
			"default: begin.*?FSM <= \\w+;\\s*\\n\\s*end", Pattern.DOTALL);

	/**
	 * TRAPS T119: an input port the network connects to nothing is DRIVEN TO ZERO, not left out
	 * of the port map. Verilog does not require an input to be connected -- it floats the pin and
	 * reads X -- so the same design was deterministic in VHDL, which has always tied these to
	 * '0', and X-propagating in Verilog. 115 pins over 50 files, found by linting.
	 */
	@Test
	public void verilogDrivesAnUnconnectedInput() throws Exception {
		generateUnvalidated(verilogGenerator, "UnconnectedInput");
		String v = read("verilog-gen/com/neosyn/test/codegen/UnconnectedInput.v");

		assertTrue("fixture sanity: the child is instantiated:\n" + v,
				v.contains("UnconnectedInput_dut dut ("));
		assertTrue("the unconnected input is driven to zero:\n" + v, v.contains(".spare(8'b0)"));
		assertTrue("its valid companion is driven too:\n" + v, v.contains(".spare_valid(1'b0)"));
	}

	/**
	 * Generates one language for a fixture the VALIDATOR rejects but the compiler accepts. An
	 * unconnected input is reported at ERROR severity, so `assertOk` refuses it, while the CLI
	 * prints it as a warning and generates anyway -- which is exactly how the real designs that
	 * carry one (Rv32iCpu's `io_rdata` inside TestRvChecksum) are built.
	 *
	 * <p>It still VALIDATES, and only skips the assertion: validating is what primes the
	 * instantiator, and without it {@code doGenerate} dies on a null {@code InstantiatorData}.
	 * One language per call, because the generators transform the IR in place and a second pass
	 * over the same tree would see it half-transformed.
	 */
	private void generateUnvalidated(ICodeGenerator generator, String name) throws Exception {
		Module module = getModule("com/neosyn/test/codegen/" + name + ".cg");
		tester.validate(module);
		getInjector().getInstance(IGenerator.class).doGenerate(module.eResource(), access);
		Resource ir = resourceSet.getResource(access.getURI("com/neosyn/test/codegen/" + name + ".ir"), true);
		Entity top = (Entity) ir.getContents().get(0);
		generator.setOutputFolder(outputPath);
		generator.prepare(top);
		for (Entity e : DpnFactory.eINSTANCE.collectEntities(top)) {
			generator.transform(e);
			generator.print(e);
		}
	}

	/** Code lines only: a header quotes the fixture's doc comment, which talks ABOUT escaped names. */
	private static String code(String generated) {
		return generated.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)(//|--).*$", "");
	}

	private String read(String relative) throws Exception {
		return new String(Files.readAllBytes(Paths.get(outputPath, relative)), StandardCharsets.UTF_8);
	}
}
