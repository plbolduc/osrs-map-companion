/*
 * Copyright (c) 2026, pbolduc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pbolduc.mapcompanion;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The bank marks what the sidebar marks as not on you, by the rule that decides what is on you. */
public class FarmBankOverlayTest
{
	private static RelayResponse.FarmBring line(String kind, String name)
	{
		RelayResponse.FarmBring line = new RelayResponse.FarmBring();
		line.kind = kind;
		line.name = name;
		return line;
	}

	private static FarmRunState.View run(String kind, List<String> carried)
	{
		RelayResponse.FarmRun run = new RelayResponse.FarmRun();
		run.kind = kind;
		run.bring = Arrays.asList(
			line("teleport", "Amulet of glory"),
			line("teleport", "Ring of the elements"),
			/* A tablet no travel flag names: the sidebar abstains on it, and so does the bank. */
			line("teleport", "Catherby tablet"),
			line("supply", "2 herb seeds"));
		return new FarmRunState.View(run, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
			false, 1, 1, carried);
	}

	@Test
	public void marksATeleportTheRunNeedsThatIsNotCarried()
	{
		List<CapabilityEvaluator.ItemTest> wanted = FarmBankOverlay.wanted(run("herb", Collections.emptyList()));
		assertEquals("the glory and the ring; not the tablet, not the seeds", 2, wanted.size());
		assertTrue(FarmBankOverlay.grants(wanted, 1712, "Amulet of glory(4)"));
		assertFalse("an emptied glory carries nothing", FarmBankOverlay.grants(wanted, 1704, "Amulet of glory"));
		assertTrue("the charged ring, by its id", FarmBankOverlay.grants(wanted, ItemID.RING_OF_ELEMENTS_CHARGED, "Ring of the elements"));
		assertFalse("the spent ring has the same name", FarmBankOverlay.grants(wanted, ItemID.RING_OF_ELEMENTS_CHARGED + 1, "Ring of the elements"));
		assertFalse(FarmBankOverlay.grants(wanted, 8007, "Catherby teleport"));
	}

	@Test
	public void marksNothingCarriedUnreadOrForAPortTaskRun()
	{
		assertTrue("both carried", FarmBankOverlay.wanted(run("herb", Arrays.asList("amuletOfGlory", "ringOfTheElements"))).isEmpty());
		assertTrue("the inventory not read yet: abstain", FarmBankOverlay.wanted(run("herb", null)).isEmpty());
		assertTrue(FarmBankOverlay.wanted(run(FarmRunWire.PORT_TASKS, Collections.emptyList())).isEmpty());
		assertTrue("no run going", FarmBankOverlay.wanted(null).isEmpty());
	}
}
