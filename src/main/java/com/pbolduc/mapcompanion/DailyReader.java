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
 * Which of the dailies the map plans a run through have been collected today.
 *
 * <p>The varbits and the rule are RuneLite's own Daily Task Indicators plugin's, kept in
 * game-reads.txt: a claim varbit reaching its count means taken, below it means waiting. For most
 * the count is one. Nightmare Zone's herb boxes count up to 15 a day, and Robin's bonemeal to 13 for
 * each Morytania diary tier from medium, so a player who took some is told the rest is still there;
 * without the medium tier Robin offers nothing, and nothing is said. The Miscellania kingdom has no
 * claim varbit and is never answered.
 *
 * <p><b>Only the day the session began.</b> The game resets these at midnight UTC, and nothing here
 * says whether a client that stays logged in across midnight sees the reset at once. So a session
 * that has crossed midnight says nothing until the next login rather than pass yesterday off as today.
 */
@Singleton
public class DailyReader extends SettlingReader
{
	private static final List<String[]> ROWS = GameReads.rows("daily");

	private final Client client;
	/** The UTC day of the settled session's first reading, or -1 before it. */
	private long day = -1;

	@Inject
	DailyReader(Client client)
	{
		this.client = client;
	}

	@Override
	public void onSessionEnded()
	{
		super.onSessionEnded();
		day = -1;
	}

	/** Each daily read, true when collected today; null while that cannot be known. */
	@Nullable
	public Map<String, Boolean> read(long nowMs)
	{
		long today = Math.floorDiv(nowMs, 86_400_000L);
		if (!settle.settled(nowMs) || day >= 0 && day != today)
		{
			return null;
		}
		day = today;
		Map<String, Boolean> out = new LinkedHashMap<>();
		try
		{
			for (String[] row : ROWS)
			{
				int allowance = Integer.parseInt(row[2]);
				if (row.length > 3)
				{
					int tiers = 0;
					for (int varbit : GameReads.ids(row[3]))
					{
						tiers += client.getVarbitValue(varbit) == 1 ? 1 : 0;
					}
					allowance *= tiers;
				}
				if (allowance > 0)
				{
					out.put(row[0], client.getVarbitValue(GameReads.ids(row[1])[0]) >= allowance);
				}
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
		return out;
	}
}
