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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import org.junit.Test;

/**
 * What drop luck added to the kill log: every monster, the collection items beside the rated
 * ones, when each monster was last killed, the pin, and the order the wire keeps under its cap.
 */
public class LootLogDropLuckTest
{
	private static final int WHIP = 4151;
	private static final int COINS = 995;
	private static final int VISAGE = 11286;
	private static final int VORKI = 21992;
	private static final int BONES = 526;

	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final Set<Integer> collection = new HashSet<>(Arrays.asList(VISAGE, VORKI, WHIP));
	private boolean catalogReady = true;
	private long now = 1_000_000_000L;
	private final LootLog log = new LootLog(game.client(), store, new Gson(), new LootLog.NotableItems()
	{
		@Override
		public boolean ready()
		{
			return catalogReady;
		}

		@Override
		public boolean notable(int itemId)
		{
			return collection.contains(itemId);
		}

		@Override
		public boolean pet(int itemId)
		{
			return itemId == VORKI;
		}

		@Override
		public Integer idFor(String name)
		{
			return "Abyssal head".equals(name) ? 7979 : null;
		}
	});

	public LootLogDropLuckTest()
	{
		game.nameItem(WHIP, "Abyssal whip");
		game.nameItem(COINS, "Coins");
		game.nameItem(VISAGE, "Draconic visage");
		game.nameItem(VORKI, "Vorki");
		game.nameItem(BONES, "Bones");
		log.clock(() -> now);
	}

