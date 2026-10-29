/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.transformations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.InterfaceType;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.graph.Edge;
import com.neosyn.neosynide.internal.generators.BuiltinEntityHelper;

/**
 * Marks the ports of <em>direct held-producer stream links</em> so the Verilog backend can
 * emit a standard (non-pipelined) ready/valid handshake for them.
 *
 * <p>A {@code sync ready} stream connection has two incompatible producer flavours:
 * <ul>
 * <li>a <b>registered</b> producer drives {@code valid} one cycle <em>after</em> it sees
 * {@code ready} — its consumer must pulse {@code ready} only while waiting ({@code ready = !valid})
 * or it takes two values;</li>
 * <li>a <b>held</b> producer presents {@code valid} <em>with</em> the data and holds it until
 * taken — its consumer must assert {@code ready = 1} and the producer must not advance until
 * {@code ready} is seen.</li>
 * </ul>
 * The two need opposite consumer logic and the consumer cannot tell them apart locally, so
 * {@link AddReadyAssignments} defaults to the registered ({@code !valid}) form. This marker finds
 * the HELD links -- both ends a plain generated task or a built-in that holds its value
 * ({@code std.fifo.SynchronousFIFO} since 2026-09-15, when it became first-word-fall-through; see
 * {@link #HELD_BUILTINS}) -- and records, in each TASK end's {@code properties}, the port names
 * that carry one ({@link #PROP_DIRECT_HELD_INPUTS} on a consumer, {@link #PROP_DIRECT_HELD_OUTPUTS}
 * on a producer). {@code AddReadyAssignments} (consumer) and {@code SynchronousPrinter} (producer
 * hold) read those marks. Only links to other built-ins, external entities and a design's own
 * ports keep the registered form.
 *
 * <p><b>A link is followed through network boundaries</b> (TRAPS T57/T58, S182). A sub-network
 * adds no register, so a task writing its network's stream output is as direct a producer as a
 * sibling task. Judging only the connection at hand missed exactly that: in the UDP-IP-Stack,
 * {@code UDP_Send}'s inline task writes {@code UDP_Send.dout}, {@code UDP_Top} re-exports it and
 * {@code IpTop} wires it to the {@code IP_Send} task — task to task, two boundaries apart. Left
 * unmarked, the consumer took the registered handshake and the producer the stall path, which
 * overwrote every value presented while {@code IP_Send} was not reading: 31 bytes of every UDP
 * reply. Each connection between instances is now resolved to its leaf endpoints; a connection
 * touching the enclosing network's own port is skipped, because the parent network resolves it.
 *
 * <p><b>One module serves every context.</b> An actor is shared by all its instantiations, so a
 * port fed by a held task in one network and by a FIFO in another cannot have both handshakes.
 * The non-held side is recorded too ({@link #PROP_NON_HELD_INPUTS}/{@link #PROP_NON_HELD_OUTPUTS})
 * and {@link #isMarked} reports a held mark only when no context contradicts it, so such a port
 * keeps the historical registered form whatever order the networks are visited in.
 *
 * <p>Runs once up-front over the whole entity hierarchy — {@code collectEntities} transforms
 * leaf actors before their parent network, so the marks must exist before any per-actor
 * transformation runs. Idempotent.
 */
public final class StreamLinkMarker {

	/** Consumer-actor property: JSON array of input-port names fed by a direct held producer. */
	public static final String PROP_DIRECT_HELD_INPUTS = "cgDirectHeldInputs";

	/** Producer-actor property: JSON array of output-port names driving a direct held consumer. */
	public static final String PROP_DIRECT_HELD_OUTPUTS = "cgDirectHeldOutputs";

	/** Consumer-actor property: input-port names that some context feeds from a NON-held producer. */
	public static final String PROP_NON_HELD_INPUTS = "cgNonHeldInputs";

	/** Producer-actor property: output-port names that some context wires to a NON-held consumer. */
	public static final String PROP_NON_HELD_OUTPUTS = "cgNonHeldOutputs";

	/** Bound on nested network boundaries followed for one link (guards a malformed cyclic graph). */
	private static final int MAX_DEPTH = 64;

