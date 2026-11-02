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
 * The status card's sharing line names what a player might want to check, in Settings' switches,
 * where it once counted fields: <i>Sharing 24 character fields</i> after the one press, which named
 * nothing and left where you died to a tooltip wider than the screen.
 */
public class SharingSummaryTest
{
	@Test
	public void theLineNamesWhatIsOffWhenThatIsShorter()
	{
		HarnessConfig config = config();
		assertEquals("Character sharing off", SharingSummary.line(config));
		for (String key : RecommendedSharing.KEYS)
		{
			config.set(key, true);
		}
		assertEquals("what the one press after Connect leaves", "Sharing all but where you died", SharingSummary.line(config));
		config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
		assertEquals("Sharing all but character name, where you died", SharingSummary.line(config));
		config.set(MapCompanionConfig.KEY_SHARE_NAME, true);
		config.set("shareDeath", true);
		assertEquals("Sharing everything", SharingSummary.line(config));
	}

	@Test
	public void theLineNamesWhatIsOnWhenThatIsShorterAndCountsOtherwise()
	{
		HarnessConfig config = config();
		on(config, SharingFields.PRESENCE);
		assertEquals("Sharing map presence", SharingSummary.line(config));
		on(config, SharingFields.LOOT);
		assertEquals("Sharing map presence, loot and milestones", SharingSummary.line(config));
		config.set("shareDeath", true);
		assertEquals("three of six is neither few on nor few off", "Sharing 3 of 6 choices", SharingSummary.line(config));
	}

	/** A group a player chose part of before the switches is said as part of it, on the line and in the tooltip. */
	@Test
	public void aPartlySharedGroupIsSaidAsPartOfIt()
	{
		HarnessConfig config = config();
		for (String key : RecommendedSharing.KEYS)
		{
			config.set(key, true);
		}
		config.set("shareFarming", false);
		assertEquals("Sharing all but part of tasks and timers, where you died", SharingSummary.line(config));

		HarnessConfig few = config();
		few.set("shareSlayer", true);
		few.set("shareClue", true);
		assertEquals("Sharing part of tasks and timers", SharingSummary.line(few));
		String list = SharingSummary.list(few);
		assertTrue(list, list.contains("Tasks and timers: Slayer task, Clue step"));
		assertTrue(list, list.contains("Map presence: off"));
	}

	/** Pet rewards and collection additions go out only with Notable drops and levels, so they count only with it. */
	@Test
	public void ridersCountOnlyWithWhatTheyRideOn()
	{
		HarnessConfig config = config();
		config.set(MapCompanionConfig.KEY_SHARE_PETS, true);
		config.set(MapCompanionConfig.KEY_SHARE_COLLECTION, true);
		assertEquals("nothing goes out, so nothing is said", "Character sharing off", SharingSummary.line(config));
		on(config, SharingFields.LOOT);
		assertEquals("Sharing loot and milestones", SharingSummary.line(config));
	}

	/**
	 * The tooltip is every switch in Settings' words, one a line, and wraps: a Swing tooltip of plain
	 * text is one line however long, and the field list it held was wider than the screen.
	 */
	@Test
	public void theTooltipGivesEachSwitchALineAndWraps()
	{
		HarnessConfig config = config();
		on(config, SharingFields.PRESENCE);
		String list = SharingSummary.list(config);
		assertTrue(list, list.startsWith("<html><body width='"));
		assertEquals(list, "Character name: off<br>Map presence: on<br>Progress and travel: off<br>Tasks and timers: off"
			+ "<br>Loot and milestones: off<br>Where you died: off", list.replaceAll("^<html><body width='\\d+'>|</body></html>$", ""));
	}

	private static void on(HarnessConfig config, SharingFields.Section section)
	{
		for (SharingFields.Field field : section.fields)
		{
			config.set(field.key, true);
		}
	}

	private static HarnessConfig config()
	{
		return new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL);
	}
}
