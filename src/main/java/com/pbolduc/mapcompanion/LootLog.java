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
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;

/**
 * Kill counts and notable drops, kept on this machine.
 *
 * <p>The log lives in the player's RuneScape character/save profile and never needs the map — which is
 * deliberate. Drop luck is the one thing here that is worth installing a plugin for on its own,
 * so it is built to work with no pairing code set, and the map is a place it can also be read
 * rather than the place it lives.
 *
 * <h2>Which event, and why it matters</h2>
 *
 * <p>{@link ServerNpcLoot} rather than {@code NpcLootReceived}. Both exist and both are posted by
 * the core {@code LootManager}, but they come from different paths: the server-authoritative script
 * path posts this one, while the older event is raised from inferring loot off ground items.
 * RuneLite's own loot tracker subscribes to this one and not the other, which settles it — taking
 * both would risk counting a kill twice, and taking the older one alone would trust an inference
 * where an authoritative answer was available.
 *
 * <h2>What is remembered</h2>
 *
 * <p>Every monster that drops loot, with when it was last killed. Two kinds of drop beside it:
 *
 * <ul>
 * <li><b>Rated drops</b> — the bundled table's. Every one is watched from the moment it is in the
 * table, zero included, because a drought needs its zeroes; these are the drops the card can call
 * lucky or dry.</li>
 * <li><b>Other collection-log items</b>, from any monster, read from the game's own collection
 * vocabulary ({@link CollectionItemCatalog}). Only what has actually fallen is kept: there is no
 * table to say what else a monster could have dropped, and a row of zeroes nobody can price is not
 * information. Pets are left out — their arrival does not reliably come through this event.</li>
 * </ul>
 *
 * <p>The bound is {@link #MAX_MONSTERS}, least recently killed first out. That is what keeps a
 * config store that syncs from growing with how long somebody has played: a kill count is a few
 * bytes, and nobody grinds three hundred different monsters they still care about.
 *
 * <p>Nothing is backfilled. These are observed loot kills: core does not emit ServerNpcLoot for
 * every zero-loot outcome, so this is not a lifetime game KC. New catalog entries and unreadable
 * histories start a new per-item observation window. The old unscoped V1 blob remains untouched;
 * attaching it to whichever character happens to log in first would repeat the identity bug.
 * Special worlds are paused because a normal-world denominator is not evidence for a boosted mode.
 *
 * <h2>Two threads, so every door is locked</h2>
 *
 * <p>Kills arrive on the client thread and the sidebar reads the same maps from Swing's, once a
 * second, for as long as the panel is open. That is an unsynchronised iteration racing a write —
 * rare, invisible in testing, and eventually a {@code ConcurrentModificationException} thrown inside
 * a repaint. Every method that touches {@link #tallies} is synchronized rather than the maps being
 * swapped for concurrent ones, because the compound operations here (count a kill, then note the
 * count against a drop) have to be atomic together, which a thread-safe map alone would not give.
 */
@Slf4j
@Singleton
public class LootLog
{
	/** Where the tallies live between sessions, in the plugin's own config group. */
	static final String STORE_KEY = "lootLogV2";
	/** The drop the player pinned, beside the log and scoped to the same character. */
	static final String PIN_KEY = "dropLuckPin";
	/** The relay's integer ceiling; never overflow into a negative or silently rejected count. */
	static final int MAX_COUNT = 10_000_000;
	/** See "What is remembered". */
	static final int MAX_MONSTERS = 300;
	/**
	 * How recent the last kill must be to be "what you are killing now".
	 *
	 * <p>Ten minutes rather than one, because a slow kill and a bank trip in the middle of a task
	 * are both still the task. Past it the card says "Last killed 12 min ago" instead, which is the
	 * same monster with an honest age rather than a claim about the present.
	 */
	static final long CURRENT_MS = 10 * 60_000L;

	private static final Type STORE_TYPE = new TypeToken<Map<String, Tally>>()
	{
	} .getType();

	/**
	 * Which items are notable when the rate table has nothing to say.
	 *
	 * <p>An interface rather than the catalog itself so a test can say "these ids are collection
	 * items" without building the cache enums the catalog reads.
	 */
	interface NotableItems
	{
		/** False until the vocabulary is whole; a half-read catalog must not decide anything. */
		boolean ready();

		boolean notable(int itemId);

		boolean pet(int itemId);

		/** An item's id from its exact name, for a picture of a drop that has not fallen yet. */
		@Nullable
		Integer idFor(String name);

		NotableItems NONE = new NotableItems()
		{
			@Override
			public boolean ready()
			{
				return false;
			}

			@Override
			public boolean notable(int itemId)
			{
				return false;
			}

			@Override
			public boolean pet(int itemId)
			{
				return false;
			}

			@Override
			public Integer idFor(String name)
			{
				return null;
			}
		};

		static NotableItems of(CollectionItemCatalog catalog)
		{
			return new NotableItems()
			{
				@Override
				public boolean ready()
				{
					return catalog.isComplete();
				}

				@Override
				public boolean notable(int itemId)
				{
					return catalog.contains(itemId);
				}

				@Override
				public boolean pet(int itemId)
				{
					return catalog.isPet(itemId);
				}

				@Override
				public Integer idFor(String name)
				{
					return catalog.isComplete() ? catalog.findItemId(name) : null;
				}
			};
		}
	}

	private final Client client;
	private final ConfigStore store;
	private final Gson gson;
	private final NotableItems notables;
	private LongSupplier clock = System::currentTimeMillis;

	/**
	 * Monster name (lower case) to the drop names worth remembering for it.
	 *
	 * <p>Indexed by the name the game prints on a kill, not the wiki page title. The map's
	 * generator strips a trailing {@code (…)} disambiguator so {@code Skeleton (Melzar's Maze)}
	 * and {@code Skeleton (mage)} become {@code Skeleton}, which is what
	 * {@code getComposition().getName()} returns. A wiki variant whose rate disagrees with the
	 * others is absent by design — averaging two denominators would invent a dryness figure
	 * that is true of neither site.
	 */
	private final Map<String, List<Rate>> tracked = new HashMap<>();

	/** Monster name as the game spells it, to what we have seen. Insertion ordered for the panel. */
	private final Map<String, Tally> tallies = new LinkedHashMap<>();

	private boolean loaded = false;
	private String loadedScope;

