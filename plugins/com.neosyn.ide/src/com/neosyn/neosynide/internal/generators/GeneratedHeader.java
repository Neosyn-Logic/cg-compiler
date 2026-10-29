/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import static com.neosyn.core.IProperties.PROP_COPYRIGHT;
import static com.neosyn.core.IProperties.PROP_JAVADOC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.neosyn.models.dpn.Entity;

/**
 * What the header of a generated HDL file may say about who wrote the design. Everything comes
 * from the {@code .cg} source; nothing from the machine that ran the compiler. The header used to
 * print the OS login ({@code Author : <login>} in every sold core built here), a year with no
 * holder, and a "project" taken from the first segment of an absolute path ({@code home}).
 */
public final class GeneratedHeader {

	/** {@code @author Jane Doe} or {@code Author: Jane Doe}, as a whole comment line. */
	private static final Pattern AUTHOR = Pattern.compile("^\\s*(?:@author\\s+|author\\s*:\\s*)(\\S.*?)\\s*$",
			Pattern.CASE_INSENSITIVE);

	private GeneratedHeader() {
	}

	/**
	 * The author the source names -- in the entity's own doc comment first, then in the file's
	 * header comment -- or {@code null} when it names none, in which case the header has no
	 * Author line at all.
	 */
	public static String author(Entity entity) {
		for (String key : new String[] { PROP_JAVADOC, PROP_COPYRIGHT }) {
			JsonArray lines = entity.getProperties().getAsJsonArray(key);
			if (lines == null) {
				continue;
			}
			for (JsonElement line : lines) {
				Matcher m = AUTHOR.matcher(line.getAsString());
				if (m.matches()) {
					return m.group(1);
				}
			}
		}
		return null;
	}
}
