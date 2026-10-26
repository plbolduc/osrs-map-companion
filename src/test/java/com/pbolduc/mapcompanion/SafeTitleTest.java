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
 * Meetup titles, which this plugin does not write.
 *
 * <p>They arrive over the network from whoever holds the pairing code and end up in a RuneLite
 * notification — a tray balloon on some systems. So the text is flattened and bounded before it goes
 * anywhere: no control characters, no newlines, and a length a notification can actually show. The
 * sidebar escapes the same strings for the same reason.
 */
public class SafeTitleTest
{
	@Test
	public void keepsAnOrdinaryTitleAsItIs()
	{
		assertEquals("Star at Rimmington", MapCompanionPlugin.safeTitle("Star at Rimmington"));
	}

	@Test
	public void flattensWhitespaceAndControlCharacters()
	{
		assertEquals("Star at Rimmington", MapCompanionPlugin.safeTitle("Star\tat\nRimmington"));
		assertEquals("Star at Rimmington", MapCompanionPlugin.safeTitle("  Star   at  Rimmington  "));
		assertEquals("hello", MapCompanionPlugin.safeTitle("hello "));
	}

	@Test
	public void boundsSomethingAbsurd()
	{
		StringBuilder huge = new StringBuilder();
		for (int i = 0; i < 500; i++)
		{
			huge.append("nonsense ");
		}
		String safe = MapCompanionPlugin.safeTitle(huge.toString());
		assertTrue("was " + safe.length() + " long", safe.length() <= 60);
		assertTrue(safe.endsWith("…"));
	}

	@Test
	public void hasSomethingToSayAboutNothing()
	{
		assertEquals("somewhere", MapCompanionPlugin.safeTitle(null));
		assertEquals("somewhere", MapCompanionPlugin.safeTitle(""));
		assertEquals("somewhere", MapCompanionPlugin.safeTitle("   "));
	}

	/** The destination's name is drawn in the game scene, where RuneLite would read a colour tag. */
	@Test
	public void aDestinationNameCannotColourOrCrowdTheScene()
	{
		RelayResponse.Destination destination = new RelayResponse.Destination();
		destination.name = "<col=ff0000>Official notice</col>";
		destination.world = 302;
		assertEquals("Official notice (W302)", DestinationSceneOverlay.label(destination));
		destination.name = "a".repeat(200);
		destination.world = null;
		assertEquals(40, DestinationSceneOverlay.label(destination).length());
		destination.name = null;
		assertEquals("Destination", DestinationSceneOverlay.label(destination));
	}

	/** The preview window shows a suffix by its ends only: with the code it admits to a group. */
	@Test
	public void thePreviewShowsOnlyTheEndsOfASuffix()
	{
		assertEquals("K7••••3X", MapCompanionPanel.masked("K7PQ2M3X"));
		assertEquals("••••", MapCompanionPanel.masked("K7P"));
	}
}
