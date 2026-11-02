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

import javax.swing.JButton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The words a wait is said in. A refusal used to carry its seconds written in when it arrived, "Try
 * again in 4s." a minute later, or none at all, "Try again in a few seconds." for half a minute.
 */
public class MapWaitTest
{
	@Test
	public void sayHowLongIsLeftRoundedUp()
	{
		assertEquals("25s", MapWait.left(24_001L));
		assertEquals("1s", MapWait.left(1L));
		assertEquals("a wait over is said as its last second", "1s", MapWait.left(0L));
		assertEquals("90s", MapWait.left(90_000L));
		assertEquals("2m", MapWait.left(90_001L));
		assertEquals("5m", MapWait.left(300_000L));
	}

	@Test
	public void aWaitCountsDownAndItsClauseGoesWhenItIsOver()
	{
		assertEquals("The map is busy. Try again in 25s.", MapWait.tryAgain(MapWait.BUSY, 25_000L));
		assertEquals("over, a busy map is said in the past", "The map was busy.", MapWait.tryAgain(MapWait.BUSY, 0L));
		assertEquals("The map asked to wait. Try again in 4s.", MapWait.tryAgain(MapWait.ASKED, 3_200L));
		assertEquals("The map asked to wait.", MapWait.tryAgain(MapWait.ASKED, -5L));
		assertEquals("The map asked to wait. Searching in 3s.", MapWait.searching(3_000L));
		assertTrue(MapWait.waits(MapWait.BUSY));
		assertTrue(MapWait.waits(MapWait.ASKED));
		assertFalse("any other sentence is said as it is", MapWait.waits(RouteClient.NO_WAY));
		assertFalse(MapWait.waits(null));
	}

	/** A press the wait greys says how long in its tooltip; pressable, or with no wait left, its own. */
	@Test
	public void aGreyedPressSaysTheWaitInItsTooltip()
	{
		JButton route = new JButton("Route");
		route.setEnabled(false);
		MapWait.tip(route, "Plan the way here", 25_000L);
		assertEquals("The map asked to wait: 25s", route.getToolTipText());
		MapWait.tip(route, "Plan the way here", 0L);
		assertEquals("Plan the way here", route.getToolTipText());
		route.setEnabled(true);
		MapWait.tip(route, null, 25_000L);
		assertEquals(null, route.getToolTipText());
	}
}
