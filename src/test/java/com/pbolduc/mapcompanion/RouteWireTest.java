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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A route rides on a reply that carries far more than the route, so a bad one may cost only itself.
 *
 * <p>Every test reads a whole {@link RelayResponse} through the plain Gson the plugin is handed,
 * because the failure being guarded against is the reply — the claim receipt, the secret, the
 * destination — being lost to one value of the wrong kind inside the steps.
 */
public class RouteWireTest
{
	private final Gson gson = new Gson();

	private static String reply(String route)
	{
		return "{\"claimed\":true,\"directive\":{\"destination\":{\"x\":3222,\"y\":3218,\"plane\":0,"
			+ "\"name\":\"Lumbridge\"" + (route == null ? "" : ",\"route\":" + route) + "}}}";
	}

	private static final String STEP = "{\"slot\":\"method\",\"title\":\"Amulet of glory\",\"at\":{\"x\":3087,\"y\":3496}}";

	@Test
	public void readsAValidRoute()
	{
		RelayResponse r = gson.fromJson(reply("{\"origin\":\"shared\",\"options\":[{\"id\":\"item:glory\","
			+ "\"label\":\"Amulet of glory\",\"best\":true,\"tiles\":40,\"unknown\":1,\"steps\":[" + STEP + "]}]}"), RelayResponse.class);
		RelayResponse.Route route = r.directive.destination.route;
		assertNotNull(route);
		assertEquals("shared", route.origin);
		assertEquals("Amulet of glory", route.options.get(0).steps.get(0).title);
		assertNull("no floor is no floor, not the ground floor written down", route.options.get(0).steps.get(0).at.plane);
	}

	@Test
	public void anAbsentRouteIsTodaysDestination()
	{
		RelayResponse r = gson.fromJson(reply(null), RelayResponse.class);
		assertEquals("Lumbridge", r.directive.destination.name);
		assertNull(r.directive.destination.route);
	}

	@Test
	public void aRouteOfTheWrongKindCostsOnlyItself()
	{
		for (String bad : new String[]{
			"\"steps\"",
			"[1,2]",
			"{\"options\":5}",
			"{\"options\":[{\"id\":\"x\",\"label\":\"X\",\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk\",\"walk\":[true]}]}]}",
			"{\"options\":[{\"id\":\"x\",\"label\":\"X\",\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk\",\"at\":{\"x\":\"far\"}}]}]}",
		})
		{
			RelayResponse r = gson.fromJson(reply(bad), RelayResponse.class);
			assertTrue("the rest of the reply is still read for " + bad, r.claimed);
			assertEquals(bad, "Lumbridge", r.directive.destination.name);
			assertNull(bad, r.directive.destination.route);
		}
	}

	@Test
	public void dropsWhatItCannotDrawAndKeepsTheRest()
	{
		String route = "{\"options\":["
			/* A way with no steps it can print is not a way. */
			+ "{\"id\":\"empty\",\"label\":\"Nothing\",\"best\":true,\"steps\":[{\"slot\":\"Method!\",\"title\":\"Bad kind\"},{\"slot\":\"method\"}]},"
			+ "{\"id\":\"ring\",\"label\":\"Ring <col=ff0000>of</col>\\u0007 dueling\",\"tiles\":-3,\"steps\":["
			+ "{\"slot\":\"method\",\"title\":\"Ring of dueling\",\"notes\":[\"\",\"  Wilderness  \"],\"at\":{\"x\":3315,\"y\":3235,\"plane\":7}},"
			+ "{\"slot\":\"later-kind\",\"title\":\"Something the map adds tomorrow\",\"walk\":true,\"at\":{\"x\":3300,\"y\":3230,\"plane\":1}}]}]}";
		RelayResponse.Route read = gson.fromJson(reply(route), RelayResponse.class).directive.destination.route;
		assertEquals(1, read.options.size());
		RelayResponse.RouteOption ring = read.options.get(0);
		assertTrue("the one way left is the best one", ring.best);
		assertNull("a negative price is no price", ring.tiles);
		assertEquals("control bytes folded; tags are escaped where they are drawn", "Ring <col=ff0000>of</col> dueling", ring.label);
		assertNull("a fifth floor is no floor: the pin goes", ring.steps.get(0).at);
		assertEquals("Ring of dueling", ring.steps.get(0).title);
		assertEquals(java.util.Collections.singletonList("Wilderness"), ring.steps.get(0).notes);
		assertEquals("a kind this build does not know is printed, not dropped", "later-kind", ring.steps.get(1).slot);
		assertNull("the origin it does not know is from nowhere", read.origin);
	}

	@Test
	public void keepsTheBestWayAndAtMostFour()
	{
		StringBuilder route = new StringBuilder("{\"options\":[");
		for (int i = 0; i < 6; i++)
		{
			route.append(i == 0 ? "" : ",").append("{\"id\":\"way").append(i).append("\",\"label\":\"Way ").append(i)
				.append("\",\"best\":").append(i == 5).append(",\"steps\":[").append(STEP).append("]}");
		}
		route.append("]}");
		RelayResponse.Route read = gson.fromJson(reply(route.toString()), RelayResponse.class).directive.destination.route;
		assertEquals(RouteWire.MAX_WAYS, read.options.size());
		assertEquals("way5", read.options.get(3).id);
		assertTrue(read.options.get(3).best);
		assertFalse(read.options.get(0).best);
	}

	/** One place arriving bare and then with its steps is one destination, not two. */
	@Test
	public void aRouteArrivingLaterDoesNotUndoADismissal()
	{
		DestinationState state = new DestinationState(gson);
		state.accept(gson.fromJson(reply(null), RelayResponse.class).directive);
		state.dismiss();
		RelayResponse.Directive withSteps = gson.fromJson(reply("{\"options\":[{\"id\":\"g\",\"label\":\"G\",\"steps\":[" + STEP + "]}]}"), RelayResponse.class).directive;
		state.accept(withSteps);
		assertNull(state.getDestination());

		DestinationState pinned = new DestinationState(gson);
		pinned.accept(gson.fromJson(reply(null), RelayResponse.class).directive);
		RelayResponse.Destination star = new RelayResponse.Destination();
		star.name = "Rimmington mine";
		pinned.selectLocal(star);
		pinned.accept(withSteps);
		assertEquals("a star pinned here outlasts the same map destination gaining a route",
					 "Rimmington mine", pinned.getDestination().name);
	}

	@Test
	public void theSameDestinationKeepsItsLatestRoute()
	{
		DestinationState state = new DestinationState(gson);
		state.accept(gson.fromJson(reply(null), RelayResponse.class).directive);
		state.accept(gson.fromJson(reply("{\"options\":[{\"id\":\"g\",\"label\":\"G\",\"steps\":[" + STEP + "]}]}"), RelayResponse.class).directive);
		assertNotNull(state.getDestination().route);
	}
}
