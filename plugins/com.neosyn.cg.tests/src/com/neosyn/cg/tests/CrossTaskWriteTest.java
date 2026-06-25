/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static com.neosyn.cg.CgConstants.NAME_LOOP;
import static com.neosyn.cg.CgConstants.NAME_SETUP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.neosyn.cg.CgUtil;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.StatementWrite;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.generator.CombinationalVisitor;
import com.neosyn.cg.generator.FsmBeautifier;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.compiler.ActorTransformer;
import com.neosyn.cg.internal.scheduler.CycleScheduler;
import com.neosyn.cg.internal.scheduler.IfScheduler;
import com.neosyn.models.dpn.Action;
import com.neosyn.models.dpn.Actor;
import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.Direction;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Var;

/**
 * Tests for cross-inline-task port write functionality.
 *
 * Verifies that when one inline task writes to another inline task's input port,
 * the ImplicitConnector correctly creates:
 * 1. An OUTPUT port on the writer task
 * 2. A Connection from writer's output to target's input
 * 3. Correct port mappings for ActorTransformer to use
 *
 * This test is designed to debug the cross-task write timeout issue
 * where Verilog simulation works but bytecode simulation times out.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class CrossTaskWriteTest {

    @Inject
    private ParseHelper<Module> parseHelper;

    @Inject
    private ValidationTestHelper validationHelper;

    @Inject
    private IInstantiator instantiator;

    /**
     * Gets the IR Entity for a CgEntity using forEachMapping.
     */
    private Entity getIrEntity(CgEntity cxEntity) {
        Entity[] holder = { null };
        instantiator.forEachMapping(cxEntity, entity -> holder[0] = entity);
        return holder[0];
    }

    /**
     * Test: Cross-task READ works (baseline - this already works in bytecode)
     * consumer reads from producer.data
     */
    @Test
    public void testCrossTaskRead() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network ReadTest {\n" +
            "    properties { test: true }\n" +
            "    producer = new task {\n" +
            "        out u8 data;\n" +
            "        void loop() { data.write(42); }\n" +
            "    };\n" +
            "    consumer = new task {\n" +
            "        void loop() {\n" +
            "            u8 val = producer.data.read();\n" +
            "            print(\"Got: \", val);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Parse errors: " + module.eResource().getErrors(),
            module.eResource().getErrors().isEmpty());

        CgEntity cxEntity = module.getEntities().get(0);
        assertTrue("Should be a Network", cxEntity instanceof Network);

        instantiator.update(module);

        Entity entity = getIrEntity(cxEntity);
        assertNotNull("DPN entity should exist", entity);
        assertTrue("Should be a DPN", entity instanceof DPN);

        DPN dpn = (DPN) entity;
        System.out.println("=== Cross-Task READ Test ===");
        printDpnDetails(dpn);

        // Find consumer entity
        Instance consumerInst = findInstance(dpn, "consumer");
        assertNotNull("consumer instance should exist", consumerInst);
        Entity consumerEntity = consumerInst.getEntity();
        assertNotNull("consumer entity should exist", consumerEntity);

        // Consumer should have an INPUT port named "producer_data" (created by ImplicitConnector)
        Port producerDataPort = findPort(consumerEntity.getInputs(), "producer_data");
        assertNotNull("consumer should have INPUT port 'producer_data' from ImplicitConnector",
            producerDataPort);
        assertEquals("producer_data should be INPUT", Direction.INPUT, producerDataPort.getDirection());

        System.out.println("PASS: Cross-task READ creates correct ports and connections");
    }

    /**
     * Test: Cross-task WRITE - this is the problematic case
     * tester writes to processor.input_val
     */
    @Test
    public void testCrossTaskWrite() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network WriteTest {\n" +
            "    properties { test: true }\n" +
            "    processor = new task {\n" +
            "        in u8 input_val;\n" +
            "        out u8 output_val;\n" +
            "        void loop() {\n" +
            "            u8 val = input_val.read();\n" +
            "            output_val.write(val * 2);\n" +
            "        }\n" +
            "    };\n" +
            "    tester = new task {\n" +
            "        void loop() {\n" +
            "            processor.input_val.write(5);\n" +
            "            u8 result = processor.output_val.read();\n" +
            "            print(\"Result: \", result);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Parse errors: " + module.eResource().getErrors(),
            module.eResource().getErrors().isEmpty());

        CgEntity cxEntity = module.getEntities().get(0);
        assertTrue("Should be a Network", cxEntity instanceof Network);

        instantiator.update(module);

        Entity entity = getIrEntity(cxEntity);
        assertNotNull("DPN entity should exist", entity);
        assertTrue("Should be a DPN", entity instanceof DPN);

        DPN dpn = (DPN) entity;
        System.out.println("\n=== Cross-Task WRITE Test ===");
        printDpnDetails(dpn);

        // Find tester entity
        Instance testerInst = findInstance(dpn, "tester");
        assertNotNull("tester instance should exist", testerInst);
        Entity testerEntity = testerInst.getEntity();
        assertNotNull("tester entity should exist", testerEntity);

        System.out.println("\n--- Tester Entity Ports ---");
        System.out.println("  Inputs:");
        for (Port p : testerEntity.getInputs()) {
            System.out.println("    " + p.getName() + " dir=" + p.getDirection() + " type=" + p.getType());
        }
        System.out.println("  Outputs:");
        for (Port p : testerEntity.getOutputs()) {
            System.out.println("    " + p.getName() + " dir=" + p.getDirection() + " type=" + p.getType());
        }

        // Tester should have an OUTPUT port named "processor_input_val" (created by ImplicitConnector for the write)
        Port writePort = findPort(testerEntity.getOutputs(), "processor_input_val");
        if (writePort == null) {
            System.err.println("BUG: tester does NOT have OUTPUT port 'processor_input_val'!");
            System.err.println("  This means ImplicitConnector.caseStatementWrite() did not create the port.");
            System.err.println("  Available outputs: ");
            for (Port p : testerEntity.getOutputs()) {
                System.err.println("    " + p.getName());
            }
            fail("tester should have OUTPUT port 'processor_input_val' from ImplicitConnector");
        }
        assertEquals("processor_input_val should be OUTPUT", Direction.OUTPUT, writePort.getDirection());

        // Tester should also have an INPUT port named "processor_output_val" (for the read)
        Port readPort = findPort(testerEntity.getInputs(), "processor_output_val");
        assertNotNull("tester should have INPUT port 'processor_output_val'", readPort);
        assertEquals("processor_output_val should be INPUT", Direction.INPUT, readPort.getDirection());

        // Check connections: tester.processor_input_val → processor.input_val
        Instance processorInst = findInstance(dpn, "processor");
        assertNotNull("processor instance should exist", processorInst);

        boolean foundWriteConnection = false;
        List<Connection> processorIncoming = dpn.getIncoming(processorInst);
        for (Connection conn : processorIncoming) {
            Endpoint src = conn.getSourceEndpoint();
            Endpoint tgt = conn.getTargetEndpoint();
            if (src != null && tgt != null) {
                String srcPort = conn.getSourcePort() != null ? conn.getSourcePort().getName() : "?";
                String tgtPort = conn.getTargetPort() != null ? conn.getTargetPort().getName() : "?";
                System.out.println("  Connection: " + describeEndpoint(src) + " → " + describeEndpoint(tgt));
                if ("processor_input_val".equals(srcPort) && "input_val".equals(tgtPort)) {
                    foundWriteConnection = true;
                }
            }
        }
        assertTrue("Should have connection tester.processor_input_val → processor.input_val",
            foundWriteConnection);

        System.out.println("PASS: Cross-task WRITE creates correct ports and connections");
    }

    /**
     * Test: Verify that instantiator.update() alone does NOT populate actions.
     * This documents that the transformation pipeline (CycleScheduler + ActorTransformer)
     * must be run separately. Without it, actors have 0 actions.
     */
    @Test
    public void testCrossTaskWriteIRTransformation() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network TransformTest {\n" +
            "    properties { test: true }\n" +
            "    processor = new task {\n" +
            "        in u8 input_val;\n" +
            "        out u8 output_val;\n" +
            "        void loop() {\n" +
            "            u8 val = input_val.read();\n" +
            "            output_val.write(val * 2);\n" +
            "        }\n" +
            "    };\n" +
            "    tester = new task {\n" +
            "        void loop() {\n" +
            "            processor.input_val.write(5);\n" +
            "            u8 result = processor.output_val.read();\n" +
            "            print(\"Result: \", result);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Parse errors: " + module.eResource().getErrors(),
            module.eResource().getErrors().isEmpty());

        CgEntity cxEntity = module.getEntities().get(0);
        instantiator.update(module);

        Entity entity = getIrEntity(cxEntity);
        assertNotNull("DPN entity should exist", entity);
        DPN dpn = (DPN) entity;

        Instance testerInst = findInstance(dpn, "tester");
        assertNotNull("tester instance should exist", testerInst);
        Entity testerEntity = testerInst.getEntity();
        assertNotNull("tester entity should exist", testerEntity);

        System.out.println("\n=== Cross-Task WRITE IR Pre-Transformation Test ===");

        // IMPORTANT: instantiator.update() does NOT run the scheduler/transformer.
        // The actor should have 0 actions at this point. This test documents that fact.
        if (testerEntity instanceof Actor) {
            Actor actor = (Actor) testerEntity;
            System.out.println("Tester actor actions BEFORE transformation: " + actor.getActions().size());
            // Actions are 0 because CycleScheduler + ActorTransformer haven't run yet
            assertEquals("Before transformation, actor should have 0 actions", 0, actor.getActions().size());
        }

        System.out.println("PASS: Confirmed that update() alone does not populate actions");
    }

    /**
     * Test: Run the FULL transformation pipeline on the tester inline task and verify:
     * 1. Actor gets > 0 actions after transformation
     * 2. Action body has InstStore targeting tester's OUTPUT port processor_input_val
     * 3. Action scheduler procedure has a non-empty name (required for bytecode)
     * 4. No InstStore targets processor's INPUT port input_val (would be a bug)
     *
     * This is the critical diagnostic test for the cross-task write timeout issue.
     */
    @Test
    public void testCrossTaskWriteFullTransformation() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network FullTransformTest {\n" +
            "    properties { test: true }\n" +
            "    processor = new task {\n" +
            "        in u8 input_val;\n" +
            "        out u8 output_val;\n" +
            "        void loop() {\n" +
            "            u8 val = input_val.read();\n" +
            "            output_val.write(val * 2);\n" +
            "        }\n" +
            "    };\n" +
            "    tester = new task {\n" +
            "        void loop() {\n" +
            "            processor.input_val.write(5);\n" +
            "            u8 result = processor.output_val.read();\n" +
            "            print(\"Result: \", result);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Parse errors: " + module.eResource().getErrors(),
            module.eResource().getErrors().isEmpty());

        CgEntity cxEntity = module.getEntities().get(0);
        assertTrue("Should be a Network", cxEntity instanceof Network);
        Network network = (Network) cxEntity;

        instantiator.update(module);

        Entity entity = getIrEntity(cxEntity);
        assertNotNull("DPN entity should exist", entity);
        DPN dpn = (DPN) entity;

        System.out.println("\n=== Cross-Task WRITE Full Transformation Test ===");

        // Transform ALL inline tasks (same as CgGenerator.transformNetwork does)
        for (Inst inst : network.getInstances()) {
            Task task = inst.getTask();
            if (task != null) {
                Instance instance = instantiator.getMapping(dpn, inst);
                assertNotNull("Instance mapping for " + inst.getName(), instance);
                Actor actor = (Actor) instance.getEntity();
                assertNotNull("Actor for " + inst.getName(), actor);

                // Find setup and loop functions
                Variable setup = null;
                Variable loop = null;
                for (Variable function : CgUtil.getFunctions(task)) {
                    String name = function.getName();
                    if (NAME_SETUP.equals(name)) {
                        setup = function;
                    } else if (NAME_LOOP.equals(name)) {
                        loop = function;
                    }
                }

                System.out.println("Transforming: " + inst.getName()
                    + " (setup=" + (setup != null) + ", loop=" + (loop != null) + ")");

                // Run the synchronous transformation pipeline
                // (same steps as CgGenerator.transformSynchronousTask)
                new CycleScheduler(instantiator, actor).schedule(setup, loop);
                new IfScheduler(instantiator, actor).visit();
                new ActorTransformer(instantiator, actor).visit();
                new FsmBeautifier().visit(actor);
                new CombinationalVisitor().visit(actor);

                System.out.println("  Actions after transformation: " + actor.getActions().size());
                for (Action action : actor.getActions()) {
                    Procedure sched = action.getScheduler();
                    Procedure body = action.getBody();
                    System.out.println("  Action: " + action.getName());
                    System.out.println("    Scheduler: " + (sched != null ? sched.getName() : "null"));
                    System.out.println("    Body: " + (body != null ? body.getName() : "null"));
                }
            }
        }

        // Now verify the tester's transformation result
        Instance testerInst = findInstance(dpn, "tester");
        assertNotNull("tester instance should exist", testerInst);
        Actor testerActor = (Actor) testerInst.getEntity();

        // 1. Verify actions were created
        assertTrue("Tester should have > 0 actions after transformation, but has "
            + testerActor.getActions().size(),
            testerActor.getActions().size() > 0);

        // 2. Verify action procedures have non-empty names (required for bytecode)
        for (Action action : testerActor.getActions()) {
            Procedure sched = action.getScheduler();
            Procedure body = action.getBody();
            assertNotNull("Action scheduler should not be null", sched);
            assertNotNull("Action body should not be null", body);
            assertNotNull("Scheduler name should not be null", sched.getName());
            assertTrue("Scheduler name should not be empty: " + sched.getName(),
                !sched.getName().isEmpty());
            assertNotNull("Body name should not be null", body.getName());
            assertTrue("Body name should not be empty: " + body.getName(),
                !body.getName().isEmpty());
        }

        // 3. Check InstStore targets in tester's body procedures
        boolean foundCrossTaskWrite = false;
        for (Action action : testerActor.getActions()) {
            Procedure body = action.getBody();
            if (body == null) continue;
            for (Block block : body.getBlocks()) {
                if (!(block instanceof BlockBasic)) continue;
                BlockBasic basicBlock = (BlockBasic) block;
                for (Instruction instr : basicBlock.getInstructions()) {
                    if (!(instr instanceof InstStore)) continue;
                    InstStore store = (InstStore) instr;
                    Var target = store.getTarget().getVariable();
                    if (target == null) continue;
                    String targetName = target.getName();
                    boolean isPort = target instanceof Port;
                    String containerName = "?";
                    if (target.eContainer() instanceof Entity) {
                        containerName = ((Entity) target.eContainer()).getName();
                    }
                    System.out.println("    InstStore: target=" + targetName
                        + " isPort=" + isPort
                        + " container=" + containerName
                        + (isPort ? " dir=" + ((Port) target).getDirection() : ""));

                    // Check for the cross-task write target
                    if (isPort && "processor_input_val".equals(targetName)) {
                        Port storePort = (Port) target;
                        assertEquals("Cross-task write port should be OUTPUT",
                            Direction.OUTPUT, storePort.getDirection());
                        assertTrue("Cross-task write port should belong to tester entity",
                            storePort.eContainer() == testerActor);
                        foundCrossTaskWrite = true;
                    }

                    // BUG CHECK: Should NOT target processor's INPUT port directly
                    if (isPort && "input_val".equals(targetName)) {
                        Port storePort = (Port) target;
                        if (storePort.getDirection() == Direction.INPUT) {
                            fail("BUG: InstStore targets processor's INPUT port 'input_val'!\n"
                                + "  Should target tester's OUTPUT port 'processor_input_val'\n"
                                + "  This would cause bytecode to access wrong entity's field");
                        }
                    }
                }
            }
        }

        assertTrue("Should find InstStore targeting 'processor_input_val' OUTPUT port on tester",
            foundCrossTaskWrite);

        // Also verify the processor's transformation
        Instance processorInst = findInstance(dpn, "processor");
        Actor processorActor = (Actor) processorInst.getEntity();
        assertTrue("Processor should have > 0 actions after transformation",
            processorActor.getActions().size() > 0);

        System.out.println("\nPASS: Full transformation produces correct IR for cross-task writes");
    }

    /**
     * Test: Directly call instantiator.getPort() to verify which port is returned
     * for the cross-task write VarRef. This is the critical test that identifies
     * whether getPort() returns the correct port (tester's OUTPUT processor_input_val)
     * or the wrong port (processor's INPUT input_val).
     */
    @Test
    public void testGetPortResolution() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network PortResTest {\n" +
            "    properties { test: true }\n" +
            "    processor = new task {\n" +
            "        in u8 input_val;\n" +
            "        out u8 output_val;\n" +
            "        void loop() {\n" +
            "            u8 val = input_val.read();\n" +
            "            output_val.write(val * 2);\n" +
            "        }\n" +
            "    };\n" +
            "    tester = new task {\n" +
            "        void loop() {\n" +
            "            processor.input_val.write(5);\n" +
            "            u8 result = processor.output_val.read();\n" +
            "            print(\"Result: \", result);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Parse errors: " + module.eResource().getErrors(),
            module.eResource().getErrors().isEmpty());

        CgEntity cxEntity = module.getEntities().get(0);
        assertTrue("Should be a Network", cxEntity instanceof Network);
        Network network = (Network) cxEntity;

        instantiator.update(module);

        Entity dpnEntity = getIrEntity(cxEntity);
        assertNotNull("DPN entity should exist", dpnEntity);
        DPN dpn = (DPN) dpnEntity;

        // Get the tester IR Entity
        Instance testerInst = findInstance(dpn, "tester");
        assertNotNull("tester instance should exist", testerInst);
        Entity testerEntity = testerInst.getEntity();
        assertNotNull("tester entity should exist", testerEntity);

        // Find the tester Inst in the AST
        Inst testerCxInst = null;
        for (Inst inst : network.getInstances()) {
            if ("tester".equals(inst.getName())) {
                testerCxInst = inst;
                break;
            }
        }
        assertNotNull("tester Inst should exist in AST", testerCxInst);
        assertNotNull("tester should have inline task", testerCxInst.getTask());

        // Find the StatementWrite in the tester's inline task AST
        StatementWrite crossTaskWrite = null;
        Iterator<EObject> it = testerCxInst.getTask().eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof StatementWrite) {
                crossTaskWrite = (StatementWrite) obj;
                break; // First write is processor.input_val.write(5)
            }
        }
        assertNotNull("Should find StatementWrite in tester task", crossTaskWrite);

        System.out.println("\n=== getPort() Resolution Test ===");
        System.out.println("StatementWrite VarRef objects:");
        for (int i = 0; i < crossTaskWrite.getPort().getObjects().size(); i++) {
            com.neosyn.cg.cg.Named obj = crossTaskWrite.getPort().getObjects().get(i);
            System.out.println("  [" + i + "] " + obj.getName() + " (" + obj.getClass().getSimpleName() + ")");
        }

        // Call getPort() - this is the method ActorTransformer uses
        Port resolvedPort = instantiator.getPort(testerEntity, crossTaskWrite.getPort());

        System.out.println("\nResolved port:");
        if (resolvedPort != null) {
            String containerName = "?";
            if (resolvedPort.eContainer() instanceof Entity) {
                containerName = ((Entity) resolvedPort.eContainer()).getName();
            }
            System.out.println("  name=" + resolvedPort.getName());
            System.out.println("  direction=" + resolvedPort.getDirection());
            System.out.println("  container=" + containerName);
            System.out.println("  identity=" + System.identityHashCode(resolvedPort));

            // THE CRITICAL CHECK: getPort() should return tester's OUTPUT port processor_input_val
            // NOT processor's INPUT port input_val
            if ("input_val".equals(resolvedPort.getName()) && resolvedPort.getDirection() == Direction.INPUT) {
                System.err.println("BUG CONFIRMED: getPort() returns processor's INPUT port 'input_val'!");
                System.err.println("  Expected: tester's OUTPUT port 'processor_input_val'");
                System.err.println("  This causes bytecode to generate: GETFIELD tester.input_val (doesn't exist!)");
                System.err.println("  Or GETFIELD processor.input_val (wrong entity!)");
                System.err.println("  Result: simulation timeout because data never reaches processor");

                // Also print what the correct port looks like
                Port correctPort = findPort(testerEntity.getOutputs(), "processor_input_val");
                if (correctPort != null) {
                    System.err.println("  Correct port exists on tester: processor_input_val (OUTPUT)");
                    System.err.println("  identity=" + System.identityHashCode(correctPort));
                }

                fail("getPort() returned wrong port: processor's 'input_val' instead of tester's 'processor_input_val'");
            }

            assertEquals("Resolved port should be named 'processor_input_val'",
                "processor_input_val", resolvedPort.getName());
            assertEquals("Resolved port should be OUTPUT (on tester)",
                Direction.OUTPUT, resolvedPort.getDirection());

            // Verify the port belongs to the tester entity
            assertTrue("Resolved port should be contained in tester entity",
                resolvedPort.eContainer() == testerEntity);

        } else {
            System.err.println("BUG: getPort() returned null!");
            System.err.println("  This means no mapping was found for the cross-task write VarRef");
            System.err.println("  ImplicitConnector should have stored a mapping via putMapping()");
            fail("getPort() returned null for cross-task write VarRef");
        }

        System.out.println("PASS: getPort() resolves to correct tester OUTPUT port");
    }

    // Helper methods

    private Instance findInstance(DPN dpn, String name) {
        for (Instance inst : dpn.getInstances()) {
            if (name.equals(inst.getName())) {
                return inst;
            }
        }
        return null;
    }

    private Port findPort(EList<Port> ports, String name) {
        for (Port p : ports) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    private void printDpnDetails(DPN dpn) {
        System.out.println("DPN: " + dpn.getName());
        System.out.println("Instances (" + dpn.getInstances().size() + "):");
        for (Instance inst : dpn.getInstances()) {
            Entity instEntity = inst.getEntity();
            System.out.println("  " + inst.getName() + " -> entity="
                + (instEntity != null ? instEntity.getName() : "null"));
            if (instEntity != null) {
                System.out.println("    inputs: " + instEntity.getInputs().size());
                for (Port p : instEntity.getInputs()) {
                    System.out.println("      " + p.getName() + " (" + p.getDirection() + ")");
                }
                System.out.println("    outputs: " + instEntity.getOutputs().size());
                for (Port p : instEntity.getOutputs()) {
                    System.out.println("      " + p.getName() + " (" + p.getDirection() + ")");
                }
            }

            // Print connections for this instance
            List<Connection> incoming = dpn.getIncoming(inst);
            for (Connection conn : incoming) {
                System.out.println("    <- " + describeEndpoint(conn.getSourceEndpoint())
                    + " -> " + describeEndpoint(conn.getTargetEndpoint()));
            }
            List<Connection> outgoing = dpn.getOutgoing(inst);
            for (Connection conn : outgoing) {
                System.out.println("    -> " + describeEndpoint(conn.getSourceEndpoint())
                    + " -> " + describeEndpoint(conn.getTargetEndpoint()));
            }
        }
    }

    private String describeEndpoint(Endpoint ep) {
        if (ep == null) return "null";
        String inst = ep.hasInstance() ? ep.getInstance().getName() : "dpn";
        String port = ep.getPort() != null ? ep.getPort().getName() : "?";
        return inst + "." + port;
    }
}
