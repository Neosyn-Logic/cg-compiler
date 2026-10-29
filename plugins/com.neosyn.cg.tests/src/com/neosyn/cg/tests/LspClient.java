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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A minimal JSON-RPC client over the built language-server jar's stdio: requests, notifications, and replies to
 * the server's own requests (registerCapability, configuration). Started with NEOSYN_CG_DEV=1.
 *
 * <p>Moved out of LspConcurrentCompileTests (T78) so other tests drive the jar the way VS Code does.
 */
public final class LspClient {
	private final Process process;
	private final OutputStream out;
	private final Map<Integer, JsonObject> responses = new HashMap<>();
	private final AtomicInteger nextId = new AtomicInteger();
	private volatile boolean dead;

	public LspClient(Path jar, Path cwd) throws IOException {
		String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		ProcessBuilder pb = new ProcessBuilder(java, "-jar", jar.toString());
		pb.environment().put("NEOSYN_CG_DEV", "1");
		pb.directory(cwd.toFile());
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		process = pb.start();
		out = process.getOutputStream();
		Thread reader = new Thread(this::readLoop, "lsp-reader");
		reader.setDaemon(true);
		reader.start();
	}

	private void readLoop() {
		try (InputStream in = process.getInputStream()) {
			while (true) {
				int length = -1;
				String line;
				while (!(line = readHeaderLine(in)).isEmpty()) {
					if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
						length = Integer.parseInt(line.substring(15).trim());
					}
				}
				byte[] body = in.readNBytes(length);
				if (body.length < length) {
					return;
				}
				JsonObject msg = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
				if (msg.has("method") && msg.has("id")) {
					// a request from the server (registerCapability, configuration): answer it
					JsonObject reply = new JsonObject();
					reply.addProperty("jsonrpc", "2.0");
					reply.add("id", msg.get("id"));
					reply.add("result", "workspace/configuration".equals(msg.get("method").getAsString())
							? new JsonArray() : JsonNull.INSTANCE);
					send(reply);
				} else if (msg.has("id") && !msg.has("method")) {
					synchronized (responses) {
						responses.put(msg.get("id").getAsInt(), msg);
						responses.notifyAll();
					}
				}
			}
		} catch (Exception e) {
			// the server exited or the stream closed
		} finally {
			dead = true;
			synchronized (responses) {
				responses.notifyAll();
			}
		}
	}

	private static String readHeaderLine(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c = in.read()) != -1 && c != '\n') {
			if (c != '\r') {
				sb.append((char) c);
			}
		}
		if (c == -1) {
			throw new IOException("end of stream");
		}
		return sb.toString();
	}

	private synchronized void send(JsonObject msg) throws IOException {
		byte[] body = msg.toString().getBytes(StandardCharsets.UTF_8);
		out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		out.write(body);
		out.flush();
	}

	public JsonObject request(String method, JsonObject params, int timeoutSeconds) throws Exception {
		int id = nextId.incrementAndGet();
		JsonObject msg = new JsonObject();
		msg.addProperty("jsonrpc", "2.0");
		msg.addProperty("id", id);
		msg.addProperty("method", method);
		msg.add("params", params == null ? JsonNull.INSTANCE : params);
		send(msg);
		long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
		synchronized (responses) {
			while (!responses.containsKey(id)) {
				long left = deadline - System.currentTimeMillis();
				if (dead) {
					throw new IOException("the language server exited during " + method);
				}
				if (left <= 0) {
					throw new IOException(method + " timed out after " + timeoutSeconds + " s");
				}
				responses.wait(Math.min(left, 1000));
			}
			JsonObject response = responses.remove(id);
			if (response.has("error")) {
				throw new IOException(method + " failed: " + response.get("error"));
			}
			JsonElement result = response.get("result");
			return result != null && result.isJsonObject() ? result.getAsJsonObject() : new JsonObject();
		}
	}

	public void notify(String method, JsonObject params) throws IOException {
		JsonObject msg = new JsonObject();
		msg.addProperty("jsonrpc", "2.0");
		msg.addProperty("method", method);
		if (params != null) {
			msg.add("params", params);
		}
		send(msg);
	}

	public void close() {
		try {
			request("shutdown", null, 30);
			notify("exit", null);
			process.waitFor(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			// fall through: the process is destroyed below
		} finally {
			process.destroyForcibly();
		}
	}
}
