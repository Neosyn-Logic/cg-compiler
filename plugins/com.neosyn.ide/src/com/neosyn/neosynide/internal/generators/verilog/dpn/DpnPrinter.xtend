/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.dpn

import com.google.gson.JsonObject
import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.DPN
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.models.dpn.Endpoint
import com.neosyn.models.dpn.Instance
import com.neosyn.models.dpn.Port
import com.neosyn.neosynide.internal.generators.BuiltinEntityHelper
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.internal.generators.verilog.VerilogIrPrinter
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.List
import com.neosyn.models.ir.Type
import com.neosyn.models.ir.TypeInt
import com.neosyn.models.util.BuiltinPortTypeResolver
import com.neosyn.neosynide.internal.generators.common.ArgumentPrinter
import com.neosyn.neosynide.internal.generators.common.ArgumentPrinter.BoolFormat

import static com.neosyn.core.IProperties.ACTIVE_LOW
import static com.neosyn.core.IProperties.PROP_ACTIVE
import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_NAME
import static com.neosyn.core.IProperties.PROP_RESETS
import static com.neosyn.core.IProperties.PROP_TYPE
import static com.neosyn.core.IProperties.RESET_ASYNCHRONOUS
import static com.neosyn.neosynide.internal.generators.GeneratorExtensions.getSignal
import static com.neosyn.neosynide.internal.generators.GeneratorExtensions.isSignalNeeded

/**
 * Prints Verilog code for DPN (Dataflow Process Network) entities.
 * Handles module instantiations, wire declarations, and port mappings.
 */
class DpnPrinter {

	val Namer namer
	val VerilogIrPrinter irPrinter
	val BuiltinEntityHelper builtinHelper
	val IPathResolver pathResolver

