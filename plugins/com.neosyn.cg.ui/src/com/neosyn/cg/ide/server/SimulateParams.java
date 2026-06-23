/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

/**
 * Parameters for the neosyn/simulate request.
 */
public class SimulateParams {

    private String uri;
    private String entityName;  // Optional: specific entity to simulate
    private int maxCycles = 10000;  // Maximum simulation cycles
    private boolean generateVcd = true;  // Generate VCD waveform

    public SimulateParams() {
    }

    public SimulateParams(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public int getMaxCycles() {
        return maxCycles;
    }

    public void setMaxCycles(int maxCycles) {
        this.maxCycles = maxCycles;
    }

    public boolean isGenerateVcd() {
        return generateVcd;
    }

    public void setGenerateVcd(boolean generateVcd) {
        this.generateVcd = generateVcd;
    }
}
