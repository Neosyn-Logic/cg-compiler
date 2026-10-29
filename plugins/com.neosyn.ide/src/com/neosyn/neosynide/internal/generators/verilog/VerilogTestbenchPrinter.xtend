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
import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.DPN
import com.neosyn.models.dpn.Entity
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import java.util.List

import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_COPYRIGHT
import static com.neosyn.core.IProperties.PROP_RESETS

/**
 * This class defines the testbench printer for Verilog.
 * 


 */
class VerilogTestbenchPrinter {

	val Namer namer

	val VerilogPrinter dfPrinter

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		dfPrinter = new VerilogPrinter(namer, pathResolver)
	}

	/**
	 * The `terminate:` condition from the entity's `test` property, as an
	 * `<instance>.<variable>` path, or null when it declares none.
	 *
	 * Only the bytecode simulator ever read this. The HDL backends ignored it, so a
	 * generated testbench had no way to finish and the gates had to accept a timeout as
	 * success. Emitting `$finish` lets the harness require COMPLETION instead.
	 */
	/**
	 * The terminate path with every segment escaped the way the design escapes it.
	 *
	 * A Cg instance called `checker` is a SystemVerilog KEYWORD, so the network emits it as
	 * `\checker ` -- and a testbench reference that does not escape it identically is a
	 * syntax error, not a missing signal. Same family as the `buf` bug.
	 */
	def private String printTerminatePath(Entity entity) {
		val condition = getTerminateCondition(entity)
		if (condition === null) {
			return null
		}
		condition.split("\\.").map[segment | namer.getSafeName(segment.trim)].join(".")
	}

	def static String getTerminateCondition(Entity entity) {
		val props = entity.properties
		if (props === null || !props.has("test")) {
			return null
		}
		val test = props.get("test")
		if (!test.jsonObject) {
			return null
		}
		val obj = test.asJsonObject
		if (!obj.has("terminate")) {
			return null
		}
		val condition = obj.get("terminate").asString
		return if (condition.nullOrEmpty) null else condition
	}

	def private printTestModules(DPN dpn, boolean synthetic) {
		if (synthetic) {
			// TRAPS T110. The harness used to be named `<entity>_test`, which is the name
			// the PRODUCT TELLS USERS to give a testbench network -- so a project with
			// `task ComputeEA` + `network ComputeEA_test`, exactly the documented shape,
			// produced TWO entities called ComputeEA_test. GHDL refuses it outright;
			// Verilog silently had three modules of that name with DIFFERENT port lists.
			// `_tb` is a namespace the generator already owns (`<entity>_tb` is the top),
			// so the harness lives there instead of in the user's.
			dpn.name = dpn.name + "_tb_dut"

			// Print the module for every SYNTHETIC instance (the per-port stimulus
			// drivers `stimulus_<port>` and the `expected` checker). There is one
			// stimulus instance per input port so each can pace its own vector.
			'''
			«dfPrinter.doSwitch(dpn)»
			«FOR inst : dpn.instances»«IF inst.entity.properties !== null && inst.entity.properties.has('synthetic')»

			«dfPrinter.doSwitch(inst.entity)»
			«ENDIF»«ENDFOR»
			'''
		} else {
			dfPrinter.doSwitch(dpn)
		}
	}

	def printTestbench(Entity entity) {
		val name = entity.simpleName
		val author = GeneratedHeader.author(entity)
		val copyright = entity.properties.getAsJsonArray(PROP_COPYRIGHT)

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

		// The module under test and its label. `name` itself stays raw: the testbench's own module
		// is `«name»_tb`, and a suffixed keyword is no keyword. The DUT's module name is a library
		// unit (escaped if it must be, never renamed) while its label is an ordinary identifier
		// (renamed if it must be) -- and the label is referenced again below, so both are computed
		// once here.
		val dutName = if (synthetic) name + "_tb_dut" else name
		val dutModule = namer.getEntityName(dutName)
		val dutLabel = namer.getSafeName(dutName.toFirstLower)

		'''
		/**
		 * Title      : Testbench generated from «name» by Neosyn IDE
		 * File       : «name».tb.v
		«IF author !== null»
		 * Author     : «author»
		«ENDIF»
		 * Standard   : Verilog-2001
		 */
		«IF copyright !== null»

		/*
		«FOR line : copyright»
		 *«IF !line.asString.empty» «line.asString»«ENDIF»
		«ENDFOR»
		 */
		«ENDIF»

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

		  «dutModule» «dutLabel» (
		    «mappings.join(",\n")»
		  );
		«val terminate = printTerminatePath(entity)»
		«IF terminate !== null»

		  // Simulation end, from the design's `terminate:` test property. Without this the
		  // testbench ran the clock forever and the harness could only time out -- which
		  // cannot tell "the checker finished" from "the DUT produced nothing", so a
		  // fixture with no vectors passed even with a dead DUT.
		  //
		  // On the FALLING edge (TRAPS T42): `finished` is set with a non-blocking assignment at a rising
		  // edge, and a rising-edge hook saw it only at the NEXT rising edge -- where the checker's own
		  // block ran once more first, so Verilog executed one loop() more than `simulate` and a KAT that
		  // counts its checks failed AFTER its success marker. Half a cycle later nothing else has run.
		  always @(negedge «clocks.head.asString») begin
		    if («dutLabel».«terminate») begin
		      $display("[terminate] «terminate» asserted at %0t", $time);
		      $finish;
		    end
		  end
		«ENDIF»

		endmodule

		«printTestModules(entity as DPN, synthetic)»
		'''
	}

}