	/** The (reset, clock) pairs of this network's reset release synchronizers -- see resetSynchronizers. */
	var List<Pair<JsonObject, String>> synchronizers = new ArrayList

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		this.pathResolver = pathResolver
		this.irPrinter = new VerilogIrPrinter(namer, pathResolver)
		this.builtinHelper = new BuiltinEntityHelper(pathResolver)
	}

	def printDPN(DPN network) {
		builtinHelper.clear()
		synchronizers = resetSynchronizers(network)
		if (!synchronizers.empty) {
			pathResolver.copyBuiltinEntityFile("std.lib.SynchronizerFF")
		}

		'''
		/**
		 * Wires
		 */
		«IF !synchronizers.empty»
		// Reset release synchronizers (target vendor «pathResolver.targetVendor»): each reset still
		// asserts asynchronously, and is released synchronously with one clock of this network.
		«FOR s : synchronizers»
		«printSynchronizerWires(s.key, s.value)»
		«ENDFOR»
		«ENDIF»
		«FOR instance : network.instances»
		«printWires(network, instance)»
		«ENDFOR»

		/**
		 * Instances
		 */
		«FOR s : synchronizers SEPARATOR "\n"»
		«printSynchronizer(s.key, s.value)»
		«ENDFOR»
		«FOR instance : network.instances SEPARATOR "\n"»
		«printInstance(network, instance)»
		«ENDFOR»

		/**
		 * Assignments to output ports
		 */
		«FOR port : network.outputs»
		«printAssigns(network, port)»
		«ENDFOR»
		'''
	}

	/**
	 * Declares wires for each output port of the given instance when needed.
	 */
	def private printWires(DPN network, Instance instance) {
		val wires = new ArrayList<CharSequence>
		val entity = builtinHelper.getEntity(instance)

		for (port : entity.outputs) {
			if (port.name !== null) {
				val endpoint = new Endpoint(instance, port)
				// Try standard lookup first
				var outgoing = network.getOutgoing(endpoint)

				// For built-in entities, port object identity may not match.
				// Fall back to finding connections by port name.
				if (outgoing.empty && BuiltinEntityHelper.isBuiltinType(BuiltinEntityHelper.getEntityType(instance))) {
					outgoing = findOutgoingByPortName(instance, port.name)
				}

				if (isSignalNeeded(outgoing)) {
					val wire = getSignal(namer, endpoint)
					val rawWireName = getRawWireName(endpoint)
					// Resolve port type - check if it needs resolution
					val portType = resolveWireType(network, instance, port, outgoing)
					wires.add('''wire «irPrinter.doSwitch(portType)» «wire»;''')

					// Cache the resolved port type for the entity
					val entityName = builtinHelper.getEntity(instance).name
					if (entityName !== null) {
						BuiltinEntityHelper.cacheResolvedPortType(entityName, port.name, portType)
					}

					// TRAPS T107 (S183): declare the union of THIS port's companions and every
					// CONSUMER's. The port map spells companions from the TARGET port's
					// interface, not the source's, so a `push` output feeding a FIFO -- whose
					// `din` is ready/valid -- produced `din_ready => <base>_ready` for a signal
					// nothing had declared. VHDL refused the file outright; Verilog's implicit
					// nets absorbed it, which is worse, because the two back ends then disagree
					// about whether the design builds. Buffering a source that cannot be
					// back-pressured is a reasonable thing to want, so support it rather than
					// rejecting it.
					for (signal : companionFlags(port, outgoing)) {
						wires.add('''wire «namer.getSafeName(rawWireName + "_" + signal)»;''')
					}
				}
			}
		}

		'''
		«IF !wires.empty»
		// Module : «namer.getName(instance)»
		«wires.join("\n")»
		«ENDIF»
		'''
	}

	/**
	 * Prints a module instantiation with port mappings.
	 */
	def private printInstance(DPN network, Instance instance) {
		val mappings = new ArrayList<CharSequence>
		// through the namer: a module whose name is a keyword is declared escaped, and an
		// instantiation that printed it raw would not parse
		val moduleName = namer.getEntityName(builtinHelper.getModuleName(instance))

		// Clock mappings
		val clocks = instance.properties.getAsJsonObject(PROP_CLOCKS)
		if (clocks !== null) {
			mappings += clocks.entrySet.map[pair|'''.«pair.key»(«pair.value.asString»)''']
		}

		// Reset mappings
		val resets = instance.properties.getAsJsonObject(PROP_RESETS)
		if (resets !== null) {
			mappings += resets.entrySet.map[pair|'''.«pair.key»(«resetActual(instance, pair.value.asString)»)''']
		}

		// Port mappings
		addPortMappings(mappings, network, instance)

		// Build parameter list - include derived parameters for built-in entities
		val params = buildParameters(instance)

		'''
		«moduleName» «IF !params.empty»#(
		  «params.join(",\n  ")»
		)
		«ENDIF»
		«namer.getName(instance)» (
		  «mappings.join(",\n")»
		);
		'''
	}

	/**
	 * The (reset, clock) pairs a reset release synchronizer is printed for: every ASYNCHRONOUS reset of this network,
	 * once per clock of the network, when the target vendor asks for it in this network -- a design root, and a vendor
	 * other than generic (decision S181; see IPathResolver.synchronizesResets). Empty otherwise, which keeps
	 * the generic output byte-identical.
	 */
	def private List<Pair<JsonObject, String>> resetSynchronizers(DPN network) {
		val result = new ArrayList<Pair<JsonObject, String>>
		if (pathResolver === null || !pathResolver.synchronizesResets(network)) {
			return result
		}
		val resets = network.properties.getAsJsonArray(PROP_RESETS)
		val clocks = network.properties.getAsJsonArray(PROP_CLOCKS)
		if (resets === null || clocks === null) {
			return result
		}
		for (reset : resets) {
			val obj = reset.asJsonObject
			if (RESET_ASYNCHRONOUS.equals(obj.get(PROP_TYPE))) {
				for (clock : clocks) {
					result += obj -> clock.asString
				}
			}
		}
		result
	}

	/** The raw name of the copy of `reset` released on `clock`. */
	def private static String syncedReset(JsonObject reset, String clock) {
		reset.get(PROP_NAME).asString + "_sync_" + clock
	}

	def private printSynchronizerWires(JsonObject reset, String clock) {
		val activeLow = ACTIVE_LOW.equals(reset.get(PROP_ACTIVE))
		val name = syncedReset(reset, clock)
		'''
		wire «namer.getSafeName(name)»;
		«IF !activeLow»
		wire «namer.getSafeName(name + "_n")»;
		«ENDIF»
		'''
	}

	/**
	 * A std.lib.SynchronizerFF clocked by `clock` with `din` tied to 1: its output follows the reset's ASSERTION at once
	 * (the flip-flops reset asynchronously) and its RELEASE two `clock` edges later. It is active low, so an active-high
	 * reset is inverted on the way in and on the way out.
	 */
	def private printSynchronizer(JsonObject reset, String clock) {
		val activeLow = ACTIVE_LOW.equals(reset.get(PROP_ACTIVE))
		val resetName = reset.get(PROP_NAME).asString
		val name = syncedReset(reset, clock)
		'''
		SynchronizerFF #(
		  .stages(2)
		)
		«namer.getSafeName(resetName + "_synchronizer_" + clock)» (
		  .reset_n(«IF activeLow»«resetName»«ELSE»~«resetName»«ENDIF»),
		  .din_clock(«clock»),
		  .dout_clock(«clock»),
		  .din(1'b1),
		  .dout(«namer.getSafeName(if (activeLow) name else name + "_n")»)
		);
		«IF !activeLow»
		assign «namer.getSafeName(name)» = ~«namer.getSafeName(name + "_n")»;
		«ENDIF»
		'''
	}

	/**
	 * The actual that a reset input of `instance` is mapped to: the network's reset or, when this network synchronizes
	 * it, the copy released on the instance's clock. An instance with several clocks (a synchronizer, an asynchronous
	 * FIFO) keeps the raw reset: no single domain is right for all of its flip-flops.
	 */
	def private String resetActual(Instance instance, String networkReset) {
		val clocks = instance.properties.getAsJsonObject(PROP_CLOCKS)
		if (synchronizers.empty || clocks === null) {
			return networkReset
		}
		val domains = clocks.entrySet.map[value.asString].toSet
		if (domains.size != 1) {
			return networkReset
		}
		val domain = domains.head
		val match = synchronizers.findFirst[key.get(PROP_NAME).asString == networkReset && value == domain]
		if (match === null) networkReset else namer.getSafeName(syncedReset(match.key, match.value))
	}

	/**
	 * Builds the parameter list for a module instantiation.
	 * For built-in entities, adds computed parameters like 'depth'.
	 */
	def private List<CharSequence> buildParameters(Instance instance) {
		val params = new ArrayList<CharSequence>
		val entityType = BuiltinEntityHelper.getEntityType(instance)

		if (BuiltinEntityHelper.isBuiltinType(entityType)) {
			// For built-in entities, read parameters directly from the instance
			// properties (IR Arguments have proxy variables with null names). Every
			// scalar numeric/boolean property is a module parameter (size, width,
			// stages, writeShiftMode, …); the non-parameter properties (clocks and
			// resets are objects, implementation is an object, entityType is a
			// string) are naturally excluded by the primitive-number/boolean test.
			// NB most built-in IRs do NOT declare <variables>, so we must NOT gate
			// on entity.variables here or the #(...) list comes out empty and the
			// instance silently uses the module defaults (width 0 => broken).
			val props = instance.properties
			if (props !== null) {
				for (key : props.keySet) {
					val value = props.get(key)
					if (value.jsonPrimitive) {
						val prim = value.asJsonPrimitive
						if (prim.isNumber || prim.isBoolean) {
							val verilogValue = if (prim.isBoolean) {
								if (prim.asBoolean) "1" else "0"
							} else {
								prim.asString
							}
							params.add('''.«key»(«verilogValue»)''')
						}
					}
				}
				// RAMs and FIFOs take a `depth` (address width) that is derived from
				// `size` rather than passed explicitly; compute it when absent.
				val simpleName = BuiltinEntityHelper.getSimpleName(entityType)
				if (#["SinglePortRAM", "DualPortRAM", "PseudoDualPortRAM",
						"SynchronousFIFO", "AsynchronousFIFO"].contains(simpleName)) {
					if (props.has("size") && !props.has("depth")) {
						val size = props.get("size").asInt
						val depth = BuiltinPortTypeResolver.ceilLog2(size)
						params.add('''.depth(«depth»)''')
					}
				}
			}
		} else {
			// Regular entities: use IR Arguments directly
			for (arg : instance.arguments) {
				if (arg.variable !== null && arg.variable.name !== null) {
					params.add('''.«namer.getName(arg.variable)»(«new ArgumentPrinter(BoolFormat.VERILOG).doSwitch(arg.value)»)''')
				}
			}
		}

		return params
	}

	/**
	 * Adds port mappings for an instance to the mappings list.
	 */
	def private addPortMappings(List<CharSequence> mappings, DPN network, Instance instance) {
		val bound = <String>newHashSet

		// Input port mappings
		for (connection : network.getIncoming(instance)) {
			val sourceEndpoint = connection.sourceEndpoint
			val wire = getSignal(namer, sourceEndpoint)
			val rawWireName = if (sourceEndpoint !== null) getRawWireName(sourceEndpoint) else null
			// For built-in entities (e.g. std.fifo.SynchronousFIFO) the targetPort
			// is an unresolved proxy with a null name, so addPortMapping would drop
			// it — leaving the instance's din/din_valid/din_ready unwired (the FIFO
			// gets no input and its ready floats). Resolve the real input port (name
			// + interface) from the loaded built-in entity, mirroring how the output
			// side resolves builtin ports by URI.
			var targetPort = connection.targetPort
			if ((targetPort === null || targetPort.name === null)
					&& BuiltinEntityHelper.isBuiltinType(BuiltinEntityHelper.getEntityType(instance))) {
				val resolved = resolveBuiltinPortObject(targetPort)
				if (resolved !== null) {
					targetPort = resolved
				}
			}
			addPortMapping(mappings, targetPort, wire, rawWireName)
			// Record what was ACTUALLY mapped, after the builtin proxy was resolved. Collecting
			// `connection.targetPort.name` instead misses a builtin's ports -- the proxy's name
			// is null until it is resolved -- so the FIFO's `din` looked unconnected, got a
			// second mapping, and iverilog said `port 'din' already bound`.
			if (targetPort !== null && targetPort.name !== null) {
				bound.add(targetPort.name)
			}
		}

		// An input nothing drives, DRIVEN TO ZERO -- the mapping is not simply left out.
		// Verilog does not require every input to be connected: it floats the pin, reads it as
		// X, and says so only as a lint warning ("Cell has missing pin"), so this was invisible
		// until the generated Verilog was linted -- 115 pins over 50 files, in Intel-8088,
		// RISC-V, RiscvSoC, SoC and UART. The VHDL backend has always tied these to '0' (VHDL
		// refuses to elaborate otherwise), so the same design was deterministic in one language
		// and X-propagating in the other. Zero is also what the hardware does: an undriven FPGA
		// input powers up low. The front end already warns, with a file:line this printer has
		// not got, so nothing is said here.
		for (port : builtinHelper.getEntity(instance).inputs) {
			if (port !== null && port.name !== null && !bound.contains(port.name)) {
				addUnconnectedInput(mappings, port)
			}
		}

		// Output port mappings
		val entity = builtinHelper.getEntity(instance)
		for (port : entity.outputs) {
			if (port.name !== null) {
				val endpoint = new Endpoint(instance, port)
				// Try standard lookup first
				var outgoing = network.getOutgoing(endpoint)

				// For built-in entities, port object identity may not match.
				// Fall back to finding connections by port name.
				if (outgoing.empty && BuiltinEntityHelper.isBuiltinType(BuiltinEntityHelper.getEntityType(instance))) {
					outgoing = findOutgoingByPortName(instance, port.name)
				}

				val wire = if (isSignalNeeded(outgoing)) {
					getSignal(namer, endpoint)
				} else if (outgoing.head !== null) {
					namer.getName(outgoing.head.port)
				} else {
					null  // unconnected
				}

				val rawWireName = if (isSignalNeeded(outgoing)) {
					getRawWireName(endpoint)
				} else if (outgoing.head !== null) {
					outgoing.head.port.name
				} else {
					null
				}

				addPortMapping(mappings, port, wire, rawWireName)
			}
		}
	}

	/**
	 * Drives an input port nothing is connected to, and its IN companions, to zero. An OUT
	 * companion (a `_ready` this instance drives back) is left unconnected, which is what an
	 * empty association means and is legal for an output.
	 */
	def private addUnconnectedInput(List<CharSequence> mappings, Port port) {
		val width = TypeUtil.getSize(port.type)
		mappings.add('''.«namer.getName(port)»(«width»'b0)''')

		for (signal : port.signals) {
			val name = port.name + "_" + signal
			val driven = port.additionalInputs.exists[v | name == v.name]
			mappings.add('''.«namer.getSafeName(name)»(«IF driven»1'b0«ENDIF»)''')
		}
	}

	/**
	 * Adds a single port mapping to the list.
	 * @param rawWireName the raw (unescaped) wire name for constructing signal suffixes, or null
	 */
	def private addPortMapping(List<CharSequence> mappings, Port port, String wire, String rawWireName) {
		if (port === null || port.name === null) {
			return
		}
		mappings.add('''.«namer.getName(port)»(«wire»)''')

		for (signal : port.signals) {
			val portSignalName = namer.getSafeName(port.name + "_" + signal)
			val wireSignalName = if (rawWireName !== null) namer.getSafeName(rawWireName + "_" + signal) else null
			mappings.add('''.«portSignalName»(«IF wireSignalName !== null»«wireSignalName»«ENDIF»)''')
		}
	}

	/**
	 * Gets the raw (unescaped) wire name for an endpoint.
	 */
	def private getRawWireName(Endpoint endpoint) {
		// Resolve the port name: for a built-in entity output (e.g. a FIFO dout)
		// the endpoint port is an unresolved proxy with a null name, which would
		// make the valid/ready signal wires "<inst>_null_valid" instead of
		// "<inst>_<port>_valid" — mismatching the driver and leaving the
		// handshake dangling. Fall back to the URI-resolved name.
		val portName = resolvePortName(endpoint.port)
		if (endpoint.hasInstance()) {
			endpoint.getInstance().name + "_" + portName
		} else {
			portName
		}
	}

	/**
	 * Returns the port's name, resolving a built-in proxy port's name from its
	 * classpath URI when the direct name is null.
	 */
	def private String resolvePortName(Port port) {
		if (port === null) {
			return null
		}
		if (port.name !== null) {
			return port.name
		}
		return extractPortNameFromUri(port)
	}

	/**
	 * The readers of an output port's driver. For a built-in instance the by-name lookup is used
	 * whenever it finds more than the identity lookup -- printWires and addPortMappings find them
	 * by name, and the connection's source port is a proxy (TRAPS T94).
	 */
	def private List<Endpoint> readersOfDriver(DPN network, Endpoint endpoint) {
		val outgoing = network.getOutgoing(endpoint)
		if (endpoint !== null && endpoint.hasInstance
				&& BuiltinEntityHelper.isBuiltinType(BuiltinEntityHelper.getEntityType(endpoint.instance))) {
			val byName = BuiltinEntityHelper.findOutgoingByPortName(endpoint.instance, resolvePortName(endpoint.port))
			if (byName.size > outgoing.size) {
				return byName
			}
		}
		return outgoing
	}

	/**
	 * Prints assigns to output ports when necessary.
	 *
	 * <p>A built-in driver's readers are counted BY NAME, as printWires and addPortMappings count
	 * them. The identity lookup can miss the sibling reader, see only this output port and decide
	 * no wire is needed -- while printWires has already routed the instance output into a wire.
	 * The port was left undriven: `speed` in NetworkStack and NetTop (TRAPS T94).
	 */
	def private printAssigns(DPN network, Port port) {
		val endpoint = network.getIncoming(port)
		val outgoing = readersOfDriver(network, endpoint)

		if (isSignalNeeded(outgoing)) {
			val wire = getSignal(namer, endpoint)
			val rawWireName = if (endpoint !== null) getRawWireName(endpoint) else null

			'''
			assign «namer.getName(port)» = «wire»;
			«FOR signal : port.signals»
			assign «namer.getSafeName(port.name + "_" + signal)» = «namer.getSafeName(rawWireName + "_" + signal)»;
			«ENDFOR»
			'''
		}
	}

	/**
	 * Finds outgoing connections from an instance by port name.
	 * Used as fallback when port object identity doesn't match (built-in entities).
	 */
	/**
	 * The companion wires (`_valid`, `_ready`, ...) that must be DECLARED for an output port.
	 *
	 * Not simply the port's own interface: a consumer's port map spells companions from the
	 * CONSUMER's interface, so a connection whose target expects more signals than the source
	 * provides would reference an undeclared name (TRAPS T107). Verilog would quietly make that
	 * an implicit net; VHDL rejects the file. Returns the union, source first, so ordering stays
	 * stable for anyone diffing generated HDL.
	 */
	def private companionFlags(Port port, Iterable<Endpoint> outgoing) {
		val flags = new LinkedHashSet<String>
		flags.addAll(port.signals)
		for (e : outgoing) {
			var tp = e.port
			if (tp === null || tp.name === null) {
				val resolved = BuiltinEntityHelper.resolveBuiltinPort(tp)
				if (resolved !== null) {
					tp = resolved
				}
			}
			if (tp !== null && tp.interface !== null) {
				flags.addAll(tp.signals)
			}
		}
		flags
	}

	def private findOutgoingByPortName(Instance instance, String portName) {
		val endpoints = new ArrayList<Endpoint>
		for (edge : instance.outgoing) {
			val connection = edge as com.neosyn.models.dpn.Connection
			val sourcePort = connection.sourcePort
			// Match by port name (handle both resolved and unresolved URIs)
			val sourcePortName = if (sourcePort !== null) {
				if (sourcePort.name !== null) {
					sourcePort.name
				} else {
					// Extract port name from URI like "classpath:/builtin-ir/.../SinglePortRAM.ir#//@outputs.0"
					extractPortNameFromUri(sourcePort)
				}
			}
			if (portName == sourcePortName) {
				endpoints.add(connection.targetEndpoint)
			}
		}
		return endpoints
	}

	/**
	 * Resolves the real {@link Port} object (with name + interface) for a port
	 * that may be an EMF proxy from a built-in entity classpath URI
	 * (e.g. ".../SynchronousFIFO.ir#//@inputs.0"). Returns null if it cannot be
	 * resolved. Used to wire built-in instance input ports, whose proxy
	 * targetPort has a null name.
	 */
	def private Port resolveBuiltinPortObject(Port port) {
		if (port !== null && port.eIsProxy) {
			val proxyUri = (port as org.eclipse.emf.ecore.InternalEObject).eProxyURI
			val fragment = proxyUri?.fragment
			if (fragment !== null) {
				val matcher = java.util.regex.Pattern.compile("@(inputs|outputs)\\.(\\d+)").matcher(fragment)
				if (matcher.find) {
					val portKind = matcher.group(1)
					val portIndex = Integer.parseInt(matcher.group(2))
					val entityType = resolveEntityTypeFromUri(proxyUri.toString)
					if (entityType !== null) {
						val entity = BuiltinPortTypeResolver.loadBuiltinEntity(entityType)
						if (entity !== null) {
							val ports = if (portKind == "inputs") entity.inputs else entity.outputs
							if (portIndex < ports.size) {
								return ports.get(portIndex)
							}
						}
					}
				}
			}
		}
		return null
	}

	/**
	 * Extracts port name from a port that may be an EMF proxy.
	 * For ports from classpath URIs, resolves and gets the name.
	 */
	def private String extractPortNameFromUri(Port port) {
		if (port.eIsProxy) {
			val proxyUri = (port as org.eclipse.emf.ecore.InternalEObject).eProxyURI
			val fragment = proxyUri?.fragment
			if (fragment !== null) {
				// Fragment format: //@outputs.0 or //@inputs.1
				// Extract the index and look up the real port name from the entity type
				val matcher = java.util.regex.Pattern.compile("@(inputs|outputs)\\.(\\d+)").matcher(fragment)
				if (matcher.find) {
					val portKind = matcher.group(1)
					val portIndex = Integer.parseInt(matcher.group(2))
					// Try to find the entity type from the URI path
					val uriStr = proxyUri.toString
					for (prefix : #["std.mem.", "std.fifo.", "std.lib."]) {
						// URI contains the entity type path
						val entityType = resolveEntityTypeFromUri(uriStr)
						if (entityType !== null) {
							val entity = BuiltinPortTypeResolver.loadBuiltinEntity(entityType)
							if (entity !== null) {
								val ports = if (portKind == "inputs") entity.inputs else entity.outputs
								if (portIndex < ports.size) {
									return ports.get(portIndex).name
								}
							}
						}
					}
				}
			}
		}
		return null
	}

	/**
	 * Resolves entity type name from a classpath proxy URI.
	 * E.g., "classpath:/builtin-ir/std/mem/SinglePortRAM.ir#//@outputs.0" -> "std.mem.SinglePortRAM"
	 */
	def private String resolveEntityTypeFromUri(String uri) {
		if (uri === null) return null
		val prefix = "builtin-ir/"
		val idx = uri.indexOf(prefix)
		if (idx >= 0) {
			val suffix = uri.substring(idx + prefix.length)
			val irIdx = suffix.indexOf(".ir")
			if (irIdx >= 0) {
				return suffix.substring(0, irIdx).replace('/', '.')
			}
		}
		return null
	}

	/**
	 * Resolves the wire type for a port, looking at connection targets if needed.
	 * If the source port has an invalid type, tries to get the type from the connected port.
	 */
	def private Type resolveWireType(DPN network, Instance instance, Port port, List<Endpoint> outgoing) {
		// First try to get resolved type from the port's instance
		val portType = builtinHelper.getResolvedPortType(instance, port)

		// Check if the type is valid
		if (portType instanceof TypeInt) {
			if (portType.size > 0) {
				return portType
			}
		} else if (portType !== null) {
			return portType
		}

		// Type is invalid, try to resolve from connection target
		if (!outgoing.empty) {
			val targetEndpoint = outgoing.head
			val targetInstance = targetEndpoint.instance
			val targetPort = targetEndpoint.port

			if (targetInstance !== null && targetPort !== null) {
				// Check if target is a built-in entity
				val targetEntityType = BuiltinEntityHelper.getEntityType(targetInstance)
				if (BuiltinEntityHelper.isBuiltinType(targetEntityType)) {
					val resolvedTargetType = builtinHelper.getResolvedPortType(targetInstance, targetPort)
					if (resolvedTargetType instanceof TypeInt && (resolvedTargetType as TypeInt).size > 0) {
						return resolvedTargetType
					}
				}

				// Try the target port's type directly
				val targetType = targetPort.type
				if (targetType instanceof TypeInt && (targetType as TypeInt).size > 0) {
					return targetType
				}
			}
		}

		// Fallback to original port type (may be invalid but guards will handle it)
		return port.type
	}
}
