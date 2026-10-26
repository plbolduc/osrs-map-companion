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
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The queue behind "somebody in your group just got a drop".
 *
 * <p>Every rule here exists because the alternative is one of two visible failures: an
 * announcement that never arrives, or one that arrives twice. Both are worse than the feature is
 * good, and neither shows up in a session where nothing goes wrong — the interesting cases are all
 * a reply that was lost, a connection that came back, or a player who switched account. So they
 * are driven here rather than left to a live client.
 */
public class MilestoneOutboxTest
{
	@Test
	public void anEventKeepsItsIdUntilTheMapNamesIt()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Zulrah", "Tanzanite fang", 12922, 1, 1_000L);

		String first = outbox.take(1_500L).get(0).id;
		/** The reply was lost. The same event goes again, and it is the same event. */
		assertEquals(first, outbox.take(9_000L).get(0).id);

		outbox.acknowledge(Collections.singletonList(first));
		assertTrue(outbox.take(9_500L).isEmpty());
	}

	@Test
	public void anAgeIsMeasuredFromWhenItHappened()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Zulrah", "Tanzanite fang", 12922, 1, 1_000L);
		assertEquals(4_000L, outbox.take(5_000L).get(0).msAgo);
	}

	@Test
	public void acknowledgingSomethingElseLeavesThisAlone()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Vorkath", "Dragonbone necklace", 22111, 1, 1_000L);
		outbox.acknowledge(Arrays.asList("not-an-id-we-minted"));
		assertEquals(1, outbox.pendingCount());
	}

	@Test
	public void sendsAtMostEightAtOnceAndKeepsTheRest()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		for (int i = 0; i < 12; i++)
		{
			outbox.levelUp("Mining", 60 + i, 1_000L + i);
		}
		List<CompanionSnapshot.Milestone> batch = outbox.take(2_000L);
		assertEquals(MilestoneOutbox.MAX_BATCH, batch.size());
		/** Oldest first, so a group reads them in the order they happened. */
		assertEquals(60, batch.get(0).level.intValue());
		assertEquals(12, outbox.pendingCount());
	}

	@Test
	public void forgetsTheOldestRatherThanGrowingWithoutLimit()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		for (int i = 0; i < MilestoneOutbox.MAX_PENDING + 5; i++)
		{
			outbox.npcDrop("Skeleton", "Bones", 526, 1, 1_000L + i);
		}
		assertEquals(MilestoneOutbox.MAX_PENDING, outbox.pendingCount());
		/**
		 * The sequence numbers prove which end was dropped: the five that went are the five that
		 * had already had the most chances to be delivered.
		 */
		assertEquals(6, outbox.take(2_000L).get(0).sequence);
	}

	@Test
	public void stopsRetryingOnceTheMapHasForgottenTheId()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Zulrah", "Magic fang", 12932, 1, 1_000L);
		assertEquals(1, outbox.take(1_000L + MilestoneOutbox.RETRY_MS).size());
		/**
		 * Past the window the map's own dedup memory has dropped the id, so a retry would arrive
		 * as news rather than as a repeat. Better an announcement missed than one made twice.
		 */
		assertTrue(outbox.take(1_001L + MilestoneOutbox.RETRY_MS).isEmpty());
	}

	@Test
	public void switchingCharacterTakesTheQueueAndTheSessionWithIt()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Zulrah", "Serpentine visage", 12927, 1, 1_000L);
		String session = outbox.take(1_100L).get(0).session;

		outbox.reset();
		assertEquals(0, outbox.pendingCount());

		outbox.levelUp("Slayer", 85, 2_000L);
		CompanionSnapshot.Milestone next = outbox.take(2_100L).get(0);
		/** A new run of the plugin, so the group can tell a restart from a continuing session. */
		assertNotEquals(session, next.session);
		assertEquals(1, next.sequence);
	}

	@Test
	public void refusesWhatTheMapWouldRefuse()
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		/** Level 1 is where everybody starts; it is not something anybody just gained. */
		outbox.levelUp("Mining", 1, 1_000L);
		outbox.levelUp("Mining", 100, 1_000L);
		outbox.npcDrop("Zulrah", "Tanzanite fang", 0, 1, 1_000L);
		outbox.npcDrop("", "Tanzanite fang", 12922, 1, 1_000L);
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	public void aLevelCarriesNoDropFieldsAndADropNoLevel()
	{
		/** One class on the wire, two kinds. A field of the other kind set would be a claim. */
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.levelUp("Farming", 99, 1_000L);
		CompanionSnapshot.Milestone level = outbox.take(1_000L).get(0);
		assertEquals(MilestoneOutbox.KIND_LEVEL, level.kind);
		assertEquals(null, level.itemId);
		assertEquals(null, level.quantity);

		outbox.reset();
		outbox.npcDrop("Zulrah", "Tanzanite fang", 12922, 2, 1_000L);
		CompanionSnapshot.Milestone drop = outbox.take(1_000L).get(0);
		assertEquals(MilestoneOutbox.KIND_NPC_DROP, drop.kind);
		assertEquals(null, drop.level);
		assertEquals(Integer.valueOf(2), drop.quantity);
	}
}
