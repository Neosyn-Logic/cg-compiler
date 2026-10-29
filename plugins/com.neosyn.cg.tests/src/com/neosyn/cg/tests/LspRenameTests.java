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
import static org.junit.Assert.assertTrue;
import static com.neosyn.cg.tests.LspTestSupport.requireLspJar;
import static com.neosyn.cg.tests.LspTestSupport.writeUtf8;
import static com.neosyn.cg.tests.LspTestSupport.deleteRecursive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Rename (F2 in VS Code) through the built jar, as the editor sends it: {@code textDocument/rename}.
 *
 * <p>REFACTOR 4a.2 (S206): the language server's rename is bound by Xtext's
 * {@code ui.refactoring.RefactorElementNameFragment2} -- a "ui" fragment that is NOT Eclipse-only: it writes the
 * IRenameService2 binding into AbstractCgIdeModule. Removing it with the other Eclipse UI fragments changed that
 * class, and no test noticed; only a per-class jar diff did. These tests make the next removal fail here.
 *
 * <p>Skipped when the standalone LSP jar isn't built.
 */
public class LspRenameTests {

	private static final String PKG = "src/com/neosyn/lsprename/";

	private static final String COUNTER = "package com.neosyn.lsprename;\n"
			+ "task Counter {\n"
			+ "\tout u8 count;\n"
			+ "\tu8 value = 0;\n"
			+ "\tvoid loop() {\n"
			+ "\t\tcount.write(value);\n"
			+ "\t\tvalue++;\n"
			+ "\t}\n"
			+ "}\n";

	private static final String NET = "package com.neosyn.lsprename;\n"
			+ "network Net {\n"
			+ "\tcounter = new Counter();\n"
			+ "\tsink = new task {\n"
			+ "\t\tin u8 data;\n"
			+ "\t\tout u8 last;\n"
			+ "\t\tvoid loop() {\n"
			+ "\t\t\tlast.write(data.read());\n"
			+ "\t\t}\n"
			+ "\t};\n"
			+ "\tsink.reads(counter.count);\n"
			+ "}\n";

	@Test
	public void renameStateVariableEditsEveryUse() throws Exception {
		Map<String, List<String>> edits = rename("Counter.cg", COUNTER, 3, "value", "tally");
		assertEquals("files edited: " + edits, Collections.singleton(uri("Counter.cg")), edits.keySet());
		List<String> got = edits.get(uri("Counter.cg"));
		assertEquals("edits in Counter.cg (declaration, write(value), value++): " + got, 3, got.size());
		assertTrue(got.toString(), got.stream().allMatch(e -> e.endsWith("value -> tally")));
		assertTrue("declaration line not edited: " + got, got.contains("3:4 value -> tally"));
	}

	@Test
	public void renameTaskEditsTheNetworkInAnotherFile() throws Exception {
		Map<String, List<String>> edits = rename("Counter.cg", COUNTER, 1, "Counter", "Ticker");
		assertEquals("files edited: " + edits, 2, edits.size());
		assertEquals(edits.toString(), List.of("1:5 Counter -> Ticker"), edits.get(uri("Counter.cg")));
		assertEquals(edits.toString(), List.of("2:15 Counter -> Ticker"), edits.get(uri("Net.cg")));
	}

	// --- helpers ---

	private Path projectDir;

	private String uri(String file) {
		return projectDir.resolve(PKG + file).toUri().toString();
	}

