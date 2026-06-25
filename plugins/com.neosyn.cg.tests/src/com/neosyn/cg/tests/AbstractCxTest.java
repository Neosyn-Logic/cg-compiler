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
import static com.neosyn.core.ICoreConstants.FOLDER_IR;

import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.URIMappingRegistryImpl;
import org.eclipse.emf.ecore.xml.namespace.XMLNamespacePackage;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.junit4.AbstractXtextTests;
import org.eclipse.xtext.junit4.validation.ValidatorTester;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.neosyn.core.NeosynCore;
import com.neosyn.cg.CgStandaloneSetup;
import com.neosyn.cg.UriComputer;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.validation.CgValidator;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnPackage;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.impl.IrResourceFactoryImpl;
import com.neosyn.models.ir.util.IrUtil;

/**
 * This abstract class defines methods to use to test actor code generation.
 * 

 * 
 * @param <T>
 *            type of the top-level object of the AST
 */
@InjectWith(CgInjectorProvider.class)
@RunWith(XtextRunner.class)
public abstract class AbstractCxTest extends AbstractXtextTests {

	private static final String CX_PLUGIN_ID = "com.neosyn.cg";

	private static int failed, total;

	public static final String OUTPUT_NAME = "NeosynTest";

	private static void ignore(Object o) {
	}

	@BeforeClass
	public static void init() {
		Map<String, Object> extToFactoryMap = Resource.Factory.Registry.INSTANCE
				.getExtensionToFactoryMap();
		Object instance = extToFactoryMap.get(FILE_EXT_IR);
		if (instance == null) {
			instance = new IrResourceFactoryImpl();
			extToFactoryMap.put(FILE_EXT_IR, instance);
		}

		// initialize packages required for correct operation
		// just referencing a package causes it to initialize itself
		ignore(XMLNamespacePackage.eINSTANCE);
		ignore(DpnPackage.eINSTANCE);
		ignore(com.neosyn.cg.cg.CgPackage.eINSTANCE);

		// maps plugin URIs to absolute file URIs
		map(CX_PLUGIN_ID, "../" + CX_PLUGIN_ID);
		map(NeosynCore.PLUGIN_ID, "../../fragments/com.neosyn.ide.libraries");
	}

	protected static void map(String id, String relativePath) {
		// from platform:/plugin/id URI
		String name = "/" + id + "/";
		URI from = URI.createPlatformPluginURI(name, true);

		// to file:/ URI absolute path
		String path = Paths.get(relativePath).toAbsolutePath().normalize().toString();
		URI uri = URI.createFileURI(path + "/");

		// register mapping
		URIMappingRegistryImpl.INSTANCE.put(from, uri);
	}

	protected static void printFailure(String name, Throwable t) {
		System.err.println("test " + name + " failed");
		System.out.println("[FAILURE] test " + name + " failed: " + t.getMessage());
		t.printStackTrace();
		t.printStackTrace(System.out);
		System.err.flush();
		System.out.flush();
	}

	@AfterClass
	public static void printStats() {
		float total = AbstractCxTest.total;
		int percent = (int) (100.0f * (total - failed) / total);
		System.out.println();
		System.out.println(
				"*******************************************************************************");
		System.out.println(percent + "% tests passed (" + failed + " failed)");
		System.out.println(
				"*******************************************************************************");
	}

	protected static void testEnded(int failed, int length) {
		AbstractCxTest.failed += failed;
		AbstractCxTest.total += length;

		if (failed != 0) {
			float total = length;
			int percent = (int) (100.0f * (total - failed) / total);
			throw new RuntimeException(percent + "% tests passed (" + failed + " failed)");
		}
	}

	@Inject
	protected JavaIoFileSystemAccess access;

	protected String outputPath;

	@Inject
	private Provider<XtextResourceSet> provider;

	protected XtextResourceSet resourceSet;

	protected ValidatorTester<CgValidator> tester;

