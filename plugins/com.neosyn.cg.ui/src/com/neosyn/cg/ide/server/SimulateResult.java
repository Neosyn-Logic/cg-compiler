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
 * Result of the neosyn/simulate request for bytecode simulation.
 */
public class SimulateResult {

    private boolean success;
    private String message;
    private String output;  // Console output from simulation
    private String vcdPath;  // Path to generated VCD file
    private int cyclesExecuted;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    // Set when the design cannot be fast-simulated at all (e.g. it instantiates a
    // DDR primitive with no bytecode model). Distinct from a normal failure: the
    // client should steer the user to Verilog simulation, not report a bug.
    private boolean fastSimUnsupported;
    private List<String> unsupportedComponents = new ArrayList<>();

    public SimulateResult() {
    }

    public SimulateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getVcdPath() {
        return vcdPath;
    }

    public void setVcdPath(String vcdPath) {
        this.vcdPath = vcdPath;
    }

    public int getCyclesExecuted() {
        return cyclesExecuted;
    }

    public void setCyclesExecuted(int cyclesExecuted) {
        this.cyclesExecuted = cyclesExecuted;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public boolean isFastSimUnsupported() {
        return fastSimUnsupported;
    }

    public void setFastSimUnsupported(boolean fastSimUnsupported) {
        this.fastSimUnsupported = fastSimUnsupported;
    }

    public List<String> getUnsupportedComponents() {
        return unsupportedComponents;
    }

    public void setUnsupportedComponents(List<String> unsupportedComponents) {
        this.unsupportedComponents = unsupportedComponents;
    }
}
