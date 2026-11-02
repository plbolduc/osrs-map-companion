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
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
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
 * Route, pressed in the sidebar, asks once and shows the answer as the destination.
 *
 * <p>The press runs through the real client-thread hand-off and a real OkHttp call answered by an
 * interceptor, so what is checked is what leaves the machine and what the card then holds.
 */
public class RoutePressTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	static final String ANSWER = "{\"target\":{\"x\":3408,\"y\":3573,\"plane\":2,\"name\":\"Abyssal demon\"},"
		+ "\"choice\":{\"among\":\"places\",\"of\":5,\"nearest\":true},\"origin\":\"request\",\"cached\":false,"
		+ "\"options\":[{\"id\":\"walk\",\"label\":\"Walk\",\"best\":true,\"blocked\":false,\"tiles\":36,"
		+ "\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk 36 tiles\",\"notes\":[\"Nearest of 5 places\"],"
		+ "\"walk\":true,\"at\":{\"x\":3408,\"y\":3573,\"plane\":2}}]}]}";

	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationRoute route = new DestinationRoute();
	private final DestinationState destinations = new DestinationState(new Gson());
	private final AtomicInteger synced = new AtomicInteger();
	private final List<Request> sent = new CopyOnWriteArrayList<>();

	private RoutePress press(int status, String body, @Nullable String retryAfter)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body));
			if (retryAfter != null)
			{
				reply.header("Retry-After", retryAfter);
			}
			return reply.build();
		}).build();
		return new RoutePress(http, new Gson(), game(), new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, route, destinations, new DestinationMarker(null, null, null, null, null, null)
		{
			@Override
			void sync()
			{
				synced.incrementAndGet();
			}
		});
	}

	private void connect()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
	}

	private static void settle(RoutePress press, String source) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while (press.said(source) != null && press.said(source).planning && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertFalse("the answer landed", press.said(source).planning);
	}

	private static JsonObject body(Request request) throws Exception
	{
		Buffer body = new Buffer();
		request.body().writeTo(body);
		return new Gson().fromJson(body.readString(StandardCharsets.UTF_8), JsonObject.class);
	}

	@Test
	public void aSearchPickAsksByTheRowsIdAndFillsTheDestinationCard() throws Exception
	{
		connect();
		RoutePress press = press(200, ANSWER, null);
		press.press(RoutePress.Target.place("search", "npc:abyssal-demon", "Abyssal demon", new WorldPoint(3240, 3600, 0),
			"Monster · ~36 tiles"));
		settle(press, "search");

		assertEquals(1, sent.size());
		Request request = sent.get(0);
		assertEquals("POST", request.method());
		assertEquals("/api/companion/route", request.url().encodedPath());
		assertEquals(pairing.code(), request.header("X-Companion-Code"));
		assertEquals(identity.id(), request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		JsonObject question = body(request);
		assertEquals("{\"id\":\"npc:abyssal-demon\"}", question.get("target").toString());
		assertEquals("{\"x\":3222,\"y\":3218,\"plane\":0}", question.get("from").toString());
		assertEquals(2, question.size());

		RelayResponse.Destination shown = destinations.getDestination();
		assertNotNull("the answer is the destination now", shown);
		assertTrue(destinations.hasLocalDestination());
		assertEquals("Abyssal demon", shown.name);
		assertEquals("where the map planned to, not where the row stood", 3408, shown.x);
		assertEquals(2, shown.plane);
		assertEquals("Monster · ~36 tiles", shown.note);
		DestinationRoute.View view = route.view();
		assertEquals(DestinationRoute.keyOf(shown), view.key);
		assertEquals("walk", view.way().id);
		assertEquals("Nearest of 5 places", view.steps().get(0).notes.get(0));
		assertTrue("a press is the player's own, so it is followed", view.following);
		assertEquals(1, synced.get());

		/* The next tick's sync with the same destination keeps the route and the following. */
		route.sync(shown);
		assertTrue(route.view().following);
		assertEquals("walk", route.view().way().id);
	}

	@Test
	public void aTileTravelsAsATileUnderTheNameThePressKnew() throws Exception
	{
		connect();
		String answer = ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":null");
		RoutePress press = press(200, answer, null);
		press.press(RoutePress.Target.tile("star", new WorldPoint(3408, 3573, 2), "Rimmington mine", 302, "T7 · ~40m left"));
		settle(press, "star");
		assertEquals("{\"x\":3408,\"y\":3573,\"plane\":2}", body(sent.get(0)).get("target").toString());
		RelayResponse.Destination shown = destinations.getDestination();
		assertEquals("Rimmington mine", shown.name);
		assertEquals(Integer.valueOf(302), shown.world);
	}

	/**
	 * A spot picked on the world map has no name of its own, so the card takes the landmark the map
	 * names near it, capitalised to head the card, and keeps the press's own words from a map that
	 * names none. Any other tile keeps the name it was pressed under.
	 */
	@Test
	public void aSpotPickedOnTheWorldMapIsNamedByTheLandmarkNearIt() throws Exception
	{
		connect();
		RoutePress press = press(200, ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":\"near Lumbridge Castle\""), null);
		RoutePress.Target spot = RoutePress.Target.tile(WorldMapRouteHere.SOURCE, new WorldPoint(3408, 3573, 2),
			WorldMapRouteHere.NAME, null, null);
		press.press(spot);
		settle(press, WorldMapRouteHere.SOURCE);
		assertEquals("{\"x\":3408,\"y\":3573,\"plane\":2}", body(sent.get(0)).get("target").toString());
		assertEquals("Near Lumbridge Castle", destinations.getDestination().name);

		RoutePress.AnswerTarget planned = new RoutePress.AnswerTarget();
		planned.x = 3408;
		planned.y = 3573;
		assertEquals("a map that names no landmark", WorldMapRouteHere.NAME,
			RoutePress.destination(spot, planned, new RelayResponse.Route()).name);
		planned.name = "near Rimmington";
		assertEquals("Rimmington mine", RoutePress.destination(RoutePress.Target.tile("star", new WorldPoint(3408, 3573, 2),
			"Rimmington mine", 302, null), planned, new RelayResponse.Route()).name);
	}

	/**
	 * A clue step that is words goes as the clue, read by the map's own clue table, and the card
	 * takes the place the map names rather than the scroll's tier.
	 */
	@Test
	public void aClueThatIsWordsTravelsAsTheClueAndTakesTheMapsName() throws Exception
	{
		connect();
		RoutePress press = press(200, ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":\"Digsite\""), null);
		CompanionSnapshot.Clue clue = new CompanionSnapshot.Clue();
		clue.tier = "hard";
		clue.text = "Dance in the Digsite.";
		clue.itemId = 2722;
		press.press(RoutePress.Target.clue("clue", clue, "Hard clue", "Hard clue"));
		settle(press, "clue");
		assertEquals("{\"clue\":{\"tier\":\"hard\",\"text\":\"Dance in the Digsite.\",\"itemId\":2722}}",
			body(sent.get(0)).get("target").toString());
		RelayResponse.Destination shown = destinations.getDestination();
		assertEquals("the place the words lead to", "Digsite", shown.name);
		assertEquals("Hard clue", shown.note);
		assertEquals(3408, shown.x);
	}

	/**
	 * A map above beginner has no words and no tile: it goes as the clue a worded step goes as, with
	 * nothing in it but its tier and its scroll, and the map places the picture by the scroll's id.
	 */
	@Test
	public void aMapScrollTravelsAsTheClueWithNoWordsAndTakesTheMapsName() throws Exception
	{
		connect();
		RoutePress press = press(200, ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":\"West of the Crafting Guild\""), null);
		CompanionSnapshot.Clue clue = new CompanionSnapshot.Clue();
		clue.tier = "medium";
		clue.itemId = net.runelite.api.gameval.ItemID.TRAIL_CLUE_MEDIUM_MAP003;
		assertTrue(ClueReader.routesByClue(clue));
		press.press(RoutePress.Target.clue("clue", clue, "Medium clue", "Medium clue"));
		settle(press, "clue");
		assertEquals("{\"clue\":{\"tier\":\"medium\",\"itemId\":3596}}", body(sent.get(0)).get("target").toString());
		assertEquals("West of the Crafting Guild", destinations.getDestination().name);
	}

	@Test
	public void aDestinationThatMerelyArrivesIsNeverFollowed()
	{
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.destination = DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid()));
		destinations.accept(directive);
		route.sync(destinations.getDestination());
		assertFalse(route.view().following);
	}

	@Test
	public void sendsNothingBeforeConnectAndSaysSo()
	{
		RoutePress press = press(200, ANSWER, null);
		press.press(RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null));
		assertTrue(sent.isEmpty());
		/* Logged in with no group: not connected, said apart from a logout, which asks to log in instead. */
		assertEquals(RouteClient.NOT_CONNECTED, press.said("search").message);
		assertNull("another button's line is its own", press.said("death"));
		assertNull(destinations.getDestination());
	}

	/**
	 * At the login screen a press said "Connect to your map to plan a route." to a player in a group,
	 * with no Connect on the page to press. It says the one thing that is true, and sends nothing.
	 */
	@Test
	public void aPressAtTheLoginScreenSaysToLogIn()
	{
		connect();
		Client loggedOut = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGIN_SCREEN : null);
		RoutePress press = new RoutePress(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			throw new java.io.IOException("nothing should be sent");
		}).build(), new Gson(), loggedOut, new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, route, destinations, new TestDestinationMarker());
		press.press(RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null));
		assertTrue(sent.isEmpty());
		assertEquals("Log in to plan a route.", press.said("search").message);
	}

	@Test
	public void sharesTheWaitWithReroute() throws Exception
	{
		connect();
		RoutePress busy = press(429, "{\"reason\":\"busy\"}", "20");
		busy.press(RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null));
		settle(busy, "search");
		/* Its own words, not Reroute's: the wait's seconds are said where it is drawn, from the gate. */
		assertEquals(MapWait.BUSY, busy.said("search").message);
		assertNull("a refusal leaves the card as it was", destinations.getDestination());
		assertFalse(busy.ready());

		busy.press(RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null));
		assertEquals("a second press inside the wait sends nothing", 1, sent.size());
		assertTrue(busy.said("search").message.startsWith("The map asked to wait."));

		route.sync(DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())));
		assertNull("nor does Reroute", route.beginReroute(System.currentTimeMillis()));
	}

	/**
	 * A busy press under a friend's or a star's Route said "Try Reroute again in a few seconds.", the
	 * Reroute client's sentence, for a wait of half a minute. It says its own words and the wait the
	 * shared gate holds, counted down as it is drawn and gone when the wait is over — the wait the gate
	 * holds, so a later press that made it longer is the one counted.
	 */
	@Test
	public void aBusyPressSaysTheWaitTheGateHoldsAndCountsItDown() throws Exception
	{
		connect();
		RoutePress busy = press(429, "{\"reason\":\"busy\"}", "20");
		busy.press(RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null));
		settle(busy, "search");
		RoutePress.Said said = busy.said("search");
		long now = System.currentTimeMillis();
		long opens = now + busy.waitMs(now);
		assertTrue("the map's twenty seconds", opens - now > 19_000L);
		assertEquals("The map is busy. Try again in 20s.", busy.words(said, opens - 20_000L));
		assertEquals("The map is busy. Try again in 4s.", busy.words(said, opens - 4_000L));
		assertEquals("The map was busy.", busy.words(said, opens));
		assertFalse(busy.words(said, now).contains("Reroute"));

		/* A run that failed since holds every Route for its half minute, and that is the wait said. */
		route.finishPress(now + 30_000L);
		assertEquals("The map is busy. Try again in 30s.", busy.words(said, now));
	}

	@Test
	public void anAnswerForAGroupSinceLeftIsDropped() throws Exception
	{
		connect();
		RoutePress press = press(200, ANSWER, null);
		RoutePress.Target target = RoutePress.Target.place("search", "npc:abyssal-demon", "Abyssal demon", null, null);
		long asked = destinations.generation();
		destinations.clear();
		press.handle(asked, target, reply(200, ANSWER));
		assertNull(destinations.getDestination());
		assertNull(press.said("search"));
	}

	@Test
	public void readsEachRefusalAsASentence() throws Exception
	{
		connect();
		Object[][] cases = {
			/* An older map's 404 says no more than its status, so the plugin's own sentence stands. */
			{404, "{\"reason\":\"not_found\"}", RouteClient.NOT_FOUND},
			{404, "not json at all", RouteClient.NOT_FOUND},
			/* A map that says why is quoted: a clue whose words it does not know is not one with no place. */
			{404, "{\"reason\":\"no_tile\",\"error\":\"The map does not know this step's words, so it has no place to plan a "
				+ "route to.\"}", "The map does not know this step's words, so it has no place to plan a route to."},
			/* Its words were typed by somebody, so they are cleaned as everything else it sends is. */
			{404, "{\"reason\":\"no_tile\",\"error\":\"<col=ff0000>No single\\u0007 place</col>\"}", "No single place"},
			{404, "{\"reason\":\"no_tile\",\"error\":\"   \"}", RouteClient.NOT_FOUND},
			/* The map refusing this member: one sentence for it, pointing at the card that says why. */
			{403, "{\"reason\":\"needs_code\"}", RouteClient.NOT_CONNECTED},
			{500, "oops", RouteClient.FAILED},
			{200, "{\"options\":[]}", RouteClient.NO_WAY},
			{200, "not json at all", RouteClient.UNREADABLE},
		};
		RoutePress press = press(200, ANSWER, null);
		RoutePress.Target target = RoutePress.Target.place("search", "bank:lumbridge", "Lumbridge bank", null, null);
		for (Object[] c : cases)
		{
			press.handle(destinations.generation(), target, reply((int) c[0], (String) c[1]));
			assertEquals("HTTP " + c[0], c[2], press.said("search").message);
			assertNull(destinations.getDestination());
		}
	}

	static Response reply(int status, String body)
	{
		return new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid/api/companion/route").build())
			.protocol(Protocol.HTTP_1_1).code(status).message("").body(ResponseBody.create(JSON, body)).build();
	}

	/** Logged in at Lumbridge, outside any instance. */
	static Client game()
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
