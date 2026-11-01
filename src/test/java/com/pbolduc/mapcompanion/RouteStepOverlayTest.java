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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The box over the game says the step the sidebar marks, and only while the route is followed. */
public class RouteStepOverlayTest
{
	private final DestinationRoute route = new DestinationRoute();

	private RelayResponse.Destination received() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		RelayResponse.Destination destination = new Gson().fromJson(reply, RelayResponse.class).directive.destination;
		route.sync(destination);
		return destination;
	}

	/** A route somebody sent is not followed until Follow, and the box waits with it. */
	@Test
	public void saysTheStepBeingWalkedOnlyWhileTheRouteIsFollowed() throws IOException
	{
		RelayResponse.Destination destination = received();
		assertNull("received, not yet followed", RouteStepOverlay.box(route.view(), destination.name));

		route.follow(true);
		RouteStepOverlay.Box box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("Lumbridge", box.title);
		assertEquals("1. Lumbridge Home Teleport", box.step);
		assertEquals("Standard spellbook", box.detail);

		route.advance(new WorldPoint(3222, 3218, 0));
		box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("2. Walk 12 tiles to the bank", box.step);
		assertNull("a walk with no detail has no second line", box.detail);

		route.advance(new WorldPoint(3208, 3220, 2));
		assertNull("gone once arrived", RouteStepOverlay.box(route.view(), destination.name));
	}

	/** A destination's name was typed by somebody in the group; it cannot colour the box. */
	@Test
	public void takesTheTagsOutOfWhatTheGroupTyped() throws IOException
	{
		received();
		route.follow(true);
		assertEquals("Bank", RouteStepOverlay.box(route.view(), "<col=ff0000>Bank</col>").title);
		assertEquals("Route", RouteStepOverlay.box(route.view(), "<col=ff0000></col>").title);
		assertEquals("Route", RouteStepOverlay.box(route.view(), null).title);
	}
}
