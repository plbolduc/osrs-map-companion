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
import java.lang.reflect.Field;
import java.util.Collections;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.GameState;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import static org.junit.Assert.*;
import org.junit.Test;

/** The production subscriptions, sharing decisions and queue, with definition-only game input. */
public class RewardDeliveryTest
{
	private final RewardTestClient game = new RewardTestClient();
	private final CollectionItemCatalog catalog = game.catalog();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, "http://localhost");
	private final MapCompanionPlugin plugin = new MapCompanionPlugin();

	public RewardDeliveryTest() throws Exception
	{
		// These tests exercise opted-in reward delivery; fresh installs start with it disabled.
		config.set(MapCompanionConfig.KEY_SHARE_MILESTONES, true);
		config.set(MapCompanionConfig.KEY_SHARE_PETS, true);
		config.set(MapCompanionConfig.KEY_SHARE_COLLECTION, true);
		game.state = GameState.LOGGED_IN;
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
		plugin.milestones = new MilestoneOutbox();
		plugin.destinationState = new DestinationState(new Gson());
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		plugin.pairing = new Pairing(store, new Gson());
		plugin.rewardObserver = new RewardObserver(game.client, catalog, catalog::contains, () -> 1000L);
		set("client", game.client);
		set("config", config);
		set("assignmentReader", new AssignmentReader(game.client, config));
		set("collectionItems", catalog);
		set("lootLog", new LootLog(new HarnessGame().client(), store, new Gson()));
	}

	@Test
	public void theActualNpcSubscriptionProducesOneAdditiveRewardAfterSettling()
	{
		npc(10020);
		assertEquals(0, plugin.milestones.pendingCount());
		settle();
		CompanionSnapshot.Milestone reward = plugin.milestones.take(5000).get(0);
		assertEquals("reward", reward.kind);
		assertEquals("item", reward.rewardType);
		assertEquals("npc", reward.source.kind);
		assertEquals("Boss", reward.source.name);
		assertEquals(Integer.valueOf(2), reward.quantity);
		assertNull(reward.npc);
		assertEquals(reward.id, plugin.milestones.take(6000).get(0).id);
		plugin.milestones.acknowledge(Collections.singletonList(reward.id));
		assertTrue(plugin.milestones.take(7000).isEmpty());
	}

	@Test
	public void unpairedOrLoggedOutObservationsDoNotAppearAfterJoining()
	{
		store.unset(MapCompanionConfig.KEY_PAIRING_CODE);
		npc(10020);
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
		game.state = GameState.LOGIN_SCREEN;
		npc(10020);
		game.state = GameState.LOGGED_IN;
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void switchingGroupClearsAnObservationBeforeItHasAnOutboxId()
	{
		npc(10020);
		plugin.onGroupChanged();
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void aProfileBoundaryClearsUnsettledEvidenceEvenBeforeTheNextLogin()
	{
		npc(10020);
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("a", null));
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void withdrawingSharingClearsBothSettledAndUnsettledEvents()
	{
		npc(10020);
		settle();
		npc(10021);
		config.set("shareMilestones", false);
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(MapCompanionConfig.GROUP);
		changed.setKey(MapCompanionConfig.KEY_SHARE_MILESTONES);
		plugin.onConfigChanged(changed);
		config.set("shareMilestones", true);
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void petAndCollectionChoicesRemainIndependent()
	{
		config.set("sharePets", false);
		npc(10000);
		settle();
		assertEquals(0, plugin.milestones.pendingCount());
		chat("New item added to your collection log: Pet replacement");
		settle();
		CompanionSnapshot.Milestone collection = plugin.milestones.take(5000).get(0);
		assertEquals("collection_log", collection.rewardType);
		assertEquals(Boolean.TRUE, collection.collectionNew);
		plugin.milestones.reset();
		config.set("sharePets", true);
		config.set("shareCollectionLog", false);
		chat("You have a funny feeling like you're being followed.");
		settle();
		CompanionSnapshot.Milestone pet = plugin.milestones.take(5000).get(0);
		assertEquals("pet", pet.rewardType);
		assertEquals("Pet", pet.item);
		assertNull(pet.itemId);
		assertNull(pet.quantity);
		assertNull(pet.source);
		assertNull(pet.collectionNew);
	}

	@Test
	public void invalidRewardMetadataCannotBeQueuedAsAValidItem()
	{
		plugin.milestones.reward(new RewardObservation("item", "Mystery", null, null, null, null, null, 1000), 2000);
		plugin.milestones.reward(new RewardObservation("pet", "Pet", -1, null, null, null, null, 1000), 2000);
		assertEquals(0, plugin.milestones.pendingCount());
		plugin.milestones.reward(new RewardObservation("collection_log", "Mystery", null, null,
				"guessed", "Last boss seen", true, 1000), 2000);
		CompanionSnapshot.Milestone reward = plugin.milestones.take(2000).get(0);
		assertNull(reward.source);
		String wire = new Gson().toJson(reward);
		assertFalse(wire.contains("itemId"));
		assertFalse(wire.contains("quantity"));
		assertTrue(wire.contains("\"collectionNew\":true"));
	}

	private void npc(int id)
	{
		plugin.onServerNpcLoot(new ServerNpcLoot(RewardTestClient.npc("Boss"),
				Collections.singletonList(new ItemStack(id, 2))));
	}

	private void chat(String text)
	{
		plugin.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0));
	}

	private void settle()
	{
		game.tick += RewardObserver.SETTLE_TICKS;
		plugin.flushRewards(5000L);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = MapCompanionPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
