/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.neosyn.cg.cg.Connect;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Obj;
import com.neosyn.cg.cg.Pair;
import com.neosyn.cg.cg.PortDecl;
import com.neosyn.cg.cg.PortDef;
import com.neosyn.cg.cg.Primitive;
import com.neosyn.cg.cg.SinglePortDecl;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarDecl;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.ide.server.FsmData.StateData;
import com.neosyn.cg.ide.server.FsmData.TransitionData;
import com.neosyn.cg.ide.server.GraphData.ConnectionData;
import com.neosyn.cg.ide.server.GraphData.InstanceData;
import com.neosyn.cg.ide.server.GraphData.PortData;

/**
 * Extracts FSM and Graph data from Cx AST for VS Code visualization.
 *
 * This class provides static methods to extract:
 * - FSM data from Task AST nodes (states, transitions)
 * - Graph data from Network AST nodes (instances, connections)
 */
public final class FsmGraphExtractor {

    private FsmGraphExtractor() {
        // Utility class - no instantiation
    }

    /**
     * Extract FSM data from a compiled IR {@link com.neosyn.models.dpn.Actor}.
     *
     * <p>This is the ground-truth path: it reads the very same FSM the Verilog
     * backend traverses to emit its {@code case (FSM)} block, so the FSM View
     * matches the generated HDL state-for-state. It supersedes the source-AST
     * heuristic in {@link #extractFsmFromTask}, which only ever guessed a
     * setup+loop pair and could not see the states the scheduler splits out
     * (e.g. one per {@code idle()} cycle or blocking port read).
     *
     * <p>An actor with no FSM (a combinational or single-action actor that the
     * backend emits without a {@code case (FSM)}) leaves {@code result} with no
     * states — the correct "No FSM" outcome, not an error.
     */
    public static void extractFsmFromActor(com.neosyn.models.dpn.Actor actor, FsmData result) {
        if (actor == null || !actor.hasFsm() || actor.getFsm() == null) {
            // No FSM: combinational / single-cycle actor. Leave states empty so
            // the view shows "No FSM", mirroring the absence of a case(FSM).
            return;
        }

        com.neosyn.models.dpn.FSM fsm = actor.getFsm();
        com.neosyn.models.dpn.State initial = fsm.getInitialState();

        // Group transitions by source state so each state can advertise the
        // actions it fires and the source line to navigate to. This mirrors the
        // Verilog printer, which renders a state's body from its OUTGOING
        // transitions' actions.
        Map<com.neosyn.models.dpn.State, List<com.neosyn.models.dpn.Transition>> bySource =
                new HashMap<>();
        for (com.neosyn.models.dpn.Transition t : fsm.getTransitions()) {
            bySource.computeIfAbsent(t.getSource(), k -> new ArrayList<>()).add(t);
        }

        int index = 0;
        for (com.neosyn.models.dpn.State state : fsm.getStates()) {
            String name = state.getName();
            if (name == null || name.isEmpty()) {
                name = "s" + index;
            }

            StateData sd = new StateData(name);
            sd.setInitial(state == initial);

            // Gather every source line the state's body touches, so the view
            // can highlight the whole state span and list each statement —
            // not just the action's first line. Walk the action body IR
            // (recursing into nested if/while blocks via getAllContents).
            java.util.TreeSet<Integer> srcLines = new java.util.TreeSet<>();
            List<com.neosyn.models.dpn.Transition> outgoing = bySource.get(state);
            if (outgoing != null) {
                for (com.neosyn.models.dpn.Transition t : outgoing) {
                    com.neosyn.models.dpn.Action action = t.getAction();
                    if (action != null) {
                        collectActionLines(action, srcLines);
                    }
                    // The transition itself also carries source lines.
                    if (t.getLines() != null) {
                        for (Integer l : t.getLines()) {
                            if (l != null && l > 0) {
                                srcLines.add(l);
                            }
                        }
                    }
                }
            }
            if (!srcLines.isEmpty()) {
                // IR line numbers are 1-based; the editor (and the renderer's
                // navigation) is 0-based.
                sd.setLine(srcLines.first() - 1);
                sd.setEndLine(srcLines.last() - 1);
                for (Integer l : srcLines) {
                    sd.getLines().add(l - 1);
                }
            }

            result.getStates().add(sd);
            index++;
        }

        if (initial != null) {
            String initName = initial.getName();
            result.setInitialState((initName == null || initName.isEmpty()) ? "s0" : initName);
        }

        for (com.neosyn.models.dpn.Transition t : fsm.getTransitions()) {
            com.neosyn.models.dpn.State src = t.getSource();
            com.neosyn.models.dpn.State tgt = t.getTarget();
            if (src == null || tgt == null) {
                continue;
            }
            TransitionData td = new TransitionData(src.getName(), tgt.getName());
            if (t.getLines() != null) {
                for (Integer l : t.getLines()) {
                    if (l != null && l > 0) {
                        td.getLines().add(l - 1);
                    }
                }
            }
            result.getTransitions().add(td);
        }

        layoutStatesCircle(result.getStates());
    }

