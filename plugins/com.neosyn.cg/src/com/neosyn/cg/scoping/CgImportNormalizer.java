/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.impl.ImportNormalizer;

/**
 * This class describes an import normalizer that allows import of x.y.Bundle and properly resolves
 * references to Bundle.A, Bundle.B etc.
 * 

 * 
 */
public class CgImportNormalizer extends ImportNormalizer {

	public CgImportNormalizer(QualifiedName importedNamespace, boolean wildCard) {
		super(importedNamespace, wildCard, false);
	}

	@Override
	public QualifiedName deresolve(QualifiedName fullyQualifiedName) {
		if (getImportedNamespacePrefix().equals(fullyQualifiedName)) {
			return QualifiedName.create(fullyQualifiedName.getLastSegment());
		} else if (fullyQualifiedName.startsWith(getImportedNamespacePrefix()) && fullyQualifiedName
				.getSegmentCount() != getImportedNamespacePrefix().getSegmentCount()) {
			return fullyQualifiedName.skipFirst(getImportedNamespacePrefix().getSegmentCount());
		}
		return null;
	}

	@Override
	public QualifiedName resolve(QualifiedName relativeName) {
		if (relativeName.isEmpty()) {
			return null;
		} else if (hasWildCard()) {
			return getImportedNamespacePrefix().append(relativeName);
		} else if (getImportedNamespacePrefix().getLastSegment()
				.equals(relativeName.getFirstSegment())) {
			return getImportedNamespacePrefix().skipLast(1).append(relativeName);
		}
		return null;
	}

}
