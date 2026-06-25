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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Module;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * Golden file comparison tests for IR and HDL generation.
 *
 * These tests compare generated output against known-good "golden" files to detect
 * regressions in code generation. If the generated output differs from the golden
 * file, the test fails, indicating a potential regression.
 *
 * Usage:
 * 1. First run with UPDATE_GOLDEN_FILES=true to generate initial golden files
 * 2. Commit the golden files to version control
 * 3. Run tests normally - they will compare against golden files
 * 4. If generation changes intentionally, re-run with UPDATE_GOLDEN_FILES=true
 *
 * @see AbstractCxTest
 */
@InjectWith(CustomCgInjectorProvider.class)
@RunWith(XtextRunner.class)
public class GoldenFileTests extends AbstractCxTest {

	/** Set to true to update golden files instead of comparing */
	private static final boolean UPDATE_GOLDEN_FILES = Boolean.getBoolean("neosyn.updateGoldenFiles");

	/** Base path for golden files */
	private static final String GOLDEN_BASE = "tests/golden";

	@Inject
	@Named(VERILOG)
	private ICodeGenerator verilogGenerator;

	// =========================================================================
	// GoldenSimple.cg - Original simple tests
	// =========================================================================

	@Test
	public void testIR_GoldenSimple_Counter() throws Exception {
		testIrGeneration("GoldenSimple", "com.neosyn.test.golden.Counter");
	}

	@Test
	public void testIR_GoldenSimple_Adder() throws Exception {
		testIrGeneration("GoldenSimple", "com.neosyn.test.golden.Adder");
	}

	@Test
	public void testIR_GoldenSimple_SimpleNetwork() throws Exception {
		testIrGeneration("GoldenSimple", "com.neosyn.test.golden.SimpleNetwork");
	}

	@Test
	public void testVerilog_GoldenSimple_Counter() throws Exception {
		testVerilogGeneration("GoldenSimple", "com.neosyn.test.golden.Counter");
	}

	@Test
	public void testVerilog_GoldenSimple_Adder() throws Exception {
		testVerilogGeneration("GoldenSimple", "com.neosyn.test.golden.Adder");
	}

	// =========================================================================
	// GoldenBasicTasks.cg - Basic task patterns
	// =========================================================================

