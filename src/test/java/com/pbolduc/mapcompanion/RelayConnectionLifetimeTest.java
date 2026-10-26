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
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Replies can already be queued when cancellation happens. A manually delivered OkHttp callback
 * exercises that order without a socket, so cancellation alone cannot accidentally pass these tests.
 */
public class RelayConnectionLifetimeTest
{
	@Test
	public void aPreviousGroupsRefusalCannotDisableTheReplacement() throws IOException
	{
		HeldClient client = new HeldClient();
		RelayClient relay = new RelayClient(client, new Gson());
		assertTrue(post(relay, "ABC234", ignored -> { }));
		HeldCall old = client.calls.get(0);

		relay.reset();
		assertTrue(old.isCanceled());
		assertTrue("a new group need not wait for the old request to finish", post(relay, "DEF567", ignored -> { }));
		old.reply(410, "{\"reason\":\"closed\"}");

		assertNull(relay.getFatalError());
		assertFalse("the old callback cannot free the new request's slot", relay.isReady(System.currentTimeMillis()));
		client.calls.get(1).reply(200, "{\"pollIntervalMs\":2000}");
		assertTrue(relay.isEverSucceeded());
	}

	@Test
	public void aPreviousSuccessCannotDeliverClaimsOrDirectives() throws IOException
	{
		HeldClient client = new HeldClient();
		RelayClient relay = new RelayClient(client, new Gson());
		AtomicInteger deliveries = new AtomicInteger();
		assertTrue(post(relay, "ABC234", ignored -> deliveries.incrementAndGet()));
		relay.reset();
		client.calls.get(0).reply(200, "{\"claimed\":true,\"pollIntervalMs\":90000,\"directive\":{}}");

		assertEquals(0, deliveries.get());
		assertFalse(relay.isEverSucceeded());
		assertEquals(0L, relay.getNextAllowedPostMs());
		assertTrue(relay.isReady(System.currentTimeMillis()));
	}

	@Test
	public void aCancelledFailuresBackoffDoesNotFollowTheNextConnection() throws IOException
	{
		HeldClient client = new HeldClient();
		RelayClient relay = new RelayClient(client, new Gson());
		assertTrue(post(relay, "ABC234", ignored -> { }));
		relay.reset();
		assertTrue(post(relay, "ABC234", ignored -> { }));
		client.calls.get(0).fail();

		assertEquals(0, relay.getConsecutiveFailures());
		assertEquals(0L, relay.getNextAllowedPostMs());
		assertFalse(relay.isReady(System.currentTimeMillis()));
		client.calls.get(1).reply(200, "{}");
		assertTrue(relay.isEverSucceeded());
	}

	private static boolean post(RelayClient relay, String code, java.util.function.Consumer<RelayResponse> callback)
	{
		return relay.post("https://example.invalid", code, null, null, new CompanionSnapshot(), callback);
	}

	private static final class HeldClient extends OkHttpClient
	{
		private final List<HeldCall> calls = new ArrayList<>();

		/**
		 * A client that already refuses redirects is used as given; one that follows them is copied
		 * with them off, and a copy would not hold these calls (see {@link CompanionHttp}).
		 */
		@Override
		public boolean followRedirects()
		{
			return false;
		}

		@Override
		public boolean followSslRedirects()
		{
			return false;
		}

		@Override
		public Call newCall(Request request)
		{
			HeldCall call = new HeldCall(request);
			calls.add(call);
			return call;
		}
	}

	private static final class HeldCall implements Call
	{
		private final Request request;
		private Callback callback;
		private boolean cancelled;

		private HeldCall(Request request)
		{
			this.request = request;
		}

		private void reply(int status, String body) throws IOException
		{
			callback.onResponse(this, new Response.Builder()
								.request(request).protocol(Protocol.HTTP_1_1).code(status).message("")
								.body(ResponseBody.create(MediaType.get("application/json"), body)).build());
		}

		private void fail()
		{
			callback.onFailure(this, new IOException("cancelled"));
		}

		@Override
		public Request request()
		{
			return request;
		}

		@Override
		public Response execute()
		{
			throw new UnsupportedOperationException("the relay must enqueue");
		}

		@Override
		public void enqueue(Callback callback)
		{
			this.callback = callback;
		}

		@Override
		public void cancel()
		{
			cancelled = true;
		}

		@Override
		public boolean isExecuted()
		{
			return callback != null;
		}

		@Override
		public boolean isCanceled()
		{
			return cancelled;
		}

		@Override
		public Timeout timeout()
		{
			return Timeout.NONE;
		}

		@Override
		public Call clone()
		{
			return new HeldCall(request);
		}
	}
}
