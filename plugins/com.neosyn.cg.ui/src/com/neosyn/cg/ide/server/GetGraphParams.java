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
 * Parameters for the neosyn/getGraph request.
 */
public class GetGraphParams {

    private String uri;
    private String networkName;

    public GetGraphParams() {
    }

    public GetGraphParams(String uri) {
        this.uri = uri;
    }

    public GetGraphParams(String uri, String networkName) {
        this.uri = uri;
        this.networkName = networkName;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }
}
