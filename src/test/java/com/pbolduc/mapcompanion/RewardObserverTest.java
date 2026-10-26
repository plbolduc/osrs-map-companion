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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.ScriptID;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.*;
import org.junit.Test;

/** Tests actual event-family overlap and uncertainty; none of these fixtures pretends to simulate a kill. */
public class RewardObserverTest
{
	private final RewardTestClient fixture = new RewardTestClient();
	private final CollectionItemCatalog catalog = fixture.catalog();
	private final RewardObserver observer = new RewardObserver(fixture.client, catalog, catalog::contains, () -> 1_000L);

	@Test
	public void copiesServerLootBeforeItsBackingListIsClearedAndIgnoresForwardedNpcRecord()
	{
		List<ItemStack> items = new ArrayList<>(Arrays.asList(new ItemStack(10020, 2), new ItemStack(30000, 3)));
		observer.onNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"), items));
		observer.onLootReceived(loot("Boss", LootRecordType.NPC, items));
		items.clear();
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals("item", result.get(0).rewardType);
		assertEquals(Integer.valueOf(5), result.get(0).quantity);
		assertEquals("npc", result.get(0).sourceKind);
		assertEquals("Boss", result.get(0).sourceName);
		assertEquals(1_000L, result.get(0).at);
	}

	@Test
	public void separateSameItemReceiptsInOneTickAreBothKept()
	{
		npc(10020);
		npc(10020);
		List<RewardObservation> result = flush();
		assertEquals(2, result.size());
		assertNull(result.get(0).collectionNew);
		assertNull(result.get(1).collectionNew);
		assertEquals(Integer.valueOf(1), result.get(0).quantity);
		assertEquals(Integer.valueOf(1), result.get(1).quantity);
	}

	@Test
	public void collectionAfterTwoMatchingReceiptsDoesNotGuessWhichSourceEarnedIt()
	{
		npc(10020);
		observer.onLootReceived(loot("Lunar Chest", LootRecordType.EVENT,
									 Collections.singletonList(new ItemStack(10020, 1))));
		collection("Item 10020");
		collection("Item 10020");
		List<RewardObservation> result = flush();
		assertEquals(3, result.size());
		assertNull(result.get(0).collectionNew);
		assertNull(result.get(1).collectionNew);
		assertEquals("Boss", result.get(0).sourceName);
		assertEquals("Lunar Chest", result.get(1).sourceName);
		assertUnknownSourceCollection(result.get(2));
	}

	@Test
	public void aSecondReceiptBeforeFlushMakesAnEarlierCollectionJoinAmbiguous()
	{
		collection("Item 10020");
		npc(10020);
		npc(10020);
		collection("Item 10020");
		List<RewardObservation> result = flush();
		assertEquals(3, result.size());
		assertNull(result.get(0).collectionNew);
		assertNull(result.get(1).collectionNew);
		assertUnknownSourceCollection(result.get(2));
	}

	@Test
	public void joinsCollectionAndLootInEitherOrderWithoutInventingAnotherReward()
	{
		collection("Item 10020");
		npc(10020);
		npc(10021);
		collection("Item 10021");
		List<RewardObservation> result = flush();
		assertEquals(2, result.size());
		for (RewardObservation reward : result)
		{
			assertEquals("item", reward.rewardType);
			assertEquals(Boolean.TRUE, reward.collectionNew);
			assertEquals("Boss", reward.sourceName);
		}
	}

	@Test
	public void popupModeConsumesItsStartOnceAndDoesNotAlsoUseChat()
	{
		fixture.collectionMode = 3;
		collection("Item 10020");
		popup("New item:<br>Item 10020");
		observer.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals("collection_log", result.get(0).rewardType);
		assertNull(result.get(0).quantity);
		assertNull(result.get(0).sourceName);
	}

	@Test
	public void repeatedPopupCyclesForTheSameCanonicalItemDoNotCreateAnotherUnlock()
	{
		fixture.collectionMode = 2;
		popup("New item:<br>Item 10000");
		popup("New item:<br>Pet replacement");
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals(Integer.valueOf(20000), result.get(0).itemId);
		assertUnknownSourceCollection(result.get(0));
	}

	@Test
	public void repeatedCollectionCallbackCannotAttachTheUnlockToASecondReceipt()
	{
		npc(10020);
		collection("Item 10020");
		collection("Item 10020");
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals("item", result.get(0).rewardType);
		assertEquals(Boolean.TRUE, result.get(0).collectionNew);
	}