	/**
	 * At most one full write of the log in this window. The log is up to {@link #MAX_MONSTERS}
	 * tallies of JSON in RuneScape-profile config, which RuneLite syncs and announces to every
	 * plugin's ConfigChanged; rewriting it on every kill meant several of those a tick when
	 * barraging or chinning. Kills inside the window are written by the next due kill, the next
	 * tick past it ({@link #saveIfDue}), or a boundary that would otherwise lose them
	 * ({@link #flush} at shutdown, a reload, a change of character).
	 */
	static final long SAVE_INTERVAL_MS = 30_000L;

	/** Tallies changed since the last write, for {@link #loadedScope}. */
	private boolean unsaved;
	private long savedAtMs;
	/** Which monsters count towards the slayer task; see {@link TaskKills}. */
	private final TaskKills tasks = new TaskKills();
	/** Where the last diary reading is kept for the character; see {@link #kourendEasy}. */
	static final String KOUREND_EASY_KEY = "dropLuckKourendEasy";

	static class Tally
	{
		int kills;
		/** Wall-clock milliseconds of the last kill; zero for a log written before this was kept. */
		long lastKillMs;
		/**
		 * The kill count from which collection items other than the rated ones were watched.
		 *
		 * <p>Null in a log written before they were: such a monster's earlier kills watched only
		 * its rated drops, so the window for everything else starts at the count it was loaded with.
		 */
		Integer collectionSince;
		/** Item name to how many fell, and the kill count when the last one did. */
		Map<String, Integer> obtained = new LinkedHashMap<>();
		Map<String, Integer> killsAtLast = new LinkedHashMap<>();
		/** New rate-table entries start now; old kills cannot prove an item was being watched. */
		Map<String, Integer> trackedSince = new LinkedHashMap<>();
		/** Item name to the id it last fell as, for its picture. Rated zeroes look theirs up. */
		Map<String, Integer> itemIds = new LinkedHashMap<>();
		/**
		 * The rows kept as collection items rather than for a rate.
		 *
		 * <p>Provenance, because the two are watched differently: a rated row is watched on every
		 * kill, a collection row only on kills the vocabulary could judge. Without it a rate dropped
		 * from the table left its row looking like a collection item, and the rate coming back
		 * re-adopted a history nobody had watched in between.
		 */
		Set<String> collection = new LinkedHashSet<>();
		/**
		 * Kills under each boosting condition, ever: "slayer-task" to 412. A boosted drop's wait is
		 * priced by two rates, so its kills are counted apart — per condition rather than per item,
		 * because one kill is on a task for every item at once.
		 */
		Map<String, Integer> boostedKills = new LinkedHashMap<>();
		/** Per boosted item, {@link #boostedKills} under its condition at its last reset or drop. */
		Map<String, Integer> boostedAtLast = new LinkedHashMap<>();
		/** Per boosted item, the same counter where its watching began. */
		Map<String, Integer> boostedAtStart = new LinkedHashMap<>();
	}

	/** One row of the bundled table. Gson fills these by field name. */
	private static class Rate
	{
		String npc;
		String item;
		/** Per kill. A table rolled twice is already folded in by the map's generator. */
		double oneIn;
		/** A better rate under a condition, or null; see {@link #condition}. */
		Boost boost;
	}

	/** Gson fills this from the table's {@code boost: { when, oneIn }}. */
	private static class Boost
	{
		String when;
		double oneIn;
	}

	/** On a slayer task: the plugin learns which monsters count from the task's own counter. */
	static final String SLAYER_TASK = "slayer-task";
	/** With the easy Kourend &amp; Kebos diary done, read from the game at the kill. */
	static final String KOUREND_EASY_DIARY = "kourend-easy-diary";

	private static class RateFile
	{
		List<Rate> rates;
	}

	@Inject
	private LootLog(Client client, ConfigManager configManager, Gson gson, CollectionItemCatalog catalog)
	{
		this(client, ConfigStore.forRuneScapeProfile(configManager), gson, NotableItems.of(catalog));
	}

	/**
	 * The same object, keeping its own state somewhere a test or the integration harness can hold
	 * — see {@link ConfigStore}. Package-private, because a seam is not an API. With no collection
	 * vocabulary it watches the rated drops only, which is what every test written before the
	 * vocabulary existed is about.
	 */
	LootLog(Client client, ConfigStore store, Gson gson)
	{
		this(client, store, gson, NotableItems.NONE);
	}

	LootLog(Client client, ConfigStore store, Gson gson, NotableItems notables)
	{
		this.client = client;
		this.store = store;
		this.gson = gson;
		this.notables = notables;
		loadRates();
	}

	/** For tests that need "ten minutes ago" without waiting ten minutes. */
	void clock(LongSupplier clock)
	{
		this.clock = clock;
	}

