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

import java.util.regex.Pattern;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TRAPS T116. The Verilog back end hoists a widened value into a temporary before truncating it
 * ({@code tmp = i + 1; i <= tmp[3:0];}). Those temporaries were named after constellations --
 * {@code orion}, {@code lyra} -- 5911 of them across the IP cores, none saying what it held.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogTempNamesTest extends AbstractVerilogCodegenTest {

	private static final Pattern CONSTELLATION = Pattern.compile(
			"\\b(orion|lyra|cygnus|draco|aquila|pegasus|perseus|andromeda|phoenix|hydra)\\b");

	@Test
	public void temporaryIsNamedAfterItsValue() throws Exception {
		String v = codeOf("com/neosyn/test/codegen/TempNames.cg", "com.neosyn.test.codegen.TempNames");
		assertTrue("the widened `i + 1` should be held in `i_plus_1`:\n" + v, v.contains("i_plus_1"));
		assertFalse("no constellation-named temporary:\n" + v, CONSTELLATION.matcher(v).find());
	}

	/**
	 * A temporary is declared inside a named block, where it hides any outer name spelled the same.
	 * Spelling `$signed(b)` as `b` hid the port `b` in Divide, and four IP gates failed. No local
	 * may share a name with a port or a module-level register.
	 */
	@Test
	public void temporaryNeverShadowsAnOuterName() throws Exception {
		String v = codeOf("com/neosyn/test/codegen/TempNames.cg", "com.neosyn.test.codegen.TempNoShadow");
		java.util.Set<String> outer = new java.util.HashSet<>();
		java.util.regex.Matcher header = Pattern.compile("(?s)module\\s+\\w+\\((.*?)\\);").matcher(v);
		assertTrue("module header:\n" + v, header.find());
		for (String part : header.group(1).split(",")) {
			String[] words = part.trim().split("\\s+");
			outer.add(words[words.length - 1]);
		}
		java.util.regex.Matcher reg = Pattern.compile("(?m)^\\s{0,4}reg\\s+(?:signed\\s+)?(?:\\[[^\\]]*\\]\\s*)?(\\w+)\\s*;").matcher(v);
		while (reg.find()) {
			outer.add(reg.group(1));
		}
		java.util.regex.Matcher local = Pattern.compile(
				"begin\\s*:\\s*\\w+[^\\n]*\\n((?:\\s*reg\\b[^\\n]*\\n)+)").matcher(v);
		int locals = 0;
		while (local.find()) {
			java.util.regex.Matcher d = Pattern.compile("reg\\s+(?:signed\\s+)?(?:\\[[^\\]]*\\]\\s*)?(\\w+)\\s*;").matcher(local.group(1));
			while (d.find()) {
				locals++;
				assertFalse("block-local `" + d.group(1) + "` shadows a port or module register:\n" + v,
						outer.contains(d.group(1)));
			}
		}
		assertTrue("the fixture should need at least one temporary, or this checks nothing:\n" + v, locals > 0);
	}
}
