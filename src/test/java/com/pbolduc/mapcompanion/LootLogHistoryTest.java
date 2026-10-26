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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.WorldType;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import static org.junit.Assert.*;
import org.junit.Test;

/** Production log plus fake client definitions; no RuneLite profile or game process is opened. */
public class LootLogHistoryTest
{
	private static final class Profiles implements ConfigStore
	{
		String profile = "a";
		boolean switchBeforeScopedOperation;
		final Map<String, String> values = new HashMap<>();
		@Override
		public String scopeKey()
		{
			return profile;
		}

		@Override
		public String get(String key)
		{
			return values.get(profile + ":" + key);
		}

		@Override
		public void set(String key, String value)
		{
			values.put(profile + ":" + key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(profile + ":" + key);
		}

		@Override
		public String getScoped(String scope, String key)
		{
			if (switchBeforeScopedOperation)
			{
				profile = "b";
			}
			return values.get(scope + ":" + key);
		}

		@Override
		public void setScoped(String scope, String key, String value)
		{
			if (switchBeforeScopedOperation)
			{
				profile = "b";
			}
			values.put(scope + ":" + key, value);
		}

		@Override
		public void unsetScoped(String scope, String key)
		{
			if (switchBeforeScopedOperation)
			{
				profile = "b";
			}
			values.remove(scope + ":" + key);
		}
	}

	private final HarnessGame game = new HarnessGame();
	private final Profiles store = new Profiles();
	private final LootLog log = new LootLog(game.client(), store, new Gson());

	public LootLogHistoryTest()
	{
		game.nameItem(4151, "Abyssal whip");
		game.nameItem(995, "Coins");
	}

	private ServerNpcLoot loot(ItemStack... items)
	{
		return new ServerNpcLoot(game.npc("Abyssal demon"), Arrays.asList(items));
	}
	/**
	 * The whip's standing, as the map reads it rather than as the wire spells it.
	 *
	 * <p>The default row — never dropped, watched from the first kill — is no longer sent. One per
	 * rate outgrew the entire snapshot budget, and {@code dropsComplete} already says the list
	 * arrived whole, which is what turns an absence into that default instead of an unknown. This
	 * reconstructs it exactly as {@code lib/dryness.ts} does on the other side, so the tests below
	 * go on asserting the standing a reader is shown, which is the thing that did not change.
	 */
	private CompanionSnapshot.Drop whip()
	{
		CompanionSnapshot.Npc npc = log.snapshot().npcs.get(0);
		for (CompanionSnapshot.Drop drop : npc.drops)
		{
			if (drop.item.equals("Abyssal whip"))
			{
				return drop;
			}
		}
		assertEquals("an absent row is the default only where the list is whole",
					 Boolean.TRUE, npc.dropsComplete);
		CompanionSnapshot.Drop implied = new CompanionSnapshot.Drop();
		implied.item = "Abyssal whip";
		implied.obtained = 0;
		implied.killsAtLast = null;
		implied.trackedSinceKill = 0;
		return implied;
	}

	/** The whip as the sidebar draws it: what the card and the list are built from. */
	private LootLog.Drop sidebarWhip()
	{
		for (LootLog.Drop drop : log.monsters().get(0).drops)
		{
			if (drop.item.equals("Abyssal whip"))
			{
				return drop;
			}
		}
		throw new AssertionError("a rated drop is always on the card, zero included");
	}

	@Test
	public void anotherCharacterCannotInheritThePreviousCharactersDrought()
	{
		log.record(loot(new ItemStack(4151, 1)));
		store.profile = "b";
		assertNull(log.snapshot());
		log.record(loot(new ItemStack(995, 1)));
		assertEquals(0, whip().obtained);
		assertEquals(1, log.snapshot().npcs.get(0).kills);
		store.profile = "a";
		assertEquals(1, whip().obtained);
		assertEquals(Integer.valueOf(1), whip().killsAtLast);
		assertEquals(1, log.snapshot().npcs.get(0).kills);
	}

	@Test
	public void logoutHidesTheLogAndAnOrdinaryRelogRestoresIt()
	{
		log.record(loot(new ItemStack(995, 1)));
		store.profile = null;
		assertNull(log.snapshot());
		log.record(loot(new ItemStack(995, 1)));
		store.profile = "a";
		assertEquals(1, log.snapshot().npcs.get(0).kills);
		log.reload();
		assertEquals(1, log.snapshot().npcs.get(0).kills);
	}

