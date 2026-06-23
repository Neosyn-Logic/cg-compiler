/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.cg.internal.AstUtil.and;
import static com.neosyn.cg.internal.AstUtil.expr;
import static com.neosyn.cg.internal.AstUtil.not;
import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Enter;
import com.neosyn.cg.cg.Leave;
import com.neosyn.cg.cg.StatementIf;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.scheduler.path.Path;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Transition;
import com.neosyn.models.util.SwitchUtil;
import com.neosyn.models.util.Void;

/**
 * This class develops a single transition with 'if' statements into multiple transitions according
 * to a code path. This extends CycleScheduler with cases for statement if (obviously) but also for
 * Enter and Leave. Indeed, these are artificial objects (in the sense not generated directly from
 * the source) created by the CycleScheduler in the first pass. When the IfDeveloper develops a
 * transition, these objects must be associated with the current transition.
 * 

 * 
 */
public class IfDeveloper extends AbstractCycleScheduler {

	private Path path;

	public IfDeveloper(IInstantiator instantiator, Actor actor) {
		super(instantiator, actor);
	}

	@Override
	protected Void associate(EObject eObject) {
		Transition transition = schedule.getTransition();
		transition.getBody().add(eObject);
		transition.getScheduler().add(eObject);
		return DONE;
	}

	@Override
	public Void caseEnter(Enter enter) {
		// simply associate this enter with the current transition
		associate(enter);
		return DONE;
	}

	@Override
	public Void caseLeave(Leave leave) {
		// simply associate this leave with the current transition
		associate(leave);
		return DONE;
	}

	@Override
	public Void caseStatementIf(StatementIf stmt) {
		if (CgUtil.isIfSimple(stmt)) {
			associate(stmt);
			return DONE;
		}

		Branch chosen = path.getNext();
		if (chosen == null) {
			// Path exhausted (more nested ifs than path entries) — treat as simple
			associate(stmt);
			return DONE;
		}
		List<Branch> branches = stmt.getBranches();
		CgExpression condition = expr(true);
		for (Branch branch : branches) {
			if (branch == chosen) {
				break;
			}

			schedule.visitCondition(this, branch.getCondition());
			condition = and(condition, not(branch.getCondition()));
		}

		Transition transition = schedule.getTransition();
		if (chosen.getCondition() != null) {
			condition = and(condition, chosen.getCondition());
		}

		// adds condition
		transition.getScheduler().add(condition);

		// visits branch
		schedule.visitBranch(this, chosen);

		return DONE;
	}

	/**
	 * Visits the given transition with the given code path.
	 * 
	 * @param transition
	 *            a transition
	 * @param path
	 *            the path to take
	 * @return the new transition
	 */
	public Transition visit(Transition transition, Path path) {
		Transition newTrans = DpnFactory.eINSTANCE.createTransition(transition.getSource(),
				transition.getTarget());
		schedule.setTransition(newTrans);

		// adds scheduling conditions of original transition
		newTrans.getAction().getPeekPattern().add(transition.getAction().getPeekPattern());
		newTrans.getScheduler().addAll(transition.getScheduler());

		// set iterator, visit objects, save pattern
		this.path = path;
		SwitchUtil.visit(this, transition.getBody());
		schedule.promotePeeks(transition.getAction());

		return newTrans;
	}

}
