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

import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldMapRouteHereTest
{
	private static final WorldPoint CENTRE = new WorldPoint(3200, 3200, 0);

	/** RuneLite draws a tile's centre at the point it maps the tile to, so the nearest tile is the rounded offset. */
	@Test
	public void thePointerPicksTheTileItIsOver()
	{
		assertEquals(CENTRE, WorldMapRouteHere.tileAt(500, 300, 500, 300, 4f, CENTRE));
		assertEquals(CENTRE, WorldMapRouteHere.tileAt(501, 299, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3201, 3200, 0), WorldMapRouteHere.tileAt(503, 300, 500, 300, 4f, CENTRE));
		/* Truncating keeps this one on the centre tile: west and south of it is where that goes wrong. */
		assertEquals(new WorldPoint(3199, 3200, 0), WorldMapRouteHere.tileAt(497, 300, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3200, 3199, 0), WorldMapRouteHere.tileAt(500, 303, 500, 300, 4f, CENTRE));
		/* Up the screen is north. */
		assertEquals(new WorldPoint(3200, 3210, 0), WorldMapRouteHere.tileAt(500, 260, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3190, 3195, 0), WorldMapRouteHere.tileAt(480, 310, 500, 300, 2f, CENTRE));
	}

	/** The chat box hears this press's refusal once its answer is in, and never the answer before it. */
	@Test
	public void onlyThePressesOwnRefusalIsSaid()
	{
		String source = WorldMapRouteHere.SOURCE;
		RoutePress.Said earlier = new RoutePress.Said(source, false, "An older refusal", null, 1);
		RoutePress.Said planning = new RoutePress.Said(source, true, null, null, 1);
		RoutePress.Said refused = new RoutePress.Said(source, false, "Connect to a map to plan a route.", null, 1);
		RoutePress.Said routed = new RoutePress.Said(source, false, null, "tile:3201,3200,0", 1);

		assertFalse("the answer from before the press", WorldMapRouteHere.answered(earlier, earlier));
		assertNull(WorldMapRouteHere.refusal(earlier, earlier));
		assertFalse("still planning", WorldMapRouteHere.answered(earlier, planning));
		assertNull(WorldMapRouteHere.refusal(earlier, planning));
		assertEquals("Connect to a map to plan a route.", WorldMapRouteHere.refusal(earlier, refused));
		assertTrue("a route came back", WorldMapRouteHere.answered(null, routed));
		assertNull(WorldMapRouteHere.refusal(null, routed));
		assertNull("another button pressed since", WorldMapRouteHere.refusal(earlier, null));
	}
}
