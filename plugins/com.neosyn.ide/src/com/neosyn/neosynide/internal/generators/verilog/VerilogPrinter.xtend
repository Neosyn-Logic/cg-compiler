/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.neosynide.internal.generators.GeneratedHeader
import com.google.gson.JsonArray
import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.Actor
import com.neosyn.models.dpn.DPN
import com.neosyn.models.dpn.Direction
import com.neosyn.models.dpn.Entity
import com.neosyn.models.dpn.Port
import com.neosyn.models.dpn.Unit
import com.neosyn.models.dpn.util.DpnSwitch
import com.neosyn.neosynide.internal.generators.BuiltinEntityHelper
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.internal.generators.verilog.actor.ActorPrinter
import com.neosyn.neosynide.internal.generators.verilog.dpn.DpnPrinter
import com.neosyn.models.ir.Type
import com.neosyn.models.ir.TypeInt
import java.util.ArrayList
import java.util.List

import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_COPYRIGHT
import static com.neosyn.core.IProperties.PROP_IMPORTS
import static com.neosyn.core.IProperties.PROP_JAVADOC
import static com.neosyn.core.IProperties.PROP_RESETS

import static extension com.neosyn.models.ir.util.IrUtil.getSimpleName

/**
 * This class defines the Verilog printer for actors, units, networks.
 * 


 */
class VerilogPrinter extends DpnSwitch<CharSequence> {

	def private static printImports(Entity entity) {
		var imports = entity.properties.getAsJsonArray(PROP_IMPORTS)
		'''
		«FOR path : imports»
			`include "«path.asString.simpleName».v"
		«ENDFOR»
		'''
	}

	val VerilogIrPrinter irPrinter

	val Namer namer
	
	val IPathResolver pathResolver

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		irPrinter = new VerilogIrPrinter(namer, pathResolver)
		this.pathResolver = pathResolver
	}

	def private void addPorts(List<CharSequence> signals, Port port, Entity entity) {
		val needReg = port.eContainer instanceof Actor
		val outputDir = '''output«IF needReg» reg«ENDIF»'''
		val signalDir = if (port.direction == Direction.INPUT) 'input' else outputDir

		// Try to get resolved port type from cache (for inline tasks connecting to built-in entities)
		val portType = getResolvedPortType(entity, port)
		signals.add('''«signalDir» «irPrinter.doSwitch(portType)» «namer.getName(port)»''')

		for (signal : port.additionalInputs) {
			signals.add('''input «signal.name»''')
		}

		for (signal : port.additionalOutputs) {
			signals.add('''«outputDir» «signal.name»''')
		}
	}

	/**
	 * Gets the resolved port type, checking the cache for types resolved during DPN processing.
	 */
	def private Type getResolvedPortType(Entity entity, Port port) {
		// Try to get from cache first
		val cachedType = BuiltinEntityHelper.getCachedPortType(entity.name, port.name)
		if (cachedType !== null) {
			// Check if cached type is valid
			if (cachedType instanceof TypeInt) {
				if ((cachedType as TypeInt).size > 0) {
					return cachedType
				}
			} else {
				return cachedType
			}
		}
		// Fall back to port's declared type
		return port.type
	}

	override caseActor(Actor actor) {
		'''
		«printModuleDeclaration(actor)»

		  «new ActorPrinter(namer, pathResolver).printActor(actor)»

		endmodule //«actor.simpleName»
		'''
	}

	override caseDPN(DPN dpn) {
		'''
		«printModuleDeclaration(dpn)»

		  «new DpnPrinter(namer, pathResolver).printDPN(dpn)»

		endmodule //«dpn.simpleName»
		'''
	}

	override caseUnit(Unit unit) {
		'''
		«printHeader(unit)»

		«printImports(unit)»

		/**
		 * Constants
		 */
		«FOR constant : unit.variables»
		«irPrinter.printStateVar(unit, constant)»
		«ENDFOR»

		«IF !unit.procedures.empty»
		/**
		 * Functions
		 */
		«FOR proc : unit.procedures»
		«irPrinter.printFunction(proc)»
		«ENDFOR»
		«ENDIF»

		'''
	}
	
	def private printComment(JsonArray lines) {
		'''
		«FOR line : lines»
		*«IF !line.asString.empty» «line.asString»«ENDIF»
		«ENDFOR»
		'''
	}

	/**
	 * The file header: what was generated from what, then the source's OWN copyright and doc
	 * comment. Author and copyright come from the .cg source and are left out when it has none --
	 * never the OS login or the current year (see GeneratedHeader).
	 */
	def private printHeader(Entity entity) {
		val copyright = entity.properties.getAsJsonArray(PROP_COPYRIGHT)
		val javadoc = entity.properties.getAsJsonArray(PROP_JAVADOC)
		val author = GeneratedHeader.author(entity)
		'''
		/**
		 * Title      : Generated from «entity.name» by Neosyn IDE
		 * File       : «entity.name».v
		«IF author !== null»
		 * Author     : «author»
		«ENDIF»
		 * Standard   : Verilog-2001
		 */
		«IF copyright !== null»

		/*
		 «copyright.printComment»
		 */
		«ENDIF»
		«IF javadoc !== null»

		/**
		 «javadoc.printComment»
		 */
		«ENDIF»
		'''
	}

	def private printModuleDeclaration(Entity entity) {
		val signals = new ArrayList<CharSequence>

		// clocks
		val clocks = entity.properties.getAsJsonArray(PROP_CLOCKS)
		signals += clocks.map[clock|'input ' + clock.asString]

		// resets
		val resets = entity.properties.getAsJsonArray(PROP_RESETS)
		signals += resets.map[reset|'input ' + reset.asJsonObject.getAsJsonPrimitive('name').asString]

		// add ports
		(entity.inputs + entity.outputs).forEach[p|addPorts(signals, p, entity)]

		// One port per line (decision S187): a 15-port task no longer declares its
		// whole interface on one line of several hundred characters.
		'''
		«printHeader(entity)»
		«IF signals.empty»
		module «entity.simpleName»();
		«ELSE»
		module «entity.simpleName»(
		  «FOR i : 0 ..< signals.size»
		  «signals.get(i)»«IF i < signals.size - 1»,«ENDIF»
		  «ENDFOR»
		);
		«ENDIF»

		  «printImports(entity)»
		'''
	}

}
