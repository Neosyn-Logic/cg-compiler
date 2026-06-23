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
import java.util.List;

/**
 * Graph (DPN - Dataflow Process Network) data returned by neosyn/getGraph.
 * Contains instances (actors) and connections for visualization.
 */
public class GraphData {

    private String networkName;
    private List<PortData> inputPorts = new ArrayList<>();
    private List<PortData> outputPorts = new ArrayList<>();
    private List<InstanceData> instances = new ArrayList<>();
    private List<ConnectionData> connections = new ArrayList<>();
    private String error;

    public GraphData() {
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public List<PortData> getInputPorts() {
        return inputPorts;
    }

    public void setInputPorts(List<PortData> inputPorts) {
        this.inputPorts = inputPorts;
    }

    public List<PortData> getOutputPorts() {
        return outputPorts;
    }

    public void setOutputPorts(List<PortData> outputPorts) {
        this.outputPorts = outputPorts;
    }

    public List<InstanceData> getInstances() {
        return instances;
    }

    public void setInstances(List<InstanceData> instances) {
        this.instances = instances;
    }

    public List<ConnectionData> getConnections() {
        return connections;
    }

    public void setConnections(List<ConnectionData> connections) {
        this.connections = connections;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Port data for network inputs/outputs.
     */
    public static class PortData {
        private String name;
        private String type;
        private String direction; // "input" or "output"
        private int width = -1;            // bus width in bits; -1 = unknown/none
        private String signalType;         // "bool" | "bus" | "custom"
        private String interfaceType;      // "bare" | "push" | "stream" | "confirm"
        private int x;
        private int y;

        public PortData() {
        }

        public PortData(String name, String direction) {
            this.name = name;
            this.direction = direction;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public String getSignalType() {
            return signalType;
        }

        public void setSignalType(String signalType) {
            this.signalType = signalType;
        }

        public String getInterfaceType() {
            return interfaceType;
        }

        public void setInterfaceType(String interfaceType) {
            this.interfaceType = interfaceType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    /**
     * Instance (actor) data for DPN visualization.
     */
    public static class InstanceData {
        private String name;
        private String entityName;
        private List<PortData> inputs = new ArrayList<>();
        private List<PortData> outputs = new ArrayList<>();
        private int line = -1;       // 0-based source line of the instantiation
        private int endLine = -1;    // 0-based end line
        private boolean network;     // true if the instantiated entity is a network
        private int x;
        private int y;
        private int width;
        private int height;

        public InstanceData() {
        }

        public InstanceData(String name, String entityName) {
            this.name = name;
            this.entityName = entityName;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public int getEndLine() {
            return endLine;
        }

        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }

        public boolean isNetwork() {
            return network;
        }

        public void setNetwork(boolean network) {
            this.network = network;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEntityName() {
            return entityName;
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public List<PortData> getInputs() {
            return inputs;
        }

        public void setInputs(List<PortData> inputs) {
            this.inputs = inputs;
        }

        public List<PortData> getOutputs() {
            return outputs;
        }

        public void setOutputs(List<PortData> outputs) {
            this.outputs = outputs;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }

    /**
     * Connection data for DPN visualization.
     */
    public static class ConnectionData {
        private String sourceInstance; // null for network input port
        private String sourcePort;
        private String targetInstance; // null for network output port
        private String targetPort;
        private int width = -1;            // bus width in bits; -1 = unknown/none
        private String signalType;         // "bool" | "bus" | "custom"
        private String interfaceType;      // "bare" | "push" | "stream" | "confirm"

        public ConnectionData() {
        }

        public ConnectionData(String sourceInstance, String sourcePort,
                              String targetInstance, String targetPort) {
            this.sourceInstance = sourceInstance;
            this.sourcePort = sourcePort;
            this.targetInstance = targetInstance;
            this.targetPort = targetPort;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public String getSignalType() {
            return signalType;
        }

        public void setSignalType(String signalType) {
            this.signalType = signalType;
        }

        public String getInterfaceType() {
            return interfaceType;
        }

        public void setInterfaceType(String interfaceType) {
            this.interfaceType = interfaceType;
        }

        public String getSourceInstance() {
            return sourceInstance;
        }

        public void setSourceInstance(String sourceInstance) {
            this.sourceInstance = sourceInstance;
        }

        public String getSourcePort() {
            return sourcePort;
        }

        public void setSourcePort(String sourcePort) {
            this.sourcePort = sourcePort;
        }

        public String getTargetInstance() {
            return targetInstance;
        }

        public void setTargetInstance(String targetInstance) {
            this.targetInstance = targetInstance;
        }

        public String getTargetPort() {
            return targetPort;
        }

        public void setTargetPort(String targetPort) {
            this.targetPort = targetPort;
        }
    }
}
