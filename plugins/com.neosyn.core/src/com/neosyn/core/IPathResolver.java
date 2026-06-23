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

public interface IPathResolver {

	/**
	 * Copies a built-in library file to the output folder.
	 * Called by DpnPrinter when it encounters a built-in entity reference.
	 *
	 * @param entityTypeName the fully qualified built-in entity name (e.g., "std.mem.SinglePortRAM")
	 */
	default void copyBuiltinEntityFile(String entityTypeName) {
		// Default implementation does nothing - override in generators that support this
	}

	/**
	 * Returns the path to the given entity. Equivalent to
	 * <code>computePath(IrUtil.getFile(entity.getName()))</code>
	 *
	 * @param entity
	 *            an entity
	 * @return a path
	 */
	String computePath(Entity entity);

	/**
	 * Returns a path composed of this generator's name (possibly with a '-gen' suffix, depending on
	 * generators), the given file name and this generator's file extension.
	 * 
	 * @param fileName
	 *            file name
	 * @return a path
	 */
	String computePath(String fileName);

	/**
	 * Returns <code>'testbench/' + file + '.' + fileExtension</code> where file is computed based
	 * on the given entity's name.
	 * 
	 * @param entity
	 *            an entity
	 * @return a path
	 */
	String computePathTb(Entity entity);

	/**
	 * Returns the absolute path to the given entity.
	 * 
	 * @param entity
	 *            an entity
	 * @return a path
	 */
	String getFullPath(Entity entity);

}
