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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Nothing the map answers is kept in RuneLite's HTTP cache.
 *
 * <p>RuneLite's client has a cache on disk and the plugin derives its own from it. The search's
 * words and the stream's group code and member id are in the address of a GET, which is what a
 * cache files a reply under. The map answers with no-store; a map address a player set themselves
 * might answer with anything, so the request says it.
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
}
