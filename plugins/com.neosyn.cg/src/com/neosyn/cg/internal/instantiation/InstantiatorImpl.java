/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.internal.instantiation;

import static com.neosyn.core.ICoreConstants.FILE_EXT_IR;
import static org.eclipse.emf.ecore.util.EcoreUtil.getURI;
import static com.neosyn.cg.cg.CgPackage.Literals.INST__ENTITY;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.neosyn.models.ir.impl.IrResourceFactoryImpl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.neosyn.cg.cg.Bundle;
import com.neosyn.cg.cg.CgEntity;
import com.neosyn.cg.cg.Inst;
import com.neosyn.cg.cg.Instantiable;
import com.neosyn.cg.cg.Module;
import com.neosyn.cg.cg.Network;
import com.neosyn.cg.cg.Task;
import com.neosyn.cg.cg.VarRef;
import com.neosyn.cg.cg.Variable;
import com.neosyn.cg.instantiation.IInstantiator;
import com.neosyn.cg.internal.CopyOf;
import com.neosyn.cg.internal.instantiation.properties.PropertiesService;
import com.neosyn.cg.internal.services.Typer;
import com.neosyn.models.dpn.DPN;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;
import com.neosyn.models.dpn.Instance;
import com.neosyn.models.dpn.Port;
import com.neosyn.models.ir.Type;
import com.neosyn.models.ir.TypeInt;
import com.neosyn.models.util.BuiltinPortTypeResolver;
import com.neosyn.models.util.Executable;
import com.neosyn.core.util.DebugLogger;
import static com.neosyn.core.util.DebugLogger.Category.IR;

/**
 * This class defines the default implementation of the instantiator.
 *

 *
 */
@Singleton
public class InstantiatorImpl implements IInstantiator {

	/** Convenience method for backward compatibility */
	private static void debugLog(String message) {
		DebugLogger.log(IR, message);
	}

	private InstantiatorData data;

	@Inject
	private EntityMapper entityMapper;

	@Inject
	private ExplicitConnector explicitConnector;

	@Inject
	private ImplicitConnector implicitConnector;

	@Inject
	private TopEntitiesLoader loader;

	@Inject
	private IQualifiedNameProvider qualifiedNameProvider;

	private ResourceSet resourceSet;

	/**
	 * URIs of entities currently being instantiated, i.e. on the active
	 * {@link #instantiate(EntityInfo, InstantiationContext)} call stack. Used to
	 * break instantiation cycles (e.g. two bundles importing each other) that
	 * would otherwise recurse until a {@link StackOverflowError}. Each URI is
	 * added on entry and removed in a finally on exit, so the set only ever holds
	 * the current recursion path and empties itself once the stack unwinds.
	 */
	private final Set<URI> instantiating = new java.util.HashSet<>();

	@Override
	public void clearData() {
		data = null;
	}

	@Override
	public Type computeType(Entity entity, EObject eObject) {
		return new Typer(this).getType(entity, eObject);
	}

	/**
	 * Creates a new connection maker and connects the given network. Visits inner tasks first, and
	 * then connect statements.
	 * 
	 * @param network
	 * @param dpn
	 */
	private void connect(Network network, DPN dpn) {
		Multimap<EObject, Port> portMap = LinkedHashMultimap.create();
		portMap.putAll(dpn, dpn.getOutputs());
		for (Instance instance : dpn.getInstances()) {
			Entity entity = instance.getEntity();
			if (entity != null) {
				portMap.putAll(instance, entity.getInputs());
			}
		}

		implicitConnector.connect(portMap, network, dpn);
		explicitConnector.connect(portMap, network, dpn);
	}

	@Override
	public Object evaluate(Entity entity, EObject eObject) {
		return new Evaluator(this).getValue(entity, eObject);
	}

	@Override
	public int evaluateInt(Entity entity, EObject eObject) {
		return new Evaluator(this).getIntValue(entity, eObject);
	}

	@Override
	public void forEachMapping(CgEntity cxEntity, Executable<Entity> executable) {
		Objects.requireNonNull(cxEntity, "cxEntity must not be null in forEachMapping");

		Iterable<Entity> entities = data.getEntities(cxEntity);
		for (Entity entity : entities) {
			executable.exec(entity);
		}
	}

