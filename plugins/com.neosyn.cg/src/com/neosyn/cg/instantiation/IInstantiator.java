/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.instantiation;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;

import com.google.inject.ImplementedBy;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.internal.instantiation.InstantiatorImpl;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Type;
import com.neosyn.models.util.Executable;

/**
 * This interface defines an instantiator.
 * 

 * 
 */
@ImplementedBy(InstantiatorImpl.class)
public interface IInstantiator {

	/**
	 * Clears all data retained by the instantiator.
	 */
	void clearData();

	/**
	 * Computes the type of the given object.
	 * 
	 * @param eObject
	 *            an object
	 * @return the type of the given object
	 */
	Type computeType(Entity entity, EObject eObject);

	/**
	 * Returns the value associated with the given object.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the value associated with the given object
	 */
	Object evaluate(Entity entity, EObject eObject);

	/**
	 * Returns the integer value associated with the given object. Returns -1 if the value is not an
	 * integer.
	 * 
	 * @param eObject
	 *            an AST node
	 * @return the integer value associated with the given object
	 */
	int evaluateInt(Entity entity, EObject eObject);

	/**
	 * Retrieves all IR entities associated to the given Cx entity, and for each <code>entity</code>
	 * , calls <code>executable.exec(entity)</code>.
	 * 
	 * @param cxEntity
	 *            Cx entity
	 * @param executable
	 *            an executable
	 * @see #execute(Entity, Executable)
	 */
	void forEachMapping(CgEntity cxEntity, Executable<Entity> executable);

	/**
	 * Returns the Cx entity currently associated with the given URI.
	 * 
	 * @param uri
	 *            URI of a Cx entity
	 * @return a Cx entity (may be <code>null</code>)
	 */
	CgEntity getEntity(URI uri);

	/**
	 * Returns the IR object that corresponds to the given Cx object in the given entity.
	 * 
	 * @param entity
	 *            the entity to use to find the mapping
	 * @param cxObj
	 *            a Cx object (entity, variable, port...)
	 * @return the IR mapping
	 */
	<T extends EObject> T getMapping(Entity entity, Object cxObj);

	/**
	 * Returns the IR port that corresponds to the given reference.
	 * 
	 * @param entity
	 *            the entity in which the mapping exists
	 * @param ref
	 *            a reference to a Cx port
	 * @return an IR port
	 */
	Port getPort(Entity entity, VarRef ref);

	/**
	 * Registers one flattened field port of a struct-typed port (Tier 2.2).
	 * Called by the skeleton maker as it fans a struct port out to N scalar
	 * field ports. Fields must be registered in struct declaration order.
	 *
	 * @param entity
	 *            the entity owning the port
	 * @param portVar
	 *            the Cx struct port variable
	 * @param fieldName
	 *            the struct field name this IR port carries
	 * @param port
	 *            the flattened scalar IR port
	 */
	void putStructPort(Entity entity, com.neosyn.cg.cg.Variable portVar, String fieldName,
			Port port);

	/**
	 * Returns the ordered field-name -> IR Port map for a struct-typed port
	 * reference (resolving same-task and cross-task references the same way as
	 * {@link #getPort}), or null if {@code ref} is not a struct port.
	 *
	 * @param entity
	 *            the entity in which the reference is used
	 * @param ref
	 *            a reference to a Cx struct port
	 * @return the field-name -> IR Port map, or null
	 */
	java.util.Map<String, Port> getStructPortFields(Entity entity, VarRef ref);

	/**
	 * Registers one flattened leaf field IR Var of a struct-typed STATE
	 * variable (bug #11 — struct state fields). Called by {@code SkeletonMaker}
	 * while fanning a {@code struct} state var into scalar entity vars.
	 */
	void putStructStateField(Entity entity, com.neosyn.cg.cg.Variable structVar,
			String fieldName, com.neosyn.models.ir.Var var);

	/**
	 * Returns the (struct state Variable -> field-name -> IR Var) map for the
	 * given entity (empty if none). Consumed by {@code ActorBuilder} to seed
	 * its struct-field lookup so `state.field` access resolves.
	 */
	java.util.Map<com.neosyn.cg.cg.Variable, java.util.Map<String, com.neosyn.models.ir.Var>>
			getStructStateVars(Entity entity);

	/**
	 * Checks whether the object at the given URI is specialized.
	 * 
	 * @param uri
	 *            URI
	 * @return true if the object at the given URI is specialized
	 */
	boolean isSpecialized(URI uri);

	/**
	 * Adds a mapping from the given Cx object to the given IR object in the given entity.
	 * 
	 * @param entity
	 *            an IR entity
	 * @param cxObj
	 *            a Cx object (entity, variable, port...)
	 * @param irObj
	 *            the IR object that corresponds to <code>cxObj</code> in the given entity
	 */
	void putMapping(Entity entity, Object cxObj, EObject irObj);

	/**
	 * Updates the instantiation tree with entities of the given module. Most of the time this
	 * method only performs a partial update of the tree.
	 *
	 * @param module
	 *            a module
	 */
	void update(Module module);

	/**
	 * Updates the instantiation tree with entities of the given module directly,
	 * without relying on the Xtext resource descriptions index.
	 * This is useful for standalone/LSP mode where the index may not be populated.
	 *
	 * @param module
	 *            a module
	 */
	void updateDirect(Module module);

}
