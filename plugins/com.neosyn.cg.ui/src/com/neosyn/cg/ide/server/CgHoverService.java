/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.xtext.ide.server.hover.HoverContext;
import org.eclipse.xtext.ide.server.hover.HoverService;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgType;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.MultiPortDecl;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.Variable;

/**
 * Produces rich markdown hover content for C⏚ source elements:
 * <ul>
 *   <li>Ports — direction, protocol (push/stream/confirm/bare), type.</li>
 *   <li>Variables — type, initial value if any.</li>
 *   <li>Tasks / Networks / Bundles — kind + port or instance count.</li>
 *   <li>Inst — instance name + instantiated entity.</li>
 * </ul>
 *
 * The dispatch is text-only; offset / range plumbing is delegated to the base
 * {@link HoverService}. Tests call {@link #getContents(EObject)} directly to
 * exercise the markdown output without spinning up the LSP transport.
 */
public class CgHoverService extends HoverService {

	@Override
	public String getKind(HoverContext context) {
		return MarkupKind.MARKDOWN;
	}

	@Override
	public String getContents(EObject element) {
		if (element == null) {
			return "";
		}
		if (element instanceof Variable) {
			return contentsForVariable((Variable) element);
		}
		if (element instanceof Task) {
			return contentsForTask((Task) element);
		}
		if (element instanceof Network) {
			return contentsForNetwork((Network) element);
		}
		if (element instanceof Bundle) {
			return contentsForBundle((Bundle) element);
		}
		if (element instanceof Inst) {
			return contentsForInst((Inst) element);
		}
		return super.getContents(element);
	}