	@Test
	public void ambiguousUnscopedHistoryIsPreservedWithoutBeingAdopted()
	{
		store.values.put("a:lootLogV1", "{\"Abyssal demon\":{\"kills\":10000}}");
		assertNull(log.snapshot());
		log.record(loot(new ItemStack(995, 1)));
		assertEquals(1, log.snapshot().npcs.get(0).kills);
		log.clear();
		assertNull(log.snapshot());
		assertTrue(store.values.containsKey("a:lootLogV1"));
	}

	/**
	 * A stack is one drop. This asserted two for a stack of two until Drop luck compared the count
	 * with per-kill odds: a dragon knife falls as 150, and 150 "drops" called one drop very lucky.
	 * The count is now kills that dropped it, which is the unit the odds are in.
	 */
	@Test
	public void quantityIsNotKillsAndAnotherKillAdvancesTheLastDropCounter()
	{
		log.record(loot(new ItemStack(4151, 2)));
		assertEquals(1, log.snapshot().npcs.get(0).kills);
		assertEquals(1, whip().obtained);
		assertEquals(Integer.valueOf(1), whip().killsAtLast);
		log.record(loot(new ItemStack(995, 80)));
		assertEquals(2, log.snapshot().npcs.get(0).kills);
		assertEquals(Integer.valueOf(1), whip().killsAtLast);
		log.record(loot(new ItemStack(4151, 1), new ItemStack(4151, 1)));
		assertEquals("two stacks in one receipt are still one kill that dropped it", 2, whip().obtained);
		assertEquals(Integer.valueOf(3), whip().killsAtLast);
	}

	@Test
	public void aProfileSwitchDuringClearCannotDeleteTheNextCharactersHistory()
	{
		log.record(loot(new ItemStack(4151, 1)));
		store.profile = "b";
		log.record(loot(new ItemStack(995, 1)));
		String other = store.get(LootLog.STORE_KEY);
		store.profile = "a";
		log.reload();
		store.switchBeforeScopedOperation = true;
		log.clear();
		assertNull(store.values.get("a:" + LootLog.STORE_KEY));
		assertEquals(other, store.values.get("b:" + LootLog.STORE_KEY));
	}

	@Test
	public void aNewlyTrackedItemDoesNotInheritKillsFromBeforeItWasWatched()
	{
		store.set(LootLog.STORE_KEY, "{\"Abyssal demon\":{\"kills\":500,\"obtained\":{},\"killsAtLast\":{},\"trackedSince\":{}}}");
		assertEquals(Integer.valueOf(500), whip().trackedSinceKill);
		assertEquals(0, whip().obtained);
		assertEquals("no drought is claimed for kills nobody watched", 0, sidebarWhip().sinceLast);
		log.record(loot(new ItemStack(995, 1)));
		assertEquals(Integer.valueOf(500), whip().trackedSinceKill);
		assertEquals(501, log.snapshot().npcs.get(0).kills);
	}
	/**
	 * Drop luck says a drought in players' words, as values the card lays out.
	 *
	 * <p>This asserted "2.5× the rate · 8% still waiting" until How dry became Drop luck. The same
	 * 1,300 kills are now "drier than 92% of players" — the same fact as 8% still waiting, said the
	 * way people say it — and, with nothing had yet, one sentence rather than two. The counts stay
	 * grouped, and a rational rate keeps its decimals.
	 */
	@Test
	public void aDroughtIsSaidInPlayersWords()
	{
		store.set(LootLog.STORE_KEY, "{\"Abyssal demon\":{\"kills\":1300,\"obtained\":{\"Abyssal whip\":0},\"killsAtLast\":{},\"trackedSince\":{\"Abyssal whip\":0}}}");
		LootLog.Monster demon = log.monsters().get(0);
		LootLog.Drop whip = sidebarWhip();
		assertEquals(1300, whip.sinceLast);
		DropLuckView.Detail detail = DropLuckView.detail(demon, whip, null);
		assertEquals("Very dry right now.", detail.verdict);
		assertEquals("1,300 kills", detail.sinceValue);
		assertEquals("drier than 92% of players", detail.sinceComparison);
		assertEquals("a rational rate keeps the decimals the table kept",
					 "5,012.5", DropOdds.grouped(5012.5));
	}

