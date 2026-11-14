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
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The cases {@code lib/drop-luck.test.ts} pins on the map's side, with the same numbers.
 *
 * <p>The sidebar and the map each do this arithmetic, and the point of pinning the same cases in
 * both suites is that a change of threshold or wording on one side fails the other's tests rather
 * than leaving two surfaces telling one player two different things.
 */
public class DropOddsTest
{
	private static final double WHIP = 512;

	@Test
	public void fourWhipsWhereTwoIsUsualIsLucky()
	{
		assertEquals(DropOdds.Luck.LUCKY, DropOdds.luck(4, 1024, WHIP));
		assertEquals("~2", DropOdds.usual(DropOdds.Kills.of(1024), DropOdds.Rate.of(WHIP)));
	}

	@Test
	public void sevenHundredKillsSinceTheLastIsDrierThanThreeQuarters()
	{
		assertEquals("75", DropOdds.percent(DropOdds.within(WHIP, 700)));
		assertEquals("dry right now", DropOdds.now(DropOdds.within(WHIP, 700)));
		assertEquals("Lucky overall, dry right now.", DropOdds.verdict(4, 1024, 700, WHIP));
	}

	/** 74.5% prints as 75 and must be called dry: the word reads the printed number. */
	@Test
	public void theWordFollowsThePrintedPercentageNotTheOneBehindIt()
	{
		double share = DropOdds.within(WHIP, 700);
		assertTrue("the unrounded share is under the threshold", share < 0.75);
		assertEquals("dry right now", DropOdds.now(share));
	}

	@Test
	public void theRestOfATask()
	{
		assertEquals("24", DropOdds.percent(DropOdds.within(WHIP, 143)));
	}

	/** One word for both halves is said once; the card used to say "dry" twice in one sentence. */
	@Test
	public void aVerdictIsSaidOnceWhereOverallAndNowAgree()
	{
		assertEquals("Dry, overall and now.", DropOdds.verdict(1, 1500, 800, WHIP));
		assertEquals("About normal, overall and now.", DropOdds.verdict(2, 1024, 300, WHIP));
	}

	@Test
	public void nothingYetSaysOneThingOnce()
	{
		assertEquals("Very dry right now.", DropOdds.verdict(0, 880 * 2, 880 * 2, WHIP));
		assertEquals("Dry right now.", DropOdds.verdict(0, 880, 880, WHIP));
	}

	/**
	 * A row says its rate, whole, as the wiki prints it: the owner asked that "1 in 469.1" lose its
	 * decimal, so 5,012.5 is no longer kept either. How many is usual moved to the details' "Had".
	 */
	@Test
	public void everyRatedRowSaysItsRateInWholeNumbers()
	{
		assertEquals("1 in 32,000", DropOdds.rateLine(DropOdds.Rate.of(32000), false, 1));
		assertEquals(DropOdds.Luck.NONE, DropOdds.luck(0, 1024, 32000));
		assertEquals("1 in 5,013", DropOdds.rateLine(DropOdds.Rate.of(5012.5), false, 1));
		assertEquals("under 0.1", DropOdds.usual(DropOdds.Kills.of(10), DropOdds.Rate.of(32000)));
	}

	@Test
	public void aFinishedWaitIsComparedFromWhicheverSideItFellOn()
	{
		assertEquals("luckier than 86% of players", DropOdds.waitComparison(80, WHIP));
		assertEquals("drier than 78% of players", DropOdds.waitComparison(780, WHIP));
	}

	@Test
	public void aWaitStillGoingSaysHowManyAreInTheSamePlaceUntilTheMedian()
	{
		assertEquals(">99% of players would still be waiting too", DropOdds.waitSoFar(2, WHIP));
		assertEquals("drier than 75% of players", DropOdds.waitSoFar(700, WHIP));
		assertEquals("drier than 50% of players", DropOdds.waitSoFar(355, WHIP));
	}

	@Test
	public void percentagesNeverRoundAPossibilityAway()
	{
		assertEquals("<1", DropOdds.percent(0.001));
		assertEquals(">99", DropOdds.percent(0.9999));
		assertEquals("0", DropOdds.percent(0));
		assertEquals("100", DropOdds.percent(1));
	}

