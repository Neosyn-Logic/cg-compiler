/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.generators.verilog;

import com.google.common.collect.ImmutableList;
import com.neosyn.core.transformations.AbstractTransformer;
import com.neosyn.core.transformations.BodyTransformation;
import com.neosyn.core.transformations.ExpressionTransformation;
import com.neosyn.core.transformations.ProcedureTransformation;
import com.neosyn.core.transformations.Transformation;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.ir.transform.PhiRemoval;
import com.neosyn.models.ir.transform.SSATransformation;
import com.neosyn.models.ir.transform.SSAVariableRenamer;
import com.neosyn.models.ir.transform.StoreOnceTransformation;
import com.neosyn.models.util.Void;
import com.neosyn.neosynide.internal.generators.verilog.transformations.LoopIndexMaker;
import com.neosyn.neosynide.internal.generators.verilog.transformations.ResizeExtractor;
import com.neosyn.neosynide.internal.generators.verilog.transformations.TruncationNarrower;
import com.neosyn.neosynide.internal.generators.verilog.transformations.VerilogTypeSystemAdapter;
import com.neosyn.neosynide.transformations.CodeCleaner;
import com.neosyn.neosynide.transformations.AddReadyAssignments;
import com.neosyn.neosynide.transformations.AddBufferedInputs;

/**
 * This class defines a transformer for Verilog.
 * 

 * 
 */
public class VerilogTransformer extends AbstractTransformer {

	@Override
	public Void caseEntity(Entity entity) {
		computeImportList(entity);
		return super.caseEntity(entity);
	}

	@Override
	protected Iterable<Transformation> getTransformations() {
		return ImmutableList.<Transformation> of( //
				// makes sure there is at most one store per variable per cycle
				new BodyTransformation(new StoreOnceTransformation()),
				//
				// cleans up code
				new ProcedureTransformation(new SSATransformation()), //
				new ProcedureTransformation(new CodeCleaner()), //
				new ProcedureTransformation(new PhiRemoval()), //
				new ProcedureTransformation(new SSAVariableRenamer()), //
				//
				// adapts IR type system to Verilog and add casts where necessary
				new ExpressionTransformation(new VerilogTypeSystemAdapter()), //
				// narrows arithmetic whose result is immediately truncated, so
				// the adapter's wide sign-extensions collapse to narrow ops
				new ExpressionTransformation(new TruncationNarrower()), //
				new ExpressionTransformation(new ResizeExtractor()), //
				//
				// adds index for "for" loops to copy arrays
				new ProcedureTransformation(new LoopIndexMaker()),
				//
				// transforms references/assignments to ready ports
				new AddReadyAssignments(),
				new AddBufferedInputs());
	}

}
