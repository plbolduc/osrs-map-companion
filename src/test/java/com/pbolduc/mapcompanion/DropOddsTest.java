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

import static org.junit.Assert.assertEquals;
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
		assertEquals("~2 on average", DropOdds.expectation(1024, WHIP));
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
	public void theRestOfATaskAndTheMedianWait()
	{
		assertEquals("24", DropOdds.percent(DropOdds.within(WHIP, 143)));
		assertEquals(355, DropOdds.killsFor(0.5, WHIP));
		assertEquals(1178, DropOdds.killsFor(0.9, WHIP));
	}

	@Test
	public void nothingYetSaysOneThingOnce()
	{
		assertEquals("Very dry right now.", DropOdds.verdict(0, 880 * 2, 880 * 2, WHIP));
		assertEquals("Dry right now.", DropOdds.verdict(0, 880, 880, WHIP));
	}

	@Test
	public void aRareDropIsARateNotAFractionOfADrop()
	{
		assertEquals("1 in 32,000", DropOdds.expectation(1024, 32000));
		assertEquals(DropOdds.Luck.NONE, DropOdds.luck(0, 1024, 32000));
		assertEquals("1 in 5,012.5", DropOdds.expectation(300, 5012.5));
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
		assertEquals("1 in 512 on task", DropOdds.expectation(new DropOdds.Kills(100, 0), tooth, true));
		assertEquals("1 in 2,560", DropOdds.expectation(new DropOdds.Kills(100, 0), tooth, false));
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
	public void aRateFoldedFromTwoRollsPrintsOneDecimal()
	{
		assertEquals("512.3", DropOdds.grouped(1 / (1 - Math.pow(1 - 1.0 / 1024, 2))));
		assertEquals("6,553.8", DropOdds.grouped(1 / (1 - Math.pow(1 - 1.0 / 13107, 2))));
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
		assertEquals("~0.9 on average", DropOdds.expectation(950, 1000));
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
		LootLog.Drop talisman = new LootLog.Drop("Xeric's talisman (inert)", -1, 0, 50, 50, 50, 50, 250.0, 125.0, LootLog.KOUREND_EASY_DIARY);
		LootLog.Monster lizardman = new LootLog.Monster("Lizardman", 50, 0, java.util.Collections.singletonList(talisman));
		DropLuckView.Detail detail = DropLuckView.detail(lizardman, talisman, 100, true);
		assertEquals(DropOdds.percent(DropOdds.within(125, 100)) + "% chance of one", detail.aheadValue);
		assertEquals("Half of players get one within 87 kills with the diary.", detail.median);
	}

	@Test
	public void noneObtainedFarBelowTheMeanIsDry()
	{
		assertEquals(DropOdds.Luck.VERY_DRY, DropOdds.luck(0, 2300, WHIP));
		assertEquals(DropOdds.Luck.NORMAL, DropOdds.luck(2, 1024, WHIP));
	}
}
