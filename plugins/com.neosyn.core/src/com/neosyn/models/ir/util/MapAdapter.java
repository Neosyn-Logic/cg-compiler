/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.util;

import static com.neosyn.models.ir.util.IrUtil.getNameSSA;

import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EReference;

import com.neosyn.models.dpn.Actor;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;

/**
 * This class defines an adapter that maintains a map of variables from a list
 * of variables.
 * 

 * 
 */
public class MapAdapter extends AdapterImpl {

	private final Map<String, Var> map;

	private final EReference reference;

	public MapAdapter(Map<String, Var> map, EReference reference) {
		this.map = map;
		this.reference = reference;
	}

	private void add(Object object) {
		Var var = (Var) object;
		map.put(getNameSSA(var), var);
	}

	@Override
	public boolean isAdapterForType(Object type) {
		return (type == Actor.class || type == Procedure.class);
	}

	@Override
	public void notifyChanged(Notification notification) {
		if (reference != notification.getFeature()) {
			return;
		}

		switch (notification.getEventType()) {
		case Notification.ADD:
			add(notification.getNewValue());
			break;

		case Notification.ADD_MANY: {
			List<?> list = (List<?>) notification.getNewValue();
			for (Object object : list) {
				add(object);
			}
			break;
		}

		case Notification.MOVE: {
			remove(notification.getOldValue());
			add(notification.getNewValue());
			break;
		}

		case Notification.REMOVE:
			remove(notification.getOldValue());
			break;

		case Notification.REMOVE_MANY: {
			List<?> list = (List<?>) notification.getOldValue();
			for (Object object : list) {
				remove(object);
			}
			break;
		}
		}
	}

	private void remove(Object object) {
		map.remove(getNameSSA((Var) object));
	}

}
