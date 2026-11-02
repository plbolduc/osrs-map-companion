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

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** The two channels cannot revive another task, character, choice or relay's cached list. */
public class SlayerStateTest
{
	private static final String A = "relay-epoch-first";
	private static final String B = "relay-epoch-second";
	private CompanionSnapshot.Slayer task(String name, int remaining)
	{
		CompanionSnapshot.Slayer task = new CompanionSnapshot.Slayer();
		task.taskName = name;
		task.remaining = remaining;
		return task;
	}
	private RelayResponse.Directive answer(String epoch, long revision, String task)
	{
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.relayEpoch = epoch;
		directive.slayerRevision = revision;
		if (task != null)
		{
			directive.slayerOptions = new RelayResponse.SlayerOptions();
			directive.slayerOptions.taskName = task;
			directive.slayerOptions.order = "best";
			directive.slayerOptions.hideWilderness = true;
		}
		return directive;
	}
	@Test
	public void countsStayLocalAndAcknowledgedListsSurviveAnOmittedField()
	{
		SlayerState state = new SlayerState();
		state.local("me", task("Dust Devils", 100), "best", true);
		RelayResponse.Directive first = answer(A, 3, "Dust Devils");
		state.accept("me", first, 1_000);
		state.local("me", task("Dust Devils", 99), "best", true);
		assertSame(first.slayerOptions, state.view(1_010).options);
		assertEquals(Integer.valueOf(99), state.view(1_010).remaining);
		assertEquals(3, state.preferences().seenRevision);
		state.accept("me", answer(A, 0, null), 122_000);
		assertSame(first.slayerOptions, state.view(122_001).options);
		assertFalse("an acknowledged quiet list is fresh while heartbeats still arrive", state.view(122_001).stale);
		assertTrue(state.view(243_000).stale);
		state.accept("me", answer(A, 2, "Dust Devils"), 244_000);
		assertSame(first.slayerOptions, state.view(244_001).options);
	}
	@Test
	public void taskAreaAndChoiceChangesHideTheOldAnswerBeforeTheNextPost()
	{
		SlayerState state = new SlayerState();
		state.local("me", task("Dust Devils", 100), "best", true);
		state.accept("me", answer(A, 1, "Dust Devils"), 1_000);
		state.local("me", task("Gargoyles", 100), "best", true);
		assertNull(state.view(1_001).options);
		assertEquals(0, state.preferences().seenRevision);
		state.accept("me", answer(A, 2, "Dust Devils"), 1_002);
		assertNull(state.view(1_003).options);
		state.accept("me", answer(A, 3, "Gargoyles"), 1_004);
		state.choices("single", false);
		assertNull(state.view(1_005).options);
		state.accept("me", answer(A, 4, "Gargoyles"), 1_006);
		assertNull(state.view(1_007).options);
		RelayResponse.Directive changed = answer(A, 5, "Gargoyles");
		changed.slayerOptions.order = "single";
		changed.slayerOptions.hideWilderness = false;
		state.accept("me", changed, 1_008);
		assertSame(changed.slayerOptions, state.view(1_009).options);
		CompanionSnapshot.Slayer konar = task("Gargoyles", 100);
		konar.areaName = "Slayer Tower";
		state.local("me", konar, "single", false);
		state.accept("me", changed, 1_010);
		assertNull(state.view(1_011).options);
	}
	/**
	 * A new ranking choice for the same task keeps the places it hid, for the card to show greyed
	 * while the map ranks again. They are never the options, so nothing that acts reads them; the new
	 * answer replaces them, and another task drops them at once.
	 */
	@Test
	public void aNewChoiceKeepsTheHiddenPlacesOnlyToShowUntilTheNewOnesArrive()
	{
		SlayerState state = new SlayerState();
		state.local("me", task("Gargoyles", 143), "best", true);
		RelayResponse.Directive best = answer(A, 1, "Gargoyles");
		state.accept("me", best, 1_000);
		state.choices("single", true);
		SlayerState.View waiting = state.view(1_001);
		assertNull(waiting.options);
		assertSame(best.slayerOptions, waiting.previous);
		assertEquals("single", waiting.order);
		assertEquals(0, state.preferences().seenRevision);
		state.choices("single", false);
		assertSame("a second change keeps the last places received", best.slayerOptions, state.view(1_002).previous);
		RelayResponse.Directive single = answer(A, 2, "Gargoyles");
		single.slayerOptions.order = "single";
		single.slayerOptions.hideWilderness = false;
		state.accept("me", single, 1_003);
		assertSame(single.slayerOptions, state.view(1_004).options);
		assertNull(state.view(1_004).previous);
		state.choices("best", false);
		assertSame(single.slayerOptions, state.view(1_005).previous);
		state.local("me", task("Dust Devils", 100), "best", false);
		assertNull("another task's places are never shown", state.view(1_006).previous);
	}

