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
 * Verifies that a {@code for} loop with a compile-time-constant trip count is
 * unrolled into straight-line IR (so it lowers to synthesizable Verilog with no
 * {@code while}), while a data-dependent loop still falls back to a {@code while}.
 *
 * <p>Value-correctness of the unrolled output is gated elsewhere — the
 * {@code ComputationForLoop} / {@code BarrelShifter} fixtures in
 * {@code BytecodePassTests} / {@code IverilogPassTests} carry expected-result
 * vectors and exercise the same unroll path.
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class ConstantLoopUnrollTest extends AbstractCxTest {

	@Inject
	@Named(VERILOG)
	private ICodeGenerator verilogGenerator;

	/** A constant-bound loop unrolls — no `while` survives. */
	@Test
	public void testConstantLoopIsUnrolled() throws Exception {
		String v = codeOf("golden/GoldenLoopUnroll.cg", "com.neosyn.test.golden.ConstUnroll");
		assertFalse("constant-bound for loop must not emit a `while`", v.contains("while"));
		// The eight iterations collapse to straight-line code that the backend
		// constant-folds; the final write is still present.
		assertTrue("the loop result should still be written", v.contains("dout <="));
	}

	/**
	 * A data-dependent loop is not unrollable: the loop condition must survive as
	 * a runtime comparison against the input-derived bound (a scheduler-lowered
	 * FSM back-edge). Were it wrongly unrolled, the index/bound comparison would
	 * be constant-folded away.
	 */
	@Test
	public void testDataDependentLoopIsNotUnrolled() throws Exception {
		String v = codeOf("golden/GoldenLoopUnroll.cg", "com.neosyn.test.golden.DataDepLoop")
				.replaceAll("\\s+", " ");
		assertTrue("data-dependent loop must keep its runtime `k < bound` test",
				v.contains("_a_k < FSM_DataDepLoop_a_bound"));
	}

	/** Generated Verilog for one entity, with comments stripped. */
	private String codeOf(String sourceFile, String entityName) throws Exception {
		String raw = generateVerilog(sourceFile, entityName);
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
