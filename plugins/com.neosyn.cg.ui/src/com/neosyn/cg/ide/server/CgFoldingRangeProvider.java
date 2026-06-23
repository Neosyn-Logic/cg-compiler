/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.folding.DefaultFoldingRangeProvider;
import org.eclipse.xtext.ide.editor.folding.IFoldingRangeAcceptor;
import org.eclipse.xtext.resource.XtextResource;

import com.neosyn.cg.cg.Block;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.Enum;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Struct;
import com.neosyn.cg.cg.Task;

/**
 * Folding-range provider for C⏚ (editor polish E3).
 *
 * <p>Xtext's {@link DefaultFoldingRangeProvider} folds <em>every</em> handled
 * multi-line AST node, which for C⏚ is noisy (every expression list, property
 * object, port-decl group, etc. becomes a fold). This subclass restricts
 * folding to the structural blocks a developer actually collapses:
 * <ul>
 *   <li>{@link Task} bodies — including anonymous inline tasks (the
 *       {@code x = new task { … }} "monitor" pattern, which is a {@code Task});</li>
 *   <li>{@link Network} bodies;</li>
 *   <li>{@link Bundle} bodies;</li>
 *   <li>{@link Struct} and {@link Enum} bodies;</li>
 *   <li>statement / function {@link Block}s (the body of {@code void loop() { … }},
 *       {@code if}/{@code for}/{@code while}, etc.).</li>
 * </ul>
 *
 * <p>Note C⏚ has no {@code fsm}/{@code states}/{@code monitor} syntax — FSMs are
 * inferred by the compiler and "monitor" is just a conventional inner-task name,
 * so there is nothing extra to fold for those.
 *
 * <p>Comment folding is suppressed: VS Code's TextMate grammar already provides
 * block-comment folding, and emitting it here too produces duplicate fold
 * controls. Contiguous {@code import} runs are intentionally out of scope for
 * iter #1 (they are sibling statements, not a single AST node).
 */
public class CgFoldingRangeProvider extends DefaultFoldingRangeProvider {

	@Override
	protected boolean isHandled(EObject eObject) {
		return eObject instanceof Task
				|| eObject instanceof Network
				|| eObject instanceof Bundle
				|| eObject instanceof Struct
				|| eObject instanceof Enum
				|| eObject instanceof Block;
	}

	/**
	 * Suppress LSP-side comment folding; VS Code handles block comments via its
	 * TextMate grammar, so emitting them here would double-fold.
	 */
	@Override
	protected void computeCommentFolding(XtextResource resource, IFoldingRangeAcceptor acceptor) {
		// intentionally no-op — see class doc
	}
}
