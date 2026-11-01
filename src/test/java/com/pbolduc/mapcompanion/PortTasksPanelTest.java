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
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * What the sidebar's Port tasks section says for each slot, in the order the task is done.
 *
 * <p>The words a player plans by: crates still to collect and where they go, then crates to
 * deliver; parts still owed and where to hand them in. And the two absences said apart — an
 * unreadable slot is never counted among the free ones.
 */
public class PortTasksPanelTest
{
	private static PortTaskReader.Slot courier(int needed, int taken, int delivered)
	{
		return new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, "Port Sarim honey delivery", "Catherby", "Port Sarim",
			needed, taken, delivered, 0, null);
	}

	private static PortTaskReader.Slot bounty(int remaining)
	{
		return new PortTaskReader.Slot(1, PortTaskReader.BOUNTY, 438, "Port Sarim tern bounty", null, "Port Sarim",
			15, 0, 0, remaining, "Tern feather");
	}

	@Test
	public void aCourierSaysWhatItStillAsksInOrder()
	{
		assertEquals("Collect 3 crates at Catherby for Port Sarim", PortTasksPanel.next(courier(3, 0, 0)));
		assertEquals("Collect 1 crate at Catherby for Port Sarim", PortTasksPanel.next(courier(3, 2, 0)));
		assertEquals("Deliver 2 crates to Port Sarim", PortTasksPanel.next(courier(3, 3, 1)));
		assertEquals("All 3 crates delivered", PortTasksPanel.next(courier(3, 3, 3)));
	}

	@Test
	public void aBountySaysThePartsStillOwedAndWhereTheyGo()
	{
		assertEquals("Tern feather · 9 of 15 still owed · hand in at Port Sarim", PortTasksPanel.next(bounty(9)));
		assertEquals("Tern feather · all collected · hand in at Port Sarim", PortTasksPanel.next(bounty(0)));
	}

	@Test
	public void theFooterTellsUnreadableFromFreeAndSaysWhenTheHoldIsUnknown()
	{
		List<PortTaskReader.Slot> slots = Arrays.asList(courier(3, 0, 0), bounty(9),
			PortTaskReader.Slot.plain(2, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(3, PortTaskReader.UNKNOWN),
			PortTaskReader.Slot.plain(4, PortTaskReader.LOCKED));
		assertEquals("1 slot couldn't be read · 1 free · hold not seen yet. The map's Port tasks page orders your stops into one run.",
			PortTasksPanel.footer(slots, null));
		assertEquals("1 slot couldn't be read · 1 free · hold carries 60 crates. The map's Port tasks page orders your stops into one run.",
			PortTasksPanel.footer(slots, 60));
		assertEquals("Hold carries 30 crates. The map's Port tasks page orders your stops into one run.",
			PortTasksPanel.footer(Arrays.asList(courier(3, 0, 0), PortTaskReader.Slot.plain(1, PortTaskReader.LOCKED)), 30));
		assertEquals("4 free · hold not seen yet.", PortTasksPanel.footer(Arrays.asList(bounty(3),
			PortTaskReader.Slot.plain(1, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(2, PortTaskReader.EMPTY),
			PortTaskReader.Slot.plain(3, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(4, PortTaskReader.EMPTY)), null));
	}
}
