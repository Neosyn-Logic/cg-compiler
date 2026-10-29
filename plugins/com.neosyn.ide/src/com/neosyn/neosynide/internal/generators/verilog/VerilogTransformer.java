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
import com.neosyn.neosynide.transformations.ArrayWritePort;
import com.neosyn.neosynide.transformations.CodeCleaner;
import com.neosyn.neosynide.transformations.AddReadyAssignments;
import com.neosyn.neosynide.transformations.AddBufferedInputs;
import com.neosyn.neosynide.transformations.PortNameDisambiguator;

/**
 * Lowers each entity's IR into a Verilog-ready form by running an ORDERED transformation
 * pipeline (see {@link #getTransformations()}); the order is load-bearing:
 * <ol>
 * <li>{@code ArrayWritePort} — an array written once per action gets one registered write
 * port (RAM-mappable), BEFORE StoreOnce: its accesses then no longer trigger the whole-array
 * local copy.</li>
 * <li>{@code StoreOnce} — at most one store per variable per cycle.</li>
 * <li>SSA → {@code CodeCleaner} → {@code PhiRemoval} → {@code SSAVariableRenamer} — put the
 * IR in SSA to clean it, then leave SSA with unique names.</li>
 * <li>{@code VerilogTypeSystemAdapter} → {@code TruncationNarrower} → {@code ResizeExtractor}
 * — make the bit-accurate IR type system explicit for Verilog (casts/resizes), narrowing
 * truncated arithmetic before extracting resizes so wide sign-extensions collapse.</li>
 * <li>{@code LoopIndexMaker} — synthesise the index variables for array-copy for-loops.</li>
 * <li>{@code AddReadyAssignments} → {@code AddBufferedInputs} — the stream handshake: ready
 * assignments MUST run first because the buffered-input pass reads them to decide which
 * ports each action consumes.</li>
 * </ol>
 */
public class VerilogTransformer extends AbstractTransformer {

	@Override
	public Void caseEntity(Entity entity) {
		computeImportList(entity);
		// A state variable named like a port or one of its companions (`a_valid` beside port
		// `a`) would be declared twice in the module (TRAPS T141). Verilog keeps case, so only
		// exact clashes move.
		PortNameDisambiguator.disambiguate(entity, false);
		return super.caseEntity(entity);
	}

	@Override
	protected Iterable<Transformation> getTransformations() {
		return ImmutableList.<Transformation> of( //
				// an array written once per action gets one registered write port, so synthesis
				// can map it to RAM (TRAPS T45); must run before StoreOnce
				new ArrayWritePort(),
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
				// transforms references/assignments to ready ports. The Verilog backend
				// enables the direct held-producer handshake (see StreamLinkMarker): a
				// task-to-task sync-ready link uses the standard ready=1 form, not the
				// registered-producer ready=!valid pulse the bytecode/VHDL backends keep.
				new AddReadyAssignments(true),
				new AddBufferedInputs());
	}

}
