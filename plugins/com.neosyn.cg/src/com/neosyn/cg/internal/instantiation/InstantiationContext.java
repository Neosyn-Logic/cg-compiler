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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Element;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Null;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.Primitive;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.node.Node;

/**
 * This class defines an instantiation context as the path and properties obtained throughout the
 * hierarchy.
 * 

 *
 */
public class InstantiationContext extends Node {

	private static boolean isReservedProperty(String key) {
		return com.neosyn.cg.CgUtil.RESERVED_INSTANTIATION_KEYS.contains(key);
	}

	private final Inst inst;

	private final Instance instance;

	private final Map<String, CgExpression> properties;

	/**
	 * Creates a new instantiation context using the given parent context and the given name.
	 * 
	 * @param parent
	 *            parent context
	 * @param name
	 *            name of an instance
	 */
	public InstantiationContext(IInstantiator instantiator, InstantiationContext parent, Inst inst,
			Instance instance) {
		super(parent, inst.getName());
		this.inst = inst;
		this.instance = instance;

		// first add properties from parent context
		properties = new LinkedHashMap<>();
		if (parent != null) {
			properties.putAll(parent.properties);
		}

		// L3 Generics iter #2: positional type arguments `new Foo<8, 16>()`. Zip
		// each against the target entity's formal params (by declaration order)
		// to recover the named property the engine consumes. Processed before the
		// named-argument Obj below so an explicit `{K: v}` wins on any overlap.
		List<CgExpression> typeArgs = inst.getTypeArgs();
		if (!typeArgs.isEmpty() && inst.getEntity() != null) {
			List<com.neosyn.cg.cg.Variable> formals = inst.getEntity().getParams();
			int n = Math.min(typeArgs.size(), formals.size());
			for (int i = 0; i < n; i++) {
				String key = formals.get(i).getName();
				if (key == null || isReservedProperty(key)) {
					continue;
				}
				Object val = instantiator.evaluate(instance.getDPN(), typeArgs.get(i));
				properties.put(key, Evaluator.getCxExpression(val));
			}
		}

		// then add inst's properties (may override parent's)
		Obj obj = inst.getArguments();
		if (obj != null) {
			for (Pair pair : obj.getMembers()) {
				String key = pair.getKey();
				if (isReservedProperty(key)) {
					continue;
				}
				Element element = pair.getValue();

				// only support primitive values for now
				if (element instanceof Primitive) {
					Primitive primitive = (Primitive) element;
					EObject value = primitive.getValue();
					if (value instanceof CgExpression) {
						Object val = instantiator.evaluate(instance.getDPN(), value);
						properties.put(key, Evaluator.getCxExpression(val));
					} else if (value instanceof Null) {
						properties.put(key, null);
					}
				}
			}
		}
	}

	/**
	 * Creates a new instantiation context using the given root name.
	 * 
	 * @param name
	 *            name of the entity at the root of the hierarchy
	 */
	public InstantiationContext(String name) {
		super(name);
		properties = new LinkedHashMap<>();
		inst = null;
		instance = null;
	}

	public Inst getInst() {
		return inst;
	}

	public Instance getInstance() {
		return instance;
	}

	/**
	 * Returns the full name as an underscore-separated list of names.
	 * 
	 * @return a string
	 */
	public String getName() {
		List<String> path = new ArrayList<>();
		Node node = this;
		do {
			path.add((String) node.getContent());
			node = node.getParent();
		} while (node != null);

		return Joiner.on('_').join(Lists.reverse(path));
	}

	/**
	 * Returns an unmodifiable map of the properties of this instantiation context.
	 * 
	 * @return a map
	 */
	public Map<String, CgExpression> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

}
