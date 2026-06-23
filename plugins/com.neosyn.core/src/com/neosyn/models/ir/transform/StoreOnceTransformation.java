/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.models.ir.transform;

import static com.neosyn.models.util.SwitchUtil.DONE;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.neosyn.models.ir.Block;
import com.neosyn.models.ir.BlockBasic;
import com.neosyn.models.ir.BlockWhile;
import com.neosyn.models.ir.Def;
import com.neosyn.models.ir.InstAssign;
import com.neosyn.models.ir.InstLoad;
import com.neosyn.models.ir.InstStore;
import com.neosyn.models.ir.IrFactory;
import com.neosyn.models.ir.Procedure;
import com.neosyn.models.ir.Use;
import com.neosyn.models.ir.Var;
import com.neosyn.models.ir.util.IrUtil;
import com.neosyn.models.util.Void;

/**
 * This class defines an actor transformation that transforms code so that at most it contains at
 * most one store per variable per cycle.
 * 

 * 
 */
public class StoreOnceTransformation extends AbstractIrVisitor {

	/**
	 * This class updates loads and stores. Loads of scalars are removed, stores of scalars that
	 * need to be transformed are replaced by assigns. Loads/stores of arrays that need to be
	 * transformed are updated to use a local copy of the array.
	 * 
	
	 * 
	 */
	private class LoadStoreUpdater extends AbstractIrVisitor {

		private final Map<Var, Var> localMap;

		public LoadStoreUpdater(Map<Var, Var> localMap) {
			this.localMap = localMap;
		}

		@Override
		public Void caseInstLoad(InstLoad load) {
			Var global = load.getSource().getVariable();
			if (load.getIndexes().isEmpty()) {
				delete(load);
			} else if (needTransformation(global)) {
				// replace global array by local array
				Var local = localMap.get(global);
				load.getSource().setVariable(local);
			}

			return DONE;
		}

		@Override
		public Void caseInstStore(InstStore store) {
			Var global = store.getTarget().getVariable();
			if (needTransformation(global)) {
				Var local = localMap.get(global);
				if (store.getIndexes().isEmpty()) {
					// replace store by assign (removes store from parent block in the process)
					InstAssign assign = ir.createInstAssign(local, store.getValue());
					replace(store, assign);

					// remove this store from global's defs
					store.getTarget().setVariable(null);
				} else {
					// replace global array by local array
					store.getTarget().setVariable(local);
				}
			}

			return DONE;
		}
	}

	/**
	 * This class fills the multiLocalMap table, which maps from each global variable to the local
	 * variables that loads it.
	 * 
	
	 * 
	 */
	private class MapFiller extends AbstractIrVisitor {

		/**
		 * mapping from global variable to the local variables that loads it
		 */
		private final Multimap<Var, Var> multiLocalMap;

		public MapFiller() {
			multiLocalMap = LinkedHashMultimap.create();
		}

		@Override
		public Void caseInstLoad(InstLoad load) {
			if (load.getIndexes().isEmpty()) {
				Var global = load.getSource().getVariable();
				Var local = load.getTarget().getVariable();

				// registers mapping of scalar variable
				multiLocalMap.put(global, local);
			}

			return DONE;
		}

		@Override
		public Void caseInstStore(InstStore store) {
			Var global = store.getTarget().getVariable();
			if (needTransformation(global)) {
				if (!multiLocalMap.containsKey(global)) {
					// adds new local variable
					String name = "local_" + global.getName();
					Var local = ir.newTempLocalVariable(procedure, global.getType(), name);
					multiLocalMap.put(global, local);
				}
			}

			return DONE;
		}

		/**
		 * Returns the multi map from global variable to local variables.
		 * 
		 * @return the multi map from global variable to local variables
		 */
		public Multimap<Var, Var> getMultiLocalMap() {
			return multiLocalMap;
		}

	}

	/**
	 * This class counts the number of Load/Store of each global variable.
	 * 
	
	 * 
	 */
	private class UseCounter extends AbstractIrVisitor {

		@Override
		public Void caseInstLoad(InstLoad load) {
			loaded.add(load.getSource().getVariable());
			return DONE;
		}

		@Override
		public Void caseInstStore(InstStore store) {
			Var var = store.getTarget().getVariable();
			stored.add(var);
			// Stores inside loops execute multiple times — count them as 2 so the
			// array gets the local-copy transformation. Without this, a shift loop
			// followed by a load on the same array would read stale values from the
			// non-blocking writes that haven't taken effect yet.
			if (isInLoop(store)) {
				stored.add(var);
			}
			return DONE;
		}

		private boolean isInLoop(InstStore store) {
			org.eclipse.emf.ecore.EObject obj = store.eContainer();
			while (obj != null) {
				if (obj instanceof BlockWhile) {
					return true;
				}
				obj = obj.eContainer();
			}
			return false;
		}

	}

