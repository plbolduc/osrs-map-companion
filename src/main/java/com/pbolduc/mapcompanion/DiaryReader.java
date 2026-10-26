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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * Which achievement diaries this account has finished, and which of their rewards it has taken.
 *
 * <p>The map ranks routes on what an account can actually use, and a good number of its transports
 * are gated on a diary: the Ardougne cloak's teleport to the Monastery, the medium Varrock
 * teleport landing at the Grand Exchange, the Kandarin hard teleport landing at Seers' bank, a
 * handful of agility shortcuts. Before this the map could only ask the reader to tick those boxes
 * by hand in its Settings, which is a form nobody fills in and which goes stale the day after they
 * do. The client already knows.
 *
 * <h2>Finished is not the same as collected</h2>
 *
 * <p>Two answers per region, not one. Completing a tier's tasks and going to the diary's NPC to
 * take the reward are separate things the game records separately, and only the second one means
 * the teleport on the cloak works. Guessing either from the other would have the map recommend a
 * route through an item the player has never held — the confident wrong answer this plugin exists
 * to refuse to give — so both flags are read and both are sent, and the map decides.
 *
 * <h2>Three answers, and why "unknown" is one of them</h2>
 *
 * <p>Per region the wire carries a tier, {@code none}, or {@code unknown}, and the third is
 * load-bearing all the way through to the map's router: {@code none} says "asked, nothing done"
 * and marks diary-gated routes unusable, while {@code unknown} says "could not tell" and leaves
 * the reader's own manually entered answer standing. A reader who has done every diary and gets
 * {@code none} because this class guessed is worse off than one who gets nothing from it at all.
 *
 * <p>So this abstains in three situations. While the session is still settling, because every
 * varbit reads zero for a moment after login and zero here means "never done" — see
 * {@link SessionSettle}. When a flag cannot be read at all. And when a flag is set to a value
 * whose meaning this file cannot defend, which is a real case: see Karamja below.
 *
 * <h2>Karamja</h2>
 *
 * <p>Karamja's three lower tiers predate the rest of the system and behave differently. Their
 * flags are named {@code ATJUN_*_DONE} rather than {@code KARAMJA_DIARY_*_COMPLETE}, and the map's
 * own {@code lib/diaries.ts} records that they store completion as 2 where every other region
 * stores 1 — which is why the transport table upstream writes the Karamja hard condition as
 * {@code 3611=2}. Nothing available here says what a 1 in those three means, so a 1 is read as
 * neither done nor not done: the region's answer becomes {@code unknown} unless a higher tier
 * settles it. The elite tier is a modern flag and is read at 1, which is how RuneLite's own
 * {@code CoordinateClue} reads it.
 */
@Singleton
public class DiaryReader
{
	/**
	 * How long a session must be up before a diary flag is believed.
	 *
	 * <p>Same number and same reason as {@link FarmingObserver}: a zero read in the login window is
	 * not a missing answer, it is a confident claim that nothing has ever been finished.
	 */
	private static final long SETTLE_MS = 15_000L;

	/** Easy, medium, hard, elite — the order every id table below is written in. */
	private static final String[] TIERS = {"easy", "medium", "hard", "elite"};

	/** The two answers that are not a tier. Spelled as {@code lib/companion-progress.ts} spells them. */
	static final String NONE = "none";
	static final String UNKNOWN = "unknown";

	/** A flag this client could not read at all, kept distinct from a flag that reads zero. */
	static final int UNREADABLE = -1;

	/** Every other region's flags mean "done" at 1; Karamja's three older ones at 2. See above. */
	private static final int[] DONE_AT_ONE = {1, 1, 1, 1};
	private static final int[] KARAMJA_DONE_AT = {2, 2, 2, 1};

	/**
	 * One region, named as the game's Achievement Diary tab names it.
	 *
	 * <p>The names are the join to the map — {@code lib/diaries.ts} spells the same twelve, and
	 * there is no translation layer, so "Kourend &amp; Kebos" being spelled "Kourend" here would
	 * simply never match and would fail silently forever.
	 */
	private static final class Region
	{
		final String name;
		final int[] completion;
		final int[] rewards;
		final int[] completeAt;

		Region(String name, int[] completion, int[] rewards, int[] completeAt)
		{
			this.name = name;
			this.completion = completion;
			this.rewards = rewards;
			this.completeAt = completeAt;
		}
	}

	private static final List<Region> REGIONS = Arrays.asList(
			new Region("Ardougne", VarbitIds.DIARY_ARDOUGNE, VarbitIds.DIARY_ARDOUGNE_REWARDS, DONE_AT_ONE),
			new Region("Desert", VarbitIds.DIARY_DESERT, VarbitIds.DIARY_DESERT_REWARDS, DONE_AT_ONE),
			new Region("Falador", VarbitIds.DIARY_FALADOR, VarbitIds.DIARY_FALADOR_REWARDS, DONE_AT_ONE),
			new Region("Fremennik", VarbitIds.DIARY_FREMENNIK, VarbitIds.DIARY_FREMENNIK_REWARDS, DONE_AT_ONE),
			new Region("Kandarin", VarbitIds.DIARY_KANDARIN, VarbitIds.DIARY_KANDARIN_REWARDS, DONE_AT_ONE),
			new Region("Karamja", VarbitIds.DIARY_KARAMJA, VarbitIds.DIARY_KARAMJA_REWARDS, KARAMJA_DONE_AT),
			new Region("Kourend & Kebos", VarbitIds.DIARY_KOUREND, VarbitIds.DIARY_KOUREND_REWARDS, DONE_AT_ONE),
			new Region("Lumbridge & Draynor", VarbitIds.DIARY_LUMBRIDGE, VarbitIds.DIARY_LUMBRIDGE_REWARDS, DONE_AT_ONE),
			new Region("Morytania", VarbitIds.DIARY_MORYTANIA, VarbitIds.DIARY_MORYTANIA_REWARDS, DONE_AT_ONE),
			new Region("Varrock", VarbitIds.DIARY_VARROCK, VarbitIds.DIARY_VARROCK_REWARDS, DONE_AT_ONE),
			new Region("Western Provinces", VarbitIds.DIARY_WESTERN, VarbitIds.DIARY_WESTERN_REWARDS, DONE_AT_ONE),
			new Region("Wilderness", VarbitIds.DIARY_WILDERNESS, VarbitIds.DIARY_WILDERNESS_REWARDS, DONE_AT_ONE)
		);