	@Test
	public void malformedPopupAndPluginOrOtherPlayerChatCannotCreateRewards()
	{
		fixture.collectionMode = 3;
		popup("Unexpected popup payload");
		observer.onChatMessage(new ChatMessage(null, ChatMessageType.CLAN_MESSAGE, "Other",
											   "You have a funny feeling like you're being followed.", "", 0));
		fixture.collectionMode = 1;
		observer.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "runelite",
											   "New item added to your collection log: Item 10020", "", 0));
		assertTrue(flush().isEmpty());
	}

	@Test
	public void joinsTheLocalPetTriggerLootAndCollectionAsOnePet()
	{
		game("You have a funny feeling like you're being followed.");
		npc(10000);
		collection("Pet replacement");
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		RewardObservation pet = result.get(0);
		assertEquals("pet", pet.rewardType);
		assertEquals(Integer.valueOf(20000), pet.itemId);
		assertEquals(Boolean.TRUE, pet.collectionNew);
		assertEquals("Boss", pet.sourceName);
	}

	@Test
	public void namesAPetFromLocalUntradeableTextButDoesNotAssumeACollectionAddition()
	{
		game("You have a funny feeling like you would have been followed...");
		game("Untradeable drop: Pet replacement");
		RewardObservation pet = flush().get(0);
		assertEquals("pet", pet.rewardType);
		assertEquals("Pet replacement", pet.item);
		assertNull(pet.collectionNew);
		assertNull(pet.quantity);
		assertNull(pet.sourceName);
	}

	@Test
	public void petAndUnrelatedItemDoNotBorrowEachOthersIdentity()
	{
		game("You feel something weird sneaking into your backpack.");
		game("Untradeable drop: Item 10020");
		collection("Item 10020");
		List<RewardObservation> result = flush();
		assertEquals(2, result.size());
		assertEquals("pet", result.get(0).rewardType);
		assertEquals("Pet", result.get(0).item);
		assertNull(result.get(0).itemId);
		assertNull(result.get(0).quantity);
		assertNull(result.get(0).sourceName);
		assertEquals("collection_log", result.get(1).rewardType);
	}

	@Test
	public void aSecondPetRollIsNotAnEventReplay()
	{
		game("You have a funny feeling like you would have been followed...");
		game("Untradeable drop: Pet replacement");
		game("You have a funny feeling like you would have been followed...");
		game("Untradeable drop: Pet replacement");
		assertEquals(2, flush().size());
	}

	@Test
	public void includesCoreChestLabelsButRejectsPlayerPickpocketAndUnknownEvents()
	{
		List<ItemStack> items = Collections.singletonList(new ItemStack(10020, 1));
		for (String name : Arrays.asList("Lunar Chest", "Fortis Colosseum", "Chambers of Xeric",
										 "Theatre of Blood", "Tombs of Amascut", "Clue Scroll (Master)", "Reward pool (Tempoross)"))
		{
			observer.onLootReceived(loot(name, LootRecordType.EVENT, items));
		}
		observer.onLootReceived(loot("Lunar Chest", LootRecordType.PLAYER, items));
		observer.onLootReceived(loot("Lunar Chest", LootRecordType.PICKPOCKET, items));
		observer.onLootReceived(loot("Other plugin invented this", LootRecordType.EVENT, items));
		List<RewardObservation> result = flush();
		assertEquals(7, result.size());
		assertEquals("Lunar Chest", result.get(0).sourceName);
		assertEquals("Fortis Colosseum", result.get(1).sourceName);
		for (RewardObservation reward : result)
		{
			assertEquals("activity", reward.sourceKind);
		}
	}

	@Test
	public void policyCanExcludeSuppliesAndEmptyOrInvalidStacksAreNotRewards()
	{
		RewardObserver narrow = new RewardObserver(fixture.client, catalog, id -> id == 10021);
		narrow.onNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"), Arrays.asList(
											   new ItemStack(10020, 1), new ItemStack(10021, 2), new ItemStack(10022, 0), new ItemStack(-1, 1))));
		narrow.onNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"), Collections.emptyList()));
		fixture.tick = RewardObserver.SETTLE_TICKS;
		List<RewardObservation> result = narrow.onGameTick(5_000L, fixture.tick);
		assertEquals(1, result.size());
		assertEquals(Integer.valueOf(10021), result.get(0).itemId);
		assertEquals(Integer.valueOf(2), result.get(0).quantity);
	}

	@Test
	public void coreKeyChestsKeepRepeatedUniqueRewardsAndTheirExactSourceLabels()
	{
		List<String> sources = Arrays.asList("Brimstone Chest", "Larran's big chest",
											 "Larran's small chest", "Elven Crystal Chest", "Bronze key red", "Steel key brown",
											 "Black key crimson", "Silver key black", "Gold key purple");
		for (String source : sources)
		{
			observer.onLootReceived(loot(source, LootRecordType.EVENT,
										 Arrays.asList(new ItemStack(40000, 1000), new ItemStack(10020, 2))));
			observer.onLootReceived(loot(source, LootRecordType.EVENT,
										 Collections.singletonList(new ItemStack(10020, 3))));
		}
		List<RewardObservation> rewards = flush();
		assertEquals(sources.size() * 2, rewards.size());
		for (int i = 0; i < sources.size(); i++)
		{
			RewardObservation first = rewards.get(i * 2);
			RewardObservation second = rewards.get(i * 2 + 1);
			assertEquals("activity", first.sourceKind);
			assertEquals(sources.get(i), first.sourceName);
			assertEquals(sources.get(i), second.sourceName);
			assertEquals(Integer.valueOf(10020), first.itemId);
			assertEquals(Integer.valueOf(2), first.quantity);
			assertEquals(Integer.valueOf(3), second.quantity);
			assertNull(first.collectionNew);
			assertNull(second.collectionNew);
		}
	}

	@Test
	public void firstCollectionUnlockRemainsNotableWhenRepeatCopiesAreExcluded()
	{
		RewardObserver quietRepeats = new RewardObserver(fixture.client, catalog, id -> false);
		quietRepeats.onNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"),
				Collections.singletonList(new ItemStack(10020, 1))));
		quietRepeats.onChatMessage(chat("New item added to your collection log: Item 10020"));
		fixture.tick = RewardObserver.SETTLE_TICKS;
		List<RewardObservation> result = quietRepeats.onGameTick(5_000L, fixture.tick);
		assertEquals(1, result.size());
		assertEquals("collection_log", result.get(0).rewardType);
		assertEquals(Boolean.TRUE, result.get(0).collectionNew);
		assertNull(result.get(0).sourceName);
	}

	@Test
	public void namedCoreContainersAndProcessingKeepNotableItemsButPvpLootKeysStayExcluded()
	{
		List<String> sources = Arrays.asList("Crystal Chest", "Chest (Moon key)", "Forgotten lockbox",
											 "Intricate pouch", "Hunters' loot sack (master)", "Tarnished amulet", "Opulent salvage",
											 "Vale offerings", "Dirty arrowtips", "Rubium geode");
		for (String source : sources)
		{
			observer.onLootReceived(loot(source, LootRecordType.EVENT,
										 Arrays.asList(new ItemStack(40000, 5000), new ItemStack(10020, 1))));
		}
		// This is deliberately EVENT too: checking only the record type admits stolen-player loot.
		observer.onLootReceived(loot("Loot Chest", LootRecordType.EVENT,
									 Collections.singletonList(new ItemStack(10020, 1))));
		observer.onLootReceived(loot("Forgotten lockbox", LootRecordType.PLAYER,
									 Collections.singletonList(new ItemStack(10020, 1))));
		List<RewardObservation> rewards = flush();
		assertEquals(sources.size(), rewards.size());
		for (int i = 0; i < sources.size(); i++)
		{
			assertEquals("activity", rewards.get(i).sourceKind);
			assertEquals(sources.get(i), rewards.get(i).sourceName);
			assertEquals(Integer.valueOf(10020), rewards.get(i).itemId);
			assertNull(rewards.get(i).collectionNew);
		}
	}

	@Test
	public void collectionOnlySharingDoesNotStartAPetObservation()
	{
		observer.onChatMessage(chat("You have a funny feeling like you're being followed."), false, true);
		observer.onChatMessage(chat("New item added to your collection log: Pet replacement"), false, true);
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals("collection_log", result.get(0).rewardType);
	}

	@Test
	public void fixedCoreRewardSourcesUseTheSameNotableItemPolicy()
	{
		List<String> sources = Arrays.asList("Herbiboar", "Zombie Pirate's Locker", "Golem Crafting",
											 "Seed pack", "Bird nest", "Casket", "Ore Pack (Volcanic Mine)",
											 "Bag full of gems (Percy)", "Bag full of gems (Belona)", "Bag full of gems (Dusuri)", "Spoils of war");
		for (String source : sources)
		{
			observer.onLootReceived(loot(source, LootRecordType.EVENT,
										 Arrays.asList(new ItemStack(40000, 1000), new ItemStack(10020, 1))));
		}
		List<RewardObservation> rewards = flush();
		assertEquals(sources.size(), rewards.size());
		for (int i = 0; i < sources.size(); i++)
		{
			assertEquals("activity", rewards.get(i).sourceKind);
			assertEquals(sources.get(i), rewards.get(i).sourceName);
			assertEquals(Integer.valueOf(10020), rewards.get(i).itemId);
			assertNull(rewards.get(i).collectionNew);
		}
	}

	@Test
	public void petOnlySharingCanNameThePetWithoutSharingCollectionStatus()
	{
		observer.onChatMessage(chat("You have a funny feeling like you're being followed."), true, false);
		observer.onChatMessage(chat("New item added to your collection log: Pet replacement"), true, false);
		observer.onChatMessage(chat("New item added to your collection log: Item 10020"), true, false);
		List<RewardObservation> result = flush();
		assertEquals(1, result.size());
		assertEquals("pet", result.get(0).rewardType);
		assertEquals("Pet replacement", result.get(0).item);
		assertNull(result.get(0).collectionNew);
	}

	@Test
	public void suppressesNpcReceiptsFromThePickpocketTick()
	{
		game("You pick the guard's pocket.");
		npc(10020);
		fixture.tick++;
		npc(10021);
		assertEquals(1, flush().size());
	}

	@Test
	public void profileOrSharingResetDropsPendingEvidenceAndPopupState()
	{
		npc(10020);
		game("You have a funny feeling like you're being followed.");
		observer.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
		observer.reset();
		assertTrue(flush().isEmpty());
	}

	@Test
	public void preservesExplicitCollectionEvidenceWhenDefinitionsAreUnavailable()
	{
		CollectionItemCatalog missing = new CollectionItemCatalog(fixture.client);
		RewardObserver cold = new RewardObserver(fixture.client, missing);
		cold.onChatMessage(chat("New item added to your collection log: Unresolved reward"));
		fixture.tick = RewardObserver.SETTLE_TICKS;
		RewardObservation reward = cold.onGameTick(5_000L, fixture.tick).get(0);
		assertEquals("collection_log", reward.rewardType);
		assertEquals("Unresolved reward", reward.item);
		assertNull(reward.itemId);
		assertNull(reward.quantity);
	}

	@Test
	public void remainingNamedCoreInterfacesAndGatheringReceiptsUseTheSameItemPolicy()
	{
		String[] sources = {"Kingdom of Miscellania", "Drift Net", "H.A.M. chest (steel)",
							"H.A.M. chest (bronze)", "H.A.M. chest (silver)", "H.A.M. chest (iron)",
							"Regular Bird House", "Oak Bird House", "Willow Bird House", "Teak Bird House", "Maple Bird House",
							"Mahogany Bird House", "Yew Bird House", "Magic Bird House", "Redwood Bird House"
						   };
		for (String source : sources)
		{
			observer.onLootReceived(loot(source, LootRecordType.EVENT,
										 Arrays.asList(new ItemStack(10020, 1), new ItemStack(40000, 1))));
		}
		List<RewardObservation> result = flush();
		assertEquals(sources.length, result.size());
		for (int i = 0; i < sources.length; i++)
		{
			assertEquals(sources[i], result.get(i).sourceName);
			assertEquals(Integer.valueOf(10020), result.get(i).itemId);
			assertNull(result.get(i).collectionNew);
		}
	}

	private void npc(int id)
	{
		observer.onNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"), Collections.singletonList(new ItemStack(id, 1))));
	}

	private void collection(String item)
	{
		game("New item added to your collection log: " + item);
	}

	private void game(String message)
	{
		observer.onChatMessage(chat(message));
	}

	private static ChatMessage chat(String message)
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, "", 0);
	}

	private void popup(String text)
	{
		fixture.varcs.put(VarClientID.NOTIFICATION_TITLE, "Collection log");
		fixture.varcs.put(VarClientID.NOTIFICATION_MAIN, text);
		observer.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
		observer.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
	}

	private List<RewardObservation> flush()
	{
		fixture.tick += RewardObserver.SETTLE_TICKS;
		return observer.onGameTick(5_000L, fixture.tick);
	}

	private static LootReceived loot(String name, LootRecordType type, List<ItemStack> items)
	{
		return new LootReceived(name, -1, type, items, 1, null);
	}

	private static void assertUnknownSourceCollection(RewardObservation reward)
	{
		assertEquals("collection_log", reward.rewardType);
		assertEquals(Boolean.TRUE, reward.collectionNew);
		assertNull(reward.sourceKind);
		assertNull(reward.sourceName);
		assertNull(reward.quantity);
	}
}