    /**
     * Collect the 1-based source line numbers of every IR instruction in an
     * action's body (recursing through nested if/while blocks). These map back
     * to the C⏚ statements the state executes.
     */
    private static void collectActionLines(com.neosyn.models.dpn.Action action,
            java.util.Set<Integer> lines) {
        com.neosyn.models.ir.Procedure body = action.getBody();
        if (body == null) {
            return;
        }
        java.util.Iterator<EObject> it = body.eAllContents();
        while (it.hasNext()) {
            EObject o = it.next();
            if (o instanceof com.neosyn.models.ir.Instruction) {
                int ln = ((com.neosyn.models.ir.Instruction) o).getLineNumber();
                if (ln > 0) {
                    lines.add(ln);
                }
            }
        }
    }

    /**
     * Extract FSM data from a Task AST node.
     * Creates a state machine representation based on the task's functions.
     */
    public static void extractFsmFromTask(Task task, FsmData result) {
        // Check for FSM metadata in properties
        int numStates = 2; // Default: setup + loop
        int numTransitions = 2;

        if (task.getProperties() != null) {
            Map<String, Object> props = extractProperties(task.getProperties());
            if (props.containsKey("num_states")) {
                numStates = ((Number) props.get("num_states")).intValue();
            }
            if (props.containsKey("num_transitions")) {
                numTransitions = ((Number) props.get("num_transitions")).intValue();
            }
        }

        // Find functions in the task
        List<String> functions = new ArrayList<>();
        for (VarDecl decl : task.getDecls()) {
            for (Variable var : decl.getVariables()) {
                if (var.getBody() != null) {
                    functions.add(var.getName());
                }
            }
        }

        // Create states based on function names
        boolean hasSetup = functions.contains("setup");
        boolean hasLoop = functions.contains("loop");

        // State 0: Initial/Setup
        StateData initState = new StateData("s0");
        initState.setInitial(true);
        if (hasSetup) {
            initState.getActions().add("setup()");
        }
        result.getStates().add(initState);
        result.setInitialState("s0");

        // Create additional states
        for (int i = 1; i < numStates; i++) {
            StateData state = new StateData("s" + i);
            if (i == 1 && hasLoop) {
                state.getActions().add("loop()");
            }
            result.getStates().add(state);
        }

        // Create transitions
        if (numStates >= 2) {
            TransitionData t1 = new TransitionData("s0", "s1");
            t1.setLabel("init done");
            result.getTransitions().add(t1);

            TransitionData t2 = new TransitionData("s1", "s1");
            t2.setLabel("loop");
            result.getTransitions().add(t2);
        }

        // Add more transitions if specified
        for (int i = 2; i < numTransitions && i < numStates; i++) {
            TransitionData t = new TransitionData("s" + (i-1), "s" + i);
            result.getTransitions().add(t);
        }

        // Layout states in a circle
        layoutStatesCircle(result.getStates());
    }

