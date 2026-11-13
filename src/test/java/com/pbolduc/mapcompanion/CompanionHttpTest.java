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

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Nothing the map answers is kept in RuneLite's HTTP cache, and every reply is read the one way.
 *
 * <p>RuneLite's client has a cache on disk and the plugin derives its own from it. The search's
 * words and the stream's group code and member id are in the address of a GET, which is what a
 * cache files a reply under. The map answers with no-store; a map address a player set themselves
 * might answer with anything, so the request says it.
 *
 * <p>{@link CompanionHttp#ask} and {@link CompanionHttp#read} are what Route, a farm run, a meetup,
 * the search and the star list read a reply through. The tests here hold what they share; each
 * client's own test holds its waits and its words.
 */
public class CompanionHttpTest
{
	@Test
	public void aReplyTheServerCallsCacheableIsStillNotKept() throws Exception
	{
		AtomicInteger asked = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange ->
		{
			asked.incrementAndGet();
			byte[] body = "{\"hits\":[]}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Cache-Control", "public, max-age=3600");
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		File directory = Files.createTempDirectory("companion-http-cache").toFile();
		Cache cache = new Cache(directory, 1_000_000L);
		try
		{
			OkHttpClient http = CompanionHttp.pinnedToServer(new OkHttpClient.Builder().cache(cache).build());
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/companion/search?q=abyssal+whip";
			for (int i = 0; i < 2; i++)
			{
				try (Response reply = http.newCall(CompanionHttp.member(url, "ABC234", "member", "secret").get().build()).execute())
				{
					assertEquals("{\"hits\":[]}", CompanionHttp.readBounded(reply.body()));
				}
			}
			assertEquals("the second search asked the map again", 2, asked.get());
			assertEquals("and was not answered from disk", 0, cache.hitCount());
			assertEquals("because nothing was written there", 0, cache.writeSuccessCount());
		}
		finally
		{
			server.stop(0);
			cache.delete();
			directory.delete();
		}
	}

	/** A call that never reached the map is the client's own word for that, with its own wait, and nothing is read. */
	@Test
	public void aCallThatNeverReachesTheMapIsRefusedInTheClientsOwnWords() throws Exception
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			throw new IOException("no route to host");
		}).build();
		List<String> said = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch landed = new CountDownLatch(1);
		CompanionHttp.ask(http.newCall(CompanionHttp.request("https://example.invalid/").build()), 10_000L, "unreachable", "unreadable",
			reply -> said.add("read"), (why, wait) ->
			{
				said.add(why + " " + wait);
				landed.countDown();
			});
		assertTrue(landed.await(3, TimeUnit.SECONDS));
		assertEquals(Collections.singletonList("unreachable 10000"), said);
	}

	/** A reply goes to the client that asked, whatever its status, and is closed once it has been read. */
	@Test
	public void aReplyIsTheClientsToReadAndIsClosedAfterwards()
	{
		for (int status : new int[]{200, 404, 429, 500})
		{
			AtomicBoolean closed = new AtomicBoolean();
			List<String> said = new ArrayList<>();
			CompanionHttp.read(reply(status, closed), 30_000L, "unreadable", reply ->
			{
				assertFalse("still open while it is read", closed.get());
				said.add("read " + reply.code());
			}, (why, wait) -> said.add(why + " " + wait));
			assertEquals(Collections.singletonList("read " + status), said);
			assertTrue("HTTP " + status + " is closed", closed.get());
		}
	}

	/** A reply the client cannot read, a body too long or one that is not what it should be, is its word for that and its wait. */
	@Test
	public void aReplyThatCannotBeReadIsRefusedAndStillClosed()
	{
		List<CompanionHttp.Reply> readers = Arrays.asList(
			reply ->
			{
				throw new IOException("Response too large");
			},
			reply ->
			{
				throw new IllegalStateException("not JSON");
			});
		for (CompanionHttp.Reply reader : readers)
		{
			AtomicBoolean closed = new AtomicBoolean();
			List<String> said = new ArrayList<>();
			CompanionHttp.read(reply(200, closed), 30_000L, "unreadable", reader, (why, wait) -> said.add(why + " " + wait));
			assertEquals(Collections.singletonList("unreadable 30000"), said);
			assertTrue(closed.get());
		}
	}

	/**
	 * A redirect is not followed, so it is the address that is wrong and asking again changes nothing.
	 * A meetup read one as "The map could not save that just now." and the search as "The map could
	 * not search just now.", and asked again after thirty seconds and ten; Route, a farm run and the
	 * star list already said the address redirects and waited five minutes. Now it is said here, once,
	 * for all five, and the client that asked never reads it.
	 */
	@Test
	public void aRedirectIsTheAddressesFaultForEveryClientAndWaitsFiveMinutes()
	{
		for (int status : new int[]{300, 301, 302, 303, 307, 308})
		{
			for (String retryAfter : new String[]{null, "7", "900"})
			{
				AtomicBoolean closed = new AtomicBoolean();
				List<String> said = new ArrayList<>();
				Response redirect = reply(status, closed).newBuilder().header("Location", "https://elsewhere.invalid/").build();
				if (retryAfter != null)
				{
					redirect = redirect.newBuilder().header("Retry-After", retryAfter).build();
				}
				CompanionHttp.read(redirect, 10_000L, "unreadable", reply -> said.add("read " + reply.code()),
					(why, wait) -> said.add(why + " " + wait));
				assertEquals("HTTP " + status + ", Retry-After " + retryAfter,
					Collections.singletonList(RoutePress.REDIRECTS + " 300000"), said);
				assertTrue(closed.get());
			}
		}
		assertEquals("This map address redirects. Check Map address in RuneLite's settings for this plugin.", RoutePress.REDIRECTS);

		/* Not Modified is a 3xx and no redirect: it is the client's to read, as any other status is. */
		List<String> said = new ArrayList<>();
		CompanionHttp.read(reply(304, new AtomicBoolean()), 10_000L, "unreadable", reply -> said.add("read " + reply.code()),
			(why, wait) -> said.add(why + " " + wait));
		assertEquals(Collections.singletonList("read 304"), said);
	}

	/** A reply with a body that tells {@code closed} when it is closed. */
	private static Response reply(int status, AtomicBoolean closed)
	{
		BufferedSource body = Okio.buffer(new ForwardingSource(new Buffer().writeUtf8("{}"))
		{
			@Override
			public void close() throws IOException
			{
				closed.set(true);
				super.close();
			}
		});
		Response.Builder reply = new Response.Builder().request(new Request.Builder().url("https://example.invalid/").build())
			.protocol(Protocol.HTTP_1_1).code(status).message("")
			.body(ResponseBody.create(MediaType.get("application/json"), 2L, body));
		return reply.build();
	}
}
