/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgFactory;
import com.neosyn.cg.internal.scheduler.path.Path;
import com.neosyn.models.node.Node;

/**
 * Unit tests for {@link Path} class.
 *
 * <p>Regression tests for NoSuchElementException when path entries are exhausted
 * by nested if-statements. The IfDeveloper consumes path entries via getNext(),
 * and if there are more nested ifs than path entries, it previously threw.</p>
 */
public class PathTest {

	private static final CgFactory cg = CgFactory.eINSTANCE;

	/**
	 * Helper to create a Node containing a Branch.
	 */
	private Node createBranchNode() {
		Branch branch = cg.createBranch();
		Node node = new Node(branch);
		return node;
	}

	/**
	 * getNext() on empty path should return null, not throw.
	 */
	@Test
	public void testGetNextOnEmptyPath() {
		Path path = new Path();
		assertNull("getNext() on empty path should return null", path.getNext());
	}

	/**
	 * getNext() returns null after all entries are consumed.
	 */
	@Test
	public void testGetNextExhausted() {
		Path path = new Path();
		path.add(createBranchNode());

		Branch first = path.getNext();
		assertNotNull("First getNext() should return a Branch", first);

		Branch second = path.getNext();
		assertNull("Second getNext() on single-entry path should return null", second);
	}

	/**
	 * getNext() returns null multiple times after exhaustion (idempotent).
	 */
	@Test
	public void testGetNextExhaustedMultipleCalls() {
		Path path = new Path();
		path.add(createBranchNode());

		path.getNext(); // consume the only entry
		assertNull("First call after exhaustion should return null", path.getNext());
		assertNull("Second call after exhaustion should return null", path.getNext());
		assertNull("Third call after exhaustion should return null", path.getNext());
	}

	/**
	 * getNext() returns entries in correct order for multi-entry paths.
	 */
	@Test
	public void testGetNextMultipleEntries() {
		Path path = new Path();

		Branch b1 = cg.createBranch();
		Branch b2 = cg.createBranch();
		Branch b3 = cg.createBranch();

		// addFirst means first added is last returned (stack-like)
		path.add(new Node(b1));
		path.add(new Node(b2));
		path.add(new Node(b3));

		// Deque with addFirst: b3 is at front, b1 at back
		Branch first = path.getNext();
		Branch second = path.getNext();
		Branch third = path.getNext();

		assertNotNull("First entry should not be null", first);
		assertNotNull("Second entry should not be null", second);
		assertNotNull("Third entry should not be null", third);
		assertEquals("First should be b3 (last added = first out)", b3, first);
		assertEquals("Second should be b2", b2, second);
		assertEquals("Third should be b1", b1, third);

		assertNull("Fourth getNext() should return null", path.getNext());
	}

	/**
	 * Nodes that don't contain a Branch are ignored by add().
	 */
	@Test
	public void testAddNonBranchNode() {
		Path path = new Path();
		// Node with non-Branch content
		Node node = new Node("not a branch");
		path.add(node);

		assertNull("getNext() should return null since no Branch was added", path.getNext());
	}

	/**
	 * toString() on empty path should not throw.
	 */
	@Test
	public void testToStringEmpty() {
		Path path = new Path();
		String result = path.toString();
		assertNotNull("toString should not return null", result);
		assertEquals("Empty path toString should be empty string", "", result);
	}
}
