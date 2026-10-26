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
import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.ScriptID;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Joins complementary reward signals without counting a receipt twice.
 *
 * <p>A first pet can arrive as NPC loot, a pet message, and a collection addition. They are three
 * descriptions of one reward. Six local ticks let those descriptions meet before the outbox gets
 * an immutable observation. Two loot receipts are never merged, even for the same item in the same
 * tick: a time-based set of item ids would silently discard real repeat rewards.
 *
 * <p>RuneLite's current core emits NPC receipts through ServerNpcLoot and forwards them through
 * LootReceived. Only known activity EVENT records are accepted from the latter. Neither player
 * loot nor arbitrary plugin-defined events can become a companion reward. Item collections are
 * read immediately: LootManager clears ServerNpcLoot's backing list after synchronous dispatch.
 *
 * <p>The collection notification hooks are the game's NOTIFICATION_START/DELAY scripts and named
 * varcs, as independently demonstrated by Dink's CollectionNotifier. Pet naming needs the local
 * pet trigger and a catalog-confirmed pet item; no clan message is inspected and unrelated loot
 * cannot supply a guessed name. Missing evidence remains an unnamed pet with no invented source.
 *
 * <p>Game callbacks run on the client thread, but a settings change can reset this object from
 * Swing. The public methods share this monitor, which the plugin also holds through outbox enqueue
 * so a reset cannot finish between taking an observation and assigning its delivery identity.
 */
@Singleton
public final class RewardObserver
{
	static final int SETTLE_TICKS = 6;
	private static final int MAX_PENDING = 256;
	private static final String COLLECTION_PREFIX = "New item added to your collection log: ";
	private static final String POPUP_PREFIX = "New item:";
	private static final String UNTRADEABLE_PREFIX = "Untradeable drop: ";
	private static final Set<String> PET_MESSAGES = new HashSet<>(Arrays.asList(
			"You have a funny feeling like you're being followed.",
			"You have a funny feeling like you would have been followed...",
			"You feel something weird sneaking into your backpack."));
	private static final Pattern PICKPOCKET_MESSAGE = Pattern.compile("You pick (the )?.+'s? pocket.*");
	/**
	 * Exact core LootTrackerPlugin event labels. Chest and shade-key names are transcribed from
	 * <a href="https://github.com/runelite/runelite/blob/ac79ed8bd8926bec7bf172aa291574b4d944b0e7/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L204">its source maps</a>;
	 * core publishes them as EVENT records at the inventory-change boundary, including repeat rolls.
	 */
	private static final Set<String> ACTIVITIES = new HashSet<>(Arrays.asList(
			"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut", "Barrows",
			"Lunar Chest", "Fortis Colosseum", "Doom of Mokhaiotl", "Fishing Trawler",
			"Kingdom of Miscellania", "Drift Net",
			"H.A.M. chest (steel)", "H.A.M. chest (bronze)", "H.A.M. chest (silver)", "H.A.M. chest (iron)",
			"Regular Bird House", "Oak Bird House", "Willow Bird House", "Teak Bird House", "Maple Bird House",
			"Mahogany Bird House", "Yew Bird House", "Magic Bird House", "Redwood Bird House",
			"Brimstone Chest", "Larran's big chest", "Larran's small chest", "Elven Crystal Chest",
			"Crystal Chest", "Stone chest", "Dorgesh-Kaan Chest", "Grubby Chest", "Isle of Souls Chest",
			"Dark Chest", "Rogues' Chest", "Chest (Ancient Vault)", "Muddy Chest", "Chest (Aldarin Villas)",
			"Chest (Moon key)", "Chest (Alchemist's signet)", "Rusty chest", "Tarnished chest", "Reinforced chest",
			"Bronze key red", "Bronze key brown", "Bronze key crimson", "Bronze key black", "Bronze key purple",
			"Steel key red", "Steel key brown", "Steel key crimson", "Steel key black", "Steel key purple",
			"Black key red", "Black key brown", "Black key crimson", "Black key black", "Black key purple",
			"Silver key red", "Silver key brown", "Silver key crimson", "Silver key black", "Silver key purple",
			"Gold key red", "Gold key brown", "Gold key crimson", "Gold key black", "Gold key purple",
			"Simple lockbox", "Elaborate lockbox", "Ornate lockbox", "Cache of runes", "Intricate pouch",
			"Frozen cache", "Bounty crate (tier 1)", "Bounty crate (tier 2)", "Bounty crate (tier 3)",
			"Bounty crate (tier 4)", "Bounty crate (tier 5)", "Bounty crate (tier 6)", "Bounty crate (tier 7)",
			"Bounty crate (tier 8)", "Bounty crate (tier 9)", "Apprentice potion pack", "Adept potion pack",
			"Expert potion pack", "Castle wars supply crate", "Forgotten lockbox", "Dossier",
			"Hunters' loot sack", "Hunters' loot sack (basic)", "Hunters' loot sack (adept)",
			"Hunters' loot sack (expert)", "Hunters' loot sack (master)",
			"Tarnished 2h sword", "Tarnished amulet", "Tarnished battleaxe", "Tarnished halberd",
			"Tarnished longsword", "Tarnished necklace", "Tarnished ring", "Tarnished spear", "Tarnished bracelet",
			"Vale offerings", "Dirty arrowtips", "Rubium geode",
			"Small salvage", "Fishy salvage", "Barracuda salvage", "Large salvage", "Plundered salvage",
			"Martial salvage", "Fremennik salvage", "Opulent salvage",
			"Herbiboar", "Zombie Pirate's Locker", "Golem Crafting", "Seed pack", "Bird nest", "Casket",
			"Ore Pack (Volcanic Mine)", "Bag full of gems (Percy)", "Bag full of gems (Belona)",
			"Bag full of gems (Dusuri)", "Spoils of war",
			"Reward pool (Tempoross)", "Casket (Tempoross)", "Guardians of the Rift",
			"Supply crate (Wintertodt)", "Reward cart (Wintertodt)", "Hallowed Sack",
			"Barbarian Assault high gamble", "Unsired", "Supply crate (Mahogany Homes)",
			"Clue Scroll (Beginner)", "Clue Scroll (Easy)", "Clue Scroll (Medium)",
			"Clue Scroll (Hard)", "Clue Scroll (Elite)", "Clue Scroll (Master)"));
	private final Client client;
	private final CollectionItemCatalog catalog;
	private final Predicate<Integer> includedItem;
	private final LongSupplier clock;
	private final List<Pending> pending = new ArrayList<>();
	private boolean popupStarted;
	private int pickpocketTick = Integer.MIN_VALUE;

