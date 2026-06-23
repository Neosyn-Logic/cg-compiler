/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Variable;
import com.neosyn.models.dpn.Entity;
import com.neosyn.core.util.DebugLogger;
import static com.neosyn.core.util.DebugLogger.Category.IR;

/**
 * This class contains data used by the instantiator and defines methods to access it.
 * 

 * 
 */
public class InstantiatorData {

	private Map<Entity, Map<Object, EObject>> mapCxToIr;

	/**
	 * map Cx entity -> IR entity
	 */
	private Map<CgEntity, Entity> mapEntities;

	/**
	 * map (Cx entity, instantiation context) -> IR entity
	 */
	private Map<CgEntity, Map<InstantiationContext, Entity>> mapSpecialized;

	private Map<URI, CgEntity> uriMap;

	/**
	 * Map for built-in entity port mappings.
	 * Key: DPN (parent network), Value: Map of "instanceName.portName" -> Port
	 */
	private Map<Entity, Map<String, com.neosyn.models.dpn.Port>> builtinPortMappings;

	/**
	 * Struct-typed ports flattened to N scalar field ports (Tier 2.2).
	 * Key: IR entity, Value: (struct port Variable -> ordered field-name -> IR Port).
	 * The inner map is a LinkedHashMap so iteration follows struct field
	 * declaration order (deterministic for IrPathDriftTests).
	 */
	private Map<Entity, Map<Variable, Map<String, com.neosyn.models.dpn.Port>>> structPortMap;

	/**
	 * Struct-typed STATE variables flattened to N scalar field state Vars
	 * (bug #11). Key: IR entity, Value: (struct state Variable -> ordered
	 * field-name -> IR Var). Mirrors {@link #structPortMap} for ports;
	 * consumed by {@code ActorBuilder}, which imports these into IrBuilder's
	 * {@code structFieldMap} so `state.field` access resolves like a local.
	 */
	private Map<Entity, Map<Variable, Map<String, com.neosyn.models.ir.Var>>> structStateMap;

