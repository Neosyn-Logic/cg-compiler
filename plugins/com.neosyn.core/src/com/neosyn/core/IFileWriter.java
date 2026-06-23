/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import java.io.InputStream;

/**
 * This interface defines a file writer that is independent of the underlying platform.
 * 

 * 
 */
public interface IFileWriter {

	/**
	 * Returns <code>true</code> if a file with the given name exists.
	 * 
	 * @param fileName
	 *            name of destination file
	 */
	boolean exists(String fileName);

	/**
	 * Returns the absolute path to the file with the given name. The path uses the same separator
	 * "/" on all platforms.
	 * 
	 * @param fileName
	 *            name of destination file
	 * @return absolute path to the file with the given name
	 */
	String getAbsolutePath(String fileName);

	/**
	 * Removes the file with the given name.
	 * 
	 * @param fileName
	 *            name of destination file
	 */
	void remove(String fileName);

	/**
	 * Sets the output folder to which file name are relative.
	 * 
	 * @param folder
	 *            an output folder
	 */
	void setOutputFolder(String folder);

	/**
	 * Writes the given contents to the file with the given name.
	 * 
	 * @param fileName
	 *            name of destination file
	 * @param sequence
	 *            a sequence of characters
	 */
	void write(String fileName, CharSequence sequence);

	/**
	 * Writes the given contents to the file with the given name.
	 * 
	 * @param fileName
	 *            name of destination file
	 * @param source
	 *            an input stream
	 */
	void write(String fileName, InputStream source);

}
