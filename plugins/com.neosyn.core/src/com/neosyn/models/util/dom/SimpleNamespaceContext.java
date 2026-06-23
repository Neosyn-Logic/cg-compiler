/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.util.dom;

import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.Iterators;

/**
 * This class describes a simple namespace context implementation.
 * 

 * 
 */
public class SimpleNamespaceContext implements NamespaceContext {

	private Element docElement;

	public SimpleNamespaceContext(Document document) {
		this.docElement = document.getDocumentElement();
	}

	@Override
	public String getNamespaceURI(String prefix) {
		return docElement.lookupNamespaceURI(prefix);
	}

	@Override
	public String getPrefix(String namespaceURI) {
		return docElement.lookupPrefix(namespaceURI);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Iterator getPrefixes(String namespaceURI) {
		return Iterators.singletonIterator(getPrefix(namespaceURI));
	}

}
