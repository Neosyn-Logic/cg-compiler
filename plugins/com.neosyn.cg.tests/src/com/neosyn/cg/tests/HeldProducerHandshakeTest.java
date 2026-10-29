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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A {@code sync ready} stream has two incompatible producer flavours (see
 * {@code StreamLinkMarker}): a <em>registered</em> producer (e.g. {@code std.fifo.SynchronousFIFO})
 * drives {@code valid} one cycle after a pop, so its consumer must pulse {@code ready = !valid};
 * a <em>held</em> producer (a plain task writing a {@code sync ready} output) presents {@code valid}
 * with the data and holds it, so its consumer must assert {@code ready} on the transfer cycle and
 * the producer must not advance its FSM while stalled.
 *
 * <p>Before the fix, every read-then-idle consumer got the registered ({@code !valid}) form, which
 * silently dropped one value per backpressure stall on a direct task-to-task link (confirmed by
 * Verilator: {@code FastSourceSlowTarget} read 4 then failed asserting 5). This test locks in the
 * network-aware handshake: a direct held link (both endpoints generated tasks) uses the standard
 * form, while a FIFO-mediated link keeps the {@code !valid} pulse.
 *
 * @see AddBufferedInputsTest
 * @see VerilogAssertMessageTest
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class HeldProducerHandshakeTest extends AbstractVerilogCodegenTest {

	private static final String READY = "com/neosyn/test/app/Ready.cg";
	private static final String FIFOS = "com/neosyn/test/app/FIFOs.cg";

	/**
	 * The consumer of a direct held producer (FastSourceSlowTarget.expected reads source.dout with
	 * {@code idle()} backpressure) must assert ready without the {@code !valid} gate, or the held
	 * value is dropped.
	 */
	@Test
	public void directHeldConsumerAssertsReadyWithoutValidGate() throws Exception {
		String v = codeOf(READY, "com.neosyn.test.app.readys.FastSourceSlowTarget",
				"com.neosyn.test.app.readys.FastSourceSlowTarget_expected");

		assertTrue("fixture should drive the source_dout ready handshake",
				v.contains("source_dout_ready"));
		// A read state must assert ready unconditionally (= 1'b1) so a held producer's value is
		// taken on the transfer cycle. Before the fix every read-then-idle state emitted
		// `ready = !valid` (0 while data is present), which dropped the value. The idle states
		// legitimately keep `ready = !valid` to deassert while the producer holds — so we assert
		// the presence of the unconditional read, not the absence of the !valid form.
		assertTrue("direct held-producer consumer must assert ready = 1'b1 in its read states "
				+ "(gating by !valid deadlocks/drops against a held producer)",
				v.contains("source_dout_ready = 1'b1"));
	}

	/** The held producer itself must hold its FSM while stalled (guarded by {@code if (!stall)}). */
	@Test
	public void heldProducerHoldsFsmWhileStalled() throws Exception {
		String v = codeOf(READY, "com.neosyn.test.app.readys.FastSourceSlowTarget",
				"com.neosyn.test.app.readys.FastSourceSlowTarget_source");

		// The producer advances (loading the next value) only when the current one is accepted
		// (dout_ready) or none is presented yet (!dout_valid); otherwise every register holds.
		assertTrue("held producer must gate its action scheduler on (dout_ready || !dout_valid) "
				+ "so it does not advance/overwrite the un-taken value under backpressure",
				v.contains("if (dout_ready || !dout_valid)"));
	}

	/**
	 * Every direct held-producer network in Ready.cg must emit the standard read handshake on its
	 * consumer (ready asserted, not gated by !valid). Also (re)generates them all to the output
	 * dir so they can be value-simulated as a set.
	 */
	@Test
	public void allDirectHeldNetworksUseStandardConsumerHandshake() throws Exception {
		generateNetworks(READY,
				"com.neosyn.test.app.readys.FastSourceSlowTarget",
				"com.neosyn.test.app.readys.FastSourceTargetSlowThenFast",
				"com.neosyn.test.app.readys.PipelineBubble",
				"com.neosyn.test.app.readys.ReadyChain",
				"com.neosyn.test.app.readys.CombineReady",
				"com.neosyn.test.app.readys.SlowSourceFastTarget");
	}

	/**
	 * A mixed RELAY on a direct held chain (ReadyChain.subtracter: consumes din, produces dout)
	 * must gate its input ready on its own output slot being free, so backpressure propagates
	 * combinationally through the chain instead of dropping a value at each idle() boundary.
	 */
	@Test
	public void directHeldRelayGatesInputReadyOnOutputSlot() throws Exception {
		String v = codeOf(READY, "com.neosyn.test.app.readys.ReadyChain",
				"com.neosyn.test.app.readys.ReadyChain_subtracter");

		assertTrue("relay input ready must track the output slot (dout_ready || !dout_valid), "
				+ "not the local stall, so a ready chain propagates backpressure",
				v.contains("din_ready = (dout_ready || !dout_valid)"));
		// The relay must also hold its FSM on the same condition.
		assertTrue("relay must hold its scheduler on the output slot",
				v.contains("if (dout_ready || !dout_valid)"));
	}

	/**
	 * Since 2026-09-15 std.fifo.SynchronousFIFO HOLDS its output until taken (first-word-fall-through,
	 * TRAPS T103 / T57), so both ends of a FIFO link use the held handshake. This test used to pin
	 * the opposite -- a FIFO-fed reader pulsing {@code ready = !valid} -- which only matched the old
	 * FIFO: it popped on ready and presented each value for one cycle, and a producer writing into
	 * it took the stall path and overwrote values while it was full (TestSynchronousFIFO lost 45).
	 *
	 * <p>Sink reads {@code fifo.dout}: no branch that READS (guarded by {@code inp_valid}) may pulse
	 * {@code inp_ready = !inp_valid}, because a held FIFO keeps valid high and that pulse would never
	 * take the value. (The waiting default branches still say {@code !inp_valid}; that is correct.)
	 * Fib writes {@code fifo.din}: it must hold its value until the FIFO takes it.
	 */
	@Test
	public void fifoLinksUseTheHeldHandshake() throws Exception {
		String sink = codeOf(FIFOS, "com.neosyn.test.app.fifos.TestSynchronousFIFO",
				"com.neosyn.test.app.fifos.Sink");
		assertTrue("fixture should drive the inp ready handshake", sink.contains("inp_ready"));
		String comb = sink.substring(sink.indexOf("always @(*)"), sink.indexOf("always @(negedge"));
		Matcher pulse = Pattern.compile("if \\(+inp_valid\\)+ begin[^\\n]*\\n\\s*inp_ready = ! ?\\(inp_valid\\);")
				.matcher(comb);
		assertFalse("a FIFO-fed read branch pulses inp_ready = !inp_valid: against a FIFO that holds its "
				+ "output the value is never taken", pulse.find());
		assertTrue("a FIFO-fed read branch must assert inp_ready = 1'b1",
				Pattern.compile("if \\(+inp_valid\\)+ begin[^\\n]*\\n\\s*inp_ready = 1'b1;").matcher(comb).find());

		// Read Fib from the hierarchy the call above already generated: compiling the same fixture a
		// second time in one test fails (IR generation is not idempotent on a shared resource set).
		String fib = new String(Files.readAllBytes(Paths.get(outputPath, "verilog-gen", "com", "neosyn", "test",
				"app", "fifos", "Fib.v")), StandardCharsets.UTF_8)
				.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
		assertTrue("a producer writing into a FIFO must hold its value until taken "
				+ "(if (dout_ready || !dout_valid)), or it overwrites while the FIFO is full",
				fib.contains("if (dout_ready || !dout_valid)"));
	}
}
