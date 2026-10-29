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
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_LITERAL_SUFFIX;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_RESERVED_WORD;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_IMPORT_ORDER;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_ARRAY_DIMS_HERE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_CONST_AFTER_TYPE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_DO_WHILE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_SWITCH;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_GENERIC_BRACKETS;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_CONST_NEEDS_TYPE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_BODY_NEEDS_BRACES;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_EMPTY_ANGLE_BRACKETS;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_SINGLE_QUOTE;
import static com.neosyn.cg.validation.IssueCodes.SYNTAX_ERROR_TOP_LEVEL_DECL;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.parser.antlr.SyntaxErrorMessageProvider;
import org.eclipse.xtext.IGrammarAccess;

import com.google.inject.Inject;

/**
 * This class provides custom messages for syntax error messages.
 * 
 */
public class CgSyntaxErrorMessageProvider extends SyntaxErrorMessageProvider {

	/**
	 * The offending token, as ANTLR quotes it in "mismatched input 'x'" and
	 * "no viable alternative at input 'x'".
	 */
	private static final Pattern OFFENDING_TOKEN = Pattern.compile("input '([^']*)'");

	/**
	 * Identifier-shaped, i.e. something a user could plausibly have MEANT as a name.
	 * Needed because Xtext counts every literal as a keyword, punctuation included, so
	 * "is a grammar keyword" alone would call `(` and `;` reserved words.
	 */
	private static final Pattern NAME_SHAPED = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	@Inject
	private IGrammarAccess grammarAccess;

	private Set<String> keywords;

	/**
	 * Derived from the LIVE grammar rather than hard-coded, so the list cannot drift as
	 * keywords are added. There is no reserved-word list published anywhere the user can
	 * read, which is precisely why the bare token in the default message is not enough to
	 * act on.
	 */
	private boolean isReservedWord(String token) {
		if (token == null || !NAME_SHAPED.matcher(token).matches()) {
			return false;
		}
		if (keywords == null) {
			keywords = GrammarUtil.getAllKeywords(grammarAccess.getGrammar());
		}
		return keywords.contains(token);
	}

	/** The token before the offending one, skipping whitespace and comments; null if unknown. */
	private static String previousToken(RecognitionException exc) {
		return previousToken(exc, 1);
	}

	/**
	 * The {@code n}th meaningful token before the offending one (1 = the one just before),
	 * skipping whitespace and comments; null if the stream does not say.
	 */
	private static String previousToken(RecognitionException exc, int n) {
		if (exc == null || !(exc.input instanceof TokenStream)) {
			return null;
		}
		TokenStream tokens = (TokenStream) exc.input;
		try {
			for (int i = exc.index - 1; i >= 0; i--) {
				String text = tokens.get(i).getText();
				String t = text == null ? "" : text.trim();
				if (!t.isEmpty() && !t.startsWith("//") && !t.startsWith("/*") && --n == 0) {
					return t;
				}
			}
		} catch (RuntimeException e) {
			// an index the stream does not hold: say nothing rather than guess
		}
		return null;
	}

