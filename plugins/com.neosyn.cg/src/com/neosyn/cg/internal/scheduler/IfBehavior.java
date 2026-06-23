/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import com.neosyn.models.node.Node;

/**
 * This interface defines methods to handle the scheduling of if statements:
 * <ul>
 * <li>fork</li>
 * <li>join</li>
 * <li>startBranch</li>
 * </ul>
 * 

 *
 */
public interface IfBehavior {

	/**
	 * Forks at the current node.
	 * 
	 * @return the current node.
	 */
	Node fork();

	/**
	 * Joins fork's branches together, collecting input/output patterns of fork's children into
	 * fork's action. This method also sets fork as the current node, but do not clear its children.
	 * 
	 * @param fork
	 *            the node that was saved before visiting branches
	 */
	void join(Node fork);

	/**
	 * Starts a new 'if' branch from the given fork node (obtained from the 'fork' method).
	 * 
	 * @param fork
	 *            the fork node
	 */
	void startBranch(Node fork);

}
