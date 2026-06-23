/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

/**
 * This class defines a pattern as a list of ports that are read/written.
 * 

 * @model
 */
public interface Pattern extends EObject {

	/**
	 * Adds the ports of the given pattern to this pattern.
	 * 
	 * @param pattern
	 *            a pattern
	 */
	void add(Pattern pattern);

	void add(Port port);

	/**
	 * Clears this pattern.
	 */
	void clear();

	/**
	 * Returns <code>true</code> if this pattern contains the given port.
	 * 
	 * @param port
	 *            a port
	 * @return a boolean
	 */
	boolean contains(Port port);

	/**
	 * Returns the ports of this pattern.
	 * 
	 * @return the ports of this pattern
	 * @model
	 */
	EList<Port> getPorts();

	/**
	 * Returns <code>true</code> if this pattern is empty.
	 * 
	 * @return <code>true</code> if this pattern is empty
	 */
	boolean isEmpty();

}
