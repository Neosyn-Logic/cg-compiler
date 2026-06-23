/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.core.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Centralized debug logging for the Neosyn SDK.
 *
 * <p>This utility provides consistent debug logging across all SDK components.
 * Logs can be written to files and/or stderr, with category-based filtering.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * // Simple logging
 * DebugLogger.log(Category.IR, "Processing entity: " + name);
 *
 * // With exception
 * DebugLogger.log(Category.BYTECODE, "Compilation failed", exception);
 *
 * // Check if enabled before expensive operations
 * if (DebugLogger.isEnabled(Category.SCOPE)) {
 *     DebugLogger.log(Category.SCOPE, buildExpensiveMessage());
 * }
 * </pre>
 *
 * <h2>Configuration</h2>
 * <p>Logging can be configured via system properties:</p>
 * <ul>
 *   <li>{@code neosyn.debug.enabled} - Enable/disable all logging (default: true)</li>
 *   <li>{@code neosyn.debug.file} - Enable file logging (default: true)</li>
 *   <li>{@code neosyn.debug.console} - Enable console logging (default: false)</li>
 *   <li>{@code neosyn.debug.categories} - Comma-separated list of enabled categories</li>
 * </ul>
 */
public final class DebugLogger {

	/**
	 * Log categories for filtering debug output.
	 */
	public enum Category {
		/** IR generation and instantiation */
		IR("neosyn-ir-debug.log"),
		/** Bytecode compilation */
		BYTECODE("neosyn-bytecode-debug.log"),
		/** Scope resolution */
		SCOPE("neosyn-scope-debug.log"),
		/** HDL generation (Verilog/VHDL) */
		HDL("neosyn-hdl-debug.log"),
		/** Simulation */
		SIM("neosyn-sim-debug.log"),
		/** Specializer and properties */
		SPECIALIZER("neosyn-ir-debug.log"),
		/** General/uncategorized */
		GENERAL("neosyn-debug.log");

		private final String logFileName;

		Category(String logFileName) {
			this.logFileName = logFileName;
		}

		public String getLogFileName() {
			return logFileName;
		}
	}

	/** Base directory for log files */
	private static final String LOG_DIR = System.getProperty("user.home");

	/** Date/time formatter for log entries */
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	/** Global enable flag - disabled by default, enable with -Dneosyn.debug.enabled=true */
	private static volatile boolean enabled = Boolean.parseBoolean(
			System.getProperty("neosyn.debug.enabled", "false"));

	/** File logging flag */
	private static volatile boolean fileLoggingEnabled = Boolean.parseBoolean(
			System.getProperty("neosyn.debug.file", "true"));

	/** Console logging flag */
	private static volatile boolean consoleLoggingEnabled = Boolean.parseBoolean(
			System.getProperty("neosyn.debug.console", "false"));

	/** Enabled categories (all enabled by default) */
	private static final Set<Category> enabledCategories = EnumSet.allOf(Category.class);

	// Private constructor - utility class
	private DebugLogger() {
	}

	/**
	 * Checks if logging is enabled for a category.
	 *
	 * @param category the log category
	 * @return true if logging is enabled for this category
	 */
	public static boolean isEnabled(Category category) {
		return enabled && enabledCategories.contains(category);
	}

	/**
	 * Logs a message.
	 *
	 * @param category the log category
	 * @param message the message to log
	 */
	public static void log(Category category, String message) {
		if (!isEnabled(category)) {
			return;
		}

		String timestamp = LocalDateTime.now().format(TIME_FMT);
		String formattedMsg = String.format("[%s] [%s] %s", timestamp, category.name(), message);

		if (fileLoggingEnabled) {
			writeToFile(category, formattedMsg);
		}

		if (consoleLoggingEnabled) {
			System.err.println(formattedMsg);
		}
	}