	@Inject
	public RewardObserver(Client client, CollectionItemCatalog catalog)
	{
		this(client, catalog, catalog::contains);
	}

	/** The caller may narrow supplies or add a separately maintained non-collection exception. */
	public RewardObserver(Client client, CollectionItemCatalog catalog, Predicate<Integer> includedItem)
	{
		this(client, catalog, includedItem, System::currentTimeMillis);
	}

	RewardObserver(Client client, CollectionItemCatalog catalog, Predicate<Integer> includedItem, LongSupplier clock)
	{
		this.client = client;
		this.catalog = catalog;
		this.includedItem = includedItem;
		this.clock = clock;
	}

	public synchronized void onNpcLoot(ServerNpcLoot event)
	{
		if (event == null || event.getComposition() == null || client.getTickCount() == pickpocketTick)
		{
			return;
		}
		String source = CollectionItemCatalog.clean(event.getComposition().getName());
		if (source != null)
		{
			readLoot(event.getItems(), "npc", source);
		}
	}

	public synchronized void onLootReceived(LootReceived event)
	{
		// Core's "Loot Chest" EVENT is a PvP loot-key withdrawal, not a newly earned activity reward.
		if (event != null && event.getType() == LootRecordType.EVENT && ACTIVITIES.contains(event.getName()))
		{
			readLoot(event.getItems(), "activity", event.getName());
		}
	}

	private void readLoot(Collection<ItemStack> items, String sourceKind, String sourceName)
	{
		if (items == null)
		{
			return;
		}
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		for (ItemStack item : items)
		{
			if (item == null || item.getQuantity() <= 0)
			{
				continue;
			}
			Integer id = catalog.canonicalId(item.getId());
			if (id != null && includedItem.test(id))
			{
				long sum = (long) quantities.getOrDefault(id, 0) + item.getQuantity();
				quantities.put(id, (int) Math.min(Integer.MAX_VALUE, sum));
			}
		}
		for (Map.Entry<Integer, Integer> item : quantities.entrySet())
		{
			String name = catalog.itemName(item.getKey());
			if (name == null)
			{
				continue;
			}
			Pending reward = findNamed(item.getKey(), name, false, true);
			if (reward == null)
			{
				reward = add(name, item.getKey());
			}
			if (reward != null)
			{
				reward.loot = true;
				reward.quantity = item.getValue();
				reward.sourceKind = sourceKind;
				reward.sourceName = sourceName;
				attachPet(reward);
				separateAmbiguousCollection(item.getKey(), name);
			}
		}
	}

