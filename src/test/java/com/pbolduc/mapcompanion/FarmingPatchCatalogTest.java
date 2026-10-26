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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The patch table's one job: turn "where am I standing" into "which patch is this number about".
 *
 * <p>The failure this guards against has no symptom. If two watched patches claim the same
 * transmit slot in the same region, both are read there, both get stamped with the same value, and
 * the map draws a herb patch's state on a fruit tree — confidently, forever, with nothing in a log
 * to notice. Nine of the twenty-four rows share slot A with another row, so the collision is one
 * mistyped region id away rather than hypothetical.
 *
 * <p>{@code FarmingObserver} cannot be tested here — it reads a live client — so this checks the
 * table it reads and the rule it applies to it.
 */
public class FarmingPatchCatalogTest
{
	@Test
	public void namesEveryPatchOnce()
	{
		Set<String> ids = new HashSet<>();
		for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
		{
			assertTrue("two rows share the id " + patch.id, ids.add(patch.id));
		}
		/**
		 * The count is asserted so that dropping a row while adding one cannot pass unnoticed. The
		 * map's own contract test is what checks that these are the same twenty-four ids it draws.
		 */
		assertEquals(48, ids.size());
	}

	@Test
	public void neverGivesOneRegionAndSlotToTwoPatches()
	{
		Map<String, java.util.List<FarmingPatchCatalog.Patch>> claimed = new HashMap<>();
		/** Every region id the game can produce: {@code (x >> 6) << 8 | (y >> 6)} tops out here. */
		for (int region = 0; region <= 0xFFFF; region++)
		{
			for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
			{
				if (!patch.transmitsIn(region))
				{
					continue;
				}
				String key = region + "/" + patch.varbit;
				java.util.List<FarmingPatchCatalog.Patch> previous = claimed.computeIfAbsent(key, ignored -> new java.util.ArrayList<>());
				// Catherby's fruit tree and north allotment reuse A in disjoint parts of 11317.
				// Check the whole region square so neither may steal the other's boundary.
				for (FarmingPatchCatalog.Patch other : previous)
					for (int dx = 0; dx < 64; dx++)
						for (int dy = 0; dy < 64; dy++)
						{
							WorldPoint point = new WorldPoint((region >> 8) * 64 + dx, (region & 255) * 64 + dy, 0);
							assertFalse(key + " at " + point + ": " + patch.id + " and " + other.id,
								patch.isLocal(point) && other.isLocal(point));
						}
				previous.add(patch);
			}
		}
	}

	@Test
	public void aSlotMeansNothingOutsideItsOwnRegions()
	{
		FarmingPatchCatalog.Patch falador = patch("falador-herb");
		FarmingPatchCatalog.Patch catherby = patch("catherby-herb");
		/** Both read the same number, and only the region tells them apart. */
		assertEquals(falador.varbit, catherby.varbit);
		assertTrue(falador.transmitsIn(12083));
		assertFalse(falador.transmitsIn(11062));
		assertTrue(catherby.transmitsIn(11062));
		assertFalse(catherby.transmitsIn(12083));
	}

	@Test
	public void leavesTheCatherbyFruitTreeSquareToTheFruitTree()
	{
		// Region membership lost the herb patch's corner of 11317. Check the client's
		// coordinate split so both crops are covered without exchanging their readings.
		WorldPoint herbCorner = new WorldPoint(2839, 3440, 0);
		WorldPoint fruitSide = new WorldPoint(2840, 3440, 0);
		assertTrue(patch("catherby-herb").isLocal(herbCorner));
		assertFalse(patch("catherby-fruit-tree").isLocal(herbCorner));
		assertFalse(patch("catherby-herb").isLocal(fruitSide));
		assertTrue(patch("catherby-fruit-tree").isLocal(fruitSide));
	}

	private static FarmingPatchCatalog.Patch patch(String id)
	{
		for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
		{
			if (patch.id.equals(id))
			{
				return patch;
			}
		}
		throw new AssertionError("no patch named " + id);
	}
}