	private static final IrFactory ir = IrFactory.eINSTANCE;

	/**
	 * mapping from global variable to the number of times it is loaded
	 */
	private Multiset<Var> loaded;

	/**
	 * mapping from global variable to the number of times it is stored
	 */
	private Multiset<Var> stored;

	/**
	 * Adds load instructions at the beginning of the given procedure.
	 * 
	 * @param procedure
	 *            procedure
	 * @param localMap
	 *            map from global to local
	 */
	private void addLoads(Procedure procedure, Map<Var, Var> localMap) {
		List<Block> blocks = new ArrayList<>();
		for (Entry<Var, Var> entry : localMap.entrySet()) {
			Var global = entry.getKey();
			Var local = entry.getValue();

			// add def at the beginning of local's definitions
			Def def = ir.createDef();
			local.getDefs().add(0, def);

			// create load
			InstLoad load = ir.createInstLoad(def, global);
			BlockBasic basic = IrUtil.getLast(blocks);
			basic.add(load);
		}

		// insert blocks at the beginning of the procedure
		procedure.getBlocks().addAll(0, blocks);
	}

	/**
	 * Adds Store instructions at the end of the given procedure. One Store is added for each global
	 * variable referenced in the localMap that needs to be transformed.
	 * 
	 * @param procedure
	 *            procedure
	 * @param localMap
	 *            map from global to local
	 * @see #needTransformation(Var)
	 */
	private void addStores(Procedure procedure, Map<Var, Var> localMap) {
		for (Entry<Var, Var> entry : localMap.entrySet()) {
			Var global = entry.getKey();
			Var local = entry.getValue();

			if (needTransformation(global)) {
				InstStore store = ir.createInstStore(global, ir.createExprVar(local));
				procedure.getLast().add(store);
			}
		}
	}

	@Override
	public Void caseProcedure(Procedure procedure) {
		loaded = HashMultiset.create();
		stored = HashMultiset.create();

		// computes loaded and stored multisets
		new UseCounter().doSwitch(procedure);

		// fills multimap
		MapFiller filler = new MapFiller();
		filler.doSwitch(procedure);

		// removes duplicate local variables
		Map<Var, Var> localMap = removeDuplicates(filler.getMultiLocalMap());

		// updates loads/stores
		new LoadStoreUpdater(localMap).doSwitch(procedure);

		if (!localMap.isEmpty()) {
			// if there are at least one local mapping
			// add loads at the beginning of the procedure
			addLoads(procedure, localMap);

			// and stores at the bottom of the procedure
			addStores(procedure, localMap);
		}

		return DONE;
	}

	/**
	 * Returns <code>true</code> if Load/Store of the given variable should be transformed.
	 * 
	 * @param global
	 *            a global variable
	 * @return <code>true</code> if a transformation is needed
	 */
	private boolean needTransformation(Var global) {
		int numLoads = loaded.count(global);
		int numStores = stored.count(global);

		if (global.getType().isArray()) {
			// Local copy needed when:
			// 1. Multiple stores (shift + enqueue pattern), OR
			// 2. At least one store AND one load in the same procedure.
			//    Non-blocking writes don't commit until end-of-cycle, so a
			//    blocking load in the same action reads stale values without
			//    the local-copy + write-forwarding transformation.
			return numStores >= 2 || (numStores > 0 && numLoads > 0);
		}

		// For scalars: transform if loaded+stored, or stored at least twice.
		// We replace multiple stores to scalars by multiple assigns + 1 store
		// this way the assigns may be inlined by the code cleaner
		return numStores > 0 && numLoads > 0 || numStores >= 2;
	}

	/**
	 * Creates a new map where there is exactly one mapping from each global to a local variable
	 * (which is why it's called removeDuplicates). The given multi map may have several local
	 * variables associated with any given global scalar variable because it may be loaded more than
	 * once.
	 * 
	 * @param multiMap
	 * @return
	 */
	private Map<Var, Var> removeDuplicates(Multimap<Var, Var> multiMap) {
		Map<Var, Var> localMap = new LinkedHashMap<Var, Var>();
		for (Var global : multiMap.keySet()) {
			Iterator<Var> it = multiMap.get(global).iterator();
			if (!it.hasNext()) {
				continue;
			}

			Var local = it.next();
			while (it.hasNext()) {
				Var duplicate = it.next();
				for (Def def : new ArrayList<>(duplicate.getDefs())) {
					def.setVariable(local);
				}

				for (Use use : new ArrayList<>(duplicate.getUses())) {
					use.setVariable(local);
				}

				// just remove from container
				EcoreUtil.remove(duplicate);
			}

			localMap.put(global, local);
		}
		return localMap;
	}

}