	/**
	 * Asserts that the resource from which the given object was loaded has no parse errors, and no
	 * semantic errors.
	 * 
	 * @param object
	 *            an object (top-level node of the AST)
	 */
	protected final void assertOk(EObject object) {
		assertNotNull(object);
		EList<org.eclipse.emf.ecore.resource.Resource.Diagnostic> errors = object.eResource()
				.getErrors();
		assertTrue("object has errors: " + errors, errors.isEmpty());

		Diagnostic diagnostic = tester.validate(object).getDiagnostic();
		StringBuilder errorMessages = new StringBuilder();
		boolean hasErrors = false;
		for (Diagnostic child : diagnostic.getChildren()) {
			if (child.getSeverity() == Diagnostic.ERROR) {
				hasErrors = true;
				if (child instanceof AbstractValidationDiagnostic) {
					AbstractValidationDiagnostic validationDiag = (AbstractValidationDiagnostic) child;
					EObject source = validationDiag.getSourceEObject();
					if (source != null) {
						ICompositeNode node = NodeModelUtils.getNode(source);
						errorMessages.append("line ").append(node.getStartLine()).append(": ");
					}
				}
				errorMessages.append(child.getMessage()).append("\n");
			}
		}
		assertFalse("Validation errors:\n" + errorMessages.toString(), hasErrors);
	}

	/**
	 * Checks that the actor has the properties specified in its test case.
	 * 
	 * @param actor
	 *            an actor
	 */
	protected final void checkProperties(Actor actor) {
		JsonPrimitive primitive = actor.getProperties().getAsJsonPrimitive("num_states");
		if (primitive != null) {
			int numberOfStates = primitive.getAsInt();
			int actual = actor.hasFsm() ? actor.getFsm().getStates().size() : 0;
			assertEquals("states in " + actor.getSimpleName(), numberOfStates, actual);
		}

		primitive = actor.getProperties().getAsJsonPrimitive("num_transitions");
		if (primitive != null) {
			int numberOfTransitions = primitive.getAsInt();
			int actual;
			if (actor.hasFsm()) {
				actual = actor.getFsm().getEdges().size();
			} else {
				actual = actor.getActions().size();
			}
			assertEquals("transitions in " + actor.getSimpleName(), numberOfTransitions, actual);
		}
	}

	/**
	 * Parses, validates, and generates code for the entity defined in the file whose name is given.
	 * 
	 * @param name
	 *            name of a .cg file that contains an entity
	 * @return an IR actor if the file could be parsed, validated, and translated to IR, otherwise
	 *         <code>null</code>
	 */
	protected Iterable<Entity> compileFile(String name) {
		final Module module = getModule(name);
		assertOk(module);

		// runs generator
		IGenerator generator = getInjector().getInstance(IGenerator.class);
		Resource resource = module.eResource();
		generator.doGenerate(resource, access);

		Iterable<Entity> entities;
		entities = Iterables.transform(module.getEntities(), new Function<CgEntity, Entity>() {
			@Override
			public Entity apply(CgEntity input) {
				return getEntity(module.getPackage() + "." + input.getName());
			}
		});
		return Iterables.filter(entities, Predicates.notNull());
	}

	private Entity getEntity(String name) {
		// clears loaded resources to force reloading them from disk
		resourceSet.getResources().clear();

		// get URI of .ir generated file
		String path = IrUtil.getFile(name) + "." + FILE_EXT_IR;
		URI uriIr = access.getURI(path);

		// load actor
		try {
			Resource irResource = resourceSet.getResource(uriIr, true);
			EObject eObject = irResource.getContents().get(0);
			assertNotNull(eObject);
			return (Entity) eObject;
		} catch (WrappedException e) {
			// could not load IR entity
			// happens when entity is not top level
			return null;
		}
	}

	/**
	 * Returns the Module of the task with the given name.
	 * 
	 * @param name
	 *            name
	 * @return a Module
	 */
	protected Module getModule(String name) {
		String path = Paths.get("tests", name).toAbsolutePath().toString();
		URI uri = URI.createFileURI(path);
		Resource resource = resourceSet.getResource(uri, true);
		return (Module) resource.getContents().get(0);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		with(CgStandaloneSetup.class);
		CgValidator validator = get(CgValidator.class);
		tester = new ValidatorTester<CgValidator>(validator, getInjector());

		// set output path
		String tmpDir = System.getProperty("java.io.tmpdir");
		outputPath = new Path(tmpDir).append(OUTPUT_NAME).toString();
		String absoluteOutput = outputPath + "/" + FOLDER_IR;
		access.setOutputPath(absoluteOutput);

		// register URI mapping from source to generated
		String path = Paths.get("tests").toAbsolutePath().toString();
		URI uri = URI.createFileURI(path + "/");
		UriComputer.INSTANCE.getURIMap().put(uri, URI.createFileURI(absoluteOutput + "/"));

		// creates the resource set to use for this test
		resourceSet = provider.get();
		resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
	}

}
