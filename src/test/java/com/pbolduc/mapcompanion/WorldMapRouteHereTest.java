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

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldMapRouteHereTest
{
	private static final WorldPoint CENTRE = new WorldPoint(3200, 3200, 0);
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private final List<WorldMapPoint> drawn = new CopyOnWriteArrayList<>();
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

		@Override
		public void invokeLater(Runnable r)
		{
			r.run();
		}
	};
	private final HarnessStore store = new HarnessStore();
	private final DestinationState destinations = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();
	/** The marker's syncs that have finished, so an answer's pins are read only once drawn. */
	private final AtomicInteger synced = new AtomicInteger();
	private final DestinationMarker marker = new DestinationMarker(manager, destinations,
		new HarnessConfig(store, "http://localhost"), now, route, null)
	{
		@Override
		void sync()
		{
			super.sync();
			synced.incrementAndGet();
		}
	};

	/** Route here wired as the plugin wires it, its map's answer held until {@code answer} opens. */
	private WorldMapRouteHere routeHere(CountDownLatch answer)
	{
		Pairing pairing = new Pairing(store, new Gson());
		MemberIdentity identity = new MemberIdentity(store);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			assertTrue(Uninterruptibles.awaitUninterruptibly(answer, 5, TimeUnit.SECONDS));
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(MediaType.get("application/json"), RoutePressTest.ANSWER)).build();
		}).build();
		RoutePress press = new RoutePress(http, new Gson(), RoutePressTest.game(), now, pairing, identity, route,
			destinations, marker);
		return new WorldMapRouteHere(null, null, null, press, pairing, null, marker);
	}

	/**
	 * The spot pressed is marked on the map the press was made on while its answer is on its way:
	 * the destination's pin, faded, and not held at the edge, which is the way to go's. The answer
	 * takes the mark away and leaves the route's own pin.
	 */
	@Test
	public void theSpotIsMarkedWhileItsPressPlans() throws Exception
	{
		CountDownLatch answer = new CountDownLatch(1);
		WorldMapRouteHere routeHere = routeHere(answer);
		WorldPoint spot = new WorldPoint(3408, 3573, 0);
		routeHere.press(spot);
		assertEquals(1, drawn.size());
		WorldMapPoint mark = drawn.get(0);
		assertEquals(spot, mark.getWorldPoint());
		assertFalse(mark.isSnapToEdge());
		assertEquals("Planning the way here…", mark.getTooltip());
		routeHere.tick();
		assertEquals("still planning", 1, drawn.size());

		answer.countDown();
		long deadline = System.currentTimeMillis() + 3_000;
		while (synced.get() == 0 && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertEquals("the answer is drawn", 1, synced.get());
		routeHere.tick();
		assertFalse("the answer takes the mark away", drawn.contains(mark));
		assertEquals(1, drawn.size());
		assertEquals("the route's own pin, where the map planned to", new WorldPoint(3408, 3573, 2), drawn.get(0).getWorldPoint());
	}

	/** A press made among destinations since cleared, by a group left, leaves no mark behind. */
	@Test
	public void aGroupLeftWhileItPlansTakesTheMark()
	{
		marker.planning(new WorldPoint(3408, 3573, 0));
		assertEquals(1, drawn.size());
		destinations.clear();
		marker.sync();
		assertTrue(drawn.isEmpty());

		marker.planning(new WorldPoint(3408, 3573, 0));
		marker.remove();
		assertTrue("nor does a plugin turned off", drawn.isEmpty());
	}

	/** RuneLite draws a tile's centre at the point it maps the tile to, so the nearest tile is the rounded offset. */
	@Test
	public void thePointerPicksTheTileItIsOver()
	{
		assertEquals(CENTRE, WorldMapRouteHere.tileAt(500, 300, 500, 300, 4f, CENTRE));
		assertEquals(CENTRE, WorldMapRouteHere.tileAt(501, 299, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3201, 3200, 0), WorldMapRouteHere.tileAt(503, 300, 500, 300, 4f, CENTRE));
		/* Truncating keeps this one on the centre tile: west and south of it is where that goes wrong. */
		assertEquals(new WorldPoint(3199, 3200, 0), WorldMapRouteHere.tileAt(497, 300, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3200, 3199, 0), WorldMapRouteHere.tileAt(500, 303, 500, 300, 4f, CENTRE));
		/* Up the screen is north. */
		assertEquals(new WorldPoint(3200, 3210, 0), WorldMapRouteHere.tileAt(500, 260, 500, 300, 4f, CENTRE));
		assertEquals(new WorldPoint(3190, 3195, 0), WorldMapRouteHere.tileAt(480, 310, 500, 300, 2f, CENTRE));
	}

	/** The chat box hears this press's refusal once its answer is in, and never the answer before it. */
	@Test
	public void onlyThePressesOwnRefusalIsSaid()
	{
		String source = WorldMapRouteHere.SOURCE;
		RoutePress.Said earlier = new RoutePress.Said(source, false, "An older refusal", null, 1);
		RoutePress.Said planning = new RoutePress.Said(source, true, null, null, 1);
		RoutePress.Said refused = new RoutePress.Said(source, false, "Connect to a map to plan a route.", null, 1);
		RoutePress.Said routed = new RoutePress.Said(source, false, null, "tile:3201,3200,0", 1);

		assertFalse("the answer from before the press", WorldMapRouteHere.answered(earlier, earlier));
		assertNull(WorldMapRouteHere.refusal(earlier, earlier));
		assertFalse("still planning", WorldMapRouteHere.answered(earlier, planning));
		assertNull(WorldMapRouteHere.refusal(earlier, planning));
		assertEquals("Connect to a map to plan a route.", WorldMapRouteHere.refusal(earlier, refused));
		assertTrue("a route came back", WorldMapRouteHere.answered(null, routed));
		assertNull(WorldMapRouteHere.refusal(null, routed));
		assertNull("another button pressed since", WorldMapRouteHere.refusal(earlier, null));
	}
}
