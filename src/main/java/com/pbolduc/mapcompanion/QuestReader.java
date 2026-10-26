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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * Travel quests and the separately opted-in full quest log use the same public RuneLite API.
 * The travel whitelist is generated from the map's transport requirements; sharing it does not
 * authorize sharing the rest of the log. Names are the join key on both sides, so unknown names
 * and unreadable states remain unknown rather than failed requirements.
 *
 * <p>The full log settles after each login and is cached for thirty seconds: Quest.getState runs
 * a client script per quest, and rereading the entire log for each position update would put
 * unnecessary work on the game thread. Session changes discard the cached account state.
 */
@Singleton
public class QuestReader
{
	/**
	 * Display names, spelled as the game spells them, because that is also how the map's transport
	 * table spells them — the join is on the string and there is no translation layer.
	 */
	static final Set<String> GATED_QUESTS = Collections.unmodifiableSet(
			Arrays.stream(new String[]
						  {
							  "Between a Rock...",
							  "Biohazard",
							  "Bone Voyage",
							  "Cabin Fever",
							  "Children of the Sun",
							  "Client of Kourend",
							  "Cold War",
							  "Death to the Dorgeshuun",
							  "Desert Treasure I",
							  "Desert Treasure II - The Fallen Empire",
							  "Dragon Slayer II",
							  "Eadgar's Ruse",
							  "Fishing Contest",
							  "Haunted Mine",
							  "Holy Grail",
							  "In Search of the Myreque",
							  "Lunar Diplomacy",
							  "Making Friends with My Arm",
							  "Monkey Madness I",
							  "Monkey Madness II",
							  "Mourning's End Part II",
							  "Nature Spirit",
							  "One Small Favour",
							  "Pandemonium",
							  "Plague City",
							  "Priest in Peril",
							  "Ratcatchers",
							  "Recipe for Disaster - King Awowogei",
							  "Regicide",
							  "Rum Deal",
							  "Sea Slug",
							  "Shades of Mort'ton",
							  "Shilo Village",
							  "Sins of the Father",
							  "Song of the Elves",
							  "Tai Bwo Wannai Trio",
							  "Temple of the Eye",
							  "The Corsair Curse",
							  "The Fremennik Exiles",
							  "The Fremennik Trials",
							  "The Giant Dwarf",
							  "The Grand Tree",
							  "The Great Brain Robbery",
							  "The Path of Glouphrie",
							  "Throne of Miscellania",
							  "Tree Gnome Village",
							  "Troubled Tortugans",
							  "Twilight's Promise",
							  "Watchtower",
						  }).collect(Collectors.toSet()));

	/**
	 * Client spelling to the map's, for the quests where the two differ.
	 *
	 * <p>Below {@link #GATED_QUESTS} rather than beside it, and that placement is load-bearing: the
	 * map's contract test reads every quoted string from {@code GATED_QUESTS} to the {@code collect}
	 * above and fails on any that no transport gates on. A client spelling belongs to the client and
	 * would fail that check while being exactly right.
	 *
	 * <p>Empty since the map's table took the client's spelling of the one quest it held, the King
	 * Awowogei subquest. It stays a table rather than going, because the next difference will not
	 * be noticed either — the failure is a quest that is simply never mentioned — and a table is a
	 * thing {@code QuestReaderTest} can walk.
	 */
	private static final Map<String, String> CLIENT_NAMES;

	static
	{
		Map<String, String> names = new HashMap<>();
		CLIENT_NAMES = Collections.unmodifiableMap(names);
	}

	/**
	 * What the map calls the quest this client calls {@code clientName}.
	 *
	 * <p>The same string back for everything the two already agree on, which today is all of them. Package-private so the test can prove every listed quest is reachable from
	 * {@code Quest.values()} through it.
	 */
	static String mapName(String clientName)
	{
		String mapped = CLIENT_NAMES.get(clientName);
		return mapped == null ? clientName : mapped;
	}

	private final Client client;
	private final SessionSettle logSettle = new SessionSettle(15_000L);
	private Map<String, String> log;
	private long logReadAt;
	private Map<String, String> travel;
	private long travelReadAt;

	@Inject
	QuestReader(Client client)
	{
		this.client = client;
	}

