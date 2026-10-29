/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.scheduler;

import static com.neosyn.models.util.SwitchUtil.visit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Switch;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.neosyn.cg.cg.Branch;
import com.neosyn.cg.cg.CgExpression;
import com.neosyn.cg.cg.Enter;
import com.neosyn.cg.cg.Leave;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.node.Node;
import com.neosyn.models.util.Void;

/**
 * This class tracks the reads/writes in a current task, and is used to compute the cycle-accurate
 * behavior.
 * 
 * @author Synflow team (now Neosyn)
 * 
 */
public class Schedule {

	/**
	 * Returns <code>true</code> if the given list of objects is empty (or contains only Enter/Leave
	 * objects).
	 * 
	 * @param eObjects
	 *            a list of objects (like <code>transition.getBody()</code> or
	 *            <code>transition.getScheduler()</code>).
	 * @return true if the list is assimilated to empty
	 */
	public static boolean isEmpty(List<EObject> eObjects) {
		// returns true if iterable is empty or it just contains Enter/Leave instances
		return Iterables.all(eObjects, new Predicate<EObject>() {
			@Override
			public boolean apply(EObject eObject) {
				return eObject instanceof Enter || eObject instanceof Leave;
			}
		});
	}

	protected final Actor actor;

	/**
	 * TRAPS T31: a handshaked read registered in a cycle that had ALREADY written a port -- the
	 * source says "send, then wait for the answer" inside one action. Recorded per actor so the
	 * network-level check (TransformationHandler) can see whether the answer comes from the very
	 * instance the request goes to, which can never complete. Kept outside the model on purpose;
	 * the actor is the key and is cleared before it is scheduled again.
	 */
	public static final class ReadAfterWrite {
		public final Port read;
		public final VarRef ref;
		public final List<Port> writtenBefore;

		ReadAfterWrite(Port read, VarRef ref, List<Port> writtenBefore) {
			this.read = read;
			this.ref = ref;
			this.writtenBefore = writtenBefore;
		}
	}

	private static final Map<Actor, Set<ReadAfterWrite>> READS_AFTER_WRITE =
			Collections.synchronizedMap(new WeakHashMap<>());

	public static Set<ReadAfterWrite> readsAfterWrite(Actor actor) {
		Set<ReadAfterWrite> set = READS_AFTER_WRITE.get(actor);
		return set == null ? Collections.emptySet() : set;
	}

	public static void forgetReadsAfterWrite(Actor actor) {
		READS_AFTER_WRITE.remove(actor);
	}

	protected final IInstantiator instantiator;

	private final List<ICycleListener> listeners;

	private Node node;

	private boolean usePeek;

	/**
	 * When true, cycle starting is disabled. Used for combinational actors where
	 * all logic must execute in a single "instant" without cycle boundaries.
	 */
	private boolean disableCycles;

	public Schedule(IInstantiator instantiator, Actor actor) {
		this.instantiator = instantiator;
		this.actor = actor;
		node = new Node(DpnFactory.eINSTANCE.createActionEmpty());
		listeners = new ArrayList<>();
		disableCycles = false;
	}

	/**
	 * Disables cycle starting for this schedule. Used for combinational actors.
	 */
	public void setDisableCycles(boolean disableCycles) {
		this.disableCycles = disableCycles;
	}

	/**
	 * Creates a new schedule whose reads/writes are copied from the given schedule.
	 * 
	 * @param schedule
	 *            an existing schedule
	 */
	public Schedule(Schedule schedule) {
		this(schedule.instantiator, schedule.actor);
		DpnFactory.eINSTANCE.addPatterns(getAction(), schedule.getAction());
		listeners.addAll(schedule.listeners);
	}

