/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators

import com.neosyn.models.dpn.Entity
import com.neosyn.models.dpn.Instance
import com.neosyn.models.dpn.Port
import com.neosyn.models.dpn.State
import com.neosyn.models.ir.Procedure
import com.neosyn.models.ir.Var
import java.util.Set
import java.util.regex.Pattern

/**
 * This class defines a Namer, that knows how to print ports and variables, and
 * escape them if necessary.
 * 

 */
class Namer {

	val String first

	val String last

	val Pattern pattern

	val Set<String> reserved

	/**
	 * Creates a new namer.
	 * 
	 * @param reserved
	 *            set of reserved identifiers
	 * @param first
	 *            first character of the escape sequence
	 * @param last
	 *            first character of the escape sequence
	 */
	new(Set<String> reserved, String first, String last) {
		this.reserved = reserved
		this.pattern = null
		this.first = first
		this.last = last
	}

	/**
	 * Creates a new namer.
	 * 
	 * @param reserved
	 *            set of reserved identifiers
	 * @param pattern
	 *            pattern that identifies reserved character sequences (may be
	 *            <code>null</code>)
	 * @param first
	 *            first character of the escape sequence
	 * @param last
	 *            first character of the escape sequence
	 */
	new(Set<String> reserved, Pattern pattern, String first, String last) {
		this.reserved = reserved
		this.pattern = pattern
		this.first = first
		this.last = last
	}

	protected new(Namer namer) {
		this.reserved = namer.reserved
		this.pattern = namer.pattern
		this.first = namer.first
		this.last = namer.last
	}

	def private String escape(String name) {
		'''«first»«name»«last»'''
	}

	def getName(Entity entity) {
		getSafeName(entity.simpleName)
	}

	def getName(Instance instance) {
		getSafeName(instance.name)
	}

	def getName(Port port) {
		val name = if (port.name !== null) {
			port.name
		} else {
			GeneratorExtensions.getPortName(port)
		}
		getSafeName(name)
	}

	def getName(Procedure procedure) {
		getSafeName(procedure.name)
	}

	def getName(State state) {
		getSafeName(state.name)
	}

	def getName(Var variable) {
		getSafeName(variable.name)
	}

	/**
	 * If the given name needs to be escaped, escapes it.
	 */
	def final getSafeName(String name) {
		if (name === null) {
			return "__null__"
		}
		if (needsEscaping(name)) {
			name.escape
		} else {
			name
		}
	}

	/**
	 * Returns <code>true</code> if the given name needs escaping.
	 * 
	 * @param name
	 *            name of a variable, parameter, port, procedure...
	 * @return <code>true</code> if the given name needs escaping
	 */
	def private needsEscaping(String name) {
		reserved.contains(name.toLowerCase()) || (pattern !== null && pattern.matcher(name).find())
	}

}
