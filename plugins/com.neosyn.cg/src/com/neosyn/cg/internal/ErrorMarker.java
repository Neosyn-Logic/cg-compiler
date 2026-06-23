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

 * 
 */
final public class ErrorMarker {

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
		this.message = message;
		this.source = source;
		this.feature = feature;
		this.index = index;
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
