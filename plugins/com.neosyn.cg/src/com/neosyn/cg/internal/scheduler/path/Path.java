/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler.path;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.services.CgPrinter;
import com.neosyn.models.node.Node;

/**
 * This class defines a code path as an iterable over StatementCond.
 * 

 * 
 */
public class Path {

	private Deque<Branch> conds;

	private Iterator<Branch> it;

	public Path() {
		conds = new ArrayDeque<>();
	}

	public void add(Node node) {
		Object content = node.getContent();
		if (content instanceof Branch) {
			Branch cond = (Branch) content;
			conds.addFirst(cond);
		}
	}

	/**
	 * Returns the next statement cond of this path.
	 * 
	 * @return a StatementCond
	 */
	public Branch getNext() {
		if (it == null) {
			it = conds.iterator();
		}
		if (!it.hasNext()) {
			return null;
		}
		return it.next();
	}

	@Override
	public String toString() {
		return Joiner.on(", ").join(Iterables.transform(conds, new Function<Branch, String>() {
			@Override
			public String apply(Branch cond) {
				CgExpression condition = cond.getCondition();
				if (condition == null) {
					return "(else)";
				} else {
					return new CgPrinter().toString(condition);
				}
			}
		}));
	}

}
