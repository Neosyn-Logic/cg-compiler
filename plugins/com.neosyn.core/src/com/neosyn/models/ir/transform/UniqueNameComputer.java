/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.neosyn.models.ir.Var;

/**
 * This class defines a name computer that generates unique names.
 * 

 *
 */
public class UniqueNameComputer {

	private Set<String> names;

	public UniqueNameComputer(Iterable<String> names) {
		this.names = Sets.newHashSet(names);
	}

	public UniqueNameComputer(List<Var> variables) {
		this(Iterables.transform(variables, new Function<Var, String>() {
			public String apply(Var variable) {
				return variable.getName();
			}
		}));
	}

	/**
	 * Returns a unique name based on the given name.
	 * 
	 * @param name
	 *            name
	 * @return a unique name based on the given name
	 */
	public String getUniqueName(String name) {
		String uniqueName = name;
		int i = 1;
		while (names.contains(uniqueName)) {
			uniqueName = name + "_" + i;
			i++;
		}
		names.add(uniqueName);
		return uniqueName;
	}

}
