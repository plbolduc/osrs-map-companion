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

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The pictures a pin wears, read back pixel by pixel: no world map draws in a test, and a pin
 * whose point is beside its tile or whose head is off the edge's centre looks right in every
 * other assertion here.
 */
public class MapPinTest
{
	private static final WorldPoint AT = new WorldPoint(3222, 3218, 0);

	private static int alpha(BufferedImage image, int x, int y)
	{
		return image.getRGB(x, y) >>> 24;
	}

	/** The lowest row holding a solid pixel: the shadow under the tip is fainter than any rim. */
	private static int lowestSolid(BufferedImage image)
	{
		for (int y = image.getHeight() - 1; y >= 0; y--)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (alpha(image, x, y) > 200)
				{
					return y;
				}
			}
		}
		return -1;
	}

	@Test
	public void aPinStandsOnItsTile()
	{
		MapPin pin = new MapPin(AT, MapPin.AMBER, "", false, "Destination", null);
		BufferedImage image = pin.getImage();
		assertNotNull("in view the picture is hung by its tip", pin.getImagePoint());
		assertEquals("the tip is under the head's middle", image.getWidth() / 2, pin.getImagePoint().getX());
		int tip = pin.getImagePoint().getY();
		assertTrue("the fill reaches the tip", alpha(image, image.getWidth() / 2, tip - 3) > 200);
		assertTrue("nothing solid is drawn far past it, so the point marks the tile and not the ground above it",
			Math.abs(lowestSolid(image) - tip) <= 2);
	}

	@Test
	public void atTheEdgeTheHeadIsCentredWhereTheDirectionIsRead()
	{
		MapPin pin = new MapPin(AT, MapPin.AMBER, "1", false, "Step 1", null);
		int tall = pin.getImage().getHeight();
		pin.onEdgeSnap();
		assertNull(pin.getImagePoint());
		assertEquals("the head alone, as wide as it is tall", pin.getImage().getWidth(), pin.getImage().getHeight());
		assertTrue(pin.getImage().getHeight() < tall);
		pin.onEdgeUnsnap();
		assertEquals(tall, pin.getImage().getHeight());
	}

	/** The pin as the map's edge holds it, its place lying {@code degrees} clockwise from east. */
	private static BufferedImage held(MapPin pin, double degrees, String says)
	{
		pin.atEdge(place -> Math.toRadians(degrees), says);
		pin.setCurrentlyEdgeSnapped(true);
		pin.onEdgeSnap();
		return pin.getImage();
	}

	/** The first and last columns holding a solid pixel. */
	private static int[] solidColumns(BufferedImage image)
	{
		int first = -1, last = -1;
		for (int x = 0; x < image.getWidth(); x++)
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				if (alpha(image, x, y) > 200)
				{
					first = first < 0 ? x : first;
					last = x;
					break;
				}
			}
		}
		return new int[]{first, last};
	}

	@Test
	public void aHeldHeadPointsTheWayItsPlaceLies()
	{
		MapPin pin = new MapPin(AT, new Color(0x38bdf8), "BR", false, "Big Rob", null);
		BufferedImage west = held(pin, 180, null);
		int middle = west.getWidth() / 2;
		int[] across = solidColumns(west);
		assertTrue("the point reaches further west of the head's middle than the head does east: " + across[0] + ".." + across[1],
			middle - across[0] >= across[1] - middle + 3);
		int[] east = solidColumns(held(pin, 0, null));
		assertEquals("and the other way for a place to the east", middle - across[0], east[1] - middle, 1);
		assertEquals("a friend's head says nothing beside it", MapPin.HEAD, west.getWidth());
	}

	@Test
	public void aHeldHeadFollowsTheMapAsItScrolls()
	{
		double[] way = {Math.PI};
		MapPin pin = new MapPin(AT, MapPin.AMBER, "1", false, "Step 1", null).atEdge(place -> way[0], null);
		pin.setCurrentlyEdgeSnapped(true);
		pin.onEdgeSnap();
		BufferedImage west = pin.getImage();
		assertSame("the same way is the same picture, not one painted each frame", west, pin.getImage());
		way[0] = Math.PI / 2;
		assertNotSame("RuneLite says nothing when the map moves under a held pin, so the pin looks", west, pin.getImage());
		way[0] = Math.PI;
		assertSame(west, pin.getImage());
	}

	@Test
	public void whatYouAskedAboutSaysWhatItIsOnTheSideAwayFromTheEdge()
	{
		MapPin pin = new MapPin(AT, MapPin.AMBER, "1", false, "Step 1", null);
		BufferedImage west = held(pin, 180, "Use Catherby tablet");
		assertTrue("wider by its words", west.getWidth() > MapPin.HEAD + 60);
		assertEquals("and no taller, so the head stays level with its place", MapPin.HEAD, west.getHeight());
		assertTrue("the head is at the edge's end, its point the first thing drawn", solidColumns(west)[0] <= 4);
		BufferedImage east = held(pin, 0, "Use Catherby tablet");
		assertTrue("and at the other end for a place to the east", solidColumns(east)[1] >= east.getWidth() - 5);
		BufferedImage south = held(pin, 90, "Use Catherby tablet");
		assertTrue("over a head pointing down, which stays in its place's column", south.getHeight() > MapPin.HEAD);
		int column = south.getWidth() / 2;
		assertTrue(alpha(south, column, south.getHeight() - 4) > 200);
	}

	@Test
	public void aLabelIsWordsOfABoundedLength()
	{
		MapPin pin = new MapPin(AT, MapPin.AMBER, "", false, "Destination", null);
		int plain = held(pin, 180, "Catherby").getWidth();
		assertEquals("a tag a member typed is not drawn or measured", plain, held(pin, 180, "<col=ff0000>Catherby</col>").getWidth());
		int longest = held(pin, 180, "The house of somebody who typed a very long name for it indeed").getWidth();
		assertTrue("cut to a label, not a banner across the map: " + longest, longest < 260);
	}

	@Test
	public void aPlaceBackUnderTheHeadIsARoundHeadThatSaysNothing()
	{
		MapPin pin = new MapPin(AT, MapPin.AMBER, "1", false, "Step 1", null);
		BufferedImage on = held(pin, Double.NaN, "Use Catherby tablet");
		assertEquals("a wide picture would hold the pin at the edge until its place was most of the way across the map",
			MapPin.HEAD, on.getWidth());
		int[] across = solidColumns(on);
		assertEquals("no point either side", on.getWidth() / 2 - across[0], across[1] - (on.getWidth() - 1) / 2, 1);
	}

	@Test
	public void aQuietFriendFadesWholeRatherThanLayerByLayer()
	{
		BufferedImage quiet = new MapPin(AT, new Color(0x38bdf8), "BR", true, "Big Rob", null).getImage();
		BufferedImage here = new MapPin(AT, new Color(0x38bdf8), "BR", false, "Big Rob", null).getImage();
		int strongest = 0;
		for (int y = 0; y < quiet.getHeight(); y++)
		{
			for (int x = 0; x < quiet.getWidth(); x++)
			{
				strongest = Math.max(strongest, alpha(quiet, x, y));
			}
		}
		assertTrue("the map shows through every part of a quiet pin: " + strongest, strongest < 160);
		assertEquals(255, alpha(here, here.getWidth() / 2, here.getWidth() / 2));
	}

	@Test
	public void aBoatIsAPinThatSaysWhoseItIs()
	{
		MapPin boat = MapPin.boat(AT, () -> "Your boat at Port Sarim");
		assertEquals("Your boat", boat.getName());
		assertEquals("Your boat at Port Sarim", boat.getTooltip());
		BufferedImage image = boat.getImage();
		int middle = image.getRGB(image.getWidth() / 2, image.getWidth() / 2 + 4);
		assertTrue("a light hull on the dark head", (middle & 0xFF) > 200);
	}
}
