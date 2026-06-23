/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators;

import java.util.List;

import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.InstReturn;
import com.neosyn.models.ir.Procedure;

/**
 * This class defines common extensions used by code generators.
 * 

 *
 */
public class GeneratorExtensions {

	/**
	 * Returns the expression that this scheduler procedure reduces to. The scheduler must be
	 * inlinable.
	 * 
	 * @param scheduler
	 *            a scheduler
	 * @return an expression
	 */
	public static Expression getExpression(Procedure scheduler) {
		BlockBasic basic = (BlockBasic) scheduler.getBlocks().get(0);
		InstReturn instReturn = (InstReturn) basic.getInstructions().get(0);
		return instReturn.getValue();
	}

	/**
	 * Returns the number of hexadecimal digits required for the given number. Equivalent to
	 * <code>(int) Math.ceil((float) n / 4.0f)</code>.
	 * 
	 * @param n
	 *            a (positive) number
	 * @return the number of hexadecimal digits required for <code>n</code>
	 */
	public static int getNumberOfHexadecimalDigits(int n) {
		return (n % 4 == 0) ? n / 4 : n / 4 + 1;
	}

	public static String getSignal(Namer namer, Endpoint endpoint) {
		if (endpoint.hasInstance()) {
			String portName = getPortName(endpoint.getPort());
			return endpoint.getInstance().getName() + "_" + portName;
		} else {
			String portName = getPortName(endpoint.getPort());
			return namer.getSafeName(portName);
		}
	}

	/**
	 * Gets the port name, resolving proxy URIs for built-in entity ports.
	 */
	public static String getPortName(com.neosyn.models.dpn.Port port) {
		if (port == null) {
			return "null";
		}
		if (port.getName() != null) {
			return port.getName();
		}
		// Try to resolve from proxy URI (e.g., "classpath:/builtin-ir/std/mem/SinglePortRAM.ir#//@outputs.0")
		if (port.eIsProxy()) {
			org.eclipse.emf.common.util.URI proxyURI = ((org.eclipse.emf.ecore.InternalEObject) port).eProxyURI();
			String fragment = proxyURI != null ? proxyURI.fragment() : null;
			if (fragment != null) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("@(inputs|outputs)\\.(\\d+)")
					.matcher(fragment);
				if (m.find()) {
					String portKind = m.group(1);
					int portIndex = Integer.parseInt(m.group(2));
					String entityType = resolveEntityTypeFromUri(proxyURI.toString());
					if (entityType != null) {
						com.neosyn.models.dpn.Entity entity =
							com.neosyn.models.util.BuiltinPortTypeResolver.loadBuiltinEntity(entityType);
						if (entity != null) {
							java.util.List<? extends com.neosyn.models.dpn.Port> ports =
								"inputs".equals(portKind) ? entity.getInputs() : entity.getOutputs();
							if (portIndex < ports.size()) {
								return ports.get(portIndex).getName();
							}
						}
					}
				}
			}
		}
		return "null";
	}

	private static String resolveEntityTypeFromUri(String uri) {
		if (uri == null) return null;
		String prefix = "builtin-ir/";
		int idx = uri.indexOf(prefix);
		if (idx >= 0) {
			String suffix = uri.substring(idx + prefix.length());
			int irIdx = suffix.indexOf(".ir");
			if (irIdx >= 0) {
				return suffix.substring(0, irIdx).replace('/', '.');
			}
		}
		return null;
	}

	/**
	 * Returns <code>true</code> if the scheduler procedure can be inlined.
	 * 
	 * @param scheduler
	 *            a scheduler procedure
	 */
	public static boolean isInlinable(Procedure scheduler) {
		if (scheduler.getLocals().isEmpty() && scheduler.getBlocks().size() == 1) {
			final Block block = scheduler.getBlocks().get(0);
			BlockBasic basic = (BlockBasic) block;
			return basic.getInstructions().size() == 1;
		}

		return false;
	}

	/**
	 * Returns <code>true</code> if a signal/wire is needed, either:
	 * <ul>
	 * <li>there is more than one connection (broadcast), or</li>
	 * <li>there is one outgoing connection to an instance.</li>
	 * </ul>
	 * In any other cases this method returns false:
	 * <ul>
	 * <li>when the list of endpoints is empty,</li>
	 * <li>when there is one endpoint that is an output port.</li>
	 * </ul>
	 */
	public static boolean isSignalNeeded(List<Endpoint> endpoints) {
		if (endpoints.isEmpty()) {
			return false;
		} else if (endpoints.size() > 1) {
			return true;
		} else {
			return endpoints.get(0).hasInstance();
		}
	}

}
