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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A step is done when the player stands at its end, on its floor — and never on a guess.
 *
 * <p>The route Lumbridge → Al Kharid bank as the map words it: a teleport that lands in Al Kharid,
 * a walk to a staircase, a climb, and a walk to the counter upstairs. Each test stands the player
 * somewhere and asks which step is current.
 */
public class DestinationRouteTest
{
	static RelayResponse.RouteStep step(String slot, String title, boolean walk, @Nullable Integer x, int y, @Nullable Integer plane)
	{
		RelayResponse.RouteStep step = new RelayResponse.RouteStep();
		step.slot = slot;
		step.title = title;
		step.walk = walk;
		if (x != null)
		{
			step.at = new RelayResponse.RouteTile();
			step.at.x = x;
			step.at.y = y;
			step.at.plane = plane;
		}
		return step;
	}

	/** Teleport to (3293,3184), walk to the stairs (3300,3190), climb to floor 1, walk to the counter. */
	static List<RelayResponse.RouteStep> alKharid()
	{
		return Arrays.asList(
			step("method", "Ring of dueling", false, 3293, 3184, null),
			step("arrival-walk", "Walk 9 tiles to the stairs", true, 3300, 3190, 0),
			step("climb", "Climb the staircase", false, 3300, 3191, 1),
			step("arrival-walk", "Walk 12 tiles to the counter", true, 3310, 3200, 1));
	}