	public synchronized void onChatMessage(ChatMessage event)
	{
		onChatMessage(event, true, true);
	}

	/** Sharing categories are independent; collection text can name an already observed pet. */
	public synchronized void onChatMessage(ChatMessage event, boolean pets, boolean collections)
	{
		if (event == null || "runelite".equalsIgnoreCase(event.getName())
			|| "runelite".equalsIgnoreCase(event.getSender()))
		{
			return;
		}
		String message = CollectionItemCatalog.clean(event.getMessage());
		if (message == null)
		{
			return;
		}
		if ((event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)
			&& PICKPOCKET_MESSAGE.matcher(message).matches())
		{
			pickpocketTick = client.getTickCount();
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		if (pets && PET_MESSAGES.contains(message))
		{
			Pending pet = findUnclaimedPetItem();
			if (pet == null)
			{
				pet = add("Pet", null);
			}
			if (pet != null)
			{
				pet.pet = true;
			}
		}
		else if (message.startsWith(COLLECTION_PREFIX)
				 && client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) == 1)
		{
			if (collections)
			{
				collection(message.substring(COLLECTION_PREFIX.length()));
			}
			else if (pets)
			{
				namePet(message.substring(COLLECTION_PREFIX.length()));
			}
		}
		else if (pets && message.startsWith(UNTRADEABLE_PREFIX))
		{
			namePet(message.substring(UNTRADEABLE_PREFIX.length()));
		}
	}

	/**
	 * The only scripts this reads. ScriptPreFired fires for every client script, hundreds a second
	 * in a bank or a busy chatbox, so the plugin asks this before it takes any lock or reads config.
	 */
	static boolean watchesScript(int scriptId)
	{
		return scriptId == ScriptID.NOTIFICATION_START || scriptId == ScriptID.NOTIFICATION_DELAY;
	}

	public synchronized void onScriptPreFired(ScriptPreFired event)
	{
		onScriptPreFired(event, true, true);
	}

