/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.node;

import java.util.Iterator;

/**
 * This class defines an iterable over nodes.
 * 

 * 
 */
public class NodeIterable implements Iterable<Node> {

	private Node node;

	public NodeIterable(Node node) {
		this.node = node;
	}

	@Override
	public Iterator<Node> iterator() {
		return new NodeIterator(node);
	}

}