	static RelayResponse.Route route(String id, List<RelayResponse.RouteStep> steps)
	{
		RelayResponse.RouteOption way = new RelayResponse.RouteOption();
		way.id = id;
		way.label = id;
		way.best = true;
		way.steps = steps;
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Arrays.asList(way));
		return route;
	}

	static RelayResponse.Destination destination(String name, @Nullable RelayResponse.Route route)
	{
		RelayResponse.Destination d = new RelayResponse.Destination();
		d.x = 3310;
		d.y = 3200;
		d.plane = 1;
		d.name = name;
		d.route = route;
		return d;
	}

	private static int at(int current, int x, int y, int plane)
	{
		return DestinationRoute.next(alKharid(), current, new WorldPoint(x, y, plane));
	}

	@Test
	public void aStepIsDoneWithinAFewTilesOfItsEnd()
	{
		assertEquals("landed two tiles off the teleport's tile", 1, at(0, 3295, 3182, 0));
		assertEquals("still in Lumbridge", 0, at(0, 3222, 3218, 0));
		assertEquals("five tiles off is not there yet", 0, at(0, 3298, 3184, 0));
	}

	@Test
	public void aStepIsDoneOnlyOnItsOwnFloor()
	{
		assertEquals("at the stairs' foot, the climb is current", 2, at(1, 3300, 3190, 0));
		assertEquals("standing under the top of the stairs is not having climbed them", 2, at(2, 3300, 3191, 0));
		assertEquals("upstairs, the climb is done", 3, at(2, 3300, 3191, 1));
		assertEquals("a step with no floor is on the ground floor: upstairs over it is not there", 0, at(0, 3293, 3184, 1));
	}

	@Test
	public void neverSkipsAheadToAStepItHasNotReached()
	{
		/* Standing at the climb's top while the walk to the stairs is current: not the current step's end. */
		assertEquals(1, at(1, 3300, 3191, 1));
		/* Nor the stairs' foot while the teleport is current — the player may be walking past it. */
		assertEquals(0, at(0, 3300, 3190, 0));
	}

	@Test
	public void standingAtTheLastStepsEndIsArrivingWhicheverWayWasTaken()
	{
		assertEquals(4, at(0, 3311, 3199, 1));
		assertEquals("and arrived stays arrived", 4, at(4, 3222, 3218, 0));
	}

	@Test
	public void aStepWithNoTileIsPassedWhenTheOneAfterItIsReached()
	{
		List<RelayResponse.RouteStep> steps = Arrays.asList(
			step("method", "Use the spirit tree", false, null, 0, null),
			step("arrival-walk", "Walk to the patch", true, 2490, 3180, 0),
			step("walk-leg", "Walk 30 tiles", true, 2520, 3180, 0));
		assertEquals(0, DestinationRoute.next(steps, 0, new WorldPoint(3222, 3218, 0)));
		assertEquals(2, DestinationRoute.next(steps, 0, new WorldPoint(2490, 3181, 0)));
	}

	@Test
	public void aPlaceGainingItsRouteKeepsItsPlaceAndANewPlaceStartsOver()
	{
		DestinationRoute route = new DestinationRoute();
		route.sync(destination("Al Kharid bank", null));
		assertFalse(route.view().hasRoute());
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		assertTrue(route.advance(new WorldPoint(3293, 3184, 0)));
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		assertEquals("the same route arriving again is not a new one", 1, route.view().step);
		route.sync(destination("Varrock west bank", route("item:ring-of-dueling", alKharid())));
		assertEquals(0, route.view().step);
		route.sync(null);
		assertNull(route.view());
	}

	@Test
	public void nothingMovesWhereTheTileCannotBeTrusted()
	{
		DestinationRoute route = new DestinationRoute();
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		assertFalse(route.advance(null));
		assertEquals(0, route.view().step);
	}

	@Test
	public void aRerouteReplacesTheStepsAndTheOldRouteIsNotPutBack()
	{
		DestinationRoute route = new DestinationRoute();
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		DestinationRoute.Ticket ticket = route.beginReroute(0L);
		assertNotNull(ticket);
		assertNull("one at a time", route.beginReroute(0L));
		route.finishReroute(ticket, route("walk", Arrays.asList(step("walk-leg", "Walk 140 tiles", true, 3310, 3200, 1))), null, 5_000L);
		assertEquals("walk", route.view().way().id);
		assertTrue(route.view().rerouted);
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		assertEquals("the destination's own route is the old answer", "walk", route.view().way().id);
	}

	@Test
	public void anAnswerForAPlaceSinceReplacedIsThrownAwayButItsWaitIsKept()
	{
		DestinationRoute route = new DestinationRoute();
		route.sync(destination("Al Kharid bank", route("item:ring-of-dueling", alKharid())));
		DestinationRoute.Ticket ticket = route.beginReroute(0L);
		route.sync(destination("Varrock west bank", route("item:varrock-teleport", alKharid())));
		route.finishReroute(ticket, route("walk", alKharid()), null, 20_000L);
		assertEquals("item:varrock-teleport", route.view().way().id);
		assertNull("the map's wait is the member's, not the place's", route.beginReroute(10_000L));
		assertNotNull(route.beginReroute(20_000L));
	}

	@Test
	public void theTileIsTrustedInAPlaceCopiedWholeAndNotInRoomsPutTogether()
	{
		assertEquals(new WorldPoint(3222, 3218, 0), DestinationRoute.standing(client(false, null)));
		assertNull("a raid's rooms are forty tiles apart in the store they come from",
			DestinationRoute.standing(client(true, assembled())));
		assertEquals("a boss lair is the place it was copied from", new WorldPoint(3200, 3200, 0),
			DestinationRoute.standing(client(true, copied())));
	}

	/** A chunk table whose one chunk is turned: rooms put together. */
	private static int[][][] assembled()
	{
		int[][][] chunks = new int[4][13][13];
		for (int[][] plane : chunks)
		{
			for (int[] row : plane)
			{
				Arrays.fill(row, -1);
			}
		}
		chunks[0][0][0] = (400 << 14) | (400 << 3) | (1 << 1);
		return chunks;
	}

	/** One chunk, unturned. */
	private static int[][][] copied()
	{
		int[][][] chunks = assembled();
		chunks[0][0][0] = (400 << 14) | (400 << 3);
		return chunks;
	}

	/**
	 * Just enough client for {@link DestinationRoute#standing}: a player at Lumbridge, and a top-level
	 * view that is or is not an instance.
	 */
	static Client client(boolean instance, @Nullable int[][][] chunks)
	{
		WorldView view = (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[]{WorldView.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "isInstance":
						return instance;
					case "getInstanceTemplateChunks":
						return chunks;
					case "getPlane":
						return 0;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalLocation":
						/* The first tile of the scene, whose chunk the table says was copied from 400,400. */
						return instance ? new LocalPoint(64, 64, WorldView.TOPLEVEL) : null;
					case "getWorldLocation":
						return new WorldPoint(3222, 3218, 0);
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalPlayer":
						return player;
					case "getTopLevelWorldView":
					case "getWorldView":
						return view;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
