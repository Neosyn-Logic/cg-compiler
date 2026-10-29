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
 * TRAPS T116. The header used to print the OS login as Author, a year with no holder as
 * Copyright, "Project : home" (the first segment of an absolute path) and, in VHDL, a {@code .v}
 * file name and "VHDL'93" for VHDL-2008 code. A task declared inside a network got none of its
 * file's comments, so its module always fell back to that template. Now Author and Copyright
 * come from the {@code .cg} source and are left out when it has none.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class GeneratedHeaderTest extends AbstractVerilogCodegenTest {

	private static final String LOGIN = System.getProperty("user.name");

	@Test
	public void headerTakesAuthorAndCopyrightFromTheSource() throws Exception {
		generateNetworks("com/neosyn/test/codegen/HeaderFromSource.cg", "com.neosyn.test.codegen.HeaderFromSource");
		for (String name : new String[] { "HeaderFromSource", "HeaderFromSource_src" }) {
			String v = read("verilog-gen/com/neosyn/test/codegen/" + name + ".v");
			// the inner task (HeaderFromSource_src) is the case that used to lose all of it
			assertTrue(name + ".v must carry the source's copyright holder:\n" + head(v), v.contains("Example Corp"));
			assertTrue(name + ".v must name the source's author:\n" + head(v), v.contains("Author     : Jane Example"));
			assertNoMachineFacts(name + ".v", v);
		}
	}

	@Test
	public void noAuthorOrCopyrightWhenTheSourceHasNone() throws Exception {
		// ExternalReady.cg starts with `package`: no file header comment, no author anywhere.
		generateNetworks("com/neosyn/test/codegen/ExternalReady.cg", "com.neosyn.test.codegen.ExternalReady");
		String v = read("verilog-gen/com/neosyn/test/codegen/ExternalReady_src.v");
		assertFalse("no Author line when the source names none:\n" + head(v), v.contains("Author"));
		assertFalse("no Copyright line when the source has none:\n" + head(v), v.contains("Copyright"));
		assertNoMachineFacts("ExternalReady_src.v", v);
	}

	private static void assertNoMachineFacts(String what, String text) {
		assertFalse(what + " must not name a project derived from a path:\n" + head(text), text.contains("Project    :"));
		if (LOGIN != null && !LOGIN.isEmpty() && !"Jane Example".equals(LOGIN)) {
			assertFalse(what + " must not name the OS login:\n" + head(text), text.contains("Author     : " + LOGIN));
		}
	}

	private String read(String relative) throws Exception {
		return new String(Files.readAllBytes(Paths.get(outputPath, relative)), StandardCharsets.UTF_8);
	}

	private static String head(String text) {
		return text.length() > 900 ? text.substring(0, 900) : text;
	}
}
