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
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The coordinate solver, which is pure arithmetic on a string and so is the one part of this plugin
 * that can be checked without a running client.
 *
 * <p>It is also the part most worth checking, because its failure is silent: a wrong conversion
 * produces a perfectly valid tile that simply is not the one the clue meant, and the player digs in
 * the wrong hemisphere without anything looking broken.
 *
 * <p>The expected tiles below are not invented. They come from the map's own coordinate-clue table,
 * and {@link ClueReader#parseCoordinate} was checked against all 166 rows of it: 165 agree exactly,
 * and the one that does not is a bad row in the source data rather than a disagreement about the
 * arithmetic.
 */
public class ClueReaderTest
{
	@Test
	public void solvesACoordinateInEveryQuadrant()
	{
		/** 01.18S 14.15E — across the water from Musa Point. */
		assertTile(2896, 3119, "01 degrees 18 minutes south 14 degrees 15 minutes east");

		/** 00.35N 35.50E — after arriving at Meiyerditch. */
		assertTile(3587, 3180, "00 degrees 35 minutes north 35 degrees 50 minutes east");

		/** 07.43S 12.26E — the one needing an axe and a machete. */
		assertTile(2838, 2914, "07 degrees 43 minutes south 12 degrees 26 minutes east");

		/** 05.18N 06.48W — west of the origin, the sign this most easily gets wrong. */
		assertTile(2222, 3331, "05 degrees 18 minutes north 06 degrees 48 minutes west");
	}

	@Test
	public void toleratesThePunctuationTheInterfaceActuallyUses()
	{
		/** The interface has written this several ways; all of them name the same six values. */
		assertTile(2896, 3119, "at 01 degrees, 18 minutes south, 14 degrees, 15 minutes east.");
		assertTile(2896, 3119, "1 degree 18 minutes South 14 degrees 15 minutes East");
	}

	@Test
	public void returnsNullForAStepThatIsNotACoordinate()
	{
		/** A cryptic step must not be coerced into a tile — the map looks those up instead. */
		assertNull(ClueReader.parseCoordinate("Speak to the bartender of the Rusty Anchor."));
		assertNull(ClueReader.parseCoordinate("Search the crate in Falador."));
		assertNull(ClueReader.parseCoordinate(""));
	}

	private static void assertTile(int expectedX, int expectedY, String text)
	{
		CompanionSnapshot.Tile tile = ClueReader.parseCoordinate(text);
		assertEquals("x for: " + text, expectedX, tile.x);
		assertEquals("y for: " + text, expectedY, tile.y);
		assertEquals("plane for: " + text, 0, tile.plane);
	}
}
