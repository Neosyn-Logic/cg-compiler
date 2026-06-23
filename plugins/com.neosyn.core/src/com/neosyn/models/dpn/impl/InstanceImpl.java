/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.dpn.impl;

import com.google.gson.JsonObject;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.util.EObjectContainmentEList;
import org.eclipse.emf.ecore.util.InternalEList;

import com.neosyn.models.dpn.Argument;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnPackage;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.graph.Graph;
import com.neosyn.models.graph.impl.VertexImpl;
import com.neosyn.models.ir.Var;

/**
 * <!-- begin-user-doc --><!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 * <li>{@link com.neosyn.models.dpn.impl.InstanceImpl#getArguments <em>Arguments</em>}</li>
 * <li>{@link com.neosyn.models.dpn.impl.InstanceImpl#getEntity <em>Entity</em>}</li>
 * <li>{@link com.neosyn.models.dpn.impl.InstanceImpl#getName <em>Name</em>}</li>
 * <li>{@link com.neosyn.models.dpn.impl.InstanceImpl#getProperties <em>Properties</em>}</li>
 * </ul>
 *
 * @generated
 */
public class InstanceImpl extends VertexImpl implements Instance {

	/**
	 * The cached value of the '{@link #getArguments() <em>Arguments</em>}' containment reference
	 * list. <!-- begin-user-doc -->
	 * 
	 * @see #getArguments()
	 * @ordered
	 */
	protected EList<Argument> arguments;

	/**
	 * The cached value of the '{@link #getEntity() <em>Entity</em>}' reference. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * 
	 * @see #getEntity()
	 * @generated
	 * @ordered
	 */
	protected Entity entity;

	/**
	 * The default value of the '{@link #getName() <em>Name</em>}' attribute. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * 
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected static final String NAME_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getName() <em>Name</em>}' attribute. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * 
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected String name = NAME_EDEFAULT;

	/**
	 * The default value of the '{@link #getProperties() <em>Properties</em>}' attribute. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @see #getProperties()
	 * @generated
	 * @ordered
	 */
	protected static final JsonObject PROPERTIES_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getProperties() <em>Properties</em>}' attribute. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @see #getProperties()
	 * @generated
	 * @ordered
	 */
	protected JsonObject properties = PROPERTIES_EDEFAULT;

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected InstanceImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Entity basicGetEntity() {
		return entity;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
		case DpnPackage.INSTANCE__ARGUMENTS:
			return getArguments();
		case DpnPackage.INSTANCE__ENTITY:
			if (resolve)
				return getEntity();
			return basicGetEntity();
		case DpnPackage.INSTANCE__NAME:
			return getName();
		case DpnPackage.INSTANCE__PROPERTIES:
			return getProperties();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID,
			NotificationChain msgs) {
		switch (featureID) {
		case DpnPackage.INSTANCE__ARGUMENTS:
			return ((InternalEList<?>) getArguments()).basicRemove(otherEnd, msgs);
		}
		return super.eInverseRemove(otherEnd, featureID, msgs);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
		case DpnPackage.INSTANCE__ARGUMENTS:
			return arguments != null && !arguments.isEmpty();
		case DpnPackage.INSTANCE__ENTITY:
			return entity != null;
		case DpnPackage.INSTANCE__NAME:
			return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
		case DpnPackage.INSTANCE__PROPERTIES:
			return PROPERTIES_EDEFAULT == null ? properties != null
					: !PROPERTIES_EDEFAULT.equals(properties);
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
		case DpnPackage.INSTANCE__ARGUMENTS:
			getArguments().clear();
			getArguments().addAll((Collection<? extends Argument>) newValue);
			return;
		case DpnPackage.INSTANCE__ENTITY:
			setEntity((Entity) newValue);
			return;
		case DpnPackage.INSTANCE__NAME:
			setName((String) newValue);
			return;
		case DpnPackage.INSTANCE__PROPERTIES:
			setProperties((JsonObject) newValue);
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
		return DpnPackage.Literals.INSTANCE;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
		case DpnPackage.INSTANCE__ARGUMENTS:
			getArguments().clear();
			return;
		case DpnPackage.INSTANCE__ENTITY:
			setEntity((Entity) null);
			return;
		case DpnPackage.INSTANCE__NAME:
			setName(NAME_EDEFAULT);
			return;
		case DpnPackage.INSTANCE__PROPERTIES:
			setProperties(PROPERTIES_EDEFAULT);
			return;
		}
		super.eUnset(featureID);
	}

	@Override
	public Argument getArgument(String name) {
		for (Argument argument : getArguments()) {
			Var variable = argument.getVariable();
			if (variable != null && variable.getName() != null && variable.getName().equals(name)) {
				return argument;
			}
		}
		return null;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public EList<Argument> getArguments() {
		if (arguments == null) {
			arguments = new EObjectContainmentEList<Argument>(Argument.class, this,
					DpnPackage.INSTANCE__ARGUMENTS);
		}
		return arguments;
	}

	@Override
	public DPN getDPN() {
		Graph graph = (Graph) eContainer();
		DPN dpn = (DPN) graph.eContainer();
		return dpn;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Entity getEntity() {
		if (entity != null && entity.eIsProxy()) {
			InternalEObject oldEntity = (InternalEObject) entity;
			entity = (Entity) eResolveProxy(oldEntity);
			if (entity != oldEntity) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE,
							DpnPackage.INSTANCE__ENTITY, oldEntity, entity));
			}
		}
		return entity;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public String getName() {
		return name;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public JsonObject getProperties() {
		return properties;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setEntity(Entity newEntity) {
		Entity oldEntity = entity;
		entity = newEntity;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, DpnPackage.INSTANCE__ENTITY,
					oldEntity, entity));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setName(String newName) {
		String oldName = name;
		name = newName;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, DpnPackage.INSTANCE__NAME,
					oldName, name));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setProperties(JsonObject newProperties) {
		JsonObject oldProperties = properties;
		properties = newProperties;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, DpnPackage.INSTANCE__PROPERTIES,
					oldProperties, properties));
	}

	@Override
	public String toString() {
		return getName() + ": " + getEntity();
	}

}
