/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.validation;

import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_ARRAY_BRACE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_SINGLE_QUOTE;

import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.RecognitionException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.parser.antlr.SyntaxErrorMessageProvider;

/**
 * This class provides custom messages for syntax error messages.
 * 

 * 
 */
public class CgSyntaxErrorMessageProvider extends SyntaxErrorMessageProvider {

	@Override
	public SyntaxErrorMessage getSyntaxErrorMessage(IParserErrorContext context) {
		RecognitionException exc = context.getRecognitionException();
		if (exc == null) {
			// lexer error
			String msg = context.getDefaultMessage();
			if (msg.startsWith("mismatched character") && msg.endsWith("expecting '''")) {
				return new SyntaxErrorMessage(
						"Syntax error: expected one character surrounded by single quotes, for strings use double quotes",
						SYNTAX_ERROR_SINGLE_QUOTE);
			}
		} else if (exc instanceof NoViableAltException) {
			INode node = context.getCurrentNode();
			if (node != null) {
				EObject element = node.getGrammarElement();
				if (element != null && element instanceof RuleCall) {
					RuleCall call = (RuleCall) element;
					if ("Value".equals(call.getRule().getName())) {
						return new SyntaxErrorMessage(
								"Syntax error: expected curly braces { } for array",
								SYNTAX_ERROR_ARRAY_BRACE);
					}
				}
			}
		}

		return super.getSyntaxErrorMessage(context);
	}

}
