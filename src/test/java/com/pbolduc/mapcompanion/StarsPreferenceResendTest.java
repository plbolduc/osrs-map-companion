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
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * An edit the map never confirms is re-sent a few times quickly, then waits for the ordinary sync.
 *
 * <p>The map answers every write with its revision and no preferences, which leaves the edit
 * unconfirmed. The Stars tab's own refresh runs every second, so before the limit each refresh
 * past the 300 ms retry sent the same filters again for as long as the tab stayed open.
 */
public class StarsPreferenceResendTest
{
	private static final MediaType JSON = MediaType.get("application/json");

	@Test
	public void anUnconfirmedEditIsResentAtMostThreeTimesQuickly() throws Exception
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		MemberIdentity identity = new MemberIdentity(store);
		pairing.rememberSecret(pairing.code(), identity.id(), "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23");

		AtomicInteger writes = new AtomicInteger();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String path = chain.request().url().encodedPath();
			String body;
			if (path.endsWith("/star-preferences"))
			{
				boolean put = "PUT".equals(chain.request().method());
				if (put)
				{
					writes.incrementAndGet();
				}
				body = put ? "{\"revision\":1}" : "{\"revision\":0,\"preferences\":{\"filters\":{},\"favorites\":[]}}";
			}
			else
			{
				body = "{\"rows\":[],\"pollIntervalMs\":60000}";
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(JSON, body)).build();
		}).build();

		StarsClient stars = new StarsClient(http, new Gson(), loggedIn(), new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);

		SwingUtilities.invokeAndWait(() -> stars.active(true));
		long deadline = System.currentTimeMillis() + 3_000;
		while (!stars.syncReady && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(20);
			SwingUtilities.invokeAndWait(stars::tick);
		}
		assertTrue("the first read settles the revision", stars.syncReady);

		SwingUtilities.invokeAndWait(stars::changed);
		long end = System.currentTimeMillis() + 3_000;
		while (System.currentTimeMillis() < end)
		{
			Thread.sleep(100);
			SwingUtilities.invokeAndWait(stars::tick);
		}
		assertTrue("one write and three quick re-sends at most, not " + writes.get(), writes.get() <= 4);
		assertTrue("the edit is still sent", writes.get() >= 1);
	}

	private static Client loggedIn()
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return GameState.LOGGED_IN;
					case "getRealSkillLevel":
						return 70;
					case "getTotalLevel":
						return 1500;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