	/**
	 * A drake's tooth is 1/2,560 off task and 1/512 on. Three hundred kills off and a hundred on is
	 * priced by both — 27% would have had one — and by neither rate alone (11% or 18% off, 82% on).
	 */
	@Test
	public void aMixedWaitIsPricedByBothRates()
	{
		DropOdds.Rate tooth = new DropOdds.Rate(2560, 512.0, "on task");
		DropOdds.Kills wait = new DropOdds.Kills(300, 100);
		assertEquals("27", DropOdds.percent(DropOdds.within(tooth, wait)));
		assertEquals("11", DropOdds.percent(DropOdds.within(2560, 300)));
		assertEquals("1 in 512 on task", DropOdds.rateLine(tooth, true, 1));
		assertEquals("1 in 2,560", DropOdds.rateLine(tooth, false, 1));
		assertEquals("one in thirty task kills of a 1/512 drop: 94% would have none",
			DropOdds.Luck.VERY_LUCKY, DropOdds.luck(1, new DropOdds.Kills(0, 30), tooth));
	}

	@Test
	public void aRateWithOneRateIsTheMixedFormWithNoBoostedKills()
	{
		DropOdds.Rate whip = DropOdds.Rate.of(WHIP);
		assertEquals(DropOdds.within(WHIP, 700), DropOdds.within(whip, DropOdds.Kills.of(700)), 0);
		assertEquals(DropOdds.atLeast(4, 1024, 1.0 / WHIP), DropOdds.atLeast(4, DropOdds.Kills.of(1024), 1.0 / WHIP, 1.0 / WHIP), 1e-12);
	}

	@Test
	public void aRateFoldedFromTwoRollsPrintsWholeAsEveryRateDoes()
	{
		assertEquals("512", DropOdds.grouped(1 / (1 - Math.pow(1 - 1.0 / 1024, 2))));
		assertEquals("6,554", DropOdds.grouped(1 / (1 - Math.pow(1 - 1.0 / 13107, 2))));
	}

	/** The map's lib/drop-luck.test.ts pins the same two values: the fingerprints must agree. */
	@Test
	public void theRatedDropsFingerprintIsTheMapsToo()
	{
		assertEquals("e933764e", LootLog.ratedKey("Drake", "Drake's tooth", "slayer-task"));
		assertEquals("a5ab08c4", LootLog.ratedKey("Abyssal demon", "Abyssal whip", null));
	}

	/** The map's `toFixed(1)` rounds the double's exact value; the sidebar now does the same. */
	@Test
	public void oneDecimalRoundsLikeTheMap()
	{
		assertEquals("0.9", DropOdds.oneDecimal(0.95));
		assertEquals("8.9", DropOdds.oneDecimal(8.95));
		assertEquals("~0.9", DropOdds.usual(DropOdds.Kills.of(950), DropOdds.Rate.of(1000)));
	}

	/**
	 * Past two hundred drops the normal curve takes over: the exact sum underflowed there and
	 * called 780 drops in 200,000 kills of a 1/250 drop "very dry", and its cost grew as the square.
	 */
	@Test
	public void hugeCountsStayFastAndHonest()
	{
		assertEquals(DropOdds.Luck.DRY, DropOdds.luck(780, 200_000, 250));
		assertEquals(DropOdds.Luck.NORMAL, DropOdds.luck(800, 200_000, 250));
		long started = System.nanoTime();
		DropOdds.luck(5_000_000, 10_000_000, 2);
		assertTrue("a malformed count cannot stall the sidebar", System.nanoTime() - started < 50_000_000L);
		assertEquals(DropOdds.atLeast(150, 30_000, 1.0 / 250), DropOdds.atLeast(150, DropOdds.Kills.of(30_000), 1.0 / 250, 1.0 / 250), 0);
	}

	/** On a Lizardmen task with the diary done, the rest of the task is priced at the diary's rate. */
	@Test
	public void aDiaryRateHoldsForTheRestOfASlayerTask()
	{
		LootLog.Drop talisman = new LootLog.Drop("Xeric's talisman (inert)", -1, 0, 50, 50, 50, 50, 250.0, 125.0, LootLog.KOUREND_EASY_DIARY,
			0, 0, 0, 0, 1, false);
		LootLog.Monster lizardman = new LootLog.Monster("Lizardman", 50, 0, java.util.Collections.singletonList(talisman));
		DropLuckView.Detail detail = DropLuckView.detail(lizardman, talisman, 100, true);
		assertEquals(DropOdds.percent(DropOdds.within(125, 100)) + "% chance", detail.aheadValue);
		assertEquals("Rest of this Slayer task (100 kills)", detail.aheadLabel);
	}

	@Test
	public void noneObtainedFarBelowTheMeanIsDry()
	{
		assertEquals(DropOdds.Luck.VERY_DRY, DropOdds.luck(0, 2300, WHIP));
		assertEquals(DropOdds.Luck.NORMAL, DropOdds.luck(2, 1024, WHIP));
	}

