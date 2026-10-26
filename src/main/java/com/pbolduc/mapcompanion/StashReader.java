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
import net.runelite.api.ScriptID;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;

/**
 * The same built-only check RuneLite's EmoteClue uses. The script says nothing about stored
 * items. Cache for a minute so a position update never runs a hundred scripts on the game thread.
 */
@Singleton
public class StashReader
{
	private final Client client;
	private final SessionSettle settle = new SessionSettle(15_000L);
	private Map<Integer, Boolean> cached;
	private long readAt;

	@Inject
	StashReader(Client client)
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
		cached = null;
	}

	@Nullable
	public Map<Integer, Boolean> read(long nowMs)
	{
		if (!settle.settled(nowMs)) return null;
		if (cached != null && nowMs - readAt < 60_000L) return cached;
		Map<Integer, Boolean> out = new LinkedHashMap<>();
		for (STASHUnit unit : STASHUnit.values())
		{
			try
			{
				client.runScript(ScriptID.WATSON_STASH_UNIT_CHECK, unit.getObjectId(), 0, 0, 0);
				int[] stack = client.getIntStack();
				if (stack != null && stack.length > 0 && (stack[0] == 0 || stack[0] == 1))
				{
					out.put(unit.getObjectId(), stack[0] == 1);
				}
			}
			catch (RuntimeException ex)
			{
				// An absent script or definition gives no answer, never "unbuilt".
			}
		}
		cached = out;
		readAt = nowMs;
		return cached;
	}
}
