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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A patch reading belongs to the character who walked past it.
 *
 * <p>The kill log was moved onto the RuneScape profile and the farming patches were left behind on
 * the settings profile, which is one slot shared by every account signed into this RuneLite. So an
 * account that had never left Lumbridge could open the map and be told its herbs were ready,
 * because a different character had seen them. There is no field in the stored blob naming whose
 * reading it was, which is why the key was bumped rather than migrated.
 *
 * <p>These pin the seam rather than the symptom. The store is asked for its scope on every entry,
 * so a switch between two reads cannot leave one character's map in memory under another's name.
 */
public class FarmingProfileTest
{
	private static final class Profiles implements ConfigStore
	{
		String profile = "a";
		final Map<String, String> values = new HashMap<>();
		@Override
		public String scopeKey()
		{
			return profile;
		}

		@Override
		public String get(String key)
		{
			return getScoped(profile, key);
		}

		@Override
		public void set(String key, String value)
		{
			setScoped(profile, key, value);
		}

		@Override
		public void unset(String key)
		{
			unsetScoped(profile, key);
		}

		@Override
		public String getScoped(String scope, String key)
		{
			return values.get(scope + ":" + key);
		}

		@Override
		public void setScoped(String scope, String key, String value)
		{
			values.put(scope + ":" + key, value);
		}

		@Override
		public void unsetScoped(String scope, String key)
		{
			values.remove(scope + ":" + key);
		}
	}

	private final HarnessGame game = new HarnessGame();
	private final Profiles store = new Profiles();
	private final FarmingObserver observer = new FarmingObserver(game.client(), store, new Gson());

	/** One patch reading, written the way the observer stores its own. */
	private void seen(String profile, String patchId, int value)
	{
		store.values.put(profile + ":patchObservationsV4",
						 "{\"" + patchId + "\":{\"value\":" + value + ",\"atMs\":1000,\"receiptAtMs\":0}}");
	}

	@Test
	public void aSecondCharacterDoesNotInheritTheFirstsPatches()
	{
		seen("a", "catherby-herb", 7);
		assertEquals(1, observer.observations(5_000L).size());

		store.profile = "b";
		assertTrue("b has walked past nothing", observer.observations(5_000L).isEmpty());

		store.profile = "a";
		assertEquals("a's own reading survives the visit", 1, observer.observations(5_000L).size());
	}

	@Test
	public void loggedOutReadsNobodysPatches()
	{
		seen("a", "catherby-herb", 7);
		assertEquals(1, observer.observations(5_000L).size());

		/** No character is selected, so no reading can be attributed to one. */
		store.profile = null;
		assertTrue(observer.observations(5_000L).isEmpty());
	}

	@Test
	public void forgettingClearsOnlyTheCharacterWhoAskedForIt()
	{
		seen("a", "catherby-herb", 7);
		seen("b", "ardougne-herb", 7);

		observer.observations(5_000L);
		observer.forget();
		assertTrue(observer.observations(5_000L).isEmpty());

		store.profile = "b";
		assertEquals("the other character's log was not theirs to clear", 1, observer.observations(5_000L).size());
	}

	@Test
	public void thePreScopingBlobIsDroppedRatherThanAdoptedByWhoeverLogsInFirst()
	{
		/** V2 was written to the shared slot with no owner recorded, so it cannot be claimed. */
		store.values.put("patchObservationsV2",
						 "{\"catherby-herb\":{\"value\":7,\"atMs\":1000,\"receiptAtMs\":0}}");
		assertTrue(observer.observations(5_000L).isEmpty());
	}
}
