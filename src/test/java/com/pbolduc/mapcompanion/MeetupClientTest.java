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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * An answer and a Meet here, as the map's two meetup routes read them, from the member's own door.
 */
public class MeetupClientTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(new Gson());
	private final GroupState group = new GroupState(destinations);
	private final List<Request> sent = new CopyOnWriteArrayList<>();
	private final List<String> copied = new CopyOnWriteArrayList<>();

	MeetupClient client(int status, String body, @Nullable String retryAfter, boolean instance)
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
		return new MeetupClient(http, new Gson(), game(instance), new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, destinations, group, copied::add);
	}

	void connect()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
	}

	static void settle(MeetupClient client, String source) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while ((client.said(source) == null || client.said(source).sending) && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertFalse("the answer landed", client.said(source).sending);
	}

	static JsonObject body(Request request) throws Exception
	{
		Buffer body = new Buffer();
		request.body().writeTo(body);
		return new Gson().fromJson(body.readString(StandardCharsets.UTF_8), JsonObject.class);
	}

	@Test
	public void anAnswerIsOnePutWithTheMembersOwnHeaders() throws Exception
	{
		connect();
		MeetupClient client = client(200, "{\"eventId\":\"star-hunt\",\"answer\":\"going\"}", null, false);
		client.answer("star-hunt", "going");
		settle(client, "answer:star-hunt");

		assertEquals(1, sent.size());
		Request request = sent.get(0);
		assertEquals("PUT", request.method());
		assertEquals("/api/companion/event/answer", request.url().encodedPath());
		assertEquals(pairing.code(), request.header("X-Companion-Code"));
		assertEquals(identity.id(), request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		assertEquals("{\"eventId\":\"star-hunt\",\"answer\":\"going\"}", body(request).toString());
		assertNull(client.said("answer:star-hunt").message);

		RelayResponse.Meetup meetup = new RelayResponse.Meetup();
		meetup.id = "star-hunt";
		meetup.mine = "maybe";
		assertEquals("the answer just given stands in until the map says it", "going",
			group.mine(meetup, System.currentTimeMillis()));
	}

	@Test
	public void onlyTheMapsFourAnswersAreSent()
	{
		connect();
		MeetupClient client = client(200, "{}", null, false);
		client.answer("star-hunt", "arrived");
		client.answer("star-hunt", "definitely");
		assertTrue(sent.isEmpty());
	}

	@Test
	public void aRefusalIsASentenceAndTheWaitIsHonoured() throws Exception
	{
		connect();
		MeetupClient busy = client(429, "{\"reason\":\"busy\"}", "20", false);
		busy.answer("star-hunt", "maybe");
		settle(busy, "answer:star-hunt");
		assertEquals(MeetupClient.BUSY, busy.said("answer:star-hunt").message);
		assertFalse(busy.ready());
		busy.answer("star-hunt", "maybe");
		assertEquals("nothing goes inside the map's wait", 1, sent.size());

		MeetupClient forged = client(403, "{\"reason\":\"needs_code\"}", null, false);
		forged.handle("answer:x", destinations.generation(), false, null, "https://osrs-map.com",
			RoutePressTest.reply(403, "{\"reason\":\"needs_code\"}"));
		assertEquals(MeetupClient.NEEDS_CONNECTION, forged.said("answer:x").message);
	}

	@Test
	public void sendsNothingBeforeConnect()
	{
		MeetupClient client = client(200, "{}", null, false);
		client.answer("star-hunt", "going");
		assertTrue(sent.isEmpty());
		assertEquals(MeetupClient.NEEDS_CONNECTION, client.said("answer:star-hunt").message);
	}

	@Test
	public void meetHereMakesTheMeetupWhereThePlayerStandsAndCopiesItsLink() throws Exception
	{
		connect();
		MeetupClient client = client(200, "{\"path\":\"/map?event=abc123&task=companion\"}", null, false);
		long start = System.currentTimeMillis() + 3_600_000L;
		client.create("  Barrows\trun  ", start);
		settle(client, "meet");

		Request request = sent.get(0);
		assertEquals("PUT", request.method());
		assertEquals("/api/companion/event", request.url().encodedPath());
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		JsonObject meetup = body(request);
		assertEquals("Barrows run", meetup.get("title").getAsString());
		assertEquals(3222, meetup.get("x").getAsInt());
		assertEquals(3218, meetup.get("y").getAsInt());
		assertEquals(0, meetup.get("plane").getAsInt());
		assertEquals(302, meetup.get("world").getAsInt());
		assertEquals(start, meetup.get("startsAtMs").getAsLong());
		assertEquals(6, meetup.size());
		assertEquals(MeetupClient.MADE, client.said("meet").message);
		assertEquals("[" + pairing.serverUrl() + "/map?event=abc123&task=companion]", copied.toString());
	}

	@Test
	public void aLinkIsOnlyEverAPathOnTheApprovedMap()
	{
		assertEquals("https://osrs-map.com/map?event=a1", MeetupClient.link("https://osrs-map.com", "/map?event=a1"));
		assertNull(MeetupClient.link("https://osrs-map.com", "//evil.example/map"));
		assertNull(MeetupClient.link("https://osrs-map.com", "https://evil.example/map"));
		assertNull(MeetupClient.link("https://osrs-map.com", "/map?event=<script>"));
		assertNull(MeetupClient.link("https://osrs-map.com", "/map event"));
		assertNull(MeetupClient.link("https://osrs-map.com", null));
	}

	@Test
	public void meetHereInsideAnInstanceSendsNothing()
	{
		connect();
		MeetupClient client = client(200, "{\"path\":\"/map\"}", null, true);
		client.create("Vorkath", System.currentTimeMillis() + 3_600_000L);
		assertTrue(sent.isEmpty());
		assertEquals(MeetupClient.NEEDS_TILE, client.said("meet").message);
	}

	/** Logged in at Lumbridge on W302, in or out of an instance. */
	static Client game(boolean instance)
	{
		Client plain = DestinationRouteTest.client(instance, null);
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return GameState.LOGGED_IN;
					case "getWorld":
						return 302;
					default:
						return method.invoke(plain, args);
				}
			});
	}
}