	/**
	 * Read the bundled table.
	 *
	 * <p>Guarded end to end. A plugin that throws while loading a resource fails to start at all,
	 * and the honest degradation for a missing table is a plugin that tracks nothing rather than one
	 * that does not run.
	 */
	private void loadRates()
	{
		try
			(InputStream in = getClass().getResourceAsStream("/unique-rates.json"))
		{
			if (in == null)
			{
				log.warn("no bundled drop rates; drop luck will have no odds");
				return;
			}
			RateFile file = gson.fromJson(
								new InputStreamReader(in, StandardCharsets.UTF_8), RateFile.class);
			if (file == null || file.rates == null)
			{
				return;
			}
			for (Rate rate : file.rates)
			{
				if (rate == null || rate.npc == null || rate.item == null || !Double.isFinite(rate.oneIn) || rate.oneIn < 1)
				{
					continue;
				}
				tracked.computeIfAbsent(key(rate.npc), k -> new ArrayList<>()).add(rate);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("could not read the bundled drop rates", e);
		}
	}

	/**
	 * One notable drop, as it fell: what it was, off what, and how many.
	 *
	 * <p>Returned rather than kept, because the two things done with a drop are different jobs. The
	 * tally is an observed total that lives on this machine and answers "how lucky am I"; an
	 * announcement is a thing that happened at a moment and is worth telling a group about once —
	 * see {@link MilestoneOutbox}. This class owns the first and knows nothing about the second.
	 */
	public static final class Notable
	{
		public final String npc;
		public final String item;
		public final int itemId;
		public final int quantity;
		/** The rate, or null for a collection item the table cannot price. */
		@Nullable
		public final Double oneIn;
		/**
		 * Kills this one took, counting the kill that dropped it: from the previous one, or from when
		 * watching began. Only meaningful with a rate; it is what the chat line compares.
		 */
		public final int wait;
		/** How many of {@link #wait} were boosted — on a task, with the diary — priced at {@link #boostOneIn}. */
		public final int waitBoosted;
		@Nullable
		public final Double boostOneIn;
		@Nullable
		public final String boostLabel;

		Notable(String npc, String item, int itemId, int quantity, @Nullable Double oneIn, int wait,
			int waitBoosted, @Nullable Double boostOneIn, @Nullable String boostLabel)
		{
			this.npc = npc;
			this.item = item;
			this.itemId = itemId;
			this.quantity = quantity;
			this.oneIn = oneIn;
			this.wait = wait;
			this.waitBoosted = waitBoosted;
			this.boostOneIn = boostOneIn;
			this.boostLabel = boostLabel;
		}

		/** The rate this drop is priced by, or null with none. */
		@Nullable
		DropOdds.Rate rate()
		{
			return oneIn == null ? null : new DropOdds.Rate(oneIn, boostOneIn, boostLabel);
		}
	}

	/**
	 * Record one kill, and say which notable drops it produced.
	 *
	 * <p>Called from the plugin rather than subscribed here: RuneLite registers the plugin object
	 * on the event bus and nothing else, so an @Subscribe on an injected singleton is a method that
	 * compiles, reads correctly, and never runs. Keeping every subscription in one file is what makes
	 * that impossible to get wrong twice.
	 *
	 * @return the notable drops from this kill, oldest caller's problem what to do with them. Empty
	 *     for the overwhelmingly common case of a kill that dropped nothing worth naming.
	 */
	public synchronized List<Notable> record(ServerNpcLoot event)
	{
		return record(event, null);
	}

	/**
	 * The same, knowing whether the easy Kourend &amp; Kebos diary is done — null where it could not
	 * be read, which restarts the window of every drop that diary boosts rather than guessing.
	 */
	public synchronized List<Notable> record(ServerNpcLoot event, @Nullable Boolean kourendEasy)
	{
		if (!ordinaryWorld(client.getWorldType()) || store.scopeKey() == null || event == null
			|| event.getComposition() == null || event.getItems() == null)
		{
			return Collections.emptyList();
		}
		String name = event.getComposition().getName();
		if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name.trim()))
		{
			return Collections.emptyList();
		}
		ensureLoaded();
		tasks.onKill(name, client.getTickCount());
		List<Rate> watched = tracked.getOrDefault(key(name), Collections.emptyList());

		Tally tally = tallies.get(name);
		if (tally == null)
		{
			tally = new Tally();
			tally.collectionSince = 0;
			tallies.put(name, tally);
			evictBeyondCap(name);
		}
		ensureTracked(tally, watched);
		if (tally.kills >= MAX_COUNT)
		{
			return Collections.emptyList();
		}
		tally.kills++;
		tally.lastKillMs = clock.getAsLong();
		kourendEasy = kourendEasy(kourendEasy);
		/*
		 * Which rate priced this kill, per condition its drops are boosted under. Counted before the
		 * drops so a drop on this kill takes this kill's count as its baseline, the same way
		 * killsAtLast takes this kill's number. A condition nobody could read restarts the drops it
		 * boosts: a wait that may or may not have been boosted has no price.
		 */
		for (String when : conditions(watched))
		{
			Boolean on = condition(when, name, kourendEasy, client.getTickCount());
			if (on == null)
			{
				for (Rate rate : watched)
				{
					if (rate.boost != null && when.equals(rate.boost.when))
					{
						resetHistory(tally, rate.item);
						resetBoost(tally, rate);
					}
				}
			}
			else if (on)
			{
				tally.boostedKills.merge(when, 1, Integer::sum);
			}
		}

