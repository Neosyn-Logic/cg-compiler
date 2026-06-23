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
import java.io.InputStream;
import java.io.OutputStream;

import com.neosyn.core.NeosynCore;

/**
 * This class defines a thread that copies an input stream to an output stream. This is intended to
 * be used when running commands in tests.
 * 

 * 
 */
public class StreamCopier extends Thread {

	private InputStream source;

	private OutputStream target;

	public StreamCopier(InputStream source, OutputStream target) {
		this.source = source;
		this.target = target;
	}

	@Override
	public void run() {
		byte[] buf = new byte[4096];
		try {
			int n = source.read(buf);
			while (n != -1) {
				target.write(buf, 0, n);
				target.flush();
				n = source.read(buf);
			}
		} catch (IOException e) {
			NeosynCore.log(e);
		}
	}

}
