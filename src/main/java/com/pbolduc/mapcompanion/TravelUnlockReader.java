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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * Permanent route answers from the client's variables, after the login settling window.
 * Unreadable flags are omitted: the map must preserve manual answers outside our coverage.
 *
 * <p>Ids and predicates follow the shortest-path table at 57590582992c32baf7da71b912819244441ae80b
 * and RuneLite's generated gameval definitions. Kastori and Nemus statues are deliberately
 * absent: that table's 11179/11180 now name agility failures. Weiss's fire is likewise unproven.
 * The house numbers are the same table's teleportation_items.tsv outside-house destinations.
 */
@Singleton
public class TravelUnlockReader
{
	private final Client client;
	private final SessionSettle settle = new SessionSettle(15_000L);
	private static final String[] HOUSES = {
		null, "rimmington", "taverley", "pollnivneach", "rellekka", "brimhaven",
		"yanille", "hosidius", "prifddinas", "aldarin"
	};
	private static final String[] FLAGS = {
		"balloon-castle-wars", "balloon-grand-tree", "balloon-crafting-guild", "balloon-varrock",
		"ates-darkfrost", "ates-twilight-temple", "ates-ralos-rise", "ates-north-aldarin",
		"sailors-marker-port-roberts", "sailors-marker-red-rock", "sailors-marker-deepfin-point",
		"rowboat-vatrachos-island", "rowboat-anglers-retreat", "rowboat-tear-of-the-soul",
		"rowboat-ynysdail", "rowboat-buccaneers-haven", "necropolis-obelisk"
	};
	static final int[] BITS = {
		2869, 2870, 2871, 2872, 11175, 11176, 11177, 11178, 19540, 19541, 19543,
		18351, 18355, 18356, 18370, 18371, 13839
	};
	private static final String[] QUETZALS = {
		"quetzal-fortis-colosseum", "quetzal-kastori", "quetzal-outer-fortis",
		"quetzal-colossal-wyrm-remains", "quetzal-cam-torum", "quetzal-salvager-overlook"
	};
	private static final int[] MASKS = {256, 16384, 128, 64, 32, 2048};
	/** The quetzal landings unlocked, one bit per landing in {@code MASKS}. */
	static final int QUETZALS_UNLOCKED_VARP = 4182;
	/** Hero is rank 9 or higher. The client's gameval names this {@code COLOSSEUM_HIGHEST_WAVE}. */
	static final int COLOSSEUM_GLORY_VARBIT = 11410;
	/** Barbarian Assault's tutorial, finished at 11; gameval's {@code BARBASSAULT_ARENANEWB}. */
	static final int BA_TUTORIAL_VARBIT = 3264;
	static final int HOUSE_LOCATION_VARBIT = 2187;

	@Inject
	TravelUnlockReader(Client client)
	{
		this.client = client;
	}

	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	public void onSessionEnded()
	{
		settle.ended();
	}

	@Nullable
	public Map<String, Boolean> read(long nowMs)
	{
		if (!settle.settled(nowMs))
		{
			return null;
		}
		Map<String, Boolean> out = new LinkedHashMap<>();
		for (int i = 0; i < FLAGS.length; i++)
		{
			int value = bit(BITS[i]);
			if (value == 0 || value == 1)
			{
				out.put(FLAGS[i], value == 1);
			}
		}
		int quetzals;
		try
		{
			quetzals = client.getVarpValue(QUETZALS_UNLOCKED_VARP);
		}
		catch (RuntimeException ex)
		{
			quetzals = -1;
		}
		if (quetzals >= 0)
		{
			for (int i = 0; i < QUETZALS.length; i++)
			{
				out.put(QUETZALS[i], (quetzals & MASKS[i]) != 0);
			}
		}
		int hero = bit(COLOSSEUM_GLORY_VARBIT);
		if (hero >= 0)
		{
			out.put("colosseum-hero-title", hero > 8);
		}
		int tutorial = bit(BA_TUTORIAL_VARBIT);
		if (tutorial >= 0 && tutorial <= 11)
		{
			out.put("barbarian-assault-tutorial", tutorial == 11);
		}
		return out;
	}

	@Nullable
	public String house(long nowMs)
	{
		return settle.settled(nowMs) ? houseFrom(bit(HOUSE_LOCATION_VARBIT)) : null;
	}

	@Nullable
	static String houseFrom(int value)
	{
		return value > 0 && value < HOUSES.length ? HOUSES[value] : null;
	}

	private int bit(int id)
	{
		try
		{
			return client.getVarbitValue(id);
		}
		catch (RuntimeException ex)
		{
			return -1;
		}
	}
}
