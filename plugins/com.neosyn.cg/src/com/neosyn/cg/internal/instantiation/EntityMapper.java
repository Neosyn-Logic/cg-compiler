/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.UriComputer;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.CgPackage.Literals;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.TransformerUtil;
import com.neosyn.cg.internal.instantiation.properties.PropertiesService;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Unit;

/**
 * This class maps Cx entities to IR URIs.
 * 

 *
 */
public class EntityMapper extends CgSwitch<Entity> {

	enum Options {
		DRY_RUN
	}

	@Inject
	private IQualifiedNameConverter converter;

	@Inject
	private IInstantiator instantiator;

	@Inject
	private IQualifiedNameProvider qualifiedNameProvider;

	@Inject
	private IScopeProvider scopeProvider;

	@Inject
	private SkeletonMaker skeletonMaker;

	@Override
	public Entity caseBundle(Bundle bundle) {
		Unit unit = DpnFactory.eINSTANCE.createUnit();
		unit.setProperties(new JsonObject());
		return unit;
	}

	@Override
	public Entity caseNetwork(Network network) {
		DPN dpn = DpnFactory.eINSTANCE.createDPN();
		dpn.init();
		new PropertiesService(instantiator).translateProperties(network, dpn);
		return dpn;
	}

	@Override
	public Entity caseTask(Task task) {
		Actor actor = DpnFactory.eINSTANCE.createActor();
		new PropertiesService(instantiator).translateProperties(task, actor);
		return actor;
	}

	/**
	 * Configures the given IR entity from the Cx entity info and the instantiation context. Sets
	 * basic properties (name, file name, line number) and translates skeleton (state variables,
	 * ports...)
	 * 
	 * @param entity
	 *            IR entity
	 * @param info
	 *            Cx entity info
	 * @param ctx
	 *            instantiation context
	 */
	public void configureEntity(Entity entity, EntityInfo info, InstantiationContext ctx) {
		CgEntity cxEntity = info.getCxEntity();

		// set name
		if (CoreUtil.isBuiltin(entity) || CoreUtil.isExternal(entity)) {
			entity.setName(getName(cxEntity));
		} else {
			entity.setName(info.getName());
		}

		// set file name
		Module module = EcoreUtil2.getContainerOfType(cxEntity, Module.class);
		String fileName = CgUtil.getFileName(module);
		entity.setFileName(fileName);

		// set line number
		int lineNumber = TransformerUtil.getStartLine(cxEntity);
		entity.setLineNumber(lineNumber);

		// create skeleton (with proper values if ctx is given)
		if (ctx == null) {
			skeletonMaker.createSkeleton(cxEntity, entity);
		} else {
			Map<Variable, CgExpression> values = setValues(cxEntity, ctx);
			try {
				skeletonMaker.createSkeleton(cxEntity, entity);
			} finally {
				restoreValues(values);
			}
		}
	}

	/**
	 * Returns an EntityInfo about the given Cx entity.
	 * 
	 * @param cxEntity
	 *            a Cx entity
	 * @return an EntityInfo object
	 */
	public EntityInfo createEntityInfo(CgEntity cxEntity) {
		return createEntityInfo(cxEntity, null, null);
	}

	/**
	 * Returns an EntityInfo about the given entity.
	 * 
	 * @param cxEntity
	 *            Cx entity
	 * @param inst
	 *            instance, may be <code>null</code>
	 * @param specializedName
	 *            specialized name, if null this method uses the entity name as returned by
	 *            {@link #getName(CgEntity)}
	 * @return an EntityInfo object
	 */
	private EntityInfo createEntityInfo(CgEntity cxEntity, Inst inst, String specializedName) {
		boolean specialized = specializedName != null;
		String name = specialized ? specializedName : getName(cxEntity);
		if (name == null) {
			// happens when cxEntity is a proxy because it could not be resolved
			return new EntityInfo(cxEntity, null, null, specialized);
		}

		URI cxUri = cxEntity.eResource().getURI();
		URI uriInst = inst == null ? null : EcoreUtil.getURI(inst);
		URI uri = UriComputer.INSTANCE.computeUri(name, cxUri, uriInst);

		return new EntityInfo(cxEntity, name, uri, specialized);
	}

	/**
	 * Returns an EntityInfo for the Cx entity instantiated using the given instantiation context.
	 * 
	 * @param ctx
	 *            an instantiation context
	 * @return an EntityInfo
	 */
	public EntityInfo createEntityInfo(InstantiationContext ctx) {
		// if this instance declares an inner task, it is specialized
		Inst inst = ctx.getInst();
		boolean specialized = inst.getTask() != null;
		Instantiable cxEntity = specialized ? inst.getTask() : inst.getEntity();

		// if the context has properties, specializes
		specialized |= !ctx.getProperties().isEmpty();

		return createEntityInfo(cxEntity, inst, specialized ? ctx.getName() : null);
	}

	/**
	 * Returns the qualified name of the given entity.
	 * 
	 * @param entity
	 *            Cx entity
	 * @return a name
	 */
	private String getName(CgEntity entity) {
		QualifiedName qualifiedName = qualifiedNameProvider.getFullyQualifiedName(entity);
		if (qualifiedName == null) {
			return null;
		}
		return qualifiedName.toString();
	}

	/**
	 * Restore values using the given map.
	 * 
	 * @param values
	 *            a map variable to value
	 */
	private void restoreValues(Map<Variable, CgExpression> values) {
		for (Entry<Variable, CgExpression> binding : values.entrySet()) {
			binding.getKey().setValue(binding.getValue());
		}
	}

	/**
	 * Sets the values of affected variables in the given Cx entity to the values given by the
	 * instantiation context.
	 * 
	 * @param cxEntity
	 *            Cx entity
	 * @param ctx
	 *            instantiation context
	 * @return a map of variable - value association
	 */
	private Map<Variable, CgExpression> setValues(CgEntity cxEntity, InstantiationContext ctx) {
		Map<Variable, CgExpression> previous = new HashMap<>();
		if (ctx.getProperties().isEmpty()) {
			return Collections.emptyMap();
		}

		IScope scope = scopeProvider.getScope(cxEntity, Literals.VAR_REF__OBJECTS);
		for (Entry<String, CgExpression> entry : ctx.getProperties().entrySet()) {
			String varName = entry.getKey();
			QualifiedName qName = converter.toQualifiedName(varName);
			IEObjectDescription objDesc = scope.getSingleElement(qName);
			if (objDesc != null) {
				EObject eObject = objDesc.getEObjectOrProxy();
				if (eObject instanceof Variable) {
					// saves the previous value and updates the variable's value
					Variable variable = (Variable) eObject;
					previous.put(variable, variable.getValue());
					variable.setValue(EcoreUtil.copy(entry.getValue()));
				}
			}
		}

		return previous;
	}

}
