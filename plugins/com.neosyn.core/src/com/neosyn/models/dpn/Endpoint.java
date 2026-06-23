/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn;

import java.util.Objects;

import com.neosyn.models.graph.Vertex;

/**
 * This class defines an endpoint.
 * 

 * 
 */
public class Endpoint {

	private final Instance instance;

	private final Port port;

	public Endpoint(DPN dpn, Port port) {
		Objects.requireNonNull(dpn, "dpn must not be null in Endpoint");
		Objects.requireNonNull(port, "port must not be null in Endpoint");

		if (port.eContainer() != dpn) {
			throw new IllegalArgumentException("port must be contained in dpn");
		}

		this.instance = null;
		this.port = port;
	}

	public Endpoint(Instance instance, Port port) {
		Objects.requireNonNull(instance, "instance must not be null in Endpoint");
		Objects.requireNonNull(port, "port must not be null in Endpoint");

		this.instance = instance;
		this.port = port;
	}

	@Override
	public boolean equals(Object anObject) {
		if (!(anObject instanceof Endpoint)) {
			return false;
		}

		Endpoint endpoint = (Endpoint) anObject;
		return Objects.equals(instance, endpoint.instance) && Objects.equals(port, endpoint.port);
	}

	public Instance getInstance() {
		return instance;
	}

	public Port getPort() {
		return port;
	}

	public Vertex getVertex() {
		if (instance == null) {
			DPN dpn = (DPN) port.eContainer();
			return dpn.getVertex();
		} else {
			return instance;
		}
	}

	@Override
	public int hashCode() {
		if (instance == null) {
			return port.hashCode();
		}
		return instance.hashCode() ^ port.hashCode();
	}

	/**
	 * Equivalent to <code>getInstance() != null</code>
	 * 
	 * @return a boolean
	 */
	public boolean hasInstance() {
		return instance != null;
	}

	@Override
	public String toString() {
		if (hasInstance()) {
			return instance.getName() + "." + port.getName();
		} else {
			return port.getName();
		}
	}

}
