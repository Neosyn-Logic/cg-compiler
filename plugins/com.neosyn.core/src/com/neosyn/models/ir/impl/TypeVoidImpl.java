/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.impl;

import org.eclipse.emf.ecore.EClass;

import com.neosyn.models.ir.IrPackage;
import com.neosyn.models.ir.TypeVoid;
import com.neosyn.models.ir.util.TypePrinter;

/**
 * This class defines a void type.
 * 

 * @author Jerome Gorin
 * 
 */
public class TypeVoidImpl extends TypeImpl implements TypeVoid {

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected TypeVoidImpl() {
		super();
	}

	@Override
	public boolean equals(Object obj) {
		return (obj instanceof TypeVoid);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return IrPackage.Literals.TYPE_VOID;
	}

	@Override
	public boolean isVoid() {
		return true;
	}

	@Override
	public String toString() {
		return new TypePrinter().toString(this);
	}

}
