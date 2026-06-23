/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Extended Language Server interface with Neosyn-specific methods.
 *
 * These methods provide FSM and Graph data for visualization in VS Code.
 */
@JsonSegment("neosyn")
public interface CgLanguageServer extends LanguageServer {

    /**
     * Get FSM (Finite State Machine) data for a given file and task.
     *
     * @param params Contains the file URI and optional task name
     * @return FSM data including states and transitions
     */
    @JsonRequest
    CompletableFuture<FsmData> getFsm(GetFsmParams params);

    /**
     * Get Graph (DPN - Dataflow Process Network) data for a given file.
     *
     * @param params Contains the file URI and optional network name
     * @return Graph data including actors and connections
     */
    @JsonRequest
    CompletableFuture<GraphData> getGraph(GetGraphParams params);

    /**
     * Generate HDL (Verilog/VHDL) for a given file.
     *
     * @param params Contains the file URI and target format
     * @return Generation result
     */
    @JsonRequest
    CompletableFuture<GenerateResult> generate(GenerateParams params);

    /**
     * Generate IR (Intermediate Representation) for a given file or project.
     * This is called on file save and project open to keep IR up to date.
     *
     * @param params Contains the file URI(s) and output directory
     * @return Generation result with list of generated IR files
     */
    @JsonRequest
    CompletableFuture<GenerateResult> generateIR(GenerateIRParams params);

    /**
     * Simulate a C⏚ design using bytecode simulation.
     * Compiles the design to Java bytecode and runs it, producing VCD waveforms.
     *
     * @param params Contains the file URI and simulation options
     * @return Simulation result with output and VCD path
     */
    @JsonRequest
    CompletableFuture<SimulateResult> simulate(SimulateParams params);
}
