/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.DPN
import com.neosyn.models.dpn.Entity
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import java.util.Calendar
import java.util.List
import org.eclipse.core.runtime.Path

import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_RESETS

/**
 * This class defines the testbench printer for Verilog.
 * 


 */
class VerilogTestbenchPrinter {

	val VerilogPrinter dfPrinter

	new(Namer namer, IPathResolver pathResolver) {
		dfPrinter = new VerilogPrinter(namer, pathResolver)
	}

	def private printTestModules(DPN dpn, boolean synthetic) {
		if (synthetic) {
			dpn.name = dpn.name + "_test"

			'''
			«dfPrinter.doSwitch(dpn)»

			«dfPrinter.doSwitch(dpn.getInstance("stimulus").entity)»

			«dfPrinter.doSwitch(dpn.getInstance("expected").entity)»
			'''
		} else {
			dfPrinter.doSwitch(dpn)
		}
	}

	def printTestbench(Entity entity) {
		val name = entity.simpleName
		val project = new Path(entity.fileName).segment(0)

		val List<CharSequence> mappings = new ArrayList

		val clocks = entity.properties.getAsJsonArray(PROP_CLOCKS)
		mappings.addAll(clocks.map[clock|
			val clockName = clock.asString
			'''.«clockName»(«clockName»)'''
		])

		val resets = entity.properties.getAsJsonArray(PROP_RESETS)
		val resetNames = resets.map[reset|reset.asJsonObject.getAsJsonPrimitive('name').asString]
		mappings.addAll(resets.map[reset|
			val resetName = reset.asJsonObject.getAsJsonPrimitive('name').asString
			'''.«resetName»(«resetName»)'''
		])

		val List<CharSequence> resetAssignments = new ArrayList
		resetNames.forEach[reset, index|if (index > 0) {
			resetAssignments += '''«reset» <= «resetNames.get(index - 1)»;'''
		}]

		val synthetic = entity.properties.has('synthetic')

		'''
		/**
		 * Title      : Generated from «name» by Neosyn IDE
		 * Project    : «project»
		 *
		 * File       : «name».tb.v
		 * Author     : «System.getProperty("user.name")»
		 * Standard   : Verilog-2001
		 *
		 **
		 * Copyright (c) «Calendar.instance.get(Calendar.YEAR)»
		 **
		 *
		 */

		module «name»_tb;

		  // clock declarations
		  «FOR clock : clocks»
		  parameter PERIOD_«clock.asString» = 5;
		  «ENDFOR»
		  «FOR clock : clocks»
		  reg «clock.asString» = 0;
		  «ENDFOR»

		  // declarations of resets
		  parameter INIT_RESET = 10 * 10;
		  reg «resetNames.map[reset|'''«reset» = 0'''].join(', ')»;

		  // reset assignments
		  initial #INIT_RESET «resetNames.head» <= 1;
		  «IF !resetNames.tail.empty»
		  always @(posedge «clocks.head.asString») begin
		    «resetAssignments.join('\n')»
		  end
		  «ENDIF»

		  // generation of clock(s)
		  «FOR clock : clocks»
		  always #PERIOD_«clock.asString» «clock.asString» <= !«clock.asString»;
		  «ENDFOR»

		  // VCD waveform generation
		  initial begin
		    $dumpfile("«name».vcd");
		    $dumpvars(0, «name»_tb);
		  end

		  «name»«IF synthetic»_test«ENDIF» «name.toFirstLower»«IF synthetic»_test«ENDIF» (
		    «mappings.join(",\n")»
		  );

		endmodule

		«printTestModules(entity as DPN, synthetic)»
		'''
	}

}
