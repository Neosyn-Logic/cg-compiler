/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.models.util.SwitchUtil.DONE;
import static com.neosyn.models.util.SwitchUtil.visit;

import java.util.List;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.node.Node;
import com.neosyn.models.util.Void;

/**
 * This class builds an n-ary hierarchical tree that matches the conditions of the Cx code.
 * 

 * 
 */
public class IfAnalyzer extends VoidCxSwitch {

	private Node node;

	public IfAnalyzer() {
		node = new Node();
	}

	@Override
	public Void caseBranch(Branch branch) {
		node = new Node(node, branch);
		visit(this, branch.getBody());
		node = node.getParent();
		return DONE;
	}

	@Override
	public Void caseStatementIf(StatementIf stmt) {
		if (CgUtil.isIfSimple(stmt)) {
			return DONE;
		}

		node = new Node(node, stmt);
		List<Branch> branches = stmt.getBranches();
		for (Branch branch : branches) {
			doSwitch(branch);
		}

		// if this 'if' has no 'else', we add an artificial 'else' branch
		if (branches.get(branches.size() - 1).getCondition() != null) {
			new Node(node, CgFactory.eINSTANCE.createBranch());
		}
		node = node.getParent();

		return DONE;
	}

	public Node getRoot() {
		return node;
	}

}