	@Test
	public void testIR_BasicTasks_BlankingLed() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.BlankingLed");
	}

	@Test
	public void testIR_BasicTasks_Counter8() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.Counter8");
	}

	@Test
	public void testIR_BasicTasks_CounterWithLimit() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.CounterWithLimit");
	}

	@Test
	public void testIR_BasicTasks_Adder8() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.Adder8");
	}

	@Test
	public void testIR_BasicTasks_Multiplier8() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.Multiplier8");
	}

	@Test
	public void testIR_BasicTasks_BitManip() throws Exception {
		testIrGeneration("GoldenBasicTasks", "com.neosyn.test.golden.BitManip");
	}

	@Test
	public void testVerilog_BasicTasks_BlankingLed() throws Exception {
		testVerilogGeneration("GoldenBasicTasks", "com.neosyn.test.golden.BlankingLed");
	}

	@Test
	public void testVerilog_BasicTasks_Counter8() throws Exception {
		testVerilogGeneration("GoldenBasicTasks", "com.neosyn.test.golden.Counter8");
	}

	@Test
	public void testVerilog_BasicTasks_Adder8() throws Exception {
		testVerilogGeneration("GoldenBasicTasks", "com.neosyn.test.golden.Adder8");
	}

	// =========================================================================
	// GoldenCombinational.cg - Combinational logic patterns
	// =========================================================================

	@Test
	public void testIR_Combinational_SimpleALU() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.SimpleALU");
	}

	@Test
	public void testIR_Combinational_Comparator() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.Comparator");
	}

	@Test
	public void testIR_Combinational_Mux4to1() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.Mux4to1");
	}

	@Test
	public void testIR_Combinational_PriorityEncoder() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.PriorityEncoder");
	}

	@Test
	public void testIR_Combinational_Decoder3to8() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.Decoder3to8");
	}

	@Test
	public void testVerilog_Combinational_SimpleALU() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.SimpleALU");
	}

	@Test
	public void testVerilog_Combinational_Mux4to1() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.Mux4to1");
	}

	@Test
	public void testVerilog_Combinational_PriorityEncoder() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.PriorityEncoder");
	}

	@Test
	public void testVerilog_Combinational_Decoder3to8() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.Decoder3to8");
	}

	// Regression tests for RouteAddr bug (Session 48/49)
	// Multiple independent if-else chains were generating empty branches

	@Test
	public void testIR_Combinational_MultiChainCombinational() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.MultiChainCombinational");
	}

	@Test
	public void testVerilog_Combinational_MultiChainCombinational() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.MultiChainCombinational");
	}

	@Test
	public void testIR_Combinational_NestedIfCombinational() throws Exception {
		testIrGeneration("GoldenCombinational", "com.neosyn.test.golden.NestedIfCombinational");
	}

	@Test
	public void testVerilog_Combinational_NestedIfCombinational() throws Exception {
		testVerilogGeneration("GoldenCombinational", "com.neosyn.test.golden.NestedIfCombinational");
	}

	// =========================================================================
	// GoldenFSM.cg - FSM and state machine patterns
	// =========================================================================

	@Test
	public void testIR_FSM_GCD() throws Exception {
		testIrGeneration("GoldenFSM", "com.neosyn.test.golden.GCD");
	}

	@Test
	public void testIR_FSM_TrafficLight() throws Exception {
		testIrGeneration("GoldenFSM", "com.neosyn.test.golden.TrafficLight");
	}

	@Test
	public void testIR_FSM_SequenceDetector() throws Exception {
		testIrGeneration("GoldenFSM", "com.neosyn.test.golden.SequenceDetector");
	}

	@Test
	public void testIR_FSM_SerialReceiver() throws Exception {
		testIrGeneration("GoldenFSM", "com.neosyn.test.golden.SerialReceiver");
	}

	@Test
	public void testIR_FSM_Debouncer() throws Exception {
		testIrGeneration("GoldenFSM", "com.neosyn.test.golden.Debouncer");
	}

	@Test
	public void testVerilog_FSM_GCD() throws Exception {
		testVerilogGeneration("GoldenFSM", "com.neosyn.test.golden.GCD");
	}

	@Test
	public void testVerilog_FSM_SequenceDetector() throws Exception {
		testVerilogGeneration("GoldenFSM", "com.neosyn.test.golden.SequenceDetector");
	}

	// =========================================================================
	// GoldenNetworks.cg - Network composition patterns
	// =========================================================================

	@Test
	public void testIR_Networks_Producer() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.Producer");
	}

	@Test
	public void testIR_Networks_Consumer() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.Consumer");
	}

	@Test
	public void testIR_Networks_BasicPipeline() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.BasicPipeline");
	}

	@Test
	public void testIR_Networks_InlineTaskNetwork() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.InlineTaskNetwork");
	}

	@Test
	public void testIR_Networks_FanOutNetwork() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.FanOutNetwork");
	}

	@Test
	public void testIR_Networks_ProcessingChain() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.ProcessingChain");
	}

	@Test
	public void testIR_Networks_SyncNetwork() throws Exception {
		testIrGeneration("GoldenNetworks", "com.neosyn.test.golden.SyncNetwork");
	}

	@Test
	public void testVerilog_Networks_Producer() throws Exception {
		testVerilogGeneration("GoldenNetworks", "com.neosyn.test.golden.Producer");
	}

	@Test
	public void testVerilog_Networks_Consumer() throws Exception {
		testVerilogGeneration("GoldenNetworks", "com.neosyn.test.golden.Consumer");
	}

	/**
	 * Tests IR generation for a specific entity against its golden file.
	 *
	 * @param sourceFile  Name of the source .cg file (without extension)
	 * @param entityName  Fully qualified name of the entity to test
	 */
	private void testIrGeneration(String sourceFile, String entityName) throws Exception {
		// Compile the source file
		Module module = getModule("golden/" + sourceFile + ".cg");
		assertOk(module);

		// Run IR generator
		IGenerator generator = getInjector().getInstance(IGenerator.class);
		Resource resource = module.eResource();
		generator.doGenerate(resource, access);

		// Read generated IR
		String irPath = entityName.replace('.', '/') + "." + FILE_EXT_IR;
		URI irUri = access.getURI(irPath);
		String generatedContent = readFile(irUri);

		// Compare with golden file
		String goldenPath = GOLDEN_BASE + "/ir/" + getSimpleName(entityName) + "." + FILE_EXT_IR;
		compareOrUpdate(goldenPath, generatedContent, "IR for " + entityName);
	}

	/**
	 * Tests Verilog generation for a specific entity against its golden file.
	 *
	 * @param sourceFile  Name of the source .cg file (without extension)
	 * @param entityName  Fully qualified name of the entity to test
	 */
	private void testVerilogGeneration(String sourceFile, String entityName) throws Exception {
		// Compile and generate IR first
		Module module = getModule("golden/" + sourceFile + ".cg");
		assertOk(module);

		IGenerator generator = getInjector().getInstance(IGenerator.class);
		Resource resource = module.eResource();
		generator.doGenerate(resource, access);

		// Find the entity
		Entity entity = null;
		for (CgEntity cxEntity : module.getEntities()) {
			String fullName = module.getPackage() + "." + cxEntity.getName();
			if (fullName.equals(entityName)) {
				// Load the IR entity
				String irPath = entityName.replace('.', '/') + "." + FILE_EXT_IR;
				URI irUri = access.getURI(irPath);
				Resource irResource = resourceSet.getResource(irUri, true);
				entity = (Entity) irResource.getContents().get(0);
				break;
			}
		}

		if (entity == null) {
			fail("Entity not found: " + entityName);
			return;
		}

		// Generate Verilog
		verilogGenerator.setOutputFolder(outputPath);
		for (Entity anEntity : DpnFactory.eINSTANCE.collectEntities(entity)) {
			verilogGenerator.transform(anEntity);
			verilogGenerator.print(anEntity);
		}

		// Read generated Verilog
		String verilogPath = Paths.get(outputPath, "verilog-gen",
				entityName.replace('.', '/').replace("/", java.io.File.separator) + ".v").toString();
		Path verilogFile = Paths.get(verilogPath);

		if (!Files.exists(verilogFile)) {
			// Try simpler path
			verilogFile = Paths.get(outputPath, "verilog-gen", getSimpleName(entityName) + ".v");
		}

		String generatedContent = "";
		if (Files.exists(verilogFile)) {
			generatedContent = new String(Files.readAllBytes(verilogFile), StandardCharsets.UTF_8);
		}

		// Compare with golden file
		String goldenPath = GOLDEN_BASE + "/verilog/" + getSimpleName(entityName) + ".v";
		compareOrUpdate(goldenPath, generatedContent, "Verilog for " + entityName);
	}

	/**
	 * Compares generated content against a golden file, or updates the golden file.
	 *
	 * @param goldenPath       Path to the golden file (relative to project)
	 * @param generatedContent The generated content to compare
	 * @param description      Description for error messages
	 */
	private void compareOrUpdate(String goldenPath, String generatedContent, String description)
			throws IOException {
		Path goldenFile = Paths.get(goldenPath).toAbsolutePath();

		if (UPDATE_GOLDEN_FILES) {
			// Create parent directories if needed
			Files.createDirectories(goldenFile.getParent());
			// Write the golden file
			Files.write(goldenFile, generatedContent.getBytes(StandardCharsets.UTF_8));
			System.out.println("Updated golden file: " + goldenPath);
		} else {
			if (!Files.exists(goldenFile)) {
				fail(description + ": Golden file not found: " + goldenPath
						+ "\nRun with -Dneosyn.updateGoldenFiles=true to create it.");
				return;
			}

			String expectedContent = new String(Files.readAllBytes(goldenFile), StandardCharsets.UTF_8);

			// Normalize line endings + absolute paths. Goldens must be
			// checkout-location independent: IR resource URIs (fileName="…")
			// and Verilog $readmemh paths otherwise embed an absolute
			// filesystem path that varies per machine / CI runner.
			String normalizedExpected = normalizePaths(normalizeLineEndings(expectedContent));
			String normalizedGenerated = normalizePaths(normalizeLineEndings(generatedContent));

			if (!normalizedExpected.equals(normalizedGenerated)) {
				// Find first difference for better error message
				String diff = findFirstDifference(normalizedExpected, normalizedGenerated);
				fail(description + " differs from golden file: " + goldenPath + "\n" + diff
						+ "\nRun with -Dneosyn.updateGoldenFiles=true to update.");
			}
		}
	}

	/**
	 * Reads file content from a URI.
	 */
	private String readFile(URI uri) throws IOException {
		String path = uri.toFileString();
		if (path == null) {
			path = uri.toString().replace("file:", "");
		}
		return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
	}

	/**
	 * Gets the simple name from a fully qualified name.
	 */
	private String getSimpleName(String qualifiedName) {
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
	}

	/**
	 * Normalizes line endings to LF for cross-platform comparison.
	 */
	private String normalizeLineEndings(String content) {
		return content.replace("\r\n", "\n").replace("\r", "\n");
	}

	/**
	 * Collapses embedded absolute filesystem paths to their basename so golden
	 * comparisons are independent of the checkout location (local vs CI).
	 * Targets the two places the compiler emits an absolute path: the EMF
	 * resource URI in generated IR ({@code fileName="…/Foo.cg"}) and the
	 * {@code $readmemh("…/Foo_var.hex", …)} init in generated Verilog.
	 */
	private String normalizePaths(String content) {
		content = content.replaceAll("fileName=\"[^\"]*/([^/\"]+)\"", "fileName=\"$1\"");
		content = content.replaceAll("\\$readmemh\\(\"[^\"]*/([^/\"]+)\"", "\\$readmemh(\"$1\"");
		return content;
	}

	/**
	 * Finds the first difference between two strings and returns a helpful message.
	 */
	private String findFirstDifference(String expected, String actual) {
		String[] expectedLines = expected.split("\n");
		String[] actualLines = actual.split("\n");

		int minLines = Math.min(expectedLines.length, actualLines.length);
		for (int i = 0; i < minLines; i++) {
			if (!expectedLines[i].equals(actualLines[i])) {
				return String.format("First difference at line %d:\n  Expected: %s\n  Actual:   %s",
						i + 1, expectedLines[i], actualLines[i]);
			}
		}

		if (expectedLines.length != actualLines.length) {
			return String.format("Line count differs: expected %d lines, got %d lines",
					expectedLines.length, actualLines.length);
		}

		return "Files appear identical (possible whitespace difference)";
	}
}
