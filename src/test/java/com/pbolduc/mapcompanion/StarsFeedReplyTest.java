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
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the Stars tab says, and how long it waits before it asks for the list again, for each kind
 * of reply to {@code GET /api/companion/stars}.
 *
 * <p>The list is asked for on a timer and not by a press, so its waits are the plugin's whole
 * request rate to the map while the tab is open. It never asks again inside a minute, whatever the
 * reply was, and a wait the map names stands when it is the longer.
 *
 * <p>Driven through real OkHttp calls answered by an interceptor, as {@link StarsHopTest} is.
 */
public class StarsFeedReplyTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String UNAVAILABLE = "Stars unavailable. Retrying shortly.";

	@Test
	public void eachKindOfReplyKeepsItsOwnWaitAndWords() throws Exception
	{
		Object[][] cases = {
			/* One sentence for every status that is no list: the feed is asked without a member, so none means "not in the group". */
			{500, null, "oops", UNAVAILABLE, 60_000L},
			{429, "7", "{}", UNAVAILABLE, 60_000L},
			{429, "200", "{}", UNAVAILABLE, 200_000L},
			{403, null, "{}", UNAVAILABLE, 60_000L},
			/* A redirect is the address's fault and no retry changes it: five minutes, whatever it names. */
			{302, null, "", Words.of("map.redirects"), 300_000L},
			{302, "7", "", Words.of("map.redirects"), 300_000L},
			/* No reply at all. */
			{0, null, "", "The map is unreachable.", 60_000L},
			{200, null, "not json at all", RoutePress.UNREADABLE, 60_000L},
			{200, "200", "not json at all", RoutePress.UNREADABLE, 200_000L},
		};
		for (Object[] c : cases)
		{
			StarsClient stars = client((int) c[0], (String) c[2], (String) c[1]);
			long before = System.currentTimeMillis();
			SwingUtilities.invokeAndWait(() -> stars.active(true));
			until(() -> stars.message != null);
			String name = "HTTP " + c[0] + ", Retry-After " + c[1];
			SwingUtilities.invokeAndWait(() ->
			{
				assertEquals(name, c[3], stars.message);
				assertNull(name + " leaves no list", stars.model.feed);
				RoutePressTest.assertWait(name, (long) c[4], stars.nextFeed - before);
			});
		}
	}

	@Test
	public void aListIsAskedForAgainNoSoonerThanAMinuteLater() throws Exception
	{
		String feed;
		try (InputStream in = StarsFeedReplyTest.class.getResourceAsStream("/companion-stars.sample.json"))
		{
			feed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		StarsClient stars = client(200, feed, "7");
		long before = System.currentTimeMillis();
		SwingUtilities.invokeAndWait(() -> stars.active(true));
		until(() -> stars.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			assertNotNull(stars.model.feed);
			assertNull("a list that landed says nothing", stars.message);
			assertTrue("the feed's own interval, never under a minute", stars.nextFeed - before >= 60_000L);
			assertTrue("nor over five", stars.nextFeed - before < 303_000L);
		});
	}

	/** The tab's client with one reply behind it; a status of 0 is a call that never reaches the map. */
	private static StarsClient client(int status, String body, @Nullable String retryAfter)
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		MemberIdentity identity = new MemberIdentity(store);
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);

		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			if (status == 0)
			{
				throw new IOException("no route to host");
			}
			Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body));
			if (retryAfter != null)
			{
				reply.header("Retry-After", retryAfter);
			}
			return reply.build();
		}).build();

		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return GameState.LOGGED_IN;
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
