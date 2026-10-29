/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import com.neosyn.models.dpn.Entity;

/**
 * This interface defines a code generator. A code generator can be initialized, and defines a
 * doSwitch method that visits an object to generate code.
 * 
 */
public interface ICodeGenerator extends IPathResolver {

	/** The default target vendor: no vendor-specific rule, the HDL generated before target vendors existed. */
	String TARGET_GENERIC = "generic";

	/** The target vendors {@code generate --target-vendor} and {@code neosyn.cg.target} accept (S181). */
	java.util.List<String> TARGET_VENDORS = java.util.List.of(TARGET_GENERIC, "amd", "intel", "lattice");

	/**
	 * Copies libraries of the given generator to the generator's output folder.
	 */
	void copyLibraries();

	/**
	 * Performs a full code generation with the given top entity.
	 *
	 * @param entity
	 */
	void fullBuild(Entity entity);

	/**
	 * Hook invoked once with the top entity BEFORE any per-entity transformation, so a backend can
	 * annotate the whole hierarchy while it is still intact ({@code collectEntities} transforms
	 * leaf actors before their parent network). Default no-op; the Verilog backend uses it to mark
	 * direct held-producer stream links. Callers that drive transformation manually (e.g. the
	 * codegen test harness) must call this first, exactly as {@code fullBuild} does.
	 *
	 * @param entity
	 *            the top entity being generated
	 */
	default void prepare(Entity entity) {
		// no-op by default
	}

	/**
	 * Returns the file extension of files this generator generates (e.g. "c").
	 * 
	 * @return the file extension
	 */
	String getFileExtension();

	/**
	 * Returns the file writer used by this code generator.
	 * 
	 * @return a file writer
	 */
	IFileWriter getFileWriter();

	/**
	 * Returns an iterable of support libraries required by this generator.
	 * 
	 * @return an iterable of qualified names
	 */
	Iterable<String> getLibraries();

	/**
	 * Returns the name of this generator.
	 * 
	 * @return the name of this generator
	 */
	String getName();

	/**
	 * Prints code for the given object, unless it has an 'implementation' property.
	 * 
	 * @param entity
	 *            entity
	 */
	void print(Entity entity);

	/**
	 * Prints a test bench for the given object.
	 * 
	 * @param entity
	 *            actor or network
	 */
	void printTestbench(Entity entity);

	/**
	 * Removes the file corresponding to the given qualified name.
	 * 
	 * @param name
	 *            qualified name
	 */
	void remove(String name);

	/**
	 * Sets the target vendor of the next generation run and its design roots -- the non-test networks nothing
	 * else instantiates, the only ones a vendor rule may change. Called before every run, generic included,
	 * because a generator is reused across runs.
	 *
	 * @param vendor
	 *            one of {@link #TARGET_VENDORS}
	 * @param designRoots
	 *            qualified names of the design roots
	 */
	default void setTarget(String vendor, java.util.Set<String> designRoots) {
	}

	/**
	 * Sets the output folder that this generator will use.
	 * 
	 * @param name
	 *            folder name
	 */
	void setOutputFolder(String name);

	/**
	 * Transforms the given entity in place.
	 * 
	 * @param entity
	 *            an entity to transform
	 */
	void transform(Entity entity);

}
