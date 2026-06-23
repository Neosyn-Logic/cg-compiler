/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.scoping;

import java.util.Iterator;

import org.eclipse.xtext.linking.impl.ImportedNamesAdapter;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;

/**
 * This class extends the default imported names adapter to use case-sensitive imported names. Makes
 * it easier to find objects directly using their qualified name.
 * 

 *
 */
public class CgImportedNamesAdapter extends ImportedNamesAdapter {

	public class CgWrappingScope extends WrappingScope {

		private IScope delegate;

		public CgWrappingScope(IScope scope) {
			super(scope);
			this.delegate = scope;
		}

		@Override
		public Iterable<IEObjectDescription> getElements(final QualifiedName name) {
			return new Iterable<IEObjectDescription>() {
				public Iterator<IEObjectDescription> iterator() {
					getImportedNames().add(name);
					final Iterable<IEObjectDescription> elements = delegate.getElements(name);
					return elements.iterator();
				}
			};
		}

		@Override
		public IEObjectDescription getSingleElement(QualifiedName name) {
			getImportedNames().add(name);
			return delegate.getSingleElement(name);
		}

	}

	@Override
	public IScope wrap(IScope scope) {
		return new CgWrappingScope(scope);
	}

}
