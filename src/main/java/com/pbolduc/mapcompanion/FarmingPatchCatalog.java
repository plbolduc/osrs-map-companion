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
import net.runelite.api.coords.WorldPoint;
import static net.runelite.api.gameval.VarbitID.*;

/**
 * The region selects the meaning of a farming transmit slot. Falador and Catherby both use D
 * for herbs; reading D everywhere and assigning it to both sites invents another person's run.
 *
 * <p>These slot/region pairs and the two boundary exceptions are transcribed from RuneLite
 * 1.12.36 FarmingWorld (see farming-LICENSE.txt). Only ground-floor observations are accepted;
 * checking an upstairs varbit is unnecessary for witnessing a replant. Site ids are the map's
 * permanent wire ids, including its separate Catherby/White Wolf Mountain sites.
 */
final class FarmingPatchCatalog
{
	static final class Patch
	{
		final String id;
		final String kind;
		final int varbit;
		final int[] regions;

		Patch(String id, String kind, int varbit, int... regions)
		{
			this.id = id;
			this.kind = kind;
			this.varbit = varbit;
			this.regions = regions;
		}

		boolean isLocal(WorldPoint location)
		{
			if (location == null || (location.getPlane() != 0 && !kind.equals("SEAWEED")))
			{
				return false;
			}
			boolean matched = false;
			for (int region : regions)
				if (region == location.getRegionID())
				{
					matched = true;
				}
			if (!matched)
			{
				return false;
			}
			if ((id.startsWith("falador-") && !kind.equals("TREE")))
			{
				return location.getY() >= 3272;
			}
			if (id.equals("catherby-fruit-tree"))
			{
				return location.getX() >= 2840 || location.getY() < 3440;
			}
			if ((id.startsWith("catherby-") && !kind.equals("FRUIT_TREE")) && location.getX() >= 2816 && location.getY() < 3456)
			{
				return location.getX() < 2840 && location.getY() >= 3440;
			}
			return true;
		}

		/** Region membership alone is insufficient at the coordinate boundaries above. */
		boolean transmitsIn(int regionId)
		{
			for (int region : regions)
				if (region == regionId)
				{
					return true;
				}
			return false;
		}
	}

	static final List<Patch> PATCHES = Collections.unmodifiableList(Arrays.asList(
			new Patch("falador-herb", "HERB", FARMING_TRANSMIT_D, 12083),
			new Patch("falador-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 12083),
			new Patch("falador-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 12083),
			new Patch("falador-flower", "FLOWER", FARMING_TRANSMIT_C, 12083),
			new Patch("catherby-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 11062, 11061, 11318, 11317),
			new Patch("catherby-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 11062, 11061, 11318, 11317),
			new Patch("catherby-flower", "FLOWER", FARMING_TRANSMIT_C, 11062, 11061, 11318, 11317),
			new Patch("ardougne-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 10548),
			new Patch("ardougne-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 10548),
			new Patch("ardougne-flower", "FLOWER", FARMING_TRANSMIT_C, 10548),
			new Patch("morytania-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 14391, 14390),
			new Patch("morytania-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 14391, 14390),
			new Patch("morytania-flower", "FLOWER", FARMING_TRANSMIT_C, 14391, 14390),
			new Patch("hosidius-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 6967, 6711),
			new Patch("hosidius-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 6967, 6711),
			new Patch("hosidius-flower", "FLOWER", FARMING_TRANSMIT_C, 6967, 6711),
			new Patch("farming-guild-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_C, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_D, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-flower", "FLOWER", FARMING_TRANSMIT_H, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("outer-fortis-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 6192, 6447, 6448, 6449, 6191, 6193),
			new Patch("outer-fortis-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 6192, 6447, 6448, 6449, 6191, 6193),
			new Patch("outer-fortis-flower", "FLOWER", FARMING_TRANSMIT_C, 6192, 6447, 6448, 6449, 6191, 6193),
			new Patch("kastori-flower", "FLOWER", FARMING_TRANSMIT_C, 5423, 5167, 5424),
			new Patch("seaweed-north", "SEAWEED", FARMING_TRANSMIT_A, 15008),
			new Patch("seaweed-south", "SEAWEED", FARMING_TRANSMIT_B, 15008),
			new Patch("catherby-herb", "HERB", FARMING_TRANSMIT_D, 11062, 11061, 11318, 11317),
			new Patch("ardougne-herb", "HERB", FARMING_TRANSMIT_D, 10548),
			new Patch("morytania-herb", "HERB", FARMING_TRANSMIT_D, 14391, 14390),
			new Patch("hosidius-herb", "HERB", FARMING_TRANSMIT_D, 6967, 6711),
			new Patch("farming-guild-herb", "HERB", FARMING_TRANSMIT_E, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("troll-stronghold-herb", "HERB", FARMING_TRANSMIT_A, 11321),
			new Patch("weiss-herb", "HERB", FARMING_TRANSMIT_A, 11325),
			new Patch("harmony-herb", "HERB", FARMING_TRANSMIT_B, 15148),
			new Patch("outer-fortis-herb", "HERB", FARMING_TRANSMIT_D, 6192, 6447, 6448, 6449, 6191, 6193),
			new Patch("falador-tree", "TREE", FARMING_TRANSMIT_A, 11828, 12084),
			new Patch("varrock-tree", "TREE", FARMING_TRANSMIT_A, 12854, 12853),
			new Patch("lumbridge-tree", "TREE", FARMING_TRANSMIT_A, 12594, 12850),
			new Patch("taverley-tree", "TREE", FARMING_TRANSMIT_A, 11573, 11829),
			new Patch("gnome-stronghold-tree", "TREE", FARMING_TRANSMIT_A, 9781, 9782, 9526, 9525),
			new Patch("farming-guild-tree", "TREE", FARMING_TRANSMIT_G, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("nemus-retreat-tree", "TREE", FARMING_TRANSMIT_A, 5427, 5428, 5684),
			new Patch("brimhaven-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_A, 11058, 11057),
			new Patch("catherby-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_A, 11317),
			new Patch("gnome-stronghold-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_B, 9781, 9782, 9526, 9525),
			new Patch("tree-gnome-village-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_A, 9777, 10033),
			new Patch("lletya-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_A, 9265, 11103),
			new Patch("farming-guild-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_K, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("kastori-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_B, 5423, 5167, 5424)
		));

	private FarmingPatchCatalog() {}
}
