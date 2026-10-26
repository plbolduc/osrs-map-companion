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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** Widget events and snapshot reads can arrive in either order; text must belong to that scroll. */
public class ClueObservationTest
{
	private final ItemReadingsTestClient game = new ItemReadingsTestClient();
	private final ClueReader reader = new ClueReader(game.client);

	@Test
	public void openingBeforeTheFirstSnapshotKeepsTheTextAndCoordinate()
	{
		game.define(1, "Clue scroll (hard)", false);
		game.carry(1);
		reader.onClueWidget(ItemReadingsTestClient.widget(
								"<col=ff0000>01 degrees 18 minutes south<br>14 degrees 15 minutes east</col>"));

		CompanionSnapshot.Clue clue = reader.read();
		assertEquals("01 degrees 18 minutes south 14 degrees 15 minutes east", clue.text);
		assertNotNull(clue.coordTile);
		assertEquals(2896, clue.coordTile.x);
		assertEquals(3119, clue.coordTile.y);
	}

	@Test
	public void openingAReplacementBeforeTheNextSnapshotKeepsItsOwnText()
	{
		game.define(1, "Clue scroll (easy)", false);
		game.define(2, "Clue scroll (medium)", false);
		game.carry(1);
		reader.onClueWidget(ItemReadingsTestClient.widget("Old step"));
		assertEquals("Old step", reader.read().text);

		game.carry(2);
		reader.onClueWidget(ItemReadingsTestClient.widget("New step"));
		assertEquals(2, reader.read().itemId.intValue());
		assertEquals("New step", reader.read().text);
	}

	@Test
	public void twoTiersCarriedFileTheTextUnderTheScrollThatWasRead()
	{
		game.define(1, "Clue scroll (easy)", false);
		game.define(2, "Clue scroll (hard)", false);
		game.carry(1, 2);
		reader.onScrollRead(2);
		reader.onClueWidget(ItemReadingsTestClient.widget("Hard step"));
		CompanionSnapshot.Clue clue = reader.read();
		assertEquals("hard", clue.tier);
		assertEquals(2, clue.itemId.intValue());
		assertEquals("Hard step", clue.text);

		/** A shuffle that puts the other scroll first neither takes the text nor wipes it. */
		game.carry(2, 1);
		assertEquals("Hard step", reader.read().text);
		game.carry(1, 2);
		assertEquals(2, reader.read().itemId.intValue());
		assertEquals("Hard step", reader.read().text);
	}

		@Test
	public void aBeginnerPictureReadBesideAnotherTierIsFiledUnderTheBeginner()
	{
		game.define(1, "Clue scroll (easy)", false);
		game.define(2, "Clue scroll (beginner)", false);
		game.carry(1, 2);
		reader.read();
		reader.onScrollRead(2);
		reader.onMapWidget(net.runelite.api.gameval.InterfaceID.TRAIL_MAP01);
		CompanionSnapshot.Clue clue = reader.read();
		assertEquals("beginner", clue.tier);
		assertEquals(3167, clue.mapTile.x);
	}

		@Test
	public void aReplacementThatHasNotBeenOpenedCannotBorrowTheOldText()
	{
		game.define(1, "Clue scroll (easy)", false);
		game.define(2, "Clue scroll (medium)", false);
		game.carry(1);
		reader.onClueWidget(ItemReadingsTestClient.widget("Old step"));
		game.carry(2);
		assertNull(reader.read().text);
	}

	@Test
	public void anInterfaceWithoutACarriedScrollDoesNotSeedTheNextClue()
	{
		reader.onClueWidget(ItemReadingsTestClient.widget("Unowned step"));
		game.define(1, "Clue scroll (easy)", false);
		game.carry(1);
		assertNull(reader.read().text);
	}
	@Test
	public void beginnerPicturesUseTheOpenedWindowAndCannotSurviveAnotherStep()
	{
		game.define(1, "Clue scroll (beginner)", false);
		game.carry(1);
		assertNull(reader.read().mapTile);
		reader.onMapWidget(net.runelite.api.gameval.InterfaceID.TRAIL_MAP01);
		assertEquals(3167, reader.read().mapTile.x);
		reader.onMapWidget(net.runelite.api.gameval.InterfaceID.TRAIL_MAP02);
		assertEquals(3290, reader.read().mapTile.x);
		reader.onClueWidget(ItemReadingsTestClient.widget("A text step"));
		assertNull(reader.read().mapTile);
		reader.reset();
		assertNull(reader.read().mapTile);
	}

	@Test
	public void anInventoryGapInvalidatesAReplacementWithTheSameBeginnerItemId()
	{
		game.define(1, "Clue scroll (beginner)", false);
		game.carry(1);
		reader.onMapWidget(net.runelite.api.gameval.InterfaceID.TRAIL_MAP01);
		assertNotNull(reader.read().mapTile);
		game.carry();
		assertNull(reader.read());
		game.carry(1);
		assertNull(reader.read().mapTile);
	}
}
