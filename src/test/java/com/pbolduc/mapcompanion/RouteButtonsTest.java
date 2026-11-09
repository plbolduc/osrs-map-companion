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
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		assertEquals("Hard clue", NowCards.clueTitle(coordinate.tier));
		assertEquals("Coordinate · dig at 2896, 3119", NowCards.clueDetail(coordinate));

		CompanionSnapshot.Clue picture = new CompanionSnapshot.Clue();
		picture.tier = "beginner";
		picture.mapTile = new CompanionSnapshot.Tile(3166, 3361, 0);
		assertSame(picture.mapTile, ClueReader.routeTile(picture));

		/*
		 * A step that is words names no tile. The map's route door now places a clue from its words
		 * (osrs-stars-map's companion-clue-route), so such a step routes by its words, and the card no
		 * longer sends the player to the website for it.
		 */
		CompanionSnapshot.Clue words = new CompanionSnapshot.Clue();
		words.tier = "medium";
		words.text = "Speak to the bartender of the Rusty Anchor.";
		assertNull(ClueReader.routeTile(words));
		assertTrue(ClueReader.routesByClue(words));
		assertEquals("Speak to the bartender of the Rusty Anchor.", NowCards.clueDetail(words));
		assertFalse("a step with a tile goes as that tile", ClueReader.routesByClue(coordinate));
		assertFalse(ClueReader.routesByClue(picture));
		assertNull(ClueReader.routeTile(null));
		assertFalse(ClueReader.routesByClue(null));
	}

	/**
	 * A map above beginner has no words and no tile, but its scroll is the picture: RuneLite's clue
	 * plugin and the map's own table both know the place by the item id. So it routes as the clue,
	 * as a worded step does, before and after the scroll is opened, and the card no longer asks the
	 * player to read a scroll whose picture they are looking at.
	 */
	@Test
	public void aMapAboveBeginnerRoutesByItsScroll()
	{
		CompanionSnapshot.Clue map = new CompanionSnapshot.Clue();
		map.tier = "hard";
		map.itemId = ItemID.TRAIL_CLUE_HARD_MAP001;
		assertNull(ClueReader.routeTile(map));
		assertTrue(ClueReader.mapScroll(map));
		assertTrue(ClueReader.routesByClue(map));
		assertEquals("Map · Route finds where it shows", NowCards.clueDetail(map));

		CompanionSnapshot.Clue easy = new CompanionSnapshot.Clue();
		easy.tier = "easy";
		easy.itemId = ItemID.TRAIL_CLUE_EASY_MAP001;
		assertTrue("an easy map is a scroll of its own too", ClueReader.routesByClue(easy));

		/* Every beginner picture is one item, so only its opened window says which: no Route until then. */
		CompanionSnapshot.Clue beginner = new CompanionSnapshot.Clue();
		beginner.tier = "beginner";
		beginner.itemId = ItemID.TRAIL_CLUE_BEGINNER;
		assertFalse(ClueReader.mapScroll(beginner));
		assertFalse(ClueReader.routesByClue(beginner));
		assertEquals("Read the scroll to see its step", NowCards.clueDetail(beginner));

		/* A scroll that is not a map still has to be read for its words. */
		CompanionSnapshot.Clue sextant = new CompanionSnapshot.Clue();
		sextant.tier = "medium";
		sextant.itemId = ItemID.TRAIL_CLUE_MEDIUM_SEXTANT001;
		assertFalse(ClueReader.routesByClue(sextant));
		assertEquals("Read the scroll to see its step", NowCards.clueDetail(sextant));
	}

	@Test
	public void aCardSaysWhatTheClueIsBeforeItIsRead()
	{
		CompanionSnapshot.Clue unread = new CompanionSnapshot.Clue();
		unread.tier = "elite";
		assertEquals("Elite clue", NowCards.clueTitle(unread.tier));
		assertEquals("Read the scroll to see its step", NowCards.clueDetail(unread));
		assertEquals("Clue scroll", NowCards.clueTitle(null));
	}

	/** A clue or a death the player does not share leaves in a Route press, so the README says so. */
	@Test
	public void theReadmeSaysWhatARoutePressSends() throws Exception
	{
		String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8)
			.replaceAll("\\s+", " ");
		/* Quest Helper's step has its Route on Home too, and leaves as a tile the same way. */
		assertTrue(readme.contains("**Route**, beside your clue, your last death, your Quest Helper step, a star or a meetup, sends the tile you are standing on and the tile it goes to"));
		assertTrue(readme.contains("That includes your clue's tile and where you died when you do not share them"));
		/* A map's Route sends its scroll as a worded step's sends its words, so the README names both. */
		assertTrue(readme.contains("For a clue step that is words, or a map above beginner, Route sends the clue instead"));
		/* Home is the tab that was Overview; the README follows the sidebar's own name for it. */
		assertTrue(readme.contains("**Search**, at the top of Home once you are connected, sends what you type to the map"));
	}
}
