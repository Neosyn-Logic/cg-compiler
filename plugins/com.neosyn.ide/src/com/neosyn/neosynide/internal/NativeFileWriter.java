/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.neosyn.core.IFileWriter;
import com.neosyn.core.NeosynCore;

/**
 * This class defines an implementation of a IFileWriter based on the native Java file classes. The
 * name of a file must be relative to the output directory.
 * 

 * 
 */
public class NativeFileWriter implements IFileWriter {

	private static final boolean RUNNING_ON_WINDOWS = java.io.File.separatorChar == '\\';

	private String outputFolder;
	
	@Override
	public boolean exists(String fileName) {
		return Files.exists(getPath(fileName));
	}

	@Override
	public String getAbsolutePath(String fileName) {
		String path = getPath(fileName).toAbsolutePath().toString();
		if (RUNNING_ON_WINDOWS) {
			path = path.replace('\\', '/');
		}
		return path;
	}

	private Path getPath(String fileName) {
		return Paths.get(outputFolder, fileName);
	}

	@Override
	public void remove(String fileName) {
		if (exists(fileName)) {
			try {
				Files.delete(getPath(fileName));
			} catch (IOException e) {
				NeosynCore.log(e);
			}
		}
	}

	@Override
	public void setOutputFolder(String folder) {
		outputFolder = folder;
	}

	@Override
	public void write(String fileName, CharSequence sequence) {
		if (sequence == null) {
			return;
		}

		Path path = getPath(fileName);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, sequence.toString().getBytes());
		} catch (IOException e) {
			NeosynCore.log(e);
		}
	}

	@Override
	public void write(String fileName, InputStream source) {
		Path path = getPath(fileName);
		try {
			Files.createDirectories(path.getParent());
			Files.copy(source, path, REPLACE_EXISTING);
		} catch (IOException e) {
			NeosynCore.log(e);
		}
	}

}
