/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static com.neosyn.cg.validation.IssueCodes.ERR_CAST_IMPOSSIBLE;
import static com.neosyn.cg.validation.IssueCodes.ERR_CONFIRM_UNSUPPORTED;
import static com.neosyn.cg.validation.IssueCodes.ERR_INIT_VALUE_OUT_OF_RANGE;
import static com.neosyn.cg.validation.IssueCodes.ERR_PORT_NO_TYPE;
import static com.neosyn.cg.validation.IssueCodes.ERR_TYPE_ONE_BIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.xtext.junit4.validation.AssertableDiagnostics;
import org.eclipse.xtext.junit4.validation.ValidatorTester;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.validation.BlockingIssueCodes;
import com.neosyn.cg.validation.CgValidator;

/**
 * Decisions 2026-10-19: a one-bit integer ({@code u1}, {@code i1}, {@code uint<1>} and the other literal-width-1
 * spellings) and a {@code confirm} port
 * are refused, and the refusal BLOCKS -- on the CLI and in the editor, which read one list
 * ({@link BlockingIssueCodes}) since TRAPS T4.
 */
@RunWith(XtextRunner.class)
@InjectWith(CgInjectorProvider.class)
public class RefusedConstructsTest {

	@Inject
	private ParseHelper<Module> parseHelper;

	@Inject
	private Injector injector;

	private ValidatorTester<CgValidator> validatorTester;

	@Before
	public void setUp() {
		validatorTester = new ValidatorTester<>(injector.getInstance(CgValidator.class), injector);
	}

	/** Issue codes of the ERROR diagnostics for {@code src}, one entry per diagnostic. */
	private List<String> errorCodes(String src) throws Exception {
		Module m = parseHelper.parse(src);
		assertNotNull(m);
		assertTrue("should parse: " + m.eResource().getErrors(), m.eResource().getErrors().isEmpty());
		AssertableDiagnostics diagnostics = validatorTester.validate(m);
		List<String> codes = new ArrayList<>();
		for (Diagnostic d : diagnostics.getDiagnostic().getChildren()) {
			if (d.getSeverity() == Diagnostic.ERROR && d instanceof AbstractValidationDiagnostic) {
				String code = ((AbstractValidationDiagnostic) d).getIssueCode();
				if (code != null) {
					codes.add(code);
				}
			}
		}
		return codes;
	}

	private static String task(String ports, String body) {
		return "package test;\ntask T {\n" + ports + "\n    void loop() {\n" + body + "\n    }\n}\n";
	}

	private static long count(List<String> codes, String code) {
		return codes.stream().filter(code::equals).count();
	}

	@Test
	public void bothRefusalsBlock_andTheEditorReadsTheSameList() {
		assertTrue(BlockingIssueCodes.CODES.contains(ERR_TYPE_ONE_BIT));
		assertTrue(BlockingIssueCodes.CODES.contains(ERR_CONFIRM_UNSUPPORTED));
		// Two of the codes the editor's old copy lacked: it showed them as warnings the CLI refused.
		assertTrue(BlockingIssueCodes.CODES.contains(ERR_CAST_IMPOSSIBLE));
		assertTrue(BlockingIssueCodes.CODES.contains(ERR_PORT_NO_TYPE));
		// TRAPS T174: assigning a const crashed the simulator and wrote to a Verilog localparam
		assertTrue(BlockingIssueCodes.CODES.contains(com.neosyn.cg.validation.IssueCodes.ERR_CANNOT_ASSIGN_CONST));
	}

	@Test
	public void oneBitIntegers_areRefused() throws Exception {
		List<String> codes = errorCodes(task("    in u1 a; in i1 b; out bool q;",
				"        q.write(a.read() == 0 && b.read() == 0);"));
		assertEquals(codes.toString(), 2, count(codes, ERR_TYPE_ONE_BIT));
	}

	@Test
	public void aLiteralWidthOfOne_isRefusedInEverySpelling() throws Exception {
		String[] types = { "uint<1>", "int<1>", "unsigned<1>", "signed<1>", "unsigned int<1>" };
		for (String t : types) {
			List<String> codes = errorCodes(task("    in " + t + " a; out bool q;", "        q.write(a.read() == 0);"));
			assertEquals(t + " -> " + codes, 1, count(codes, ERR_TYPE_ONE_BIT));
		}
	}

