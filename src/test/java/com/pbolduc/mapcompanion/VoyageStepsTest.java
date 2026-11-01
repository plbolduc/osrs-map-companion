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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A voyage, followed: <i>Board your boat at Port Sarim</i>, <i>Sail to Catherby</i>, <i>Dock at
 * Catherby</i>, ticked by the deck and the boats' ports rather than by tiles a deck never stands on.
 *
 * <p>The route is the map's own shape for a boat it knows: a walk to the mooring, the three rows of
 * the voyage, and a walk on from the pier. Each test stands the player somewhere, on a deck or not,
 * with the boats read or not, and asks which row is current.
 */
public class VoyageStepsTest
{
	private static final WorldPoint PORT_SARIM_PIER = new WorldPoint(3051, 3192, 0);
	private static final WorldPoint AT_SEA = new WorldPoint(2950, 3205, 0);
	private static final WorldPoint OFF_CATHERBY = new WorldPoint(2805, 3402, 0);
	private static final WorldPoint CATHERBY_PIER = new WorldPoint(2797, 3412, 0);

	private static RelayResponse.Voyage voyage(String to)
	{
		RelayResponse.Voyage voyage = new RelayResponse.Voyage();
		voyage.from = "Port Sarim";
		voyage.to = to;
		voyage.line = new int[][]{{3051, 3192}, {2950, 3205}, {2797, 3412}};
		RelayResponse.Gate gate = new RelayResponse.Gate();
		gate.text = "20 Sailing";
		gate.tier = "unmet";
		voyage.gates = Collections.singletonList(gate);
		return voyage;
	}

	/** Walk to the mooring, board, sail, dock — landing on {@code dockPlane} — and walk to the bank. */
	private static List<RelayResponse.RouteStep> steps(int dockPlane)
	{
		return Arrays.asList(
			DestinationRouteTest.step("boarding-walk", "Walk 12t north-west", true, 3051, 3192, 0),
			DestinationRouteTest.step("board", "Board your boat at Port Sarim", false, 3051, 3192, 0),
			DestinationRouteTest.step("sail", "Sail to Catherby", false, 2797, 3412, 0),
			DestinationRouteTest.step("dock", "Dock at Catherby", false, 2797, 3412, dockPlane),
			DestinationRouteTest.step("arrival-walk", "Walk 30t north", true, 2809, 3440, 0));
	}

