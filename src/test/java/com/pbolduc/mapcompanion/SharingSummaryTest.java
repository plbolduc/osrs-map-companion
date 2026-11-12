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
	/**
	 * The press after Connect left where you died off, so its line was "Sharing all but where you
	 * died". It ticks everything now, so a new player reads "Sharing everything"; one who pressed
	 * before reads what they always have, the one field that is off by its own name. The name was a
	 * switch on this line; it has none now, and the tooltip says how the group sees the player.
	 */
	@Test
	public void theLineNamesWhatIsOffWhenThatIsShorter()
	{
		HarnessConfig config = config();
		assertEquals("Character sharing off", line(config));
		for (String key : RecommendedSharing.KEYS)
		{
			config.set(key, true);
		}
		assertEquals("what the one press after Connect leaves", "Sharing everything", line(config));
		assertTrue(list(config), list(config).endsWith("<br>Shown as: your character name</body></html>"));
		config.set("shareDeath", false);
		assertEquals("what that press left before it ticked the death", "Sharing all but where you died", line(config));
		config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
		assertEquals("the name is no switch", "Sharing all but where you died", line(config));
		assertTrue(list(config), list(config).endsWith("<br>Shown as: Member</body></html>"));
		on(config, SharingFields.LOOT, false);
		assertEquals("Sharing all but where you died, loot and milestones", line(config));
	}

	/**
	 * Rewritten for three switches where there were six: the one or two that are on are named while
	 * no more is on than off, a switch missing fields is part of itself, and the count is left for
	 * the profile where every switch is partly on.
	 */
	@Test
	public void theLineNamesWhatIsOnWhenThatIsShorterAndCountsOtherwise()
	{
		HarnessConfig config = config();
		on(config, SharingFields.PRESENCE, true);
		assertEquals("the death is under the same switch, and off", "Sharing part of where you are", line(config));
		config.set("shareDeath", true);
		assertEquals("Sharing where you are", line(config));
		on(config, SharingFields.LOOT, true);
		assertEquals("Sharing all but your account", line(config));
		on(config, SharingFields.PRESENCE, false);
		config.set("shareDeath", false);
		assertEquals("Sharing loot and milestones", line(config));
		config.set("sharePosition", true);
		config.set("shareSkills", true);
		config.set(MapCompanionConfig.KEY_SHARE_PETS, false);
		assertEquals("every switch partly on is neither few on nor few off", "Sharing 0 of 3 choices, 3 in part", line(config));
	}

	/** A switch a player chose part of is said as part of it, or by the one field that is off, on the line and in the tooltip. */
	@Test
	public void aPartlySharedGroupIsSaidAsPartOfIt()
	{
		HarnessConfig config = config();
		for (String key : RecommendedSharing.KEYS)
		{
			config.set(key, true);
		}
		config.set("shareFarming", false);
		assertEquals("Sharing all but farming patches", line(config));
		assertTrue(list(config), list(config).contains("Your account: all but Farming patches<br>"));
		config.set("shareClue", false);
		assertEquals("Sharing all but part of your account", line(config));

		HarnessConfig few = config();
		few.set("shareSlayer", true);
		few.set("shareClue", true);
		assertEquals("Sharing part of your account", line(few));
		String list = list(few);
		assertTrue(list, list.contains("Your account: Slayer task, Clue step"));
		assertTrue(list, list.contains("Where you are: off"));
	}

	/** Pet rewards and collection additions go out only with Notable drops and levels, so they count only with it. */
	@Test
	public void ridersCountOnlyWithWhatTheyRideOn()
	{
		HarnessConfig config = config();
		config.set(MapCompanionConfig.KEY_SHARE_PETS, true);
		config.set(MapCompanionConfig.KEY_SHARE_COLLECTION, true);
		assertEquals("nothing goes out, so nothing is said", "Character sharing off", line(config));
		on(config, SharingFields.LOOT, true);
		assertEquals("Sharing loot and milestones", line(config));
	}

	/**
	 * The tooltip is every switch in Settings' words, one a line, and wraps: a Swing tooltip of plain
	 * text is one line however long, and the field list it held was wider than the screen.
	 */
	@Test
	public void theTooltipGivesEachSwitchALineAndWraps()
	{
		HarnessConfig config = config();
		on(config, SharingFields.PRESENCE, true);
		String list = list(config);
		assertTrue(list, list.startsWith("<html><body width='"));
		/* Six lines, one a switch, before; three now, a partly shared one in the fewer words, and the name's. */
		assertEquals(list, "Where you are: all but Where you died<br>Your account: off<br>Loot and milestones: off<br>Shown as: Member",
			list.replaceAll("^<html><body width='\\d+'>|</body></html>$", ""));
	}

	private static void on(HarnessConfig config, SharingFields.Section section, boolean on)
	{
		for (SharingFields.Field field : section.fields)
		{
			config.set(field.key, on);
		}
	}

	/* The summary reads the fields RuneLite does not declare from the store the config writes them to. */
	private static String line(HarnessConfig config)
	{
		return SharingSummary.line(config, config.store());
	}

	private static String list(HarnessConfig config)
	{
		return SharingSummary.list(config, config.store());
	}

	private static HarnessConfig config()
	{
		return new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL);
	}
}
