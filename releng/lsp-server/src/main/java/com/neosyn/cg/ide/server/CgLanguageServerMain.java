/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;


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
 * Main entry point for the C⏚ Language Server with HDL generation support.
 *
 * <h2>Usage:</h2>
 *
 * <h3>LSP Mode (default):</h3>
 * <pre>
 * java -jar cg-language-server.jar
 * </pre>
 * Starts the language server and listens for LSP requests on stdin/stdout.
 *
 * <h3>CLI Mode - Generate HDL:</h3>
 * <pre>
 * java -jar cg-language-server.jar generate &lt;path&gt; [--target verilog|vhdl]
 * </pre>
 * Generates HDL from a C⏚ file or project directory.
 *
 * <h3>CLI Mode - Generate IR:</h3>
 * <pre>
 * java -jar cg-language-server.jar generate-ir &lt;path&gt;
 * </pre>
 * Generates intermediate representation (IR) files.
 *
 * <h3>CLI Mode - Simulate:</h3>
 * <pre>
 * java -jar cg-language-server.jar simulate &lt;file.cg&gt;
 * </pre>
 * Runs bytecode simulation on a C⏚ test file.
 *
 * <h3>CLI Mode - Help:</h3>
 * <pre>
 * java -jar cg-language-server.jar --help
 * </pre>
 */
public class CgLanguageServerMain {

    private static final String VERSION = "2.6.9";

    public static void main(String[] args) {
        // The open-source Verilog compiler runs with no license gate.
        if (args.length == 0) {
            // No arguments - start in LSP mode
            runLspMode();
        } else {
            // CLI mode - parse arguments
            runCliMode(args);
        }
    }

    private static void runLspMode() {
        System.err.println("[CgLanguageServer] Starting C⏚ Language Server with HDL support...");
        try {
            // CRITICAL: Capture the real stdout BEFORE redirecting it.
            // The LSP protocol uses stdout for JSON-RPC messages.
            // Any System.out.println() from the codebase would corrupt the protocol stream,
            // so we redirect System.out to a log file after capturing the real stream.
            OutputStream realStdout = System.out;

            // Redirect System.out to a log file so stray prints don't corrupt LSP
            String logPath = System.getProperty("user.home") + File.separator + "neosyn-server-stdout.log";
            PrintStream logStream = new PrintStream(new FileOutputStream(logPath, true), true);
            System.setOut(logStream);

            // Launch with the standalone generator module for HDL generation
            CgLanguageServerLauncher.launch(
                System.in,
                realStdout,
                new StandaloneGeneratorModule()
            );
            System.err.println("[CgLanguageServer] Server started, listening for requests...");
        } catch (Exception e) {
            System.err.println("[CgLanguageServer] ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCliMode(String[] args) {
        String command = args[0].toLowerCase();

        switch (command) {
            case "--stdio":
            case "stdio":
                // VS Code passes --stdio to start in LSP mode
                runLspMode();
                return;

            case "--help":
            case "-h":
            case "help":
                printHelp();
                break;

            case "--version":
            case "-v":
            case "version":
                System.out.println("Neosyn C⏚ Language Server v" + VERSION);
                break;

            case "generate":
            case "gen":
                runGenerate(args);
                break;

            case "generate-ir":
            case "ir":
                runGenerateIR(args);
                break;

            case "fsm":
                runFsm(args);
                break;

            case "graph":
            case "network":
                runGraph(args);
                break;

            default:
                System.err.println("Unknown command: " + command);
                System.err.println("Use --help for usage information.");
                System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Neosyn C⏚ Language Server v" + VERSION);
        System.out.println();
        System.out.println("Usage: java -jar cg-language-server.jar [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  (none)              Start in LSP mode (stdin/stdout)");
        System.out.println("  generate <path>     Generate Verilog from C⏚ source");
        System.out.println("  generate-ir <path>  Generate IR files from C⏚ source");
        System.out.println("  fsm <file>          Print a task's compiled FSM (states + transitions)");
        System.out.println("  graph <file>        Print a network's compiled graph (instances + connections)");
        System.out.println("                      [--task <name>] to pick a task; mirrors the generated HDL");
        System.out.println("  --help              Show this help message");
        System.out.println("  --version           Show version number");
        System.out.println();
        System.out.println("Options for 'generate':");
        System.out.println("  --output <dir>      Output directory (default: same as project)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar cg-language-server.jar generate /path/to/project");
        System.out.println("  java -jar cg-language-server.jar generate /path/to/file.cg");
        System.out.println();
        System.out.println("Documentation: https://neosyn.io/docs");
    }

    private static CgLanguageServerImpl createServer() {
        System.err.println("[CLI] Initializing Neosyn compiler...");

        // Create custom module that binds our extended language server
        AbstractModule customModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(CgLanguageServer.class).to(CgLanguageServerImpl.class);
            }
        };

        // Collect all modules
        List<Module> allModules = new ArrayList<>();
        allModules.add(new ServerModule());
        allModules.add(new CgRuntimeModule());
        allModules.add(new CgIdeModule());
        allModules.add(new StandaloneGeneratorModule());
        allModules.add(customModule);

        // Create a combined injector with ALL required modules
        Injector serverInjector = Guice.createInjector(
            Modules2.mixin(allModules.toArray(new Module[0]))
        );

        // Register the language and EMF packages
        new CgStandaloneSetup() {
            @Override
            public Injector createInjector() {
                return serverInjector;
            }
        }.createInjectorAndDoEMFRegistration();

        return serverInjector.getInstance(CgLanguageServerImpl.class);
    }

    private static void runGenerate(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing path argument");
            System.err.println("Usage: java -jar cg-language-server.jar generate <path> [--target verilog|vhdl]");
            System.exit(1);
        }

        String path = args[1];
        String target = "verilog";
        String outputDir = null;

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            if ("--target".equals(args[i]) && i + 1 < args.length) {
                target = args[++i];
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputDir = args[++i];
            }
        }

