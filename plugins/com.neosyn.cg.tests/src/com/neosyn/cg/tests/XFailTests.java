/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.core.ICoreConstants.FILE_EXT_CX;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.junit4.validation.AssertableDiagnostics.DiagnosticPredicate;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.Test;

import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Element;
import com.neosyn.cg.cg.ExpressionString;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.Primitive;

/**
 * This class defines Cx tests that are expected to fail.
 * 

 * 
 */
@InjectWith(CgInjectorProvider.class)
public class XFailTests extends AbstractCxTest {

	/**
	 * Asserts that the entity has an expected issue.
	 * 
	 * @param entity
	 *            a Cx entity
	 */
	private void assertExpectedFail(CgEntity entity) {
		final String issue = getExpectedIssue(entity);
		if (issue == null) {
			throw new IllegalArgumentException("entity " + entity.getName()
					+ " has no \"issue\" property");
		}

		DiagnosticPredicate predicate = new DiagnosticPredicate() {
			public boolean apply(Diagnostic d) {
				if (d instanceof AbstractValidationDiagnostic) {
					AbstractValidationDiagnostic diag = (AbstractValidationDiagnostic) d;
					return issue.equals(diag.getIssueCode());
				}
				return false;
			}
		};

		tester.validate(entity).assertAny(predicate);
	}

	@Test
	public void declarations() throws Exception {
		test("Declarations");
	}

	private String getExpectedIssue(CgEntity entity) {
		Obj properties = entity.getProperties();
		for (Pair pair : properties.getMembers()) {
			if ("issue".equals(pair.getKey())) {
				Element value = pair.getValue();
				if (value instanceof Primitive) {
					Primitive primitive = (Primitive) value;
					EObject eObject = primitive.getValue();
					if (eObject instanceof ExpressionString) {
						ExpressionString expr = (ExpressionString) eObject;
						return expr.getValue();
					}
				}
			}
		}
		return null;
	}

	private void test(String name) throws Exception {
		int failed = 0;
		String fileName = "com/neosyn/test/xfail/" + name + "." + FILE_EXT_CX;
		System.out.println("testing " + fileName);

		// initialize to '1' in case compileFile fails
		int total = 1;
		try {
			Module module = getModule(fileName);
			tester.validate(module);

			// set to 0
			total = 0;
			for (CgEntity entity : module.getEntities()) {
				total++;
				try {
					assertExpectedFail(entity);
				} catch (Throwable t) {
					printFailure(fileName, t);
					failed++;
				}
			}
		} catch (Throwable t) {
			printFailure(fileName, t);
			failed++;
		}

		testEnded(failed, total);
	}

}
