/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.node;

import java.util.ListIterator;

/**
 * This class defines an iterator over a node's children.
 * 

 * 
 */
public class NodeIterator implements ListIterator<Node> {

	private Node currentNode;

	private Node referenceNode;

	public NodeIterator(Node node) {
		this.referenceNode = node;
	}

	@Override
	public void add(Node e) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the current node.
	 * 
	 * @return a node
	 */
	public Node current() {
		if (currentNode == null) {
			currentNode = referenceNode.getFirstChild();
		}
		return currentNode;
	}

	/**
	 * Returns what the next node would be, but does not advance this iterator.
	 * 
	 * @return a node
	 */
	public Node getNext() {
		if (currentNode == null) {
			return referenceNode.getFirstChild();
		} else {
			return currentNode.getNextSibling();
		}
	}

	/**
	 * Returns what the previous node would be, but does not advance this iterator.
	 * 
	 * @return a node
	 */
	private Node getPrevious() {
		if (currentNode == null) {
			return referenceNode.getLastChild();
		} else {
			return currentNode.getPreviousSibling();
		}
	}

	@Override
	public boolean hasNext() {
		return getNext() != null;
	}

	@Override
	public boolean hasPrevious() {
		return getPrevious() != null;
	}

	@Override
	public Node next() {
		currentNode = getNext();
		return currentNode;
	}

	@Override
	public int nextIndex() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Node previous() {
		currentNode = getPrevious();
		return currentNode;
	}

	@Override
	public int previousIndex() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void remove() {
		currentNode.remove();
		currentNode = null;
	}

	/**
	 * Resets this node iterator.
	 */
	public void reset() {
		currentNode = null;
	}

	@Override
	public void set(Node e) {
		throw new UnsupportedOperationException();
	}

}