		/* Resolve before applying known drops: an unknown stack cannot erase a known reset later
		 * in this same receipt merely because RuneLite supplied its item list in a different order. */
		Map<Integer, String> names = new HashMap<>();
		boolean unreadable = false;
		for (ItemStack stack : event.getItems())
		{
			if (stack == null || stack.getId() <= 0 || stack.getQuantity() <= 0)
			{
				continue;
			}
			String item = itemName(stack.getId());
			names.put(stack.getId(), item);
			unreadable |= item == null;
		}
		if (unreadable)
		{
			/* A failed lookup may conceal a reset. Resume complete evidence from this receipt. */
			for (Rate rate : watched)
			{
				resetHistory(tally, rate.item);
				resetBoost(tally, rate);
			}
		}
		/*
		 * A kill the collection vocabulary could not judge is a kill whose other drops nobody
		 * watched. The rows already kept stay — they are counts of what was seen, never the basis
		 * of a probability — but the window moves, so "since the last" cannot claim a kill that was
		 * never looked at.
		 */
		boolean judged = !unreadable && notables.ready();
		if (!judged)
		{
			tally.collectionSince = tally.kills;
		}
		List<Notable> notable = new ArrayList<>();
		/*
		 * A drop is a kill that dropped it, once — not the items in the stack. A dragon knife falls
		 * as 150, and counting those as 150 drops called one drop "very lucky"; the odds are per
		 * kill, so the count they are compared with is too. Two stacks of one item in one receipt
		 * (two rolls landing on it) are still one kill that dropped it.
		 */
		Set<String> counted = new HashSet<>();
		for (ItemStack stack : event.getItems())
		{
			if (stack == null || stack.getId() <= 0 || stack.getQuantity() <= 0)
			{
				continue;
			}
			String item = names.get(stack.getId());
			if (item == null)
			{
				continue;
			}
			Rate rate = rateFor(watched, item);
			if (rate != null)
			{
				if (!counted.add(rate.item))
				{
					continue;
				}
				int wait = tally.kills - baseline(tally, rate.item);
				int waitBoosted = rate.boost == null ? 0 : boosted(tally, rate) - tally.boostedAtLast.getOrDefault(rate.item, 0);
				count(tally, rate.item, stack);
				if (rate.boost != null)
				{
					tally.boostedAtLast.put(rate.item, boosted(tally, rate));
				}
				notable.add(new Notable(name, rate.item, stack.getId(), stack.getQuantity(), rate.oneIn, wait,
					waitBoosted, rate.boost == null ? null : rate.boost.oneIn, rate.boost == null ? null : boostLabel(rate.boost.when)));
			}
			else if (judged && notables.notable(stack.getId()) && !notables.pet(stack.getId()))
			{
				if (!counted.add(item))
				{
					continue;
				}
				if (!tally.trackedSince.containsKey(item))
				{
					tally.trackedSince.put(item, tally.collectionSince == null ? 0 : tally.collectionSince);
				}
				int wait = tally.kills - baseline(tally, item);
				count(tally, item, stack);
				tally.collection.add(item);
				notable.add(new Notable(name, item, stack.getId(), stack.getQuantity(), null, wait, 0, null, null));
			}
		}
		unsaved = true;
		saveIfDue();
		return notable;
	}

	@Nullable
	private static Rate rateFor(List<Rate> watched, String item)
	{
		for (Rate candidate : watched)
		{
			if (candidate.item.equalsIgnoreCase(item))
			{
				return candidate;
			}
		}
		return null;
	}

	/**
	 * The kill the current wait for this item is counted from.
	 *
	 * <p>For a collection row, never before {@code collectionSince}: a kill the vocabulary could not
	 * judge is one where this item falling would not have been recorded, so the wait restarts there
	 * — in the arithmetic only, because moving {@code trackedSince} past the last drop would make the
	 * row inconsistent and drop it.
	 */
	private static int baseline(Tally tally, String item)
	{
		Integer at = tally.killsAtLast.get(item);
		Integer since = tally.trackedSince.get(item);
		int base = Math.max(since == null ? 0 : since, at == null ? 0 : at);
		if (tally.collection.contains(item) && tally.collectionSince != null)
		{
			base = Math.max(base, tally.collectionSince);
		}
		return base;
	}

	/** The distinct conditions this monster's drops are boosted under. */
	private static Set<String> conditions(List<Rate> watched)
	{
		Set<String> out = new LinkedHashSet<>();
		for (Rate rate : watched)
		{
			if (rate.boost != null && rate.boost.when != null)
			{
				out.add(rate.boost.when);
			}
		}
		return out;
	}

	/**
	 * Whether a condition held on this kill; null where it cannot be known.
	 *
	 * <p>A condition this plugin does not know — a table newer than the plugin — is null too, so its
	 * drops restart rather than being priced at a rate that may never apply.
	 */
	@Nullable
	private Boolean condition(String when, String npc, @Nullable Boolean kourendEasy, int tick)
	{
		if (SLAYER_TASK.equals(when))
		{
			return tasks.onTaskAt(npc, tick);
		}
		if (KOUREND_EASY_DIARY.equals(when))
		{
			return kourendEasy;
		}
		return null;
	}

	/** "on task", "with the diary": how a boosted rate is named beside the ordinary one. */
	@Nullable
	static String boostLabel(@Nullable String when)
	{
		if (SLAYER_TASK.equals(when))
		{
			return "on task";
		}
		if (KOUREND_EASY_DIARY.equals(when))
		{
			return "with the diary";
		}
		return null;
	}

	private static int boosted(Tally tally, Rate rate)
	{
		return rate.boost == null ? 0 : tally.boostedKills.getOrDefault(rate.boost.when, 0);
	}

	/** A boosted item's two baselines, both at the counter's current value. */
	private static void resetBoost(Tally tally, Rate rate)
	{
		if (rate.boost == null)
		{
			tally.boostedAtLast.remove(rate.item);
			tally.boostedAtStart.remove(rate.item);
			return;
		}
		int now = boosted(tally, rate);
		tally.boostedAtLast.put(rate.item, now);
		tally.boostedAtStart.put(rate.item, now);
	}

	private static void count(Tally tally, String item, ItemStack stack)
	{
		tally.obtained.put(item, (int) Math.min(MAX_COUNT, (long) tally.obtained.getOrDefault(item, 0) + 1));
		tally.killsAtLast.put(item, tally.kills);
		tally.itemIds.put(item, stack.getId());
	}

	/**
	 * Over the cap, the least recently killed monster goes — never the one just added, and never
	 * the one the player pinned, which is the grind they told us they care about.
	 */
	private void evictBeyondCap(String keep)
	{
		if (tallies.size() <= MAX_MONSTERS)
		{
			return;
		}
		String pinnedNpc = pinnedNpc();
		String oldest = null;
		long oldestMs = Long.MAX_VALUE;
		for (Map.Entry<String, Tally> entry : tallies.entrySet())
		{
			String name = entry.getKey();
			if (name.equals(keep) || name.equalsIgnoreCase(pinnedNpc))
			{
				continue;
			}
			if (entry.getValue().lastKillMs < oldestMs)
			{
				oldestMs = entry.getValue().lastKillMs;
				oldest = name;
			}
		}
		if (oldest != null)
		{
			tallies.remove(oldest);
		}
	}

	/**
	 * The item's name, or null.
	 *
	 * <p>Guarded because a composition lookup for an id the client has not cached can throw, and a
	 * plugin that throws inside an event callback takes more than itself down. The cost of a failure
	 * is one drop not recognised, which the counter survives.
	 */
	@Nullable
	private String itemName(int itemId)
	{
		try
		{
			ItemComposition composition = client.getItemDefinition(itemId);
			return composition == null ? null : composition.getName();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** What goes on the wire, or null when there is nothing worth sending. */
	@Nullable
	public synchronized CompanionSnapshot.Loot snapshot()
	{
		if (!ordinaryWorld(client.getWorldType()))
		{
			return null;
		}
		ensureLoaded();
		if (tallies.isEmpty())
		{
			return null;
		}
		long now = clock.getAsLong();
		CompanionSnapshot.Loot loot = new CompanionSnapshot.Loot();
		loot.npcs = new ArrayList<>();
		/*
		 * The pinned monster, then most recently killed first, then the rest by kills.
		 *
		 * The wire format caps this at forty monsters and eight drops each, and the cap truncates —
		 * so the order here decides what survives it. Most-killed first kept a monster ground for a
		 * week last year above the one started this morning, which is the one the map's list is
		 * asked about; the pinned one survives either way, because the player said it matters.
		 */
		String pinnedNpc = pinnedNpc();
		for (Map.Entry<String, Tally> entry : ordered(pinnedNpc))
		{
			Tally tally = entry.getValue();
			CompanionSnapshot.Npc npc = new CompanionSnapshot.Npc();
			npc.npc = entry.getKey();
			npc.kills = tally.kills;
			npc.lastKillMsAgo = tally.lastKillMs > 0 ? Math.max(0L, now - tally.lastKillMs) : null;
			npc.dropsComplete = true;
			npc.drops = new ArrayList<>();
			List<Rate> watched = tracked.getOrDefault(key(entry.getKey()), Collections.emptyList());
			/* Sent even for no rated drops: a map whose table has some must learn this one had none. */
			npc.ratedKey = ratedKey(watched);
			ensureTracked(tally, watched);
			for (String item : dropOrder(tally, watched, true))
			{
				int obtained = tally.obtained.getOrDefault(item, 0);
				Integer last = tally.killsAtLast.get(item);
				Integer since = tally.trackedSince.get(item);
				Rate rated = rateFor(watched, item);
				Integer boostedSinceLast = rated == null || rated.boost == null ? null
					: boosted(tally, rated) - tally.boostedAtLast.getOrDefault(item, 0);
				Integer boostedWatched = rated == null || rated.boost == null ? null
					: boosted(tally, rated) - tally.boostedAtStart.getOrDefault(item, 0);
				/**
				 * "Never dropped, watched from the first kill" is the default this monster's rates
				 * already imply, and it is most of the rows. Sending it cost more than the whole
				 * snapshot is allowed: the forty rate-heaviest monsters in the bundled table came to
				 * 16,456 bytes of loot against a 15,872-byte budget for everything, so the readers who
				 * used this feature most were the ones permanently told part of their log was missing.
				 *
				 * <p>{@code dropsComplete} is what makes leaving it out safe: it already says nothing
				 * was lost on the way, which turns an absent row into the default rather than an
				 * unknown. Where it is false the map abstains, exactly as it did before any of this
				 * travelled. A baseline other than zero is a real fact about when tracking began, and
				 * always travels.
				 */
				if (obtained == 0 && last == null && since != null && since == 0
					&& (boostedWatched == null || boostedWatched == 0))
				{
					continue;
				}
				CompanionSnapshot.Drop d = new CompanionSnapshot.Drop();
				d.item = item;
				d.obtained = obtained;
				d.killsAtLast = last;
				d.trackedSinceKill = since;
				d.boostedSinceLast = boostedSinceLast;
				d.boostedWatched = boostedWatched;
				npc.drops.add(d);
			}
			loot.npcs.add(npc);
		}
		String current = current();
		if (current != null)
		{
			loot.current = current;
			loot.currentOnTask = tasks.onTask(current) ? Boolean.TRUE : null;
		}
		String[] pin = pin();
		if (pin != null)
		{
			CompanionSnapshot.Pin wire = new CompanionSnapshot.Pin();
			wire.npc = pin[0];
			wire.item = pin[1];
			loot.pinned = wire;
		}
		return loot;
	}

	/**
	 * Which rated drops this plugin watches for a monster, as eight hex digits.
	 *
	 * <p>The map reads an absent row as "watched from the first kill, never dropped", which is only
	 * true of a drop this plugin was watching. A map whose rate table is newer than the one bundled
	 * here would otherwise tell a player with two visages they have had none. So each monster carries
	 * a fingerprint of the table this plugin used — the drops' names and boosts, lower case, sorted,
	 * one per line, FNV-1a — and the map, which computes the same over its own rows, treats a
	 * mismatch as an incomplete list. {@code lib/drop-luck.ts} has the twin; both pin the same value.
	 */
	static String ratedKey(List<Rate> watched)
	{
		List<String> lines = new ArrayList<>();
		for (Rate rate : watched)
		{
			lines.add(rate.item.toLowerCase(Locale.ROOT).trim() + "|" + (rate.boost == null || rate.boost.when == null ? "" : rate.boost.when));
		}
		Collections.sort(lines);
		int hash = 0x811c9dc5;
		for (byte b : String.join("\n", lines).getBytes(StandardCharsets.UTF_8))
		{
			hash ^= b & 0xff;
			hash *= 0x01000193;
		}
		return String.format(Locale.ROOT, "%08x", hash);
	}

	/** For the test that pins the fingerprint's value against the map's. */
	static String ratedKey(String npc, String item, @Nullable String when)
	{
		Rate rate = new Rate();
		rate.npc = npc;
		rate.item = item;
		if (when != null)
		{
			rate.boost = new Boost();
			rate.boost.when = when;
		}
		return ratedKey(Collections.singletonList(rate));
	}

	/** The pinned monster, then by last kill, then by kills — the order the wire and the list share. */
	private List<Map.Entry<String, Tally>> ordered(@Nullable String pinnedNpc)
	{
		List<Map.Entry<String, Tally>> ordered = new ArrayList<>(tallies.entrySet());
		ordered.sort((a, b) ->
		{
			boolean ap = a.getKey().equalsIgnoreCase(pinnedNpc);
			boolean bp = b.getKey().equalsIgnoreCase(pinnedNpc);
			if (ap != bp)
			{
				return ap ? -1 : 1;
			}
			int recent = Long.compare(b.getValue().lastKillMs, a.getValue().lastKillMs);
			return recent != 0 ? recent : Integer.compare(b.getValue().kills, a.getValue().kills);
		});
		return ordered;
	}

	/**
	 * Rated drops, then the other collection items most recently had first.
	 *
	 * <p>Rarest first on the wire, because the cap truncates from the end and a 1/32,000 history is
	 * the hardest to rebuild. Most common first on the card, because that is the drop a grind is
	 * usually about and the one whose odds mean something within a session.
	 */
	private static List<String> dropOrder(Tally tally, List<Rate> watched, boolean rarestFirst)
	{
		List<Rate> rated = new ArrayList<>(watched);
		rated.sort((a, b) -> rarestFirst ? Double.compare(b.oneIn, a.oneIn) : Double.compare(a.oneIn, b.oneIn));
		List<String> order = new ArrayList<>();
		for (Rate rate : rated)
		{
			order.add(rate.item);
		}
		List<String> others = new ArrayList<>();
		for (String item : tally.obtained.keySet())
		{
			if (rateFor(watched, item) == null)
			{
				others.add(item);
			}
		}
		others.sort((a, b) -> Integer.compare(tally.killsAtLast.getOrDefault(b, 0), tally.killsAtLast.getOrDefault(a, 0)));
		order.addAll(others);
		return order;
	}

	/**
	 * The monster being killed now: the last one killed, if that was within {@link #CURRENT_MS}.
	 */
	@Nullable
	public synchronized String current()
	{
		ensureLoaded();
		String best = null;
		long bestMs = 0;
		for (Map.Entry<String, Tally> entry : tallies.entrySet())
		{
			if (entry.getValue().lastKillMs > bestMs)
			{
				bestMs = entry.getValue().lastKillMs;
				best = entry.getKey();
			}
		}
		return best != null && clock.getAsLong() - bestMs <= CURRENT_MS ? best : null;
	}

	/** The task's varps, every tick, so a kill can be matched to the counter it moved. */
	public void onTick(int tick, int task, int remaining)
	{
		tasks.onTick(tick, task, remaining);
	}

	/** Whether any of this monster's rated drops is boosted under {@code when}. */
	public boolean boosts(String npc, String when)
	{
		return conditions(tracked.getOrDefault(key(npc), Collections.emptyList())).contains(when);
	}

	/** Whether a drop's boost applies to the next kill of this monster, as far as is known now. */
	public synchronized boolean boostedNow(String npc, @Nullable String when)
	{
		if (SLAYER_TASK.equals(when))
		{
			/*
			 * The monster being killed now, on the task — the map's rule, which has only the current
			 * monster's task flag. A drake pinned and banked between task kills reads the same on both.
			 */
			return npc.equalsIgnoreCase(current()) && tasks.onTask(npc);
		}
		return KOUREND_EASY_DIARY.equals(when) && Boolean.TRUE.equals(kourendEasy(null));
	}

	/**
	 * The diary reading to use: this one where the game gave one, else the last one kept for this
	 * character, else null.
	 *
	 * <p>The game answers nothing for fifteen seconds after every login and hop, and a reading of
	 * "unknown" restarts the drops a diary boosts — so taking each kill's reading at its word wiped
	 * the talisman's count whenever a lizardman died in that window, which near the canyon is most
	 * hops. A diary cannot be undone, so the last real answer stands in. It is kept with the
	 * character's log rather than in memory, because the window it covers starts at a login, which
	 * is exactly when memory has just been cleared. A kept "not done" can be one completion stale
	 * for the seconds before the game answers again; that kill is priced at the ordinary rate.
	 */
	@Nullable
	private Boolean kourendEasy(@Nullable Boolean read)
	{
		ensureLoaded();
		if (loadedScope == null)
		{
			return read;
		}
		String kept = store.getScoped(loadedScope, KOUREND_EASY_KEY);
		if (read != null)
		{
			if (!read.toString().equals(kept))
			{
				store.setScoped(loadedScope, KOUREND_EASY_KEY, read.toString());
			}
			return read;
		}
		return kept == null ? null : Boolean.valueOf(kept);
	}

	/** Kills left on the slayer task, for a monster seen to count towards it; otherwise null. */
	@Nullable
	public Integer taskRemaining(String npc)
	{
		return tasks.remaining(npc);
	}

	/**
	 * Everything the sidebar draws, as values.
	 *
	 * <p>Copies rather than the live maps, so Swing can lay it out at leisure while the client thread
	 * goes on counting. Pinned first, then most recent, the same order the wire uses.
	 */
	public synchronized List<Monster> monsters()
	{
		if (!ordinaryWorld(client.getWorldType()))
		{
			return Collections.emptyList();
		}
		ensureLoaded();
		List<Monster> out = new ArrayList<>();
		for (Map.Entry<String, Tally> entry : ordered(pinnedNpc()))
		{
			Tally tally = entry.getValue();
			List<Rate> watched = tracked.getOrDefault(key(entry.getKey()), Collections.emptyList());
			ensureTracked(tally, watched);
			List<Drop> drops = new ArrayList<>();
			for (String item : dropOrder(tally, watched, false))
			{
				Rate rate = rateFor(watched, item);
				Integer id = tally.itemIds.get(item);
				if (id == null)
				{
					id = notables.idFor(item);
				}
				Integer since = tally.trackedSince.get(item);
				boolean boost = rate != null && rate.boost != null;
				drops.add(new Drop(item, id == null ? -1 : id, tally.obtained.getOrDefault(item, 0),
					tally.kills - baseline(tally, item), tally.kills - (since == null ? 0 : since),
					boost ? boosted(tally, rate) - tally.boostedAtLast.getOrDefault(item, 0) : 0,
					boost ? boosted(tally, rate) - tally.boostedAtStart.getOrDefault(item, 0) : 0,
					rate == null ? null : rate.oneIn,
					boost ? rate.boost.oneIn : null, boost ? rate.boost.when : null));
			}
			out.add(new Monster(entry.getKey(), tally.kills, tally.lastKillMs, drops));
		}
		return out;
	}

	/** One monster, for the sidebar. */
	public static final class Monster
	{
		public final String npc;
		public final int kills;
		/** Wall-clock ms of the last kill, or zero when the log predates keeping it. */
		public final long lastKillMs;
		public final List<Drop> drops;

		Monster(String npc, int kills, long lastKillMs, List<Drop> drops)
		{
			this.npc = npc;
			this.kills = kills;
			this.lastKillMs = lastKillMs;
			this.drops = Collections.unmodifiableList(drops);
		}

		/** True where at least one of its drops has a rate, which is where odds can be given. */
		public boolean rated()
		{
			for (Drop drop : drops)
			{
				if (drop.oneIn != null)
				{
					return true;
				}
			}
			return false;
		}
	}

	/** One drop's standing. {@code oneIn} null is a collection item with no rate to price it. */
	public static final class Drop
	{
		public final String item;
		/** For its picture; -1 where neither a drop nor the collection vocabulary has named it. */
		public final int itemId;
		public final int obtained;
		/** Kills since the last one fell, or since watching began when none has. */
		public final int sinceLast;
		/**
		 * Kills since watching began. Overall luck is priced over these, not over every kill: a drop
		 * whose rate arrived at kill 500 has only been watched for the kills since, and dividing its
		 * count by all 1,024 would call it dry on kills nobody looked at.
		 */
		public final int watched;
		/** Of {@link #sinceLast} and {@link #watched}, the kills priced at the boosted rate. */
		public final int boostedSinceLast;
		public final int boostedWatched;
		@Nullable
		public final Double oneIn;
		@Nullable
		public final Double boostOneIn;
		/** "slayer-task", "kourend-easy-diary", or null. */
		@Nullable
		public final String boostWhen;

		Drop(String item, int itemId, int obtained, int sinceLast, int watched, int boostedSinceLast, int boostedWatched,
			@Nullable Double oneIn, @Nullable Double boostOneIn, @Nullable String boostWhen)
		{
			this.item = item;
			this.itemId = itemId;
			this.obtained = obtained;
			this.sinceLast = sinceLast;
			this.watched = watched;
			this.boostedSinceLast = boostedSinceLast;
			this.boostedWatched = boostedWatched;
			this.oneIn = oneIn;
			this.boostOneIn = boostOneIn;
			this.boostWhen = boostWhen;
		}

		/** The rate pair, or null for a collection item with no rate. */
		@Nullable
		DropOdds.Rate rate()
		{
			return oneIn == null ? null : new DropOdds.Rate(oneIn, boostOneIn, boostLabel(boostWhen));
		}

		DropOdds.Kills sinceKills()
		{
			return new DropOdds.Kills(sinceLast - boostedSinceLast, boostedSinceLast);
		}

		DropOdds.Kills watchedKills()
		{
			return new DropOdds.Kills(watched - boostedWatched, boostedWatched);
		}
	}

	/**
	 * Pin a drop, or unpin it with nulls.
	 *
	 * <p>Pinned is "this is my grind": the card shows it when nothing is being killed, the list puts
	 * it on top, the wire keeps its monster past the cap and the map shows the same one.
	 */
	public synchronized void pin(@Nullable String npc, @Nullable String item)
	{
		ensureLoaded();
		if (loadedScope == null)
		{
			return;
		}
		if (npc == null || item == null)
		{
			store.unsetScoped(loadedScope, PIN_KEY);
		}
		else
		{
			store.setScoped(loadedScope, PIN_KEY, npc + "\n" + item);
		}
	}

	/** {npc, item}, or null. Read each time: it is one short string and a relog may change it. */
	@Nullable
	public synchronized String[] pin()
	{
		ensureLoaded();
		if (loadedScope == null)
		{
			return null;
		}
		String raw = store.getScoped(loadedScope, PIN_KEY);
		if (raw == null)
		{
			return null;
		}
		String[] parts = raw.split("\n", -1);
		return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty() ? parts : null;
	}

	@Nullable
	private String pinnedNpc()
	{
		String[] pin = pin();
		return pin == null ? null : pin[0];
	}

	/** A one-line summary for the sidebar, or null when nothing is tracked yet. */
	@Nullable
	public synchronized String summary()
	{
		ensureLoaded();
		/**
		 * The character question comes before the world question, because the world type is empty at
		 * the login screen as well as on a special world. Asking the other way round told a
		 * logged-out player that dryness was paused on this world — the wrong reason — and, because
		 * that string is not null, stood up a Forget button whose scoped delete had no scope to
		 * address and so quietly did nothing.
		 */
		if (loadedScope == null)
		{
			return null;
		}
		if (!ordinaryWorld(client.getWorldType()))
		{
			return PAUSED;
		}
		int monsters = tallies.size();
		if (monsters == 0)
		{
			return null;
		}
		long kills = 0;
		for (Tally tally : tallies.values())
		{
			kills += tally.kills;
		}
		return kills + (kills == 1 ? " observed loot kill" : " observed loot kills")
			   + " across " + monsters + (monsters == 1 ? " monster" : " monsters");
	}

	/** What {@link #summary()} says on a world whose kills are not counted. */
	static final String PAUSED = "Drop luck paused on this world";

	/**
	 * Whether there is anything to forget.
	 *
	 * <p>The button's question, which is not the sidebar's: {@link #summary()} answers with a line
	 * to print, and one of its answers is a note about the world that says nothing about whether a
	 * log exists. Deriving the button from "is that line non-null" offered Forget to a player who
	 * had never tracked a kill.
	 */
	public synchronized boolean hasLog()
	{
		ensureLoaded();
		return loadedScope != null && !tallies.isEmpty();
	}

	/** Forget everything. Offered because a log the player cannot clear is one they cannot trust. */
	public synchronized void clear()
	{
		ensureLoaded();
		if (loadedScope == null)
		{
			/**
			 * Nothing is addressable without a character. Emptying the in-memory map here would look
			 * exactly like a successful clear and then hand the log straight back at the next login.
			 */
			return;
		}
		tallies.clear();
		loaded = true;
		unsaved = false;
		store.unsetScoped(loadedScope, STORE_KEY);
		store.unsetScoped(loadedScope, PIN_KEY);
		store.unsetScoped(loadedScope, KOUREND_EASY_KEY);
	}

	private synchronized void ensureLoaded()
	{
		String scope = store.scopeKey();
		if (!Objects.equals(scope, loadedScope))
		{
			/* Still the old character's: write it there before it is dropped, and start a new window. */
			flush();
			savedAtMs = 0L;
			tallies.clear();
			loaded = false;
			loadedScope = scope;
		}
		if (scope == null)
		{
			return;
		}
		if (loaded)
		{
			return;
		}
		loaded = true;
		try
		{
			String raw = store.getScoped(scope, STORE_KEY);
			if (raw == null || raw.isEmpty())
			{
				return;
			}
			Map<String, Tally> stored = gson.fromJson(raw, STORE_TYPE);
			if (stored != null)
			{
				String before = gson.toJson(stored, STORE_TYPE);
				stored.forEach((name, tally) ->
				{
					if (tally != null && name != null && !name.trim().isEmpty()
						&& tally.kills >= 0 && tally.kills <= MAX_COUNT)
					{
						/** An older blob may predate a field; empty maps beat null dereferences. */
						if (tally.obtained == null)
						{
							tally.obtained = new LinkedHashMap<>();
						}
						if (tally.killsAtLast == null)
						{
							tally.killsAtLast = new LinkedHashMap<>();
						}
						if (tally.trackedSince == null)
						{
							tally.trackedSince = new LinkedHashMap<>();
						}
						if (tally.itemIds == null)
						{
							tally.itemIds = new LinkedHashMap<>();
						}
						if (tally.collection == null)
						{
							tally.collection = new LinkedHashSet<>();
						}
						if (tally.boostedKills == null)
						{
							tally.boostedKills = new LinkedHashMap<>();
						}
						if (tally.boostedAtLast == null)
						{
							tally.boostedAtLast = new LinkedHashMap<>();
						}
						if (tally.boostedAtStart == null)
						{
							tally.boostedAtStart = new LinkedHashMap<>();
						}
						if (tally.lastKillMs < 0)
						{
							tally.lastKillMs = 0;
						}
						if (tally.collectionSince == null || tally.collectionSince < 0 || tally.collectionSince > tally.kills)
						{
							tally.collectionSince = tally.kills;
						}
						ensureTracked(tally, tracked.getOrDefault(key(name), Collections.emptyList()));
						tallies.put(name, tally);
					}
				});
				/* Retired rows cannot survive an unobserved catalog hiatus and later reappear as a
				 * continuous drought. Store the normalized windows, still only in this character. */
				if (!before.equals(gson.toJson(tallies, STORE_TYPE)))
				{
					save();
				}
			}
		}
		catch (RuntimeException e)
		{
			/** A log we cannot read is a log we start again, not a plugin that refuses to load. */
			log.warn("could not read the stored kill log; starting a new one", e);
		}
	}

	/** Write what changed if the window has passed. Cheap enough for every tick when nothing has. */
	public synchronized void saveIfDue()
	{
		if (unsaved && System.currentTimeMillis() - savedAtMs >= SAVE_INTERVAL_MS)
		{
			save();
		}
	}

	/** Write what changed now: logout, shutdown, and before the tallies are dropped. */
	public synchronized void flush()
	{
		if (unsaved)
		{
			save();
		}
	}

	private synchronized void save()
	{
		unsaved = false;
		savedAtMs = System.currentTimeMillis();
		if (loadedScope == null)
		{
			/* Nothing is addressable without a character; see clear(). */
			return;
		}
		try
		{
			store.setScoped(loadedScope, STORE_KEY, gson.toJson(tallies, STORE_TYPE));
		}
		catch (RuntimeException e)
		{
			log.debug("could not save the kill log", e);
		}
	}

	/** Invalidate the cache on login/logout too; another character may use the same settings. */
	public synchronized void reload()
	{
		/* A relog lands here, and a kill still inside the save window belongs to the log it re-reads. */
		flush();
		savedAtMs = 0L;
		tallies.clear();
		loaded = false;
	}

	private static String key(String npc)
	{
		return npc.toLowerCase().trim();
	}

	private static void resetHistory(Tally tally, String item)
	{
		tally.trackedSince.put(item, tally.kills);
		tally.obtained.put(item, 0);
		tally.killsAtLast.remove(item);
	}

	/**
	 * Every rated row present and consistent; every other row a consistent record of a real drop.
	 *
	 * <p>The two are repaired differently on purpose. A rated row that does not add up is restarted
	 * from now — a drought claimed across a gap would be a number with nothing behind it. A
	 * collection row that does not add up is dropped: it was only ever a count of what was seen,
	 * and there is no zero to restart it at that would mean anything. A row that is neither — a
	 * rate that left the table — goes too, and a collection row that gains a rate starts again,
	 * because the kills it was not judged on were never watched the way a rate needs.
	 */
	private static void ensureTracked(Tally tally, List<Rate> watched)
	{
		Set<String> rated = new HashSet<>();
		for (Rate rate : watched)
		{
			rated.add(rate.item);
		}
		for (String item : new ArrayList<>(tally.collection))
		{
			if (rated.contains(item))
			{
				tally.collection.remove(item);
				resetHistory(tally, item);
			}
		}
		for (String item : new ArrayList<>(tally.obtained.keySet()))
		{
			if (rated.contains(item))
			{
				continue;
			}
			Integer obtained = tally.obtained.get(item);
			Integer at = tally.killsAtLast.get(item);
			Integer since = tally.trackedSince.get(item);
			if (!tally.collection.contains(item) || obtained == null || obtained <= 0 || obtained > MAX_COUNT
				|| at == null || since == null || since < 0 || at < since || at > tally.kills)
			{
				tally.obtained.remove(item);
				tally.killsAtLast.remove(item);
				tally.trackedSince.remove(item);
				tally.itemIds.remove(item);
			}
		}
		tally.collection.retainAll(tally.obtained.keySet());
		Set<String> kept = new HashSet<>(tally.obtained.keySet());
		kept.addAll(rated);
		tally.killsAtLast.keySet().retainAll(kept);
		tally.trackedSince.keySet().retainAll(kept);
		tally.itemIds.keySet().retainAll(kept);
		tally.boostedAtLast.keySet().retainAll(rated);
		tally.boostedAtStart.keySet().retainAll(rated);
		for (Rate rate : watched)
		{
			Integer since = tally.trackedSince.get(rate.item);
			Integer obtained = tally.obtained.get(rate.item);
			Integer at = tally.killsAtLast.get(rate.item);
			if (since == null || since < 0 || since > tally.kills || obtained == null
				|| obtained < 0 || obtained > MAX_COUNT || (obtained == 0 && at != null)
				|| (obtained > 0 && (at == null || at < since || at > tally.kills))
				|| !boostConsistent(tally, rate))
			{
				resetHistory(tally, rate.item);
				resetBoost(tally, rate);
			}
		}
	}

	/**
	 * A boosted item's baselines exist and fit inside its counter; an unboosted item has none.
	 *
	 * <p>A rate gaining a boost lands here with no baselines, and restarts: its earlier kills were
	 * never sorted by condition, so they cannot be priced by two rates.
	 */
	private static boolean boostConsistent(Tally tally, Rate rate)
	{
		Integer last = tally.boostedAtLast.get(rate.item);
		Integer start = tally.boostedAtStart.get(rate.item);
		if (rate.boost == null)
		{
			return last == null && start == null;
		}
		int now = boosted(tally, rate);
		int kills = tally.kills - tally.trackedSince.getOrDefault(rate.item, 0);
		return last != null && start != null && start >= 0 && start <= last && last <= now && now - start <= kills;
	}

	/** This table describes ordinary members worlds; F2P cannot roll every listed member item. */
	static boolean ordinaryWorld(EnumSet<WorldType> types)
	{
		return types != null && types.contains(WorldType.MEMBERS)
			   && EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.BOUNTY,
							 WorldType.SKILL_TOTAL, WorldType.HIGH_RISK).containsAll(types);
	}
}
