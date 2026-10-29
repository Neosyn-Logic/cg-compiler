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
 * Parameters for the neosyn/generate request.
 */
public class GenerateParams {

    private String uri;
    private String target; // "verilog" or "vhdl"
    private String outputDirectory;
    // "generic" (default: today's HDL), "amd", "intel" or "lattice" -- vendor-specific generation rules
    private String targetVendor;
    // simple or qualified entity name: emit only it and what it uses (null = everything)
    private String top;

    public GenerateParams() {
    }

    public GenerateParams(String uri, String target) {
        this.uri = uri;
        this.target = target;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getTargetVendor() {
        return targetVendor;
    }

    public void setTargetVendor(String targetVendor) {
        this.targetVendor = targetVendor;
    }

    public String getTop() {
        return top;
    }

    public void setTop(String top) {
        this.top = top;
    }
}
