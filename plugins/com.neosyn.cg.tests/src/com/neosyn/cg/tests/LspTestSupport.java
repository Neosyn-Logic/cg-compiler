/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Assume;

/**
 * File and jar helpers shared by the tests that drive the built language-server jar.
 *
 * <p>Until REFACTOR step 0.3 (S206) each of these was a private copy in 21-24 test classes; two
 * copies of {@code deleteRecursive} had already drifted, and most copies never closed the
 * {@code Files.walk} stream. One definition, so the next jar test cannot get them subtly wrong.
 */
public final class LspTestSupport {

	private LspTestSupport() {
	}

	/** The neosyn-studio checkout, found by walking up from the working directory. */
	public static Path findWorkspaceRoot() {
		Path cwd = Paths.get("").toAbsolutePath();
		for (Path candidate = cwd; candidate != null; candidate = candidate.getParent()) {
			if (Files.isDirectory(candidate.resolve("plugins/com.neosyn.cg.tests"))
					&& Files.isDirectory(candidate.resolve("releng"))) {
				return candidate;
			}
		}
		throw new IllegalStateException(
				"Could not locate neosyn-studio workspace root from " + cwd);
	}

	/** The built language-server jar; the calling test is SKIPPED (not failed) when it is absent. */
	public static Path requireLspJar() {
		Path lspJar = findWorkspaceRoot().resolve("releng/lsp-server/target/cg-language-server.jar");
		Assume.assumeTrue(
				"LSP jar not built at " + lspJar + "; run `mvn package -pl releng/lsp-server` first",
				Files.isRegularFile(lspJar));
		return lspJar;
	}

	public static void writeUtf8(Path path, String content) throws IOException {
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
	}

	/** Best-effort removal of a temp tree; a file that cannot be deleted is left behind. */
	public static void deleteRecursive(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignored) {
							// best-effort temp cleanup
						}
					});
		}
	}
}