	/** A monster killed {@code kills} times, each drop as {name, 1 in, kills since the last}. */
	private static LootLog.Monster monster(String npc, int kills, Object[]... drops)
	{
		List<LootLog.Drop> out = new ArrayList<>();
		for (Object[] drop : drops)
		{
			int since = (int) drop[2];
			out.add(new LootLog.Drop((String) drop[0], -1, since == kills ? 0 : 1, since, kills, 0, 0,
				((Number) drop[1]).doubleValue(), null, null, 0, 0, 0, 0, 1, false));
		}
		return new LootLog.Monster(npc, kills, 0, out);
	}

	private static final LootLog.Monster GORILLA = monster("Demonic gorilla", 3000,
		new Object[] {"Zenyte shard", 300, 130}, new Object[] {"Ballista limbs", 500, 900},
		new Object[] {"Ballista spring", 500, 300}, new Object[] {"Light frame", 750, 500},
		new Object[] {"Heavy frame", 1500, 700}, new Object[] {"Monkey tail", 1500, 400});
	private static final LootLog.Monster CERBERUS = monster("Cerberus", 2700,
		new Object[] {"Eternal crystal", 520, 100}, new Object[] {"Pegasian crystal", 520, 50},
		new Object[] {"Primordial crystal", 520, 2600}, new Object[] {"Smouldering stone", 520, 300},
		new Object[] {"Jar of Souls", 2000, 800});
	private static final LootLog.Monster DEMON = monster("Abyssal demon", 1024,
		new Object[] {"Abyssal whip", 512, 700}, new Object[] {"Abyssal head", 6000, 1024},
		new Object[] {"Abyssal dagger", 32000, 1024});

	/**
	 * The list's chip, on the map's numbers. Six drops each ordinary alone are not a dry monster,
	 * though the limbs' 900 kills is drier than 83% of players: one of six that dry is usual.
	 */
	@Test
	public void theDriestOfSeveralDropsIsWeighedAgainstHowManyCouldBeThatDry()
	{
		DropLuckView.Standing gorilla = DropLuckView.standing(GORILLA, null);
		assertEquals("Ballista limbs", gorilla.drop.item);
		assertEquals("34", DropOdds.percent(gorilla.drierThan));
		assertEquals(DropOdds.Luck.NONE, DropLuckView.dryNow(gorilla));
	}

	/** A real drought survives the weighing: 2,600 kills among four crystals at one rate. */
	@Test
	public void aLongDroughtAmongEquallyCommonDropsIsStillVeryDry()
	{
		DropLuckView.Standing cerberus = DropLuckView.standing(CERBERUS, null);
		assertEquals("Primordial crystal", cerberus.drop.item);
		assertEquals("97", DropOdds.percent(cerberus.drierThan));
		assertEquals(DropOdds.Luck.VERY_DRY, DropLuckView.dryNow(cerberus));
		assertEquals("Primordial crystal: 2,600 kills since the last", DropLuckView.standingNote(cerberus));
	}

	/** A dagger at 1 in 32,000 cannot be dry after 1,024 kills, so it does not dilute the whip. */
	@Test
	public void aDropTooRareToBeDryYetDoesNotDiluteTheOthers()
	{
		DropLuckView.Standing demon = DropLuckView.standing(DEMON, null);
		assertEquals("Abyssal whip", demon.drop.item);
		assertEquals("75", DropOdds.percent(demon.drierThan));
		assertEquals(DropOdds.Luck.DRY, DropLuckView.dryNow(demon));
	}

	/** Pinned is chosen before looking, so it is judged on its own wait and nothing else is. */
	@Test
	public void aPinnedDropIsJudgedOnItsOwnWait()
	{
		String[] pin = {"Demonic gorilla", "Ballista limbs"};
		DropLuckView.Standing pinned = DropLuckView.standing(GORILLA, pin);
		assertEquals("83", DropOdds.percent(pinned.drierThan));
		assertEquals(DropOdds.Luck.DRY, DropLuckView.dryNow(pinned));
		assertEquals("a pin on another monster changes nothing here",
			"34", DropOdds.percent(DropLuckView.standing(GORILLA, new String[] {"Cerberus", "Jar of Souls"}).drierThan));
	}

	@Test
	public void driestSortsByTheSameStandingTheChipShows()
	{
		List<LootLog.Monster> order = DropLuckView.list(Arrays.asList(GORILLA, DEMON, CERBERUS), "", DropLuckView.Sort.DRIEST, null);
		assertEquals("Cerberus", order.get(0).npc);
		assertEquals("Abyssal demon", order.get(1).npc);
		assertEquals("Demonic gorilla", order.get(2).npc);
		assertNull("a monster with no rated drop has no standing",
			DropLuckView.standing(new LootLog.Monster("Hill Giant", 40, 0, new ArrayList<>()), null));
		assertEquals(DropOdds.Luck.NONE, DropLuckView.dryNow(null));
	}

