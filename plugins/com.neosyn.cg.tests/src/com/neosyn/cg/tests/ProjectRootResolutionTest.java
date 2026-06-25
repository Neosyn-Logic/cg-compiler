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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.neosyn.cg.ide.server.ServerUtils;

/**
 * Guards {@link ServerUtils#findProjectRoot}, which drives the cross-file
 * resource walk for the LSP/CLI generate, IR and simulate paths.
 *
 * <p>Regression for the SHA-256 step1/step2 cross-directory import bug: a
 * {@code .cg} file in a sub-package directory (e.g. {@code .../sha256/step1/}
 * declaring {@code package com.neosyn.sha256.step1}) imports a bundle in the
 * parent-package directory ({@code com.neosyn.sha256.SHACommon} in
 * {@code .../sha256/}). The root must resolve to the parent-package directory
 * so the sibling bundle is loaded — otherwise its symbols stay unresolved
 * proxies and the transform NPEs ({@code Variable.eContainer()} on null).
 */
public class ProjectRootResolutionTest {

	private Path tmp;

	@Before
	public void setUp() throws IOException {
		tmp = Files.createTempDirectory("cg-root-test");
	}

	@After
	public void tearDown() throws IOException {
		if (tmp != null && Files.exists(tmp)) {
			Files.walk(tmp).sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			});
		}
	}

	private Path writeCg(Path dir, String name, String pkg) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve(name);
		Files.write(file, ("package " + pkg + ";\n\ntask " + name.replace(".cg", "") + " {}\n").getBytes());
		return file;
	}

	/**
	 * A file in a sub-package directory must root at the parent-package
	 * directory so a sibling bundle there is co-loaded.
	 */
	@Test
	public void subdirectoryImportResolvesToPackageRoot() throws IOException {
		Path sha256 = tmp.resolve("sha256");
		writeCg(sha256, "SHACommon.cg", "com.neosyn.sha256");
		Path step1 = writeCg(sha256.resolve("step1"), "SHA256_step1.cg", "com.neosyn.sha256.step1");

		assertEquals(sha256.toString(), ServerUtils.findProjectRoot(step1.toString()));
	}

	/**
	 * The parent-package file itself stays anchored at the package root
	 * (unchanged from the previous behaviour).
	 */
	@Test
	public void siblingFileStaysAtPackageRoot() throws IOException {
		Path sha256 = tmp.resolve("sha256");
		Path common = writeCg(sha256, "SHACommon.cg", "com.neosyn.sha256");

		assertEquals(sha256.toString(), ServerUtils.findProjectRoot(common.toString()));
	}

	/**
	 * A two-level-deep sub-package directory walks all the way up to the
	 * common package-prefix directory.
	 */
	@Test
	public void deepSubdirectoryResolvesToTopMatchingDirectory() throws IOException {
		Path sha256 = tmp.resolve("sha256");
		Path leaf = writeCg(sha256.resolve("step2").resolve("inner"), "Deep.cg",
				"com.neosyn.sha256.step2.inner");

		assertEquals(sha256.toString(), ServerUtils.findProjectRoot(leaf.toString()));
	}

	/**
	 * When the directory name does not mirror the trailing package segment,
	 * the file's own directory is used — identical to the previous fallback,
	 * so unrelated layouts are unaffected.
	 */
	@Test
	public void nonMirroringLayoutUsesFileDirectory() throws IOException {
		Path dir = tmp.resolve("examples");
		Path file = writeCg(dir, "Foo.cg", "com.example.foo");

		assertEquals(dir.toString(), ServerUtils.findProjectRoot(file.toString()));
	}

	/** A {@code .project} marker takes precedence over package-based resolution. */
	@Test
	public void projectFileMarkerTakesPrecedence() throws IOException {
		Path proj = tmp.resolve("proj");
		Files.createDirectories(proj);
		Files.write(proj.resolve(".project"), "<projectDescription/>".getBytes());
		Path file = writeCg(proj.resolve("sha256").resolve("step1"), "X.cg",
				"com.neosyn.sha256.step1");

		assertEquals(proj.toString(), ServerUtils.findProjectRoot(file.toString()));
	}

	/** A {@code src/} marker takes precedence and roots at its parent. */
	@Test
	public void srcMarkerTakesPrecedence() throws IOException {
		Path proj = tmp.resolve("proj");
		Path file = writeCg(proj.resolve("src").resolve("com").resolve("neosyn"), "X.cg",
				"com.neosyn");

		assertEquals(proj.toString(), ServerUtils.findProjectRoot(file.toString()));
	}
}
