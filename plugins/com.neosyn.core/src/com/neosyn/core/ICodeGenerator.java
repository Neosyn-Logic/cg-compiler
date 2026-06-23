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

 * 
 */
public interface ICodeGenerator extends IPathResolver {

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
