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
 * FSM (Finite State Machine) data returned by neosyn/getFsm.
 * Contains states and transitions for visualization.
 */
public class FsmData {

    private String taskName;
    private String initialState;
    private List<StateData> states = new ArrayList<>();
    private List<TransitionData> transitions = new ArrayList<>();
    private String error;

    public FsmData() {
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getInitialState() {
        return initialState;
    }

    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }

    public List<StateData> getStates() {
        return states;
    }

    public void setStates(List<StateData> states) {
        this.states = states;
    }

    public List<TransitionData> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionData> transitions) {
        this.transitions = transitions;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * State data for FSM visualization.
     */
    public static class StateData {
        private String name;
        private boolean isInitial;
        private int x;
        private int y;
        private int line;
        private int endLine;
        private List<String> actions = new ArrayList<>();
        private List<Integer> lines = new ArrayList<>();

        public StateData() {
        }

        public StateData(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isInitial() {
            return isInitial;
        }

        public void setInitial(boolean initial) {
            isInitial = initial;
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

        public List<String> getActions() {
            return actions;
        }

        public void setActions(List<String> actions) {
            this.actions = actions;
        }

        /** Distinct 0-based source line numbers covered by this state's body. */
        public List<Integer> getLines() {
            return lines;
        }

        public void setLines(List<Integer> lines) {
            this.lines = lines;
        }
    }

    /**
     * Transition data for FSM visualization.
     */
    public static class TransitionData {
        private String source;
        private String target;
        private String action;
        private String label;
        private List<Integer> lines = new ArrayList<>();

        public TransitionData() {
        }

        public TransitionData(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public List<Integer> getLines() {
            return lines;
        }

        public void setLines(List<Integer> lines) {
            this.lines = lines;
        }
    }
}