	@Test
	public void widerOrNamedWidths_areAccepted() throws Exception {
		// uint<2> is two bits; a width given by a name is not judged here (a generic's width-1 instance is legitimate)
		List<String> codes = errorCodes("package test;\ntask T {\n    const int W = 1;\n    in uint<2> a; in uint<W> b; out bool q;\n"
				+ "    void loop() {\n        q.write(a.read() == 0 && b.read() == 0);\n    }\n}\n");
		assertEquals(codes.toString(), 0, count(codes, ERR_TYPE_ONE_BIT));
	}

	private static String stateVars(String decls) {
		return "package test;\ntask T {\n" + decls + "\n    out u8 q;\n    void loop() {\n        q.write(1);\n    }\n}\n";
	}

	/** TRAPS T173: `u2 w = 50;` was stored as 2 without a word. */
	@Test
	public void initialValueThatDoesNotFit_isRefused() throws Exception {
		String[] bad = { "    u2 w = 50;", "    u8 w = 256;", "    u8 w = -1;", "    i4 w = 8;", "    i4 w = -9;" };
		for (String d : bad) {
			List<String> codes = errorCodes(stateVars(d));
			assertEquals(d + " -> " + codes, 1, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
		}
	}

	@Test
	public void initialValueAtTheEdges_isAccepted() throws Exception {
		List<String> codes = errorCodes(stateVars("    u2 a = 3; u8 b = 255; u8 c = 0; i4 d = 7; i4 e = -8; u16 f = 0xFFFF;"));
		assertEquals(codes.toString(), 0, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
	}

	/** TRAPS T173: a 5-value initialiser for a 2-element array crashed the simulator. */
	@Test
	public void arrayInitialiserLongerThanTheArray_isRefused() throws Exception {
		List<String> codes = errorCodes(stateVars("    u5 w[2] = { 1, 2, 1, 2, 1 };"));
		assertEquals(codes.toString(), 1, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
		codes = errorCodes(stateVars("    u4 w[3] = { 1, 2, 16 };"));   // right length, one element too big
		assertEquals(codes.toString(), 1, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
	}

	@Test
	public void arrayInitialiserOfTheRightLengthOrShorter_isAccepted() throws Exception {
		List<String> codes = errorCodes(stateVars("    u4 a[3] = { 1, 2, 15 }; u4 b[4] = { 1, 2 };"));
		assertEquals(codes.toString(), 0, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
	}

	@Test
	public void bundleConstantThatDoesNotFit_isRefused() throws Exception {
		List<String> codes = errorCodes("package test;\nbundle B {\n    u2 w = 50;\n}\n");
		assertEquals(codes.toString(), 1, count(codes, ERR_INIT_VALUE_OUT_OF_RANGE));
	}

	@Test
	public void bool_isAccepted() throws Exception {
		List<String> codes = errorCodes(task("    in bool a; out bool q;", "        q.write(!a.read());"));
		assertEquals(codes.toString(), 0, count(codes, ERR_TYPE_ONE_BIT));
	}

	@Test
	public void confirm_isRefusedInEverySpelling() throws Exception {
		// inline, parentheses, block, and the deprecated `sync ack` / `(ack)`: one error per port
		String[] spellings = {
				"    in stream u8 x; out confirm u8 y;",
				"    in stream u8 x; out u8 y(confirm);",
				"    in stream u8 x; confirm { out u8 y; }",
				"    in stream u8 x; out sync ack u8 y;",
				"    in stream u8 x; out u8 y(ack);",
		};
		for (String ports : spellings) {
			List<String> codes = errorCodes(task(ports, "        y.write(x.read());"));
			assertEquals(ports + " -> " + codes, 1, count(codes, ERR_CONFIRM_UNSUPPORTED));
		}
	}

	@Test
	public void confirm_inheritedFromACommaList_isRefused() throws Exception {
		// `y2` has no type of its own: it takes type AND interface from `y1`
		List<String> codes = errorCodes(task("    in stream u8 x; out confirm u8 y1, y2;",
				"        u8 v = x.read();\n        y1.write(v);\n        y2.write(v);"));
		assertEquals(codes.toString(), 2, count(codes, ERR_CONFIRM_UNSUPPORTED));
	}

	private static final String BOOL_STATE = "    in stream bool a; out stream u4 n;\n    bool buf[2][4]; bool rowp;";

	/** TRAPS T181: `1 - rowp` was a warning, and in an index not reported at all; IR generation then crashed. */
	@Test
	public void arithmeticOnABool_isRefused_inAnIndexToo() throws Exception {
		String[] bodies = {
				"        u4 z = 1 - rowp; n.write(z);",
				"        bool x = buf[1 - rowp][1]; n.write(x ? 1 : 0);",
				"        buf[1 - rowp][1] = a.read(); n.write(0);",
				"        u4 z = rowp << 1; n.write(z);",
		};
		for (String body : bodies) {
			List<String> codes = errorCodes(task(BOOL_STATE, body));
			assertEquals(body + " -> " + codes, 1, count(codes, com.neosyn.cg.validation.IssueCodes.ERR_BOOL_ARITHMETIC));
		}
		assertTrue(BlockingIssueCodes.CODES.contains(com.neosyn.cg.validation.IssueCodes.ERR_BOOL_ARITHMETIC));
	}

	/** `bool == 1` is supported on purpose (BlockingIssueCodes NB); integer arithmetic in an index is fine. */
	@Test
	public void comparingABool_andIntegerIndexArithmetic_areNotThisError() throws Exception {
		String[] bodies = {
				"        bool x = a.read(); n.write(x == 1 ? 1 : 0);",
				"        bool x = buf[(rowp ? 1 : 0)][1]; n.write(x ? 1 : 0);",
				"        u2 i = 1; bool x = buf[1][i + 1]; n.write(x ? 1 : 0);",
		};
		for (String body : bodies) {
			List<String> codes = errorCodes(task(BOOL_STATE, body));
			assertEquals(body + " -> " + codes, 0, count(codes, com.neosyn.cg.validation.IssueCodes.ERR_BOOL_ARITHMETIC));
		}
		assertFalse(BlockingIssueCodes.CODES.contains(com.neosyn.cg.validation.IssueCodes.ERR_OPERATOR_UNDEFINED));
	}

	private static String combinational(String fields, String body) {
		return "package test;\ntask T {\n    properties { type: \"combinational\" }\n"
				+ "    push { in u3 addr; out u2 y; }\n" + fields + "\n    void loop() {\n" + body + "\n    }\n}\n";
	}

	/** TRAPS T184: a written state variable in a combinational task became a latch behind a warning. */
	@Test
	public void aCombinationalTaskThatWritesStateIsRefused() throws Exception {
		String[][] cases = {
				{ "    u3 last;", "        if (addr.available()) { last = addr.read(); }\n        y.write((u2) (last >> 1));" },
				{ "    u2 t[4];", "        u3 a = addr.read(); t[1] = (u2) a; y.write(t[0]);" },
				{ "    u2 n;", "        u3 a = addr.read(); n++; y.write(n);" },
		};
		for (String[] c : cases) {
			List<String> codes = errorCodes(combinational(c[0], c[1]));
			assertEquals(c[0] + " -> " + codes, 1,
					count(codes, com.neosyn.cg.validation.IssueCodes.ERR_COMBINATIONAL_STATE));
		}
		assertTrue(BlockingIssueCodes.CODES.contains(com.neosyn.cg.validation.IssueCodes.ERR_COMBINATIONAL_STATE));
	}

	/** A table it only reads is a ROM (Intel-8088 BIU_RF): no error. */
	@Test
	public void aCombinationalTaskThatOnlyReadsATableIsAccepted() throws Exception {
		List<String> codes = errorCodes(combinational("    u2 table[4] = {0, 1, 2, 3};",
				"        u3 a = addr.read(); y.write(table[(u2) a]);"));
		assertEquals(codes.toString(), 0, count(codes, com.neosyn.cg.validation.IssueCodes.ERR_COMBINATIONAL_STATE));
	}

	@Test
	public void pushAndStream_areAccepted() throws Exception {
		List<String> codes = errorCodes(task("    in stream u8 x; out push u8 p; out stream u8 s;",
				"        u8 v = x.read();\n        p.write(v);\n        s.write(v);"));
		assertEquals(codes.toString(), 0, count(codes, ERR_CONFIRM_UNSUPPORTED));
	}
}
