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

import net.runelite.api.GameState;
import static org.junit.Assert.*;
import org.junit.Test;

/** Cache traversal failures must leave the catalog unknown rather than publish a small valid fragment. */
public class CollectionItemCatalogTest
{
	@Test
	public void readsDefinitionsAtLoginWithoutAskingForAPlayer()
	{
		RewardTestClient fixture = new RewardTestClient();
		CollectionItemCatalog catalog = fixture.catalog();
		assertTrue(catalog.isComplete());
		assertTrue(catalog.contains(10999));
		assertFalse(catalog.contains(40000));
		assertEquals(Integer.valueOf(20000), catalog.canonicalId(10000));
		assertEquals(Integer.valueOf(10020), catalog.canonicalId(30000));
		assertEquals("Pet replacement", catalog.itemName(10000));
		assertEquals(Integer.valueOf(20000), catalog.findItemId("Item 10000"));
		assertTrue(catalog.isPet(10000));
		assertFalse(catalog.isPet(10020));
	}

	@Test
	public void doesNotReadDefinitionsOffTheClientThread()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.clientThread = false;
		assertFalse(new CollectionItemCatalog(fixture.client).initialize());
		assertEquals(0, fixture.enumReads);
	}

	@Test
	public void noPartialCatalogEscapesAMissingBranch()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.enums.remove(4049);
		CollectionItemCatalog catalog = new CollectionItemCatalog(fixture.client);
		assertFalse(catalog.initialize());
		assertFalse(catalog.isComplete());
		assertFalse(catalog.contains(10020));
		assertNull(catalog.findItemId("Item 10020"));
	}

	@Test
	public void rejectsAValidButCollapsedTree()
	{
		RewardTestClient fixture = new RewardTestClient();
		for (int tab = 0; tab < 5; tab++)
		{
			fixture.enums.put(1000 + tab, new int[] {3000});
		}
		assertFalse(new CollectionItemCatalog(fixture.client).initialize());
	}

	@Test
	public void malformedReplacementsAndMissingNamesCannotClaimCompleteness()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.replacementValues = new int[0];
		assertFalse(new CollectionItemCatalog(fixture.client).initialize());
		fixture.replacementValues = new int[] {20000};
		fixture.itemNames.remove(10999);
		assertFalse(new CollectionItemCatalog(fixture.client).initialize());
	}

	@Test
	public void refusesToGuessBetweenTwoDifferentItemsWithOneName()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.itemNames.put(10020, "Ambiguous reward");
		fixture.itemNames.put(10021, "Ambiguous reward");
		CollectionItemCatalog catalog = fixture.catalog();
		assertTrue(catalog.contains(10020));
		assertNull(catalog.findItemId("Ambiguous reward"));
	}

	@Test
	public void retriesLocallyAtABoundedCadenceAndNeverDuringStartup()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.state = GameState.STARTING;
		CollectionItemCatalog catalog = new CollectionItemCatalog(fixture.client);
		for (int tick = 0; tick < 100; tick++)
		{
			catalog.onGameTick(tick);
		}
		assertEquals(0, fixture.enumReads);
		fixture.state = GameState.LOGIN_SCREEN;
		fixture.enums.remove(2102);
		for (int tick = 100; tick < 10_000; tick++)
		{
			catalog.onGameTick(tick);
		}
		assertEquals(CollectionItemCatalog.MAX_ATTEMPTS, fixture.enumReads);
		assertFalse(catalog.isComplete());
	}

	@Test
	public void retriesADeferredDefinitionAndStopsOnceComplete()
	{
		RewardTestClient fixture = new RewardTestClient();
		int[] page = fixture.enums.remove(4049);
		CollectionItemCatalog catalog = new CollectionItemCatalog(fixture.client);
		catalog.onGameTick(0);
		int firstReads = fixture.enumReads;
		fixture.enums.put(4049, page);
		catalog.onGameTick(1);
		assertEquals(firstReads, fixture.enumReads);
		catalog.onGameTick(CollectionItemCatalog.RETRY_TICKS);
		assertTrue(catalog.isComplete());
		int completeReads = fixture.enumReads;
		catalog.onGameTick(1_000);
		assertEquals(completeReads, fixture.enumReads);
	}
}
