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
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A world hop is not a logout. Star hunters hop all the time, and the tab polls the game's state
 * every second while it is open, so it sees the hop's HOPPING and LOADING and a dropped
 * connection's CONNECTION_LOST. Read as "logged out", each threw away the list, the pick and the
 * call in flight, and the list came back only on the feed's own schedule, up to minutes later.
 *
 * <p>Driven through real OkHttp calls answered by an interceptor, with a game whose state the test
 * sets, so what is checked is what the tab still holds and what would have left the machine.
 */
public class StarsHopTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	/** A world further down the list than the first, so a pick that survives is not a default. */
	private static final String PICK = "330|rimmington mine";

	private final AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
	private final List<String> sent = new CopyOnWriteArrayList<>();

	@Test
	public void aHopAndALoadingScreenKeepTheListAndThePickAndAskNothing() throws Exception
	{
		StarsClient stars = client(null);
		SwingUtilities.invokeAndWait(() -> stars.active(true));
		until(() -> stars.model.feed != null);
		SwingUtilities.invokeAndWait(() -> stars.model.selected = PICK);
		assertEquals("the list is the only thing the tab asks for", Collections.singletonList("/api/companion/stars"), sent);
		int asked = sent.size();

		/* A hop, then a lost connection the game gets back, each ending on a world again. */
		for (GameState state : new GameState[]{GameState.HOPPING, GameState.LOADING, GameState.LOGGED_IN,
			GameState.CONNECTION_LOST, GameState.LOADING, GameState.LOGGED_IN})
		{
			game.set(state);
			tick(stars);
			SwingUtilities.invokeAndWait(() ->
			{
				assertNotNull(state + ": the list stays", stars.model.feed);
				assertEquals(state + ": so does the pick", PICK, stars.model.selected);
				assertNull(state + ": and nothing says to log in", stars.message);
				assertTrue(state + ": the tab does not read as logged out", stars.eligible());
			});
		}
		assertEquals("nothing asked between worlds, and nothing brought forward by coming back: " + sent,
			asked, sent.size());

		/* The login screen is a logout, and clears as it always has. */
		game.set(GameState.LOGIN_SCREEN);
		tick(stars);
		SwingUtilities.invokeAndWait(() ->
		{
			assertNull("a logout clears the list", stars.model.feed);
			assertFalse(stars.eligible());
		});
		assertEquals("and asks nothing either", asked, sent.size());
	}

	@Test
	public void aFeedAskedForJustBeforeAHopStillLands() throws Exception
	{
		CountDownLatch answer = new CountDownLatch(1);
		StarsClient stars = client(answer);
		SwingUtilities.invokeAndWait(() -> stars.active(true));
		until(() -> sent.contains("/api/companion/stars"));

		game.set(GameState.HOPPING);
		tick(stars);
		answer.countDown();
		until(() -> stars.model.feed != null);
		assertEquals("the one call, not a second after the hop", 1,
			sent.stream().filter("/api/companion/stars"::equals).count());
	}

	/**
	 * The tab's client with the fixture feed behind it. {@code answer}, when given, holds the feed's
	 * reply until the test lets it go, so a call can be in flight across a change of game state.
	 */
	private StarsClient client(@Nullable CountDownLatch answer) throws IOException
	{
		String feed;
		try (InputStream in = StarsHopTest.class.getResourceAsStream("/companion-stars.sample.json"))
		{
			feed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		MemberIdentity identity = new MemberIdentity(store);
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);

		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			sent.add(path);
			if (answer != null)
			{
				hold(answer);
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(JSON, feed)).build();
		}).build();

		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return game.get();
					case "getRealSkillLevel":
						return 72;
					case "getTotalLevel":
						return 1620;
					case "getWorld":
						return 302;
					case "getWorldType":
						return EnumSet.of(WorldType.MEMBERS);
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		return new StarsClient(http, new Gson(), client, new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
	}

	private static void hold(CountDownLatch answer) throws IOException
	{
		try
		{
			if (!answer.await(5, TimeUnit.SECONDS))
			{
				throw new InterruptedIOException("the test never let the reply go");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("interrupted while holding the reply");
		}
	}

	/** One second of the open tab: the game read, then the answer handed back to Swing and run. */
	private static void tick(StarsClient stars) throws Exception
	{
		SwingUtilities.invokeAndWait(stars::tick);
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	/** Asked on the event thread, which is the one that writes what is asked about. */
	private static void until(Supplier<Boolean> condition) throws Exception
	{
		long deadline = System.currentTimeMillis() + 3_000;
		boolean[] met = new boolean[1];
		while (System.currentTimeMillis() < deadline)
		{
			SwingUtilities.invokeAndWait(() -> met[0] = condition.get());
			if (met[0])
			{
				return;
			}
			Thread.sleep(5);
		}
		assertTrue("the answer landed", met[0]);
	}
}
