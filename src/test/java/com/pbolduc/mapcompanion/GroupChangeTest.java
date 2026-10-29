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
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What does not follow you from one group into the next.
 *
 * <p>Everything else this plugin holds is a state the next post repairs, so moving between groups
 * costs it nothing. Two things are not states, and both would arrive somewhere they do not belong.
 *
 * <p>The one worth a test of its own is the outbox. An announcement waits for the map to say it
 * heard, for up to five minutes, and a group change is the one event in that window that changes
 * who is listening — so a drop somebody got in one group was announced to the next group they
 * joined, correctly deduplicated, with the right name on it, and read by people who were nowhere
 * near it. It is the same mistake as posting one character's drop under another's dot, one level
 * out, and it has no symptom on this side at all.
 */
public class GroupChangeTest
{
	private final Gson gson = new Gson();

	@Test
	public void aDropQueuedInOneGroupIsNotAnnouncedToTheNext()
	{
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234-K7PQ2M3X");
		plugin.milestones.npcDrop("Zulrah", "Tanzanite fang", 12_922, 1, 1_000L);
		assertEquals(1, plugin.milestones.pendingCount());

		/* The player leaves, or pastes an invite to somewhere else. */
		plugin.pairing.join(JoinCode.parse("DEF567"));
		plugin.onGroupChanged();

		assertEquals("an unacknowledged drop belongs to the group it happened in",
					 0, plugin.milestones.pendingCount());
		assertTrue(plugin.milestones.take(2_000L).isEmpty());
	}

	@Test
	public void theNewGroupGetsANewTimelineRatherThanTheOldOnesNumbering()
	{
		/**
		 * The session id and the sequence are how the map tells a fresh start from a reconnect, so
		 * carrying them across a group change would file the new group's first announcement as a
		 * continuation of a timeline it never saw the beginning of.
		 */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		plugin.milestones.npcDrop("Zulrah", "Tanzanite fang", 12_922, 1, 1_000L);
		String before = plugin.milestones.take(1_500L).get(0).session;

		plugin.onGroupChanged();
		plugin.milestones.levelUp("Slayer", 85, 2_000L);
		CompanionSnapshot.Milestone after = plugin.milestones.take(2_500L).get(0);

		assertEquals("the numbering starts again with the group", 1, after.sequence);
		assertTrue("and it is a different sitting as far as the map is concerned",
				   !before.equals(after.session));
	}

	@Test
	public void theSuffixGoesWithIt()
	{
		/** The other half of the same rule: a suffix minted for one group means nothing in another. */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234-K7PQ2M3X");
		assertEquals("K7PQ2M3X", plugin.pairing.suffix());

		/* The code is changed first: the reaction keeps a suffix filed under the current code. */
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "DEF567");
		plugin.onGroupChanged();

		assertNull(plugin.pairing.suffix());
	}

	@Test
	public void aJoinsOwnSuffixSurvivesTheReactionToItsCode()
	{
		/**
		 * The reaction to a new code runs on the client thread, after join has filed the code's
		 * suffix. It used to clear unconditionally, so a new member's first post went without the
		 * suffix and a group that keeps members answered with "paste your RuneLite code".
		 */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234-K7PQ2M3X");

		plugin.onGroupChanged();

		assertEquals("K7PQ2M3X", plugin.pairing.suffix());
	}

	@Test
	public void thePreviousDestinationAndMeetupDoNotWaitForANewReply() throws Exception
	{
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		RelayResponse.Directive directive = gson.fromJson(
												"{\"destination\":{\"name\":\"Old star\",\"x\":3222,\"y\":3218},"
												+ "\"event\":{\"id\":\"old-meetup\",\"title\":\"Old group\"}}",
												RelayResponse.Directive.class);
		plugin.destinationState.accept(directive);
		/*
		 * The reminder now watches every meetup of the week rather than one, so what it has said is a
		 * set held by MeetupReminder instead of a single key; the rule is unchanged: a group change
		 * forgets it.
		 */
		long start = System.currentTimeMillis() + 60_000L;
		assertTrue(plugin.meetupReminder.due(java.util.Collections.singletonList(
			new MeetupReminder.Upcoming("old-meetup", "Old group", start)), start - 60_000L, true, 5).size() == 1);
		assertTrue(plugin.meetupReminder.said("old-meetup", start, "Old group"));

		plugin.pairing.join(JoinCode.parse("DEF567"));
		plugin.onGroupChanged();

		assertNull(plugin.destinationState.getDestination());
		assertNull(plugin.destinationState.getEvent());
		assertTrue(!plugin.meetupReminder.said("old-meetup", start, "Old group"));
		assertTrue(((TestDestinationMarker) plugin.destinationMarker).removed);
	}

	@Test
	public void aDismissalInThePreviousGroupDoesNotHideTheNewGroupsSameTile()
	{
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		RelayResponse.Directive directive = gson.fromJson(
												"{\"destination\":{\"name\":\"Old star\",\"x\":3222,\"y\":3218}}",
												RelayResponse.Directive.class);
		plugin.destinationState.accept(directive);
		plugin.destinationState.dismiss();
		assertNull(plugin.destinationState.getDestination());

		plugin.pairing.join(JoinCode.parse("DEF567"));
		plugin.onGroupChanged();
		plugin.destinationState.accept(directive);

		assertEquals("dismissals belong to the group that sent the destination",
					 "Old star", plugin.destinationState.getDestination().name);
	}

	/**
	 * The plugin with the two collaborators this rule touches, and nothing else.
	 *
	 * <p>Everything else it holds needs a client, an injector or a network, and none of that is
	 * reachable here — which is exactly why the rule was moved into a method of its own rather than
	 * left inline in the event handler where nothing could ever have run it.
	 */
	/** The store behind the last plugin made, so a test can write a setting the way RuneLite does. */
	private FakeStore store;

	private MapCompanionPlugin pluginJoinedTo(String pasted)
	{
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		store = new FakeStore();
		plugin.pairing = new Pairing(store, gson);
		plugin.milestones = new MilestoneOutbox();
		plugin.destinationState = new DestinationState(gson);
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		HarnessGame game = new HarnessGame();
		plugin.rewardObserver = new RewardObserver(game.client(), new CollectionItemCatalog(game.client()));
		plugin.pairing.join(JoinCode.parse(pasted));
		return plugin;
	}

	/** The two lines of key-value store {@link Pairing} uses, in a map. */
	private static final class FakeStore implements ConfigStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Nullable
		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
		}
	}
}
