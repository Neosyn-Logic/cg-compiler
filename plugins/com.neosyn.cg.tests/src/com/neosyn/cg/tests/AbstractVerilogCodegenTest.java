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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IGenerator;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.neosyn.cg.cg.Module;
import com.neosyn.core.ICodeGenerator;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * Base class for Verilog codegen-quality tests: it compiles a fixture and returns the generated
 * Verilog for one entity, so the concrete tests only carry their assertions. Concrete subclasses
 * still supply their own {@code @InjectWith}/{@code @RunWith} and inject the VERILOG generator
 * through this class.
 *
 * @see VerilogCastSimplificationTest
 * @see VerilogAssertMessageTest
 * @see ConstantLoopUnrollTest
 */
public abstract class AbstractVerilogCodegenTest extends AbstractCxTest {

	@Inject
	@Named(VERILOG)
	protected ICodeGenerator verilogGenerator;

	/** Compile a fixture and return the generated Verilog for one entity. */
	protected String generateVerilog(String sourceFile, String entityName) throws Exception {
		return generateVerilog(sourceFile, entityName, entityName);
	}

	/**
	 * Compile a fixture, generate the whole hierarchy rooted at {@code topEntityName}, and return
	 * the Verilog of {@code targetEntityName} (which may be a child instance's entity). Generating
	 * from the network top is required for network-aware passes (e.g. the direct held-producer
	 * marking): a leaf actor loaded alone carries no connections.
	 */
	protected String generateVerilog(String sourceFile, String topEntityName, String targetEntityName)
			throws Exception {
		Module module = getModule(sourceFile);
		assertOk(module);

		IGenerator generator = getInjector().getInstance(IGenerator.class);
		generator.doGenerate(module.eResource(), access);

		String irPath = topEntityName.replace('.', '/') + "." + FILE_EXT_IR;
		URI irUri = access.getURI(irPath);
		Resource irResource = resourceSet.getResource(irUri, true);
		Entity entity = (Entity) irResource.getContents().get(0);

		verilogGenerator.setOutputFolder(outputPath);
		// Up-front hook (as fullBuild does): mark direct held-producer stream links before any
		// leaf actor is transformed, so the handshake is emitted correctly.
		verilogGenerator.prepare(entity);
		for (Entity anEntity : DpnFactory.eINSTANCE.collectEntities(entity)) {
			verilogGenerator.transform(anEntity);
			verilogGenerator.print(anEntity);
		}

		String verilogPath = Paths.get(outputPath, "verilog-gen",
				targetEntityName.replace('.', '/').replace("/", java.io.File.separator) + ".v").toString();
		return new String(Files.readAllBytes(Paths.get(verilogPath)), StandardCharsets.UTF_8);
	}

	/**
	 * Compile a fixture ONCE and generate the whole hierarchy for each of the given top entities
	 * (so several networks in one module can be regenerated without re-running IR generation, which
	 * is not idempotent on a shared resource set).
	 */
	protected void generateNetworks(String sourceFile, String... topEntityNames) throws Exception {
		Module module = getModule(sourceFile);
		assertOk(module);

		IGenerator generator = getInjector().getInstance(IGenerator.class);
		generator.doGenerate(module.eResource(), access);

		verilogGenerator.setOutputFolder(outputPath);
		for (String topEntityName : topEntityNames) {
			String irPath = topEntityName.replace('.', '/') + "." + FILE_EXT_IR;
			Resource irResource = resourceSet.getResource(access.getURI(irPath), true);
			Entity entity = (Entity) irResource.getContents().get(0);

			verilogGenerator.prepare(entity);
			for (Entity anEntity : DpnFactory.eINSTANCE.collectEntities(entity)) {
				verilogGenerator.transform(anEntity);
				verilogGenerator.print(anEntity);
			}
		}
	}

	/** The generated Verilog for one entity, with comments stripped (assert on real code only). */
	protected String codeOf(String sourceFile, String entityName) throws Exception {
		return stripComments(generateVerilog(sourceFile, entityName));
	}

	/**
	 * The generated Verilog of {@code targetEntityName} (a child of {@code topEntityName}), with
	 * comments stripped. Use when the entity to inspect is instantiated inside a network that must
	 * be generated as a whole for network-aware passes to see the connections.
	 */
	protected String codeOf(String sourceFile, String topEntityName, String targetEntityName)
			throws Exception {
		return stripComments(generateVerilog(sourceFile, topEntityName, targetEntityName));
	}

	private static String stripComments(String raw) {
		return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
	}
}
