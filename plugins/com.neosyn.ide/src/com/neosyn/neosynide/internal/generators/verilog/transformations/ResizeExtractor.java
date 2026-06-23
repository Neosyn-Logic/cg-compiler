/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog.transformations;

import java.util.List;

import java.math.BigInteger;

import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.ExprBinary;
import com.neosyn.models.ir.ExprInt;
import com.neosyn.models.ir.ExprResize;
import com.neosyn.models.ir.ExprTypeConv;
import com.neosyn.models.ir.Expression;
import com.neosyn.models.ir.Instruction;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.OpBinary;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.transform.AbstractExpressionTransformer;
import com.neosyn.models.ir.transform.UniqueNameComputer;
import com.neosyn.models.ir.util.TypeUtil;
import com.neosyn.models.util.EcoreHelper;

/**
 * This class implements resizes in a lint-friendly way using temporary variables.
 * 

 *
 */
public class ResizeExtractor extends AbstractExpressionTransformer {

	private static final IrFactory ir = IrFactory.eINSTANCE;

	/**
	 * Names handed to the holding registers this pass extracts. Rather than a
	 * bland {@code temp}/{@code temp_0}, each extracted resize operand is named
	 * after a constellation, so the generated HDL reads as e.g.
	 * {@code orion = (a + b); sum <= orion[7:0];}. Plain lowercase identifiers,
	 * all valid Verilog. Cycled per entity; {@link UniqueNameComputer} appends
	 * {@code _N} if a module needs more temps than the pool has names.
	 */
	private static final String[] CONSTELLATIONS = {
			"orion", "lyra", "cygnus", "draco", "aquila", "pegasus", "perseus",
			"andromeda", "phoenix", "hydra", "centaurus", "cassiopeia", "carina",
			"vela", "auriga", "cepheus", "columba", "corvus", "crux", "fornax",
			"gemini", "hercules", "indus", "lupus", "lynx", "norma", "octans",
			"pictor", "pyxis", "sagitta", "serpens", "tucana", "volans", "vulpecula"
	};

	private UniqueNameComputer nameComputer;

	/** Index into {@link #CONSTELLATIONS} for the next extracted temp. */
	private int constellationIndex = 0;

	@Override
	public Expression caseExprResize(ExprResize resize) {
		resize = (ExprResize) super.caseExprResize(resize);

		Expression expr = resize.getExpr();
		Type type = TypeUtil.getType(expr);
		if (!type.isInt()) {
			return resize;
		}

		TypeInt ti = (TypeInt) type;

		int targetSize = resize.getTargetSize();
		int sourceSize = ti.getSize();

		// (d) Drop a redundant low-bits mask we are about to truncate away:
		// trunc(N, x & C) == trunc(N, x) when C's low N bits are all 1, since
		// the truncation keeps only those low N bits anyway. Removes the
		// `& 0xFF`-style mask the cast pipeline emits before a narrowing cast.
		if (targetSize < sourceSize) {
			Expression unmasked = stripRedundantLowMask(expr, targetSize);
			if (unmasked != expr) {
				expr = unmasked;
				type = TypeUtil.getType(expr);
				if (!type.isInt()) {
					return resize;
				}
				ti = (TypeInt) type;
				sourceSize = ti.getSize();
			}
		}

		if (sourceSize < targetSize) {
			return extract(ti.isSigned() ? "sext" : "zext", ti, targetSize, expr);
		} else if (targetSize < sourceSize) {
			return extract("trunc", ti, targetSize, expr);
		} else {
			// unnecessary resize, ignore it and replace by expr
			return expr;
		}
	}

	/**
	 * If {@code expr} is a bitwise-AND with a constant whose low {@code keptBits}
	 * bits are all 1, return the other (non-constant) operand — the mask cannot
	 * affect a value that is about to be truncated to {@code keptBits} bits.
	 * Otherwise returns {@code expr} unchanged.
	 */
	private Expression stripRedundantLowMask(Expression expr, int keptBits) {
		if (!(expr instanceof ExprBinary)) {
			return expr;
		}
		ExprBinary bin = (ExprBinary) expr;
		if (bin.getOp() != OpBinary.BITAND) {
			return expr;
		}
		if (isLowBitsAllSet(bin.getE2(), keptBits)) {
			return bin.getE1();
		}
		if (isLowBitsAllSet(bin.getE1(), keptBits)) {
			return bin.getE2();
		}
		return expr;
	}

	/** True if {@code e} is a non-negative int literal with its low {@code n} bits all set. */
	private boolean isLowBitsAllSet(Expression e, int n) {
		if (!(e instanceof ExprInt)) {
			return false;
		}
		BigInteger value = ((ExprInt) e).getValue();
		if (value.signum() < 0) {
			// Negative literal (e.g. a sign-extended all-ones mask) — be
			// conservative and leave it alone.
			return false;
		}
		BigInteger lowMask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
		return value.and(lowMask).equals(lowMask);
	}

	private Expression extract(String operation, TypeInt source, int targetSize, Expression expr) {
		if (!expr.isExprVar()) {
			// must extract expr into a holding variable before resizing it;
			// name it after a constellation rather than a bland `temp`.
			Type type = ir.createTypeInt(source.getSize(), source.isSigned());
			String constellation = CONSTELLATIONS[constellationIndex++ % CONSTELLATIONS.length];
			String name = nameComputer.getUniqueName(constellation);
			Var resizeTmp = ir.newTempLocalVariable(getProcedure(), type, name);

			Instruction inst = EcoreHelper.getContainerOfType(expr, Instruction.class);
			if (inst == null) {
				Block block = EcoreHelper.getContainerOfType(expr, Block.class);
				List<Block> blocks = EcoreHelper.getContainingList(block);
				BlockBasic blockBasic = ir.createBlockBasic();
				blocks.add(blocks.indexOf(block), blockBasic);
				blockBasic.add(ir.createInstAssign(resizeTmp, expr));
			} else {
				BlockBasic block = (BlockBasic) inst.eContainer();
				block.add(block.indexOf(inst), ir.createInstAssign(resizeTmp, expr));
			}

			expr = ir.createExprVar(resizeTmp);
		}

		ExprTypeConv truncated = ir.convert(operation + "." + targetSize, expr);
		truncated.setComputedType(ir.createTypeInt(targetSize, source.isSigned()));
		return truncated;
	}

	@Override
	public void setProcedure(Procedure procedure) {
		if (nameComputer == null) {
			Entity entity = EcoreHelper.getContainerOfType(procedure, Entity.class);
			nameComputer = new UniqueNameComputer(entity.getVariables());
		}

		super.setProcedure(procedure);
	}

}