	/**
	 * When the token just before the offending one is {@code )}, the meaningful token before its matching
	 * {@code (} -- for {@code switch (s) {} that is {@code switch}. Null when the shape is different or
	 * the stream does not say.
	 */
	private static String tokenBeforeParenGroup(RecognitionException exc) {
		if (exc == null || !(exc.input instanceof TokenStream)) {
			return null;
		}
		TokenStream tokens = (TokenStream) exc.input;
		try {
			int depth = 0;
			boolean seenClose = false;
			for (int i = exc.index - 1; i >= 0; i--) {
				String text = tokens.get(i).getText();
				String t = text == null ? "" : text.trim();
				if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*")) {
					continue;
				}
				if (!seenClose) {
					if (!")".equals(t)) {
						return null;
					}
					seenClose = true;
					depth = 1;
				} else if (depth > 0) {
					if (")".equals(t)) {
						depth++;
					} else if ("(".equals(t)) {
						depth--;
					}
				} else {
					return t;
				}
			}
		} catch (RuntimeException e) {
			// an index the stream does not hold: say nothing rather than guess
		}
		return null;
	}

	/** The first meaningful token after the offending one, skipping whitespace and comments; null if the stream does not say. */
	private static String nextToken(RecognitionException exc) {
		if (exc == null || !(exc.input instanceof TokenStream)) {
			return null;
		}
		TokenStream tokens = (TokenStream) exc.input;
		try {
			for (int i = exc.index + 1; i < tokens.size(); i++) {
				String text = tokens.get(i).getText();
				String t = text == null ? "" : text.trim();
				if (!t.isEmpty() && !t.startsWith("//") && !t.startsWith("/*")) {
					return t;
				}
			}
		} catch (RuntimeException e) {
			// an index the stream does not hold: say nothing rather than guess
		}
		return null;
	}

	/**
	 * A number immediately followed by a C/Java suffix (`123L`, `0xFFu`) in the statement
	 * around the error -- the same line as the offending token -- or null.
	 */
	private static String suffixedLiteral(RecognitionException exc) {
		if (exc == null || !(exc.input instanceof TokenStream) || exc.token == null) {
			return null;
		}
		TokenStream tokens = (TokenStream) exc.input;
		int line = exc.token.getLine();
		try {
			int from = Math.max(0, exc.index - 64);
			int to = Math.min(tokens.size() - 1, exc.index + 64);
			for (int i = from; i < to; i++) {
				Token number = tokens.get(i);
				Token next = tokens.get(i + 1);
				if (number.getLine() != line || next.getLine() != line || number.getText() == null
						|| next.getText() == null) {
					continue;
				}
				boolean adjacent = number.getCharPositionInLine() + number.getText().length()
						== next.getCharPositionInLine();
				if (adjacent && number.getText().matches("[0-9][0-9_]*|0[xX][0-9a-fA-F_]+")
						&& next.getText().matches("[lLuU]{1,3}")) {
					return number.getText() + next.getText();
				}
			}
		} catch (RuntimeException e) {
			// an index the stream does not hold: say nothing rather than guess
		}
		return null;
	}

	private static String offendingToken(String message) {
		Matcher m = OFFENDING_TOKEN.matcher(message);
		return m.find() ? m.group(1) : null;
	}

	@Override
	public SyntaxErrorMessage getSyntaxErrorMessage(IParserErrorContext context) {
		RecognitionException exc = context.getRecognitionException();

		String defaultMessage = context.getDefaultMessage();

		// A C/Java integer suffix: `6364136223846793005L`, `5u`. C⏚ literals take none, so the
		// lexer reads a number and then a NAME `L` -- and inside a cast the parser's lookahead
		// failed back at the cast's type, so the first error said "`uint` is a reserved word
		// ... rename it" (user report): advice to rename a type. Checked first: when the
		// statement holds such a literal, that is the mistake, whatever token derailed.
		String suffixed = suffixedLiteral(exc);
		if (suffixed != null) {
			String bare = suffixed.replaceAll("[lLuU]+$", "");
			return new SyntaxErrorMessage(
					"Syntax error: `" + suffixed + "` \u2014 a C\u23DA integer literal takes no suffix "
							+ "(`L`, `U`, `UL`): write `" + bare + "`. Its width comes from where it is "
							+ "used: the variable it is assigned to, or a cast such as `(uint<64>)`.",
					SYNTAX_ERROR_LITERAL_SUFFIX);
		}

		// M2 (model friendliness, 2026-09-30): shapes a model brings from other languages. The
		// default ANTLR text ("mismatched input '[' expecting '{'") names a token and no fix, and a
		// model that is not told the spelling rewrites the whole file instead of the line.
		String offending = exc != null && exc.token != null ? exc.token.getText() : null;
		String before2 = previousToken(exc, 2);
		if ("[".equals(offending) && ("task".equals(before2) || "network".equals(before2))) {
			String name = previousToken(exc);
			return new SyntaxErrorMessage(
					"Syntax error: C\u23DA declares parameters in ANGLE brackets, with `=` for the "
							+ "default: `" + before2 + " " + name + "<int W = 32> { ... }`, and each "
							+ "instance picks its value: `new " + name + "<64>()`. Inside, use them as "
							+ "constants and as widths (`uint<W>`).",
					SYNTAX_ERROR_GENERIC_BRACKETS);
		}
		// `switch (s) { case A: ... }` -- the C habit. `switch` is not a keyword, so the parser reads
		// `switch (s)` as a call and fails at the `{`; its default text ("mismatched input '{'
		// expecting ';'") and the cascade after it never said "switch" (TRAPS T171: the agent kit's
		// hint keyed on an older wording and went silent).
		if ("{".equals(offending) && "switch".equals(tokenBeforeParenGroup(exc))) {
			return new SyntaxErrorMessage(
					"Syntax error: C\u23DA has no `switch` / `case` statement. Write an if / else-if chain: "
							+ "`if (s == A) { ... } else if (s == B) { ... } else { ... }`. For a state machine, "
							+ "test an enum-typed state field the same way.",
					SYNTAX_ERROR_SWITCH);
		}
		// `do` is not a keyword, so a signal may be NAMED `do` (data out): only `do {` is the loop.
		if ("do".equals(offending) && "{".equals(nextToken(exc))
				|| "do".equals(previousToken(exc)) && "{".equals(offending)) {
			return new SyntaxErrorMessage(
					"Syntax error: C\u23DA has no `do { ... } while (c);`. Write "
							+ "`while (true) { ...; if (!c) { break; } }` -- the body runs first, as in "
							+ "a do-while.",
					SYNTAX_ERROR_DO_WHILE);
		}

		// `const` is a MODIFIER and the grammar puts it first -- both `VarDecl`
		// (`(constant ?= 'const')? (type | void) ...`) and `StatementVariable` read the
		// modifier before the type. Writing the type first is the C/Java habit, and it is
		// the shape a model reaches for, but it derails the parser at the `const` token:
		// the default message names the token and the two or three that follow are cascade,
		// so nothing on screen says "order".
		//
		// Worse, it is indistinguishable from the file-scope ORDERING error just below,
		// which the compiler explains well -- so the user is invited to look in exactly the
		// wrong place. Hedged the same way for the same reason: this message also resurfaces
		// as cascade damage once the parser has been derailed by something else.
		//
		// TWO parser messages, established by running each shape rather than assumed: a
		// MEMBER (`u16 const f()`, `u16 const K = 3`, and `const` used as a bare name) comes
		// out as "no viable alternative", while a LOCAL (`u8 const k = 2;` inside a body)
		// comes out as "extraneous input ... expecting RULE_ID". Same mistake, same remedy,
		// so both map here -- matching only the first left the local case, which is at least
		// as likely from a model, still reporting nothing about order.
		if (defaultMessage != null
				&& (defaultMessage.startsWith("no viable alternative at input 'const'")
						|| defaultMessage.startsWith("extraneous input 'const' expecting"))) {
			return new SyntaxErrorMessage(
					"Syntax error: `const` is a modifier and goes BEFORE the type \u2014 write "
							+ "`const u16 name`, not `u16 const name`. (It is also reserved, so "
							+ "it cannot be used as a name.) The order is the same for a member "
							+ "declaration and for a local variable. If the `const` named here "
							+ "is already written first, this is not the real error: the parser "
							+ "was derailed earlier, so fix the FIRST diagnostic and re-check.",
					SYNTAX_ERROR_CONST_AFTER_TYPE);
		}

		// A KEYWORD where a name was expected. The default message names the token and
		// stops there -- `out push bool ack;` reported "mismatched input 'ack' expecting
		// RULE_ID", and a user cannot know from that why an ordinary-looking port name was
		// refused. This shape shipped in the public docs for months.
		//
		// Checked LAST of the token-based branches, so the two that give better, more
		// specific advice for their own keyword (`const` order, array dimensions) still win.
		//
		// The reserved-word test is against the LIVE grammar, so it cannot drift; the
		// name-shaped test is what keeps it honest, because Xtext counts punctuation as
		// keywords too and `(` is not something anyone meant as a name.
		if (defaultMessage != null
				&& (defaultMessage.startsWith("mismatched input ")
						|| defaultMessage.startsWith("no viable alternative at input "))) {
			String token = offendingToken(defaultMessage);
			// `import` is never meant as a name: it reaches here when it comes AFTER other
			// declarations (`properties` first in a network, T101), and "rename it to
			// `import_`" sent the user after a fix that cannot exist.
			if ("import".equals(token)) {
				return new SyntaxErrorMessage(
						"Syntax error: `import` is out of place \u2014 imports go first: at the top of "
								+ "the file, just after `package`, or first in a task or network body, "
								+ "before `properties` and every other declaration. Move this `import` up.",
						SYNTAX_ERROR_IMPORT_ORDER);
			}
			if (isReservedWord(token)) {
				return new SyntaxErrorMessage(
						"Syntax error: `" + token + "` is a reserved word in C\u23DA and cannot "
								+ "be used as a name \u2014 rename it (for example `" + token
								+ "_` or a more descriptive name). Reserved words include the "
								+ "port protocols (`push`, `stream`, `confirm`, `sync`, `ready`, "
								+ "`ack`), the directions (`in`, `out`), and the declaration "
								+ "keywords (`task`, `network`, `bundle`, `const`, `typedef`, "
								+ "`struct`, `enum`, `properties`). If `" + token + "` is not meant "
								+ "as a name here, the word is out of place instead: check the order "
								+ "of the declarations, or fix the FIRST diagnostic and re-check.",
						SYNTAX_ERROR_RESERVED_WORD);
			}
		}

		// `[` where the declaration does not take dimensions. Array PORTS became legal on
		// 2026-09-09 (they fan out to one port per element), so this message no longer
		// covers them -- what remains is a struct field or a `typedef`.
		//
		// The message names every position rather than guessing at the user's intent: the
		// SAME parser message comes from a struct FIELD array and from a `typedef` array.
		// Established by running each position rather than inferred: dimensions ARE accepted
		// on a state variable, a local, a `const` (including at file scope) and a port, and
		// are NOT accepted on a struct field or a typedef. A stray `[` in an EXPRESSION is a
		// different message ("expecting ')'") and does not land here.
		if (defaultMessage != null
				&& defaultMessage.startsWith("mismatched input '[' expecting ';'")) {
			return new SyntaxErrorMessage(
					"Syntax error: array dimensions are not allowed in this declaration. "
							+ "`[...]` is accepted on a state variable, a local, a `const`, or "
							+ "a port \u2014 but not on a struct field or a `typedef`. If the "
							+ "`[` named here is in none of those places, this is not the real "
							+ "error: the parser was derailed earlier, so fix the FIRST "
							+ "diagnostic and re-check.",
					SYNTAX_ERROR_ARRAY_DIMS_HERE);
		}

		// "missing EOF at 'const'" is what the parser says when a declaration appears
		// where the module has already finished. Since file-scope declarations landed,
		// that no longer means "declarations are not allowed" -- they are, but only
		// BEFORE the first entity, so a file has at most one implicit bundle. Getting the
		// ORDER wrong is now the remaining way to hit this.
		//
		// Deliberately hedged at the end: the same message reappears as CASCADE damage --
		// once the parser has been derailed by something else (a `switch`, say, which
		// C(g) does not have), it resurfaces at an arbitrary token deep in the file, where
		// advice about file-scope ordering is irrelevant because the declaration is
		// usually already inside an entity.
		if (defaultMessage != null && defaultMessage.startsWith("missing EOF at ")) {
			return new SyntaxErrorMessage(
					"Syntax error: a file-scope declaration must come BEFORE the first "
							+ "`task`, `network` or `bundle` \u2014 move it up, just after the "
							+ "imports. (`const`, `typedef`, `struct` and `enum` may be written "
							+ "at file scope and are then in scope unqualified for the whole "
							+ "file.) If the token named here is already inside an entity, this "
							+ "is not the real error: the parser was derailed earlier, so fix "
							+ "the FIRST diagnostic and re-check.",
					SYNTAX_ERROR_TOP_LEVEL_DECL);
		}

		// `<>` -- empty angle brackets. Established by running each shape: `new T<>()`,
		// `task T<> {` and `int<> x` all come out as "no viable alternative at input '>'",
		// while a malformed comparison or shift names a different token (`)` or `;`). The
		// default message gave a model nothing to act on (a model-authoring trial, 2026-09-24: it wrote
		// `new IntakeSum<>()` meaning "the defaults"). Only when the token before the `>` really
		// is `<`, so this cannot fire on some other derailment that happens to stop at a `>`.
		if (defaultMessage != null && defaultMessage.startsWith("no viable alternative at input '>'")
				&& "<".equals(previousToken(exc))) {
			return new SyntaxErrorMessage(
					"Syntax error: `<>` is empty \u2014 angle brackets must hold something. To "
							+ "instantiate with the default parameters, leave them out: `new T()`, "
							+ "not `new T<>()`; to set one, `new T<327680>()`. A task with no "
							+ "parameters is `task T {`. A width goes inside them: `int<32>`, "
							+ "`uint<8>`.",
					SYNTAX_ERROR_EMPTY_ANGLE_BRACKETS);
		}

		// `const NAME = value;` -- a const with no TYPE. Measured: at file scope and as a member
		// it is "no viable alternative at input '='", as a local "missing RULE_ID at '='", and
		// the name is then misread as a type ("Couldn't resolve reference to NamedType"). A model
		// that hit it at file scope moved the const into the task and concluded that file-scope
		// consts are not allowed -- they are; the type was what was missing (a model-authoring trial).
		if (defaultMessage != null
				&& (defaultMessage.startsWith("no viable alternative at input '='")
						|| defaultMessage.startsWith("missing RULE_ID at '='"))
				&& "const".equals(previousToken(exc, 2))) {
			String name = previousToken(exc, 1);
			return new SyntaxErrorMessage(
					"Syntax error: a `const` needs a type \u2014 write `const u32 " + name
							+ " = ...;` (or `int<N>`, `uint<N>`, `bool` ...), not `const " + name
							+ " = ...;`. A typed `const` is allowed at file scope (before the "
							+ "first `task` or `network`), in a task, and as a local.",
					SYNTAX_ERROR_CONST_NEEDS_TYPE);
		}

		// A body without braces. Measured: `for (...) x;`, `while (...) x;`, `if (...) x;` and
		// `else x;` all come out as "missing '{' at '<first token of the body>'", with `)` or
		// `else` just before -- which is what this checks, so an unrelated missing `{` (after a
		// task name, say) keeps the parser's own message. The cascade that follows ("expecting
		// '}'" at the end of the file) disappears with the fix. A model hit this four times in one
		// turn and abandoned a working test (a model-authoring trial).
		if (defaultMessage != null && defaultMessage.startsWith("missing '{' at ")
				&& (")".equals(previousToken(exc)) || "else".equals(previousToken(exc)))) {
			return new SyntaxErrorMessage(
					"Syntax error: a body needs braces, even for one statement \u2014 C\u23DA has no "
							+ "brace-less `for`, `while`, `if` or `else`: write "
							+ "`for (...) { v.write(x); }`, not `for (...) v.write(x);`. The "
							+ "\"expecting '}'\" reported further down is the same mistake.",
					SYNTAX_ERROR_BODY_NEEDS_BRACES);
		}

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
