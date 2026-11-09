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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * The Sailing sea charting tasks this character has charted, by the task's id.
 *
 * <p>The game keeps one bit a task in twelve varps, {@code SAILING_CHARTING_COMPLETION_0} to
 * {@code _11}, and the task with id {@code n} is bit {@code n % 32} of varp {@code n / 32}. That
 * is read from the game cache rather than guessed: each of the 358 {@code SAILING_CHARTING_..._COMPLETE}
 * varbits is that one bit, and the task its name gives has that id in the {@code SailingChartingCore}
 * table's unique id column, which is also the id the map's sea charting tasks carry (the evidence is
 * the map's {@code docs/review-evidence/game-readers-2026-11-09.md}).
 */
@Singleton
public class SeaChartReader extends SettlingReader
{
	private static final int[] VARPS = GameReads.ids(GameReads.rows("charting").get(0)[1]);

	private final Client client;

	@Inject
	SeaChartReader(Client client)
	{
		this.client = client;
	}

	/** The charted tasks' ids, lowest first, or null while unsettled or unreadable. */
	@Nullable
	public List<Integer> read(long nowMs)
	{
		if (!settle.settled(nowMs))
		{
			return null;
		}
		List<Integer> charted = new ArrayList<>();
		try
		{
			for (int i = 0; i < VARPS.length; i++)
			{
				int bits = client.getVarpValue(VARPS[i]);
				for (int bit = 0; bit < 32; bit++)
				{
					if ((bits >>> bit & 1) == 1)
					{
						charted.add(i * 32 + bit);
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
		return charted;
	}
}