	/**
	 * The finished ones. Must run on the client thread.
	 *
	 * <p>Only {@code FINISHED} counts. A quest in progress stays absent rather than being reported
	 * as some partial state, because the map's router abstains on anything it was not told about —
	 * and "half done" is not something it can act on. The one inference it does make is the safe
	 * direction: a finished quest satisfies a requirement written as "X (partial)", which the map
	 * expands on its own side.
	 */
	public List<String> readFinished(long now)
	{
		List<String> finished = new ArrayList<>();
		for (Map.Entry<String, String> entry : readStates(now).entrySet())
		{
			if ("finished".equals(entry.getValue()))
			{
				finished.add(entry.getKey());
			}
		}
		return finished;
	}

	/**
	 * Every whitelisted quest and where this account stands on it. Must run on the client thread.
	 *
	 * <p><b>Why this exists alongside {@link #readFinished}.</b> A list of finished quests cannot
	 * express the difference between a quest that is not done and a quest that could not be read,
	 * and the map's router needs that difference: the first marks a route unusable and the second
	 * has to leave the reader's own manually entered answer standing. Naming every requested quest and
	 * saying which of the states each is in states this plugin's <i>coverage</i> as well as its
	 * result, so a quest missing from the map is one nobody asked about rather than one somebody
	 * has not done.
	 *
	 * <p>{@code in_progress} is reported and deliberately proves very little. A quest half done can
	 * open a travel gate — Plague City's Ardougne teleport works partway through — but the client
	 * cannot say <i>which</i> step, so the map treats it as neither open nor shut and this does not
	 * pretend otherwise. Only {@code finished} proves a gate.
	 *
	 * <p>The vocabulary is the map's: {@code finished}, {@code in_progress}, {@code not_started},
	 * {@code unknown} — see {@code lib/companion-progress.ts}, which turns anything else it is sent
	 * into {@code unknown} rather than dropping the row.
	 */
	public Map<String, String> readStates(long now)
	{
		/*
		 * Cached for the same thirty seconds as the full log. Each read runs a client script per
		 * quest, 49 of them, and a snapshot goes out every two to six seconds; a quest finished in
		 * the meantime reaches the map one cache later, which is soon enough for a route.
		 */
		if (travel == null || now < travelReadAt || now - travelReadAt >= 30_000L)
		{
			travel = new LinkedHashMap<>();
			for (Quest quest : Quest.values())
			{
				String name = mapName(quest.getName());
				if (GATED_QUESTS.contains(name))
				{
					travel.put(name, stateOf(quest));
				}
			}
			travelReadAt = now;
		}
		return new LinkedHashMap<>(travel);
	}

	void onSessionResumed(long now)
	{
		logSettle.resumed(now);
	}

	/**
	 * Whether quest states can be believed yet. Right after a login every quest reads as not
	 * started, and a confident "not started" marks quest-gated routes unusable on the map, so the
	 * travel quests wait out the same window as the full log.
	 */
	boolean settled(long now)
	{
		return logSettle.settled(now);
	}

	void onSessionEnded()
	{
		logSettle.ended();
		clearLog();
		travel = null;
	}

	void clearLog()
	{
		log = null;
	}

	/** Independent of the travel whitelist and called only under the full-log consent. */
	public Map<String, String> readLog(long now)
	{
		if (!logSettle.settled(now))
		{
			return null;
		}
		if (log == null || now < logReadAt || now - logReadAt >= 30_000L)
		{
			log = new LinkedHashMap<>();
			for (Quest quest : Quest.values())
			{
				log.put(mapName(quest.getName()), stateOf(quest));
			}
			logReadAt = now;
		}
		return new LinkedHashMap<>(log);
	}

	private String stateOf(Quest quest)
	{
		try
		{
			QuestState state = quest.getState(client);
			if (state == QuestState.FINISHED)
			{
				return "finished";
			}
			if (state == QuestState.IN_PROGRESS)
			{
				return "in_progress";
			}
			if (state == QuestState.NOT_STARTED)
			{
				return "not_started";
			}
		}
		catch (RuntimeException e)
		{
			/** Falls through to unknown, which is honest and is what the map abstains on. */
		}
		return "unknown";
	}
}