	/**
	 * Renames the first {@code oldName} on {@code line} (0-based) of {@code file}. Returns, per file URI, each edit as
	 * "line:character oldText -> newText", sorted, with oldText read from the file at the edit's range.
	 */
	private Map<String, List<String>> rename(String file, String text, int line, String oldName, String newName)
			throws Exception {
		Path lspJar = requireLspJar();
		projectDir = Files.createTempDirectory("lsp-rename-");
		LspClient lsp = null;
		try {
			Path pkg = Files.createDirectories(projectDir.resolve(PKG));
			writeUtf8(projectDir.resolve(".project"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<projectDescription><name>lsp-rename</name></projectDescription>\n");
			writeUtf8(pkg.resolve("Counter.cg"), COUNTER);
			writeUtf8(pkg.resolve("Net.cg"), NET);

			lsp = new LspClient(lspJar, projectDir);
			JsonObject init = new JsonObject();
			init.add("processId", JsonNull.INSTANCE);
			init.addProperty("rootUri", projectDir.toUri().toString());
			init.add("capabilities", new JsonObject());
			JsonObject initResult = lsp.request("initialize", init, 180);
			JsonElement provider = initResult.getAsJsonObject("capabilities").get("renameProvider");
			assertTrue("the server does not advertise renameProvider: " + initResult,
					provider != null && !(provider.isJsonPrimitive() && !provider.getAsBoolean()));
			lsp.notify("initialized", new JsonObject());

			JsonObject doc = new JsonObject();
			doc.addProperty("uri", uri(file));
			doc.addProperty("languageId", "cg");
			doc.addProperty("version", 1);
			doc.addProperty("text", text);
			JsonObject open = new JsonObject();
			open.add("textDocument", doc);
			lsp.notify("textDocument/didOpen", open);

			int character = text.split("\n", -1)[line].indexOf(oldName);
			assertTrue("fixture: '" + oldName + "' not on line " + line, character >= 0);
			JsonObject id = new JsonObject();
			id.addProperty("uri", uri(file));
			JsonObject position = new JsonObject();
			position.addProperty("line", line);
			position.addProperty("character", character + 1);
			JsonObject params = new JsonObject();
			params.add("textDocument", id);
			params.add("position", position);
			params.addProperty("newName", newName);
			JsonObject edit = lsp.request("textDocument/rename", params, 120);
			return describe(edit);
		} finally {
			if (lsp != null) {
				lsp.close();
			}
			deleteRecursive(projectDir);
		}
	}

	/** WorkspaceEdit (changes or documentChanges) -> uri -> sorted "line:char oldText -> newText". */
	private Map<String, List<String>> describe(JsonObject edit) throws IOException {
		Map<String, List<String>> out = new java.util.TreeMap<>();
		if (edit.has("changes") && edit.get("changes").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : edit.getAsJsonObject("changes").entrySet()) {
				out.put(e.getKey(), describeEdits(e.getKey(), e.getValue().getAsJsonArray()));
			}
		}
		if (edit.has("documentChanges") && edit.get("documentChanges").isJsonArray()) {
			for (JsonElement dc : edit.getAsJsonArray("documentChanges")) {
				JsonObject o = dc.getAsJsonObject();
				if (o.has("textDocument") && o.has("edits")) {
					String u = o.getAsJsonObject("textDocument").get("uri").getAsString();
					out.put(u, describeEdits(u, o.getAsJsonArray("edits")));
				}
			}
		}
		assertTrue("textDocument/rename returned no edits: " + edit, !out.isEmpty());
		return out;
	}

	private List<String> describeEdits(String fileUri, JsonArray edits) throws IOException {
		String[] lines = new String(Files.readAllBytes(Path.of(java.net.URI.create(fileUri))), StandardCharsets.UTF_8)
				.split("\n", -1);
		List<String> out = new ArrayList<>();
		for (JsonElement el : edits) {
			JsonObject te = el.getAsJsonObject();
			JsonObject start = te.getAsJsonObject("range").getAsJsonObject("start");
			JsonObject end = te.getAsJsonObject("range").getAsJsonObject("end");
			int l = start.get("line").getAsInt();
			int c = start.get("character").getAsInt();
			assertEquals("edit spans lines: " + te, l, end.get("line").getAsInt());
			String old = lines[l].substring(c, end.get("character").getAsInt());
			out.add(l + ":" + c + " " + old + " -> " + te.get("newText").getAsString());
		}
		Collections.sort(out);
		return out;
	}
}
