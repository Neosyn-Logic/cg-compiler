/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import com.google.inject.Inject;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Named;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;

/**
 * This class defines an helper class that creates Connections.
 * 

 * 
 */
public class ConnectorHelper {

	@Inject
	private IInstantiator instantiator;

	/**
	 * Returns the endpoint associated with the port associated with the given reference.
	 * 
	 * @param ref
	 *            reference to a port in another instance or in the containing network
	 * @return an endpoint
	 */
	public Endpoint getEndpoint(DPN dpn, VarRef ref) {
		Variable cxPort = ref.getVariable();
		Instance otherInst = getInstance(dpn, ref);
		if (otherInst == null) {
			Port otherPort = instantiator.getMapping(dpn, cxPort);
			if (otherPort == null) {
				// may happen if link refers to a non-existent port
				return null;
			}

			return new Endpoint(dpn, otherPort);
		} else {
			if (otherInst.getEntity() == null) {
				return null;
			}

			Port otherPort = instantiator.getMapping(otherInst.getEntity(), cxPort);
			if (otherPort == null) {
				// For built-in entities, look up the port directly by name
				// Built-in entity ports are not in the regular mapping
				String portName = cxPort.getName();
				com.neosyn.models.dpn.Entity entity = otherInst.getEntity();

				// Search in inputs
				for (Port p : entity.getInputs()) {
					if (portName.equals(p.getName())) {
						otherPort = p;
						break;
					}
				}

				// Search in outputs if not found in inputs
				if (otherPort == null) {
					for (Port p : entity.getOutputs()) {
						if (portName.equals(p.getName())) {
							otherPort = p;
							break;
						}
					}
				}

				if (otherPort == null) {
					// may happen if link refers to a non-existent port
					return null;
				}
			}

			return new Endpoint(otherInst, otherPort);
		}
	}

	/**
	 * If the given port reference refers to a port in an instance, returns that instance.
	 * Otherwise, if the reference is that of a simple port (no instance), returns null.
	 * 
	 * @param dpn
	 *            dpn
	 * @param ref
	 *            a port reference
	 * @return an instance
	 */
	public Instance getInstance(DPN dpn, VarRef ref) {
		Named named = ref.getObjects().get(0);
		if (named instanceof Inst) {
			Inst inst = (Inst) named;
			return instantiator.getMapping(dpn, inst);
		}
		
		return null;
	}

}