	@Override
	public CgEntity getEntity(URI uri) {
		if (data == null) {
			return null;
		}
		return data.getCxEntity(uri);
	}

	@Override
	public <T extends EObject> T getMapping(Entity entity, Object cxObj) {
		T result = data.getMapping(entity, cxObj);
		if (result == null && cxObj instanceof EObject) {
			// Cross-file fall-through: when Counter.cg references `count_t`
			// defined in sibling Definitions.cg, the Bundle holding the typedef
			// may not have been instantiated yet — `updateDirect(module)` only
			// processes the current module's entities and `clearData()` runs per
			// file. Lazily instantiate the containing Bundle the first time it's
			// queried. (Requires the proxy to already be resolved; the caller is
			// responsible for pre-loading sibling .cg files into the resource
			// set so EReference dereferencing returns a non-proxy object.)
			Bundle bundle = EcoreUtil2.getContainerOfType((EObject) cxObj, Bundle.class);
			if (bundle != null && bundle != cxObj
					&& !data.getEntities().containsKey(bundle)) {
				updateEntity(bundle);
				result = data.getMapping(entity, cxObj);
			}
		}
		return result;
	}

	@Override
	public Port getPort(Entity entity, VarRef refOrCopyOfRef) {
		final VarRef ref = CopyOf.getOriginal(refOrCopyOfRef);

		// first try port in named task/network
		Port port = getMapping(entity, ref.getVariable());
		if (port == null) {
			// otherwise get mapping of reference (anonymous task)
			port = getMapping(entity, ref);
		}

		// If still null, check for cross-task port reference (processor.input_val)
		// This handles cases where one inline task references another inline task's port
		if (port == null && ref.getObjects().size() >= 2) {
			port = getCrossTaskPort(entity, ref);
		}

		// If still null, check for built-in entity port reference
		// This handles cases like line1.q.read() where line1 is a built-in entity instance
		if (port == null && ref.getObjects().size() >= 2) {
			port = getBuiltinPort(entity, ref);
		}

		return port;
	}

	@Override
	public void putStructPort(Entity entity, Variable portVar, String fieldName, Port port) {
		data.putStructPort(entity, portVar, fieldName, port);
	}

	@Override
	public java.util.Map<String, Port> getStructPortFields(Entity entity, VarRef refOrCopyOfRef) {
		final VarRef ref = CopyOf.getOriginal(refOrCopyOfRef);
		// Same-entity lookup suffices: a cross-task struct port reference is
		// wired by ImplicitConnector#visitPort into local field ports
		// registered on the *referencing* entity under the port variable
		// (mirroring how scalar cross-task ports get a local port via getPort).
		return data.getStructPortFields(entity, ref.getVariable());
	}

	@Override
	public void putStructStateField(Entity entity, Variable structVar, String fieldName,
			com.neosyn.models.ir.Var var) {
		data.putStructStateField(entity, structVar, fieldName, var);
	}

	@Override
	public java.util.Map<Variable, java.util.Map<String, com.neosyn.models.ir.Var>>
			getStructStateVars(Entity entity) {
		return data.getStructStateVars(entity);
	}

	/**
	 * Looks up a port from another inline task within the same network.
	 * For references like processor.input_val where processor is an inline task.
	 *
	 * @param entity the entity being compiled (e.g., the tester task)
	 * @param ref the VarRef containing [instance, port] objects
	 * @return the IR Port, or null if not a cross-task port reference
	 */
	private Port getCrossTaskPort(Entity entity, VarRef ref) {
		List<com.neosyn.cg.cg.Named> objects = ref.getObjects();
		if (objects.size() < 2) {
			return null;
		}

		// First object should be an instance (e.g., processor)
		com.neosyn.cg.cg.Named first = objects.get(0);
		if (!(first instanceof Inst)) {
			return null;
		}

		Inst inst = (Inst) first;

		// Check if this is an inline task (has embedded task definition)
		if (inst.getTask() == null) {
			return null;  // Not an inline task, might be built-in or external
		}

		String instanceName = inst.getName();

		// Second object should be the port variable
		com.neosyn.cg.cg.Named second = objects.get(1);
		if (!(second instanceof Variable)) {
			return null;
		}
		Variable portVar = (Variable) second;

		// Find the Entity for the target inline task
		// The entity name pattern is: parentDpn_instanceName
		String entityName = entity.getName();
		String parentDpnName = extractParentDpnName(entityName);

		if (parentDpnName == null) {
			return null;
		}

		// Build the target entity name: parentDpn_instanceName
		String targetEntityName = parentDpnName + "_" + instanceName;

		// Find the target entity in the map of all entities
		Entity targetEntity = data.getEntities().values().stream()
			.filter(e -> targetEntityName.equals(e.getName()))
			.findFirst()
			.orElse(null);

		if (targetEntity != null) {
			// Look up the port mapping using the target entity
			Port targetPort = getMapping(targetEntity, portVar);
			if (targetPort != null) {
				debugLog("[Instantiator] Found cross-task port: " + instanceName + "." + portVar.getName() +
					" on entity " + targetEntityName);
				return targetPort;
			}
		}

		return null;
	}