	private void kill(String npc, int... items)
	{
		ItemStack[] stacks = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++)
		{
			stacks[i] = new ItemStack(items[i], 1);
		}
		log.record(new ServerNpcLoot(game.npc(npc), Arrays.asList(stacks)));
	}

	private LootLog.Monster monster(String npc)
	{
		LootLog.Monster found = DropLuckView.find(log.monsters(), npc);
		assertNotNull(npc + " is tracked", found);
		return found;
	}

	@Test
	public void aMonsterWithNoRateIsCountedAndListed()
	{
		kill("Chicken", BONES);
		kill("Chicken", BONES);
		LootLog.Monster chicken = monster("Chicken");
		assertEquals(2, chicken.kills);
		assertTrue("bones are not notable", chicken.drops.isEmpty());
		assertFalse(chicken.rated());
		assertEquals("and it travels, with no drops and a whole list", 2,
			DropLuckView.find(log.monsters(), "Chicken").kills);
	}

	/** Corporeal Beast has no rate on file; a collection item it drops is counted. */
	@Test
	public void aCollectionItemFromAnUnratedMonsterIsKeptWithItsWait()
	{
		kill("Corporeal Beast", BONES);
		kill("Corporeal Beast", BONES);
		kill("Corporeal Beast", VISAGE);
		kill("Corporeal Beast", BONES);
		LootLog.Drop visage = monster("Corporeal Beast").drops.get(0);
		assertEquals("Draconic visage", visage.item);
		assertEquals(1, visage.obtained);
		assertEquals(1, visage.sinceLast);
		assertNull("no rate, so never any odds", visage.oneIn);
		assertEquals(VISAGE, visage.itemId);
	}

	@Test
	public void aPetIsNotCountedBecauseItsArrivalIsNotReliablySeen()
	{
		kill("Corporeal Beast", VORKI);
		assertTrue(monster("Corporeal Beast").drops.isEmpty());
	}

	/** A kill the vocabulary could not judge moves the window rather than inventing a drought. */
	@Test
	public void killsTheCatalogCouldNotJudgeAreNotWatchedForCollectionItems()
	{
		catalogReady = false;
		kill("Corporeal Beast", VISAGE);
		assertTrue("not judged, so not counted", monster("Corporeal Beast").drops.isEmpty());
		catalogReady = true;
		kill("Corporeal Beast", VISAGE);
		LootLog.Drop visage = monster("Corporeal Beast").drops.get(0);
		assertEquals(1, visage.obtained);
	}

	@Test
	public void aRatedDropStaysRatedAndAZeroRowHasItsPictureFromTheVocabulary()
	{
		kill("Abyssal demon", COINS);
		LootLog.Monster demon = monster("Abyssal demon");
		assertTrue(demon.rated());
		LootLog.Drop head = null;
		for (LootLog.Drop drop : demon.drops)
		{
			if (drop.item.equals("Abyssal head"))
			{
				head = drop;
			}
		}
		assertNotNull("every rated drop is on the card, zero included", head);
		assertEquals(0, head.obtained);
		assertEquals(7979, head.itemId);
		assertNotNull(head.oneIn);
	}

	@Test
	public void theCurrentMonsterIsTheLastKilledForTenMinutes()
	{
		kill("Abyssal demon", COINS);
		now += 60_000L;
		kill("Gargoyle", COINS);
		assertEquals("Gargoyle", log.current());
		now += LootLog.CURRENT_MS;
		assertEquals("Gargoyle", log.current());
		now += 1;
		assertNull("past ten minutes it is the last monster killed, not the one being killed", log.current());
	}

	@Test
	public void theWireCarriesRecencyTheCurrentMonsterAndThePin()
	{
		kill("Abyssal demon", COINS);
		now += 60_000L;
		kill("Gargoyle", COINS);
		log.pin("Abyssal demon", "Abyssal whip");
		now += 5_000L;
		CompanionSnapshot.Loot loot = log.snapshot();
		assertEquals("the pinned monster first, whatever its age", "Abyssal demon", loot.npcs.get(0).npc);
		assertEquals("Gargoyle", loot.npcs.get(1).npc);
		assertEquals(Long.valueOf(65_000L), loot.npcs.get(0).lastKillMsAgo);
		assertEquals(Long.valueOf(5_000L), loot.npcs.get(1).lastKillMsAgo);
		assertEquals("Gargoyle", loot.current);
		assertNull("unknown is absent, never false", loot.currentOnTask);
		assertEquals("Abyssal demon", loot.pinned.npc);
		assertEquals("Abyssal whip", loot.pinned.item);
	}

	@Test
	public void aTaskKillIsMarkedOnTheWire()
	{
		log.onTick(1, 42, 150);
		kill("Abyssal demon", COINS);
		log.onTick(2, 42, 149);
		assertEquals(Boolean.TRUE, log.snapshot().currentOnTask);
		assertEquals(Integer.valueOf(149), log.taskRemaining("Abyssal demon"));
	}

	@Test
	public void mostRecentlyKilledComesFirstSoTheCapKeepsTheGrindStartedToday()
	{
		for (int i = 0; i < 50; i++)
		{
			kill("Old grind", COINS);
		}
		now += 60_000L;
		kill("Today", COINS);
		assertEquals("Today", log.snapshot().npcs.get(0).npc);
	}

	@Test
	public void pastTheCapTheLeastRecentlyKilledGoesButNeverThePinnedOne()
	{
		kill("Pinned grind", COINS);
		log.pin("Pinned grind", "Coins");
		now += 1;
		kill("Oldest", COINS);
		for (int i = 0; i < LootLog.MAX_MONSTERS - 1; i++)
		{
			now += 1;
			kill("Monster " + i, COINS);
		}
		List<LootLog.Monster> all = log.monsters();
		assertEquals(LootLog.MAX_MONSTERS, all.size());
		assertNotNull("the pin is the grind they told us they care about", DropLuckView.find(all, "Pinned grind"));
		assertNull("the least recently killed made room", DropLuckView.find(all, "Oldest"));
	}

	@Test
	public void forgettingTheLogForgetsThePinToo()
	{
		kill("Abyssal demon", COINS);
		log.pin("Abyssal demon", "Abyssal whip");
		log.clear();
		assertNull(log.pin());
	}

	/** A rate leaving the table must not leave its row looking like a collection item. */
	@Test
	public void aRatedRowDoesNotSurviveAsACollectionRowWhenItsRateGoes() throws Exception
	{
		kill("Abyssal demon", WHIP);
		java.lang.reflect.Field field = LootLog.class.getDeclaredField("tracked");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<String, List<Object>> tracked = (java.util.Map<String, List<Object>>) field.get(log);
		tracked.put("abyssal demon", Collections.emptyList());
		kill("Abyssal demon", COINS);
		assertTrue(monster("Abyssal demon").drops.isEmpty());
	}

	/** A drake killed on its task, then off it: the wait is split by which rate priced each kill. */
	@Test
	public void aTaskKillIsCountedApartForADropWithATaskRate()
	{
		game.nameItem(22966, "Drake's tooth");
		log.onTick(1, 42, 150);
		kill("Drake", COINS);
		log.onTick(2, 42, 149);
		kill("Drake", COINS);
		log.onTick(3, 42, 148);
		log.onTick(500, 0, 0);
		kill("Drake", COINS);
		LootLog.Drop tooth = null;
		for (LootLog.Drop drop : monster("Drake").drops)
		{
			if (drop.item.equals("Drake's tooth"))
			{
				tooth = drop;
			}
		}
		assertNotNull(tooth);
		assertEquals(3, tooth.sinceLast);
		assertEquals("the second kill was seen to move the counter; the first taught it", 1, tooth.boostedSinceLast);
		assertEquals(Double.valueOf(512), tooth.boostOneIn);
		assertEquals(LootLog.SLAYER_TASK, tooth.boostWhen);
		CompanionSnapshot.Drop wire = null;
		for (CompanionSnapshot.Drop drop : DropLuckViewTestSupport.npc(log.snapshot(), "Drake").drops)
		{
			if (drop.item.equals("Drake's tooth"))
			{
				wire = drop;
			}
		}
		assertNotNull("a row with boosted kills is information, and travels", wire);
		assertEquals(Integer.valueOf(1), wire.boostedSinceLast);
		assertEquals(Integer.valueOf(1), wire.boostedWatched);
	}

	/**
	 * The game says nothing about diaries for fifteen seconds after each login and hop. A diary
	 * cannot be undone, so the last real reading stands in, kept with the character's log — and
	 * only a diary never once read restarts the talisman's window.
	 */
	@Test
	public void anUnreadDiaryUsesTheLastReadingAndOnlyANeverReadOneRestarts()
	{
		ServerNpcLoot coins = new ServerNpcLoot(game.npc("Lizardman"), Arrays.asList(new ItemStack(COINS, 1)));
		log.record(coins, null);
		assertEquals("never read: the window restarts", 0, monster("Lizardman").drops.get(0).watched);
		log.record(coins, true);
		log.record(coins, true);
		LootLog.Drop talisman = monster("Lizardman").drops.get(0);
		assertEquals(2, talisman.boostedWatched);
		log.reload();
		log.record(coins, null);
		talisman = monster("Lizardman").drops.get(0);
		assertEquals("a relog's silent window keeps the history", 3, talisman.watched);
		assertEquals("and prices the kill at the diary's rate", 3, talisman.boostedWatched);
		assertTrue(log.boosts("Lizardman", LootLog.KOUREND_EASY_DIARY));
		assertFalse(log.boosts("Abyssal demon", LootLog.KOUREND_EASY_DIARY));
	}

	/** Luck over the kills a drop was watched for: a rate that arrived at kill 500 is not dry on 1,024. */
	@Test
	public void overallLuckCountsOnlyTheKillsADropWasWatchedFor()
	{
		store.set(LootLog.STORE_KEY, "{\"Abyssal demon\":{\"kills\":1024,\"collectionSince\":0,"
			+ "\"obtained\":{\"Abyssal whip\":2},\"killsAtLast\":{\"Abyssal whip\":900},\"trackedSince\":{\"Abyssal whip\":800}}}");
		LootLog.Drop whip = null;
		for (LootLog.Drop drop : monster("Abyssal demon").drops)
		{
			if (drop.item.equals("Abyssal whip"))
			{
				whip = drop;
			}
		}
		assertEquals(224, whip.watched);
		assertEquals("two in 224 watched kills of a 1/512 drop is very lucky",
			DropOdds.Luck.VERY_LUCKY, DropLuckView.rowLuck(monster("Abyssal demon"), whip));
		assertEquals("over all 1,024 kills the same two would have read as ordinary",
			DropOdds.Luck.NORMAL, DropOdds.luck(2, 1024, 512));
	}

	@Test
	public void theChatLineComparesTheWaitAndSaysNothingWithoutARate()
	{
		for (int i = 0; i < 79; i++)
		{
			kill("Abyssal demon", COINS);
		}
		List<LootLog.Notable> drops = log.record(new ServerNpcLoot(game.npc("Abyssal demon"),
			Collections.singletonList(new ItemStack(WHIP, 1))));
		assertEquals(1, drops.size());
		assertEquals(80, drops.get(0).wait);
		assertEquals("Abyssal whip after 80 kills — luckier than 86% of players.", DropLuckView.chatLine(drops.get(0)));

		List<LootLog.Notable> visage = log.record(new ServerNpcLoot(game.npc("Corporeal Beast"),
			Collections.singletonList(new ItemStack(VISAGE, 1))));
		assertNull("the game prints its own line for a collection item", DropLuckView.chatLine(visage.get(0)));
	}
}
