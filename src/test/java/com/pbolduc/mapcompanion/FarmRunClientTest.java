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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import static org.junit.Assert.*;
import org.junit.Test;

/** The real press and HTTP callback: consent, body, wait, cancellation and connection lifetime. */
public class FarmRunClientTest
{
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String EPOCH = "contract-relay-epoch";
	private final Gson gson = new Gson();
	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, gson);
	private final MemberIdentity identity = new MemberIdentity(store);
	private final FarmRunState state = new FarmRunState();
	private final DestinationState destinations = new DestinationState(gson);
	private final DestinationRoute gate = new DestinationRoute();
	private final List<Request> sent = new CopyOnWriteArrayList<>();
	private final List<Call> calls = new CopyOnWriteArrayList<>();
	private final AtomicReference<String> answer = new AtomicReference<>("{}");
	private final AtomicReference<Integer> status = new AtomicReference<>(200);
	private final AtomicReference<String> retry = new AtomicReference<>();
	private final AtomicReference<String> removedRevision = new AtomicReference<>();
	private CountDownLatch entered;
	private CountDownLatch release;

	private FarmRunClient client()
	{
		return client(gate);
	}

	private FarmRunClient client(DestinationRoute gate)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			calls.add(chain.call());
			if (entered != null) entered.countDown();
			if (status.get() == 0) throw new java.io.IOException("no route to host");
			if (release != null)
			{
				try
				{
					assertTrue(release.await(3, TimeUnit.SECONDS));
				}
				catch (InterruptedException error)
				{
					throw new java.io.IOException(error);
				}
			}
			Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
			.code(status.get()).message("").body(ResponseBody.create(MediaType.get("application/json"), answer.get()));
			reply.header("X-Companion-Relay-Epoch", EPOCH);
			if (retry.get() != null) reply.header("Retry-After", retry.get());
			if (removedRevision.get() != null) reply.header("X-Companion-Run-Revision", removedRevision.get());
			if (status.get() == 302) reply.header("Location", "https://elsewhere.invalid/");
			return reply.build();
		}).build();
		Client plain = DestinationRouteTest.client(false, null);
		Client game = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGGED_IN : method.invoke(plain, args));
		ClientThread thread = new ClientThread()
		{
			@Override
			public void invokeLater(Runnable action)
			{
				action.run();
			}
		};
		return new FarmRunClient(http, gson, game, thread, pairing, identity, state, destinations, gate);
	}

	private void connect()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		state.bind(identity.id());
	}

	private RelayResponse.FarmRun example() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(), StandardCharsets.UTF_8);
		return gson.fromJson(json, RelayResponse.FarmRun.class);
	}

	private void readyAnswer() throws Exception
	{
		RelayResponse.Directive reply = new RelayResponse.Directive();
		reply.relayEpoch = EPOCH;
		reply.run = example();
		reply.runRevision = 1;
		answer.set(gson.toJson(reply));
	}

	private void settled(FarmRunClient client) throws Exception
	{
		long until = System.currentTimeMillis() + 3_000;
		while (client.said() != null && client.said().planning && System.currentTimeMillis() < until)
		{
			Thread.sleep(5);
		}
		assertNotNull(client.said());
		assertFalse(client.said().planning);
	}

	private JsonObject body(Request request) throws Exception
	{
		Buffer buffer = new Buffer();
		request.body().writeTo(buffer);
		return gson.fromJson(buffer.readUtf8(), JsonObject.class);
	}

	@Test
	public void sendsNothingBeforeConnect() throws Exception
	{
		FarmRunClient client = client();
		client.start("birdhouses");
		assertTrue(sent.isEmpty());
		assertNull(state.view());
		/* Logged in with no group: not connected, said apart from a logout, which asks to log in instead. */
		assertEquals(RoutePress.NOT_CONNECTED, client.said().message);
	}

	/**
	 * At the login screen a run's presses said "Connect to your map to plan a route." and "Run ended
	 * here. Connect to end it on the map too." to a player in a group. They say to log in, End still
	 * ends the run here, and nothing is sent. Logged in with no member the map takes yet, End says
	 * the map still has the run.
	 */
	@Test
	public void atTheLoginScreenARunsPressesSayToLogIn() throws Exception
	{
		connect();
		Client loggedOut = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGIN_SCREEN : null);
		FarmRunClient client = new FarmRunClient(new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			throw new java.io.IOException("nothing should be sent");
		}).build(), gson, loggedOut, new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, state, destinations, gate);
		client.start("birdhouses");
		assertEquals("Log in to start a run.", client.said().message);
		client.end();
		assertEquals("Run ended here. Log in to end it on the map too.", client.said().message);
		assertTrue(sent.isEmpty());
		assertEquals("Log in to reroute the run.", FarmRunClient.unsent(false, true, false));
		assertEquals("Run ended here. Not connected to the group yet, so the map still has it.", FarmRunClient.unsent(true, false, true));
	}

	@Test
	public void startsOnceWithOwnCredentialsAndTileAndReadsTheReply() throws Exception
	{
		connect();
		readyAnswer();
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
		FarmRunClient client = client();
		client.start("preset:herb");
		assertTrue(entered.await(3, TimeUnit.SECONDS));
		client.start("preset:tree");
		assertEquals(1, sent.size());
		release.countDown();
		settled(client);
		Request request = sent.get(0);
		assertEquals(FarmRunClient.PATH, request.url().encodedPath());
		assertEquals("ABC234", request.header("X-Companion-Code"));
		assertEquals(identity.id(), request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		assertEquals("preset:herb", body(request).get("start").getAsString());
		assertEquals(3222, body(request).getAsJsonObject("from").get("x").getAsInt());
		assertNotNull(state.view());
		assertEquals(example().id, state.view().run.id);
	}

	@Test
	public void reroutesOnlyWithTheCurrentRunAndItsConfirmedOrdinals() throws Exception
	{
		connect();
		readyAnswer();
		RelayResponse.FarmRun run = example();
		state.acceptFor(identity.id(), EPOCH, run, 1, System.currentTimeMillis() - 1_000);
		state.observe(null, null, null, System.currentTimeMillis());
		CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
		patch.id = "falador-herb";
		patch.completionId = "local-replant";
		patch.completedMsAgo = 0L;
		state.observe(List.of(patch), null, null, System.currentTimeMillis());
		FarmRunClient client = client();
		client.reroute();
		settled(client);
		JsonObject reroute = body(sent.get(0)).getAsJsonObject("reroute");
		assertEquals(run.id, reroute.get("id").getAsString());
		assertEquals("[1,3]", reroute.get("done").toString()); // Fixture stop 3 was already confirmed.
		assertEquals("[4]", reroute.get("visited").toString()); // A visit is not a replant.
	}

	@Test
	public void honoursTheMapsWaitWithoutRetryingOnItsOwn() throws Exception
	{
		connect();
		status.set(429);
		retry.set("90");
		answer.set("{\"error\":\"Please wait for this run.\"}");
		FarmRunClient client = client();
		client.start("birdhouses");
		settled(client);
		assertEquals("Please wait for this run.", client.said().message);
		assertFalse(client.ready());
		client.start("birdhouses");
		assertEquals(1, sent.size());
		assertFalse(gate.pressReady(System.currentTimeMillis() + 89_000));
	}

	/**
	 * "Nothing the client has seen needs a visit yet" is Ready now's commonest answer, and it held
	 * every Route, Reroute and Start for half a minute, as if the map had failed. A refusal the map
	 * words is an answer: it holds the five seconds between presses that Route keeps after a 404.
	 */
	@Test
	public void aRefusalTheMapWordsHoldsOnlyTheWaitBetweenPresses() throws Exception
	{
		connect();
		for (int code : new int[] {409, 404, 400})
		{
			DestinationRoute held = new DestinationRoute();
			status.set(code);
			answer.set("{\"reason\":\"nothing_due\",\"error\":\"Nothing the client has seen needs a visit yet.\"}");
			FarmRunClient client = client(held);
			client.start("ready-now");
			settled(client);
			assertEquals("Nothing the client has seen needs a visit yet.", client.said().message);
			assertFalse(code + " still holds the next press", held.pressReady(System.currentTimeMillis()));
			assertTrue(code + " holds it five seconds, not thirty",
				held.pressReady(System.currentTimeMillis() + RoutePress.MIN_GAP_MS + 1_000));
		}
	}

	@Test
	public void aWordedRefusalStillKeepsTheWaitTheMapNames() throws Exception
	{
		connect();
		status.set(409);
		retry.set("20");
		answer.set("{\"reason\":\"no_run\",\"error\":\"That run has ended. Start another.\"}");
		FarmRunClient client = client();
		client.start("birdhouses");
		settled(client);
		assertFalse(gate.pressReady(System.currentTimeMillis() + 15_000));
		assertTrue(gate.pressReady(System.currentTimeMillis() + 21_000));
	}

	/** No answer to read, a failing map, or a refusal whose words cannot be read: still the half minute. */
	@Test
	public void aFailureOrAnUnreadableRefusalStillBacksOff() throws Exception
	{
		connect();
		String[][] replies = {
			{"500", "{\"error\":\"The map could not plan that run just now.\"}"},
			{"409", "<html>Conflict</html>"},
			{"404", ""}
		};
		for (String[] reply : replies)
		{
			DestinationRoute held = new DestinationRoute();
			status.set(Integer.parseInt(reply[0]));
			answer.set(reply[1]);
			FarmRunClient client = client(held);
			client.start("birdhouses");
			settled(client);
			assertFalse(reply[0] + " " + reply[1] + " backs off",
				held.pressReady(System.currentTimeMillis() + RoutePress.FAILURE_GAP_MS - 5_000));
		}
	}

	/**
	 * How long each kind of reply keeps every Route, Reroute and run waiting, and what the card says.
	 * A busy map is waited on for exactly what it asks, as Route waits on it: the two share one gate,
	 * so a run that waited longer would keep Route shut past the map's own word. A refusal the map
	 * words has its own test above.
	 */
	@Test
	public void eachKindOfReplyKeepsItsOwnWaitAndWords() throws Exception
	{
		connect();
		String worded = "{\"error\":\"Please wait for this run.\"}";
		String notPrepared = Words.of("farm.notPrepared");
		Object[][] cases = {
			{429, "7", "{}", MapWait.BUSY, 7_000L},
			{503, "7", "{}", MapWait.BUSY, 7_000L},
			{429, "90", "{}", MapWait.BUSY, 90_000L},
			{429, null, worded, "Please wait for this run.", 30_000L},
			{503, null, "{}", MapWait.BUSY, 30_000L},
			{403, null, "{}", RoutePress.NOT_CONNECTED, 30_000L},
			{409, null, "{}", notPrepared, 30_000L},
			{500, "7", "oops", notPrepared, 30_000L},
			{500, "60", "oops", notPrepared, 60_000L},
			/* A redirect is the address's fault and no retry changes it: five minutes, whatever it names. */
			{302, null, "", RoutePress.REDIRECTS, 300_000L},
			{302, "7", "", RoutePress.REDIRECTS, 300_000L},
			/* No reply at all. */
			{0, null, "", RoutePress.UNREACHABLE, 30_000L},
			{200, null, "not json at all", RoutePress.UNREADABLE, 30_000L},
			{200, null, "{}", "The map returned no readable run.", 5_000L},
			{200, "20", "{}", "The map returned no readable run.", 20_000L},
		};
		for (Object[] c : cases)
		{
			DestinationRoute held = new DestinationRoute();
			status.set((Integer) c[0]);
			retry.set((String) c[1]);
			answer.set((String) c[2]);
			FarmRunClient client = client(held);
			long before = System.currentTimeMillis();
			client.start("birdhouses");
			settled(client);
			String name = "HTTP " + c[0] + ", Retry-After " + c[1];
			assertEquals(name, c[3], client.said().message);
			RoutePressTest.assertWait(name, (long) c[4], held.pressWait(before));
		}
	}

	@Test
	public void refusesRedirectsInsteadOfSendingCredentialsElsewhere() throws Exception
	{
		connect();
		status.set(302);
		FarmRunClient client = client();
		client.start("birdhouses");
		settled(client);
		assertEquals(1, sent.size());
		assertEquals(RoutePress.REDIRECTS, client.said().message);
		assertNull(state.view());
	}

	@Test
	public void endCancelsTheInitialPlanAndALateReplyCannotRestartIt() throws Exception
	{
		connect();
		readyAnswer();
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
		FarmRunClient client = client();
		client.start("birdhouses");
		assertTrue(entered.await(3, TimeUnit.SECONDS));
		status.set(204);
		client.end();
		state.acceptFor(identity.id(), EPOCH, example(), 1, System.currentTimeMillis());
		assertNull("a delayed stream frame is refused before End has returned", state.view());
		long until = System.currentTimeMillis() + 3_000;
		while (sent.size() < 2 && System.currentTimeMillis() < until)
		{
			Thread.sleep(5);
		}
		assertEquals(2, sent.size());
		release.countDown();
		settled(client);
		assertEquals("DELETE", sent.get(1).method());
		assertNull(state.view());
		assertEquals("Run ended.", client.said().message);
	}

	@Test
	public void endsWithTheServersRemovalRevisionEvenWhenTheRemovalStreamFrameWasMissed() throws Exception
	{
		connect();
		status.set(204);
		removedRevision.set("8");
		FarmRunClient client = client();
		client.end();
		settled(client);
		assertFalse(state.ending());
		state.acceptFor(identity.id(), EPOCH, example(), 7, System.currentTimeMillis());
		assertNull(state.view());
		RelayResponse.FarmRun next = example();
		next.id = "a-new-run-after-end";
		state.acceptFor(identity.id(), EPOCH, next, 9, System.currentTimeMillis());
		assertEquals(next.id, state.view().run.id);
	}

	@Test
	public void aSecondEndPressDoesNotCancelAndReplaceTheDeletionAlreadyInFlight() throws Exception
	{
		connect();
		status.set(204);
		removedRevision.set("1");
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
		FarmRunClient client = client();
		client.end();
		assertTrue(entered.await(3, TimeUnit.SECONDS));
		client.end();
		try
		{
			assertFalse(calls.get(0).isCanceled());
		}
		finally
		{
			release.countDown();
		}
		settled(client);
		assertEquals(1, sent.size());
	}

	/**
	 * A run that lands is answered by its stops. "Run ready." used to be said too, and stayed under
	 * them for the run's whole life, still there at the first stop; the answer is still the run's, so
	 * an acknowledged server restart retires it with the run.
	 */
	@Test
	public void aRunThatLandsIsAnsweredByItsStopsAndRetiresWithThem() throws Exception
	{
		connect();
		readyAnswer();
		FarmRunClient client = client();
		client.start("birdhouses");
		settled(client);
		assertNotNull(state.view());
		assertNull("no Run ready. under the stops", client.said().message);
		assertFalse(client.said().shown(System.currentTimeMillis()));
		state.acceptFor(identity.id(), "second-server-epoch", null, 0, System.currentTimeMillis());
		assertNull(state.view());
		assertNull(client.said());
	}

	/** "Run ended." is news for a moment and then gives its row back; a refusal stays until the next press. */
	@Test
	public void aConfirmationGoesAfterTenSecondsAndARefusalStays() throws Exception
	{
		connect();
		status.set(204);
		removedRevision.set("1");
		FarmRunClient client = client();
		client.end();
		settled(client);
		FarmRunClient.Said ended = client.said();
		assertEquals("Run ended.", ended.message);
		assertTrue(ended.shown(ended.atMs + 9_000));
		assertFalse(ended.shown(ended.atMs + FarmRunClient.Said.CONFIRMATION_MS));

		status.set(409);
		answer.set("{\"reason\":\"nothing_seen\",\"error\":\"The client has not seen a patch yet.\"}");
		FarmRunClient refused = client(new DestinationRoute());
		refused.start("ready-now");
		settled(refused);
		FarmRunClient.Said said = refused.said();
		assertTrue("a refusal is still said a minute on", said.shown(said.atMs + 60_000));
	}

	@Test
	public void anOldReplyCannotPutARunOnAnotherConnection() throws Exception
	{
		connect();
		readyAnswer();
		entered = new CountDownLatch(1);
		release = new CountDownLatch(1);
		FarmRunClient client = client();
		client.start("birdhouses");
		assertTrue(entered.await(3, TimeUnit.SECONDS));
		client.clear();
		state.bind("another-character");
		destinations.clear();
		release.countDown();
		Thread.sleep(50);
		assertNull(state.view());
		assertNull(client.said());
	}

	@Test
	public void endStopsLocallyAndNamesTheRunItMayDelete() throws Exception
	{
		connect();
		RelayResponse.FarmRun run = example();
		state.acceptFor(identity.id(), EPOCH, run, 1, System.currentTimeMillis());
		status.set(204);
		FarmRunClient client = client();
		client.end();
		assertNull(state.view());
		settled(client);
		Request request = sent.get(0);
		assertEquals("DELETE", request.method());
		assertEquals(run.id, request.url().queryParameter("run"));
		state.acceptFor(identity.id(), EPOCH, run, 2, System.currentTimeMillis());
		assertNull("an already queued reroute cannot undo local End", state.view());
	}
}