	/**
	 * Looks up a port from a built-in entity instance.
	 * For references like line1.q.read(), this finds the port on the built-in SinglePortRAM.
	 *
	 * @param entity the entity being compiled (e.g., the inline task)
	 * @param ref the VarRef containing [instance, port] objects
	 * @return the IR Port, or null if not a built-in port reference
	 */
	private Port getBuiltinPort(Entity entity, VarRef ref) {
		List<com.neosyn.cg.cg.Named> objects = ref.getObjects();
		if (objects.size() < 2) {
			return null;
		}

		// First object should be an instance (e.g., line1)
		com.neosyn.cg.cg.Named first = objects.get(0);
		if (!(first instanceof Inst)) {
			return null;
		}

		Inst inst = (Inst) first;
		String instanceName = inst.getName();

		// Second object should be the port (e.g., q)
		com.neosyn.cg.cg.Named second = objects.get(1);
		String portName = second.getName();

		// Build the port key
		String portKey = instanceName + "." + portName;

		// Find the parent DPN from the entity name
		// e.g., "support.LineBuffer_feed_line2" -> parent is "support.LineBuffer"
		String entityName = entity.getName();
		String parentDpnName = extractParentDpnName(entityName);

		if (parentDpnName != null) {
			// Look for the parent DPN in our data
			Entity parentDpn = findDpnByName(parentDpnName);
			if (parentDpn != null) {
				Port builtinPort = data.getBuiltinPortMapping(parentDpn, portKey);
				if (builtinPort != null) {
					debugLog("[Instantiator] Found built-in port: " + portKey + " on parent DPN " + parentDpnName);
					return builtinPort;
				}
			}
		}

		return null;
	}

	/**
	 * Extracts the parent DPN name from an inline task's entity name.
	 * e.g., "support.LineBuffer_feed_line2" -> "support.LineBuffer"
	 *
	 * @param entityName the inline task's full entity name
	 * @return the parent DPN name, or null if not found
	 */
	private String extractParentDpnName(String entityName) {
		int lastUnderscore = entityName.lastIndexOf('_');
		if (lastUnderscore <= 0) {
			return null;
		}
		return entityName.substring(0, lastUnderscore);
	}

	/**
	 * Finds a DPN entity by its fully qualified name.
	 *
	 * @param name the fully qualified name
	 * @return the Entity, or null if not found
	 */
	private Entity findDpnByName(String name) {
		// Check in mapEntities
		for (java.util.Map.Entry<CgEntity, Entity> entry : data.getEntities().entrySet()) {
			Entity entity = entry.getValue();
			if (entity != null && name.equals(entity.getName())) {
				return entity;
			}
		}
		return null;
	}

