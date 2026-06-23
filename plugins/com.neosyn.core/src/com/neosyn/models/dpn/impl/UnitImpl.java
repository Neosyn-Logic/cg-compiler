/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn.impl;

import org.eclipse.emf.ecore.EClass;

import com.neosyn.models.dpn.DpnPackage;
import com.neosyn.models.dpn.Unit;
import com.neosyn.models.ir.Procedure;

/**
 * <!-- begin-user-doc --> An implementation of the model object ' <em><b>Unit</b></em>'. <!--
 * end-user-doc -->
 *
 * @generated
 */
public class UnitImpl extends EntityImpl implements Unit {

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected UnitImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return DpnPackage.Literals.UNIT;
	}

	@Override
	public Procedure getProcedure(String name) {
		for (Procedure procedure : getProcedures()) {
			if (procedure.getName().equals(name)) {
				return procedure;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		if (eIsProxy())
			return super.toString();
		return "unit " + getName();
	}

}
