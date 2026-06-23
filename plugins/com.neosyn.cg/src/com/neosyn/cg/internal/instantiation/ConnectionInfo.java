/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.cg.CgConstants.TYPE_READS;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.emf.ecore.EObject;

import com.google.common.collect.Multimap;
import com.neosyn.cg.cg.Connect;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;

/**
 * This class holds information about a connect statement.
 * 

 *
 */
public class ConnectionInfo implements Iterable<Port> {

	private final Instance instance;

	private final String name;

	private final Collection<Port> ports;

	public ConnectionInfo(IInstantiator instantiator, Multimap<EObject, Port> portMap, DPN dpn,
			Connect connect) {
		if (connect.isThis()) {
			instance = null;
		} else {
			instance = instantiator.getMapping(dpn, connect.getInstance());
		}

		name = instance == null ? "this" : instance.getName();
		ports = getPorts(portMap, dpn, connect.getType());
	}

	public Instance getInstance() {
		return instance;
	}

	public String getName() {
		return name;
	}

	public int getNumPorts() {
		return ports.size();
	}

	private Collection<Port> getPorts(Multimap<EObject, Port> portMap, DPN dpn, String type) {
		if (instance == null) {
			if (TYPE_READS.equals(type)) {
				return portMap.get(dpn);
			} else { // TYPE_WRITES
				return dpn.getInputs();
			}
		} else {
			Entity entity = instance.getEntity();
			if (TYPE_READS.equals(type)) {
				return portMap.get(instance);
			} else { // TYPE_WRITES
				return entity.getOutputs();
			}
		}
	}

	@Override
	public Iterator<Port> iterator() {
		return ports.iterator();
	}

	@Override
	public String toString() {
		return name;
	}

}
