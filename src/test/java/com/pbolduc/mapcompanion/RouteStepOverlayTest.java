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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

	/**
	 * A long name wraps inside the box, as the step line does, rather than running past both of its
	 * sides as one centred line, cut off where the box sits at the screen's edge.
	 */
	@Test
	public void aLongNameWrapsInsideTheBox() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		Dimension one = drawn(destination);
		destination.name = "Civitas illa Fortis east bank deposit box";
		Dimension wrapped = drawn(destination);
		assertEquals("no letter is drawn outside the box", 0, lettersOutside);
		assertTrue("the name took another line rather than being dropped", wrapped.height > one.height);
	}

	/**
	 * The box names the destination in the tile label's words, and adds its world as the label does:
	 * only while the player is on another, where it says which world to hop to.
	 */
	@Test
	public void namesTheWorldOnlyWhileThePlayerIsOnAnother() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		RouteStepOverlay.Box box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("Lumbridge (W302)", RouteStepOverlay.title(box, destination, 444));
		assertEquals("Lumbridge", RouteStepOverlay.title(box, destination, 302));
		assertEquals("the tile's label says the same", RouteStepOverlay.title(box, destination, 444),
			DestinationSceneOverlay.label(destination) + DestinationSceneOverlay.elsewhere(destination, 444));
		assertEquals(RouteStepOverlay.title(box, destination, 302),
			DestinationSceneOverlay.label(destination) + DestinationSceneOverlay.elsewhere(destination, 302));
		destination.world = null;
		assertEquals("a destination on any world names none", "Lumbridge", RouteStepOverlay.title(box, destination, 444));
	}

	/**
	 * A run's leg has no step pins on the world map, and its stop's number, which the stop's own pin
	 * carries, heads the box; the step under it is its words alone. Any other route's step keeps the
	 * number its pin carries.
	 */
	@Test
	public void aRunsLegNumbersItsStopAndNotItsStep() throws IOException
	{
		DestinationState destinations = new DestinationState(new Gson());
		FarmRunState farm = new FarmRunState();
		FarmRunRoute run = new FarmRunRoute(farm, destinations, route, new ClientThread());
		RouteStepOverlay overlay = new RouteStepOverlay(null, null, destinations, route, run);
		String json = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "farm-run.json")), StandardCharsets.UTF_8);
		farm.accept(new Gson().fromJson(json, RelayResponse.FarmRun.class), 1, 1_000);
		run.sync();
		RouteStepOverlay.Box box = overlay.box(destinations.getDestination());
		assertEquals("1. Falador farm", box.title);
		assertEquals("Use Explorer's ring 2: Teleport", box.step);

		/* A place picked instead of the run's next stop: its steps have pins, and so their numbers. */
		RelayResponse.Destination place = received();
		destinations.selectLocal(place);
		route.follow(true);
		assertEquals("1. Lumbridge Home Teleport", overlay.box(destinations.getDestination()).step);
	}

	/** The tile's one line cuts a long name short; the box, which wraps, keeps it whole, up to a bound. */
	@Test
	public void keepsTheWholeNameTheTileCutsShort() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		destination.name = "Civitas illa Fortis east bank deposit box";
		assertEquals(destination.name, RouteStepOverlay.box(route.view(), destination.name).title);
		assertEquals("Civitas illa Fortis east bank deposit b…", DestinationSceneOverlay.label(destination));
		assertEquals("a name somebody typed is still bounded", 120,
			RouteStepOverlay.box(route.view(), "a".repeat(500)).title.length());
	}

	/** White pixels drawn outside the box by the last {@link #drawn}. */
	private int lettersOutside;

	/** The box as the game draws it, twice, because a panel sizes its background from the frame before. */
	private Dimension drawn(RelayResponse.Destination destination)
	{
		DestinationState destinations = new DestinationState(new Gson());
		destinations.selectLocal(destination);
		HarnessGame game = new HarnessGame();
		game.login("Example", 444, 3222, 3218, 0);
		RouteStepOverlay overlay = new RouteStepOverlay(game.client(),
			new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL), destinations, route,
			new FarmRunRoute(new FarmRunState(), destinations, route, new ClientThread()));
		int left = 200;
		BufferedImage image = new BufferedImage(600, 240, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		graphics.translate(left, 20);
		overlay.render(graphics);
		Dimension size = overlay.render(graphics);
		graphics.dispose();
		lettersOutside = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == Color.WHITE.getRGB() && (x < left || x >= left + size.width))
				{
					lettersOutside++;
				}
			}
		}
		return size;
	}
}
