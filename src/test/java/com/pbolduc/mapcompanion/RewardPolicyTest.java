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

import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.*;
import org.junit.Test;

/** A curated exception is a named item, never a price or rarity rule that admits ordinary supplies. */
public class RewardPolicyTest
{
	@Test
	public void rareSignatureExceptionsWorkWithoutAnInitializedCollectionTree()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.itemNames.put(13273, "Unsired");
		fixture.itemNames.put(11286, "Draconic visage");
		fixture.itemNames.put(11335, "Dragon full helm");
		RewardPolicy policy = new RewardPolicy(new CollectionItemCatalog(fixture.client));
		assertTrue(policy.includes(ItemID.ABYSSALSIRE_UNSIRED));
		assertTrue(policy.includes(ItemID.DRAGONFIRE_VISAGE));
		assertTrue(policy.includes(ItemID.BRUT_DRAGON_FULL_HELM));
		assertFalse(policy.includes(10020));
	}

	@Test
	public void ordinarySuppliesStayQuietEvenWhenTheCollectionTreeIncludesThem()
	{
		RewardTestClient fixture = new RewardTestClient();
		int[] ids = {12934, 21816, 21817, 27616};
		String[] names = {"Zulrah's scales", "Bracelet of ethereum", "Bracelet of ethereum (uncharged)", "Ancient essence"};
		for (int i = 0; i < ids.length; i++)
		{
			fixture.enums.get(4001)[i] = ids[i];
			fixture.itemNames.put(ids[i], names[i]);
		}
		CollectionItemCatalog catalog = fixture.catalog();
		RewardPolicy policy = new RewardPolicy(catalog);
		for (int id : ids)
		{
			assertTrue(catalog.contains(id));
			assertFalse(policy.includes(id));
		}
		assertTrue(policy.includes(10024));
	}

	@Test
	public void coinsRunesAndSuppliesHaveNoGenericRarityOrPriceException()
	{
		RewardTestClient fixture = new RewardTestClient();
		fixture.itemNames.put(ItemID.COINS, "Coins");
		fixture.itemNames.put(ItemID.AIRRUNE, "Air rune");
		RewardPolicy policy = new RewardPolicy(fixture.catalog());
		assertFalse(policy.includes(ItemID.COINS));
		assertFalse(policy.includes(ItemID.AIRRUNE));
		assertFalse(policy.includes(40000));
		assertFalse(policy.includes(-1));
	}

	@Test
	public void curatedNumbersStillNameTheReviewedItemsInTheInstalledClient()
	{
		assertEquals(12934, ItemID.SNAKEBOSS_SCALE);
		assertEquals(21816, ItemID.WILD_CAVE_BRACELET_CHARGED);
		assertEquals(21817, ItemID.WILD_CAVE_BRACELET_UNCHARGED);
		assertEquals(27616, ItemID.ANCIENT_ESSENCE);
		assertEquals(13273, ItemID.ABYSSALSIRE_UNSIRED);
		assertEquals(11286, ItemID.DRAGONFIRE_VISAGE);
		assertEquals(11335, ItemID.BRUT_DRAGON_FULL_HELM);
	}
}
