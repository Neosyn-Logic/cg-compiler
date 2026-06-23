/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_MISMATCH;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;

import com.neosyn.cg.services.VoidCxSwitch;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.util.TypePrinter;
import com.neosyn.models.ir.util.TypeUtil;

/**
 * This class defines an abstract checker, that provides "error" protected methods similar to
 * Validator.
 * 

 * 
 */
public abstract class Checker extends VoidCxSwitch {

	protected ValidationMessageAcceptor acceptor;

	public Checker() {
	}

	public Checker(ValidationMessageAcceptor acceptor) {
		this.acceptor = acceptor;
	}

	/**
	 * Checks the expression can be assigned to the variable.
	 * 
	 * @param source
	 *            AST node to use to signal an error
	 * @param typeTgt
	 *            type of target
	 * @param typeSrc
	 *            type of source
	 */
	protected void checkAssign(Type typeTgt, Type typeSrc, EObject source,
			EStructuralFeature feature, int index) {
		if (typeTgt == null || typeSrc == null) {
			return;
		}

		if (!TypeUtil.canAssign(typeSrc, typeTgt)) {
			error("Type mismatch: cannot convert from " + new TypePrinter().toString(typeSrc)
					+ " to " + new TypePrinter().toString(typeTgt), source, feature, index,
					ERR_TYPE_MISMATCH);
		}
	}

	/**
	 * Checks the expression can be assigned to the variable, and implicitly cast to bool if needed.
	 * 
	 * @param source
	 *            AST node to use to signal an error
	 * @param typeTgt
	 *            type of target
	 * @param typeSrc
	 *            type of source
	 */
	protected void checkAssignImplicitBool(Type typeTgt, Type typeSrc, EObject source,
			EStructuralFeature feature, int index) {
		if (typeTgt == null || typeSrc == null) {
			return;
		}

		if (!TypeUtil.canAssignBool(typeSrc, typeTgt)) {
			error("Type mismatch: cannot convert from " + new TypePrinter().toString(typeSrc)
					+ " to " + new TypePrinter().toString(typeTgt), source, feature, index,
					ERR_TYPE_MISMATCH);
		}
	}

	protected void error(String message, EObject source, EStructuralFeature feature, int index) {
		error(message, source, feature, index, null);
	}

	protected void error(String message, EObject source, EStructuralFeature feature, int index,
			String code, String... issueData) {
		acceptor.acceptError(message, source, feature, index, code, issueData);
	}

	protected void error(String message, EObject source, EStructuralFeature feature, String code,
			String... issueData) {
		acceptor.acceptError(message, source, feature,
				ValidationMessageAcceptor.INSIGNIFICANT_INDEX, code, issueData);
	}

	/**
	 * If the acceptor is not set at construction time (for example if this checker is injected),
	 * this method allows users to set it later.
	 * 
	 * @param acceptor
	 */
	public void setValidator(ValidationMessageAcceptor acceptor) {
		this.acceptor = acceptor;
	}

	protected void warning(String message, EObject source, EStructuralFeature feature) {
		warning(message, source, feature, ValidationMessageAcceptor.INSIGNIFICANT_INDEX);
	}

	protected void warning(String message, EObject source, EStructuralFeature feature, int index) {
		warning(message, source, feature, index, null);
	}

	protected void warning(String message, EObject source, EStructuralFeature feature, int index,
			String code, String... issueData) {
		acceptor.acceptWarning(message, source, feature, index, code, issueData);
	}

}
