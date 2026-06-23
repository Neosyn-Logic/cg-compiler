/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.ide.server.ServerModule;
import org.eclipse.xtext.util.Modules2;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.neosyn.cg.CgRuntimeModule;
import com.neosyn.cg.CgStandaloneSetup;
import com.neosyn.cg.ide.CgIdeModule;

/**
 * Main entry point for the C⏚ Language Server.
 *
 * This server implements the Language Server Protocol (LSP) and provides:
 * - Code completion
 * - Syntax highlighting
 * - Error/warning diagnostics
 * - Go to definition
 * - Find references
 * - Hover information
 * - Document symbols
 * - Formatting
 * - Custom Neosyn methods for FSM/Graph visualization
 *
 * Usage: java -jar cg-language-server.jar
 *
 * The server communicates via stdin/stdout using JSON-RPC.
 */
public class CgLanguageServerLauncher {

    public static void main(String[] args) {
        System.err.println("[CgLanguageServer] Starting C⏚ Language Server...");
        try {
            launch(System.in, System.out);
            System.err.println("[CgLanguageServer] Server started, listening for requests...");
        } catch (Exception e) {
            System.err.println("[CgLanguageServer] ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Launch the language server with default modules.
     */
    public static Future<Void> launch(InputStream in, OutputStream out) {
        return launch(in, out, new Module[0]);
    }

    /**
     * Launch the language server with additional Guice modules.
     *
     * @param in Input stream (stdin)
     * @param out Output stream (stdout)
     * @param additionalModules Additional Guice modules to include (e.g., HDL generators)
     */
    public static Future<Void> launch(InputStream in, OutputStream out, Module... additionalModules) {
        System.err.println("[CgLanguageServer] Creating Guice modules...");

        // Create custom module that binds our extended language server
        AbstractModule customModule = new AbstractModule() {
            @Override
            protected void configure() {
                // Bind our custom language server implementation
                bind(LanguageServerImpl.class).to(CgLanguageServerImpl.class);
                bind(CgLanguageServer.class).to(CgLanguageServerImpl.class);
                // Index cross-project `cg.deps` roots so the editor's linker
                // resolves imports from sibling projects (overrides the stock
                // ServerModule binding via Modules2.mixin override order).
                bind(org.eclipse.xtext.ide.server.IMultiRootWorkspaceConfigFactory.class)
                        .to(CgWorkspaceConfigFactory.class);
                // Run the declarative CgValidator (@Checks: type/width, port-width,
                // generic-arg, …) as LIVE editor diagnostics, not just syntax
                // errors. The stock validator skips language-specific @Checks
                // because its validation context lacks CURRENT_LANGUAGE_NAME.
                bind(org.eclipse.xtext.validation.IResourceValidator.class)
                        .to(CgResourceValidator.class);
            }
        };

        System.err.println("[CgLanguageServer] Creating injector...");

        // Collect all modules
        List<Module> allModules = new ArrayList<>();
        allModules.add(new ServerModule());
        allModules.add(new CgRuntimeModule());
        allModules.add(new CgIdeModule());
        allModules.add(customModule);
        allModules.addAll(Arrays.asList(additionalModules));

        // Create a combined injector with ALL required modules
        Injector serverInjector = Guice.createInjector(
            Modules2.mixin(allModules.toArray(new Module[0]))
        );

        System.err.println("[CgLanguageServer] Registering language and EMF packages...");

        // Register the language and EMF packages
        new CgStandaloneSetup() {
            @Override
            public Injector createInjector() {
                return serverInjector;
            }
        }.createInjectorAndDoEMFRegistration();

        System.err.println("[CgLanguageServer] Getting language server instance...");

        // Get our custom language server implementation
        CgLanguageServerImpl languageServer = serverInjector.getInstance(CgLanguageServerImpl.class);

        // Create executor for async operations
        ExecutorService executorService = Executors.newCachedThreadPool();

        System.err.println("[CgLanguageServer] Building LSP launcher...");

        // Build and start the LSP launcher with custom interfaces
        Launcher<LanguageClient> launcher = new Launcher.Builder<LanguageClient>()
            .setLocalService(languageServer)
            .setRemoteInterface(LanguageClient.class)
            .setInput(in)
            .setOutput(out)
            .setExecutorService(executorService)
            .traceMessages(new PrintWriter(System.err))
            .create();

        System.err.println("[CgLanguageServer] Connecting to client...");

        // Connect to the client
        languageServer.connect(launcher.getRemoteProxy());

        System.err.println("[CgLanguageServer] Starting listener...");

        // Start listening
        return launcher.startListening();
    }
}