    /**
     * Extract Graph data from a Network AST node.
     * Creates instance and connection data for visualization.
     */
    public static void extractGraphFromNetwork(Network network, GraphData result) {
        // Extract network input/output ports
        for (PortDecl portDecl : network.getPortDecls()) {
            if (portDecl instanceof SinglePortDecl) {
                SinglePortDecl singleDecl = (SinglePortDecl) portDecl;
                String direction = singleDecl.getDirection();
                for (PortDef portDef : singleDecl.getPorts()) {
                    Variable var = portDef.getVar();
                    if (var != null) {
                        PortData portData = new PortData(var.getName(), direction);
                        if ("in".equals(direction)) {
                            result.getInputPorts().add(portData);
                        } else {
                            result.getOutputPorts().add(portData);
                        }
                    }
                }
            }
        }

        // Extract instances
        Map<String, InstanceData> instanceMap = new HashMap<>();
        int x = 150;
        for (Inst inst : network.getInstances()) {
            InstanceData instance = new InstanceData();
            instance.setName(inst.getName());

            // Get entity name
            if (inst.getEntity() != null) {
                instance.setEntityName(inst.getEntity().getName());
            } else if (inst.getTask() != null) {
                instance.setEntityName("anonymous task");
            }

            instance.setX(x);
            instance.setY(100);
            instance.setWidth(100);
            instance.setHeight(60);
            x += 150;

            // Extract ports from the task
            Task task = inst.getTask();
            if (task != null) {
                extractTaskPorts(task, instance);
            }

            result.getInstances().add(instance);
            instanceMap.put(inst.getName(), instance);
        }

        // Extract connections
        for (Connect connect : network.getConnects()) {
            String instanceName = null;
            boolean isThis = connect.isThis();

            if (connect.getInstance() != null) {
                instanceName = connect.getInstance().getName();
            }

            String type = connect.getType();

            for (VarRef portRef : connect.getPorts()) {
                ConnectionData conn = new ConnectionData();

                if ("reads".equals(type)) {
                    String portName = getPortName(portRef);
                    if (isThis) {
                        conn.setSourceInstance(null);
                        conn.setSourcePort(portName);
                        conn.setTargetInstance(instanceName);
                        conn.setTargetPort("in");
                    } else {
                        String[] parts = parsePortRef(portRef);
                        if (parts != null) {
                            conn.setSourceInstance(parts[0]);
                            conn.setSourcePort(parts[1]);
                            conn.setTargetInstance(instanceName);
                            conn.setTargetPort("in");
                        }
                    }
                } else if ("writes".equals(type)) {
                    String portName = getPortName(portRef);
                    if (isThis) {
                        conn.setSourceInstance(instanceName);
                        conn.setSourcePort("out");
                        conn.setTargetInstance(null);
                        conn.setTargetPort(portName);
                    }
                }

                if (conn.getSourcePort() != null || conn.getTargetPort() != null) {
                    result.getConnections().add(conn);
                }
            }
        }
    }

