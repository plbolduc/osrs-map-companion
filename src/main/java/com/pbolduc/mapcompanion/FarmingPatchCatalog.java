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

import java.util.*;
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
	}

	/**
	 * The transmit varbits a row of farming-patches.txt names by letter, in one order, so the
	 * game ids stay the client's own constants while the table is data.
	 */
	private static final String[] SLOTS = {
		"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
		"A1", "A2", "B1", "B2", "C1", "C2", "D1", "D2", "E1", "E2", "F1", "F2"};
	private static final int[] TRANSMITS = {
		FARMING_TRANSMIT_A, FARMING_TRANSMIT_B, FARMING_TRANSMIT_C, FARMING_TRANSMIT_D,
		FARMING_TRANSMIT_E, FARMING_TRANSMIT_F, FARMING_TRANSMIT_G, FARMING_TRANSMIT_H,
		FARMING_TRANSMIT_I, FARMING_TRANSMIT_J, FARMING_TRANSMIT_K, FARMING_TRANSMIT_L,
		FARMING_TRANSMIT_M, FARMING_TRANSMIT_A1, FARMING_TRANSMIT_A2, FARMING_TRANSMIT_B1,
		FARMING_TRANSMIT_B2, FARMING_TRANSMIT_C1, FARMING_TRANSMIT_C2, FARMING_TRANSMIT_D1,
		FARMING_TRANSMIT_D2, FARMING_TRANSMIT_E1, FARMING_TRANSMIT_E2, FARMING_TRANSMIT_F1,
		FARMING_TRANSMIT_F2};

	/** Every patch the plugin reads, in the order farming-patches.txt lists them. */
	static final List<Patch> PATCHES = load();

	private static List<Patch> load()
	{
		List<String> slots = Arrays.asList(SLOTS);
		List<Patch> patches = new ArrayList<>();
		for (String line : Resources.lines("/farming-patches.txt"))
		{
			String[] row = line.split("\\|");
			patches.add(new Patch(row[0], row[1], TRANSMITS[slots.indexOf(row[2])],
				Arrays.stream(row[3].split(",")).mapToInt(Integer::parseInt).toArray()));
		}
		return Collections.unmodifiableList(patches);
	}

	private FarmingPatchCatalog() {}
}