	@Test
	public void aRestartResetsTheCounterAndRejectsDelayedFramesFromTheRetiredProcess()
	{
		SlayerState state = new SlayerState();
		state.local("me", task("Dust Devils", 100), "best", true);
		state.accept("me", answer(A, 7, "Dust Devils"), 1_000);
		state.accept("me", answer(B, 0, null), 1_001);
		assertNull(state.view(1_002).options);
		assertEquals(0, state.preferences().seenRevision);
		RelayResponse.Directive restarted = answer(B, 1, "Dust Devils");
		state.accept("me", restarted, 1_003);
		state.accept("me", answer(A, 8, "Dust Devils"), 1_004);
		assertSame(restarted.slayerOptions, state.view(1_005).options);
		assertEquals(1, state.preferences().seenRevision);
	}
	@Test
	public void clearingAndChangingCharacterRejectOldChannelsEvenForTheSameTask()
	{
		SlayerState state = new SlayerState();
		state.local("first", task("Dust Devils", 100), "best", true);
		state.accept("first", answer(A, 7, "Dust Devils"), 1_000);
		state.local("second", task("Dust Devils", 50), "best", true);
		state.accept("first", answer(A, 8, "Dust Devils"), 1_001);
		assertNull(state.view(1_002).options);
		state.accept("second", answer(B, 1, "Dust Devils"), 1_003);
		assertEquals(1, state.preferences().seenRevision);
		state.clear();
		state.accept("second", answer(B, 2, "Dust Devils"), 1_004);
		assertNull(state.view(1_005).options);
	}
	@Test
	public void aRemovalIsAcknowledgedWithoutKeepingThePreviousTaskList()
	{
		SlayerState state = new SlayerState();
		state.local("me", task("Dust Devils", 100), "best", true);
		state.accept("me", answer(A, 1, "Dust Devils"), 1_000);
		state.local("me", null, "best", true);
		state.accept("me", answer(A, 2, null), 1_001);
		assertNull(state.view(1_002).options);
		assertEquals(2, state.preferences().seenRevision);
	}

	@Test
	public void boundsAndCleansTheListWithoutLosingOtherReplyFields() throws Exception
	{
		String fixture = new String(getClass().getResourceAsStream("/contract/slayer-options.json").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		Gson gson = new Gson();
		RelayResponse.SlayerOptions options = gson.fromJson(fixture, RelayResponse.SlayerOptions.class);
		options.rows = new java.util.ArrayList<>();
		for (int i = 0; i < 100; i++)
		{
			RelayResponse.SlayerSpot row = gson.fromJson(fixture, RelayResponse.SlayerOptions.class).rows.get(0);
			row.id = "slayer:Gargoyles#" + i + ":3432,9937,3";
			row.rank = 1;
			row.name = "A".repeat(400);
			row.labels = java.util.Collections.nCopies(30, "B".repeat(400));
			options.rows.add(row);
		}
		RelayResponse.SlayerOptions clean = SlayerWire.normalize(options);
		assertEquals(80, clean.rows.size());
		assertEquals(120, clean.rows.get(0).name.length());
		assertEquals(12, clean.rows.get(0).labels.size());
		assertEquals(160, clean.rows.get(0).labels.get(0).length());
		assertTrue(SlayerPanel.wikiUrl("https://example.test/<tag> ?x=1").startsWith("https://oldschool.runescape.wiki/w/"));
		assertFalse(SlayerPanel.wikiUrl("a#b").contains("#"));
	}

	@Test
	public void anUnreadableFeatureCannotLoseThePairingSecretOrRewardAcknowledgement()
	{
		RelayResponse reply = new Gson().fromJson("{\"memberSecret\":\"keep-this\",\"acknowledgedMilestones\":[\"receipt\"],\"directive\":{\"slayerOptions\":{\"rows\":42},\"slayerRevision\":{},\"relayEpoch\":\"relay-epoch-first\"}}", RelayResponse.class);
		assertEquals("keep-this", reply.memberSecret);
		assertEquals("receipt", reply.acknowledgedMilestones.get(0));
		assertNull(reply.directive.slayerOptions);
		assertEquals(0, reply.directive.slayerRevision);
	}
}
