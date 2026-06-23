/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import static com.neosyn.models.dpn.DpnFactory.eINSTANCE;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.gson.JsonPrimitive;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.node.Node;

/**
 * This class defines a network flattener.
 * 

 *
 */
public class NetworkFlattener {

	private Multimap<Endpoint, Endpoint> mapEndpoints;

	private Multiset<String> names;

	private DPN targetDpn;

	private void computeHierarchicalName(Node node, Instance copy) {
		List<String> hierarchy = new ArrayList<>();
		hierarchy.add(copy.getName());
		names.add(copy.getName());

		while (node != null) {
			hierarchy.add(((Instance) node.getContent()).getName());
			node = node.getParent();
		}

		String name = Joiner.on('_').join(Lists.reverse(hierarchy));
		copy.getProperties().add("hierarchical_name", new JsonPrimitive(name));
	}

	private void ensureNameUniqueness(Instance instance) {
		if (names.count(instance.getName()) > 1) {
			// need to use hierarchical name
			String name = instance.getProperties().get("hierarchical_name").getAsString();
			instance.setName(name);
		}

		instance.getProperties().remove("hierarchical_name");
	}

	/**
	 * Transforms the given hierarchical DPN into a flat network.
	 * 
	 * @param dpn
	 *            a hierarchical DPN
	 */
	public void flatten(DPN dpn) {
		targetDpn = dpn;

		List<Instance> existing = new ArrayList<Instance>(dpn.getInstances());

		mapEndpoints = LinkedHashMultimap.create();
		names = HashMultiset.create();
		visit(null, dpn, existing);

		for (Instance instance : existing) {
			dpn.remove(instance);
		}

		for (Instance instance : dpn.getInstances()) {
			ensureNameUniqueness(instance);
		}
	}

	private List<Endpoint> getMapped(Endpoint endpoint) {
		List<Endpoint> mapped = new ArrayList<>();
		getMultipleMapped(mapped, endpoint);
		return mapped;
	}

	private Endpoint getSingleMapped(Endpoint endpoint) {
		List<Endpoint> mapped = getMapped(endpoint);
		if (mapped.size() > 1) {
			throw new IllegalArgumentException("cannot have more than one input mapping");
		}
		return mapped.get(0);
	}

	private void getMultipleMapped(List<Endpoint> mapped, Endpoint endpoint) {
		if (mapEndpoints.containsKey(endpoint)) {
			for (Endpoint mappedEndpoint : mapEndpoints.get(endpoint)) {
				getMultipleMapped(mapped, mappedEndpoint);
			}
		} else {
			mapped.add(endpoint);
		}
	}

	public void visit(Node parent, DPN dpn, List<Instance> instances) {
		for (Instance instance : instances) {
			if (instance.getEntity() instanceof DPN) {
				DPN subDpn = (DPN) instance.getEntity();
				for (Connection connection : dpn.getIncoming(instance)) {
					Endpoint inner = new Endpoint(subDpn, connection.getTargetPort());
					Endpoint outer = connection.getSourceEndpoint();
					mapEndpoints.put(inner, outer);
				}

				for (Connection connection : dpn.getOutgoing(instance)) {
					Endpoint inner = new Endpoint(subDpn, connection.getSourcePort());
					Endpoint outer = connection.getTargetEndpoint();
					mapEndpoints.put(inner, outer);
				}

				visit(new Node(parent, instance), subDpn, subDpn.getInstances());

				for (Connection connection : dpn.getIncoming(instance)) {
					Endpoint outer = connection.getTargetEndpoint();
					for (Connection inner : subDpn.getOutgoing(connection.getTargetPort())) {
						mapEndpoints.put(outer, inner.getTargetEndpoint());
					}
				}

				for (Connection connection : dpn.getOutgoing(instance)) {
					Endpoint outer = connection.getSourceEndpoint();
					Endpoint inner = subDpn.getIncoming(connection.getSourcePort());
					mapEndpoints.put(outer, inner);
				}
			} else {
				Instance copy = EcoreUtil.copy(instance);
				computeHierarchicalName(parent, copy);
				targetDpn.add(copy);

				for (Connection connection : dpn.getIncoming(instance)) {
					Endpoint target = connection.getTargetEndpoint();
					mapEndpoints.put(target, new Endpoint(copy, connection.getTargetPort()));
				}

				for (Connection connection : dpn.getOutgoing(instance)) {
					Endpoint source = connection.getSourceEndpoint();
					mapEndpoints.put(source, new Endpoint(copy, connection.getSourcePort()));
				}
			}
		}

		for (Instance instance : instances) {
			for (Connection connection : dpn.getOutgoing(instance)) {
				Endpoint source = getSingleMapped(connection.getSourceEndpoint());
				for (Endpoint target : getMapped(connection.getTargetEndpoint())) {
					targetDpn.getGraph().add(eINSTANCE.createConnection(source, target));
				}
			}
		}
	}

}
