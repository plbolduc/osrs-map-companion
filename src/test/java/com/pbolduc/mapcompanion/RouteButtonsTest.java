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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What each Route button beside a clue, a death, a star or a meetup asks the map about.
 *
 * <p>The request itself is {@link RoutePressTest}'s; these are the targets the buttons hand it and
 * the words the cards say, which are pure and so pinned here without a client.
 */
public class RouteButtonsTest
{
	@Test
	public void aClueRoutesToTheTileItsStepNames()
	{
		CompanionSnapshot.Clue coordinate = new CompanionSnapshot.Clue();
		coordinate.tier = "hard";
		coordinate.text = "01 degrees 18 minutes south 14 degrees 15 minutes east";
		coordinate.coordTile = ClueReader.parseCoordinate(coordinate.text);
		assertSame(coordinate.coordTile, ClueReader.routeTile(coordinate));
		assertEquals("Hard clue", MapCompanionPanel.clueTitle(coordinate.tier));
		assertEquals("Coordinate · dig at 2896, 3119", MapCompanionPanel.clueDetail(coordinate));

		CompanionSnapshot.Clue picture = new CompanionSnapshot.Clue();
		picture.tier = "beginner";
		picture.mapTile = new CompanionSnapshot.Tile(3166, 3361, 0);
		assertSame(picture.mapTile, ClueReader.routeTile(picture));

		CompanionSnapshot.Clue words = new CompanionSnapshot.Clue();
		words.tier = "medium";
		words.text = "Speak to the bartender of the Rusty Anchor.";
		assertNull("a step that is words names no tile, so it offers no Route", ClueReader.routeTile(words));
		assertTrue(MapCompanionPanel.clueDetail(words).startsWith("Speak to the bartender"));
		assertNull(ClueReader.routeTile(null));
	}

	@Test
	public void aCardSaysWhatTheClueIsBeforeItIsRead()
	{
		CompanionSnapshot.Clue unread = new CompanionSnapshot.Clue();
		unread.tier = "elite";
		assertEquals("Elite clue", MapCompanionPanel.clueTitle(unread.tier));
		assertEquals("Read the scroll to see its step", MapCompanionPanel.clueDetail(unread));
		assertEquals("Clue scroll", MapCompanionPanel.clueTitle(null));
	}

	/** A clue or a death the player does not share leaves in a Route press, so the README says so. */
	@Test
	public void theReadmeSaysWhatARoutePressSends() throws Exception
	{
		String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8)
			.replaceAll("\\s+", " ");
		assertTrue(readme.contains("**Route**, beside your clue, your last death, a star or a meetup, sends the tile you are standing on and the tile it goes to"));
		assertTrue(readme.contains("That includes your clue's tile and where you died when you do not share them"));
		/* Home is the tab that was Overview; the README follows the sidebar's own name for it. */
		assertTrue(readme.contains("**Search**, at the top of Home once you are connected, sends what you type to the map"));
	}
}
