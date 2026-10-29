/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.util;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.neosyn.models.dpn.Entity;

/**
 * Decides which entity "Simulate" runs for a source file.
 *
 * <p>The simulate handler hands this EVERY entity of the project -- the IR of all its
 * files, and of its {@code cg.deps} -- but the question is about ONE file. Choosing
 * across the whole project was TRAPS T87: {@code TestFullStack.cg}, a network with no
 * {@code test} property, ran {@code Test_ArpSendHeader} from another file and reported
 * "Simulation completed successfully (8 checks)", while the ambiguity warning said the
 * file held four testbenches that were all in other files.
 *
 * <p>Auto-selection (no entity named in the request), in order:
 * <ol>
 * <li>{@code <name>_test}, wherever it is declared -- a naming contract, not a guess;</li>
 * <li>among the entities declared in the TARGET FILE (hoisted children carry their
 * parent's file name): a testbench named {@code <name>}, then any testbench, then a
 * test-by-name that is not a hoisted child, then {@code <name>}, then the first
 * entity that is not a hoisted child;</li>
 * <li>if no entity records the target file (IR without file names), {@code <name>}
 * anywhere -- and otherwise nothing.</li>
 * </ol>
 * It never borrows another file's testbench. A file without one runs its own entity,
 * and the handler's zero-check guard then reports that the run verified nothing.
 *
 * <p>An explicit entity name must match. It used to fall back to the project's first
 * entity, which silently ran an arbitrary design.
 */
public final class SimTargetSelector {

	/** What {@link #select} chose, and the testbenches it counted for the ambiguity warning. */
	public static final class Selection {
		private final Entity chosen;
		private final List<Entity> testbenchesInFile;

		Selection(Entity chosen, List<Entity> testbenchesInFile) {
			this.chosen = chosen;
			this.testbenchesInFile = Collections.unmodifiableList(testbenchesInFile);
		}

		/** The entity to simulate, or {@code null} when nothing matches. */
		public Entity getChosen() {
			return chosen;
		}

		/** The testbenches (entities with a {@code test} property) declared in the target file. */
		public List<Entity> getTestbenchesInFile() {
			return testbenchesInFile;
		}
	}

	private SimTargetSelector() {
	}

	/**
	 * @param entities   every entity loaded for the project, in any order
	 * @param targetFile the {@code .cg} file the user asked to simulate
	 * @param targetName the entity name requested, or the file's base name
	 * @param explicit   whether {@code targetName} was named in the request
	 */
	public static Selection select(List<Entity> entities, String targetFile, String targetName,
			boolean explicit) {
		if (explicit) {
			return new Selection(named(entities, targetName), new ArrayList<>());
		}

		Path target = normalise(targetFile);
		List<Entity> inFile = new ArrayList<>();
		for (Entity e : entities) {
			if (target != null && target.equals(normalise(e.getFileName()))) {
				inFile.add(e);
			}
		}
		List<Entity> testbenches = new ArrayList<>();
		for (Entity e : inFile) {
			if (isTestbench(e)) {
				testbenches.add(e);
			}
		}

		Entity chosen = targetName == null ? null : named(entities, targetName + "_test");
		if (chosen == null && inFile.isEmpty()) {
			chosen = named(entities, targetName);
		}
		if (chosen == null) {
			chosen = named(testbenches, targetName);
		}
		if (chosen == null && !testbenches.isEmpty()) {
			chosen = testbenches.get(0);
		}
		if (chosen == null) {
			for (Entity e : inFile) {
				if (isTestByName(e) && !isHoistedChild(e, inFile)) {
					chosen = e;
					break;
				}
			}
		}
		if (chosen == null) {
			chosen = named(inFile, targetName);
		}
		if (chosen == null) {
			for (Entity e : inFile) {
				if (!isHoistedChild(e, inFile)) {
					chosen = e;
					break;
				}
			}
		}
		if (chosen == null && !inFile.isEmpty()) {
			chosen = inFile.get(0);
		}
		return new Selection(chosen, testbenches);
	}

	/** A real testbench network: it carries a {@code test} property. */
	public static boolean isTestbench(Entity entity) {
		JsonObject props = entity.getProperties();
		return props != null && props.has("test");
	}

	/** The name heuristic: ends in {@code _test} or contains {@code Test}. */
	public static boolean isTestByName(Entity entity) {
		String n = entity.getSimpleName();
		return n != null && (n.endsWith("_test") || n.contains("Test"));
	}

	/**
	 * Test code, by any of the product's three conventions: a {@code test} property, the name
	 * convention ({@link #isTestByName}), or a {@code test} / {@code tests} segment in its package.
	 * Most core testbenches carry only the second or third -- {@code TopTest}, {@code TestSha},
	 * {@code net/tests/TestPhy}, and the helper {@code intel8088/test/InnerNet} that only tests
	 * instantiate -- so checking the property alone put a vendor reset synchronizer into them.
	 */
	public static boolean isTest(Entity entity) {
		if (isTestbench(entity) || isTestByName(entity)) {
			return true;
		}
		String pkg = entity.getPackage();
		if (pkg != null) {
			for (String segment : pkg.split("\\.")) {
				if ("test".equals(segment) || "tests".equals(segment)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A child hoisted out of a network in the same file, named {@code <network>_<instance>}
	 * -- e.g. {@code FifoThoroughTest_producer}. It contains "Test" through its parent, so
	 * the name heuristic must not pick it over the parent. {@code Bar_test} is exempt: it is
	 * the test OF {@code Bar}, not a child of it.
	 */
	private static boolean isHoistedChild(Entity entity, List<Entity> sameFile) {
		String n = entity.getSimpleName();
		if (n == null || n.endsWith("_test")) {
			return false;
		}
		for (Entity other : sameFile) {
			String o = other.getSimpleName();
			if (other != entity && o != null && n.startsWith(o + "_")) {
				return true;
			}
		}
		return false;
	}

	private static Entity named(List<Entity> candidates, String simpleName) {
		if (simpleName == null) {
			return null;
		}
		for (Entity e : candidates) {
			if (simpleName.equals(e.getSimpleName())) {
				return e;
			}
		}
		return null;
	}

	/** Absolute, normalised, and symlink-resolved when the file exists; null if unusable. */
	private static Path normalise(String file) {
		if (file == null) {
			return null;
		}
		try {
			Path p = Paths.get(file).toAbsolutePath().normalize();
			try {
				return p.toRealPath();
			} catch (IOException | SecurityException e) {
				return p;
			}
		} catch (InvalidPathException e) {
			return null;
		}
	}
}
