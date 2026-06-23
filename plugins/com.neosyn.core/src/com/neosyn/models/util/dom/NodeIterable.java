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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.UnmodifiableIterator;

/**
 * This class defines an iterable over DOM Nodes.
 * 

 * 
 */
public class NodeIterable implements Iterable<Node> {

	private class NodeIterator extends UnmodifiableIterator<Node> {

		private int i;

		public NodeIterator() {
			i = 0;
		}

		@Override
		public boolean hasNext() {
			return i < nodes.getLength();
		}

		@Override
		public Node next() {
			return nodes.item(i++);
		}

	}

	private NodeList nodes;

	public NodeIterable(NodeList nodes) {
		this.nodes = nodes;
	}

	@Override
	public Iterator<Node> iterator() {
		return new NodeIterator();
	}

}
