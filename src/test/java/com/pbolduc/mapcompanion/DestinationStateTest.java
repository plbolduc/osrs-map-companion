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

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 * Dismiss is local, and a new destination forgets it.
 *
 * <p>The same directive keeps arriving on the stream. Without a fingerprint, dismiss would
 * last one frame. Without clearing it on a different destination, the next place would
 * stay hidden too.
 */
public class DestinationStateTest
{
	private final DestinationState state = new DestinationState(new Gson());

	@Test public void locallyChosenStarSurvivesUnchangedHeartbeatsAndANewMapPinReplacesIt()
	{
		RelayResponse.Directive old = directive(place("Old map pin", 3200, 3200, 302));
		state.accept(old);
		state.selectLocal(place("Rimmington mine", 2975, 3237, 330));
		state.accept(old);
		assertEquals("Rimmington mine", state.getDestination().name);
		assertTrue(state.hasLocalDestination());
		state.dismiss(); state.accept(old); assertNull(state.getDestination());
		state.selectLocal(place("Rimmington mine", 2975, 3237, 330));
		state.accept(directive(place("New map pin", 3201, 3200, 444)));
		assertEquals("New map pin", state.getDestination().name);
		assertFalse(state.hasLocalDestination());
	}

	@Test public void noRemoteDestinationCannotEraseALocalStarAndClearDropsBoth()
	{
		state.selectLocal(place("Rimmington mine", 2975, 3237, 330));
		state.accept(null); state.accept(new RelayResponse.Directive());
		assertNotNull(state.getDestination());
		state.clear(); assertNull(state.getDestination()); assertFalse(state.hasLocalDestination());
	}

	@Test
	public void dismissHidesTheSameDestinationWhenItArrivesAgain()
	{
		state.accept(directive(place("Lumbridge", 3222, 3218, 1)));
		assertEquals("Lumbridge", state.getDestination().name);
		state.dismiss();
		assertNull(state.getDestination());
		state.accept(directive(place("Lumbridge", 3222, 3218, 1)));
		assertNull(state.getDestination());
	}

	@Test
	public void aDifferentDestinationClearsTheDismissal()
	{
		state.accept(directive(place("Lumbridge", 3222, 3218, 1)));
		state.dismiss();
		state.accept(directive(place("Varrock", 3213, 3428, 444)));
		assertNotNull(state.getDestination());
		assertEquals("Varrock", state.getDestination().name);
		assertEquals(Integer.valueOf(444), state.getDestination().world);
	}

	private static RelayResponse.Directive directive(RelayResponse.Destination destination)
	{
		RelayResponse.Directive d = new RelayResponse.Directive();
		d.destination = destination;
		return d;
	}

	private static RelayResponse.Destination place(String name, int x, int y, Integer world)
	{
		RelayResponse.Destination dest = new RelayResponse.Destination();
		dest.name = name;
		dest.x = x;
		dest.y = y;
		dest.plane = 0;
		dest.world = world;
		return dest;
	}
}