	private StreamLinkMarker() {
	}

	/**
	 * Walks the entity hierarchy rooted at {@code top} and marks the ports of every direct
	 * held-producer stream link. Safe to call on any entity and any number of times.
	 */
	public static void mark(Entity top) {
		markRecursive(top, new LinkedHashSet<>());
	}

	/**
	 * TRAPS T156. A task's testbench network (TestGenerator) is built after {@link #mark} ran, so
	 * its synthetic checker was never marked: it took the registered-producer handshake ({@code
	 * ready = !valid} in a state followed by a {@code null} vector) against a DUT that HOLDS its
	 * value -- the value was checked but not taken, and checked again. This marks the checker's
	 * stream inputs fed by a holding producer, and ONLY those: the DUT's own marks stay as its real
	 * networks set them, so a testbench can never change the design's module.
	 */
	public static void markTestbenchChecker(DPN testbench) {
		if (testbench.getGraph() == null) {
			return;
		}
		for (Edge edge : testbench.getGraph().getEdges()) {
			if (!(edge instanceof Connection)) {
				continue;
			}
			Connection connection = (Connection) edge;
			Endpoint source = connection.getSourceEndpoint();
			Endpoint target = connection.getTargetEndpoint();
			if (source == null || target == null || !source.hasInstance() || !target.hasInstance()) {
				continue;
			}
			Entity checker = target.getInstance().getEntity();
			Port sourcePort = resolved(connection.getSourcePort());
			Port targetPort = connection.getTargetPort();
			if (!(checker instanceof Actor) || checker.getProperties() == null
					|| !checker.getProperties().has("synthetic") || sourcePort == null || targetPort == null
					|| sourcePort.getInterface() != InterfaceType.SYNC_READY
					|| targetPort.getInterface() != InterfaceType.SYNC_READY) {
				continue;
			}
			Endpoint producer = resolveProducer(source);
			if (producer != null && kindOf(producer) != Kind.NON_HELD) {
				addPortName(checker, PROP_DIRECT_HELD_INPUTS, targetPort.getName());
			}
		}
	}

	private static void markRecursive(Entity entity, Set<Entity> visited) {
		if (!(entity instanceof DPN) || !visited.add(entity)) {
			return;
		}
		DPN dpn = (DPN) entity;

		if (dpn.getGraph() != null) {
			for (Edge edge : dpn.getGraph().getEdges()) {
				if (edge instanceof Connection) {
					markConnection((Connection) edge);
				}
			}
		}

		for (Instance instance : dpn.getInstances()) {
			markRecursive(instance.getEntity(), visited);
		}
	}

	private static void markConnection(Connection connection) {
		// A built-in's port in a connection is often an unresolved proxy
		// (`classpath:/builtin-ir/std/fifo/SynchronousFIFO.ir#//@outputs.0`) whose interface reads
		// as the default. Judged unresolved, every FIFO link looked like a non-stream connection
		// and was skipped -- so a module fed by a FIFO in one network and a task in another took
		// whichever handshake the task context gave it (TRAPS T103).
		Port sourcePort = resolved(connection.getSourcePort());
		Port targetPort = resolved(connection.getTargetPort());
		if (sourcePort == null || targetPort == null) {
			return;
		}
		if (sourcePort.getInterface() != InterfaceType.SYNC_READY
				|| targetPort.getInterface() != InterfaceType.SYNC_READY) {
			return;
		}
		Endpoint source = connection.getSourceEndpoint();
		Endpoint target = connection.getTargetEndpoint();
		// A connection from the enclosing network's own input, or to its own output, is one
		// segment of a longer link; the parent network sees both real ends and resolves it.
		if (source == null || target == null || !source.hasInstance() || !target.hasInstance()) {
			return;
		}

		Endpoint producer = resolveProducer(source);
		List<Endpoint> consumers = resolveConsumers(target, 0);
		if (producer == null || consumers == null || consumers.size() != 1) {
			return; // leaves the network, or fans out: not a single direct link
		}
		Endpoint consumer = consumers.get(0);
		Kind producerKind = kindOf(producer);
		Kind consumerKind = kindOf(consumer);

		// A held link: each end is a generated task or a built-in that holds its value until
		// taken (std.fifo.SynchronousFIFO). Marks go on the TASK ends only -- a built-in's module
		// is fixed HDL, and its entity is often a shared proxy.
		boolean held = producerKind != Kind.NON_HELD && consumerKind != Kind.NON_HELD;
		if (producerKind == Kind.TASK) {
			addPortName(producer.getInstance().getEntity(),
					held ? PROP_DIRECT_HELD_OUTPUTS : PROP_NON_HELD_OUTPUTS, producer.getPort().getName());
		}
		if (consumerKind == Kind.TASK) {
			addPortName(consumer.getInstance().getEntity(),
					held ? PROP_DIRECT_HELD_INPUTS : PROP_NON_HELD_INPUTS, consumer.getPort().getName());
		}
	}