	public void addListener(ICycleListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Before testing if the port is available, if it has already been read starts a new cycle.
	 *
	 * @param ref
	 *            reference to an input port
	 */
	public void available(VarRef ref) {
		Port port = patternPort(ref);
		if (port == null) {
			// Port could not be resolved (e.g., built-in entity port like line1.q)
			return;
		}
		if (hasBeenRead(port)) {
			startNewCycle();
		}
	}

	/**
	 * The port a read, write or test of {@code ref} registers in the patterns. A STRUCT port has no
	 * Port of its own: it fans out to field ports, and for a push/stream struct only the FIRST field
	 * carries the handshake (SkeletonMaker, Tier 2.4). That field stands for the whole struct here.
	 * Returning null for it -- as before -- left every struct read and write out of the patterns:
	 * the reader never asserted ready nor waited on valid, and a stream struct through a task hung
	 * in Verilog (TRAPS T169). Null still means "not a port of this actor" (a built-in's port).
	 */
	private Port patternPort(VarRef ref) {
		Port port = instantiator.getPort(actor, ref);
		if (port != null) {
			return port;
		}
		Map<String, Port> fields = instantiator.getStructPortFields(actor, ref);
		return fields == null || fields.isEmpty() ? null : fields.values().iterator().next();
	}

	/**
	 * Returns the current action holding the peek/input/output patterns.
	 * 
	 * @return an action
	 */
	protected final Action getAction() {
		return getAction(node);
	}

	/**
	 * Returns the action associated with the given node.
	 * 
	 * @param node
	 *            a node
	 * @return an action
	 */
	protected Action getAction(Node node) {
		return (Action) node.getContent();
	}

	public Node getNode() {
		return node;
	}

	/**
	 * Returns <code>true</code> if the port has been read in the current cycle.
	 * 
	 * @param port
	 *            an input port
	 * @return a boolean
	 */
	protected boolean hasBeenRead(Port port) {
		return getAction().getInputPattern().contains(port);
	}

	/**
	 * Returns <code>true</code> if the port has been written in the current cycle.
	 * 
	 * @param port
	 *            an output port
	 * @return a boolean
	 */
	protected boolean hasBeenWritten(Port port) {
		return getAction().getOutputPattern().contains(port);
	}

	protected final void newCycleStarted() {
		int i = 0; 
		while (i < listeners.size()) {
			ICycleListener listener = listeners.get(i);
			int size = listeners.size();
			listener.newCycleStarted();
			if (size == listeners.size() && listener == listeners.get(i)) {
				i++;
			}
		}
	}

	/**
	 * Adds peeks to reads.
	 * 
	 * @param action
	 */
	public final void promotePeeks(Action action) {
		action.getInputPattern().add(action.getPeekPattern());
	}

	/**
	 * Registers a read from the given port.
	 *
	 * @param ref
	 *            reference to an input port
	 */
	public void read(VarRef ref) {
		Port port = patternPort(ref);
		if (port == null) {
			// Port could not be resolved (e.g., built-in entity port like line1.q)
			// Skip pattern registration - the port will be handled during code generation
			return;
		}
		if (hasBeenRead(port)) {
			startNewCycle();
		} else if (!usePeek && port.isSynchronous() && !getAction().getOutputPattern().isEmpty()) {
			READS_AFTER_WRITE.computeIfAbsent(actor, a -> Collections.synchronizedSet(new LinkedHashSet<>()))
					.add(new ReadAfterWrite(port, ref, new ArrayList<>(getAction().getOutputPattern().getPorts())));
		}

		if (usePeek) {
			getAction().getPeekPattern().add(port);
		} else {
			getAction().getInputPattern().add(port);
		}
	}

	/**
	 * Before testing if the port is ready, if it has already been written starts a new cycle.
	 *
	 * @param ref
	 *            reference to an output port
	 */
	public void ready(VarRef ref) {
		Port port = patternPort(ref);
		if (port == null) {
			// Port could not be resolved (e.g., built-in entity port like line1.q)
			return;
		}
		if (hasBeenWritten(port)) {
			startNewCycle();
		}
	}

	public void removeListener(ICycleListener listener) {
		listeners.remove(listener);
	}

	public void setNode(Node node) {
		this.node = node;
	}

	/**
	 * Starts a new cycle. Does nothing if cycles are disabled (for combinational actors).
	 * Subclasses should override doStartNewCycle() to provide custom behavior.
	 */
	public void startNewCycle() {
		// For combinational actors, never start new cycles - all logic is in one instant
		if (disableCycles) {
			return;
		}

		doStartNewCycle();
	}

	/**
	 * Internal method that actually starts a new cycle. Called by startNewCycle()
	 * after checking the disableCycles flag. Subclasses can override this.
	 */
	protected void doStartNewCycle() {
		// if the node had any child branches, clear them up
		node.clearChildren();

		node.setContent(DpnFactory.eINSTANCE.createActionEmpty());
		newCycleStarted();
	}

	@Override
	public String toString() {
		return getAction().toString();
	}

	/**
	 * Visits the given branch (condition and body) with the given void switch, and then promote
	 * peeks.
	 * 
	 * @param voidSwitch
	 *            an EMF Void Switch
	 * @param branch
	 *            a Branch
	 */
	public void visitBranch(Switch<Void> voidSwitch, Branch branch) {
		visitCondition(voidSwitch, branch.getCondition());
		visit(voidSwitch, branch.getBody());
		promotePeeks(getAction());
	}

	/**
	 * Visits the given condition with the given void switch.
	 * 
	 * @param voidSwitch
	 *            an EMF Void Switch
	 * @param condition
	 *            a CgExpression (may be <code>null</code>)
	 */
	public void visitCondition(Switch<Void> voidSwitch, CgExpression condition) {
		if (condition != null) {
			// condition may be absent (for else statements)
			usePeek = true;
			visit(voidSwitch, condition);
			usePeek = false;
		}
	}

	/**
	 * Visits a write to the given port.
	 *
	 * @param voidSwitch
	 *            an EMF Void Switch
	 * @param stmt
	 *            a write statement
	 */
	public void write(Switch<Void> voidSwitch, StatementWrite stmt) {
		// first check for existing writes: a second write to the same port starts its new cycle
		// BEFORE the value is visited. Visiting the value first registered a read inside it
		// (`packet.write(x); packet.write(body.read());`) in the PREVIOUS cycle's input pattern,
		// while the value itself was used one cycle later: the token was taken a cycle early and
		// the next one was used instead, on every backend (TRAPS T100).
		Port port = patternPort(stmt.getPort());
		if (port != null && hasBeenWritten(port)) {
			startNewCycle();
		}

		// only then visit the value -- and the `last` argument of `write(v, last)`, which can read
		// ports too (TRAPS T151) -- (always visit, even if port is null)
		visit(voidSwitch, stmt.getValue(), stmt.getLast());

		if (port == null) {
			// Port could not be resolved (e.g., built-in entity port like line1.d)
			// Skip pattern registration - the port will be handled during code generation
			return;
		}

		// and records the fact that we are doing a write
		addToOutputPattern(port);
	}

	/**
	 * Records a write of {@code port} in the current action's output pattern. ScheduleFsm records
	 * it on EVERY open transition (TRAPS T135).
	 */
	protected void addToOutputPattern(Port port) {
		getAction().getOutputPattern().add(port);
	}

}
