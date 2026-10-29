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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

/**
 * Time Tracking owns the region-gated observation and its account-scoped history. Reading its
 * public configuration avoids a second observer disagreeing with the birdhouse timer in RuneLite.
 * The timestamp is the first sighting of this state, not proof of when the player seeded it.
 * The map therefore presents fifty minutes as an upper estimate, never a confirmed harvest.
 */
@Singleton
public class BirdhouseReader
{
	private final java.util.function.Function<String, String> stored;
	private final SessionSettle settle = new SessionSettle(15_000L);
	private static final String[] IDS = {"meadow-north", "meadow-south", "valley-north", "valley-south"};
	static final int[] VARPS = {1626, 1627, 1628, 1629};

	@Inject
	BirdhouseReader(ConfigManager config)
	{
		this(key -> config.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, key));
	}

	/** Account-scoped read seam for the harness; no writes to another plugin's configuration. */
	BirdhouseReader(java.util.function.Function<String, String> stored)
	{
		this.stored = stored;
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
	public List<CompanionSnapshot.Birdhouse> read(long nowMs)
	{
		if (!settle.settled(nowMs)) return null;
		List<CompanionSnapshot.Birdhouse> out = new ArrayList<>();
		for (int i = 0; i < IDS.length; i++)
		{
			String raw = stored.apply(TimeTrackingConfig.BIRD_HOUSE + "." + VARPS[i]);
			CompanionSnapshot.Birdhouse reading = decode(IDS[i], raw, nowMs);
			if (reading != null) out.add(reading);
		}
		return out.isEmpty() ? null : out;
	}

	@Nullable
	static CompanionSnapshot.Birdhouse decode(String id, String raw, long nowMs)
	{
		if (raw == null) return null;
		String[] parts = raw.split(":", -1);
		if (parts.length != 2) return null;
		try
		{
			int value = Integer.parseInt(parts[0]);
			long atMs = Math.multiplyExact(Long.parseLong(parts[1]), 1000L);
			// Nine house types, three states each: BirdHouseState.fromVarpValue in 1.12.39.
			if (value < 0 || value > 27 || atMs < 0 || atMs > nowMs || nowMs - atMs > 6 * 60 * 60_000L) return null;
			CompanionSnapshot.Birdhouse out = new CompanionSnapshot.Birdhouse();
			out.id = id;
			out.state = value == 0 ? "empty" : value % 3 == 0 ? "seeded" : "built";
			out.observedMsAgo = nowMs - atMs;
			return out;
		}
		catch (NumberFormatException | ArithmeticException ex)
		{
			return null;
		}
	}
}
