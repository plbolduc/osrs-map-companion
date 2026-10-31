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
 * <p>These slot/region pairs and boundary exceptions are transcribed from RuneLite
 * 1.13.4 FarmingWorld (see farming-LICENSE.txt). Only ground-floor observations are accepted;
 * seaweed and the coral nursery's own floor are the exceptions. Site ids are the map's
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
			if (location == null || (location.getPlane() != 0 && !kind.equals("SEAWEED")
				&& !(kind.equals("CORAL") && location.getPlane() == 1)))
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
			if (id.equals("port-sarim-spirit-tree"))
			{
				return location.getY() < 3272;
			}
			if (id.startsWith("fossil-island-"))
			{
				// RuneLite excludes these stairs: a different floor's slots arrive a tick early.
				int x = location.getX();
				int y = location.getY();
				if (x == 3753 && y >= 3868 && y <= 3870)
				{
					return false;
				}
				if ((x == 3729 || x == 3728 || x == 3747 || x == 3746) && y >= 3830 && y <= 3832)
				{
					return false;
				}
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
			new Patch("kastori-fruit-tree", "FRUIT_TREE", FARMING_TRANSMIT_B, 5423, 5167, 5424),
			new Patch("al-kharid-cactus", "CACTUS", FARMING_TRANSMIT_A, 13106, 13362, 13105),
			new Patch("aldarin-hops", "HOPS", FARMING_TRANSMIT_A, 5421, 5165, 5166, 5422, 5677, 5678),
			new Patch("anglers-retreat-hardwood", "HARDWOOD_TREE", FARMING_TRANSMIT_A, 9770),
			new Patch("ardougne-bush", "BUSH", FARMING_TRANSMIT_A, 10290, 10546),
			new Patch("auburnvale-belladonna", "BELLADONNA", FARMING_TRANSMIT_B, 5427, 5428, 5684),
			new Patch("locus-oasis-hardwood", "HARDWOOD_TREE", FARMING_TRANSMIT_A, 6702, 6446),
			new Patch("brimhaven-spirit-tree", "SPIRIT_TREE", FARMING_TRANSMIT_B, 11058, 11057),
			new Patch("champions-guild-bush", "BUSH", FARMING_TRANSMIT_A, 12596),
			new Patch("draynor-manor-belladonna", "BELLADONNA", FARMING_TRANSMIT_A, 12340),
			new Patch("entrana-hops", "HOPS", FARMING_TRANSMIT_A, 11060, 11316),
			new Patch("etceteria-bush", "BUSH", FARMING_TRANSMIT_A, 10300),
			new Patch("etceteria-spirit-tree", "SPIRIT_TREE", FARMING_TRANSMIT_B, 10300),
			new Patch("fossil-island-east-hardwood", "HARDWOOD_TREE", FARMING_TRANSMIT_A, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163),
			new Patch("fossil-island-middle-hardwood", "HARDWOOD_TREE", FARMING_TRANSMIT_B, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163),
			new Patch("fossil-island-west-hardwood", "HARDWOOD_TREE", FARMING_TRANSMIT_C, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163),
			new Patch("great-conch-east-coral", "CORAL", FARMING_TRANSMIT_A, 12581, 12325, 12326, 12327, 12580, 12582, 12583, 12836, 12837, 12838, 12839, 13092, 13093, 13194),
			new Patch("great-conch-west-coral", "CORAL", FARMING_TRANSMIT_B, 12581, 12325, 12326, 12327, 12580, 12582, 12583, 12836, 12837, 12838, 12839, 13092, 13093, 13194),
			new Patch("great-conch-calquat", "CALQUAT", FARMING_TRANSMIT_C, 12581, 12325, 12326, 12327, 12580, 12582, 12583, 12836, 12837, 12838, 12839, 13092, 13093, 13194),
			new Patch("harmony-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 15148),
			new Patch("kastori-calquat", "CALQUAT", FARMING_TRANSMIT_A, 5423, 5167, 5424),
			new Patch("hosidius-spirit-tree", "SPIRIT_TREE", FARMING_TRANSMIT_F, 6967, 6711),
			new Patch("hosidius-east-1-grape", "GRAPES", FARMING_TRANSMIT_A1, 7223),
			new Patch("hosidius-east-2-grape", "GRAPES", FARMING_TRANSMIT_A2, 7223),
			new Patch("hosidius-east-3-grape", "GRAPES", FARMING_TRANSMIT_B1, 7223),
			new Patch("hosidius-east-4-grape", "GRAPES", FARMING_TRANSMIT_B2, 7223),
			new Patch("hosidius-east-5-grape", "GRAPES", FARMING_TRANSMIT_C1, 7223),
			new Patch("hosidius-east-6-grape", "GRAPES", FARMING_TRANSMIT_C2, 7223),
			new Patch("hosidius-west-1-grape", "GRAPES", FARMING_TRANSMIT_D1, 7223),
			new Patch("hosidius-west-2-grape", "GRAPES", FARMING_TRANSMIT_D2, 7223),
			new Patch("hosidius-west-3-grape", "GRAPES", FARMING_TRANSMIT_E1, 7223),
			new Patch("hosidius-west-4-grape", "GRAPES", FARMING_TRANSMIT_E2, 7223),
			new Patch("hosidius-west-5-grape", "GRAPES", FARMING_TRANSMIT_F1, 7223),
			new Patch("hosidius-west-6-grape", "GRAPES", FARMING_TRANSMIT_F2, 7223),
			new Patch("lumbridge-hops", "HOPS", FARMING_TRANSMIT_A, 12851),
			new Patch("morytania-mushroom", "MUSHROOM", FARMING_TRANSMIT_A, 13622, 13878),
			new Patch("port-sarim-spirit-tree", "SPIRIT_TREE", FARMING_TRANSMIT_A, 12082, 12083),
			new Patch("rimmington-bush", "BUSH", FARMING_TRANSMIT_A, 11570, 11826),
			new Patch("seers-village-hops", "HOPS", FARMING_TRANSMIT_A, 10551, 10550),
			new Patch("tai-bwo-wannai-calquat", "CALQUAT", FARMING_TRANSMIT_A, 11056),
			new Patch("yanille-hops", "HOPS", FARMING_TRANSMIT_A, 10288),
			new Patch("farming-guild-hespori", "HESPORI", FARMING_TRANSMIT_J, 5021),
			new Patch("farming-guild-bush", "BUSH", FARMING_TRANSMIT_B, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-cactus", "CACTUS", FARMING_TRANSMIT_F, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-spirit-tree", "SPIRIT_TREE", FARMING_TRANSMIT_A, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-anima", "ANIMA", FARMING_TRANSMIT_M, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-celastrus", "CELASTRUS", FARMING_TRANSMIT_L, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("farming-guild-redwood", "REDWOOD", FARMING_TRANSMIT_I, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667),
			new Patch("prifddinas-north-allotment", "ALLOTMENT", FARMING_TRANSMIT_A, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
			new Patch("prifddinas-south-allotment", "ALLOTMENT", FARMING_TRANSMIT_B, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
			new Patch("prifddinas-flower", "FLOWER", FARMING_TRANSMIT_C, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250),
			new Patch("prifddinas-crystal", "CRYSTAL_TREE", FARMING_TRANSMIT_E, 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250)
		));

	private FarmingPatchCatalog() {}
}