	/**
	 * Instantiates a Cx entity based on the given info and instantiation context.
	 * 
	 * @param info
	 *            entity info (URI of IR, name, reference to original Cx entity)
	 * @param ctx
	 *            instantiation context (hierarchical path, inherited properties). May be
	 *            <code>null</code>.
	 * @return a specialized IR entity
	 */
	private Entity instantiate(EntityInfo info, InstantiationContext ctx) {
		CgEntity cxEntity = info.getCxEntity();

		// Check for null URI (can happen with unresolved proxy entities)
		if (info.getURI() == null) {
			debugLog("[Instantiator] Skipping entity with null URI: " + info.getName());
			return null;
		}

		// Break instantiation cycles: if this exact entity is already being
		// instantiated higher up the stack (e.g. two bundles that import each
		// other), bail out instead of recursing into a StackOverflowError. The
		// cyclic dependency itself is reported as a validation error by
		// CgValidator#checkNoCyclicImport; here we just keep the generator alive.
		if (!instantiating.add(info.getURI())) {
			debugLog("[Instantiator] Cyclic instantiation detected, skipping re-entry of: " + info.getURI());
			return null;
		}
		try {
		// load bundles first (if necessary)
		Iterable<Bundle> bundles = loader.loadBundles(resourceSet, cxEntity);
		for (Bundle bundle : bundles) {
			updateEntity(bundle);
		}

		// map entity and update mapping
		Entity entity = entityMapper.doSwitch(cxEntity);
		if (entity == null) {
			// happens with unresolved references to instantiable entities
			return null;
		}
		data.updateMapping(cxEntity, entity, ctx);

		// add to resource
		// Ensure the ResourceSet has the IR resource factory registered
		Map<String, Object> extMap = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
		if (!extMap.containsKey(FILE_EXT_IR)) {
			debugLog("[Instantiator] Registering IrResourceFactoryImpl for extension: " + FILE_EXT_IR);
			extMap.put(FILE_EXT_IR, new IrResourceFactoryImpl());
		}

		Resource resource = resourceSet.getResource(info.getURI(), false);
		if (resource == null) {
			debugLog("[Instantiator] Creating new resource for URI: " + info.getURI());
			resource = resourceSet.createResource(info.getURI());
		}
		if (resource == null) {
			debugLog("[Instantiator] Failed to create resource for URI: " + info.getURI());
			return entity; // Return entity anyway, just not persisted
		}
		debugLog("[Instantiator] Resource created/found: " + resource.getURI());
		if (!resource.getContents().isEmpty()) {
			// IR entity already created during this build, discard
			// happens when revalidating the instantiating parent of a specialized entity
			resource.getContents().clear();
		}
		resource.getContents().add(entity);

		// configure entity
		entityMapper.configureEntity(entity, info, ctx);

		// instantiate network
		if (cxEntity instanceof Network) {
			instantiate((Network) cxEntity, entity, ctx);
		}

		return entity;
		} finally {
			instantiating.remove(info.getURI());
		}
	}

