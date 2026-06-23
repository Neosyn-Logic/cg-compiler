/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.services;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

import com.neosyn.cg.cg.ExpressionVariable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;

/**
 * This class defines a qualified name provider for Cx that computes the qualified name of a module
 * as its simple name (file name without extension).
 * 

 * 
 */
public class CgQualifiedNameProvider extends DefaultDeclarativeQualifiedNameProvider {

	@Override
	public QualifiedName getFullyQualifiedName(final EObject obj) {
		if (obj instanceof Variable) {
			EObject cter = obj.eContainer();
			if (cter instanceof ExpressionVariable) {
				// ignore synthetic variables that belong to ExpressionVariable
				return null;
			}
		}

		return super.getFullyQualifiedName(obj);
	}

	protected QualifiedName qualifiedName(Module module) {
		return getConverter().toQualifiedName(module.getPackage());
	}

	protected QualifiedName qualifiedName(Task task) {
		String name = task.getName();
		if (name == null) {
			// anonymous task
			EObject cter = task.eContainer();
			QualifiedName qName = getFullyQualifiedName(cter);
			int size = qName.getSegmentCount();
			String taskName = qName.getSegment(size - 2) + "_" + qName.getLastSegment();
			return qName.skipLast(2).append(taskName);
		}

		// will use task's "name" attribute and container
		return null;
	}

}
