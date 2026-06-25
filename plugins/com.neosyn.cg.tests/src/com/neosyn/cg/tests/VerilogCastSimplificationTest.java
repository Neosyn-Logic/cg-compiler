/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.core.ICoreConstants.FILE_EXT_IR;
import static com.neosyn.neosynide.NeosynIdeModule.VERILOG;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.cg.cg.Module;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * Verifies the Verilog backend does not emit redundant nested sign
 * reinterpretations such as {@code $unsigned($unsigned(x))} or
 * {@code $signed($signed(x))}. Only the outermost {@code $signed}/{@code
 * $unsigned} affects the value in its enclosing context, so directly-nested
 * ones are dead weight (peephole in {@code VerilogExpressionPrinter}).
 *
 * @see GoldenFileTests
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class VerilogCastSimplificationTest extends AbstractCxTest {

	@Inject
	@Named(VERILOG)
	private ICodeGenerator verilogGenerator;

	/** (a) No directly-nested $signed/$unsigned reinterpretations. */
	@Test
	public void testNoNestedSignConversions() throws Exception {
		String v = codeOf("golden/GoldenCasts.cg", "com.neosyn.test.golden.CastNest");

		assertFalse("redundant nested $unsigned($unsigned(...))", v.contains("$unsigned($unsigned"));
		assertFalse("redundant nested $signed($signed(...))", v.contains("$signed($signed"));
		assertFalse("redundant nested $unsigned($signed(...))", v.contains("$unsigned($signed"));
		assertFalse("redundant nested $signed($unsigned(...))", v.contains("$signed($unsigned"));

		// Sanity: the fixture genuinely drives sign conversions, so the
		// assertions above are not vacuously true.
		assertTrue("fixture should still emit at least one sign conversion",
				v.contains("$unsigned(") || v.contains("$signed("));
	}

	/**
	 * (d) No low-bits mask kept right before a truncation. The fixture's only
	 * 0xFF is the mask in {@code (u8)((row*32+col) & 0xFF)}; truncating to
	 * {@code [7:0]} already discards everything above bit 7, so the mask must be
	 * dropped — and 0xFF must therefore not appear in the generated code.
	 */
	@Test
	public void testNoRedundantLowMaskBeforeTruncate() throws Exception {
		String v = codeOf("golden/GoldenCasts.cg", "com.neosyn.test.golden.CastNest");
		assertFalse("redundant low-bits mask survived a truncation", v.contains("hff"));
	}

	/**
	 * (b) Arithmetic whose result is truncated must compute on narrow operands.
	 * {@code (u8)((acc*32) & 0xFF)} for a 32-bit state {@code acc} must narrow
	 * to an 8-bit multiply, not a 38-bit sign-extended one.
	 */
	@Test
	public void testTruncatedArithmeticIsNarrowed() throws Exception {
		String v = codeOf("golden/GoldenCasts.cg", "com.neosyn.test.golden.WideArith");
		assertFalse("wide sign-extended multiply (38-bit) survived narrowing", v.contains("* 38'"));
		assertFalse("operand sign-extension replication survived narrowing", v.contains("{{"));
		assertTrue("multiply should use an 8-bit constant", v.contains("* 8'sh20"));
		assertTrue("operand should be truncated to 8 bits", v.contains("acc[7 : 0]"));
	}

	/** Generated Verilog for one entity, with comments stripped. */
	private String codeOf(String sourceFile, String entityName) throws Exception {
		String raw = generateVerilog(sourceFile, entityName);
		// Generated HDL carries source comments through; assert only on real code.
		return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
	}

	/** Compile a fixture and return the generated Verilog for one entity. */
	private String generateVerilog(String sourceFile, String entityName) throws Exception {
		Module module = getModule(sourceFile);
		assertOk(module);

		IGenerator generator = getInjector().getInstance(IGenerator.class);
		generator.doGenerate(module.eResource(), access);

		String irPath = entityName.replace('.', '/') + "." + FILE_EXT_IR;
		URI irUri = access.getURI(irPath);
		Resource irResource = resourceSet.getResource(irUri, true);
		Entity entity = (Entity) irResource.getContents().get(0);

		verilogGenerator.setOutputFolder(outputPath);
		for (Entity anEntity : DpnFactory.eINSTANCE.collectEntities(entity)) {
			verilogGenerator.transform(anEntity);
			verilogGenerator.print(anEntity);
		}

		String verilogPath = Paths.get(outputPath, "verilog-gen",
				entityName.replace('.', '/').replace("/", java.io.File.separator) + ".v").toString();
		return new String(Files.readAllBytes(Paths.get(verilogPath)), StandardCharsets.UTF_8);
	}
}