	/** The real port behind a built-in's proxy port, or the port itself. */
	private static Port resolved(Port port) {
		Port real = BuiltinEntityHelper.resolveBuiltinPort(port);
		return real != null ? real : port;
	}

	/**
	 * Follows a stream output down through sub-networks to the leaf instance that drives it, or
	 * returns {@code null} when the driver is not a leaf inside the hierarchy (e.g. a sub-network
	 * output wired straight to one of its own inputs).
	 */
	private static Endpoint resolveProducer(Endpoint endpoint) {
		for (int depth = 0; endpoint != null && depth < MAX_DEPTH; depth++) {
			if (!endpoint.hasInstance()) {
				return null;
			}
			Entity entity = endpoint.getInstance().getEntity();
			if (!(entity instanceof DPN)) {
				return endpoint;
			}
			if (endpoint.getPort().eContainer() != entity) {
				return null;
			}
			endpoint = ((DPN) entity).getIncoming(endpoint.getPort());
		}
		return null;
	}

	/**
	 * Follows a stream input down through sub-networks to the leaf instance(s) that consume it.
	 * Returns {@code null} when some path does not end at a leaf inside the hierarchy.
	 */
	private static List<Endpoint> resolveConsumers(Endpoint endpoint, int depth) {
		if (endpoint == null || !endpoint.hasInstance() || depth >= MAX_DEPTH) {
			return null;
		}
		Entity entity = endpoint.getInstance().getEntity();
		List<Endpoint> leaves = new ArrayList<>();
		if (!(entity instanceof DPN)) {
			leaves.add(endpoint);
			return leaves;
		}
		if (endpoint.getPort().eContainer() != entity) {
			return null;
		}
		for (Connection inner : ((DPN) entity).getOutgoing(endpoint.getPort())) {
			List<Endpoint> found = resolveConsumers(inner.getTargetEndpoint(), depth + 1);
			if (found == null) {
				return null;
			}
			leaves.addAll(found);
		}
		return leaves;
	}

	/** What sits at one end of a stream link, as far as the handshake is concerned. */
	private enum Kind {
		/** A plain generated task: holds its output until taken, and can be marked. */
		TASK,
		/** A built-in whose HDL holds its output until taken; never marked itself. */
		HELD_BUILTIN,
		/** Anything else -- another built-in, an external entity, or an unresolved one. */
		NON_HELD
	}

	/**
	 * Built-ins whose HDL is a HELD endpoint on both sides: they present a value and hold it
	 * until it is taken, and accept a value only on valid && ready. SynchronousFIFO became one on
	 * 2026-09-15 (decision, TRAPS T103 / T57). std.math.Divide and Multiply joined on
	 * 2026-09-30: their RTL already accepted an operand only on valid && ready and held the
	 * result until taken, but left out of this list, a task reading their result kept the
	 * registered `ready = !valid` handshake -- and a reader that paused between reads lost every
	 * other result on both back ends (TRAPS T57 remainder, tools/qa/repro/T57-builtins).
	 * AsynchronousFIFO is still not listed: its Verilog does not build yet (TRAPS T133).
	 */
	private static final Set<String> HELD_BUILTINS = Set.of("std.fifo.SynchronousFIFO",
			"std.math.Divide", "std.math.Multiply");

