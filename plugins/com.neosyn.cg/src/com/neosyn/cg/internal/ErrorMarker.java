/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal;

import static org.eclipse.xtext.validation.ValidationMessageAcceptor.INSIGNIFICANT_INDEX;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * This class defines an error marker. Like a diagnostic but more lightweight.
 * 
 */
final public class ErrorMarker {

	/** Xtext issue code, or null. Only a coded marker can BLOCK a CLI build; see below. */
	private final String code;

	private final EStructuralFeature feature;

	private final int index;

	private final String message;

	private final EObject source;

	public ErrorMarker(EObject source) {
		this(null, source, null, INSIGNIFICANT_INDEX);
	}

	public ErrorMarker(String message, EObject source) {
		this(message, source, null, INSIGNIFICANT_INDEX);
	}

	public ErrorMarker(String message, EObject source, EStructuralFeature feature) {
		this(message, source, feature, INSIGNIFICANT_INDEX);
	}

	public ErrorMarker(String message, EObject source, EStructuralFeature feature, int index) {
		this(message, source, feature, index, null);
	}

	/**
	 * Creates a marker carrying an Xtext ISSUE CODE.
	 *
	 * <p>Markers reach the user through {@code CgValidator.printErrors}, which forwards them to
	 * {@code acceptError}. A diagnostic with no issue code cannot match
	 * {@code ValidationHelper.BLOCKING_ISSUE_CODES}, so it is surfaced as a non-blocking
	 * {@code warning:} however severe it is, and the CLI carries on and builds the design anyway.
	 * That is how a connection the compiler had ALREADY detected as broken still produced a
	 * simulation that ran the broken graph and reported success (T24). Pass a code for any marker
	 * that must be able to stop a build.
	 */
	public ErrorMarker(String message, EObject source, EStructuralFeature feature, int index,
			String code) {
		this.message = message;
		this.source = source;
		this.feature = feature;
		this.index = index;
		this.code = code;
	}

	/**
	 * Creates a new marker with the given message and using the same source, feature, index as the
	 * given marker.
	 * 
	 * @param marker
	 * @param message
	 */
	public ErrorMarker(String message, ErrorMarker marker) {
		this.message = message;
		this.source = marker.getSource();
		this.feature = marker.getFeature();
		this.index = marker.getIndex();
		this.code = marker.getCode();
	}

	public String getCode() {
		return code;
	}

	public EStructuralFeature getFeature() {
		return feature;
	}

	public int getIndex() {
		return index;
	}

	public String getMessage() {
		return message;
	}

	public EObject getSource() {
		return source;
	}

}
