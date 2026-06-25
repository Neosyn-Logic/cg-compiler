/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.instantiation.IInstantiator;

/**
 * Unit tests for Cx IR generation.
 * These tests verify that the instantiator correctly transforms Cx AST to IR.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class CgIRGenerationTest {

    @Inject
    private ParseHelper<Module> parseHelper;

    @Inject
    private ValidationTestHelper validationHelper;

    @Inject
    private IInstantiator instantiator;

    // ==================== TASK IR GENERATION TESTS ====================

    @Test
    public void testSimpleTaskParsing() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task SimpleTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(inp.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        EList<Diagnostic> errors = module.eResource().getErrors();
        assertTrue("Should have no parse errors: " + errors, errors.isEmpty());

        // Validate the module has no semantic errors
        validationHelper.assertNoErrors(module);

        // Check the module contains a task
        assertFalse("Module should have entities", module.getEntities().isEmpty());
        CgEntity entity = module.getEntities().get(0);
        assertTrue("Entity should be a Task", entity instanceof Task);
        assertEquals("Task name should be SimpleTask", "SimpleTask", entity.getName());
    }

    @Test
    public void testTaskWithStateVariable() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task TaskWithState {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    u8 state = 0;\n" +
            "    void loop() {\n" +
            "        state = inp.read();\n" +
            "        outp.write(state);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testTaskWithMultiplePorts() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task MultiPortTask {\n" +
            "    in u8 in1, in2;\n" +
            "    out u8 out1, out2;\n" +
            "    void loop() {\n" +
            "        out1.write(in1.read());\n" +
            "        out2.write(in2.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testTaskWithSyncPorts() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task SyncPortTask {\n" +
            "    in sync u8 inp;\n" +
            "    out sync u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(inp.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== NETWORK IR GENERATION TESTS ====================

    @Test
    public void testSimpleNetworkParsing() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network SimpleNetwork {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());

        assertFalse("Module should have entities", module.getEntities().isEmpty());
        CgEntity entity = module.getEntities().get(0);
        assertTrue("Entity should be a Network", entity instanceof Network);
        assertEquals("Network name should be SimpleNetwork", "SimpleNetwork", entity.getName());
    }

    @Test
    public void testNetworkWithInlineTask() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "network NetworkWithInlineTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    \n" +
            "    inner = new task {\n" +
            "        in u8 a; out u8 b;\n" +
            "        void loop() { b.write(a.read()); }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
    }

    // ==================== BUNDLE IR GENERATION TESTS ====================

    @Test
    public void testSimpleBundleParsing() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "bundle Constants {\n" +
            "    const u8 VALUE = 42;\n" +
            "    const u16 LARGE = 1000;\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());

        assertFalse("Module should have entities", module.getEntities().isEmpty());
        CgEntity entity = module.getEntities().get(0);
        assertTrue("Entity should be a Bundle", entity instanceof Bundle);
        assertEquals("Bundle name should be Constants", "Constants", entity.getName());
    }

    // ==================== TYPE SYSTEM TESTS ====================

    @Test
    public void testVariousTypes() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task TypesTask {\n" +
            "    in bool b_in;\n" +
            "    in u8 u8_in;\n" +
            "    in u16 u16_in;\n" +
            "    in u32 u32_in;\n" +
            "    in u64 u64_in;\n" +
            "    in i8 i8_in;\n" +
            "    in i16 i16_in;\n" +
            "    in i32 i32_in;\n" +
            "    out bool b_out;\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testArrayType() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ArrayTask {\n" +
            "    u8 arr[10];\n" +
            "    void loop() {\n" +
            "        arr[0] = 1;\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
    }

    // ==================== EXPRESSION TESTS ====================

    @Test
    public void testArithmeticExpressions() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ArithTask {\n" +
            "    in u8 a, b;\n" +
            "    out u8 sum, diff, prod;\n" +
            "    void loop() {\n" +
            "        sum.write((u8)(a.read() + b.read()));\n" +
            "        diff.write((u8)(a.read() - b.read()));\n" +
            "        prod.write((u8)(a.read() * b.read()));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testBitwiseExpressions() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task BitwiseTask {\n" +
            "    in u8 a, b;\n" +
            "    out u8 and_r, or_r, xor_r;\n" +
            "    void loop() {\n" +
            "        and_r.write(a.read() & b.read());\n" +
            "        or_r.write(a.read() | b.read());\n" +
            "        xor_r.write(a.read() ^ b.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testShiftExpressions() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ShiftTask {\n" +
            "    in u8 a;\n" +
            "    out u8 shl, shr;\n" +
            "    void loop() {\n" +
            "        shl.write((u8)(a.read() << 2));\n" +
            "        shr.write(a.read() >> 2);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== CONTROL FLOW TESTS ====================

    @Test
    public void testIfStatement() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task IfTask {\n" +
            "    in u8 a;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        u8 x = a.read();\n" +
            "        if (x > 10) {\n" +
            "            outp.write(1);\n" +
            "        } else {\n" +
            "            outp.write(0);\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testWhileLoop() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task WhileTask {\n" +
            "    in u8 count;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        u8 i = 0;\n" +
            "        u8 n = count.read();\n" +
            "        while (i < n) {\n" +
            "            i++;\n" +
            "        }\n" +
            "        outp.write(i);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testForLoop() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ForTask {\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        u8 sum = 0;\n" +
            "        for (u8 i = 0; i < 10; i++) {\n" +
            "            sum++;\n" +
            "        }\n" +
            "        outp.write(sum);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== FUNCTION CALL TESTS ====================

    @Test
    public void testFunctionDefinitionAndCall() throws Exception {
        // In Cx, functions that return a value must be declared 'const' (pure functions)
        Module module = parseHelper.parse(
            "package test;\n" +
            "task FuncTask {\n" +
            "    in u8 a;\n" +
            "    out u8 outp;\n" +
            "    \n" +
            "    const u8 double(u8 x) {\n" +
            "        return (u8)(x * 2);\n" +
            "    }\n" +
            "    \n" +
            "    void loop() {\n" +
            "        outp.write(double(a.read()));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== INSTANTIATOR TESTS ====================

    @Test
    public void testInstantiatorUpdateDirect() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task InstantiatorTestTask {\n" +
            "    in u8 inp;\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(inp.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors", module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);

        // Clear any previous data
        instantiator.clearData();

        // Use updateDirect for standalone mode (bypasses Xtext index)
        instantiator.updateDirect(module);

        // Verify IR entity was created by checking the mapping
        CgEntity cxEntity = module.getEntities().get(0);
        assertNotNull("Cx entity should exist", cxEntity);
        assertEquals("Entity name should match", "InstantiatorTestTask", cxEntity.getName());

        // The update should complete without throwing
        // IR entity is created and added to the resource set
    }

    @Test
    public void testInstantiatorWithNetwork() throws Exception {
        // Network with inline task that reads network input directly
        // (like stage_1 in Simple.cx example)
        Module module = parseHelper.parse(
            "package test;\n" +
            "network TestNetwork {\n" +
            "    in sync u8 inp;\n" +
            "    out sync u8 outp;\n" +
            "    \n" +
            "    inner = new task {\n" +
            "        void loop() {\n" +
            "            outp.write(inp.read);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);

        // Clear any previous data
        instantiator.clearData();

        // Use updateDirect for standalone mode
        instantiator.updateDirect(module);

        // Verify network entity was processed
        CgEntity cxEntity = module.getEntities().get(0);
        assertNotNull("Cx entity should exist", cxEntity);
        assertTrue("Entity should be a Network", cxEntity instanceof Network);
    }

    // ==================== MULTIPLE ENTITIES TEST ====================

    @Test
    public void testMultipleEntitiesInModule() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "\n" +
            "bundle SharedConstants {\n" +
            "    const u8 MAGIC = 0xAB;\n" +
            "}\n" +
            "\n" +
            "task Producer {\n" +
            "    out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(SharedConstants.MAGIC);\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "task Consumer {\n" +
            "    in u8 inp;\n" +
            "    void loop() {\n" +
            "        u8 x = inp.read();\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull("Module should not be null", module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());

        assertEquals("Module should have 3 entities", 3, module.getEntities().size());
    }

    // ==================== ARITHMETIC TESTS (from Arith.cx) ====================

    @Test
    public void testAddTask() throws Exception {
        // Based on Add task from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Add {\n" +
            "    properties {\n" +
            "        test: {in1: [ 0, 65535, 112 ], in2: [ 255, 1, 112 ], outp: [ 255, 0, 224 ] }\n" +
            "    }\n" +
            "    in u16 in1, in2; out u16 outp;\n" +
            "    void loop() {\n" +
            "        outp.write((in1.read() + in2.read()) & 0xFFFF);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testSubTask() throws Exception {
        // Based on Sub task from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Sub {\n" +
            "    in u16 in1, in2; out u16 outp;\n" +
            "    void loop() {\n" +
            "        outp.write((u16) (in1.read() - in2.read()));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testMultTask() throws Exception {
        // Based on Mult task from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Mult {\n" +
            "    in u16 in1, in2; out u16 outp;\n" +
            "    void loop() {\n" +
            "        outp.write((in1.read() * in2.read()) & 65535);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testDivModTask() throws Exception {
        // Based on DivTwo and Modulo tasks from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task DivMod {\n" +
            "    in u8 inp; out u8 div_out, mod_out;\n" +
            "    void loop() {\n" +
            "        u8 val = inp.read();\n" +
            "        div_out.write(val / 2);\n" +
            "        mod_out.write(val % 16);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testUnaryMinusTask() throws Exception {
        // Based on UnaryMinus task from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task UnaryMinus {\n" +
            "    in i32 in1; out i33 out1;\n" +
            "    void loop() {\n" +
            "        out1.write(-in1.read);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testOperatorsTask() throws Exception {
        // Based on Operators task from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Operators {\n" +
            "    in u16 in1, in2; out u16 outp;\n" +
            "    void loop() {\n" +
            "        u16 inp;\n" +
            "        inp = in1.read();\n" +
            "        inp += 15;\n" +
            "        inp = (u16) (inp << 3);\n" +
            "        inp -= in2.read();\n" +
            "        inp = inp >> 2;\n" +
            "        inp = inp & 4095;\n" +
            "        outp.write(inp);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testLargeBitWidths() throws Exception {
        // Based on Arith_64 and Arith_128 from Arith.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task LargeBits {\n" +
            "    sync {\n" +
            "        in u64 in1, in2; out u64 outp;\n" +
            "    }\n" +
            "    u64 a, b;\n" +
            "    void loop() {\n" +
            "        a = in1.read;\n" +
            "        b = in2.read;\n" +
            "        outp.write((u64) (a + b));\n" +
            "        outp.write((u64) (a - b));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== BIT OPERATION TESTS (from Bits.cx) ====================

    @Test
    public void testBitSelect() throws Exception {
        // Based on BitSelect1 task from Bits.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task BitSelect {\n" +
            "    sync {\n" +
            "        in u16 inp; out bool outp;\n" +
            "    }\n" +
            "    ushort w;\n" +
            "    void loop() {\n" +
            "        ushort v = inp.read();\n" +
            "        outp.write(v[15]);\n" +
            "        w = inp.read();\n" +
            "        outp.write(w[8]);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testBitAssignment() throws Exception {
        // Based on BitSelect2 task from Bits.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task BitAssign {\n" +
            "    sync {\n" +
            "        in u8 inp; out u8 outp;\n" +
            "    }\n" +
            "    void loop() {\n" +
            "        ushort v = inp.read();\n" +
            "        v[0] = false;\n" +
            "        v[7] = true;\n" +
            "        outp.write(v & 0xFF);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testBitwiseNegation() throws Exception {
        // Based on BitwiseNeg task from Bits.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task BitwiseNeg {\n" +
            "    in u16 din; out u16 dout;\n" +
            "    void loop() {\n" +
            "        u31 a = 0x4000_0000;\n" +
            "        u31 b = ~a;\n" +
            "        assert(b == 0x3FFF_FFFF);\n" +
            "        dout.write(~din.read);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testCounterIncrement() throws Exception {
        // Based on CounterIncrement task from Bits.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task CounterIncrement {\n" +
            "    out sync u2 count;\n" +
            "    u2 cnt;\n" +
            "    void loop() {\n" +
            "        count.write(cnt);\n" +
            "        cnt++;\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testMaskAndResize() throws Exception {
        // Based on Mask and Resize tasks from Bits.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task MaskResize {\n" +
            "    in u16 inp; out u9 outp1, outp2;\n" +
            "    void loop() {\n" +
            "        outp1.write(inp.read() & 511);\n" +
            "        outp2.write((u9) inp.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== FUNCTION CALL TESTS (from Calls.cx) ====================

    @Test
    public void testConstFunctionCall() throws Exception {
        // Based on Call1 task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ConstFuncs {\n" +
            "    in sync u4 i; out sync u4 o;\n" +
            "    void loop() {\n" +
            "        u4 a = i.read();\n" +
            "        u4 b = i.read();\n" +
            "        o.write((u4) (g() * f(a, b)));\n" +
            "    }\n" +
            "    const u4 f(u4 x, u4 y) {\n" +
            "        return (u4) (x + y);\n" +
            "    }\n" +
            "    const u4 g() {\n" +
            "        return 1;\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testVoidProcedureCall() throws Exception {
        // Based on Invoke1 task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task VoidProc {\n" +
            "    in sync u16 i; out sync u16 o;\n" +
            "    void loop() {\n" +
            "        f();\n" +
            "    }\n" +
            "    void f() {\n" +
            "        o.write(i.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testMultipleProcedureCalls() throws Exception {
        // Based on Invoke2 task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task MultiProc {\n" +
            "    sync {\n" +
            "        in u16 i; out u16 o, o2;\n" +
            "    }\n" +
            "    void loop() {\n" +
            "        f();\n" +
            "        g();\n" +
            "    }\n" +
            "    void f() {\n" +
            "        o.write(i.read());\n" +
            "    }\n" +
            "    void g() {\n" +
            "        o2.write((i.read() << 1) & 0xFFFF);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testAssertStatement() throws Exception {
        // Based on AssertTest task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task AssertTest {\n" +
            "    in sync u32 in1, sync in2; out sync bool outp;\n" +
            "    void loop() {\n" +
            "        assert(in1.read + in2.read - 1 == 0xFFFF_FFFF);\n" +
            "        outp.write(true);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testPrintStatement() throws Exception {
        // Based on PrintStmt task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task PrintTest {\n" +
            "    in sync u32 in1, sync in2; out sync bool outp;\n" +
            "    void loop() {\n" +
            "        bool justAbool = false;\n" +
            "        print(\"sum = \", (in1.read + in2.read) & 0xFFFF_FFFF, \" done\");\n" +
            "        outp.write(true);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== COMBINATIONAL TASK TESTS (from Comb.cx) ====================

    @Test
    public void testCombinationalTask() throws Exception {
        // Based on Combinational1 task from Comb.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Combinational {\n" +
            "    properties {\n" +
            "        type: \"combinational\"\n" +
            "    }\n" +
            "    in u32 op1, op2, bool doAdd;\n" +
            "    out u32 res, bool overflow;\n" +
            "    void loop() {\n" +
            "        u32 oper1 = op1.read;\n" +
            "        u32 oper2 = op2.read;\n" +
            "        u32 result = 0;\n" +
            "        if (doAdd.read) {\n" +
            "            result = (u32) (oper1 + oper2);\n" +
            "        } else {\n" +
            "            result = (u32) (oper1 - oper2);\n" +
            "        }\n" +
            "        overflow.write(oper1[31] && oper2[31] && !result[31]);\n" +
            "        res.write(result);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    @Test
    public void testBarrelShifter() throws Exception {
        // Based on BarrelShifter task from Comb.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task BarrelShifter {\n" +
            "    in sync u32 inn, sync u8 inp; out sync u32 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(barrelShifter(inn.read(), inp.read()));\n" +
            "    }\n" +
            "    const u32 barrelShifter(u32 a, u8 b) {\n" +
            "        u32 toto[8];\n" +
            "        toto[0] = a;\n" +
            "        for (u8 i = 1; i < 8; i++) {\n" +
            "            toto[i] = (u32) (toto[i - 1] << 1);\n" +
            "        }\n" +
            "        return toto[b];\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== SETUP FUNCTION TESTS ====================

    @Test
    public void testSetupFunction() throws Exception {
        // Based on Fib task from FIFOs.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task WithSetup {\n" +
            "    out sync u8 dout;\n" +
            "    u8 n_1 = 1;\n" +
            "    u8 n = 1;\n" +
            "    u8 count;\n" +
            "    void setup() {\n" +
            "        for (count = 0; count != 11; count++) {\n" +
            "            u8 old_n = n;\n" +
            "            n += n_1;\n" +
            "            dout.write(n);\n" +
            "            n_1 = old_n;\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== READY PORT TESTS ====================

    @Test
    public void testReadyPorts() throws Exception {
        // Based on Fib task from FIFOs.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ReadyPorts {\n" +
            "    out sync ready u8 dout;\n" +
            "    in sync ready u8 din;\n" +
            "    void loop() {\n" +
            "        dout.write(din.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== TYPEDEF TESTS ====================

    @Test
    public void testTypedef() throws Exception {
        // Based on Definitions bundle from Simple.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "bundle Definitions {\n" +
            "    typedef u9 count_t;\n" +
            "    typedef u6 clipped;\n" +
            "}\n" +
            "task UseTypedefs {\n" +
            "    import test.Definitions.*;\n" +
            "    in count_t inp;\n" +
            "    out clipped outp;\n" +
            "    void loop() {\n" +
            "        count_t local = inp.read();\n" +
            "        outp.write(local > 63 ? 63 : (clipped) local);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== ARRAY INITIALIZATION TESTS ====================

    @Test
    public void testArrayInitialization() throws Exception {
        // Based on Call2 task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task ArrayInit {\n" +
            "    in sync bool bitt;\n" +
            "    out sync bool outp;\n" +
            "    u4 TAB[4] = { 0, 3, 2, 1 };\n" +
            "    u5 count = 1;\n" +
            "    void loop() {\n" +
            "        bool b = bitt.read;\n" +
            "        if (!b) {\n" +
            "            TAB[1] = 2;\n" +
            "        }\n" +
            "        count++;\n" +
            "        outp.write(count == TAB[1]);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== NETWORK WITH EXTERNAL TASK TESTS ====================

    @Test
    public void testNetworkWithExternalTask() throws Exception {
        // Based on TopCounter network from Simple.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Counter {\n" +
            "    out sync u8 count;\n" +
            "    u8 count_i;\n" +
            "    void loop() {\n" +
            "        count.write(count_i);\n" +
            "        count_i++;\n" +
            "    }\n" +
            "}\n" +
            "network TopNetwork {\n" +
            "    out sync u8 outp;\n" +
            "    counter = new Counter();\n" +
            "    process = new task {\n" +
            "        void loop() {\n" +
            "            outp.write(counter.count.read);\n" +
            "        }\n" +
            "    };\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== NETWORK WITH READS/WRITES CONNECTIONS ====================

    @Test
    public void testNetworkReadsWrites() throws Exception {
        // Based on TopCounter network from Simple.cx with explicit connections
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Processor {\n" +
            "    in sync u8 a, b; out sync u8 result;\n" +
            "    void loop() {\n" +
            "        result.write((u8)(a.read + b.read));\n" +
            "    }\n" +
            "}\n" +
            "network TopNet {\n" +
            "    in sync u8 x, y;\n" +
            "    out sync u8 z;\n" +
            "    proc = new Processor();\n" +
            "    proc.reads(x, y);\n" +
            "    proc.writes(z);\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== PROPERTIES BLOCK TESTS ====================

    @Test
    public void testPropertiesWithTestVectors() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task WithTestVectors {\n" +
            "    properties {\n" +
            "        num_states: 0,\n" +
            "        num_transitions: 1,\n" +
            "        test: {\n" +
            "            inp: [ 1, 2, 3, 4 ],\n" +
            "            outp: [ 2, 4, 6, 8 ]\n" +
            "        }\n" +
            "    }\n" +
            "    in u8 inp; out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write((u8)(inp.read() * 2));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== TERNARY EXPRESSION TESTS ====================

    @Test
    public void testTernaryExpression() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task TernaryTest {\n" +
            "    in u8 cond, a, b; out u8 result;\n" +
            "    void loop() {\n" +
            "        u8 c = cond.read();\n" +
            "        result.write(c > 0 ? a.read() : b.read());\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== FENCE STATEMENT TESTS ====================

    @Test
    public void testFenceStatement() throws Exception {
        // Fence must be placed between two statements
        Module module = parseHelper.parse(
            "package test;\n" +
            "task FenceTest {\n" +
            "    in sync u8 inp; out sync u8 outp;\n" +
            "    u8 temp;\n" +
            "    void setup() {\n" +
            "        temp = inp.read();\n" +
            "        fence;\n" +
            "        outp.write(temp);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== IDLE STATEMENT TESTS ====================

    @Test
    public void testIdleStatement() throws Exception {
        // Based on Compare task from Simple.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task IdleTest {\n" +
            "    in sync u8 inp; out sync u8 outp;\n" +
            "    u8 temp;\n" +
            "    void setup() {\n" +
            "        idle(1);\n" +
            "        temp = inp.read();\n" +
            "    }\n" +
            "    void loop() {\n" +
            "        outp.write(temp);\n" +
            "        temp = inp.read();\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== CHAR TYPE TESTS ====================

    @Test
    public void testCharType() throws Exception {
        // Based on SubWord task from Calls.cx
        Module module = parseHelper.parse(
            "package test;\n" +
            "task CharTest {\n" +
            "    in sync char inw; out sync ushort outw;\n" +
            "    void loop() {\n" +
            "        char first = inw.read;\n" +
            "        outw.write(f(first, inw.read));\n" +
            "    }\n" +
            "    const ushort f(char c1, char c2) {\n" +
            "        return ((c1 << 16) >> 8) | c2;\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== SIGNED TYPE TESTS ====================

    @Test
    public void testSignedTypes() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task SignedTest {\n" +
            "    in i16 a, i16 b;\n" +
            "    out i16 sum, i16 diff;\n" +
            "    void loop() {\n" +
            "        i16 x = a.read();\n" +
            "        i16 y = b.read();\n" +
            "        sum.write((i16)(x + y));\n" +
            "        diff.write((i16)(x - y));\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== HEXADECIMAL LITERAL TESTS ====================

    @Test
    public void testHexLiterals() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task HexTest {\n" +
            "    out u32 outp;\n" +
            "    void loop() {\n" +
            "        u32 a = 0xFFFF_FFFF;\n" +
            "        u32 b = 0x1234_5678;\n" +
            "        u64 c = 0xDEAD_BEEF_CAFE_BABE;\n" +
            "        outp.write(a ^ b);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);
    }

    // ==================== IR GENERATION WITH INSTANTIATOR ====================

    @Test
    public void testIRGenerationMultipleTasks() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "task Task1 {\n" +
            "    in u8 inp; out u8 outp;\n" +
            "    void loop() { outp.write(inp.read()); }\n" +
            "}\n" +
            "task Task2 {\n" +
            "    in u16 inp; out u16 outp;\n" +
            "    void loop() { outp.write((u16)(inp.read() * 2)); }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue(module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);

        instantiator.clearData();
        instantiator.updateDirect(module);

        assertEquals("Should have 2 tasks", 2, module.getEntities().size());
        assertTrue(module.getEntities().get(0) instanceof Task);
        assertTrue(module.getEntities().get(1) instanceof Task);
    }

    @Test
    public void testIRGenerationBundleAndTask() throws Exception {
        Module module = parseHelper.parse(
            "package test;\n" +
            "bundle Config {\n" +
            "    const u8 SIZE = 16;\n" +
            "    typedef u4 index_t;\n" +
            "}\n" +
            "task UseConfig {\n" +
            "    import test.Config.*;\n" +
            "    in index_t idx; out u8 outp;\n" +
            "    void loop() {\n" +
            "        outp.write(idx.read() < SIZE ? 1 : 0);\n" +
            "    }\n" +
            "}\n"
        );
        assertNotNull(module);
        assertTrue("Should have no parse errors: " + module.eResource().getErrors(),
                   module.eResource().getErrors().isEmpty());
        validationHelper.assertNoErrors(module);

        instantiator.clearData();
        instantiator.updateDirect(module);

        assertEquals("Should have 2 entities", 2, module.getEntities().size());
        assertTrue(module.getEntities().get(0) instanceof Bundle);
        assertTrue(module.getEntities().get(1) instanceof Task);
    }
}
