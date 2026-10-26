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
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

/**
 * A redirect never carries the group's credentials to a host the player did not approve.
 *
 * <p>Two real servers on loopback, because the behaviour under test lives inside OkHttp's own
 * follow-up handling, below anything an interceptor can stand in for. The approved one answers a
 * snapshot post with a 302 naming the other; the other counts what reaches it, and must count
 * nothing.
 *
 * <p>A 302 rather than a 307: OkHttp will not replay a POST body on a 307, but it turns a POST
 * answered with 302 into a GET that keeps every header except {@code Authorization}, so it is the
 * redirect that would actually carry the member secret away.
 */
public class RedirectRefusalTest
{
	private HttpServer approved;
	private HttpServer elsewhere;

	@After
	public void stop()
	{
		if (approved != null)
		{
			approved.stop(0);
		}
		if (elsewhere != null)
		{
			elsewhere.stop(0);
		}
	}

	@Test
	public void aSnapshotPostDoesNotFollowARedirectToAnotherHost() throws Exception
	{
		AtomicInteger reachedElsewhere = new AtomicInteger();
		elsewhere = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		elsewhere.createContext("/", exchange ->
		{
			reachedElsewhere.incrementAndGet();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		elsewhere.start();

		CountDownLatch askedApproved = new CountDownLatch(1);
		approved = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		approved.createContext("/", exchange ->
		{
			exchange.getResponseHeaders().add("Location",
				"http://localhost:" + elsewhere.getAddress().getPort() + "/api/companion/snapshot");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
			askedApproved.countDown();
		});
		approved.start();

		RelayClient relay = new RelayClient(new OkHttpClient(), new Gson());
		String server = "http://127.0.0.1:" + approved.getAddress().getPort();
		CountDownLatch answered = new CountDownLatch(1);
		assertTrue(relay.post(server, "ABC234", null, "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23",
			new CompanionSnapshot(), reply -> answered.countDown()));

		assertTrue("the approved server was never asked", askedApproved.await(5, TimeUnit.SECONDS));
		/** Long enough for a followed redirect to have landed; a refused one never will. */
		answered.await(2, TimeUnit.SECONDS);
		assertEquals(0, reachedElsewhere.get());
	}

	@Test
	public void everyDerivedClientRefusesToFollow()
	{
		OkHttpClient pinned = CompanionHttp.pinnedToServer(new OkHttpClient());
		assertTrue(!pinned.followRedirects() && !pinned.followSslRedirects());
	}
}
