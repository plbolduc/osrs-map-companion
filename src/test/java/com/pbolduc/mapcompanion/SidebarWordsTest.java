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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the sidebar says about a group, a meetup and a death, pinned down as values.
 *
 * <p>Each was a number a player had to translate: a group by its code, "Meetup in 1560m", "Died 7m
 * ago at 3222, 3218" in red. These hold the translation, including two cases that go wrong without
 * a symptom: a meetup more than a week out, whose weekday would be a guess, and a locale whose own
 * clock pattern carries a character the sidebar's font has no glyph for.
 */
public class SidebarWordsTest
{
	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private static long at(int month, int day, int hour, int minute)
	{
		return LocalDateTime.of(2026, month, day, hour, minute).atZone(LONDON).toInstant().toEpochMilli();
	}

	/** A Wednesday evening. */
	private static final long NOW = at(9, 30, 18, 10);

	private static String when(long startsAtMs, Locale locale)
	{
		return MapCompanionPanel.meetupWhen(startsAtMs, NOW, LONDON, locale, null);
	}

	@Test
	public void aMeetupIsMinutesThenHoursThenADayAndAClock()
	{
		assertEquals("Meetup now", when(NOW - 60_000L, Locale.US));
		assertEquals("Meetup in 45m", when(NOW + 45 * 60_000L, Locale.US));
		assertEquals("Meetup in 3h", when(NOW + 180 * 60_000L, Locale.US));
		assertEquals("Meetup in 3h 20m", when(NOW + 200 * 60_000L, Locale.US));
		assertEquals("Meetup tomorrow 6:42 AM", when(at(10, 1, 6, 42), Locale.US));
		assertEquals("Meetup Sat 9:00 PM", when(at(10, 3, 21, 0), Locale.US));
		/** Past six days a weekday could be either of two weeks, so it is a date. */
		assertEquals("Meetup 9 Oct 9:00 PM", when(at(10, 9, 21, 0), Locale.US));
	}

	@Test
	public void aMeetupLaterTodayIsToday()
	{
		long earlyMorning = at(9, 30, 0, 30);
		assertEquals("Meetup today 11:00 PM",
					 MapCompanionPanel.meetupWhen(at(9, 30, 23, 0), earlyMorning, LONDON, Locale.US, null));
	}

	@Test
	public void theWorldFollowsTheTimeWhenTheMapNamedOne()
	{
		assertEquals("Meetup in 45m · W444",
					 MapCompanionPanel.meetupWhen(NOW + 45 * 60_000L, NOW, LONDON, Locale.US, 444));
		assertEquals("Meetup tomorrow 6:42 AM · W302",
					 MapCompanionPanel.meetupWhen(at(10, 1, 6, 42), NOW, LONDON, Locale.US, 302));
	}

	@Test
	public void theClockCountsTheReadersHoursInCharactersTheFontHas()
	{
		assertEquals("Meetup tomorrow 06:42", when(at(10, 1, 6, 42), Locale.UK));
		assertEquals("Meetup tomorrow 06:42", when(at(10, 1, 6, 42), Locale.FRANCE));
		/**
		 * Java's own English short time is "h:mm a" on a current JDK, and the sidebar's font has
		 * no narrow no-break space: it would draw a box between the time and "AM".
		 */
		String us = when(at(10, 1, 6, 42), Locale.US);
		for (char c : us.toCharArray())
		{
			assertTrue("a character the sidebar's font may not have, in \"" + us + "\"", c < 0x80 || c == '·');
		}
	}

	@Test
	public void theStatusLineNamesTheGroupAndFallsBackToItsCode()
	{
		assertEquals("Sunday raids", MapCompanionPanel.statusGroup("KQX482", "Sunday raids"));
		assertEquals("KQX 482", MapCompanionPanel.statusGroup("KQX482", null));
		assertEquals("KQX 482", MapCompanionPanel.statusGroup("KQX482", "   "));
		/** An adopted legacy group is named after its own code, and says it once. */
		assertEquals("KQX 482", MapCompanionPanel.statusGroup("KQX482", "KQX 482"));
	}

	@Test
	public void aDeathSaysWhenAndWhichFloorWithTheTileBehindAHover()
	{
		assertEquals("You died just now", MapCompanionPanel.deathLine(30_000L, 0));
		assertEquals("You died 7m ago", MapCompanionPanel.deathLine(7 * 60_000L + 5_000L, 0));
		assertEquals("You died 7m ago on floor 1", MapCompanionPanel.deathLine(7 * 60_000L, 1));
		assertEquals("3222, 3218", MapCompanionPanel.deathTile(3222, 3218, 0));
		assertEquals("3222, 3218 (floor 1)", MapCompanionPanel.deathTile(3222, 3218, 1));
	}

	@Test
	public void showOnMapPinsTheTileInTheMapsOwnGrammar()
	{
		/* An explicit ground floor matters too: it must not resolve to a named place upstairs. */
		assertEquals("/map?p=3222,3218,0", MapCompanionPanel.deathMapPath(3222, 3218, 0));
		assertEquals("/map?p=2272,4058,2", MapCompanionPanel.deathMapPath(2272, 4058, 2));
	}
}
