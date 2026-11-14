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
	/* Made up: only the names given below matter to these tests. */
	private static final int EYE = 90_001;
	private static final int FANG = 90_002;
	private static final int CLUE = 90_003;
	private static final int VENOM = 90_004;
	private static final int SHARK = 90_005;
	private static final int AXE_HEAD = 90_006;
	private static final int GOLD_RING = 90_007;

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
			return "Abyssal head".equals(name) ? Integer.valueOf(7979) : "Hydra's eye".equals(name) ? Integer.valueOf(EYE) : null;
		}
	});

	public LootLogDropLuckTest()
	{
		game.nameItem(WHIP, "Abyssal whip");
		game.nameItem(COINS, "Coins");
		game.nameItem(VISAGE, "Draconic visage");
		game.nameItem(VORKI, "Vorki");
		game.nameItem(BONES, "Bones");
		game.nameItem(EYE, "Hydra's eye");
		game.nameItem(FANG, "Hydra's fang");
		game.nameItem(CLUE, "Clue scroll (elite)");
		game.nameItem(VENOM, "Coagulated venom");
		game.nameItem(SHARK, "Raw shark");
		game.nameItem(AXE_HEAD, "Executioner's axe head");
		game.nameItem(GOLD_RING, "Gold ring");
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

	/** Whether any of a monster's drops has a rate, which is where odds can be given. */
	private static boolean rated(LootLog.Monster monster)
	{
		return monster.drops.stream().anyMatch(drop -> drop.oneIn != null);
	}

	@Test
	public void aMonsterWithNoRateIsCountedAndListed()
	{
		kill("Chicken", BONES);
		kill("Chicken", BONES);
		LootLog.Monster chicken = monster("Chicken");
		assertEquals(2, chicken.kills);
		assertTrue("bones are not notable", chicken.drops.isEmpty());
		assertFalse(rated(chicken));
		assertEquals("and it travels, with no drops and a whole list", 2,
			DropLuckView.find(log.monsters(), "Chicken").kills);
	}

	/**
	 * Yama has no rate on file, because a contract fight hands out its uniques; a collection item it
	 * drops is counted. These tests named the Corporeal Beast, then Nex and Zalcano, until each got rates.
	 */
	@Test
	public void aCollectionItemFromAnUnratedMonsterIsKeptWithItsWait()
	{
		kill("Yama", BONES);
		kill("Yama", BONES);
		kill("Yama", VISAGE);
		kill("Yama", BONES);
		LootLog.Drop visage = monster("Yama").drops.get(0);
		assertEquals("Draconic visage", visage.item);
		assertEquals(1, visage.obtained);
		assertEquals(1, visage.sinceLast);
		assertNull("no rate, so never any odds", visage.oneIn);
		assertEquals(VISAGE, visage.itemId);
	}

	@Test
	public void aPetIsNotCountedBecauseItsArrivalIsNotReliablySeen()
	{
		kill("Yama", VORKI);
		assertTrue(monster("Yama").drops.isEmpty());
	}

	/** A kill the vocabulary could not judge moves the window rather than inventing a drought. */
	@Test
	public void killsTheCatalogCouldNotJudgeAreNotWatchedForCollectionItems()
	{
		catalogReady = false;
		kill("Yama", VISAGE);
		assertTrue("not judged, so not counted", monster("Yama").drops.isEmpty());
		catalogReady = true;
		kill("Yama", VISAGE);
		LootLog.Drop visage = monster("Yama").drops.get(0);
		assertEquals(1, visage.obtained);
	}

	@Test
	public void aRatedDropStaysRatedAndAZeroRowHasItsPictureFromTheVocabulary()
	{
		kill("Abyssal demon", COINS);
		LootLog.Monster demon = monster("Abyssal demon");
		assertTrue(rated(demon));
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
		/* A game tick has read which world this is; the list goes by that, since Swing's thread asks for it. */
		log.onTick(1, 0, 0);
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
		/* The first one the log has seen: the kills before it were never counted, and the line says so. */
		assertEquals("Abyssal whip after 80 recorded kills — luckier than 86% of players.", DropLuckView.chatLine(drops.get(0)));
		for (int i = 0; i < 779; i++)
		{
			kill("Abyssal demon", COINS);
		}
		List<LootLog.Notable> second = log.record(new ServerNpcLoot(game.npc("Abyssal demon"),
			Collections.singletonList(new ItemStack(WHIP, 1))));
		assertEquals("a wait between two drops is a whole wait, and is said plainly",
			"Abyssal whip after 780 kills — drier than 78% of players.", DropLuckView.chatLine(second.get(0)));

		List<LootLog.Notable> visage = log.record(new ServerNpcLoot(game.npc("Yama"),
			Collections.singletonList(new ItemStack(VISAGE, 1))));
		assertNull("the game prints its own line for a collection item", DropLuckView.chatLine(visage.get(0)));
	}

	/** The ring comes a piece at a time, so a piece is a drop of the set, counted once a kill. */
	@Test
	public void aPieceOfASetCountsAsOneDropOfTheSet()
	{
		for (int i = 0; i < 99; i++)
		{
			kill("Alchemical Hydra", BONES);
		}
		kill("Alchemical Hydra", BONES, EYE);
		kill("Alchemical Hydra", BONES);
		kill("Alchemical Hydra", BONES, FANG);
		LootLog.Monster hydra = monster("Alchemical Hydra");
		LootLog.Drop ring = hydra.drops.stream().filter(d -> d.item.equals("Brimstone ring piece")).findFirst().orElse(null);
		assertNotNull("the set is one row", ring);
		assertEquals(2, ring.obtained);
		assertEquals(0, ring.sinceLast);
		assertNotNull(ring.oneIn);
		assertTrue("no row of its own for a piece", hydra.drops.stream().noneMatch(d -> d.item.equals("Hydra's eye") || d.item.equals("Hydra's fang")));
		assertEquals("a piece that has fallen draws the row", FANG, ring.itemId);
	}

	@Test
	public void aSetThatHasNotFallenIsDrawnWithItsFirstPiece()
	{
		kill("Alchemical Hydra", BONES);
		LootLog.Drop ring = monster("Alchemical Hydra").drops.stream()
			.filter(d -> d.item.equals("Brimstone ring piece")).findFirst().orElse(null);
		assertNotNull(ring);
		assertEquals(EYE, ring.itemId);
	}

	/**
	 * A destroyed corpse rolls none of Araxxor's loot, so it is not a kill the fang could have
	 * fallen on; a harvest is, and so is one whose only other drop could not be read.
	 */
	@Test
	public void anAraxxorWhoseLootWasGivenUpIsNotCounted()
	{
		kill("Araxxor", SHARK);
		kill("Araxxor", CLUE, VENOM);
		kill("Araxxor");
		kill("Araxxor", SHARK, CLUE);
		assertEquals(2, monster("Araxxor").kills);
		kill("Araxxor", 99_999);
		assertEquals("an unreadable item is never taken for a destroy", 3, monster("Araxxor").kills);
	}

	private LootLog.Drop drop(String npc, String item)
	{
		return monster(npc).drops.stream().filter(d -> d.item.equals(item)).findFirst().orElse(null);
	}

	/** A pet is never in the loot: the game's line after the kill counts it, against that kill. */
	@Test
	public void aPetIsCountedFromTheGamesLineAgainstTheKillItCameWith()
	{
		for (int i = 0; i < 40; i++)
		{
			kill("Cerberus", BONES);
			game.tick();
		}
		List<LootLog.Notable> said = log.onPetMessage(game.client().getTickCount());
		LootLog.Drop pup = drop("Cerberus", "Hellpuppy");
		assertEquals(1, pup.obtained);
		assertEquals(0, pup.sinceLast);
		assertEquals("Hellpuppy", said.get(0).item);
		assertEquals("one message, one pet", 0, log.onPetMessage(game.client().getTickCount()).size());
		assertEquals(1, drop("Cerberus", "Hellpuppy").obtained);
	}

	/** The line can come before the loot; it waits a tick for its kill. */
	@Test
	public void aPetLineBeforeItsKillWaitsForIt()
	{
		kill("Cerberus", BONES);
		game.tick();
		game.tick();
		game.tick();
		assertTrue(log.onPetMessage(game.client().getTickCount()).isEmpty());
		kill("Cerberus", BONES);
		assertEquals(1, drop("Cerberus", "Hellpuppy").obtained);
	}

	/** A pet from woodcutting finds no kill, and counts nothing anywhere. */
	@Test
	public void aPetWithNoKillBesideItCountsNothing()
	{
		kill("Cerberus", BONES);
		for (int i = 0; i < 5; i++)
		{
			game.tick();
		}
		log.onPetMessage(game.client().getTickCount());
		for (int i = 0; i < 5; i++)
		{
			game.tick();
		}
		kill("Cerberus", BONES);
		assertEquals(0, drop("Cerberus", "Hellpuppy").obtained);
	}

	/**
	 * Callisto hands its unique to one player by damage, so a kill others shared is the player's
	 * share of one: 30 of 100 damage is 0.3 of a kill at the claws' rate. Its pet is counted alone,
	 * and a shared kill leaves it where it was. A monster priced per kill pays no attention.
	 */
	@Test
	public void aTeamBossCountsASharedKillAsTheShareOfIt()
	{
		int tick = game.client().getTickCount();
		log.onHitsplat("Callisto", true, false, 30, tick);
		log.onHitsplat("Callisto", false, true, 70, tick);
		kill("Callisto", BONES);
		assertEquals(1, monster("Callisto").kills);

		assertFalse(monster("Callisto").solo);
		LootLog.Drop claws = drop("Callisto", "Claws of Callisto");
		assertEquals(1, claws.sharedWatched);
		assertEquals(0.3, claws.shareWatched, 1e-9);
		assertEquals(0, claws.watchedKills().ordinary);
		LootLog.Drop cub = drop("Callisto", "Callisto cub");
		assertEquals("a pet counted alone takes no share", 0, cub.shareWatched, 1e-9);
		kill("Callisto", BONES);
		assertEquals("a kill with nobody else's hit is a whole one", 1, drop("Callisto", "Claws of Callisto").watchedKills().ordinary);
		log.onHitsplat("Abyssal demon", false, true, 50, game.client().getTickCount());
		kill("Abyssal demon", BONES);
		assertEquals("another player's hit means nothing on a monster priced per kill", 1, monster("Abyssal demon").kills);
	}

	/** Scurrius's pet is the top damage's alone, so a kill another player hit is not counted at all. */
	@Test
	public void aBossPricedAloneSkipsAKillOthersHit()
	{
		log.onHitsplat("Scurrius", false, true, 10, game.client().getTickCount());
		kill("Scurrius", BONES);
		assertNull("shared, so not counted", DropLuckView.find(log.monsters(), "Scurrius"));
		for (int i = 0; i <= LootLog.SHARED_TICKS; i++)
		{
			game.tick();
		}
		kill("Scurrius", BONES);
		assertEquals("a fight goes stale after a minute without a hit", 1, monster("Scurrius").kills);
		assertTrue(monster("Scurrius").solo);
		assertTrue(DropLuckView.context(monster("Scurrius"), false, null, now).endsWith("1 solo kill"));
	}

	/**
	 * Nex's uniques are priced for a player alone, who is her MVP. A shared kill counts its share,
	 * and loses the MVP's 10% where the game did not name the player MVP — before the loot or just
	 * after it. Her minions' damage is hers.
	 */
	@Test
	public void nexWeighsAShareByTheMvpLine()
	{
		int tick = game.client().getTickCount();
		log.onHitsplat("Nex", true, false, 40, tick);
		log.onHitsplat("Fumus", true, false, 10, tick);
		log.onHitsplat("Nex", false, true, 50, tick);
		kill("Nex", BONES);
		assertEquals(0.5 / 1.1, drop("Nex", "Ancient hilt").shareWatched, 1e-9);
		assertEquals("the pet holds no MVP bonus", 0.5, drop("Nex", "Nexling").shareWatched, 1e-9);
		log.onMvpMessage(game.client().getTickCount());
		assertEquals("named MVP just after the loot", 0.5, drop("Nex", "Ancient hilt").shareWatched, 1e-9);
		game.tick();
		log.onMvpMessage(game.client().getTickCount());
		log.onHitsplat("Nex", true, false, 25, game.client().getTickCount());
		log.onHitsplat("Nex", false, true, 75, game.client().getTickCount());
		kill("Nex", BONES);
		assertEquals("named MVP just before it", 0.75, drop("Nex", "Ancient hilt").shareWatched, 1e-9);
	}

	/**
	 * Vardorvis is one name and one NPC id at three combat levels: the ordinary fight, the awakened
	 * one with three times the unique table, and the quest's. Each priced level is its own monster,
	 * and the quest's is not counted.
	 */
	@Test
	public void aBossIsCountedUnderTheVersionItsLevelNames()
	{
		log.record(new ServerNpcLoot(RewardTestClient.npc("Vardorvis", 784), Arrays.asList(new ItemStack(BONES, 1))));
		log.record(new ServerNpcLoot(RewardTestClient.npc("Vardorvis", 1136), Arrays.asList(new ItemStack(AXE_HEAD, 1))));
		log.record(new ServerNpcLoot(RewardTestClient.npc("Vardorvis", 572), Arrays.asList(new ItemStack(AXE_HEAD, 1))));
		assertEquals(1, monster("Vardorvis").kills);
		assertEquals(0, drop("Vardorvis", "Executioner's axe head").obtained);
		assertEquals(1, monster("Vardorvis (awakened)").kills);
		LootLog.Drop axe = drop("Vardorvis (awakened)", "Executioner's axe head");
		assertEquals(1, axe.obtained);
		assertEquals(1088 / 3.0, axe.oneIn, 1e-9);
	}

	/** A vestige is three steps the game shows, a gold ring first; each step is one drop of its progress. */
	@Test
	public void aGoldRingIsAStepOfTheVestige()
	{
		log.record(new ServerNpcLoot(RewardTestClient.npc("Vardorvis", 784), Arrays.asList(new ItemStack(GOLD_RING, 1))));
		log.record(new ServerNpcLoot(RewardTestClient.npc("Vardorvis", 784), Arrays.asList(new ItemStack(GOLD_RING, 2))));
		LootLog.Drop progress = drop("Vardorvis", "Ultor vestige progress");
		assertEquals(2, progress.obtained);
		assertEquals(1088 / 3.0, progress.oneIn, 1e-9);
	}

	/** Outside an instance the canyon shaman is counted, warhammer and all. */
	@Test
	public void theCanyonShamanCountsItsWarhammer()
	{
		kill("Lizardman shaman", BONES);
		assertNotNull(drop("Lizardman shaman", "Dragon warhammer"));
		assertEquals(1, monster("Lizardman shaman").kills);
	}
}