	private static RelayResponse.Destination destination(List<RelayResponse.RouteStep> steps, RelayResponse.Voyage voyage)
	{
		RelayResponse.RouteOption way = new RelayResponse.RouteOption();
		way.id = "docking_point:2797,3412,0";
		way.label = "Docking point";
		way.best = true;
		way.steps = steps;
		way.voyage = voyage;
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Collections.singletonList(way));
		RelayResponse.Destination destination = new RelayResponse.Destination();
		destination.x = 2809;
		destination.y = 3440;
		destination.plane = 0;
		destination.name = "Catherby bank";
		destination.route = route;
		return destination;
	}

	private static DestinationRoute following(int dockPlane)
	{
		DestinationRoute route = new DestinationRoute();
		route.sync(destination(steps(dockPlane), voyage("Catherby")));
		return route;
	}

	private static DestinationRoute.Sailing aboard()
	{
		return new DestinationRoute.Sailing(true, null);
	}

	private static DestinationRoute.Sailing ashore(String... portsBySlot)
	{
		Map<Integer, String> moored = new HashMap<>();
		for (int i = 0; i < portsBySlot.length; i++)
		{
			if (portsBySlot[i] != null)
			{
				moored.put(i + 1, portsBySlot[i]);
			}
		}
		return new DestinationRoute.Sailing(false, moored);
	}

	@Test
	public void boardingIsTheDeckAndNotThePier()
	{
		DestinationRoute route = following(0);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		assertEquals("at the pier the walk is done, and boarding is next", 1, route.view().step);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		assertEquals("standing beside the boat is not being on it", 1, route.view().step);
		route.advance(PORT_SARIM_PIER, aboard());
		assertEquals("on the deck, the sail is next", 2, route.view().step);
	}

	@Test
	public void theSailEndsAboardWithinReachOfTheMooring()
	{
		DestinationRoute route = following(0);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(AT_SEA, aboard());
		assertEquals("halfway across is still sailing", 2, route.view().step);
		route.advance(OFF_CATHERBY, aboard());
		assertEquals("a boat alongside the mooring has sailed there", 3, route.view().step);
		route.advance(OFF_CATHERBY, aboard());
		assertEquals("still aboard is not docked", 3, route.view().step);
		route.advance(CATHERBY_PIER, ashore("Catherby"));
		assertEquals("ashore at the pier, the walk on is next", 4, route.view().step);
	}

	@Test
	public void steppingAshoreBeforeTheSailWasSeenPassesIt()
	{
		DestinationRoute route = following(0);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		route.advance(PORT_SARIM_PIER, aboard());
		/* No tick saw the boat near Catherby: the dock row, done, passes the sail before it. */
		route.advance(CATHERBY_PIER, ashore("Catherby"));
		assertEquals(4, route.view().step);
	}

	@Test
	public void aVoyageEndingOnAnotherFloorIsDockedByTheBoatsPort()
	{
		/* The dock row lands upstairs; the player steps off on the ground floor, where no tile matches. */
		DestinationRoute route = following(1);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim", "Brimhaven"));
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(OFF_CATHERBY, aboard());
		assertEquals(3, route.view().step);
		route.advance(CATHERBY_PIER, ashore("Port Sarim", "Brimhaven"));
		assertEquals("ground floor, and no boat moved: not docked yet", 3, route.view().step);
		route.advance(CATHERBY_PIER, ashore("Catherby", "Brimhaven"));
		assertEquals("the boat now reads moored at Catherby, which it did not before boarding", 4, route.view().step);
	}

	@Test
	public void aBoatAlreadyAtTheDockingPortIsNoEvidence()
	{
		DestinationRoute route = following(1);
		/* Slot 2 was at Catherby all along; only a port that changed says this voyage docked. */
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim", "Catherby"));
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(OFF_CATHERBY, aboard());
		route.advance(CATHERBY_PIER, ashore("Port Sarim", "Catherby"));
		assertEquals(3, route.view().step);
		assertFalse(DestinationRoute.mooredAnew("Catherby", null, Collections.singletonMap(1, "Catherby")));
	}

	@Test
	public void unreadBoatsMoveNothingByPort()
	{
		DestinationRoute route = following(1);
		route.advance(PORT_SARIM_PIER, DestinationRoute.Sailing.ASHORE_UNKNOWN);
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(OFF_CATHERBY, aboard());
		route.advance(CATHERBY_PIER, DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertEquals("with no boats read, only the tile could say, and it is on another floor", 3, route.view().step);
	}

	@Test
	public void aBoatMovedSincePlanningIsSaid()
	{
		DestinationRoute route = following(0);
		route.advance(new WorldPoint(3100, 3200, 0), ashore("Catherby"));
		assertEquals("Port Sarim", route.view().boatAway);
		route.advance(new WorldPoint(3100, 3200, 0), ashore("Port Sarim"));
		assertNull(route.view().boatAway);
		route.advance(new WorldPoint(3100, 3200, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertNull("boats nobody read say nothing about where the boat is", route.view().boatAway);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(OFF_CATHERBY, aboard());
		route.advance(CATHERBY_PIER, ashore("Catherby"));
		assertNull("once boarded, the boat moving is the voyage itself", route.view().boatAway);
	}

	@Test
	public void aVoyageForABoatTheMapDidNotKnowIsFollowedByItsTiles()
	{
		/* Two rows, the ride and the dock: no board row, so no deck evidence and no "boat moved". */
		List<RelayResponse.RouteStep> steps = Arrays.asList(
			DestinationRouteTest.step("boarding-walk", "Walk 12t north-west", true, 3051, 3192, 0),
			DestinationRouteTest.step("method", "Take your ship", false, 2797, 3412, 0),
			DestinationRouteTest.step("dock", "Dock at Catherby", false, 2797, 3412, 0));
		DestinationRoute route = new DestinationRoute();
		route.sync(destination(steps, voyage("Catherby")));
		route.advance(new WorldPoint(3100, 3200, 0), ashore("Rimmington"));
		assertNull(route.view().boatAway);
		route.advance(PORT_SARIM_PIER, ashore("Rimmington"));
		route.advance(CATHERBY_PIER, ashore("Rimmington"));
		assertTrue(route.view().arrived());
		assertEquals("the ride before the dock carries the gates", 1, RouteStepsView.sailingRow(route.view().way()));
	}

	@Test
	public void aVoyageIsNeverHandedToShortestPath()
	{
		RelayResponse.RouteStep sail = DestinationRouteTest.step("sail", "Sail to Catherby", true, 2797, 3412, 0);
		assertFalse("a sail sent as a walk is read as the sail it is", RouteWire.step(sail).walk);
		assertFalse(RouteWire.step(DestinationRouteTest.step("board", "Board", true, 3051, 3192, 0)).walk);
		assertFalse(RouteWire.step(DestinationRouteTest.step("dock", "Dock", true, 2797, 3412, 0)).walk);
		assertTrue(RouteWire.step(DestinationRouteTest.step("arrival-walk", "Walk", true, 2809, 3440, 0)).walk);

		List<PluginMessage> posted = new ArrayList<>();
		ShortestPath shortestPath = new ShortestPath(posted::add);
		DestinationRoute route = following(0);
		route.follow(true);
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		route.advance(PORT_SARIM_PIER, aboard());
		Map<String, Object> answer = new HashMap<>();
		shortestPath.tick(0, route.view(), true, AT_SEA, () ->
		{
		});
		answer.put("id", posted.get(0).getData().get("id"));
		answer.put("set", false);
		shortestPath.onPluginMessage(new PluginMessage(ShortestPath.NAMESPACE, "currentTarget", answer));
		posted.clear();
		shortestPath.tick(1, route.view(), true, AT_SEA, () ->
		{
		});
		for (PluginMessage message : posted)
		{
			assertFalse("nothing is drawn while the current row is a sail", "path".equals(message.getName()));
		}
		assertEquals(0, shortestPath.drawing());
	}

	@Test
	public void readsTheVoyageAndBoundsIt()
	{
		RelayResponse.Voyage sent = voyage("Catherby");
		sent.line = new int[][]{{3051, 3192}, {0, 5}, null, {1, 2, 3}, {2797, 3412}};
		RelayResponse.Gate odd = new RelayResponse.Gate();
		odd.text = "Swan Song";
		odd.tier = "maybe";
		sent.gates = Arrays.asList(sent.gates.get(0), odd);
		RelayResponse.Voyage read = RouteWire.voyage(sent);
		assertArrayEquals(new int[][]{{3051, 3192}, {2797, 3412}}, read.line);
		assertEquals("a tier this cannot draw is dropped, not guessed", 1, read.gates.size());
		sent.line = new int[][]{{3051, 3192}};
		assertNull("one point is no line", RouteWire.voyage(sent).line);
		sent.from = " ";
		assertNull("a voyage from nowhere is none", RouteWire.voyage(sent));
		int[][] long_ = new int[200][];
		for (int i = 0; i < long_.length; i++)
		{
			long_[i] = new int[]{3000 + i, 3200};
		}
		RelayResponse.Voyage many = voyage(null);
		many.line = long_;
		assertEquals(RouteWire.MAX_VOYAGE_POINTS, RouteWire.voyage(many).line.length);
		assertNull("a sail out to sea docks nowhere", RouteWire.voyage(many).to);
	}

	@Test
	public void theGatesHangUnderTheSailInTheirTiers()
	{
		RelayResponse.Gate gate = new RelayResponse.Gate();
		gate.text = "76 Sailing";
		gate.tier = "unmet";
		assertEquals("Needs 76 Sailing", RouteStepsView.gateLine(gate));
		gate.tier = "unknown";
		assertEquals("Unconfirmed: 76 Sailing", RouteStepsView.gateLine(gate));
		gate.tier = "met";
		assertEquals("✓ 76 Sailing", RouteStepsView.gateLine(gate));
		DestinationRoute route = following(0);
		assertEquals(2, RouteStepsView.sailingRow(route.view().way()));
		assertEquals("Your boat is no longer at Port Sarim. Reroute plans from where it is.", RouteStepsView.boatAwayLine("Port Sarim"));
	}

	@Test
	public void theWalkToTheBoatAndBoardingShareOnePin()
	{
		DestinationRoute route = following(0);
		assertEquals("[1@3051,3192,0*, 3@2797,3412,0+]", DestinationMarker.stepPins(route.view()).toString());
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		assertEquals("the shared pin takes the current step's number",
			"[2@3051,3192,0*, 3@2797,3412,0+]", DestinationMarker.stepPins(route.view()).toString());
		route.advance(PORT_SARIM_PIER, aboard());
		assertEquals("[1@3051,3192,0-, 3@2797,3412,0*]", DestinationMarker.stepPins(route.view()).toString());
		route.advance(OFF_CATHERBY, aboard());
		assertEquals("[1@3051,3192,0-, 4@2797,3412,0*]", DestinationMarker.stepPins(route.view()).toString());
	}

	@Test
	public void theLineIsDrawnForTheVoyageBeingShownAndAmberWhileSailing()
	{
		RelayResponse.Destination shown = destination(steps(0), voyage("Catherby"));
		DestinationRoute route = new DestinationRoute();
		route.sync(shown);
		String key = DestinationRoute.keyOf(shown);
		assertEquals(3, VoyageMapOverlay.line(route.view(), key).length);
		assertNull("another destination's route draws nothing", VoyageMapOverlay.line(route.view(), "elsewhere"));
		assertFalse(VoyageMapOverlay.sailing(route.view()));
		route.advance(PORT_SARIM_PIER, ashore("Port Sarim"));
		assertTrue("boarding is part of the voyage", VoyageMapOverlay.sailing(route.view()));
		route.advance(PORT_SARIM_PIER, aboard());
		route.advance(OFF_CATHERBY, aboard());
		route.advance(CATHERBY_PIER, ashore("Catherby"));
		assertFalse("walking on from the pier", VoyageMapOverlay.sailing(route.view()));
		route.advance(new WorldPoint(2809, 3440, 0), ashore("Catherby"));
		assertNull("arrived, nothing is drawn", VoyageMapOverlay.line(route.view(), key));
	}
}
