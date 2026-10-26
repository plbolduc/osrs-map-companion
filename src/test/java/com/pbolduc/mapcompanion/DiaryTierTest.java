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
import org.junit.Test;

/**
 * Turning four flag values into one answer about a region, including refusing to.
 *
 * <p>The failure worth guarding is not getting a tier wrong; it is answering {@code none} when the
 * truthful answer is "could not tell". The map treats those two oppositely — {@code none} marks
 * every diary-gated route unusable, {@code unknown} leaves the reader's own manually entered
 * answer standing — so a confident zero here quietly deletes a player's own settings, and the
 * player's only clue would be routes they used yesterday no longer being offered.
 */
public class DiaryTierTest
{
	private static final int[] AT_ONE = {1, 1, 1, 1};
	/** Karamja: the three older flags mean done at 2, the modern elite one at 1. */
	private static final int[] KARAMJA = {2, 2, 2, 1};

	@Test
	public void namesTheHighestTierThatIsDone()
	{
		assertEquals("hard", DiaryReader.completedTierFrom(new int[] {1, 1, 1, 0}, AT_ONE));
		assertEquals("easy", DiaryReader.completedTierFrom(new int[] {1, 0, 0, 0}, AT_ONE));
		assertEquals("elite", DiaryReader.completedTierFrom(new int[] {1, 1, 1, 1}, AT_ONE));
	}

	@Test
	public void nothingDoneIsNoneRatherThanUnknown()
	{
		/** A real answer, and a different one from "could not tell". Both have to exist. */
		assertEquals("none", DiaryReader.completedTierFrom(new int[] {0, 0, 0, 0}, AT_ONE));
		assertEquals("none", DiaryReader.tierFrom(new int[] {0, 0, 0, 0}, AT_ONE));
	}

	@Test
	public void aFlagThatCannotBeReadIsDoubtRatherThanAZero()
	{
		assertEquals("unknown",
					 DiaryReader.completedTierFrom(new int[] {DiaryReader.UNREADABLE, 0, 0, 0}, AT_ONE));
		assertEquals("unknown",
					 DiaryReader.tierFrom(new int[] {DiaryReader.UNREADABLE, 0, 0, 0}, AT_ONE));
	}

	@Test
	public void aTierFinishedOutOfOrderDoesNotProveTheOnesBelowIt()
	{
		/**
		 * Tasks can be done from any tier in any order, so a finished medium with easy undone is an
		 * account that has finished neither in the sense the map reads, which is also the sense a
		 * reward claim needs. This read "medium" while completion was taken to be cumulative.
		 */
		assertEquals("none", DiaryReader.completedTierFrom(new int[] {0, 1, 0, 0}, AT_ONE));
		assertEquals("easy", DiaryReader.completedTierFrom(new int[] {1, 0, 1, 1}, AT_ONE));
	}

	@Test
	public void doubtUnderAFinishedTierLeavesCompletionUnknownButNotARewardClaim()
	{
		/** An unreadable easy flag could be the zero that stops the run, so completion cannot say. */
		assertEquals("unknown",
					 DiaryReader.completedTierFrom(new int[] {DiaryReader.UNREADABLE, 1, 1, 0}, AT_ONE));
		/**
		 * A claimed reward is cumulative: claiming hard needs easy and medium complete, so an
		 * unreadable easy reward flag is not a reason to throw the region's answer away.
		 */
		assertEquals("hard",
					 DiaryReader.tierFrom(new int[] {DiaryReader.UNREADABLE, 1, 1, 0}, AT_ONE));
	}

	@Test
	public void doubtAboveTheHighestProvenTierMakesTheRegionUnknown()
	{
		/** Reporting "medium" here would be reporting a ceiling that may not be one. */
		assertEquals("unknown",
					 DiaryReader.completedTierFrom(new int[] {1, 1, DiaryReader.UNREADABLE, 0}, AT_ONE));
		assertEquals("unknown",
					 DiaryReader.tierFrom(new int[] {1, 1, DiaryReader.UNREADABLE, 0}, AT_ONE));
	}

	@Test
	public void karamjaNeedsTwoBeforeItsOlderFlagsCount()
	{
		assertEquals("medium", DiaryReader.completedTierFrom(new int[] {2, 2, 0, 0}, KARAMJA));
		/** The elite flag is a modern one and counts at 1, as RuneLite's own clue code reads it. */
		assertEquals("elite", DiaryReader.completedTierFrom(new int[] {2, 2, 2, 1}, KARAMJA));
	}

	@Test
	public void aKaramjaOneIsNotReadAsDoneAndNotReadAsUndone()
	{
		/**
		 * Nothing available to this repository says what a 1 in those three means, so it is doubt.
		 * The whole region reads unknown rather than this file inventing a reading of it.
		 */
		assertEquals("unknown", DiaryReader.completedTierFrom(new int[] {1, 0, 0, 0}, KARAMJA));
		/** Under finished tiers too: that 1 might be the easy tier the run needs, or might not. */
		assertEquals("unknown", DiaryReader.completedTierFrom(new int[] {1, 2, 2, 0}, KARAMJA));
	}

	/** The reader itself, so its call site cannot go back to the cumulative rule unnoticed. */
	@Test
	public void theReaderReportsCompletionAsARunFromEasy() throws Exception
	{
		java.util.Map<Integer, Integer> varbits = new java.util.HashMap<>();
		/* Kourend & Kebos: medium and hard finished, easy not. */
		varbits.put(VarbitIds.DIARY_KOUREND[1], 1);
		varbits.put(VarbitIds.DIARY_KOUREND[2], 1);
		net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
			net.runelite.api.Client.class.getClassLoader(), new Class<?>[] {net.runelite.api.Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("getVarbitValue"))
				{
					return varbits.getOrDefault((Integer) args[0], 0);
				}
				throw new UnsupportedOperationException(method.getName());
			});
		java.lang.reflect.Constructor<DiaryReader> make = DiaryReader.class.getDeclaredConstructor(net.runelite.api.Client.class);
		make.setAccessible(true);
		DiaryReader reader = make.newInstance(client);
		reader.onSessionResumed(0);

		assertEquals("none", reader.readCompleted(60_000).get("Kourend & Kebos"));
	}
}
