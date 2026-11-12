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
import javax.annotation.Nullable;

/**
 * The quests and achievement diary tiers this client watched being finished.
 *
 * <p>An announcement says "this just happened", and the readers it is built on cannot say that:
 * {@link QuestReader} and {@link DiaryReader} report where an account <i>stands</i>, and the first
 * reading of a session is a career. Announcing what that reading says is done would tell a group
 * that somebody had just finished every quest they ever played. So a completion is announced only
 * when two readings taken by this object, one after the other, show it going from a known "not
 * done" to done. Nothing else counts: a reading this object has nothing before is a baseline, and
 * a state that was unknown before is not "not done".
 *
 * <p>The baseline is thrown away, and the next reading becomes the new one, whenever the readings
 * on either side of it might not be about the same watched stretch of play: a login, a world hop,
 * a lost connection to the game, a change of character, a new pairing, and any of the matching
 * sharing choices being turned off. The last is what makes turning one back on announce nothing:
 * the quest finished while it was off was not watched by anybody who was allowed to say so.
 *
 * <p>Whole quests and whole tiers only. A quest that has been started is not news here, and neither
 * is a single diary task; the readers below never see either, which is the point of building on
 * them. The Recipe for Disaster subquests are skipped: RuneLite lists them beside the quest they
 * are parts of, and the map does not take them as quests of their own.
 *
 * <p>Synchronized, because the readings arrive on the client thread and a sharing choice is
 * withdrawn on whichever thread wrote it.
 */
final class CompletionWatcher
{
	/** The quest-state words {@link QuestReader} reports. Only the first is done. */
	private static final String FINISHED = "finished";
	private static final String IN_PROGRESS = "in_progress";
	private static final String NOT_STARTED = "not_started";

	/** RuneLite's prefix for the parts of Recipe for Disaster, which the whole is announced for. */
	private static final String SUBQUEST_PREFIX = "Recipe for Disaster - ";

	/** The last reading of each quest, by the map's name for it; null until there is one. */
	private Map<String, String> quests;

	/** The last reading of each region's four tier flags; null until there is one. */
	private Map<String, Boolean[]> diaries;

	/**
	 * The quests finished since the last reading, and this reading kept for the next.
	 *
	 * <p>{@code states} is either reader's map of quest name to state, or null while it has nothing
	 * to say — the settling window — which keeps the baseline as it was. A quest read as unknown
	 * leaves the baseline rather than being kept as what it was before: if a reading went blank, the
	 * one before it is no longer something this can vouch for.
	 */
	synchronized List<String> quests(@Nullable Map<String, String> states)
	{
		if (states == null)
		{
			return Collections.emptyList();
		}
		List<String> finished = new ArrayList<>();
		Map<String, String> next = new HashMap<>();
		for (Map.Entry<String, String> entry : states.entrySet())
		{
			String name = entry.getKey();
			String state = entry.getValue();
			if (name == null || !(FINISHED.equals(state) || IN_PROGRESS.equals(state) || NOT_STARTED.equals(state)))
			{
				continue;
			}
			String before = quests == null ? null : quests.get(name);
			if (FINISHED.equals(state) && (IN_PROGRESS.equals(before) || NOT_STARTED.equals(before))
				&& !name.startsWith(SUBQUEST_PREFIX))
			{
				finished.add(name);
			}
			next.put(name, state);
		}
		quests = next;
		return finished;
	}

	/**
	 * The diary tiers finished since the last reading, as {region, tier}, and this reading kept.
	 *
	 * <p>{@code tiers} is {@link DiaryReader#readTiers}, or null while it settles. Each tier is its
	 * own flag, so finishing medium after hard announces medium; a tier announces only from a plain
	 * zero, never from a flag that could not be read.
	 */
	synchronized List<String[]> diaries(@Nullable Map<String, Boolean[]> tiers)
	{
		if (tiers == null)
		{
			return Collections.emptyList();
		}
		List<String[]> finished = new ArrayList<>();
		Map<String, Boolean[]> next = new HashMap<>();
		for (Map.Entry<String, Boolean[]> entry : tiers.entrySet())
		{
			Boolean[] now = entry.getValue();
			if (entry.getKey() == null || now == null)
			{
				continue;
			}
			Boolean[] before = diaries == null ? null : diaries.get(entry.getKey());
			for (int i = 0; i < now.length && i < DiaryReader.TIERS.length; i++)
			{
				if (Boolean.TRUE.equals(now[i]) && before != null && i < before.length && Boolean.FALSE.equals(before[i]))
				{
					finished.add(new String[]{entry.getKey(), DiaryReader.TIERS[i]});
				}
			}
			next.put(entry.getKey(), now.clone());
		}
		diaries = next;
		return finished;
	}

	/** Quest sharing went off or changed source: the next quest reading is a baseline. */
	synchronized void forgetQuests()
	{
		quests = null;
	}

	/** Diary sharing went off: the next diary reading is a baseline. */
	synchronized void forgetDiaries()
	{
		diaries = null;
	}

	/** A session, character, pairing or announcement-sharing boundary: both start again. */
	synchronized void reset()
	{
		quests = null;
		diaries = null;
	}
}
