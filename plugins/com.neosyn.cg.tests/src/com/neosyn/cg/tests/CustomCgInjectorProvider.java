/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import org.eclipse.emf.ecore.EPackage;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.neosyn.core.IFileWriter;
import com.neosyn.cg.CgRuntimeModule;
import com.neosyn.cg.CgStandaloneSetup;
import com.neosyn.cg.cg.CgPackage;
import com.neosyn.neosynide.NeosynIdeModule;

/**
 * This class configures additional modules in addition to the CgRuntimeModule.
 * Uses NeosynIdeModule for full code generator support in tests, but overrides
 * the file writer to use TestFileWriter instead of EclipseFileWriter.
 *
 * <p>IMPORTANT: When using {@link Modules#override}, the normal Guice binding chain
 * that triggers CgPackage EMF registration is broken. We must explicitly register
 * CgPackage in {@link EPackage.Registry} before creating the injector.</p>
 */
public class CustomCgInjectorProvider extends CgInjectorProvider {

	@Override
	protected Injector internalCreateInjector() {
		// Force CgPackage EMF registration BEFORE creating the Guice injector.
		// Modules.override() changes the binding resolution chain, preventing
		// the normal CgPackage initialization that happens through CgRuntimeModule.
		if (!EPackage.Registry.INSTANCE.containsKey(CgPackage.eNS_URI)) {
			EPackage.Registry.INSTANCE.put(CgPackage.eNS_URI, CgPackage.eINSTANCE);
		}

		return new CgStandaloneSetup() {
			@Override
			public Injector createInjector() {
				// Use createRuntimeModule() from parent CgInjectorProvider which
				// overrides bindClassLoaderToInstance() for OSGi/Tycho compatibility.
				CgRuntimeModule runtimeModule = createRuntimeModule();

				// Override IFileWriter with TestFileWriter for headless tests
				Module testOverride = new AbstractModule() {
					@Override
					protected void configure() {
						bind(IFileWriter.class).to(TestNeosynModule.TestFileWriter.class);
					}
				};
				return Guice.createInjector(
						Modules.override(
							Modules.override(runtimeModule).with(new NeosynIdeModule())
						).with(testOverride));
			}
		}.createInjectorAndDoEMFRegistration();
	}

}