        // Validate path
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Error: Path does not exist: " + path);
            System.exit(1);
        }

        // Convert to URI
        String uri = file.toURI().toString();

        System.out.println("Generating " + target.toUpperCase() + " from: " + path);

        try {
            CgLanguageServerImpl server = createServer();

            GenerateParams params = new GenerateParams();
            params.setUri(uri);
            params.setTarget(target);
            if (outputDir != null) {
                params.setOutputDirectory(outputDir);
            }

            GenerateResult result = server.generate(params).get(5, TimeUnit.MINUTES);

            if (result.isSuccess()) {
                int failed = result.getErrors() == null ? 0 : result.getErrors().size();
                if (failed > 0) {
                    System.out.println("Partial success: generated " + result.getGeneratedFiles().size()
                            + " files; " + failed + " entity/entities skipped:");
                    for (String err : result.getErrors()) {
                        System.out.println("  [skipped] " + err);
                    }
                } else {
                    System.out.println("Success! Generated " + result.getGeneratedFiles().size() + " files.");
                }
                for (String f : result.getGeneratedFiles()) {
                    System.out.println("  " + f);
                }
            } else {
                System.err.println("Error: " + result.getMessage());
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runFsm(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing file argument");
            System.err.println("Usage: java -jar cg-language-server.jar fsm <file.cg> [--task <name>]");
            System.exit(1);
        }

        String path = args[1];
        String taskName = null;
        for (int i = 2; i < args.length; i++) {
            if ("--task".equals(args[i]) && i + 1 < args.length) {
                taskName = args[++i];
            }
        }

        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Error: Path does not exist: " + path);
            System.exit(1);
        }

        try {
            CgLanguageServerImpl server = createServer();

            GetFsmParams params = new GetFsmParams();
            params.setUri(file.toURI().toString());
            params.setTaskName(taskName);

            FsmData fsm = server.getFsm(params).get(5, TimeUnit.MINUTES);

            if (fsm.getError() != null) {
                System.err.println("Error: " + fsm.getError());
                System.exit(1);
            }

            System.out.println("Task: " + (fsm.getTaskName() != null ? fsm.getTaskName() : "(default)"));
            System.out.println("Initial state: " + fsm.getInitialState());
            System.out.println("States (" + fsm.getStates().size() + "):");
            for (FsmData.StateData s : fsm.getStates()) {
                String span = s.getLines().isEmpty() ? ""
                        : "  lines " + (s.getLine() + 1) + "-" + (s.getEndLine() + 1)
                          + " (" + s.getLines().size() + " stmt line(s))";
                System.out.println("  " + (s.isInitial() ? "* " : "  ") + s.getName() + span);
            }
            System.out.println("Transitions (" + fsm.getTransitions().size() + "):");
            for (FsmData.TransitionData t : fsm.getTransitions()) {
                System.out.println("  " + t.getSource() + " -> " + t.getTarget());
            }
            if (fsm.getStates().isEmpty()) {
                System.out.println("(No FSM — combinational / single-cycle actor.)");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runGraph(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing file argument");
            System.err.println("Usage: java -jar cg-language-server.jar graph <file.cg> [--network <name>]");
            System.exit(1);
        }

        String path = args[1];
        String networkName = null;
        for (int i = 2; i < args.length; i++) {
            if ("--network".equals(args[i]) && i + 1 < args.length) {
                networkName = args[++i];
            }
        }

        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Error: Path does not exist: " + path);
            System.exit(1);
        }

        try {
            CgLanguageServerImpl server = createServer();

            GetGraphParams params = new GetGraphParams();
            params.setUri(file.toURI().toString());
            params.setNetworkName(networkName);

            GraphData graph = server.getGraph(params).get(5, TimeUnit.MINUTES);

            if (System.getenv("CG_GRAPH_JSON") != null) {
                System.out.println("JSON: " + new com.google.gson.GsonBuilder().create().toJson(graph));
            }

            if (graph.getError() != null) {
                System.err.println("Error: " + graph.getError());
                System.exit(1);
            }

            System.out.println("Network: " + (graph.getNetworkName() != null ? graph.getNetworkName() : "(default)"));
            System.out.println("Inputs (" + graph.getInputPorts().size() + "): " + portList(graph.getInputPorts()));
            System.out.println("Outputs (" + graph.getOutputPorts().size() + "): " + portList(graph.getOutputPorts()));
            System.out.println("Instances (" + graph.getInstances().size() + "):");
            for (GraphData.InstanceData inst : graph.getInstances()) {
                System.out.println("  " + inst.getName() + " : " + inst.getEntityName()
                        + (inst.isNetwork() ? " [network]" : "")
                        + "  in=" + portList(inst.getInputs())
                        + " out=" + portList(inst.getOutputs()));
            }
            System.out.println("Connections (" + graph.getConnections().size() + "):");
            for (GraphData.ConnectionData c : graph.getConnections()) {
                String src = (c.getSourceInstance() != null ? c.getSourceInstance() : "this") + "." + c.getSourcePort();
                String tgt = (c.getTargetInstance() != null ? c.getTargetInstance() : "this") + "." + c.getTargetPort();
                String w = c.getWidth() >= 0 ? "[" + c.getWidth() + "]" : "";
                System.out.println("  " + src + " -> " + tgt + " (" + c.getInterfaceType() + w + ")");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static String portList(java.util.List<GraphData.PortData> ports) {
        StringBuilder sb = new StringBuilder();
        for (GraphData.PortData p : ports) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.getName());
            if (p.getWidth() >= 0) sb.append(":").append(p.getWidth());
        }
        return "[" + sb + "]";
    }

    private static void runGenerateIR(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing path argument");
            System.err.println("Usage: java -jar cg-language-server.jar generate-ir <path>");
            System.exit(1);
        }

        String path = args[1];

        // Validate path
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("Error: Path does not exist: " + path);
            System.exit(1);
        }

        // Convert to URI
        String uri = file.toURI().toString();

        System.out.println("Generating IR from: " + path);

        try {
            CgLanguageServerImpl server = createServer();

            GenerateIRParams params = new GenerateIRParams();
            if (file.isDirectory()) {
                params.setProjectUri(uri);
            } else {
                params.setUri(uri);
            }

            GenerateResult result = server.generateIR(params).get(5, TimeUnit.MINUTES);

            if (result.isSuccess()) {
                System.out.println("Success! Generated " + result.getGeneratedFiles().size() + " IR files.");
                for (String f : result.getGeneratedFiles()) {
                    System.out.println("  " + f);
                }
            } else {
                System.err.println("Error: " + result.getMessage());
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

}