	private static Kind kindOf(Endpoint endpoint) {
		Instance instance = endpoint.getInstance();
		Entity entity = instance.getEntity();
		// A plain generated task: a resolved Actor with NO implementation property. Checked FIRST:
		// BuiltinEntityHelper.getEntityType falls back to the entity's own name, so it is non-null
		// for every task too and cannot tell a task from a built-in.
		if (entity instanceof Actor && !entity.eIsProxy() && CoreUtil.getImplementation(entity) == null) {
			return Kind.TASK;
		}
		// Otherwise a built-in -- identified by its `entityType` property (a proxy entity) or its
		// resolved name -- or an external entity.
		String type = BuiltinEntityHelper.getEntityType(instance);
		if (type != null && HELD_BUILTINS.contains(type)) {
			return Kind.HELD_BUILTIN;
		}
		return Kind.NON_HELD;
	}

	private static void addPortName(Entity actor, String key, String portName) {
		if (actor == null || portName == null) {
			return;
		}
		JsonObject properties = actor.getProperties();
		if (properties == null) {
			properties = new JsonObject();
			actor.setProperties(properties);
		}
		JsonArray names = properties.getAsJsonArray(key);
		if (names == null) {
			names = new JsonArray();
			properties.add(key, names);
		}
		for (JsonElement element : names) {
			if (portName.equals(element.getAsString())) {
				return; // already marked
			}
		}
		names.add(new JsonPrimitive(portName));
	}

	/**
	 * True when {@code actor} is a <em>held producer</em>: its only stream output
	 * ({@code readyOutputs}, exactly one) carries a direct held link. Then the HDL gates the actor's
	 * WHOLE scheduler on that output ({@code if (ready || !valid)}): while its value waits unread,
	 * no action runs, not even one that writes no stream. Any other actor gates each action only on
	 * the stream outputs that action writes. The Verilog and VHDL printers and the simulator all ask
	 * here, so the three cannot disagree (TRAPS T168).
	 */
	public static boolean isDirectHeldProducer(Entity actor, List<Port> readyOutputs) {
		return readyOutputs.size() == 1 && isMarked(actor, PROP_DIRECT_HELD_OUTPUTS, readyOutputs.get(0).getName());
	}

	/** Returns {@code true} if the actor has any port recorded under {@code key}. */
	public static boolean hasAnyMark(Entity actor, String key) {
		if (actor == null) {
			return false;
		}
		JsonObject properties = actor.getProperties();
		if (properties == null) {
			return false;
		}
		JsonArray names = properties.getAsJsonArray(key);
		return names != null && names.size() > 0;
	}

	/**
	 * Returns {@code true} if {@code portName} is recorded under {@code key} in the actor's
	 * properties (used by the readers in the Verilog and VHDL backends). A held key
	 * ({@link #PROP_DIRECT_HELD_INPUTS}/{@link #PROP_DIRECT_HELD_OUTPUTS}) counts only when no
	 * context recorded the same port as non-held — see the class comment.
	 */
	public static boolean isMarked(Entity actor, String key, String portName) {
		if (!isRecorded(actor, key, portName)) {
			return false;
		}
		if (PROP_DIRECT_HELD_INPUTS.equals(key)) {
			return !isRecorded(actor, PROP_NON_HELD_INPUTS, portName);
		}
		if (PROP_DIRECT_HELD_OUTPUTS.equals(key)) {
			return !isRecorded(actor, PROP_NON_HELD_OUTPUTS, portName);
		}
		return true;
	}

	private static boolean isRecorded(Entity actor, String key, String portName) {
		if (actor == null || portName == null) {
			return false;
		}
		JsonObject properties = actor.getProperties();
		if (properties == null) {
			return false;
		}
		JsonArray names = properties.getAsJsonArray(key);
		if (names == null) {
			return false;
		}
		for (JsonElement element : names) {
			if (portName.equals(element.getAsString())) {
				return true;
			}
		}
		return false;
	}
}
