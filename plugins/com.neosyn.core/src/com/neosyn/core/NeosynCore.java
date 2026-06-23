/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import static com.neosyn.core.ICoreConstants.PROP_GENERATOR;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * This class defines the Neosyn core plug-in, as well as various constants.
 *

 *
 */
public class NeosynCore implements BundleActivator {

	private static BundleContext context;

	// The shared instance
	private static NeosynCore plugin;

	// The plug-in ID
	public static final String PLUGIN_ID = "com.neosyn.core";

	/**
	 * Returns the bundle associated with this plug-in.
	 * 
	 * @return the bundle associated with this plug-in
	 */
	public static Bundle getBundle() {
		return context.getBundle();
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static NeosynCore getDefault() {
		return plugin;
	}

	/**
	 * Returns the code generator used by and configured for the given project, or <code>null</code> .
	 * 
	 * @param project
	 *            a project
	 * @return a code generator
	 */
	public static ICodeGenerator getGenerator(IProject project) {
		String name = getProjectPreferences(project).get(PROP_GENERATOR, null);
		if (name == null) {
			// no generator associated with the project
			try {
				IMarker marker = project.createMarker(IMarker.PROBLEM);
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				String msg = "No generator associated with project '" + project.getName()
						+ "'. Please edit project properties.";
				marker.setAttribute(IMarker.MESSAGE, msg);
			} catch (CoreException e) {
				NeosynCore.log(e);
			}
			return null;
		}

		ICodeGenerator generator;
		try {
			generator = getDefault().getInstance(ICodeGenerator.class, name);
			generator.setOutputFolder(project.getName());
		} catch (ConfigurationException e) {
			generator = null;
			try {
				IMarker marker = project.createMarker(IMarker.PROBLEM);
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				String msg = name + " code generator required to compile project '" + project.getName()
						+ "' is not available. " + "Please edit project properties.";
				marker.setAttribute(IMarker.MESSAGE, msg);
			} catch (CoreException ex) {
				NeosynCore.log(ex);
			}
		}
		return generator;
	}

	/**
	 * Returns the list of declared generators.
	 * 
	 * @return a list of names of generators
	 */
	public static List<String> getGenerators() {
		// extensions
		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IConfigurationElement[] elements = reg.getConfigurationElementsFor("com.neosyn.core.injection");
		List<String> generators = new ArrayList<>();
		for (IConfigurationElement element : elements) {
			if ("generator".equals(element.getName())) {
				String name = element.getAttribute("name");
				generators.add(name);
			}
		}
		return generators;
	}

	/**
	 * Returns the preferences node for the given project.
	 * 
	 * @param project
	 *            a project
	 * @return a preference node
	 */
	public static IEclipsePreferences getProjectPreferences(IProject project) {
		return new ProjectScope(project).getNode(NeosynCore.PLUGIN_ID);
	}

	/**
	 * Returns the list of projects with the Neosyn nature.
	 *
	 * @return the list of projects with the Neosyn nature
	 */
	public static IProject[] getProjects() {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		List<IProject> projects = new ArrayList<>();
		for (IProject project : root.getProjects()) {
			try {
				if (project.isOpen() && project.hasNature(NeosynNature.NATURE_ID)) {
					projects.add(project);
				}
			} catch (CoreException e) {
				log(e);
			}
		}

		return projects.toArray(new IProject[projects.size()]);
	}

	/**
	 * Returns true if this plug-in is loaded.
	 * 
	 * @return true if this plug-in is loaded
	 */
	public static boolean isLoaded() {
		return plugin != null;
	}

	/**
	 * Logs an error status based on the given throwable.
	 * 
	 * @param t
	 *            a throwable
	 */
	public static void log(Throwable t) {
		if (!isLoaded()) {
			t.printStackTrace();
			return;
		}

		IStatus status;
		if (t instanceof CoreException) {
			status = ((CoreException) t).getStatus();
		} else {
			status = new Status(IStatus.ERROR, PLUGIN_ID, t.getMessage(), t);
		}
		Platform.getLog(getBundle()).log(status);
	}

	/**
	 * Opens an input stream on the given file. Works within Eclipse and in a standalone environment.
	 * 
	 * @param fileName
	 *            name of a file contained in this bundle or in a fragment, beginning with a /
	 * @return an input stream
	 * @throws IOException
	 */
	public static InputStream openStream(String fileName) throws IOException {
		URI uri = URI.createPlatformPluginURI("/" + NeosynCore.PLUGIN_ID + fileName, false);
		return new ExtensibleURIConverterImpl().createInputStream(uri, null);
	}

	private Injector injector;

	/**
	 * Returns the appropriate instance for the given injection type.
	 * 
	 * @param type
	 *            injection type
	 * @return an instance of type T
	 */
	public <T> T getInstance(Class<T> type) {
		return injector.getInstance(type);
	}

	/**
	 * Returns the appropriate instance for the given injection type, with a Named annotation (name is
	 * given).
	 * 
	 * @param type
	 *            injection type
	 * @param name
	 *            annotated name
	 * @return an instance of type T
	 */
	public <T> T getInstance(Class<T> type, String name) {
		Named annotation = Names.named(name);
		Key<T> key = Key.get(type, annotation);
		return injector.getInstance(key);
	}

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		NeosynCore.context = bundleContext;
		NeosynCore.plugin = this;

		injector = Guice.createInjector(new NeosynModule());
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		injector = null;
		NeosynCore.context = null;
		NeosynCore.plugin = null;
	}

}
