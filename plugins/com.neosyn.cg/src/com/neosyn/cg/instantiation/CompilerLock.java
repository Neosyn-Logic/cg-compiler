/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.instantiation;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The one lock for the C⏚ compiler's shared state.
 *
 * <p>The instantiator is a Guice {@code @Singleton} whose mappings are replaced on every compile
 * ({@code clearData()} + {@code updateDirect(module)}), the language server hands every request the same
 * workspace resource set, and the code generators are singletons with per-run fields. The language
 * server runs its custom requests -- IR generation, HDL generation, simulation, the FSM and Graph views
 * -- on pool threads, so without this lock a view refresh could wipe the mappings of a compile in
 * progress: TRAPS T78, AES failing IR generation in the IDE with "Port not found" while the CLI stayed
 * clean, reproduced 8/8 by requesting {@code neosyn/getFsm} during {@code neosyn/generateIR}.
 *
 * <p>Reentrant, so a locked operation may call another (simulate -> generateIR -> generateIRForFile).
 * Hold it only around compiler work, never around a running simulation.
 */
public final class CompilerLock {

	private static final ReentrantLock LOCK = new ReentrantLock();

	private CompilerLock() {
	}

	/** Runs {@code body} holding the lock and returns its result. */
	public static <T> T call(Supplier<T> body) {
		LOCK.lock();
		try {
			return body.get();
		} finally {
			LOCK.unlock();
		}
	}

	public static void lock() {
		LOCK.lock();
	}

	public static void unlock() {
		LOCK.unlock();
	}

	/** How many times the current thread holds the lock (0 if it does not). */
	public static int holdCount() {
		return LOCK.getHoldCount();
	}

	/**
	 * Releases the current thread's holds down to {@code holds}, a count read with {@link #holdCount()}
	 * before locking. For code that must release early on one path and also in a {@code finally}.
	 */
	public static void unlockTo(int holds) {
		while (LOCK.isHeldByCurrentThread() && LOCK.getHoldCount() > holds) {
			LOCK.unlock();
		}
	}
}