	private String contentsForVariable(Variable variable) {
		String name = nameOrAnonymous(variable.getName());
		CgType variableType = variable.getType();
		if (variableType == null) {
			EObject parent = variable.eContainer();
			if (parent instanceof VarDecl) {
				variableType = ((VarDecl) parent).getType();
			}
		}
		String typeText = typeText(variableType);

		PortInfo info = findPortInfo(variable);
		if (info != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("**`").append(info.direction).append(' ');
			if (!"bare".equals(info.protocol)) {
				sb.append(info.protocol).append(' ');
			}
			sb.append(typeText).append(' ').append(name).append("`**");
			sb.append("\n\nPort — `").append(info.direction).append("` direction, ");
			sb.append("`").append(info.protocol).append("` protocol.");
			return sb.toString();
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**`").append(typeText).append(' ').append(name).append("`**");
		sb.append("\n\nVariable");
		if (variableType != null) {
			sb.append(" of type `").append(typeText).append("`");
		}
		sb.append('.');
		return sb.toString();
	}

	private String contentsForTask(Task task) {
		int portCount = 0;
		if (task.getPortDecls() != null) {
			for (Object decl : task.getPortDecls()) {
				portCount += countPortsIn(decl);
			}
		}
		int declCount = task.getDecls() == null ? 0 : task.getDecls().size();
		StringBuilder sb = new StringBuilder();
		sb.append("**`task ").append(nameOrAnonymous(task.getName())).append("`**");
		sb.append("\n\nC⏚ task — ").append(portCount).append(" port")
				.append(portCount == 1 ? "" : "s");
		if (declCount > 0) {
			sb.append(", ").append(declCount).append(" inner declaration")
					.append(declCount == 1 ? "" : "s");
		}
		sb.append('.');
		return sb.toString();
	}

	private String contentsForNetwork(Network network) {
		int instCount = network.getInstances() == null ? 0 : network.getInstances().size();
		int portCount = 0;
		if (network.getPortDecls() != null) {
			for (Object decl : network.getPortDecls()) {
				portCount += countPortsIn(decl);
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("**`network ").append(nameOrAnonymous(network.getName())).append("`**");
		sb.append("\n\nC⏚ network — ").append(instCount).append(" instance")
				.append(instCount == 1 ? "" : "s");
		if (portCount > 0) {
			sb.append(", ").append(portCount).append(" port")
					.append(portCount == 1 ? "" : "s");
		}
		sb.append('.');
		return sb.toString();
	}

	private String contentsForBundle(Bundle bundle) {
		int declCount = bundle.getDecls() == null ? 0 : bundle.getDecls().size();
		StringBuilder sb = new StringBuilder();
		sb.append("**`bundle ").append(nameOrAnonymous(bundle.getName())).append("`**");
		sb.append("\n\nC⏚ bundle — ").append(declCount).append(" declaration")
				.append(declCount == 1 ? "" : "s").append('.');
		return sb.toString();
	}

	private String contentsForInst(Inst inst) {
		String name = nameOrAnonymous(inst.getName());
		Instantiable target = inst.getEntity();
		if (target == null) {
			target = inst.getTask();
		}
		if (target == null) {
			return "**`" + name + "`**\n\nInstance.";
		}
		String entityKind = target instanceof Network ? "network"
				: target instanceof Bundle ? "bundle" : "task";
		String entityName = target instanceof com.neosyn.cg.cg.Named
				? ((com.neosyn.cg.cg.Named) target).getName()
				: target.eClass().getName();
		StringBuilder sb = new StringBuilder();
		sb.append("**`").append(name).append(" = new ")
				.append(nameOrAnonymous(entityName)).append("`**");
		sb.append("\n\nInstance of ").append(entityKind).append(" `")
				.append(nameOrAnonymous(entityName)).append("`.");
		return sb.toString();
	}

	private static int countPortsIn(Object decl) {
		if (decl instanceof SinglePortDecl) {
			SinglePortDecl single = (SinglePortDecl) decl;
			return single.getPorts() == null ? 0 : single.getPorts().size();
		}
		if (decl instanceof MultiPortDecl) {
			int total = 0;
			MultiPortDecl multi = (MultiPortDecl) decl;
			if (multi.getDecls() != null) {
				for (SinglePortDecl inner : multi.getDecls()) {
					total += inner.getPorts() == null ? 0 : inner.getPorts().size();
				}
			}
			return total;
		}
		return 0;
	}

	private static PortInfo findPortInfo(Variable variable) {
		EObject container = variable.eContainer();
		PortDef portDef = container instanceof PortDef ? (PortDef) container : null;
		if (portDef == null) {
			return null;
		}
		SinglePortDecl single = null;
		EObject parent = portDef.eContainer();
		if (parent instanceof SinglePortDecl) {
			single = (SinglePortDecl) parent;
		}
		if (single == null) {
			return null;
		}
		String direction = single.getDirection() != null ? single.getDirection() : "?";

		String protocol = readProtocol(portDef);
		if ("bare".equals(protocol)) {
			EObject outer = single.eContainer();
			if (outer instanceof MultiPortDecl) {
				protocol = readProtocol((MultiPortDecl) outer);
			}
		}

		return new PortInfo(direction, protocol);
	}

	private static String readProtocol(PortDef portDef) {
		if (portDef.isPush() || portDef.isPushOld()) {
			return "push";
		}
		if (portDef.isStream() || portDef.isStreamOld()) {
			return "stream";
		}
		if (portDef.isConfirm() || portDef.isConfirmOld()) {
			return "confirm";
		}
		if (portDef.isReady() || portDef.isReadyOld()) {
			return "stream";
		}
		if (portDef.isAck() || portDef.isAckOld()) {
			return "confirm";
		}
		if (portDef.isSync() || portDef.isSyncOld()) {
			return "push";
		}
		return "bare";
	}

	private static String readProtocol(MultiPortDecl multi) {
		if (multi.isPush()) {
			return "push";
		}
		if (multi.isStream()) {
			return "stream";
		}
		if (multi.isConfirm()) {
			return "confirm";
		}
		if (multi.isReady()) {
			return "stream";
		}
		if (multi.isAck()) {
			return "confirm";
		}
		if (multi.isSync()) {
			return "push";
		}
		return "bare";
	}

	private static String typeText(CgType type) {
		if (type == null) {
			return "void";
		}
		ICompositeNode node = NodeModelUtils.findActualNodeFor(type);
		if (node == null) {
			return type.eClass().getName();
		}
		String text = NodeModelUtils.getTokenText(node).trim();
		return text.isEmpty() ? type.eClass().getName() : text;
	}

	private static String nameOrAnonymous(String name) {
		return (name == null || name.isEmpty()) ? "<anonymous>" : name;
	}

	private static final class PortInfo {
		final String direction;
		final String protocol;

		PortInfo(String direction, String protocol) {
			this.direction = direction;
			this.protocol = protocol;
		}
	}
}
