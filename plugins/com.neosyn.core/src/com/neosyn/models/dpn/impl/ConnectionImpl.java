/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn.impl;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;

import com.neosyn.models.dpn.Connection;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnPackage;
import com.neosyn.models.dpn.Endpoint;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.graph.Graph;
import com.neosyn.models.graph.Vertex;
import com.neosyn.models.graph.impl.EdgeImpl;

/**
 * This class represents a connection in a network. A connection can have a number of attributes,
 * that can be types or expressions.
 * 

 * @author Herve Yviquel
 * @generated
 */
public class ConnectionImpl extends EdgeImpl implements Connection {

	/**
	 * The cached value of the '{@link #getSourcePort() <em>Source Port</em>}' reference. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @see #getSourcePort()
	 * @generated
	 * @ordered
	 */
	protected Port sourcePort;

	/**
	 * The cached value of the '{@link #getTargetPort() <em>Target Port</em>}' reference. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @see #getTargetPort()
	 * @generated
	 * @ordered
	 */
	protected Port targetPort;

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected ConnectionImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Port basicGetSourcePort() {
		return sourcePort;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Port basicGetTargetPort() {
		return targetPort;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
		case DpnPackage.CONNECTION__SOURCE_PORT:
			if (resolve)
				return getSourcePort();
			return basicGetSourcePort();
		case DpnPackage.CONNECTION__TARGET_PORT:
			if (resolve)
				return getTargetPort();
			return basicGetTargetPort();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
		case DpnPackage.CONNECTION__SOURCE_PORT:
			return sourcePort != null;
		case DpnPackage.CONNECTION__TARGET_PORT:
			return targetPort != null;
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
		case DpnPackage.CONNECTION__SOURCE_PORT:
			setSourcePort((Port) newValue);
			return;
		case DpnPackage.CONNECTION__TARGET_PORT:
			setTargetPort((Port) newValue);
			return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return DpnPackage.Literals.CONNECTION;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
		case DpnPackage.CONNECTION__SOURCE_PORT:
			setSourcePort((Port) null);
			return;
		case DpnPackage.CONNECTION__TARGET_PORT:
			setTargetPort((Port) null);
			return;
		}
		super.eUnset(featureID);
	}

	private Endpoint getEndpoint(Vertex vertex, Port port) {
		Graph graph = (Graph) eContainer();
		DPN dpn = (DPN) graph.eContainer();
		if (vertex == dpn.getVertex()) {
			return new Endpoint(dpn, port);
		} else {
			Instance instance = (Instance) vertex;
			return new Endpoint(instance, port);
		}
	}

	@Override
	public Endpoint getSourceEndpoint() {
		return getEndpoint(getSource(), getSourcePort());
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Port getSourcePort() {
		if (sourcePort != null && sourcePort.eIsProxy()) {
			InternalEObject oldSourcePort = (InternalEObject) sourcePort;
			sourcePort = (Port) eResolveProxy(oldSourcePort);
			if (sourcePort != oldSourcePort) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE,
							DpnPackage.CONNECTION__SOURCE_PORT, oldSourcePort, sourcePort));
			}
		}
		return sourcePort;
	}

	@Override
	public Endpoint getTargetEndpoint() {
		return getEndpoint(getTarget(), getTargetPort());
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Port getTargetPort() {
		if (targetPort != null && targetPort.eIsProxy()) {
			InternalEObject oldTargetPort = (InternalEObject) targetPort;
			targetPort = (Port) eResolveProxy(oldTargetPort);
			if (targetPort != oldTargetPort) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE,
							DpnPackage.CONNECTION__TARGET_PORT, oldTargetPort, targetPort));
			}
		}
		return targetPort;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setSourcePort(Port newSourcePort) {
		Port oldSourcePort = sourcePort;
		sourcePort = newSourcePort;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET,
					DpnPackage.CONNECTION__SOURCE_PORT, oldSourcePort, sourcePort));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setTargetPort(Port newTargetPort) {
		Port oldTargetPort = targetPort;
		targetPort = newTargetPort;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET,
					DpnPackage.CONNECTION__TARGET_PORT, oldTargetPort, targetPort));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(getSource());
		if (getSourcePort() != null) {
			builder.append('.');
			builder.append(getSourcePort().getName());
		}
		builder.append(" --> ");
		builder.append(getTarget());
		if (getTargetPort() != null) {
			builder.append('.');
			builder.append(getTargetPort().getName());
		}
		return builder.toString();
	}

}
