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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * Permanent route answers from the client's variables, after the login settling window.
 * Unreadable flags are omitted: the map must preserve manual answers outside our coverage.
 *
 * <p>The routes, the quetzal landings and the house locations are game-reads.txt's travel, quetzal
 * and house rows, which say where each id and predicate came from; GameReadsTest holds every
 * travel varbit to the gameval constant its row names.
 */
@Singleton
public class TravelUnlockReader extends SettlingReader
{
	private final Client client;
	/** A house location's id, row n - 1 for the varbit's n. */
	private static final List<String[]> HOUSES = GameReads.rows("house");
	/** A route's id and the varbit that reads 1 once it is unlocked, in the order the map is sent them. */
	private static final List<String[]> FLAGS = GameReads.rows("travel");
	static final int[] BITS = FLAGS.stream().mapToInt(row -> GameReads.ids(row[1])[0]).toArray();
	/** A landing's id and its bit in {@code QUETZALS_UNLOCKED_VARP}. */
	private static final List<String[]> QUETZALS = GameReads.rows("quetzal");
	/** The quetzal landings unlocked, one bit per landing in {@code QUETZALS}. */
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

	@Nullable
	public Map<String, Boolean> read(long nowMs)
	{
		if (!settle.settled(nowMs))
		{
			return null;
		}
		Map<String, Boolean> out = new LinkedHashMap<>();
		for (int i = 0; i < BITS.length; i++)
		{
			int value = bit(BITS[i]);
			if (value == 0 || value == 1)
			{
				out.put(FLAGS.get(i)[0], value == 1);
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
			for (String[] landing : QUETZALS)
			{
				out.put(landing[0], (quetzals & Integer.parseInt(landing[1])) != 0);
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
		return value > 0 && value <= HOUSES.size() ? HOUSES.get(value - 1)[0] : null;
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
