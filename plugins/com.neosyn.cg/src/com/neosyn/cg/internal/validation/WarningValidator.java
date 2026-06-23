/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.EValidatorRegistrar;

/**
 * This class was previously used for warnings in the Eclipse IDE.
 *
 * All validation checks have been moved to TypeScript providers in the VS Code extension
 * (CgDiagnosticsProvider.ts) since Xtext @Check validators don't run in the LSP context.
 *
 * The class is kept for Xtext's validation framework registration.
 */
public class WarningValidator extends AbstractDeclarativeValidator {

	@Override
	public void register(EValidatorRegistrar registrar) {
		// do nothing: packages are already registered by CgJavaValidator
	}

}