    /**
     * Extract Graph data from the COMPILED DPN (dataflow process network) — the
     * same fully-resolved structure the HDL backend traverses. This is the
     * ground-truth path, the network-view analogue of {@link
     * #extractFsmFromActor}: instances, their resolved ports (with width and
     * interface), and the real connections all come straight from the IR, so
     * the Network View matches the generated Verilog instead of approximating
     * it from source text. Multi-line {@code reads(...)}/{@code writes(...)},
     * named-entity ports (which the AST path could not resolve), struct-
     * flattened handshakes and monomorphized widths are all handled for free
     * because no text is parsed.
     *
     * @param dpn        compiled network
     * @param astNetwork source AST network, used only to recover source line
     *                   numbers for click-to-navigate (may be {@code null})
     * @param result     output DTO
     */
    public static void extractGraphFromDpn(com.neosyn.models.dpn.DPN dpn,
            Network astNetwork, GraphData result) {
        // Network boundary ports (driven from / read by the outside world).
        for (com.neosyn.models.dpn.Port port : dpn.getInputs()) {
            result.getInputPorts().add(toPortData(port, "input"));
        }
        for (com.neosyn.models.dpn.Port port : dpn.getOutputs()) {
            result.getOutputPorts().add(toPortData(port, "output"));
        }

        // Recover source line spans for instances, keyed by instance name, so
        // a click in the view can jump to the `x = new Foo();` line.
        Map<String, int[]> lineByName = new HashMap<>();
        if (astNetwork != null) {
            for (Inst inst : astNetwork.getInstances()) {
                if (inst.getName() == null) {
                    continue;
                }
                ICompositeNode node = NodeModelUtils.getNode(inst);
                if (node != null) {
                    // NodeModelUtils lines are 1-based; the client expects 0-based.
                    lineByName.put(inst.getName(),
                            new int[] { node.getStartLine() - 1, node.getEndLine() - 1 });
                }
            }
        }

        // Instances and their resolved ports.
        for (com.neosyn.models.dpn.Instance inst : dpn.getInstances()) {
            InstanceData instance = new InstanceData();
            instance.setName(inst.getName());

            com.neosyn.models.dpn.Entity entity = inst.getEntity();
            if (entity != null) {
                instance.setEntityName(entity.getSimpleName());
                instance.setNetwork(entity instanceof com.neosyn.models.dpn.DPN);
                for (com.neosyn.models.dpn.Port port : entity.getInputs()) {
                    instance.getInputs().add(toPortData(port, "input"));
                }
                for (com.neosyn.models.dpn.Port port : entity.getOutputs()) {
                    instance.getOutputs().add(toPortData(port, "output"));
                }
            }

            int[] span = lineByName.get(inst.getName());
            if (span != null) {
                instance.setLine(span[0]);
                instance.setEndLine(span[1]);
            }

            result.getInstances().add(instance);
        }

        // Real connections, straight from the DPN graph edges. A null instance
        // on an endpoint means the network boundary ("this" on the client).
        for (com.neosyn.models.graph.Edge edge : dpn.getGraph().getEdges()) {
            if (!(edge instanceof com.neosyn.models.dpn.Connection)) {
                continue;
            }
            com.neosyn.models.dpn.Connection connection = (com.neosyn.models.dpn.Connection) edge;
            com.neosyn.models.dpn.Endpoint src = connection.getSourceEndpoint();
            com.neosyn.models.dpn.Endpoint tgt = connection.getTargetEndpoint();
            if (src == null || tgt == null || src.getPort() == null || tgt.getPort() == null) {
                continue;
            }

            ConnectionData conn = new ConnectionData();
            conn.setSourceInstance(src.hasInstance() ? src.getInstance().getName() : null);
            conn.setSourcePort(src.getPort().getName());
            conn.setTargetInstance(tgt.hasInstance() ? tgt.getInstance().getName() : null);
            conn.setTargetPort(tgt.getPort().getName());

            // Edge styling follows the source port's type/interface.
            com.neosyn.models.dpn.Port carrier = src.getPort();
            int[] classified = classifyType(carrier);
            conn.setSignalType(signalTypeName(classified[0]));
            conn.setWidth(classified[1]);
            conn.setInterfaceType(interfaceName(carrier.getInterface()));

            result.getConnections().add(conn);
        }
    }

    /**
     * Build a {@link PortData} from a compiled IR port, carrying its width,
     * signal classification and interface type for the renderer.
     */
    private static PortData toPortData(com.neosyn.models.dpn.Port port, String direction) {
        PortData data = new PortData(port.getName(), direction);
        int[] classified = classifyType(port);
        data.setSignalType(signalTypeName(classified[0]));
        data.setWidth(classified[1]);
        data.setInterfaceType(interfaceName(port.getInterface()));
        return data;
    }

