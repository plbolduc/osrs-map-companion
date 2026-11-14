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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the sidebar's Port tasks section says for each slot, in the order the task is done.
 *
 * <p>The words a player plans by: where the crates wait and where they go, then where to deliver
 * them; a bounty's part and where to hand it in; and beside each, how far along it is as a count.
 * And the two absences said apart — an unreadable slot is never counted among the free ones.
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

	/**
	 * These lines carried the numbers in a sentence, "Collect 1 crate at Catherby for Port Sarim",
	 * "All 3 crates delivered". The number is a count beside the task's name now, taken of asked
	 * while any crate is still to collect and delivered of asked after, and the line says only where.
	 */
	@Test
	public void aCourierSaysWhatItStillAsksInOrder()
	{
		assertEquals("Collect at Catherby for Port Sarim", PortTasksPanel.next(courier(3, 0, 0)));
		assertEquals("0/3", PortTasksPanel.count(courier(3, 0, 0)));
		assertEquals("Collect at Catherby for Port Sarim", PortTasksPanel.next(courier(3, 2, 0)));
		assertEquals("crates taken, of those asked", "2/3", PortTasksPanel.count(courier(3, 2, 0)));
		assertEquals("Deliver to Port Sarim", PortTasksPanel.next(courier(3, 3, 1)));
		assertEquals("crates delivered, once all are aboard", "1/3", PortTasksPanel.count(courier(3, 3, 1)));
		assertEquals("Delivered", PortTasksPanel.next(courier(3, 3, 3)));
		assertEquals("3/3", PortTasksPanel.count(courier(3, 3, 3)));
	}

	/**
	 * A bounty's line said "9 of 15 still owed" between its part and its board. Its count is the
	 * parts had of those asked, beside its name, and reads whole once all are collected; the line is
	 * the part and where it is handed in either way.
	 */
	@Test
	public void aBountySaysThePartsHadAndWhereTheyGo()
	{
		assertEquals("Tern feather · hand in at Port Sarim", PortTasksPanel.next(bounty(9)));
		assertEquals("6/15", PortTasksPanel.count(bounty(9)));
		assertEquals("Tern feather · hand in at Port Sarim", PortTasksPanel.next(bounty(0)));
		assertEquals("all collected", "15/15", PortTasksPanel.count(bounty(0)));
	}

	/** What the client's tables did not name is still said around: no port to collect at, no board, no part, no total. */
	@Test
	public void aTaskTheTablesHalfNamedKeepsItsGuards()
	{
		PortTaskReader.Slot nowhere = new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, null, null, null, 3, 1, 0, 0, null);
		assertEquals("Collect for the board", PortTasksPanel.next(nowhere));
		assertEquals("1/3", PortTasksPanel.count(nowhere));
		PortTaskReader.Slot uncounted = new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, null, "Catherby", null, 0, 0, 0, 0, null);
		assertEquals("Deliver to the board", PortTasksPanel.next(uncounted));
		assertNull("crates the game did not count have no count", PortTasksPanel.count(uncounted));
		PortTaskReader.Slot parts = new PortTaskReader.Slot(1, PortTaskReader.BOUNTY, 438, null, null, null, 0, 0, 0, 9, null);
		assertEquals("Parts · hand in at the board", PortTasksPanel.next(parts));
		assertEquals("no total to count against, so what is owed", "9 left", PortTasksPanel.count(parts));
		assertEquals("never fewer had than none", "0/15", PortTasksPanel.count(bounty(20)));
	}

	/**
	 * The footer used to end by sending a courier's order to the map's Port tasks page. Start run now
	 * asks for that order here, beneath the footer, so the footer says only what the slots and the hold
	 * are; the order is the button's. It said "2 free", naming nothing, "hold carries 30 crates", which
	 * read as cargo aboard, and "hold not seen yet", with nothing to do about it: it names the slots,
	 * says what the hold fits, and an unseen hold is the step that shows it.
	 */
	@Test
	public void theFooterTellsUnreadableFromFreeAndSaysWhenTheHoldIsUnknown()
	{
		List<PortTaskReader.Slot> slots = Arrays.asList(courier(3, 0, 0), bounty(9),
			PortTaskReader.Slot.plain(2, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(3, PortTaskReader.UNKNOWN),
			PortTaskReader.Slot.plain(4, PortTaskReader.LOCKED));
		assertEquals("1 task couldn't be read · 1 slot free. Board your boat once so the run knows its hold.",
			PortTasksPanel.footer(slots, null));
		assertEquals("1 task couldn't be read · 1 slot free · hold fits 60 crates.", PortTasksPanel.footer(slots, 60));
		assertEquals("Hold fits 30 crates.",
			PortTasksPanel.footer(Arrays.asList(courier(3, 0, 0), PortTaskReader.Slot.plain(1, PortTaskReader.LOCKED)), 30));
		assertEquals("4 slots free. Board your boat once so the run knows its hold.", PortTasksPanel.footer(Arrays.asList(bounty(3),
			PortTaskReader.Slot.plain(1, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(2, PortTaskReader.EMPTY),
			PortTaskReader.Slot.plain(3, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(4, PortTaskReader.EMPTY)), null));
		assertEquals(PortTasksPanel.BOARD_ONCE, PortTasksPanel.footer(Arrays.asList(courier(3, 0, 0),
			PortTaskReader.Slot.plain(1, PortTaskReader.LOCKED)), null));
	}

	@Test
	public void aRunIsOfferedOnlyForATaskItCanDo()
	{
		assertTrue(PortTasksPanel.startable(Arrays.asList(bounty(0), PortTaskReader.Slot.plain(1, PortTaskReader.EMPTY))));
		assertFalse(PortTasksPanel.startable(Arrays.asList(PortTaskReader.Slot.plain(0, PortTaskReader.UNKNOWN),
			PortTaskReader.Slot.plain(1, PortTaskReader.LOCKED))));
		assertFalse(PortTasksPanel.startable(null));
	}
}
