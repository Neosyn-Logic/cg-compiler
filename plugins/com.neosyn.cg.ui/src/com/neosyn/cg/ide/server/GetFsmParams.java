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
 * Parameters for the neosyn/getFsm request.
 */
public class GetFsmParams {

    private String uri;
    private String taskName;

    public GetFsmParams() {
    }

    public GetFsmParams(String uri) {
        this.uri = uri;
    }

    public GetFsmParams(String uri, String taskName) {
        this.uri = uri;
        this.taskName = taskName;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
}
