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

import net.runelite.api.gameval.ObjectID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The hold's crates, from the objects a boat spawns.
 *
 * <p>The tables are the Hub Sailing plugin's {@code CargoHoldTier} and {@code SizeClass} at the
 * pinned commit; the first test pins a corner of each so a reordered row fails here rather than
 * reading a mahogany sloop as an oak raft. The rest is the rule: only the boat the player stands in
 * counts, it is remembered ashore, and no boat is no capacity rather than none.
 */
public class CargoHoldsTest
{
	private final CargoHolds holds = new CargoHolds();

	@Test
	public void theTablesAreTheSourcesRowForRow()
	{
		assertEquals(ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT, CargoHolds.HOLDS[0][0]);
		assertEquals(60245, CargoHolds.HOLDS[0][0]);
		assertEquals(ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO, CargoHolds.HOLDS[6][8]);
		assertEquals(ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_WOOD, CargoHolds.HULLS[0][0]);
		assertEquals(ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_ROSEWOOD, CargoHolds.HULLS[2][6]);
		assertArrayEquals(new int[] {20, 30, 40, 4}, CargoHolds.CAPACITY[0]);
		assertArrayEquals(new int[] {120, 180, 240, 10}, CargoHolds.CAPACITY[6]);
		assertEquals(7, CargoHolds.HOLDS.length);
		for (int[] tier : CargoHolds.HOLDS)
		{
			assertEquals(9, tier.length);
		}
	}

	@Test
	public void noBoatStoodOnIsNoCapacity()
	{
		assertNull(holds.capacity(false));
		holds.spawned(3, CargoHolds.HOLDS[1][0]);
		holds.spawned(3, CargoHolds.HULLS[0][1]);
		holds.standingIn(0, true);
		assertNull("a boat in sight is not the boat being sailed", holds.capacity(false));
	}

	@Test
	public void onlyTheBoatThePlayerStandsInCounts()
	{
		/* Somebody else's rosewood sloop beside ours, an oak raft. */
		holds.spawned(4, CargoHolds.HOLDS[6][6]);
		holds.spawned(4, CargoHolds.HULLS[2][6]);
		holds.spawned(5, CargoHolds.HOLDS[1][1]);
		holds.spawned(5, CargoHolds.HULLS[0][1]);
		holds.standingIn(5, false);
		assertEquals(Integer.valueOf(30), holds.capacity(false));
		holds.standingIn(0, true);
		assertEquals("ashore, the boat moored there is still the boat", Integer.valueOf(30), holds.capacity(false));
		assertEquals(Integer.valueOf(5), holds.capacity(true));
	}

	@Test
	public void aBoatWithNoHoldTellsNothing()
	{
		holds.spawned(6, CargoHolds.HULLS[1][0]);
		holds.standingIn(6, false);
		assertNull(holds.capacity(false));
	}

	@Test
	public void aNewSessionForgetsTheBoat()
	{
		holds.spawned(5, CargoHolds.HOLDS[3][4]);
		holds.spawned(5, CargoHolds.HULLS[1][3]);
		holds.standingIn(5, false);
		assertEquals(Integer.valueOf(90), holds.capacity(false));
		holds.reset();
		assertNull(holds.capacity(false));
		holds.unloaded(5);
		holds.standingIn(5, false);
		assertNull(holds.capacity(false));
	}
}
