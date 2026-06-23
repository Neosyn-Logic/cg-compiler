/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.transformations;

import static com.neosyn.core.IProperties.PROP_IMPORTS;
import static com.neosyn.models.ir.util.IrUtil.array;
import static com.neosyn.models.util.EcoreHelper.getContainerOfType;
import static com.neosyn.models.util.SwitchUtil.CASCADE;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.util.EcoreUtil.ExternalCrossReferencer;

import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.dpn.util.DpnSwitch;
import com.neosyn.models.graph.Vertex;
import com.neosyn.models.graph.visit.Ordering;
import com.neosyn.models.graph.visit.ReversePostOrder;
import com.neosyn.models.util.Void;

/**
 * This class defines an abstract code transformer.
 * 

 * 
 */
public abstract class AbstractTransformer extends DpnSwitch<Void> {

	@Override
	public Void caseDPN(DPN dpn) {
		List<Vertex> entries = getEntries(dpn);
		if (!entries.isEmpty()) {
			sortInstances(dpn, entries);
		}

		return CASCADE;
	}

	@Override
	public Void caseEntity(Entity entity) {
		// implement patterns with tests and assignments
		new PatternImplementation().doSwitch(entity);

		for (Transformation transformation : getTransformations()) {
			transformation.doSwitch(entity);
		}

		return DONE;
	}

	/**
	 * Computes the import list of the given entity (sorted by alphabetical order), and adds it to
	 * the "imports" attribute of the template data of the given entity.
	 * 
	 * @param entity
	 *            an entity
	 */
	protected void computeImportList(Entity entity) {
		Set<String> imports = new TreeSet<>();

		Map<EObject, Collection<Setting>> crossRefs = ExternalCrossReferencer.find(entity);
		for (Collection<Setting> settings : crossRefs.values()) {
			for (Setting setting : settings) {
				Object object = setting.get(true);
				if (object instanceof EObject) {
					EObject eObject = (EObject) object;
					Unit unit = getContainerOfType(eObject, Unit.class);
					if (unit != null) {
						imports.add(unit.getName());
					}
				}
			}
		}

		entity.getProperties().add(PROP_IMPORTS, array(imports));
	}

	/**
	 * Compute the list of entries
	 * 
	 * @param dpn
	 * @return
	 */
	private List<Vertex> getEntries(DPN dpn) {
		List<Vertex> entries = new ArrayList<>();

		// all source instances
		for (Instance instance : dpn.getInstances()) {
			if (instance.getIncoming().isEmpty()) {
				entries.add(instance);
			}
		}

		// all outgoing connections of input ports come from DPN itself
		if (!dpn.getVertex().getOutgoing().isEmpty()) {
			entries.add(dpn.getVertex());
		}

		return entries;
	}

	protected abstract Iterable<Transformation> getTransformations();

	/**
	 * Sorts the instances of the given DPN by topological order/reverse post order. Nothing happens
	 * if no entry vertex can be found; an entry is a vertex with no incoming connections, such as
	 * an input port or an instance with no input ports.
	 * 
	 * @param dpn
	 *            a DPN
	 */
	private void sortInstances(DPN dpn, List<Vertex> entries) {
		// sorts instances of the given network with topological order
		Ordering ordering = new ReversePostOrder(dpn.getGraph(), entries);

		final Map<Vertex, Integer> position = new HashMap<Vertex, Integer>();
		int i = 0;
		for (Vertex vertex : ordering) {
			position.put(vertex, i);
			i++;
		}

		// sorts vertices according to topological order
		// vertices not in the "position" map are not sorted
		ECollections.sort(dpn.getInstances(), new Comparator<Instance>() {
			@Override
			public int compare(Instance o1, Instance o2) {
				Integer p1 = position.get(o1);
				if (p1 == null) {
					p1 = 0;
				}

				Integer p2 = position.get(o2);
				if (p2 == null) {
					p2 = 0;
				}
				return p1.compareTo(p2);
			}
		});
	}

}
