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
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.*;
import org.junit.Test;

public class FarmingObserverTest
{
	private static final WorldPoint FALADOR = new WorldPoint(3056, 3307, 0);
	private static final WorldPoint CATHERBY = new WorldPoint(2808, 3463, 0);
	private static final class Profiles implements ConfigStore
	{
		String profile = "alice";
		final Map<String, String> values = new HashMap<>();
		public String scopeKey()
		{
			return profile;
		}

		public String get(String key)
		{
			return getScoped(profile, key);
		}

		public void set(String key, String value)
		{
			setScoped(profile, key, value);
		}

		public void unset(String key)
		{
			unsetScoped(profile, key);
		}

		public String getScoped(String scope, String key)
		{
			return values.get(scope + ":" + key);
		}

		public void setScoped(String scope, String key, String value)
		{
			values.put(scope + ":" + key, value);
		}

		public void unsetScoped(String scope, String key)
		{
			values.remove(scope + ":" + key);
		}
	}

	private final Profiles store = new Profiles();
	private final FarmingObserver observer = new FarmingObserver(null, store, new Gson());
	private int tick;
	private long now = 10_000L;

	private void read(WorldPoint position, int value)
	{
		observer.observeAt(position, tick++, now, ignored -> value);
		now += 600L;
	}

	private void settle(WorldPoint position, int value)
	{
		read(position, value);
		read(position, value);
		read(position, value);
	}

	private CompanionSnapshot.Patch patch(String id)
	{
		return observer.observations(now).stream().filter(patch -> id.equals(patch.id)).findFirst().orElse(null);
	}

	@Test
	public void onlyAnObservedLocalReplantProducesAStableReceipt()
	{
		settle(FALADOR, 8); // Mature Guam.
		assertNull(patch("falador-herb").completionId);
		read(FALADOR, 3); // Harvested / empty.
		assertNull(patch("falador-herb").completionId);
		read(FALADOR, 4); // New seed.
		CompanionSnapshot.Patch planted = patch("falador-herb");
		assertNotNull(planted.completionId);
		assertEquals(Long.valueOf(600L), planted.completedMsAgo);
		read(FALADOR, 4);
		assertEquals(planted.completionId, patch("falador-herb").completionId);
		assertEquals(Long.valueOf(1200L), patch("falador-herb").completedMsAgo);
		assertEquals("growing", planted.state);
		assertEquals(Long.valueOf(60 * 60_000L), planted.readyInMinMs);
		assertEquals(Long.valueOf(80 * 60_000L), planted.readyInMaxMs);
	}

	/** Standing at a patch moves only its "last seen", which the store needs only after a restart. */
	@Test
	public void standingStillWritesNothingAndTheSessionEndKeepsTheLatestSighting()
	{
		settle(FALADOR, 4);
		String first = store.values.get("alice:patchObservationsV4");
		assertNotNull("a new observation is written at once", first);
		for (int i = 0; i < 100; i++) read(FALADOR, 4);
		assertEquals("a minute at the patch is no write", first, store.values.get("alice:patchObservationsV4"));
		observer.onSessionEnded();
		assertNotEquals(first, store.values.get("alice:patchObservationsV4"));
		FarmingObserver restarted = new FarmingObserver(null, store, new Gson());
		CompanionSnapshot.Patch seen = restarted.observations(now).stream()
			.filter(patch -> "falador-herb".equals(patch.id)).findFirst().orElse(null);
		assertEquals("the restart knows the last sighting, not the first", 600L, seen.observedMsAgo);
	}

		@Test
	public void arrivingAtAGrowingPatchAndNaturalGrowthNeverCompleteIt()
	{
		settle(FALADOR, 4);
		for (int value : new int[] {5, 6, 7, 8, 9, 10, 3}) read(FALADOR, value);
		assertNull(patch("falador-herb").completionId);
		assertNull(patch("falador-herb").completedMsAgo);
	}

