/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.generator;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IFileSystemAccess;
import org.eclipse.xtext.generator.IGenerator;

/**
 * A no-op generator for standalone LSP mode.
 *
 * The real CgGenerator depends on Eclipse workspace APIs (ResourcesPlugin)
 * which are not available in standalone mode. This generator does nothing,
 * since code generation is not needed for IDE features like completion,
 * validation, and navigation.
 */
public class CgNoOpGenerator implements IGenerator {

    @Override
    public void doGenerate(Resource input, IFileSystemAccess fsa) {
        // No-op: Code generation is not performed in standalone/LSP mode.
        // The actual CgGenerator requires Eclipse workspace APIs that
        // are not available outside of the Eclipse IDE.
    }
}
