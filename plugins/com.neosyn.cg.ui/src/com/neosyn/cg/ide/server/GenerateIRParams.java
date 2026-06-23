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
 * Parameters for the neosyn/generateIR request.
 *
 * Can specify either:
 * - A single file URI (for file save)
 * - A list of file URIs (for batch processing)
 * - A project URI (for project open - generates all .cg files)
 */
public class GenerateIRParams {

    private String uri;              // Single file URI
    private List<String> uris;       // Multiple file URIs
    private String projectUri;       // Project root URI (generates all .cg files)
    private String outputDirectory;  // Output directory for IR files

    public GenerateIRParams() {
        this.uris = new ArrayList<>();
    }

    public GenerateIRParams(String uri) {
        this();
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<String> getUris() {
        return uris;
    }

    public void setUris(List<String> uris) {
        this.uris = uris;
    }

    public String getProjectUri() {
        return projectUri;
    }

    public void setProjectUri(String projectUri) {
        this.projectUri = projectUri;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
}
