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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What counts as watching a quest or a diary tier being finished.
 *
 * <p>The failure this guards is a flood: a career announced as news, on a login, a hop or a
 * sharing choice turned back on. Every case below is a reading that says "done" and must not be
 * announced, beside the one shape that must.
 */
public class CompletionWatcherTest
{
	@Test
	public void theFirstReadingIsABaselineHoweverMuchItSaysIsDone()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		assertTrue(watcher.quests(quests("Dragon Slayer I", "finished", "Cook's Assistant", "finished")).isEmpty());
		assertTrue(watcher.diaries(diary("Varrock", true, true, false, false)).isEmpty());
	}

	@Test
	public void aQuestSeenGoingFromNotDoneToDoneIsAnnouncedOnce()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Dragon Slayer I", "in_progress", "Lost City", "not_started"));
		assertEquals(Collections.singletonList("Dragon Slayer I"),
			watcher.quests(quests("Dragon Slayer I", "finished", "Lost City", "not_started")));
		assertTrue("still finished is not finished again",
			watcher.quests(quests("Dragon Slayer I", "finished", "Lost City", "not_started")).isEmpty());
		assertEquals("from not started counts as well: a short quest can begin and end between readings",
			Collections.singletonList("Lost City"), watcher.quests(quests("Lost City", "finished")));
	}

	@Test
	public void startingAQuestIsNotNews()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Dragon Slayer I", "not_started"));
		assertTrue(watcher.quests(quests("Dragon Slayer I", "in_progress")).isEmpty());
	}

	@Test
	public void aStateThatWasUnknownIsNotNotDone()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Dragon Slayer I", "in_progress"));
		watcher.quests(quests("Dragon Slayer I", "unknown"));
		assertTrue("a blank reading drops what came before it",
			watcher.quests(quests("Dragon Slayer I", "finished")).isEmpty());
	}

	@Test
	public void noReadingKeepsTheBaselineAndAResetStartsAnother()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Dragon Slayer I", "in_progress"));
		assertTrue("the settling window says nothing", watcher.quests(null).isEmpty());
		assertEquals(Collections.singletonList("Dragon Slayer I"), watcher.quests(quests("Dragon Slayer I", "finished")));

		watcher.quests(quests("Dragon Slayer II", "in_progress"));
		watcher.reset();
		assertTrue("after a boundary the next reading is a baseline again",
			watcher.quests(quests("Dragon Slayer II", "finished")).isEmpty());

		watcher.quests(quests("Monkey Madness I", "in_progress"));
		watcher.forgetQuests();
		assertTrue(watcher.quests(quests("Monkey Madness I", "finished")).isEmpty());
	}

	@Test
	public void aQuestTheLastReadingDidNotCoverIsABaseline()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Lost City", "in_progress"));
		/** The source widened, from the travel quests to the full log: what is new is a baseline. */
		assertTrue(watcher.quests(quests("Lost City", "in_progress", "Dragon Slayer I", "finished")).isEmpty());
		/** And a quest the narrower source dropped is not remembered for when it comes back. */
		watcher.quests(quests("Lost City", "in_progress"));
		assertTrue(watcher.quests(quests("Lost City", "in_progress", "Dragon Slayer I", "finished")).isEmpty());
	}

	@Test
	public void recipeForDisastersPartsAreNotQuestsButTheWholeIs()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.quests(quests("Recipe for Disaster - Pirate Pete", "in_progress", "Recipe for Disaster", "in_progress"));
		assertEquals(Collections.singletonList("Recipe for Disaster"),
			watcher.quests(quests("Recipe for Disaster - Pirate Pete", "finished", "Recipe for Disaster", "finished")));
	}

	@Test
	public void aDiaryTierIsAnnouncedByItsOwnFlag()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		watcher.diaries(diary("Varrock", true, false, true, false));
		List<String[]> finished = watcher.diaries(diary("Varrock", true, true, true, false));
		assertEquals("medium, not hard, though the run from easy now reaches hard", 1, finished.size());
		assertEquals(Arrays.asList("Varrock", "medium"), Arrays.asList(finished.get(0)));
		assertTrue(watcher.diaries(diary("Varrock", true, true, true, false)).isEmpty());
	}

	@Test
	public void aTierOutOfDoubtIsNotAnnounced()
	{
		CompletionWatcher watcher = new CompletionWatcher();
		/** Karamja's easy flag at 1: neither done nor plainly not done. */
		watcher.diaries(diary("Karamja", null, false, false, false));
		assertTrue(watcher.diaries(diary("Karamja", true, false, false, false)).isEmpty());
		watcher.forgetDiaries();
		watcher.diaries(diary("Karamja", true, false, false, false));
		assertEquals(Arrays.asList("Karamja", "medium"),
			Arrays.asList(watcher.diaries(diary("Karamja", true, true, false, false)).get(0)));
	}

	private static Map<String, String> quests(String... nameThenState)
	{
		Map<String, String> states = new LinkedHashMap<>();
		for (int i = 0; i < nameThenState.length; i += 2)
		{
			states.put(nameThenState[i], nameThenState[i + 1]);
		}
		return states;
	}

	private static Map<String, Boolean[]> diary(String region, Boolean... tiers)
	{
		Map<String, Boolean[]> flags = new LinkedHashMap<>();
		flags.put(region, tiers);
		return flags;
	}
}
