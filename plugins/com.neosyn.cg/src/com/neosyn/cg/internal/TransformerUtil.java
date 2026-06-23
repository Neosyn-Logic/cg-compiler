/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal;

import static java.math.BigInteger.ZERO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.neosyn.cg.cg.Statement;
import com.neosyn.models.ir.ExprBool;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.Expression;

/**
 * This class defines utility functions for transformation of Cx.
 * 

 * 
 */
public class TransformerUtil {

	/**
	 * Returns the line at which the given object ends.
	 * 
	 * @param object
	 *            an AST object
	 * @return the line at which the given object ends
	 */
	public static int getEndLine(EObject object) {
		ICompositeNode node = NodeModelUtils.getNode(object);
		if (node == null) {
			object = object.eContainer();
			if (object == null) {
				return 0;
			} else {
				return getEndLine(object);
			}
		} else {
			return node.getEndLine() + 1;
		}
	}

	/**
	 * Returns the line at which the given object starts.
	 * 
	 * @param object
	 *            an AST object
	 * @return the line at which the given object starts
	 */
	public static int getStartLine(EObject object) {
		ICompositeNode node = NodeModelUtils.getNode(object);
		if (node == null) {
			object = object.eContainer();
			if (object == null) {
				return 0;
			} else {
				return getStartLine(object);
			}
		} else {
			return node.getStartLine();
		}
	}

	/**
	 * Returns <code>true</code> if <code>expr</code> is an ExprBool whose value is
	 * <code>false</code>.
	 * 
	 * @param expr
	 *            an expression
	 * @return a boolean indicating whether <code>expr</code> is an ExprBool whose value is
	 *         <code>false</code>
	 */
	public static boolean isFalse(Expression expr) {
		return expr instanceof ExprBool && !((ExprBool) expr).isValue();
	}

	/**
	 * Returns <code>true</code> if <code>expr</code> is an ExprInt whose value is different from
	 * <code>0</code>.
	 * 
	 * @param expr
	 *            an expression
	 * @return a boolean indicating whether <code>expr</code> is an ExprInt whose value is different
	 *         from <code>0</code>
	 */
	public static boolean isOne(Expression expr) {
		return expr instanceof ExprInt && ((ExprInt) expr).getValue().compareTo(ZERO) != 0;
	}

	/**
	 * Returns <code>true</code> if <code>expr</code> is an ExprBool whose value is
	 * <code>true</code>.
	 * 
	 * @param expr
	 *            an expression
	 * @return a boolean indicating whether <code>expr</code> is an ExprBool whose value is
	 *         <code>true</code>
	 */
	public static boolean isTrue(Expression expr) {
		return expr instanceof ExprBool && ((ExprBool) expr).isValue();
	}

	/**
	 * Returns <code>true</code> if <code>expr</code> is an ExprInt whose value equals
	 * <code>0</code>.
	 * 
	 * @param expr
	 *            an expression
	 * @return a boolean indicating whether <code>expr</code> is an ExprInt whose value equals
	 *         <code>0</code>
	 */
	public static boolean isZero(Expression expr) {
		return expr instanceof ExprInt && ((ExprInt) expr).getValue().compareTo(ZERO) == 0;
	}

	public static void mkComment(Statement statement) {
		List<String> lines = new ArrayList<>();
		ICompositeNode node = NodeModelUtils.findActualNodeFor(statement);
		for (ILeafNode leaf : node.getLeafNodes()) {
			if (leaf.isHidden()) {
				String text = leaf.getText();
				EObject eObject = leaf.getGrammarElement();
				if (eObject instanceof TerminalRule) {
					TerminalRule rule = (TerminalRule) eObject;
					String name = rule.getName();
					if ("SL_COMMENT".equals(name)) {
						lines.add(text.substring(2).trim());
					} else if ("ML_COMMENT".equals(name)) {
						String contents = text.substring(2, text.length() - 2);
						String[] lineArray = contents.split("\\r?\\n");
						lines.addAll(Arrays.asList(lineArray));
					} else {
						continue;
					}
				}
			}
		}
	}

}
