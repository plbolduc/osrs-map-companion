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
import static org.junit.Assert.assertNull;
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
