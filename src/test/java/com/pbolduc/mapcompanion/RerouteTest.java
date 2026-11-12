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
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Reroute asks once, from where the player stands, and waits as long as the map says to.
 *
 * <p>The press is driven through {@link RoutePress#reroute}, the real client thread hand-off and a
 * real OkHttp call, answered by an interceptor rather than a server, so what is checked is what
 * would leave the machine.
 */
public class RerouteTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String ANSWER = "{\"target\":{\"x\":3310,\"y\":3200,\"plane\":1,\"name\":null},\"origin\":\"request\","
		+ "\"cached\":false,\"options\":[{\"id\":\"walk\",\"label\":\"Walk\",\"best\":true,\"blocked\":false,\"tiles\":90,"
		+ "\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk 90 tiles\",\"walk\":true,\"at\":{\"x\":3310,\"y\":3200,\"plane\":1}}]}]}";

	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationRoute route = new DestinationRoute();
	private final DestinationState destinations = new DestinationState(new Gson());
	private final List<Request> sent = new CopyOnWriteArrayList<>();

	private RoutePress client(int status, String body, @Nullable String retryAfter)
	{
		return press(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body));
			if (retryAfter != null)
			{
				reply.header("Retry-After", retryAfter);
			}
			return reply.build();
		}).build(), game());
	}

	private RoutePress press(OkHttpClient http, Client game)
	{
		return new RoutePress(http, new Gson(), game, new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, route, destinations, marker());
	}

	/** A pin that draws nothing: Reroute moves the route's steps, never the destination's pin. */
	private static DestinationMarker marker()
	{
		return new DestinationMarker(null, null, null, null, null, null)
		{
			@Override
			void sync()
			{
			}
		};
	}

	private void connect()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
	}

	private void destination()
	{
		route.sync(DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())), null);
	}

	private void settle() throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while (route.view().rerouting && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertFalse("the answer landed", route.view().rerouting);
	}

	@Test
	public void asksFromWhereThePlayerStandsWithTheMembersOwnCredentials() throws Exception
	{
		connect();
		destination();
		client(200, ANSWER, null).reroute();
		settle();

		assertEquals(1, sent.size());
		Request request = sent.get(0);
		assertEquals("POST", request.method());
		assertEquals("/api/companion/route", request.url().encodedPath());
		assertEquals(pairing.code(), request.header("X-Companion-Code"));
		assertEquals(identity.id(), request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		Buffer body = new Buffer();
		request.body().writeTo(body);
		JsonObject question = new Gson().fromJson(body.readString(StandardCharsets.UTF_8), JsonObject.class);
		assertEquals("{\"x\":3310,\"y\":3200,\"plane\":1}", question.get("target").toString());
		assertEquals("{\"x\":3222,\"y\":3218,\"plane\":0}", question.get("from").toString());
		assertEquals("the two tiles and nothing else", 2, question.size());

		DestinationRoute.View view = route.view();
		assertEquals("walk", view.way().id);
		assertEquals("request", view.origin);
		assertTrue(view.rerouted);
		assertNull(view.message);
	}

	@Test
	public void sendsNothingBeforeConnect() throws Exception
	{
		destination();
		client(200, ANSWER, null).reroute();
		assertTrue(sent.isEmpty());
		/* Logged in with no group: not connected, said apart from a logout, which asks to log in instead. */
		assertEquals(RoutePress.NOT_CONNECTED, route.view().message);
		assertEquals("item:ring-of-dueling", route.view().way().id);
	}

	/** At the login screen Reroute says to log in, not to connect a map the player is already in. */
	@Test
	public void atTheLoginScreenRerouteSaysToLogIn() throws Exception
	{
		connect();
		destination();
		Client loggedOut = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGIN_SCREEN : null);
		press(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			throw new java.io.IOException("nothing should be sent");
		}).build(), loggedOut).reroute();
		assertTrue(sent.isEmpty());
		assertEquals("Log in to plan a route.", route.view().message);
	}

	@Test
	public void waitsAsLongAsTheMapAsks() throws Exception
	{
		connect();
		destination();
		long before = System.currentTimeMillis();
		RoutePress busy = client(429, "{\"reason\":\"busy\"}", "20");
		busy.reroute();
		settle();
		DestinationRoute.View view = route.view();
		/* The wait it met, whose seconds are said as the line is drawn: "Try Reroute again in a few seconds" never counted. */
		assertEquals(MapWait.BUSY, view.message);
		assertTrue("Retry-After is honoured", view.nextRerouteMs >= before + 20_000L);
		assertEquals("the steps it had are kept", "item:ring-of-dueling", view.way().id);

		busy.reroute();
		assertEquals("a second press inside the wait sends nothing", 1, sent.size());
	}

	/**
	 * Inside a raid's rooms the game gives no tile to plan from, so Reroute sends nothing; its line
	 * names the one step that fixes it as well as the cause.
	 */
	@Test
	public void insideARaidsRoomsRerouteSendsNothingAndSaysWhatFixesIt() throws Exception
	{
		connect();
		destination();
		/* One chunk turned: rooms put together, as DestinationRouteTest's assembled table is. */
		int[][][] rooms = new int[4][13][13];
		for (int[][] plane : rooms)
		{
			for (int[] row : plane)
			{
				java.util.Arrays.fill(row, -1);
			}
		}
		rooms[0][0][0] = (400 << 14) | (400 << 3) | (1 << 1);
		Client raid = DestinationRouteTest.client(true, rooms);
		Client loggedIn = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGGED_IN : method.invoke(raid, args));
		press(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			throw new java.io.IOException("nothing should be sent");
		}).build(), loggedIn).reroute();
		assertTrue(sent.isEmpty());
		assertEquals(RoutePress.NEEDS_TILE, route.view().message);
		assertEquals("Reroute can't see where you are inside an instance. Press it again once you're out.", route.view().message);
	}

	@Test
	public void readsEachRefusalAsASentenceAndKeepsTheSteps() throws Exception
	{
		connect();
		Object[][] cases = {
			{503, "{\"reason\":\"busy\"}", MapWait.BUSY},
			{404, "{\"reason\":\"not_found\"}", RoutePress.NOT_FOUND},
			/* A map that says why is quoted, as it is for a press of Route: the reply is read the same way. */
			{404, "{\"reason\":\"not_found\",\"error\":\"That place is not on the map.\"}", "That place is not on the map."},
			/* The map refusing this member: one sentence for it, pointing at the card that says why. */
			{403, "{\"reason\":\"needs_code\"}", RoutePress.NOT_CONNECTED},
			{500, "oops", RoutePress.FAILED},
			{200, "{\"options\":[]}", RoutePress.NO_WAY},
			{200, "not json at all", RoutePress.UNREADABLE},
		};
		for (Object[] c : cases)
		{
			DestinationRoute fresh = new DestinationRoute();
			fresh.sync(DestinationRouteTest.destination("Al Kharid bank",
				DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())), null);
			DestinationRoute.Ticket ticket = fresh.beginReroute(0L);
			assertNotNull(ticket);
			RoutePress reader = new RoutePress(new OkHttpClient(), new Gson(), game(), new ClientThread(), pairing, identity, fresh,
				destinations, marker());
			reader.read(reply((int) c[0], (String) c[1]), reader.rerouted(ticket));
			assertEquals("HTTP " + c[0], c[2], fresh.view().message);
			assertEquals("HTTP " + c[0], "item:ring-of-dueling", fresh.view().way().id);
			assertFalse(fresh.view().rerouting);
			assertTrue("HTTP " + c[0] + " still waits before the next press", fresh.view().nextRerouteMs > 0);
		}
	}

	/**
	 * The press sends a tile position sharing may have kept back, so the README has to say so, and
	 * the body has to stay the two tiles the README names.
	 */
	@Test
	public void theReadmeSaysWhatAReroutePressSends() throws Exception
	{
		Set<String> fields = new TreeSet<>();
		for (Field field : RoutePress.Question.class.getDeclaredFields())
		{
			if (!field.isSynthetic())
			{
				fields.add(field.getName());
			}
		}
		assertEquals("[from, target]", fields.toString());
		String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8)
			.replaceAll("\\s+", " ");
		assertTrue("the README's What you share names Reroute's tile",
			readme.contains("**Reroute**, under the steps of a destination the map sent, sends the tile you are standing on"));
	}

	private static Response reply(int status, String body)
	{
		return new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid/api/companion/route").build())
			.protocol(Protocol.HTTP_1_1).code(status).message("").body(ResponseBody.create(JSON, body)).build();
	}

	/** Logged in at Lumbridge, outside any instance. */
	private static Client game()
	{
		Client plain = DestinationRouteTest.client(false, null);
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				if ("getGameState".equals(method.getName()))
				{
					return GameState.LOGGED_IN;
				}
				return method.invoke(plain, args);
			});
	}
}
