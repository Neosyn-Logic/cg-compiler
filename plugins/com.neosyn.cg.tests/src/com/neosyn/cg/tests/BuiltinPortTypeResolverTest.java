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

import org.junit.Test;

import com.google.gson.JsonObject;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.util.BuiltinPortTypeResolver;

/**
 * Guards the width resolution of parameterized built-in ports.
 *
 * Regression: SynchronizerMux/FF bake their DEFAULT const width (16) onto the
 * port instead of the -1 placeholder RAMs/FIFOs use, so an instance's explicit
 * {@code width: 3} was ignored and the connection wire came out 16 bits wide
 * (iverilog "expects 3 bits, got 16 — padding 13 high bits"). The resolver must
 * trust concrete instance arguments over a baked default.
 */
public class BuiltinPortTypeResolverTest {

	private static Type typeInt(int size) {
		TypeInt t = IrFactory.eINSTANCE.createTypeInt();
		t.setSize(size);
		t.setSigned(false);
		return t;
	}

	private static int size(Type t) {
		return ((TypeInt) t).getSize();
	}

	/** The bug: instance width must win over the baked default. */
	@Test
	public void synchronizerMuxHonoursInstanceWidthOverBakedDefault() {
		Type resolved = BuiltinPortTypeResolver.resolvePortType(
				"std.lib.SynchronizerMux", "dout", typeInt(16), -1, 3);
		assertEquals(3, size(resolved));
	}

	/** No width arg: the baked default (16) must be preserved (no regression). */
	@Test
	public void synchronizerMuxKeepsDefaultWhenNoWidthArg() {
		Type resolved = BuiltinPortTypeResolver.resolvePortType(
				"std.lib.SynchronizerMux", "dout", typeInt(16), -1, -1);
		assertEquals(16, size(resolved));
	}

	/** RAM data width still resolves from the width arg (placeholder path). */
	@Test
	public void ramDataResolvesFromWidthArgument() {
		Type resolved = BuiltinPortTypeResolver.resolvePortType(
				"std.mem.SinglePortRAM", "data", typeInt(-1), 1024, 8);
		assertEquals(8, size(resolved));
	}

	/** RAM address width is ceil(log2(size)). */
	@Test
	public void ramAddressResolvesToCeilLog2OfSize() {
		Type resolved = BuiltinPortTypeResolver.resolvePortType(
				"std.mem.SinglePortRAM", "address", typeInt(-1), 1024, 8);
		assertEquals(10, size(resolved));
	}

	/** A non-parameterized 1-bit control port keeps its width. */
	@Test
	public void nonParameterizedPortIsUnchanged() {
		Type resolved = BuiltinPortTypeResolver.resolvePortType(
				"std.fifo.SynchronousFIFO", "empty", typeInt(1), 16, 8);
		assertEquals(1, size(resolved));
	}

	/** DDR primitives' rising/falling ports are width-parameterized too. */
	@Test
	public void demuxDdrRisingFallingResolveFromWidth() {
		assertEquals(4, size(BuiltinPortTypeResolver.resolvePortType(
				"std.lib.DemuxDDR", "rising", typeInt(-1), -1, 4)));
		assertEquals(4, size(BuiltinPortTypeResolver.resolvePortType(
				"std.lib.DemuxDDR", "falling", typeInt(-1), -1, 4)));
	}

	/**
	 * Built-in width args live in instance JSON properties (not IR Arguments).
	 * The instance-based resolver must read them — this is what actually fixed
	 * the 16-bit speed-sync wire.
	 */
	@Test
	public void instanceWidthIsReadFromJsonProperties() {
		Instance inst = DpnFactory.eINSTANCE.createInstance();
		JsonObject props = new JsonObject();
		props.addProperty("entityType", "std.lib.SynchronizerMux");
		props.addProperty("width", 3);
		inst.setProperties(props);

		Port dout = DpnFactory.eINSTANCE.createPort();
		dout.setName("dout");
		dout.setType(typeInt(16)); // baked default

		assertEquals(3, size(BuiltinPortTypeResolver.resolvePortType(inst, dout)));
		assertEquals(3, BuiltinPortTypeResolver.getArgumentValue(inst, "width", -1));
	}
}