	/**
	 * A shared kill is the player's share of one: two kills at 0.5 each leave as many still waiting
	 * as one kill alone, and expect as many drops.
	 */
	@Test
	public void aSharedKillIsPricedAsTheShareOfOne()
	{
		DropOdds.Rate rate = DropOdds.Rate.of(100);
		DropOdds.Kills shared = new DropOdds.Kills(3, 0, 2, 1.0);
		assertEquals(DropOdds.stillWaiting(100, 4), DropOdds.stillWaiting(rate, shared), 1e-12);
		assertEquals(5, shared.total());
		assertEquals("a run deep in the Doom is worth more than its one claim", 7.0, new DropOdds.Kills(0, 0, 2, 7.0).share, 0);
		assertEquals(DropOdds.atLeast(1, 4, 0.01), DropOdds.atLeast(1, shared, 0.01, 0.01), 1e-12);
	}

	/**
	 * "Any unique" adds a boss's rated drops up, the pet apart. Nex's six are the wiki's own 1/43,
	 * and three in 212 kills alone is dry while each item alone is normal. The map's
	 * lib/drop-luck.test.ts pins the same Nex.
	 */
	@Test
	public void anyUniqueIsNexsOwnRollAndDryWhereEachItemIsNormal()
	{
		LootLog.Monster nex = nex(0);
		LootLog.Drop any = DropLuckView.anyUnique(nex);
		assertEquals("1 in 43, +10% as MVP", DropLuckView.rowDetail(nex, any, false));
		List<String> rows = new ArrayList<>();
		for (LootLog.Drop drop : nex.drops)
		{
			rows.add(DropLuckView.rowDetail(nex, drop, false));
		}
		assertEquals(java.util.Arrays.asList("1 in 172", "1 in 258", "1 in 258", "1 in 258", "1 in 258", "1 in 516", "1 in 500"), rows);
		assertEquals(3, any.obtained);
		assertEquals(75, any.sinceLast);
		assertEquals(DropOdds.Luck.DRY, DropLuckView.rowLuck(nex, any));
		for (LootLog.Drop drop : nex.drops.subList(0, 5))
		{
			assertEquals(drop.item, DropOdds.Luck.NORMAL, DropLuckView.rowLuck(nex, drop));
		}
		DropLuckView.Detail detail = DropLuckView.detail(nex, any, null);
		assertEquals("Dry, overall and now.", detail.verdict);
		assertEquals("3 (usual ~5.4)", detail.had);
		assertEquals("75 kills", detail.sinceValue);
		assertEquals("drier than 86% of players", detail.sinceComparison);
		assertEquals("93% chance", detail.aheadValue);
		assertEquals(DropLuckView.ANY, DropLuckView.selected(nex, null, null).item);
		assertEquals("Nihil horn", DropLuckView.selected(nex, "Nihil horn", null).item);
		assertEquals("Zaryte vambraces", DropLuckView.selected(nex, null, new String[] {"Nex", "Zaryte vambraces"}).item);
	}

	/** Rows watched over different kills are no one wait, and one rated drop has nothing to add up. */
	@Test
	public void anyUniqueAbstainsWhereTheDropsAreNotOneWait()
	{
		assertNull(DropLuckView.anyUnique(nex(50)));
		assertNull(DropLuckView.anyUnique(monster("Abyssal demon", 100, new Object[] {"Abyssal whip", 512, 100})));
	}

	/** Nex alone, 212 kills: the platelegs at kill 64, the horn at 120, the vambraces at 137. */
	private static LootLog.Monster nex(int hiltWatchedFrom)
	{
		Object[][] rows = {
			{"Zaryte vambraces", 172, 137}, {"Nihil horn", 258, 120}, {"Torva full helm (damaged)", 258, null},
			{"Torva platebody (damaged)", 258, null}, {"Torva platelegs (damaged)", 258, 64}, {"Ancient hilt", 516, null},
		};
		List<LootLog.Drop> out = new ArrayList<>();
		for (Object[] row : rows)
		{
			Integer at = (Integer) row[2];
			int watched = "Ancient hilt".equals(row[0]) ? 212 - hiltWatchedFrom : 212;
			out.add(new LootLog.Drop((String) row[0], -1, at == null ? 0 : 1, at == null ? watched : 212 - at, watched, 0, 0,
				(Integer) row[1] / 1.1, null, null, 0, 0, 0, 0, 1.1, false));
		}
		out.add(new LootLog.Drop("Nexling", -1, 0, 212, 212, 0, 0, 500.0, null, null, 0, 0, 0, 0, 1, true));
		return new LootLog.Monster("Nex", 212, 0, out);
	}
}
