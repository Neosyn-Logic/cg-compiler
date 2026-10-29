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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TRAPS T116. A comment that ends a line ({@code h.write(0x1234); // identification}) is handed by
 * the parser to the NEXT statement, so it was printed above the wrong code -- the sold IP_Send.v
 * labelled the flags write "identification" and the checksum "ttl and protocol". It was also printed
 * once per IR instruction of the statement (a write is two: the value and its valid). Tasks declared
 * inside a network got no statement comments at all.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogCommentPlacementTest extends AbstractVerilogCodegenTest {

	@Test
	public void eachCommentOnceAboveItsOwnStatement() throws Exception {
		generateNetworks("com/neosyn/test/codegen/CommentPlacement.cg", "com.neosyn.test.codegen.CommentPlacement");
		String v = new String(Files.readAllBytes(Paths.get(outputPath,
				"verilog-gen/com/neosyn/test/codegen/CommentPlacement_src.v")), StandardCharsets.UTF_8);
		check(v, "// identification", "16'h1234");
		check(v, "// flags", "16'h0");
		check(v, "// ttl and protocol", "16'h4011");
	}

	/** The comment appears exactly once, and the first code line after it writes {@code value}. */
	private static void check(String v, String comment, String value) {
		String[] lines = v.split("\\R");
		int count = 0;
		for (int i = 0; i < lines.length; i++) {
			if (!lines[i].trim().equals(comment)) {
				continue;
			}
			count++;
			int j = i + 1;
			while (j < lines.length && lines[j].trim().startsWith("//")) {
				j++;
			}
			assertTrue("'" + comment + "' must sit directly above the write of " + value + ", but is above: "
					+ (j < lines.length ? lines[j].trim() : "<end>") + "\n" + v,
					j < lines.length && lines[j].contains("h <= " + value) || j < lines.length
							&& lines[j].contains("_h <= " + value));
		}
		assertEquals("'" + comment + "' must be printed exactly once\n" + v, 1, count);
	}
}
