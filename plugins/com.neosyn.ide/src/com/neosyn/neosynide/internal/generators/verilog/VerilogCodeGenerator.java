/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog;

import static com.neosyn.neosynide.NeosynIdeModule.VERILOG;
import static com.neosyn.neosynide.internal.generators.GeneratorExtensions.getNumberOfHexadecimalDigits;

import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.neosyn.core.util.CoreUtil;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeArray;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.neosynide.internal.generators.AbstractGenerator;
import com.neosyn.neosynide.internal.generators.Namer;

/**
 * This class implements a generator for Verilog.
 * 

 * 
 */
public class VerilogCodeGenerator extends AbstractGenerator {

	// Verilog-2001 reserved keywords
	private static Set<String> RESERVED = ImmutableSet.of("always", "and", "assign", "automatic",
			"begin", "bit", "buf", "bufif0", "bufif1", "byte", "case", "casex", "casez", "cell",
			"cmos", "config", "deassign", "default", "defparam", "design", "disable", "edge",
			"else", "end", "endcase", "endconfig", "endfunction", "endgenerate", "endmodule",
			"endprimitive", "endspecify", "endtable", "endtask", "event", "for", "force", "forever",
			"fork", "function", "generate", "genvar", "highz0", "highz1", "if", "ifnone", "initial",
			"instance", "inout", "input", "integer", "join", "large", "liblist", "localparam",
			"macromodule", "medium", "module", "nand", "negedge", "nmos", "nor", "not",
			"noshowcancelled", "notif0", "notif1", "or", "output", "parameter", "pmos", "posedge",
			"primitive", "pull0", "pull1", "pulldown", "pullup", "pulsestyle_onevent",
			"pulsestyle_ondetect", "rcmos", "real", "realtime", "reg", "release", "repeat", "rnmos",
			"rpmos", "rtran", "rtranif0", "rtranif1", "scalared", "signed", "showcancelled",
			"small", "specify", "specparam", "strength", "strong0", "strong1", "supply0", "supply1",
			"table", "task", "time", "tran", "tranif0", "tranif1", "tri", "tri0", "tri1", "triand",
			"trior", "trireg", "type", "unsigned", "use", "vectored", "wait", "wand", "weak0",
			"weak1", "while", "wire", "wor", "xnor", "xor",
			// SystemVerilog reserved keywords (commonly used as identifiers that cause conflicts)
			"assert", "assume", "before", "bind", "bins", "binsof", "break", "chandle", "checker",
			"class", "clocking", "constraint", "context", "continue", "cover", "covergroup",
			"coverpoint", "cross", "dist", "do", "endchecker", "endclass", "endclocking",
			"endgroup", "endinterface", "endpackage", "endprogram", "endproperty", "endsequence",
			"enum", "eventually", "expect", "export", "extends", "extern", "final", "first_match",
			"foreach", "forkjoin", "global", "iff", "ignore_bins", "illegal_bins", "implements",
			"implies", "import", "inside", "interface", "intersect", "join_any", "join_none",
			"let", "local", "logic", "longint", "matches", "modport", "new", "nexttime", "null",
			"package", "packed", "priority", "program", "property", "protected", "pure", "rand",
			"randc", "randcase", "randsequence", "ref", "reject_on", "return", "s_always",
			"s_eventually", "s_nexttime", "s_until", "s_until_with", "sequence", "shortint",
			"shortreal", "solve", "static", "string", "strong", "struct", "super", "sync_accept_on",
			"sync_reject_on", "tagged", "this", "throughout", "timeprecision", "timeunit", "typedef",
			"union", "unique", "unique0", "until", "until_with", "untyped", "var", "virtual", "void",
			"wait_order", "weak", "wildcard", "with", "within");
	
	private Namer namer;

	public VerilogCodeGenerator() {
		namer = new Namer(RESERVED, "\\", " ");
	}
	
	@Override
	protected void doPrint(Entity entity) {
		if (!isEnabled()) {
			return;
		}
		
		CharSequence contents = new VerilogPrinter(namer, this).doSwitch(entity);
		writer.write(computePath(entity), contents);
	}

	@Override
	protected void doPrintTestbench(Entity entity) {
		// compute path *before* changing the entity
		String path = computePathTb(entity);

		// generate test
		if (CoreUtil.needsWrapper(entity)) {
			entity = createTestDpn(entity);
		}

		// print testbench
		CharSequence contents = new VerilogTestbenchPrinter(namer, this).printTestbench(entity);
		writer.write(path, contents);
	}

	@Override
	public String getFileExtension() {
		return "v";
	}

	@Override
	public String getName() {
		return VERILOG;
	}

	/**
	 * Prints one .hex file per each variable that is an array. A .hex file contains all values of
	 * the array in hexadecimal notation.
	 * 
	 * @param variables
	 *            a list of variables
	 */
	private void printHexFiles(Entity entity) {
		List<Var> variables = entity.getVariables();

		String file = IrUtil.getFile(entity.getName());
		for (Var var : variables) {
			Type type = var.getType();
			if (type.isArray()) {
				TypeArray typeArray = (TypeArray) type;
				int size = TypeUtil.getSize(typeArray.getElementType());

				CharSequence sequence;
				EObject value = var.getInitialValue();
				if (value == null) {
					int n = 1;
					for (Integer dim : typeArray.getDimensions()) {
						n *= dim;
					}

					int numDigits = getNumberOfHexadecimalDigits(size);
					String str = String.format("%0" + numDigits + "x\n", 0);
					sequence = Strings.repeat(str, n);
				} else {
					sequence = new VerilogHexPrinter(size).doSwitch(value);
				}

				String path = computePath(file + "_" + var.getName(), "hex");
				writer.write(path, sequence);
			}
		}
	}

	@Override
	public void transform(Entity entity) {
		printHexFiles(entity);

		VerilogTransformer transformer = new VerilogTransformer();
		transformer.doSwitch(entity);
	}
	
	@Override
	protected boolean isEnabled() {
		// Verilog generation is the open-source path — always enabled.
		// Commercial entitlement is enforced at the CLI/LSP entry point,
		// not in the generator (see releng/lsp-server license gate).
		return true;
	}
}