	public synchronized void onScriptPreFired(ScriptPreFired event, boolean pets, boolean collections)
	{
		if (event == null)
		{
			return;
		}
		if (event.getScriptId() == ScriptID.NOTIFICATION_START)
		{
			popupStarted = true;
		}
		else if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY)
		{
			boolean started = popupStarted;
			popupStarted = false;
			int mode = client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM);
			if (started && (mode == 2 || mode == 3) && "Collection log".equalsIgnoreCase(
					CollectionItemCatalog.clean(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE))))
			{
				String message = CollectionItemCatalog.clean(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN));
				if (message != null && message.startsWith(POPUP_PREFIX))
				{
					if (collections)
					{
						collection(message.substring(POPUP_PREFIX.length()));
					}
					else if (pets)
					{
						namePet(message.substring(POPUP_PREFIX.length()));
					}
				}
			}
		}
	}

	private void collection(String itemName)
	{
		String name = CollectionItemCatalog.clean(itemName);
		if (name == null || name.length() > 120)
		{
			return;
		}
		Integer id = catalog.findItemId(name);
		for (Pending observed : pending)
		{
			// Collection additions are unique, unlike loot receipts and duplicate pet rolls. A
			// repeated notification callback must not attach that one addition to a second receipt.
			if (observed.collection && matches(observed, id, name))
			{
				return;
			}
		}
		// A first collection unlock is an achievement even when later copies are ordinary supplies.
		Pending reward = findNamed(id, name, true, false);
		if (reward == null)
		{
			reward = add(name, id);
		}
		if (reward != null)
		{
			reward.collection = true;
			reward.collectionTick = client.getTickCount();
			reward.collectionAt = clock.getAsLong();
			reward.ambiguousCollection = matchingLootCount(id, name) > 1;
			attachPet(reward);
		}
	}

	private void namePet(String itemName)
	{
		String name = CollectionItemCatalog.clean(itemName);
		Integer id = catalog.findItemId(name);
		if (id != null && catalog.isPet(id))
		{
			Pending pet = unnamedPet();
			if (pet != null)
			{
				pet.item = name;
				pet.itemId = id;
			}
		}
	}

	/** Flushes completed observations; the caller owns sharing policy and outbox assignment. */
	public synchronized List<RewardObservation> onGameTick(long nowMillis, int tick)
	{
		catalog.onGameTick(tick);
		List<RewardObservation> result = new ArrayList<>();
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();)
		{
			Pending reward = it.next();
			if (tick - reward.tick >= SETTLE_TICKS)
			{
				String type = reward.pet ? "pet" : reward.loot ? "item" : "collection_log";
				result.add(new RewardObservation(type, reward.item, reward.itemId, reward.quantity,
												 reward.sourceKind, reward.sourceName, reward.collection ? Boolean.TRUE : null,
												 Math.min(reward.at, nowMillis)));
				it.remove();
			}
		}
		return result;
	}

	public synchronized void reset()
	{
		pending.clear();
		popupStarted = false;
		pickpocketTick = Integer.MIN_VALUE;
	}

	private Pending add(String name, Integer id)
	{
		if (pending.size() >= MAX_PENDING - 1)
		{
			// Keep one slot for splitting a collection join if a later receipt makes it ambiguous.
			// Existing observations keep their order rather than becoming an endless replay backlog.
			return null;
		}
		Pending reward = new Pending(name, id, client.getTickCount(), clock.getAsLong());
		pending.add(reward);
		return reward;
	}

	private Pending findNamed(Integer id, String name, boolean needsCollection, boolean needsLoot)
	{
		Pending found = null;
		for (Pending reward : pending)
		{
			if (recent(reward) && !(needsCollection && reward.collection) && !(needsLoot && reward.loot)
				&& !reward.ambiguousCollection && matches(reward, id, name))
			{
				if (found != null)
				{
					// Matching item names establish identity, not which of two receipts supplied it.
					return null;
				}
				found = reward;
			}
		}
		if (found != null && found.itemId == null)
		{
			found.itemId = id;
		}
		return found;
	}

	private static boolean matches(Pending reward, Integer id, String name)
	{
		return id != null && reward.itemId != null
			   ? Objects.equals(id, reward.itemId) : name.equalsIgnoreCase(reward.item);
	}

	private int matchingLootCount(Integer id, String name)
	{
		int count = 0;
		for (Pending reward : pending)
		{
			if (recent(reward) && reward.loot && matches(reward, id, name))
			{
				count++;
			}
		}
		return count;
	}

	private void separateAmbiguousCollection(Integer id, String name)
	{
		if (matchingLootCount(id, name) < 2)
		{
			return;
		}
		for (Pending reward : pending)
		{
			if (recent(reward) && reward.loot && reward.collection && matches(reward, id, name))
			{
				// The collection signal may arrive first. A later second receipt makes its source
				// ambiguous before flush, so undo the earlier join without losing either receipt.
				Pending collection = new Pending(reward.item, reward.itemId, reward.collectionTick,
												 reward.collectionAt);
				collection.collection = true;
				collection.ambiguousCollection = true;
				reward.collection = false;
				pending.add(collection);
				return;
			}
		}
	}

	private boolean recent(Pending reward)
	{
		int age = client.getTickCount() - reward.tick;
		return age >= 0 && age < SETTLE_TICKS;
	}

	private Pending unnamedPet()
	{
		for (Pending reward : pending)
		{
			if (recent(reward) && reward.pet && reward.itemId == null)
			{
				return reward;
			}
		}
		return null;
	}

	private Pending findUnclaimedPetItem()
	{
		Pending found = null;
		for (Pending reward : pending)
		{
			if (recent(reward) && !reward.pet && reward.itemId != null && catalog.isPet(reward.itemId))
			{
				if (found != null)
				{
					// Two candidate pet items cannot establish which triggered this message.
					return null;
				}
				found = reward;
			}
		}
		return found;
	}

	private void attachPet(Pending reward)
	{
		if (!reward.pet && reward.itemId != null && catalog.isPet(reward.itemId))
		{
			Pending pet = unnamedPet();
			if (pet != null)
			{
				reward.pet = true;
				pending.remove(pet);
			}
		}
	}

	private static final class Pending
	{
		String item;
		Integer itemId;
		Integer quantity;
		String sourceKind;
		String sourceName;
		boolean loot;
		boolean collection;
		boolean ambiguousCollection;
		int collectionTick;
		long collectionAt;
		boolean pet;
		final int tick;
		final long at;

		Pending(String item, Integer itemId, int tick, long at)
		{
			this.item = item;
			this.itemId = itemId;
			this.tick = tick;
			this.at = at;
		}
	}
}