	public InstantiatorData() {
		mapCxToIr = new HashMap<>();
		mapEntities = new HashMap<>();
		mapSpecialized = new HashMap<>();
		uriMap = new HashMap<>();
		builtinPortMappings = new HashMap<>();
		structPortMap = new HashMap<>();
		structStateMap = new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	private <T extends EObject> T basicGetMapping(Entity entity, Object cxObj) {
		Map<Object, EObject> map = mapCxToIr.get(entity);
		if (map == null) {
			return null;
		} else {
			return (T) map.get(cxObj);
		}
	}

	/**
	 * Adds all Cx entities transitively instantiated by the given entity to the
	 * <code>entities</code> set.
	 * 
	 * @param entities
	 *            a set of entities
	 * @param cxEntity
	 *            a Cx entity
	 */
	private void collectEntities(Set<CgEntity> entities, CgEntity cxEntity) {
		entities.add(cxEntity);
		if (cxEntity instanceof Network) {
			Network network = (Network) cxEntity;
			for (Inst inst : network.getInstances()) {
				CgEntity subEntity = inst.getEntity() == null ? inst.getTask() : inst.getEntity();
				if (!entities.contains(subEntity)) {
					// just to avoid looping forever in case an entity instantiates itself
					collectEntities(entities, subEntity);
				}
			}
		}
	}

	/**
	 * Returns the Cx entity currently associated with the given URI.
	 * 
	 * @param uri
	 *            URI of a Cx entity
	 * @return a Cx entity (may be <code>null</code>)
	 */
	public CgEntity getCxEntity(URI uri) {
		return uriMap.get(uri);
	}

	/**
	 * Returns a collection of IR entities associated with the given Cx entity. If the Cx entity is
	 * specialized, this method returns a list of possibly many specialized IR entities
	 * corresponding to the original Cx entity; otherwise a single IR entity is returned.
	 * 
	 * @param cxEntity
	 *            a Cx entity
	 * @return a collection of IR entities
	 */
	public Iterable<Entity> getEntities(CgEntity cxEntity) {
		Objects.requireNonNull(cxEntity, "cxEntity must not be null in getEntities");

		String cxName = cxEntity.getName() != null ? cxEntity.getName() : "(anonymous:" + cxEntity.getClass().getSimpleName() + ")";
		DebugLogger.log(IR, "[InstantiatorData] getEntities called for: " + cxName + " (hashCode=" + System.identityHashCode(cxEntity) + ")");
		DebugLogger.log(IR, "[InstantiatorData]   mapEntities keys: " + mapEntities.size());
		DebugLogger.log(IR, "[InstantiatorData]   mapSpecialized keys: " + mapSpecialized.size());
		for (CgEntity key : mapSpecialized.keySet()) {
			String keyName = key.getName() != null ? key.getName() : "(anonymous:" + key.getClass().getSimpleName() + ")";
			DebugLogger.log(IR, "[InstantiatorData]     specialized key: " + keyName + " (hashCode=" + System.identityHashCode(key) + ")");
		}

		List<Entity> entities = new ArrayList<>();
		Entity entity = mapEntities.get(cxEntity);
		if (entity != null) {
			DebugLogger.log(IR, "[InstantiatorData]   Found in mapEntities: " + entity.getName());
			entities.add(entity);
		}

		Map<InstantiationContext, Entity> map = mapSpecialized.get(cxEntity);
		if (map != null) {
			DebugLogger.log(IR, "[InstantiatorData]   Found in mapSpecialized: " + map.size() + " entries");
			entities.addAll(map.values());
		} else {
			DebugLogger.log(IR, "[InstantiatorData]   NOT found in mapSpecialized");
		}

		DebugLogger.log(IR, "[InstantiatorData]   Returning " + entities.size() + " entities");
		return entities;
	}

	/**
	 * Returns the IR entity that is currently associated with the given Cx instantiable.
	 * 
	 * @param instantiable
	 *            an instantiable Cx entity
	 * @return an IR entity, or <code>null</code>
	 */
	public Entity getIrEntity(CgEntity instantiable) {
		return mapEntities.get(instantiable);
	}

	/**
	 * Returns all Cx-to-IR entity mappings.
	 * Used for looking up entities by name when the Cx entity is not available.
	 *
	 * @return the map of Cx entities to IR entities
	 */
	public Map<CgEntity, Entity> getEntities() {
		return mapEntities;
	}

	public <T extends EObject> T getMapping(Entity entity, Object cxObj) {
		Objects.requireNonNull(entity, "entity must not be null in getMapping");

		T irObj = basicGetMapping(entity, cxObj);
		if (irObj == null) {
			if (!(cxObj instanceof EObject)) {
				return null;
			}

			CgEntity cxEntity = EcoreUtil2.getContainerOfType((EObject) cxObj, CgEntity.class);
			if (cxObj instanceof Variable && CgUtil.isPort((Variable) cxObj)) {
				// must not look mapping for ports
				return null;
			}

			// lookup in mapEntities, because Bundles are not specialized (yet)
			entity = mapEntities.get(cxEntity);
			if (entity != null) {
				irObj = basicGetMapping(entity, cxObj);
			}
		}
		return irObj;
	}

	/**
	 * Returns the specialization info associated with the given Cx entity.
	 * 
	 * @param cxEntity
	 *            a Cx entity
	 * @return a map
	 */
	public Map<InstantiationContext, Entity> getSpecialization(CgEntity cxEntity) {
		return mapSpecialized.get(cxEntity);
	}

	/**
	 * Checks whether the object at the given URI is specialized.
	 * 
	 * @param uri
	 *            URI
	 * @return true if the object at the given URI is specialized
	 */
	public boolean isSpecialized(URI uri) {
		CgEntity cxEntity = uriMap.get(uri);
		return mapSpecialized.containsKey(cxEntity);
	}

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
	public void putMapping(Entity entity, Object cxObj, EObject irObj) {
		Objects.requireNonNull(entity, "entity must not be null in putMapping");

		Map<Object, EObject> map = mapCxToIr.get(entity);
		if (map == null) {
			map = new HashMap<>();
			mapCxToIr.put(entity, map);
		}
		map.put(cxObj, irObj);
	}

	/**
	 * Registers one flattened field port of a struct-typed port (Tier 2.2).
	 * Fields must be registered in struct declaration order to keep the inner
	 * map's iteration deterministic.
	 */
	public void putStructPort(Entity entity, Variable portVar, String fieldName,
			com.neosyn.models.dpn.Port port) {
		Map<Variable, Map<String, com.neosyn.models.dpn.Port>> byVar =
				structPortMap.computeIfAbsent(entity, e -> new HashMap<>());
		Map<String, com.neosyn.models.dpn.Port> byField =
				byVar.computeIfAbsent(portVar, v -> new LinkedHashMap<>());
		byField.put(fieldName, port);
	}

	/**
	 * Returns the ordered field-name -> IR Port map for a struct-typed port,
	 * or null if {@code portVar} is not a flattened struct port on {@code entity}.
	 */
	public Map<String, com.neosyn.models.dpn.Port> getStructPortFields(Entity entity,
			Variable portVar) {
		Map<Variable, Map<String, com.neosyn.models.dpn.Port>> byVar = structPortMap.get(entity);
		return byVar == null ? null : byVar.get(portVar);
	}

	/**
	 * Registers one flattened leaf field Var of a struct-typed STATE variable
	 * (bug #11). Fields are registered in struct declaration order so the inner
	 * map's iteration stays deterministic (for IrPathDriftTests).
	 */
	public void putStructStateField(Entity entity, Variable structVar, String fieldName,
			com.neosyn.models.ir.Var var) {
		Map<Variable, Map<String, com.neosyn.models.ir.Var>> byVar =
				structStateMap.computeIfAbsent(entity, e -> new HashMap<>());
		Map<String, com.neosyn.models.ir.Var> byField =
				byVar.computeIfAbsent(structVar, v -> new LinkedHashMap<>());
		byField.put(fieldName, var);
	}

	/**
	 * Returns the (struct state Variable -> field-name -> IR Var) map for the
	 * given entity, or an empty map. Consumed by {@code ActorBuilder} to seed
	 * its {@code structFieldMap}.
	 */
	public Map<Variable, Map<String, com.neosyn.models.ir.Var>> getStructStateVars(Entity entity) {
		Map<Variable, Map<String, com.neosyn.models.ir.Var>> byVar = structStateMap.get(entity);
		return byVar == null ? java.util.Collections.emptyMap() : byVar;
	}

	/**
	 * Stores a port mapping for a built-in entity instance.
	 * This allows inline tasks to resolve port references like line1.q.read().
	 *
	 * @param dpn the parent DPN network
	 * @param portKey the key in format "instanceName.portName" (e.g., "line1.q")
	 * @param port the IR Port from the built-in entity
	 */
	public void putBuiltinPortMapping(Entity dpn, String portKey, com.neosyn.models.dpn.Port port) {
		Map<String, com.neosyn.models.dpn.Port> map = builtinPortMappings.get(dpn);
		if (map == null) {
			map = new HashMap<>();
			builtinPortMappings.put(dpn, map);
		}
		map.put(portKey, port);
	}

	/**
	 * Retrieves a port mapping for a built-in entity instance.
	 *
	 * @param dpn the parent DPN network
	 * @param portKey the key in format "instanceName.portName" (e.g., "line1.q")
	 * @return the IR Port, or null if not found
	 */
	public com.neosyn.models.dpn.Port getBuiltinPortMapping(Entity dpn, String portKey) {
		Map<String, com.neosyn.models.dpn.Port> map = builtinPortMappings.get(dpn);
		if (map == null) {
			return null;
		}
		return map.get(portKey);
	}

	/**
	 * Removes info about all specialized entities that can be reached from the given entity.
	 * 
	 * @param cxEntity
	 */
	public Iterable<InstantiationContext> removeSpecialized(CgEntity cxEntity) {
		List<InstantiationContext> contexts = new ArrayList<>();
		if (cxEntity == null) {
			return contexts;
		}

		Set<CgEntity> entities = new LinkedHashSet<>();
		collectEntities(entities, cxEntity);

		for (CgEntity aCxEntity : entities) {
			Map<InstantiationContext, Entity> map = mapSpecialized.remove(aCxEntity);
			if (map != null) {
				contexts.addAll(map.keySet());
				mapCxToIr.keySet().removeAll(map.values());
			}
		}

		return contexts;
	}

	public void updateMapping(CgEntity cxEntity, Entity entity, InstantiationContext ctx) {
		Objects.requireNonNull(cxEntity, "cxEntity must not be null in updateMapping");

		String cxName = cxEntity.getName() != null ? cxEntity.getName() : "(anonymous:" + cxEntity.getClass().getSimpleName() + ")";
		String entityName = entity.getName() != null ? entity.getName() : "(anonymous IR)";
		DebugLogger.log(IR, "[InstantiatorData] updateMapping called: cxEntity=" + cxName +
			" (hashCode=" + System.identityHashCode(cxEntity) + "), entity=" + entityName +
			", ctx=" + (ctx != null ? ctx.getName() : "null"));

		URI uri = EcoreUtil.getURI(cxEntity);
		CgEntity oldEntity = uriMap.get(uri);
		if (oldEntity != cxEntity) {
			uriMap.put(uri, cxEntity);
		}

		// updates mapEntities/mapSpecialized
		if (ctx == null) {
			// clean up anything associated with previous version of cxEntity
			// only does this for mapEntities, specialized mappings are cleared by instantiator
			if (oldEntity != null && oldEntity != cxEntity) {
				mapCxToIr.remove(mapEntities.remove(oldEntity));
			}

			mapEntities.put(cxEntity, entity);
			DebugLogger.log(IR, "[InstantiatorData]   Stored in mapEntities (non-specialized)");
		} else {
			Map<InstantiationContext, Entity> map = mapSpecialized.get(cxEntity);
			if (map == null) {
				map = new LinkedHashMap<>();
				mapSpecialized.put(cxEntity, map);
			}
			map.put(ctx, entity);
			DebugLogger.log(IR, "[InstantiatorData]   Stored in mapSpecialized (specialized)");
		}
	}

}