	/**
	 * Logs a message with an exception.
	 *
	 * @param category the log category
	 * @param message the message to log
	 * @param throwable the exception to log
	 */
	public static void log(Category category, String message, Throwable throwable) {
		if (!isEnabled(category)) {
			return;
		}

		StringWriter sw = new StringWriter();
		throwable.printStackTrace(new PrintWriter(sw));
		log(category, message + "\n" + sw.toString());
	}

	/**
	 * Logs a formatted message.
	 *
	 * @param category the log category
	 * @param format the format string
	 * @param args the format arguments
	 */
	public static void logf(Category category, String format, Object... args) {
		if (!isEnabled(category)) {
			return;
		}
		log(category, String.format(format, args));
	}

	/**
	 * Writes a message to the log file for a category.
	 */
	private static void writeToFile(Category category, String message) {
		Path logPath = Paths.get(LOG_DIR, category.getLogFileName());
		try {
			Files.writeString(logPath, message + "\n",
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			// Fall back to stderr if file writing fails
			System.err.println("[DebugLogger] Failed to write to " + logPath + ": " + e.getMessage());
			System.err.println(message);
		}
	}

	/**
	 * Clears the log file for a category.
	 *
	 * @param category the category whose log file to clear
	 */
	public static void clearLog(Category category) {
		Path logPath = Paths.get(LOG_DIR, category.getLogFileName());
		try {
			Files.deleteIfExists(logPath);
		} catch (IOException e) {
			// Ignore
		}
	}

	/**
	 * Clears all log files.
	 */
	public static void clearAllLogs() {
		for (Category cat : Category.values()) {
			clearLog(cat);
		}
	}

	// =========================================================================
	// Configuration methods
	// =========================================================================

	/**
	 * Enables or disables all debug logging.
	 *
	 * @param enable true to enable, false to disable
	 */
	public static void setEnabled(boolean enable) {
		enabled = enable;
	}

	/**
	 * Enables or disables file logging.
	 *
	 * @param enable true to enable file logging
	 */
	public static void setFileLoggingEnabled(boolean enable) {
		fileLoggingEnabled = enable;
	}

	/**
	 * Enables or disables console logging.
	 *
	 * @param enable true to enable console logging
	 */
	public static void setConsoleLoggingEnabled(boolean enable) {
		consoleLoggingEnabled = enable;
	}

	/**
	 * Enables a specific category.
	 *
	 * @param category the category to enable
	 */
	public static void enableCategory(Category category) {
		enabledCategories.add(category);
	}

	/**
	 * Disables a specific category.
	 *
	 * @param category the category to disable
	 */
	public static void disableCategory(Category category) {
		enabledCategories.remove(category);
	}

	/**
	 * Enables only the specified categories.
	 *
	 * @param categories the categories to enable
	 */
	public static void setEnabledCategories(Category... categories) {
		enabledCategories.clear();
		for (Category cat : categories) {
			enabledCategories.add(cat);
		}
	}

	/**
	 * Enables all categories.
	 */
	public static void enableAllCategories() {
		enabledCategories.addAll(EnumSet.allOf(Category.class));
	}

	// =========================================================================
	// Convenience methods for backward compatibility
	// =========================================================================

	/**
	 * Logs an IR-related message.
	 * Convenience method for {@code log(Category.IR, message)}.
	 */
	public static void ir(String message) {
		log(Category.IR, message);
	}

	/**
	 * Logs a bytecode-related message.
	 * Convenience method for {@code log(Category.BYTECODE, message)}.
	 */
	public static void bytecode(String message) {
		log(Category.BYTECODE, message);
	}

	/**
	 * Logs a scope-related message.
	 * Convenience method for {@code log(Category.SCOPE, message)}.
	 */
	public static void scope(String message) {
		log(Category.SCOPE, message);
	}

	/**
	 * Logs an HDL-related message.
	 * Convenience method for {@code log(Category.HDL, message)}.
	 */
	public static void hdl(String message) {
		log(Category.HDL, message);
	}

	/**
	 * Logs a simulation-related message.
	 * Convenience method for {@code log(Category.SIM, message)}.
	 */
	public static void sim(String message) {
		log(Category.SIM, message);
	}
}
