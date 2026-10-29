/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.neosyn.core.util.SimTargetSelector;
import com.neosyn.core.util.SimTargetSelector.Selection;
import com.neosyn.models.dpn.DpnFactory;
import com.neosyn.models.dpn.Entity;

/**
 * Guards which entity "Simulate" runs for a source file -- TRAPS T87.
 *
 * <p>The simulate handler loads EVERY entity of the project, but its auto-select
 * priority was written for one file. Simulating {@code TestFullStack.cg}, a network
 * with no {@code test} property, therefore ran {@code Test_ArpSendHeader} from another
 * file and reported "Simulation completed successfully (8 checks)". The fixture below
 * is that project, with the foreign testbenches FIRST in load order, as they were.
 */
public class SimTargetSelectorTest {

	private static final String SRC = "/p/UDP-IP-Stack/src/com/neosyn/net/test/";

	private static Entity entity(String simpleName, String file, boolean testbench) {
		Entity e = DpnFactory.eINSTANCE.createDPN();
		e.setName("com.neosyn.net.test." + simpleName);
		e.setFileName(file == null ? null : SRC + file);
		if (testbench) {
			JsonObject props = new JsonObject();
			props.add("test", new JsonObject());
			e.setProperties(props);
		}
		return e;
	}

	private static List<Entity> udpProject() {
		List<Entity> l = new ArrayList<>();
		l.add(entity("Test_ArpSendHeader", "Test_ArpSendHeader.cg", true));
		l.add(entity("Test_ArpSendHeader_checker", "Test_ArpSendHeader.cg", false));
		l.add(entity("TestChecksum", "TestChecksum.cg", true));
		l.add(entity("Test_InsertMacHeader", "Test_InsertMacHeader.cg", true));
		l.add(entity("TestFullStack_dummy", "TestFullStack.cg", false));
		l.add(entity("TestFullStack", "TestFullStack.cg", false));
		return l;
	}

	private static String chosen(Selection s) {
		return s.getChosen() == null ? null : s.getChosen().getSimpleName();
	}

	@Test
	public void nonTestbenchFileRunsItsOwnEntity_neverAnotherFilesTestbench() {
		Selection s = SimTargetSelector.select(udpProject(), SRC + "TestFullStack.cg", "TestFullStack", false);
		assertEquals("TestFullStack", chosen(s));
		assertEquals(0, s.getTestbenchesInFile().size());
	}

	@Test
	public void testbenchFileRunsItsTestbench_andCountsOnlyItsOwnFile() {
		Selection s = SimTargetSelector.select(udpProject(), SRC + "Test_InsertMacHeader.cg",
				"Test_InsertMacHeader", false);
		assertEquals("Test_InsertMacHeader", chosen(s));
		// The CLI warned "file has 4 testbenches" here: it counted the whole project.
		assertEquals(1, s.getTestbenchesInFile().size());
	}

	@Test
	public void twoTestbenchesInOneFile_areBothCounted() {
		List<Entity> l = udpProject();
		l.add(entity("TB_A", "Multi.cg", true));
		l.add(entity("TB_B", "Multi.cg", true));
		Selection s = SimTargetSelector.select(l, SRC + "Multi.cg", "Multi", false);
		assertEquals("TB_A", chosen(s));
		assertEquals(2, s.getTestbenchesInFile().size());
	}

	@Test
	public void taskAndItsTestNetworkInOneFile_runsTheTest() {
		List<Entity> l = udpProject();
		l.add(entity("Adder", "Adder.cg", false));
		l.add(entity("Adder_test", "Adder.cg", false));
		assertEquals("Adder_test", chosen(SimTargetSelector.select(l, SRC + "Adder.cg", "Adder", false)));
	}

	@Test
	public void nameTestInAnotherFile_isStillHonoured() {
		// `<name>_test` is a naming contract, not an arbitrary pick: it stays project-wide.
		List<Entity> l = udpProject();
		l.add(entity("Mux", "Mux.cg", false));
		l.add(entity("Mux_test", "Mux_test.cg", false));
		assertEquals("Mux_test", chosen(SimTargetSelector.select(l, SRC + "Mux.cg", "Mux", false)));
	}

	@Test
	public void testNamedDifferentlyFromItsFile_isStillFound() {
		List<Entity> l = udpProject();
		l.add(entity("Bar", "Foo.cg", false));
		l.add(entity("Bar_test", "Foo.cg", false));
		assertEquals("Bar_test", chosen(SimTargetSelector.select(l, SRC + "Foo.cg", "Foo", false)));
	}

	@Test
	public void hoistedChildDoesNotOutrankTheNetworkItCameFrom() {
		List<Entity> l = udpProject();
		l.add(entity("FifoThoroughTest_producer", "FifoThoroughTest.cg", false));
		l.add(entity("FifoThoroughTest", "FifoThoroughTest.cg", false));
		assertEquals("FifoThoroughTest",
				chosen(SimTargetSelector.select(l, SRC + "FifoThoroughTest.cg", "FifoThoroughTest", false)));
	}

	@Test
	public void targetPathIsCompared_normalised() {
		Selection s = SimTargetSelector.select(udpProject(), SRC + "../test/./TestFullStack.cg",
				"TestFullStack", false);
		assertEquals("TestFullStack", chosen(s));
	}

	@Test
	public void entitiesWithoutAFileName_fallBackToTheExactName_notToAnyTestbench() {
		List<Entity> l = new ArrayList<>();
		l.add(entity("Test_ArpSendHeader", null, true));
		l.add(entity("TestFullStack", null, false));
		assertEquals("TestFullStack",
				chosen(SimTargetSelector.select(l, SRC + "TestFullStack.cg", "TestFullStack", false)));
	}

	@Test
	public void explicitNameThatMatchesNothing_selectsNothing() {
		// Used to return the FIRST entity of the project -- an arbitrary design, run silently.
		assertNull(chosen(SimTargetSelector.select(udpProject(), SRC + "TestFullStack.cg", "NoSuchEntity", true)));
	}

	@Test
	public void explicitNameInAnotherFile_isHonoured() {
		assertEquals("Test_ArpSendHeader",
				chosen(SimTargetSelector.select(udpProject(), SRC + "TestFullStack.cg", "Test_ArpSendHeader", true)));
	}
}
