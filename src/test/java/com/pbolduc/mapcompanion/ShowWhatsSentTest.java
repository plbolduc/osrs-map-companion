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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Show what's sent prints the real snapshot, and the words around it say what is true now: nothing
 * about how often something goes out while nothing does, the two keys in a sentence each with their
 * headers in brackets, and what "Me" in the snapshot means for what the group sees.
 */
public class ShowWhatsSentTest
{
	private static final Gson GSON = new Gson();
	private static final String SNAPSHOT = "{\n  \"v\": 1,\n  \"member\": {\n    \"id\": \"aabd67b5\",\n    \"nick\": \"Me\"\n  }\n}";

	@Test
	public void loggedOutItSaysNothingGoesOutAndPrintsNoSnapshot()
	{
		String text = SettingsPage.previewText(null, true, true, "osrs-map.com", null, true, false, GSON);
		assertEquals("Nothing goes out while you're logged out. Log in to see what would.", text);
	}

	@Test
	public void connectedItSaysWhereAndHowOftenThenTheKeysThenTheSnapshot()
	{
		String text = SettingsPage.previewText(SNAPSHOT, true, true, "osrs-map.com", null, true, false, GSON);
		assertTrue(text, text.startsWith("This goes to osrs-map.com every couple of seconds while you're logged in. "
			+ "Your group sees it.\n\n"));
		assertTrue(text, text.contains("your member key (X-Companion-Member-Secret), which proves you're this member"));
		assertTrue(text, text.contains("this RuneLite's own key (X-Companion-Installation)"));
		assertTrue(text, text.contains("Leave group deletes the first and replaces the second."));
		assertTrue("the snapshot itself, as built", text.contains(SNAPSHOT));
		assertTrue(text, text.endsWith("\"Me\" means you set no name of your own: your group sees Member."));
	}

	@Test
	public void beforeAGroupOrAnApprovedMapItSaysWhatConnectWouldStart()
	{
		String none = SettingsPage.previewText(SNAPSHOT, false, false, "osrs-map.com", null, false, false, GSON);
		assertTrue(none, none.startsWith("You're not in a group, so nothing goes out. Connect would start sending this to osrs-map.com:"));
		assertFalse(none, none.contains("every couple of seconds"));
		assertTrue("no member key is held yet", none.contains("A key goes with it and is never shown here: this RuneLite's own key"));
		assertFalse("there is no group to leave yet", none.contains("Leave group"));

		String other = SettingsPage.previewText(SNAPSHOT, true, false, "maps.example.org", null, true, false, GSON);
		assertTrue(other, other.startsWith("You're not connected to maps.example.org, so nothing goes out. Connect would start sending this:"));
	}

	/** "Me" is the map's word for no name; with the character name shared, that is what the group sees instead. */
	@Test
	public void meIsExplainedAsWhatTheGroupSees()
	{
		String named = SettingsPage.previewText(SNAPSHOT, true, true, "osrs-map.com", null, true, true, GSON);
		assertTrue(named, named.endsWith("your group sees your character name."));
		String scout = SettingsPage.previewText(SNAPSHOT.replace("\"Me\"", "\"Scout\""), true, true, "osrs-map.com", null,
			true, false, GSON);
		assertFalse("a name of your own needs no line", scout.contains("means you set no name"));
	}

	@Test
	public void theRuneLiteCodeIsNamedByItsHeaderAndShownOnlyByItsEnds()
	{
		String text = SettingsPage.previewText(SNAPSHOT, true, true, "osrs-map.com", "ABC234-" + SettingsPage.masked("K7PQ2M3X"),
			true, false, GSON);
		assertTrue(text, text.contains("Your RuneLite code goes with it too (X-Companion-Suffix), ABC234-K7••••3X, until"));
		assertFalse(text.contains("K7PQ2M3X"));
	}
}
