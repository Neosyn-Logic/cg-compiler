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
import java.util.HashSet
import java.util.Map
import java.util.Set
import java.util.regex.Pattern
import org.eclipse.emf.common.util.TreeIterator
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil

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
	 * Whether the target folds identifier case. VHDL does, Verilog does not, and it decides
	 * two things: whether {@code TYPE} counts as the keyword {@code type}, and whether two
	 * spellings that differ only in case collide.
	 */
	val boolean foldsCase

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
	new(Set<String> reserved, String first, String last, boolean foldsCase) {
		this.reserved = reserved
		this.pattern = null
		this.first = first
		this.last = last
		this.foldsCase = foldsCase
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
	new(Set<String> reserved, Pattern pattern, String first, String last, boolean foldsCase) {
		this.reserved = reserved
		this.pattern = pattern
		this.first = first
		this.last = last
		this.foldsCase = foldsCase
	}

	protected new(Namer namer) {
		this.reserved = namer.reserved
		this.pattern = namer.pattern
		this.first = namer.first
		this.last = namer.last
		this.foldsCase = namer.foldsCase
	}

	/**
	 * The entity being printed. Names that belong to no entity -- an instance label, a signal the
	 * back end synthesizes -- are resolved against it.
	 */
	var Entity scope = null

	/** Lower-cased names declared per entity, so a renamed keyword can be checked against them. */
	val Map<Entity, Set<String>> declaredByEntity = newHashMap

	/**
	 * Per entity, the lower-cased names that more than one DISTINCT spelling maps to. Empty for a
	 * case-SENSITIVE target (Verilog), where two spellings are simply two names.
	 */
	val Map<Entity, Set<String>> collidingByEntity = newHashMap

	/** Per entity, the lower-cased names {@link #freshName} has handed out. */
	val Map<Entity, Set<String>> freshByEntity = newHashMap

	def private String escape(String name) {
		'''«first»«name»«last»'''
	}

	/**
	 * Declares the entity about to be printed: its names are what a keyword rename has to step
	 * over, and the ones that differ only in CASE are what has to be told apart.
	 *
	 * <p>C⏚ is case-sensitive and VHDL is not, so the ordinary convention of SCREAMING_CASE
	 * constants beside lower_case state — {@code const int CHECKS} next to {@code u16 checks}
	 * — produced VHDL that would not analyse:
	 *
	 * <pre>
	 * error: identifier "checks" already used for a declaration
	 * error: previous declaration: constant "CHECKS"
	 * </pre>
	 *
	 * and {@code generate} reported {@code Success!} regardless, so nothing surfaced until
	 * someone ran a VHDL tool. One core hit it three times in a single file.
	 *
	 * <p>Every member of a colliding group is escaped, not just one: a VHDL extended
	 * identifier is case-sensitive AND never equal to a basic identifier, so escaping all of
	 * them keeps the names distinct without depending on which was seen first.
	 */
	def setScope(Entity entity) {
		scope = entity
		// RE-index it. An entity may already have been indexed as some other entity's child, and
		// a transform runs between the two: PortNameDisambiguator renames a constant `Q` that
		// collides with a port `q` to `Q_v`, and an index taken before that still sees the
		// collision. At this point the entity about to be printed is fully transformed.
		declaredByEntity.remove(entity)
		collidingByEntity.remove(entity)
		// Called once per file printed: the fresh names start again at _1, so a regenerate in a
		// long LSP session prints the same file.
		freshByEntity.remove(entity)
		index(entity)
	}

	/**
	 * Indexes one entity: every name it declares, lower-cased, and the lower-cased names that
	 * more than one spelling maps to.
	 *
	 * <p>Collected reflectively over the whole containment tree rather than from a fixed list of
	 * ports/variables/procedures/states: a name that shadows another is a collision wherever it
	 * comes from, and a hand-kept list would silently miss whichever kind was added next.
	 */
	def private void index(Entity entity) {
		if (entity === null || declaredByEntity.containsKey(entity)) {
			return
		}
		// Bounded: recomputing is cheap and gives the same answer, so an LSP session that
		// generates for thousands of entities does not hold them all.
		if (declaredByEntity.size > 4096) {
			declaredByEntity.clear
			collidingByEntity.clear
		}
		val Map<String, Set<String>> byLower = newHashMap
		// typed: the (Notifier, boolean) overload would give a TreeIterator this code cannot walk
		val TreeIterator<EObject> all = EcoreUtil.getAllContents(entity, true)
		while (all.hasNext) {
			val EObject obj = all.next
			val feature = obj.eClass.getEStructuralFeature("name")
			if (feature instanceof EAttribute) {
				val value = obj.eGet(feature)
				if (value instanceof String) {
					val key = value.toLowerCase
					var spellings = byLower.get(key)
					if (spellings === null) {
						spellings = new HashSet<String>
						byLower.put(key, spellings)
					}
					spellings.add(value)
				}
			}
		}
		declaredByEntity.put(entity, new HashSet<String>(byLower.keySet))
		val Set<String> colliding = new HashSet<String>
		if (foldsCase) {
			for (entry : byLower.entrySet) {
				if (entry.value.size > 1) {
					colliding.add(entry.key)
				}
			}
		}
		collidingByEntity.put(entity, colliding)
	}

	/** The entity a printed object belongs to, or null when it belongs to none. */
	def private static Entity ownerOf(EObject object) {
		var EObject current = object
		while (current !== null) {
			if (current instanceof Entity) {
				return current as Entity
			}
			current = current.eContainer
		}
		null
	}

	def private Set<String> declaredIn(Entity entity) {
		index(entity)
		declaredByEntity.get(entity) ?: emptySet
	}

	def private Set<String> collidingIn(Entity entity) {
		index(entity)
		collidingByEntity.get(entity) ?: emptySet
	}

	/**
	 * A name the back end introduces itself -- {@code base_1}, {@code base_2}, ... -- that is new in
	 * the entity being printed: not declared by it (case-folded, so it holds for VHDL too), not
	 * reserved, and not handed out before. The caller keeps the answer; asking again gives the
	 * next one. A generated name must be unique against EVERY identifier in scope: a temporary
	 * spelled like a port once shadowed it (TRAPS T116).
	 */
	def String freshName(String base) {
		val declared = declaredIn(scope)
		var issued = freshByEntity.get(scope)
		if (issued === null) {
			issued = new HashSet<String>
			freshByEntity.put(scope, issued)
		}
		var n = 1
		var candidate = base + "_" + n
		while (declared.contains(candidate.toLowerCase) || reserved.contains(candidate.toLowerCase)
				|| issued.contains(candidate.toLowerCase)) {
			n = n + 1
			candidate = base + "_" + n
		}
		issued.add(candidate.toLowerCase)
		candidate
	}

	def getName(Entity entity) {
		getEntityName(entity.simpleName)
	}

	/**
	 * The name of a library unit -- a VHDL entity, a Verilog module. ESCAPED when the name itself
	 * forces it and never renamed, because the declaration, the {@code entity work.X} of every
	 * instantiation and the file on disk all have to agree, and they are printed from different
	 * places: renaming one of them gave {@code entity \Resize\ is} in the unit's own file beside
	 * {@code entity work.Resize_v} in the testbench, and GHDL could not find the unit.
	 *
	 * <p>Never because of a CASE collision either: an entity name is not declared in the
	 * architecture's declarative region, so it collides with nothing there, and the collisions a
	 * namer knows are those of whichever entity is being printed -- the same name would come out
	 * escaped in one file and bare in the next.
	 */
	def getEntityName(String name) {
		if (name === null) {
			return "__null__"
		}
		if (isKeyword(name) || hasIllegalSequence(name)) name.escape else name
	}

	def getName(Instance instance) {
		getSafeName(instance.name)
	}

	/**
	 * A port is resolved against the entity that DECLARES it, which is not always the one being
	 * printed: a network prints the ports of every instance in its port maps. Keying the rename
	 * of a keyword off the owner is what makes the two agree -- resolved against the network, a
	 * port named {@code body} could come out {@code body_v} there and {@code body_v2} in its own
	 * file, and the port map would name a port the component has not got.
	 */
	def getName(Port port) {
		val name = if (port.name !== null) {
			port.name
		} else {
			GeneratorExtensions.getPortName(port)
		}
		safeNameIn(name, ownerOf(port) ?: scope)
	}

	def getName(Procedure procedure) {
		safeNameIn(procedure.name, ownerOf(procedure) ?: scope)
	}

	def getName(State state) {
		safeNameIn(state.name, ownerOf(state) ?: scope)
	}

	def getName(Var variable) {
		safeNameIn(variable.name, ownerOf(variable) ?: scope)
	}

	/**
	 * The name to print: the name itself, a renamed one when it is a keyword of the target, or
	 * an escaped one when nothing else will do.
	 */
	def final getSafeName(String name) {
		safeNameIn(name, scope)
	}

	/**
	 * The name to print. {@code owner} is the entity that DECLARES it, which is what the rename of
	 * a keyword is keyed off so the same name comes out the same in the owner's file and in a
	 * parent's port map. Escaping for a CASE collision is keyed off the entity being PRINTED
	 * instead: two spellings collide inside one declarative region, and the region that matters is
	 * the file being written.
	 */
	def private String safeNameIn(String name, Entity owner) {
		if (name === null) {
			return "__null__"
		}
		if (isKeyword(name) && !collidingIn(scope).contains(name.toLowerCase) && !hasIllegalSequence(name)) {
			return rename(name, owner)
		}
		escapeIfNeeded(name)
	}

	/**
	 * A name that happens to be a keyword of the target is RENAMED -- {@code body} is printed
	 * {@code body_v} -- rather than printed as the escaped identifier {@code \body\} (VHDL) or
	 * {@code \body } (Verilog).
	 *
	 * <p>Escaping is legal in both languages and was correct, but it reads badly and it does not
	 * survive leaving the language: GHDL copies a VHDL extended identifier verbatim into the
	 * Verilog netlist it writes, where {@code \rem\;} swallows the semicolon that ends the
	 * declaration, and SeqDiv's netlist would not parse (TRAPS T116). Verilog's own escape is
	 * worse still, since it is terminated by a SPACE -- one reformatting tool away from being a
	 * different identifier. A rename has neither problem and is what a person would have written.
	 *
	 * <p>Only a keyword is renamed. Two spellings that collide because VHDL folds their case
	 * ({@code CHECKS} beside {@code checks}) keep escaping: no keyword is involved, and escaping
	 * is precisely what keeps them distinct.
	 */
	def private String rename(String name, Entity owner) {
		// The owner's declarations, so the answer does not depend on which file is being written.
		// They are read as of the first time the owner was indexed; the only names a later
		// transform adds are the disambiguator's own `_v` suffixes, which it only creates for a
		// declaration that shares a PORT's name -- and that would be a second declaration spelled
		// like the keyword port itself. GHDL then names the port it cannot find, loudly.
		val declared = declaredIn(owner)
		var candidate = name + "_v"
		var n = 2
		while (declared.contains(candidate.toLowerCase) || reserved.contains(candidate.toLowerCase)) {
			candidate = name + "_v" + n
			n = n + 1
		}
		candidate
	}

	/** Whether the name is a keyword of the target -- case-insensitively only where case is folded. */
	def private boolean isKeyword(String name) {
		reserved.contains(if (foldsCase) name.toLowerCase else name)
	}

	/** Whether the name holds a character sequence the target forbids (a leading or doubled underscore). */
	def private boolean hasIllegalSequence(String name) {
		pattern !== null && pattern.matcher(name).find
	}

	/**
	 * If the given name needs to be escaped, escapes it.
	 */
	def private escapeIfNeeded(String name) {
		if (name === null) {
			return "__null__"
		}
		if (isKeyword(name) || collidingIn(scope).contains(name.toLowerCase) || hasIllegalSequence(name)) {
			name.escape
		} else {
			name
		}
	}

}
