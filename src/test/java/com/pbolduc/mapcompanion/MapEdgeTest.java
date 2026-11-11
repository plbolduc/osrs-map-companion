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
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Which way a held pin points, as plain numbers: a view four hundred pixels by three hundred, a
 * place so many pixels from its middle. Angles are the screen's, clockwise from east, so south is
 * a quarter turn and north three.
 */
public class MapEdgeTest
{
	private static final double W = 200, H = 150;

	private static double degrees(double dx, double dy)
	{
		return Math.toDegrees(MapEdge.way(dx, dy, W, H));
	}

	@Test
	public void aPlaceOffOneSideIsStraightOutThroughThatEdge()
	{
		assertEquals("west, however far up the edge the head is held", 180, degrees(-900, -120), 1e-9);
		assertEquals(180, degrees(-900, 140), 1e-9);
		assertEquals("east", 0, degrees(201, 0), 1e-9);
		assertEquals("north is up the screen", -90, degrees(30, -151), 1e-9);
		assertEquals("south", 90, degrees(-199, 4000), 1e-9);
	}

	@Test
	public void aPlacePastACornerIsTheWayFromTheCornerNotFromTheMiddle()
	{
		assertEquals("as far past the corner down as across", 135, degrees(-300, 250), 1e-9);
		assertEquals("far below and a little left is nearly straight down", 90, degrees(-210, 4150), 1);
	}

	@Test
	public void aPlaceUnderTheHeadHasNoWay()
	{
		assertTrue(Double.isNaN(MapEdge.way(0, 0, W, H)));
		assertTrue("RuneLite holds a pin a while after its place is back in view; the head sits on it then",
			Double.isNaN(MapEdge.way(-200, 150, W, H)));
	}
}
