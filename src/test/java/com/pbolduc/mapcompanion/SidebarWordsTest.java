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
import static org.junit.Assert.assertFalse;
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

	/** Joining another group is a row of the groups menu, and closes the field it opened. */
	@Test
	public void theGroupsMenuJoinsAnotherGroupAndClosesItsField()
	{
		assertEquals("Join another group…", MapCompanionPanel.joinMenuWords(false));
		assertEquals("Hide the code field", MapCompanionPanel.joinMenuWords(true));
	}

	/** Renaming is the groups menu's last row, for whoever the map says runs the group. */
	@Test
	public void theGroupsMenuRenamesTheGroup()
	{
		assertEquals("Rename group…", MapCompanionPanel.RENAME);
	}

	/**
	 * And the status card says it too, while the group still has the name Connect gave it: the moment
	 * a new group most wants a name. Once named, or to a member who does not run it, the card is quiet.
	 */
	@Test
	public void theStatusCardOffersRenameOnlyWhileTheRunnersGroupIsUnnamed()
	{
		assertEquals("Our group", MapCompanionPanel.STARTING_NAME);
		assertTrue(MapCompanionPanel.offersRename(true, true, "Our group"));
		assertFalse("named already", MapCompanionPanel.offersRename(true, true, "Sunday raids"));
		assertFalse("does not run it", MapCompanionPanel.offersRename(true, false, "Our group"));
		assertFalse("no group", MapCompanionPanel.offersRename(false, true, "Our group"));
		assertFalse("no name heard yet", MapCompanionPanel.offersRename(true, true, null));
	}

	/**
	 * Before a group the card says one plain thing, in the reader's words rather than this code's.
	 * While connected the first line is the group's name: "Connected" became the dot's tooltip when
	 * the card went from three lines to two, so this asserts the name where it asserted the word.
	 */
	@Test
	public void aFreshInstallIsNotConnectedRatherThanNotPaired()
	{
		assertEquals("Not connected", MapCompanionPanel.statusWords(false, false, Health.IDLE, "Not paired", null));
		assertEquals("Press Connect", MapCompanionPanel.statusWords(true, false, Health.WAITING, "Press Connect to choose osrs-map.com", "Sunday raids"));
		assertEquals("Sunday raids", MapCompanionPanel.statusWords(true, true, Health.LIVE, "Connected to osrs-map.com", "Sunday raids"));
		assertEquals("Logged out", MapCompanionPanel.statusWords(true, true, Health.WAITING, "Logged out", "Sunday raids"));
		/**
		 * The field asked for "your connection code", which only the map's panel could give. The orange
		 * button makes a group now and the field is the other way in, so a friend's invite comes first.
		 */
		assertEquals("Paste an invite or code", MapCompanionPanel.JOIN_PLACEHOLDER);
	}

	/**
	 * One orange button, saying the one thing to press: Connect before a group, which makes one, and
	 * Open the map after, which opens the website as the player. A fresh install no longer needs the
	 * map.
	 */
	@Test
	public void theOrangeButtonConnectsBeforeAGroupAndOpensTheMapAfter()
	{
		assertEquals("Connect", MapCompanionPanel.CONNECT);
		assertEquals("Open the map", MapCompanionPanel.OPEN_MAP);
	}

	/**
	 * Connect asks nothing of the map the plugin was installed for — the install warning was the
	 * question — and a map anywhere else is asked about by name. Only the address decides which.
	 */
	@Test
	public void onlyAMapOtherThanTheInstalledOneIsAskedAbout()
	{
		assertTrue(MapCompanionPanel.isDefaultMap("https://osrs-map.com"));
		assertTrue(MapCompanionPanel.isDefaultMap(Pairing.normalizeServer("https://OSRS-MAP.com:443/")));
		assertFalse(MapCompanionPanel.isDefaultMap("https://osrs-map.com.example.test"));
		assertFalse(MapCompanionPanel.isDefaultMap("http://localhost:3000"));
		assertFalse(MapCompanionPanel.isDefaultMap(null));
	}

	/** The invite says both ways in, and keeps the link last so a chat client links all of it. */
	@Test
	public void anInviteSaysWhereToPasteItAndEndsWithTheLink()
	{
		String link = "https://osrs-map.com/map?join=ABC234&i=K7PQ2M3XABCDEFGH&task=companion";
		String message = MapCompanionPanel.inviteMessage(link);
		assertTrue(message, message.contains("OSRS Map Companion"));
		assertTrue(message, message.endsWith(" " + link));
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

	/** The death card's button opens the website and says so; "Show on map" also meant the game's map. */
	@Test
	public void showOnMapPinsTheTileInTheMapsOwnGrammar()
	{
		assertEquals("Open website", MapCompanionPanel.OPEN_WEBSITE);
		/* An explicit ground floor matters too: it must not resolve to a named place upstairs. */
		assertEquals("/map?p=3222,3218,0", MapCompanionPanel.deathMapPath(3222, 3218, 0));
		assertEquals("/map?p=2272,4058,2", MapCompanionPanel.deathMapPath(2272, 4058, 2));
	}
}