	@Test
	public void removingThenRestoringARateCannotBridgeAnUnobservedHistory() throws Exception
	{
		log.record(loot(new ItemStack(4151, 1)));
		Field field = LootLog.class.getDeclaredField("tracked");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, List<Object>> tracked = (Map<String, List<Object>>) field.get(log);
		List<Object> rows = tracked.get("abyssal demon");
		Object whipRate = null;
		for (Object rate : rows)
		{
			Field item = rate.getClass().getDeclaredField("item");
			item.setAccessible(true);
			if ("Abyssal whip".equals(item.get(rate)))
			{
				whipRate = rate;
			}
		}
		assertNotNull(whipRate);
		rows.remove(whipRate);
		log.record(loot(new ItemStack(995, 1)));
		rows.add(whipRate);
		assertEquals(Integer.valueOf(2), whip().trackedSinceKill);
		assertEquals(0, whip().obtained);
		assertNull(whip().killsAtLast);
	}

	@Test
	public void futureOrContradictoryDropHistoryBecomesANewBaseline()
	{
		store.set(LootLog.STORE_KEY, "{\"Abyssal demon\":{\"kills\":500,\"obtained\":{\"Abyssal whip\":1},\"killsAtLast\":{\"Abyssal whip\":999},\"trackedSince\":{\"Abyssal whip\":0}}}");
		assertEquals(Integer.valueOf(500), whip().trackedSinceKill);
		assertEquals(0, whip().obtained);
		assertNull(whip().killsAtLast);
		assertEquals(0, sidebarWhip().sinceLast);
	}

	@Test
	public void failedItemLookupCannotManufactureAContinuingDrought()
	{
		log.record(loot(new ItemStack(4151, 1)));
		log.record(loot(new ItemStack(123456, 1)));
		assertEquals(Integer.valueOf(2), whip().trackedSinceKill);
		assertNull(whip().killsAtLast);
		assertEquals(0, whip().obtained);
	}

	/** The whole log is one config value, so a burst of kills is one write, and none is lost. */
	@Test
	public void killsInsideTheSaveWindowAreWrittenOnceAndNotLost()
	{
		log.record(loot(new ItemStack(995, 1)));
		String first = store.values.get("a:" + LootLog.STORE_KEY);
		assertTrue(first.contains("\"kills\":1"));
		log.record(loot(new ItemStack(995, 1)));
		log.record(loot(new ItemStack(995, 1)));
		assertEquals("inside the window nothing more is written", first, store.values.get("a:" + LootLog.STORE_KEY));
		log.reload();
		assertTrue(store.values.get("a:" + LootLog.STORE_KEY).contains("\"kills\":3"));
		assertEquals(3, log.snapshot().npcs.get(0).kills);
	}

	@Test
	public void invalidQuantitiesCannotBecomeDropsOrOverflowTheCount()
	{
		log.record(loot(new ItemStack(4151, -1), new ItemStack(4151, 0)));
		assertEquals(0, whip().obtained);
		log.record(loot(new ItemStack(4151, Integer.MAX_VALUE)));
		assertEquals("an enormous stack is one drop", 1, whip().obtained);
		/*
		 * The ceiling is read from a stored log alone. Cleared first because a kill still inside
		 * the save window is written by the reload below, and would replace the seeded log.
		 */
		log.clear();
		store.set(LootLog.STORE_KEY, "{\"Abyssal demon\":{\"kills\":" + LootLog.MAX_COUNT + ",\"obtained\":{\"Abyssal whip\":" + LootLog.MAX_COUNT
			+ "},\"killsAtLast\":{\"Abyssal whip\":5},\"trackedSince\":{\"Abyssal whip\":0}}}");
		log.reload();
		log.record(loot(new ItemStack(4151, 1)));
		assertEquals("the count stops at the relay's ceiling rather than overflowing", LootLog.MAX_COUNT, whip().obtained);
	}

	@Test
	public void anUnreadableStackDoesNotEraseAKnownDropInTheSameReceipt()
	{
		log.record(loot(new ItemStack(4151, 1), new ItemStack(123456, 1)));
		assertEquals(Integer.valueOf(1), whip().trackedSinceKill);
		assertEquals(Integer.valueOf(1), whip().killsAtLast);
		assertEquals(1, whip().obtained);
	}

