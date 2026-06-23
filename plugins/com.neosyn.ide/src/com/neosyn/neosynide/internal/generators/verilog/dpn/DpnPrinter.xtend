/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.dpn

import com.neosyn.core.IPathResolver
import com.neosyn.models.dpn.DPN
import com.neosyn.models.dpn.Endpoint
import com.neosyn.models.dpn.Instance
import com.neosyn.models.dpn.Port
import com.neosyn.neosynide.internal.generators.BuiltinEntityHelper
import com.neosyn.neosynide.internal.generators.Namer
import com.neosyn.neosynide.internal.generators.verilog.VerilogIrPrinter
import java.util.ArrayList
import java.util.List
import com.neosyn.models.ir.Type
import com.neosyn.models.ir.TypeInt
import com.neosyn.models.util.BuiltinPortTypeResolver
import com.neosyn.neosynide.internal.generators.common.ArgumentPrinter
import com.neosyn.neosynide.internal.generators.common.ArgumentPrinter.BoolFormat

import static com.neosyn.core.IProperties.PROP_CLOCKS
import static com.neosyn.core.IProperties.PROP_RESETS
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

	new(Namer namer, IPathResolver pathResolver) {
		this.namer = namer
		this.irPrinter = new VerilogIrPrinter(namer, pathResolver)
		this.builtinHelper = new BuiltinEntityHelper(pathResolver)
	}

	def printDPN(DPN network) {
		builtinHelper.clear()

		'''
		/**
		 * Wires
		 */
		«FOR instance : network.instances»
		«printWires(network, instance)»
		«ENDFOR»

		/**
		 * Instances
		 */
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

					for (signal : port.interface.signals) {
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
		val moduleName = builtinHelper.getModuleName(instance)

		// Clock mappings
		val clocks = instance.properties.getAsJsonObject(PROP_CLOCKS)
		if (clocks !== null) {
			mappings += clocks.entrySet.map[pair|'''.«pair.key»(«pair.value.asString»)''']
		}

		// Reset mappings
		val resets = instance.properties.getAsJsonObject(PROP_RESETS)
		if (resets !== null) {
			mappings += resets.entrySet.map[pair|'''.«pair.key»(«pair.value.asString»)''']
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
	 * Builds the parameter list for a module instantiation.
	 * For built-in entities, adds computed parameters like 'depth'.
	 */
	def private List<CharSequence> buildParameters(Instance instance) {
		val params = new ArrayList<CharSequence>
		val entityType = BuiltinEntityHelper.getEntityType(instance)

		if (BuiltinEntityHelper.isBuiltinType(entityType)) {
			// For built-in entities, read parameters from instance properties
			// (IR Arguments have proxy variables with null names)
			val props = instance.properties
			if (props !== null) {
				// Load the entity from classpath to get variable names
				val entity = builtinHelper.getEntity(instance)
				if (entity !== null) {
					for (v : entity.variables) {
						if (v.name !== null && props.has(v.name)) {
							val value = props.get(v.name)
							if (value.jsonPrimitive) {
								val prim = value.asJsonPrimitive
								val verilogValue = if (prim.isBoolean) {
									if (prim.asBoolean) "1" else "0"
								} else {
									prim.asString
								}
								params.add('''.«v.name»(«verilogValue»)''')
							}
						}
					}
				}
				// For RAMs, compute depth from size if not present
				val simpleName = BuiltinEntityHelper.getSimpleName(entityType)
				if (#["SinglePortRAM", "DualPortRAM", "PseudoDualPortRAM"].contains(simpleName)) {
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
	 * Adds a single port mapping to the list.
	 * @param rawWireName the raw (unescaped) wire name for constructing signal suffixes, or null
	 */
	def private addPortMapping(List<CharSequence> mappings, Port port, String wire, String rawWireName) {
		if (port === null || port.name === null) {
			return
		}
		mappings.add('''.«namer.getName(port)»(«wire»)''')

		for (signal : port.interface.signals) {
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
	 * Prints assigns to output ports when necessary.
	 */
	def private printAssigns(DPN network, Port port) {
		val endpoint = network.getIncoming(port)
		val outgoing = network.getOutgoing(endpoint)

		if (isSignalNeeded(outgoing)) {
			val wire = getSignal(namer, endpoint)
			val rawWireName = if (endpoint !== null) getRawWireName(endpoint) else null

			'''
			assign «namer.getName(port)» = «wire»;
			«FOR signal : port.interface.signals»
			assign «namer.getSafeName(port.name + "_" + signal)» = «namer.getSafeName(rawWireName + "_" + signal)»;
			«ENDFOR»
			'''
		}
	}

	/**
	 * Finds outgoing connections from an instance by port name.
	 * Used as fallback when port object identity doesn't match (built-in entities).
	 */
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
