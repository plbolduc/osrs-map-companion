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

import net.runelite.api.Skill;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A level the player just gained, told apart from a level they have had for a year.
 *
 * <p>The client reports every skill's level on login, and it reports them again after a hop.
 * Announcing those would tell a group that somebody levelled twenty-three skills at once the
 * moment they signed in — which is the same class of mistake as the farming observer's login
 * window, arriving through a different door.
 */
public class XpTrackerLevelTest
{
	@Test
	public void theFirstSightingOfASkillIsABaseline()
	{
		XpTracker tracker = new XpTracker();
		assertFalse(tracker.observeLevel(Skill.MINING, 70));
	}

	@Test
	public void anIncreaseAfterThatIsALevelUp()
	{
		XpTracker tracker = new XpTracker();
		tracker.observeLevel(Skill.MINING, 70);
		assertTrue(tracker.observeLevel(Skill.MINING, 71));
	}

	@Test
	public void anUnchangedLevelIsNotOne()
	{
		/** StatChanged fires on experience too, so most of these carry a level that has not moved. */
		XpTracker tracker = new XpTracker();
		tracker.observeLevel(Skill.MINING, 70);
		assertFalse(tracker.observeLevel(Skill.MINING, 70));
	}

	@Test
	public void aLevelGoingDownIsNotAnAchievement()
	{
		XpTracker tracker = new XpTracker();
		tracker.observeLevel(Skill.MINING, 70);
		assertFalse(tracker.observeLevel(Skill.MINING, 60));
		/** And the lower one becomes the baseline, so climbing back is a level-up again. */
		assertTrue(tracker.observeLevel(Skill.MINING, 61));
	}

	@Test
	public void aResetMakesTheNextSightingABaselineAgain()
	{
		/**
		 * Which is what a logout, a hop or a character change does. The alternative is announcing
		 * the next account's existing levels as this session's achievements.
		 */
		XpTracker tracker = new XpTracker();
		tracker.observeLevel(Skill.MINING, 70);
		tracker.reset();
		assertFalse(tracker.observeLevel(Skill.MINING, 99));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void theSentinelIsNotASkill()
	{
		/** Same deprecated OVERALL the snapshot builder skips; it is a total, not a stat. */
		XpTracker tracker = new XpTracker();
		assertFalse(tracker.observeLevel(Skill.OVERALL, 1500));
		assertFalse(tracker.observeLevel(Skill.OVERALL, 1600));
	}
}
