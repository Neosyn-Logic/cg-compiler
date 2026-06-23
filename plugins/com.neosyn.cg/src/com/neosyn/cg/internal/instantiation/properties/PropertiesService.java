/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.ErrorMarker;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;

/**
 * This class defines a properties support class that checks and updates properties to a normal
 * form.
 * 

 * 
 */
public class PropertiesService implements IJsonErrorHandler {

	/**
	 * the marker to use when no specific marker information is available
	 */
	private ErrorMarker defaultMarker;

	private List<ErrorMarker> errors;

	private final IInstantiator instantiator;

	private JsonMaker maker;

	public PropertiesService(IInstantiator instantiator) {
		this.instantiator = instantiator;
	}

	@Override
	public void addError(JsonElement element, String message) {
		ErrorMarker marker = maker.getMapping(element);
		if (marker == null) {
			marker = new ErrorMarker(message, defaultMarker);
		} else {
			marker = new ErrorMarker(message, marker);
		}

		errors.add(marker);
	}

	private JsonObject translateJson(Entity entity, EObject source, EStructuralFeature feature) {
		defaultMarker = new ErrorMarker(null, source, feature);
		errors = getContainerOfType(source, Instantiable.class).getErrors();
		maker = new JsonMaker(instantiator, entity);

		Obj obj = (Obj) source.eGet(feature);
		if (obj == null) {
			return new JsonObject();
		} else {
			return maker.toJson(obj);
		}
	}

	/**
	 * Translate properties of <code>inst</code> and set them to the given instance.
	 *
	 * @param inst
	 *            Cx instance
	 * @param instance
	 *            IR instance
	 */
	public void translateProperties(Inst inst, Instance instance) {
		translateProperties(inst, instance, instance.getEntity());
	}

	/**
	 * Translate properties of <code>inst</code> and set them to the given instance,
	 * using the specified scope entity for constant resolution.
	 *
	 * <p>This overload is used when the instance's entity is a built-in entity (like
	 * std.mem.SinglePortRAM) but constants need to be resolved from the parent network's
	 * scope (e.g., LINE_WIDTH imported from BufferConstants).</p>
	 *
	 * @param inst
	 *            Cx instance
	 * @param instance
	 *            IR instance
	 * @param scopeEntity
	 *            the entity to use for constant resolution (typically the parent DPN)
	 */
	public void translateProperties(Inst inst, Instance instance, Entity scopeEntity) {
		JsonObject properties;
		if (inst.getTask() == null) {
			properties = translateJson(scopeEntity, inst, Literals.INST__ARGUMENTS);
		} else {
			// inner task, use the task's properties for the instance
			properties = translateJson(scopeEntity, inst.getTask(), Literals.CG_ENTITY__PROPERTIES);
		}

		// Merge with existing properties (e.g., entityType set earlier for built-in entities)
		JsonObject existingProps = instance.getProperties();
		if (existingProps != null) {
			for (String key : existingProps.keySet()) {
				if (!properties.has(key)) {
					properties.add(key, existingProps.get(key));
				}
			}
		}

		instance.setProperties(properties);

		new InstancePropertiesChecker(this).checkProperties(instance);
	}

	/**
	 * Translate properties of <code>instantiable</code> and set them to the given IR entity.
	 * 
	 * @param instantiable
	 *            Cx instantiable (task or network)
	 * @param entity
	 *            IR entity
	 */
	public void translateProperties(Instantiable instantiable, Entity entity) {
		JsonObject properties;
		properties = translateJson(entity, instantiable, Literals.CG_ENTITY__PROPERTIES);
		entity.setProperties(properties);

		new EntityPropertiesChecker(this).checkProperties(instantiable, properties);
	}

}