	/**
	 * Instantiates the given network and its instances recursively, and connects the network.
	 * 
	 * @param network
	 *            the network
	 * @param ctx
	 *            instantiation context (hierarchical path, inherited properties)
	 */
	private void instantiate(Network network, Entity entity, InstantiationContext ctx) {
		if (ctx == null) {
			// create a context for this network to serve as a parent context
			// only useful for specialized instances
			ctx = new InstantiationContext(entity.getName());
		}

		DPN dpn = (DPN) entity;

		// Debug: Log all instances in the network
		int instCount = 0;
		for (Inst i : network.getInstances()) {
			instCount++;
			debugLog("[Instantiator] Network " + network.getName() + " has Inst[" + instCount + "]: " + i.getName() +
				" (entity=" + (i.getEntity() != null ? i.getEntity().getName() : "null") +
				", task=" + (i.getTask() != null ? "INLINE" : "null") + ")");
		}
		debugLog("[Instantiator] Network " + network.getName() + " total instances: " + instCount);

		int processedCount = 0;
		for (Inst inst : network.getInstances()) {
			processedCount++;
			debugLog("[Instantiator] Processing Inst[" + processedCount + "/" + instCount + "]: " + inst.getName());

			try {
				Instance instance = DpnFactory.eINSTANCE.createInstance(inst.getName());
				data.putMapping(dpn, inst, instance);
				dpn.add(instance);
				debugLog("[Instantiator]   Created Instance, added to DPN. DPN now has " + dpn.getInstances().size() + " instances");

				InstantiationContext subCtx = new InstantiationContext(this, ctx, inst, instance);
				debugLog("[Instantiator]   Created subCtx with name: " + subCtx.getName());

				EntityInfo info = entityMapper.createEntityInfo(subCtx);
				debugLog("[Instantiator]   Created EntityInfo: name=" + info.getName() +
					", uri=" + info.getURI() + ", specialized=" + info.isSpecialized() +
					", cxEntity=" + (info.getCxEntity() != null ? info.getCxEntity().getClass().getSimpleName() : "null"));

			// Get the actual entity type name for ALL external entity references
			// This is critical because cross-resource references get lost during IR serialization.
			// The bytecode generator needs entityType to look up the correct class name.
			// IMPORTANT: We must check the actual entity type from inst.getEntity(), not info.getName()
			// because info.getName() returns the specialized name (e.g., "support.LineBuffer_line1")
			// while we need the entity type (e.g., "std.mem.SinglePortRAM" or "sobel.SobelFilter")
			Instantiable instantiable = inst.getEntity();
			String actualEntityTypeName = null;

			// Check if instantiable is null OR an unresolved proxy
			boolean isUnresolved = (instantiable == null) ||
				(instantiable instanceof org.eclipse.emf.ecore.EObject &&
				 ((org.eclipse.emf.ecore.EObject) instantiable).eIsProxy());

			debugLog("[Instantiator] Instance " + inst.getName() + ": instantiable=" +
				(instantiable != null ? instantiable.getClass().getSimpleName() : "null") +
				", isProxy=" + (instantiable instanceof org.eclipse.emf.ecore.EObject ? ((org.eclipse.emf.ecore.EObject) instantiable).eIsProxy() : "N/A") +
				", task=" + (inst.getTask() != null ? "INLINE" : "null"));

			// For external entity references (not inline tasks), get the fully qualified name
			// inst.getTask() != null means it's an INLINE task (code defined inside network)
			// inst.getTask() == null means it's an EXTERNAL reference (Task, Network, Bundle)
			if (instantiable != null && !isUnresolved && inst.getTask() == null) {
				// Get the fully qualified name of the referenced entity
				QualifiedName qn = qualifiedNameProvider.getFullyQualifiedName(instantiable);
				actualEntityTypeName = qn != null ? qn.toString() : null;
				debugLog("[Instantiator]   Resolved entity type: " + actualEntityTypeName);
			} else if (isUnresolved && inst.getTask() == null) {
				// Entity reference is unresolved (e.g., std.mem.SinglePortRAM in standalone mode)
				// Extract the type name from the AST node model
				debugLog("[Instantiator]   Unresolved reference, extracting from node model...");
				actualEntityTypeName = getEntityTypeFromNodeModel(inst);
				if (actualEntityTypeName != null) {
					debugLog("[Instantiator] Extracted entityType from node model: " + actualEntityTypeName + " for instance " + inst.getName());
				} else {
					debugLog("[Instantiator] WARNING: Could not extract entityType from node model for " + inst.getName());
				}
			}

			// ALWAYS store entityType for external entity references
			// This covers both built-in entities AND cross-file project entities
			if (actualEntityTypeName != null) {
				com.google.gson.JsonObject props = instance.getProperties();
				if (props == null) {
					props = new com.google.gson.JsonObject();
				}
				props.addProperty("entityType", actualEntityTypeName);
				instance.setProperties(props);
				debugLog("[Instantiator] Stored entityType: " + actualEntityTypeName + " for instance " + inst.getName());
			}

			// For built-in entities, load pre-built IR from classpath to get port definitions
			// This is needed so inline tasks can resolve port references like line1.q.read()
			if (actualEntityTypeName != null && BuiltinPortTypeResolver.isBuiltinType(actualEntityTypeName)) {
				debugLog("[Instantiator] Built-in entity detected: " + actualEntityTypeName);

				// Load the built-in entity IR from classpath (uses consolidated loader)
				Entity builtinEntity = BuiltinPortTypeResolver.loadBuiltinEntity(actualEntityTypeName);
				if (builtinEntity != null) {
					debugLog("[Instantiator]   Loaded: " + builtinEntity.getName() + " (" +
						builtinEntity.getInputs().size() + " in, " + builtinEntity.getOutputs().size() + " out)");

					// Set the entity on the instance so port references can be resolved
					instance.setEntity(builtinEntity);

					// Create port mappings from Cx AST to IR ports
					// This enables inline tasks to resolve references like line1.q.read()
					createBuiltinPortMappings(dpn, inst, instance, builtinEntity);

					debugLog("[Instantiator]   Created port mappings for built-in entity");

					// Translate properties for the built-in entity instance
					// Use the parent DPN for constant resolution (e.g., LINE_WIDTH from BufferConstants)
					new PropertiesService(this).translateProperties(inst, instance, dpn);
					debugLog("[Instantiator]   Translated properties for built-in entity: " + instance.getProperties());
				} else {
					debugLog("[Instantiator]   WARNING: Could not load built-in IR for " + actualEntityTypeName);
				}

				// Continue to next instance - don't generate IR for built-in entities
				continue;
			}

			Entity subEntity;
			if (info.isSpecialized()) {
				// specialized: instantiate with sub-context (inline tasks go here)
				debugLog("[Instantiator]   SPECIALIZED path - calling instantiate(info, subCtx)");
				subEntity = instantiate(info, subCtx);
				debugLog("[Instantiator]   Specialized instantiate returned: " + (subEntity != null ? subEntity.getName() : "null"));
			} else {
				// not specialized: remove sub-context (not needed anymore)
				debugLog("[Instantiator]   NOT SPECIALIZED path");
				subCtx.delete();

				// try to look up existing mapping
				subEntity = data.getIrEntity(info.getCxEntity());
				debugLog("[Instantiator]   Existing mapping lookup returned: " + (subEntity != null ? subEntity.getName() : "null"));
				if (subEntity == null) {
					// no existing mapping, transform Cx entity to IR
					// not specialized => no need for instantiation context (1:1 mapping)
					debugLog("[Instantiator]   No existing mapping, calling instantiate(info, null)");
					subEntity = instantiate(info, null);
					debugLog("[Instantiator]   instantiate returned: " + (subEntity != null ? subEntity.getName() : "null"));
				}
			}

			// happens with unresolved references to instantiable entities (cross-file dependencies)
			if (subEntity == null) {
				debugLog("[Instantiator]   subEntity is NULL - possible unresolved reference");
				// Store entity type name for bytecode generation even when entity can't be resolved
				// Use actualEntityTypeName computed above from inst.getEntity()
				if (actualEntityTypeName != null) {
					debugLog("[Instantiator] Cross-file entity detected: " + actualEntityTypeName + " for instance " + inst.getName());
					com.google.gson.JsonObject props = instance.getProperties();
					if (props == null) {
						props = new com.google.gson.JsonObject();
					}
					props.addProperty("entityType", actualEntityTypeName);
					instance.setProperties(props);
					new PropertiesService(this).translateProperties(inst, instance);
				}
				continue;
			}

			instance.setEntity(subEntity);
			debugLog("[Instantiator]   Set entity on instance: " + (subEntity != null ? subEntity.getName() : "null"));

			// set properties. For anonymous tasks, use the task's properties for the instance
			new PropertiesService(this).translateProperties(inst, instance);
			debugLog("[Instantiator]   Completed processing Inst: " + inst.getName());

			} catch (Exception e) {
				debugLog("[Instantiator] ERROR processing Inst " + inst.getName() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}

		debugLog("[Instantiator] Network instantiation complete. DPN has " + dpn.getInstances().size() + " instances");
		connect(network, dpn);
	}

	@Override
	public boolean isSpecialized(URI uri) {
		return data.isSpecialized(uri);
	}

	@Override
	public void putMapping(Entity entity, Object cxObj, EObject irObj) {
		data.putMapping(entity, cxObj, irObj);
	}

	@Override
	public void update(Module module) {
		debugLog("[Instantiator] update() called");
		resourceSet = module.eResource().getResourceSet();
		debugLog("[Instantiator] ResourceSet: " + resourceSet);

		try {
			Iterable<CgEntity> entities;
			if (data == null) {
				debugLog("[Instantiator] data is null, creating new InstantiatorData and loading top entities");
				data = new InstantiatorData();
				entities = loader.loadTopEntities(resourceSet);
				// Count entities for logging
				int count = 0;
				for (CgEntity e : entities) {
					count++;
					debugLog("[Instantiator]   Top entity: " + e.getName() + " (" + e.getClass().getSimpleName() + ")");
				}
				debugLog("[Instantiator] loadTopEntities returned " + count + " entities");
				// Re-get the iterable since we consumed it
				entities = loader.loadTopEntities(resourceSet);
			} else {
				debugLog("[Instantiator] data exists, using module.getEntities()");
				entities = module.getEntities();
				for (CgEntity e : entities) {
					debugLog("[Instantiator]   Module entity: " + e.getName() + " (" + e.getClass().getSimpleName() + ")");
				}
				entities = module.getEntities();
			}

			for (CgEntity cxEntity : entities) {
				debugLog("[Instantiator] Calling updateEntity for: " + cxEntity.getName());
				updateEntity(cxEntity);
			}
			debugLog("[Instantiator] update() completed");
		} finally {
			resourceSet = null;
		}
	}

	@Override
	public void updateDirect(Module module) {
		debugLog("[Instantiator] updateDirect() called - standalone mode");
		resourceSet = module.eResource().getResourceSet();

		try {
			// Always create fresh data for direct updates
			if (data == null) {
				data = new InstantiatorData();
			}

			// Use module's entities directly instead of loading from index
			Iterable<CgEntity> entities = module.getEntities();
			debugLog("[Instantiator] updateDirect: Processing " + module.getEntities().size() + " entities from module");

			for (CgEntity cxEntity : entities) {
				debugLog("[Instantiator] updateDirect: Processing entity " + cxEntity.getName());
				updateEntity(cxEntity);
			}
			debugLog("[Instantiator] updateDirect() completed");
		} finally {
			resourceSet = null;
		}
	}

	private void updateEntity(CgEntity cxEntity) {
		debugLog("[Instantiator] updateEntity: " + cxEntity.getName());

		// Check if entity has a resource before calling getURI
		if (cxEntity.eResource() == null) {
			debugLog("[Instantiator]   Entity has null eResource, skipping lookup");
			// Proceed without checking for old entity
			EntityInfo info = entityMapper.createEntityInfo(cxEntity);
			debugLog("[Instantiator]   EntityInfo name: " + info.getName());
			debugLog("[Instantiator]   EntityInfo URI: " + info.getURI());
			instantiate(info, null);
			return;
		}

		CgEntity oldEntity = data.getCxEntity(getURI(cxEntity));
		debugLog("[Instantiator]   oldEntity: " + (oldEntity != null ? oldEntity.getName() : "null"));
		debugLog("[Instantiator]   cxEntity == oldEntity: " + (cxEntity == oldEntity));
		if (cxEntity == oldEntity) {
			debugLog("[Instantiator]   Skipping (same entity)");
			return;
		}

		// look up specialization info using previous entity
		// do this now, before removeSpecialized removes the map
		Map<InstantiationContext, Entity> map = data.getSpecialization(oldEntity);

		// removes specialized info, returns iterable of contexts to delete later
		Iterable<InstantiationContext> contexts = data.removeSpecialized(oldEntity);

		// A non-instantiable entity (e.g. a bundle) cannot be specialized, so it
		// must always be (re)created via the base instantiate path — never via
		// updateSpecialized, whose cast to Instantiable would crash on a stale
		// specialization map keyed under such an entity.
		if (map == null || !(cxEntity instanceof Instantiable)) {
			// no previous record of specialization info exists
			debugLog("[Instantiator]   Creating EntityInfo...");
			EntityInfo info = entityMapper.createEntityInfo(cxEntity);
			debugLog("[Instantiator]   EntityInfo name: " + info.getName());
			debugLog("[Instantiator]   EntityInfo URI: " + info.getURI());
			debugLog("[Instantiator]   EntityInfo isSpecialized: " + info.isSpecialized());
			debugLog("[Instantiator]   Calling instantiate()...");
			Entity irEntity = instantiate(info, null);
			debugLog("[Instantiator]   instantiate() returned: " + (irEntity != null ? irEntity.getName() : "null"));
			if (irEntity != null) {
				debugLog("[Instantiator]   IR entity resource: " + (irEntity.eResource() != null ? irEntity.eResource().getURI() : "null"));
			}
		} else {
			// update specialization info
			updateSpecialized(map, cxEntity);
		}

		// clean up: deletes old contexts
		for (InstantiationContext ctx : contexts) {
			ctx.delete();
		}
	}

	private void updateSpecialized(Map<InstantiationContext, Entity> map, CgEntity cxEntity) {
		// copy context set because map is modified by instantiation
		Set<InstantiationContext> contexts = ImmutableSet.copyOf(map.keySet());
		for (InstantiationContext ctx : contexts) {
			InstantiationContext parent = (InstantiationContext) ctx.getParent();

			Inst inst = ctx.getInst();
			Instance instance = ctx.getInstance();
			InstantiationContext newCtx = new InstantiationContext(this, parent, inst, instance);

			// update inst's entity to the latest version
			inst.setEntity((Instantiable) cxEntity);

			// instantiate
			EntityInfo info = entityMapper.createEntityInfo(newCtx);
			Entity entity = instantiate(info, newCtx);
			instance.setEntity(entity);
		}
	}

	/**
	 * Creates port mappings for a built-in entity instance.
	 * This maps the Cx AST port references to the IR ports from the built-in entity.
	 *
	 * @param dpn the parent DPN (network)
	 * @param inst the Cx instance declaration
	 * @param instance the IR Instance object
	 * @param builtinEntity the loaded built-in entity IR with port definitions
	 */
	private void createBuiltinPortMappings(DPN dpn, Inst inst, Instance instance, Entity builtinEntity) {
		// For built-in entities, we need to create mappings so that inline tasks
		// can resolve port references like line1.q.read()
		//
		// Uses BuiltinPortTypeResolver (single source of truth) to resolve parameterized types
		debugLog("[Instantiator] createBuiltinPortMappings for " + inst.getName());

		// Store the built-in entity's ports with resolved types
		for (Port irPort : builtinEntity.getInputs()) {
			String portKey = inst.getName() + "." + irPort.getName();
			Port resolvedPort = BuiltinPortTypeResolver.createResolvedPort(instance, irPort);
			debugLog("[Instantiator]   Input: " + portKey + " (size=" + getPortSize(resolvedPort) + ")");
			data.putBuiltinPortMapping(dpn, portKey, resolvedPort);
		}

		for (Port irPort : builtinEntity.getOutputs()) {
			String portKey = inst.getName() + "." + irPort.getName();
			Port resolvedPort = BuiltinPortTypeResolver.createResolvedPort(instance, irPort);
			debugLog("[Instantiator]   Output: " + portKey + " (size=" + getPortSize(resolvedPort) + ")");
			data.putBuiltinPortMapping(dpn, portKey, resolvedPort);
		}
	}

	private String getPortSize(Port port) {
		Type type = port.getType();
		if (type instanceof TypeInt) {
			return String.valueOf(((TypeInt) type).getSize());
		}
		return "N/A";
	}

	/**
	 * Extracts the entity type name from the AST node model for unresolved references.
	 * This is used when inst.getEntity() returns null (e.g., for built-in entities
	 * like std.mem.SinglePortRAM in standalone LSP mode).
	 *
	 * @param inst the instance with an unresolved entity reference
	 * @return the entity type name (e.g., "std.mem.SinglePortRAM"), or null if not found
	 */
	private String getEntityTypeFromNodeModel(Inst inst) {
		try {
			// Get the nodes for the 'entity' feature of the Inst
			List<INode> nodes = NodeModelUtils.findNodesForFeature(inst, INST__ENTITY);
			if (nodes.isEmpty()) {
				debugLog("[Instantiator] No nodes found for entity feature in Inst: " + inst.getName());
				return null;
			}

			// Build the qualified name from all the nodes
			// For "std.mem.SinglePortRAM", we get nodes for each segment: "std", "mem", "SinglePortRAM"
			StringBuilder sb = new StringBuilder();
			for (INode node : nodes) {
				String text = NodeModelUtils.getTokenText(node);
				if (text != null && !text.isEmpty()) {
					if (sb.length() > 0) {
						sb.append(".");
					}
					sb.append(text.trim());
				}
			}

			String result = sb.toString();
			debugLog("[Instantiator] Extracted entity type from nodes: " + result + " (from " + nodes.size() + " nodes)");
			return result.isEmpty() ? null : result;
		} catch (Exception e) {
			debugLog("[Instantiator] Error extracting entity type from node model: " + e.getMessage());
			return null;
		}
	}

}
