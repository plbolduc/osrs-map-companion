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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The route's numbered pins on the world map: one per step that names its end, numbered as the
 * sidebar numbers it, the current one holding the map's edge, and none once there.
 */
public class RouteStepPinsTest
{
	private final List<WorldMapPoint> drawn = new ArrayList<>();
	private final WorldMapPointManager manager = new WorldMapPointManager()
	{
		@Override
		public void add(WorldMapPoint point)
		{
			drawn.add(point);
		}

		@Override
		public void remove(WorldMapPoint point)
		{
			drawn.remove(point);
		}
	};
	private final ClientThread now = new ClientThread()
	{
		@Override
		public void invoke(Runnable r)
		{
			r.run();
		}
	};
	private final DestinationState state = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();
	private final HarnessConfig config = new HarnessConfig(new HarnessStore(), "http://localhost");
	private final DestinationMarker markers = new DestinationMarker(manager, state, config, now, route, null);

	/** The Al Kharid route; its last step ends on the destination's own tile. */
	private void routed()
	{
		state.selectLocal(DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())));
		route.sync(state.getDestination(), null);
	}

	@Test
	public void numbersEachStepAndLeavesTheDestinationsOwnTileToItsPin()
	{
		routed();
		assertEquals("[1@3293,3184,0*, 2@3300,3190,0+, 3@3300,3191,1+]",
			DestinationMarker.stepPins(route.view()).toString());
	}

	@Test
	public void theStepsBehindFadeAndNoneAreLeftOnceThere()
	{
		routed();
		route.advance(new WorldPoint(3293, 3184, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		route.advance(new WorldPoint(3300, 3190, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertEquals("[1@3293,3184,0-, 2@3300,3190,0-, 3@3300,3191,1*]",
			DestinationMarker.stepPins(route.view()).toString());
		route.advance(new WorldPoint(3310, 3200, 1), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertTrue(DestinationMarker.stepPins(route.view()).isEmpty());
	}

	@Test
	public void onlyTheCurrentStepHoldsTheMapsEdgeAndAllGoWithTheDestination()
	{
		routed();
		markers.sync();
		/* The destination's own pin, then the steps, the current one last. */
		assertEquals(4, drawn.size());
		WorldMapPoint first = drawn.get(3);
		assertEquals("1. Ring of dueling", first.getTooltip());
		assertTrue(first.isSnapToEdge());
		assertFalse(drawn.get(1).isSnapToEdge());

		List<WorldMapPoint> before = new ArrayList<>(drawn);
		markers.sync();
		assertEquals("an unchanged route draws nothing new", before, drawn);

		state.dismiss();
		route.sync(state.getDestination(), null);
		markers.sync();
		assertTrue(drawn.isEmpty());
	}

	/**
	 * The edge shows one amber disc, the way to go now: while the current step's pin holds it, the
	 * destination's own pin lets go. On the last step, whose pin is the destination's, and with no
	 * route at all, the destination holds the edge itself.
	 */
	@Test
	public void theDestinationLeavesTheEdgeToTheCurrentStep()
	{
		routed();
		markers.sync();
		assertEquals("[1. Ring of dueling]", edge());
		route.advance(new WorldPoint(3293, 3184, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		route.advance(new WorldPoint(3300, 3190, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		markers.sync();
		assertEquals("[3. Climb the staircase]", edge());

		route.advance(new WorldPoint(3300, 3191, 1), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		markers.sync();
		assertEquals("the last step's pin is the destination's", "[Al Kharid bank]", edge());

		state.selectLocal(DestinationRouteTest.destination("Al Kharid counter", null));
		route.sync(state.getDestination(), null);
		markers.sync();
		assertEquals("a destination with no route", "[Al Kharid counter]", edge());
	}

	/** What holds the map's edge, by tooltip. */
	private String edge()
	{
		List<String> held = new ArrayList<>();
		for (WorldMapPoint point : drawn)
		{
			if (point.isSnapToEdge())
			{
				held.add(point.getTooltip());
			}
		}
		return held.toString();
	}

	@Test
	public void aFarmRunDrawsItsStopNumbersWithoutADuplicateDestinationOrJourneyNumbers() throws Exception
	{
		FarmRunState farm = new FarmRunState();
		RelayResponse.FarmRun run = new Gson().fromJson(new String(getClass().getResourceAsStream("/contract/farm-run.json")
			.readAllBytes(), StandardCharsets.UTF_8), RelayResponse.FarmRun.class);
		FarmRunRoute follower = new FarmRunRoute(farm, state, route, now);
		DestinationMarker farmMarkers = new DestinationMarker(manager, state, config, now, route, follower);
		farm.accept(run, 1, 1_000);
		follower.sync();
		farmMarkers.sync();
		assertEquals("four stops, without a second destination or journey-step pins", 4, drawn.size());
		assertEquals("1. Falador farm · next", drawn.get(3).getTooltip());
		assertTrue(drawn.get(3).isSnapToEdge());
		assertTrue(drawn.stream().anyMatch(pin -> "3. Fossil Island birdhouse · done".equals(pin.getTooltip())));
		assertTrue(drawn.stream().anyMatch(pin -> "4. My bank · visited".equals(pin.getTooltip())));
		assertEquals(1, drawn.stream().filter(WorldMapPoint::isSnapToEdge).count());
		farm.follow(false);
		farmMarkers.sync();
		assertTrue(drawn.stream().noneMatch(WorldMapPoint::isSnapToEdge));
		farm.end();
		follower.sync();
		farmMarkers.sync();
		assertTrue(drawn.isEmpty());
	}
}
