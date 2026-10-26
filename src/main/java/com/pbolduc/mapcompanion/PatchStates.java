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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * RuneLite's farming decode, captured as data rather than guessed from bit arithmetic.
 *
 * <p>The upstream decoder is package-private. Shipping a split-package bridge or reflecting into
 * the live client makes a harmless client refactor a runtime failure. Instead the bundled table
 * is transcribed from the pinned client's own decoder, and the parity test compares every byte
 * value with the installed client. A new encoding must be reviewed before that test passes.
 * The source, licence and regeneration command are recorded with the table.
 */
@Slf4j
final class PatchStates
{
	static final String EMPTY = "empty";
	static final String GROWING = "growing";
	static final String READY = "ready";
	static final String DISEASED = "diseased";
	static final String DEAD = "dead";
	static final String UNKNOWN = "unknown";
	static final Decoded UNREADABLE = new Decoded(UNKNOWN, null, null, false, null);
	private static final Map<String, Decoded[]> TABLE = load();

	static final class Decoded
	{
		final String state;
		final String produce;
		final Integer stagesLeft;
		final boolean newlyPlanted;
		final Integer growthMinutes;

		Decoded(String state, String produce, Integer stagesLeft, boolean newlyPlanted, Integer growthMinutes)
		{
			this.state = state;
			this.produce = produce;
			this.stagesLeft = stagesLeft;
			this.newlyPlanted = newlyPlanted;
			this.growthMinutes = growthMinutes;
		}
	}

	static Decoded decode(String kind, int value)
	{
		Decoded[] values = TABLE.get(kind);
		return values == null || value < 0 || value >= values.length || values[value] == null
			   ? UNREADABLE : values[value];
	}

	private static Map<String, Decoded[]> load()
	{
		Map<String, Decoded[]> table = new HashMap<>();
		try
			(InputStream in = PatchStates.class.getResourceAsStream("/farming-crops.txt"))
		{
			if (in == null)
			{
				return table;
			}
			try
				(BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				for (String line; (line = reader.readLine()) != null;)
				{
					if (line.startsWith("#") || line.isEmpty())
					{
						continue;
					}
					String[] row = line.split("\\|");
					int value = Integer.parseInt(row[1]);
					int stage = Integer.parseInt(row[4]);
					int stages = Integer.parseInt(row[5]);
					boolean weeds = "Weeds".equals(row[3]);
					String state;
					if (weeds || "EMPTY".equals(row[2]))
					{
						state = EMPTY;
					}
					else if ("HARVESTABLE".equals(row[2]) || ("GROWING".equals(row[2]) && stage >= stages - 1))
					{
						state = READY;
					}
					else if ("GROWING".equals(row[2]))
					{
						state = GROWING;
					}
					else if ("DISEASED".equals(row[2]))
					{
						state = DISEASED;
					}
					else if ("DEAD".equals(row[2]))
					{
						state = DEAD;
					}
					else
					{
						state = UNKNOWN;
					}
					table.computeIfAbsent(row[0], ignored -> new Decoded[256])[value] = new Decoded(
						state, weeds ? null : row[3], GROWING.equals(state) ? stages - 1 - stage : null,
						!weeds && GROWING.equals(state) && stage == 0,
						GROWING.equals(state) ? Integer.parseInt(row[6]) : null);
				}
			}
		}
		catch (Exception e)
		{
			// A damaged resource must never turn an unreadable crop into automatic completion.
			log.warn("Could not read bundled farming states; farming observations unavailable", e);
			table.clear();
		}
		return table;
	}

	private PatchStates() {}
}