	@Test
	public void aSpecialWorldIsNotEvidenceForOrdinaryDropRates()
	{
		assertFalse("F2P cannot roll member-only drops", LootLog.ordinaryWorld(EnumSet.noneOf(WorldType.class)));
		assertTrue(LootLog.ordinaryWorld(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
		for (WorldType type : EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN, WorldType.BETA_WORLD,
										 WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD, WorldType.QUEST_SPEEDRUNNING,
										 WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA, WorldType.FRESH_START_WORLD))
		{
			assertFalse(type.name(), LootLog.ordinaryWorld(EnumSet.of(WorldType.MEMBERS, type)));
		}
		assertFalse(LootLog.ordinaryWorld(null));
		log.record(loot(new ItemStack(995, 1)));
		game.worldTypes(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL));
		log.record(loot(new ItemStack(4151, 1)));
		assertNull(log.snapshot());
		assertTrue("nothing to draw on a world whose kills are not counted", log.monsters().isEmpty());
		assertEquals(LootLog.PAUSED, log.summary());
		game.worldTypes(EnumSet.of(WorldType.MEMBERS));
		assertEquals(1, log.snapshot().npcs.get(0).kills);
		assertEquals(0, whip().obtained);
	}
	/**
	 * The Forget button appears when there is a log, and the delete it runs is scoped to a
	 * character. At the login screen there is no character, so both had to stop pretending: the
	 * sidebar blamed the world for something the world had not done, and the button ran a delete
	 * with no scope to address, emptying only what was in memory. It looked like it had worked
	 * until the next login handed the whole log back.
	 */
	@Test
	public void aLoggedOutPlayerIsOfferedNothingToForget()
	{
		log.record(loot(new ItemStack(995, 1)));
		assertTrue(log.hasLog());
		assertNotNull(log.summary());

		store.profile = null;
		assertNull("no character selected is not a paused world", log.summary());
		assertFalse("and nothing addressable is offered for forgetting", log.hasLog());

		log.clear();
		store.profile = "a";
		assertTrue("the log was still there at the next login", log.hasLog());
		assertEquals(1, log.snapshot().npcs.get(0).kills);
	}

	/** A note about the world is not a log; the button used to appear for players who had none. */
	@Test
	public void aSpecialWorldNoteDoesNotStandUpAForgetButton()
	{
		game.worldTypes(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL));
		assertEquals(LootLog.PAUSED, log.summary());
		assertFalse(log.hasLog());
	}

	/** Anything that is not the default is information, and all of it still travels. */
	@Test
	public void anInformativeRowStillTravels()
	{
		log.record(loot(new ItemStack(4151, 1)));
		CompanionSnapshot.Npc npc = log.snapshot().npcs.get(0);
		assertEquals("only the drop that actually landed", 1, npc.drops.size());
		assertEquals("Abyssal whip", npc.drops.get(0).item);
		assertEquals(1, npc.drops.get(0).obtained);
		assertEquals(Boolean.TRUE, npc.dropsComplete);
	}

	/**
	 * Renamed from "carry an explicit complete baseline", which is what these rows used to do.
	 * They are not sent at all now — the bytes were the problem — and the completeness claim is
	 * what carries the baseline in their place. The standing a reader ends up with is identical,
	 * which is what the assertions below still check through {@link #whip()}.
	 */
	@Test
	public void neverObtainedRowsAreImpliedByACompleteListRatherThanSent()
	{
		log.record(loot(new ItemStack(995, 1)));
		assertEquals(Boolean.TRUE, log.snapshot().npcs.get(0).dropsComplete);
		assertTrue("the default row is not worth its bytes",
				   log.snapshot().npcs.get(0).drops.isEmpty());
		assertEquals(Integer.valueOf(0), whip().trackedSinceKill);
		assertEquals(0, whip().obtained);
		assertNull(whip().killsAtLast);
	}

	/** Moved with the arithmetic to {@link DropOdds}; the same guarantees, printed without the "%". */
	@Test
	public void tinyNonzeroOddsAreNotPrintedAsImpossibleAndDecimalsStayExact()
	{
		assertEquals("<1", DropOdds.percent(DropOdds.stillWaiting(512, 3000)));
		assertEquals("<1", DropOdds.percent(DropOdds.stillWaiting(512, 10000000)));
		assertEquals("0", DropOdds.percent(DropOdds.stillWaiting(1, 1)));
		assertEquals("100", DropOdds.percent(DropOdds.stillWaiting(1, 0)));
		assertEquals(Long.toString(Math.round(Math.pow(1 - 1 / 12.5, 15) * 100)),
					 DropOdds.percent(DropOdds.stillWaiting(12.5, 15)));
	}
}