	@Test
	public void aSlotBelongsOnlyToTheCurrentRegionAndStaysOldWhenAway()
	{
		settle(FALADOR, 3);
		assertEquals(4, observer.observations(now).size());
		long age = patch("falador-herb").observedMsAgo;
		settle(CATHERBY, 4);
		assertNull(patch("catherby-herb").completionId);
		assertTrue(patch("falador-herb").observedMsAgo > age);
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
	}

	@Test
	public void settlingTicksCannotTurnStaleArrivalValuesIntoAReplant()
	{
		read(FALADOR, 3);
		read(FALADOR, 3);
		read(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
		assertEquals("growing", patch("falador-herb").state);
	}

	@Test
	public void sessionModalAndSharingGapsRequireAFreshBaseline()
	{
		settle(FALADOR, 3);
		observer.resetSession(); // Used for modal, loading, logout, hop and disabled sharing.
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
		read(FALADOR, 3);
		tick += 2; // Missed ticks cannot be joined across the gap.
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
		read(FALADOR, 3);
		now += 10_000;
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
	}

	@Test
	public void duplicateTickAndUnreadableValueNeverSupplyMissingEvidence()
	{
		settle(FALADOR, 3);
		observer.observeAt(FALADOR, tick - 1, now, ignored -> 4);
		assertNull(patch("falador-herb").completionId);
		read(FALADOR, -1);
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
	}

	@Test
	public void emptyAboveGroundCannotCompleteAPatchSeenUpstairs()
	{
		settle(FALADOR, 3);
		settle(new WorldPoint(FALADOR.getX(), FALADOR.getY(), 1), 4);
		assertEquals("empty", patch("falador-herb").state);
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
	}

	@Test
	public void completionSurvivesRestartButDoesNotLeakToAnotherProfile()
	{
		settle(FALADOR, 3);
		read(FALADOR, 4);
		String id = patch("falador-herb").completionId;
		FarmingObserver restarted = new FarmingObserver(null, store, new Gson());
		assertEquals(id, restarted.observations(now).get(0).completionId);
		store.profile = "bob";
		observer.resetProfile();
		assertTrue(observer.observations(now).isEmpty());
		settle(FALADOR, 4);
		assertNull(patch("falador-herb").completionId);
		store.profile = "alice";
		observer.resetProfile();
		assertEquals(id, patch("falador-herb").completionId);
		observer.forget();
		assertTrue(observer.observations(now).isEmpty());
		assertNotNull(store.values.get("bob:patchObservationsV4"));
	}

	@Test
	public void aLaterReplantGetsAnotherReceiptButTreeHealthChecksDoNot()
	{
		WorldPoint tree = new WorldPoint(3001, 3372, 0);
		settle(tree, 11);
		read(tree, 12);
		read(tree, 13);
		assertNull(patch("falador-tree").completionId);
		read(tree, 3);
		read(tree, 8);
		String first = patch("falador-tree").completionId;
		assertNotNull(first);
		read(tree, 3);
		read(tree, 8);
		assertNotEquals(first, patch("falador-tree").completionId);
	}

	@Test
	public void allSupportedPresetsHaveLocalReplantCoverage()
	{
		assertEquals(48, FarmingPatchCatalog.PATCHES.size());
		for (FarmingPatchCatalog.Patch definition : FarmingPatchCatalog.PATCHES)
		{
			observer.resetProfile();
			store.values.clear();
			int region = definition.regions[0];
			WorldPoint position = new WorldPoint((region >> 8) * 64 + 32, (region & 255) * 64 + 32, 0);
			assertTrue(definition.id, definition.isLocal(position));
			settle(position, 3);
			read(position, definition.kind.equals("HERB") || definition.kind.equals("SEAWEED") ? 4 : definition.kind.equals("ALLOTMENT") ? 6 : 8);
			assertNotNull(definition.id, patch(definition.id).completionId);
		}
	}
}
