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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Telling a change of character apart from an ordinary login.
 *
 * <p>Both arrive as the same event, and acting on the wrong one is silently destructive: the
 * plugin's answer to "where did I die" is meant to survive logging back in — that is the moment
 * somebody wants it — and a rule that treated every profile change as a new player would throw it
 * away on every relog, with nothing to see but a marker that stopped appearing.
 */
public class CharacterSwitchTest
{
	@Test
	public void theFirstLoginOfASessionIsNotASwitch()
	{
		assertFalse(MapCompanionPlugin.isCharacterSwitch(null, "profile-a"));
	}

	@Test
	public void loggingOutIsNotASwitch()
	{
		assertFalse(MapCompanionPlugin.isCharacterSwitch("profile-a", null));
	}

	@Test
	public void theSameCharacterComingBackIsNotASwitch()
	{
		assertFalse(MapCompanionPlugin.isCharacterSwitch("profile-a", "profile-a"));
	}

	@Test
	public void aDifferentCharacterIsOne()
	{
		assertTrue(MapCompanionPlugin.isCharacterSwitch("profile-a", "profile-b"));
	}

	@Test
	public void whatThePluginWatchedBeatsWhatTheEventClaims()
	{
		/** Once a profile has played while this plugin was running, that is who was playing. */
		assertEquals("profile-a",
					 MapCompanionPlugin.rememberedProfile("profile-a", "profile-b"));
		assertEquals("profile-a", MapCompanionPlugin.rememberedProfile("profile-a", null));
		/** With nothing watched, the event is the only thing that names the outgoing character. */
		assertEquals("profile-b", MapCompanionPlugin.rememberedProfile(null, "profile-b"));
		assertNull(MapCompanionPlugin.rememberedProfile(null, null));
	}

	@Test
	public void aPluginEnabledMidSessionStillNoticesTheNextCharacter()
	{
		/**
		 * The sequence that was silently wrong. Turning the plugin on while already logged in as A
		 * means it never saw the event that named A, so logging out and back in as B arrives as
		 * (A → null) and then (null → B): two events, neither of which names two real profiles.
		 * Without the seed, B inherits A's outbox, experience window and death marker.
		 */
		String remembered = null;

		/* (A → null): not a switch, but it is where A is learned. */
		remembered = MapCompanionPlugin.rememberedProfile(remembered, "profile-a");
		assertFalse(MapCompanionPlugin.isCharacterSwitch(remembered, null));

		/* (null → B): now there is something to compare against, and it differs. */
		remembered = MapCompanionPlugin.rememberedProfile(remembered, null);
		assertTrue(MapCompanionPlugin.isCharacterSwitch(remembered, "profile-b"));
	}

	@Test
	public void theSameCharacterComingBackIsStillNotASwitchWithTheSeed()
	{
		/** The other half: the seed must not turn an ordinary relog into a change of character. */
		String remembered = MapCompanionPlugin.rememberedProfile(null, "profile-a");
		assertFalse(MapCompanionPlugin.isCharacterSwitch(remembered, null));
		remembered = MapCompanionPlugin.rememberedProfile(remembered, null);
		assertFalse(MapCompanionPlugin.isCharacterSwitch(remembered, "profile-a"));
	}
}