    /**
     * Classify a port's type into a {@code [signalKind, width]} pair where
     * signalKind is 0=bool, 1=bus, 2=custom and width is the bus width in bits
     * (-1 when not a sized integer).
     */
    private static int[] classifyType(com.neosyn.models.dpn.Port port) {
        com.neosyn.models.ir.Type type = port.getType();
        if (type instanceof com.neosyn.models.ir.TypeBool) {
            return new int[] { 0, -1 };
        }
        if (type instanceof com.neosyn.models.ir.TypeInt) {
            return new int[] { 1, ((com.neosyn.models.ir.TypeInt) type).getSize() };
        }
        return new int[] { 2, -1 };
    }

    private static String signalTypeName(int kind) {
        switch (kind) {
            case 0:  return "bool";
            case 1:  return "bus";
            default: return "custom";
        }
    }

    /**
     * Map the IR {@link com.neosyn.models.dpn.InterfaceType} to the client's
     * connection-coloring vocabulary (bare/push/stream/confirm).
     */
    private static String interfaceName(com.neosyn.models.dpn.InterfaceType iface) {
        if (iface == null) {
            return "bare";
        }
        switch (iface) {
            case SYNC:       return "push";
            case SYNC_READY: return "stream";
            case SYNC_ACK:   return "confirm";
            case BARE:
            default:         return "bare";
        }
    }

    /**
     * Extract ports from a task definition.
     */
    public static void extractTaskPorts(Task task, InstanceData instance) {
        for (PortDecl portDecl : task.getPortDecls()) {
            if (portDecl instanceof SinglePortDecl) {
                SinglePortDecl singleDecl = (SinglePortDecl) portDecl;
                String direction = singleDecl.getDirection();
                for (PortDef portDef : singleDecl.getPorts()) {
                    Variable var = portDef.getVar();
                    if (var != null) {
                        PortData portData = new PortData(var.getName(), direction);
                        if ("in".equals(direction)) {
                            instance.getInputs().add(portData);
                        } else {
                            instance.getOutputs().add(portData);
                        }
                    }
                }
            }
        }

        // Add default ports if none found
        if (instance.getInputs().isEmpty()) {
            instance.getInputs().add(new PortData("in", "input"));
        }
        if (instance.getOutputs().isEmpty()) {
            instance.getOutputs().add(new PortData("out", "output"));
        }
    }

    /**
     * Extract properties from an Obj (JSON-like properties block).
     */
    public static Map<String, Object> extractProperties(Obj obj) {
        Map<String, Object> result = new HashMap<>();
        if (obj != null && obj.getMembers() != null) {
            for (Pair pair : obj.getMembers()) {
                String key = pair.getKey();
                Object value = extractValue(pair.getValue());
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Extract a value from an Element.
     */
    private static Object extractValue(EObject element) {
        if (element instanceof Primitive) {
            Primitive prim = (Primitive) element;
            if (prim.getValue() != null) {
                String text = prim.getValue().toString();
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * Get simple port name from VarRef.
     */
    private static String getPortName(VarRef ref) {
        if (ref != null && !ref.getObjects().isEmpty()) {
            return ref.getObjects().get(ref.getObjects().size() - 1).getName();
        }
        return "unknown";
    }

    /**
     * Parse a port reference like "instance.port" into [instance, port].
     */
    private static String[] parsePortRef(VarRef ref) {
        if (ref != null && ref.getObjects().size() >= 2) {
            String instance = ref.getObjects().get(0).getName();
            String port = ref.getObjects().get(1).getName();
            return new String[] { instance, port };
        }
        return null;
    }

    /**
     * Layout states in a circular pattern.
     */
    public static void layoutStatesCircle(List<StateData> states) {
        int centerX = 200;
        int centerY = 150;
        int radius = 100;

        int n = states.size();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            int x = centerX + (int)(radius * Math.cos(angle));
            int y = centerY + (int)(radius * Math.sin(angle));
            states.get(i).setX(x);
            states.get(i).setY(y);
        }
    }
}
