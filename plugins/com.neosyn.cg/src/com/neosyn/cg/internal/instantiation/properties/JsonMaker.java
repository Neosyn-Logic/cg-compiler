/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation.properties;

import java.util.IdentityHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.cg.cg.Array;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Element;
import com.neosyn.cg.cg.Null;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.Primitive;
import com.neosyn.cg.cg.util.CgSwitch;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.ErrorMarker;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.util.ValueUtil;

/**
 * This class transforms our Javascript-like syntax to pure JSON.
 * 

 * 
 */
public class JsonMaker extends CgSwitch<JsonElement> {

	private Entity entity;

	private IInstantiator instantiator;

	private Map<JsonElement, ErrorMarker> mapping;

	public JsonMaker(IInstantiator instantiator, Entity entity) {
		mapping = new IdentityHashMap<>();
		this.instantiator = instantiator;
		this.entity = entity;
	}

	@Override
	public JsonArray caseArray(Array array) {
		JsonArray jsonArray = new JsonArray();
		mapping.put(jsonArray, new ErrorMarker(array));
		for (Element element : array.getElements()) {
			jsonArray.add(doSwitch(element));
		}
		return jsonArray;
	}

	@Override
	public JsonPrimitive caseCgExpression(CgExpression expression) {
		Object value = instantiator.evaluate(entity, expression);
		JsonPrimitive primitive;
		if (ValueUtil.isBool(value)) {
			primitive = new JsonPrimitive((Boolean) value);
		} else if (ValueUtil.isFloat(value) || ValueUtil.isInt(value)) {
			primitive = new JsonPrimitive((Number) value);
		} else if (ValueUtil.isString(value)) {
			primitive = new JsonPrimitive((String) value);
		} else {
			return null;
		}

		mapping.put(primitive, new ErrorMarker(expression));
		return primitive;
	}

	@Override
	public JsonNull caseNull(Null null_) {
		return JsonNull.INSTANCE;
	}

	@Override
	public JsonObject caseObj(Obj obj) {
		JsonObject jsonObj = new JsonObject();
		mapping.put(jsonObj, new ErrorMarker(obj));
		for (Pair pair : obj.getMembers()) {
			Element value = pair.getValue();
			if (value != null) {
				String key = pair.getKey();
				jsonObj.add(key, doSwitch(value));
			}
		}
		return jsonObj;
	}

	@Override
	public JsonElement casePrimitive(Primitive primitive) {
		return doSwitch(primitive.getValue());
	}

	/**
	 * Returns the error marker that corresponds to the given JSON element.
	 * 
	 * @param element
	 *            a JSON element
	 * @return an error marker
	 */
	public ErrorMarker getMapping(JsonElement element) {
		return mapping.get(element);
	}

	/**
	 * Transforms the given object to JSON.
	 * 
	 * @param obj
	 *            an object
	 * @return a JSON object
	 */
	public JsonObject toJson(Obj obj) {
		return (JsonObject) doSwitch(obj);
	}

}