	private final Client client;
	private final SessionSettle settle = new SessionSettle(SETTLE_MS);

	@Inject
	private DiaryReader(Client client)
	{
		this.client = client;
	}

	/** The client is up. Starts the settling clock, but only if it was not already running. */
	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	/** Logged out or hopping. Every flag is about to read zero, so believe none of them. */
	public void onSessionEnded()
	{
		settle.ended();
	}

	/**
	 * The highest completed tier per region, or null when there is nothing honest to say yet.
	 *
	 * <p>Null rather than twelve {@code unknown}s during the login window: the map reads an absent
	 * field as "never asked", which is the same statement and costs a hundred bytes less on a wire
	 * with a sixteen kilobyte cap.
	 *
	 * <p>Must run on the client thread — every read below is live game state. Read afresh each time
	 * rather than cached: it is a hundred array lookups, it happens at most once per post, and a
	 * cache here would need invalidating from {@code VarbitChanged}, which reports {@code -1} for
	 * the varbit id when the underlying varp is what moved.
	 */
	@Nullable
	public Map<String, String> readCompleted(long nowMs)
	{
		return read(nowMs, true);
	}

	/** The highest reward tier collected per region, on the same terms as {@link #readCompleted}. */
	@Nullable
	public Map<String, String> readRewards(long nowMs)
	{
		return read(nowMs, false);
	}

	@Nullable
	private Map<String, String> read(long nowMs, boolean completion)
	{
		if (!settle.settled(nowMs))
		{
			return null;
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (Region region : REGIONS)
		{
			int[] ids = completion ? region.completion : region.rewards;
			int[] values = new int[ids.length];
			for (int i = 0; i < ids.length; i++)
			{
				values[i] = value(ids[i]);
			}
			/**
			 * A reward is a flag somebody either took or did not, so it is read at 1 for every
			 * region including Karamja. The two-valued reading is about the older completion flags
			 * specifically, and applying it here would refuse to report rewards that are there.
			 */
			out.put(region.name, completion
				? completedTierFrom(values, region.completeAt)
				: tierFrom(values, DONE_AT_ONE));
		}
		return out;
	}

	private int value(int varbit)
	{
		try
		{
			return client.getVarbitValue(varbit);
		}
		catch (RuntimeException e)
		{
			/** Unreadable is its own answer here, and it must not collapse into "not done". */
			return UNREADABLE;
		}
	}

	/**
	 * The highest tier done with every tier below it done too, {@code none}, or {@code unknown}.
	 *
	 * <p>Completion is not cumulative: tasks can be done from any tier in any order, so a finished
	 * medium says nothing about easy. The map reads a tier as "this and everything below it", which
	 * is also what claiming its reward needs, so the answer is the run of finished tiers from easy
	 * up. Where that run stops on a flag that is doubt rather than a plain zero, the region is
	 * unknown — the run might go further. Tiers above a plain zero cannot change the answer.
	 *
	 * <p>Package-private and static so the whole table of cases can be driven without a client.
	 */
	static String completedTierFrom(int[] values, int[] completeAt)
	{
		int tiers = Math.min(values.length, TIERS.length);
		int done = 0;
		while (done < tiers && values[done] != UNREADABLE && values[done] >= completeAt[done])
		{
			done++;
		}
		if (done < tiers && (values[done] == UNREADABLE || values[done] > 0))
		{
			/* Unreadable, or set to a value this file cannot defend reading as completion. */
			return UNKNOWN;
		}
		return done == 0 ? NONE : TIERS[done - 1];
	}

	/**
	 * The highest reward tier four flag values prove, {@code none}, or {@code unknown}.
	 *
	 * <p>Rewards are cumulative where completion is not: a tier's reward can only be claimed once
	 * every tier below it is complete, so a proven hard reward settles easy and medium whatever
	 * their flags say. What makes this worth a function of its own is what happens above the
	 * highest tier that is plainly claimed: doubt there means the honest answer for the region is
	 * that we do not know, because reporting the lower tier would be reporting a ceiling that may
	 * not be one.
	 *
	 * <p>Package-private and static so the whole table of cases can be driven without a client.
	 */
	static String tierFrom(int[] values, int[] completeAt)
	{
		int highestDone = -1;
		int highestDoubt = -1;
		for (int i = 0; i < values.length && i < TIERS.length; i++)
		{
			int value = values[i];
			if (value == UNREADABLE)
			{
				highestDoubt = i;
			}
			else if (value >= completeAt[i])
			{
				highestDone = i;
			}
			else if (value > 0)
			{
				/** Set, but not to a value this file can defend reading as completion. */
				highestDoubt = i;
			}
		}
		if (highestDoubt > highestDone)
		{
			return UNKNOWN;
		}
		return highestDone < 0 ? NONE : TIERS[highestDone];
	}
}
