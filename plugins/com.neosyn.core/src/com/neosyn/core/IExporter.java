/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core;

import org.w3c.dom.Document;

import com.neosyn.models.dpn.Entity;

/**
 * This interface defines an exporter.
 * 

 * 
 */
public interface IExporter {

	/**
	 * name of the folder where synthesis projects are generated
	 */
	String FOLDER_PROJECTS = "projects";

	/**
	 * Runs this exporter on the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	ProcessBuilder createProject(Entity entity, Document xmlDoc);
	
	/**
	 * Runs this exporter on the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	ProcessBuilder buildProject(Entity entity, Document xmlDoc);
	
	/**
	 * Runs this exporter on the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	ProcessBuilder setupPR(Entity entity);
	
	/**
	 * Runs this exporter on the given entity.
	 * 
	 * @param entity
	 *            a second entity for Partial Reconfig
	 *            
	 * @param entity
	 *            a third entity for Partial Reconfig
	 */
	/* TODO 
	 * Make exportPR generic i.e. a table of entities rather than two entities
	 */
	ProcessBuilder exportPR(Entity entity, Entity variant1, Entity variant2, Document xmlDoc);


}
