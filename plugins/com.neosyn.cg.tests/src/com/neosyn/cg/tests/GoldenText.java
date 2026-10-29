/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

/**
 * Normalisation shared by every test that compares generated text against a checked-in golden.
 *
 * <p>The IR records the ABSOLUTE path of its source: {@code fileName="/home/<user>/<checkout>/
 * plugins/com.neosyn.cg.tests/tests/golden/GoldenFSM.cg"}. Compared verbatim, every IR golden passed
 * only for the user and checkout that wrote it. On the nightly build machine all 28 GoldenFileTests IR
 * cases failed, and IrPathDriftTests -- which compares the LSP's IR against the SAME golden files --
 * failed there the first time it ran at all (S180). One definition, so the next golden comparison
 * cannot forget it.
 */
public final class GoldenText {

	private GoldenText() {
	}

	/** Keep the part of each source path inside this bundle; drop where the checkout lives. */
	public static String normalizeSourcePaths(String content) {
		return content.replaceAll("fileName=\"[^\"]*/(tests/golden/[^\"]*)\"", "fileName=\"$1\"");
	}
}
