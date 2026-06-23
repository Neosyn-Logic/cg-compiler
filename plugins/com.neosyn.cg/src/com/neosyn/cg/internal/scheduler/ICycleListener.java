/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

/**
 * This class defines a cycle listener. It has a single method, called when a new cycle has started.
 * 

 * 
 */
public interface ICycleListener {

	/**
	 * This method is called when a new cycle is being started.
	 */
	void newCycleStarted();

}
