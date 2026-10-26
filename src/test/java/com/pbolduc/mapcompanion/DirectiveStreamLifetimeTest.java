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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;
import okio.Pipe;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A restarted pairing is a new lifetime even when its code is unchanged. Real OkHttp interceptors
 * and held response bodies reproduce callbacks and queued retries that outlive stop without sockets.
 */
public class DirectiveStreamLifetimeTest
{
	@Test
	public void aScheduledRetryRecoversFromARefusedStream() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		AtomicInteger retries = new AtomicInteger();
		AtomicReference<Runnable> retry = new AtomicReference<>();
		CountDownLatch scheduled = new CountDownLatch(1);
		Pipe replacement = new Pipe(4096);
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
			requests.incrementAndGet() == 1
				? reply(chain, 503, ResponseBody.create(MediaType.get("text/plain"), "restart"))
				: reply(chain, 200, body(replacement))).build();
		DirectiveStream stream = new DirectiveStream(client, new Gson(), (action, delay) ->
		{
			assertEquals(10_000L, delay);
			retries.incrementAndGet();
			retry.set(action);
			scheduled.countDown();
			return new FutureTask<>(action, null);
		});
		try
		{
			start(stream, ignored -> { });
			assertTrue(scheduled.await(3, TimeUnit.SECONDS));
			assertEquals(1, requests.get());
			retry.get().run();
			await(() -> requests.get() == 2 && stream.isConnected());
			assertEquals("one refusal queues one retry", 1, retries.get());
		}
		finally
		{
			stream.stop();
			replacement.sink().close();
			client.dispatcher().executorService().shutdown();
		}
	}

	@Test
	public void aWakingOldReconnectCannotOpenAlongsideTheReplacement() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		Pipe replacement = new Pipe(4096);
		CountDownLatch scheduled = new CountDownLatch(1);
		AtomicReference<Runnable> oldRetry = new AtomicReference<>();
		AtomicReference<FutureTask<Void>> oldTask = new AtomicReference<>();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			if (requests.incrementAndGet() == 1)
			{
				return reply(chain, 503, ResponseBody.create(MediaType.get("text/plain"), "restart"));
			}
			return reply(chain, 200, body(replacement));
		}).build();
		DirectiveStream stream = new DirectiveStream(client, new Gson(), (action, delay) ->
		{
			assertEquals(10_000L, delay);
			oldRetry.set(action);
			FutureTask<Void> task = new FutureTask<>(action, null);
			oldTask.set(task);
			scheduled.countDown();
			return task;
		});
		try
		{
			start(stream, ignored -> { });
			assertTrue(scheduled.await(3, TimeUnit.SECONDS));
			stream.stop();
			assertTrue(oldTask.get().isCancelled());
			start(stream, ignored -> { });
			await(() -> requests.get() == 2 && stream.isConnected());
			// Cancellation cannot prevent a callback that was already dequeued from running.
			oldRetry.get().run();
			assertEquals("a restart owns exactly one replacement stream", 2, requests.get());
			assertTrue(stream.isConnected());
		}
		finally
		{
			stream.stop();
			replacement.sink().close();
			client.dispatcher().executorService().shutdown();
		}
	}

	@Test
	public void anOldBodyCannotDeliverOrDisconnectTheReplacement() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		AtomicInteger staleDeliveries = new AtomicInteger();
		Pipe old = new Pipe(4096);
		Pipe replacement = new Pipe(4096);
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
			reply(chain, 200, body(requests.incrementAndGet() == 1 ? old : replacement))).build();
		DirectiveStream stream = new DirectiveStream(client, new Gson(),
			(action, delay) -> new FutureTask<>(action, null));
		try
		{
			start(stream, ignored -> staleDeliveries.incrementAndGet());
			await(stream::isConnected);
			stream.stop();
			start(stream, ignored -> { });
			await(() -> requests.get() == 2 && stream.isConnected());
			okio.BufferedSink writer = Okio.buffer(old.sink());
			writer.writeUtf8("data: {}\n\n");
			writer.close();
			await(() -> client.dispatcher().runningCallsCount() == 1);
			assertEquals(0, staleDeliveries.get());
			assertTrue("the old body's finally must not disconnect the new stream", stream.isConnected());
		}
		finally
		{
			stream.stop();
			old.sink().close();
			replacement.sink().close();
			client.dispatcher().executorService().shutdown();
		}
	}

	/** A 200 that closes before a word is not a connection the back-off may forget failures for. */
	@Test
	public void anEmptySuccessKeepsBackingOffAndTheFirstLineResetsIt() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		java.util.concurrent.LinkedBlockingQueue<Long> delays = new java.util.concurrent.LinkedBlockingQueue<>();
		java.util.concurrent.LinkedBlockingQueue<Runnable> retries = new java.util.concurrent.LinkedBlockingQueue<>();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
			reply(chain, 200, ResponseBody.create(MediaType.get("text/event-stream"),
				requests.incrementAndGet() == 3 ? ": keep-alive" + (char) 10 : ""))).build();
		DirectiveStream stream = new DirectiveStream(client, new Gson(), (action, delay) ->
		{
			delays.add(delay);
			retries.add(action);
			return new FutureTask<>(action, null);
		});
		try
		{
			start(stream, ignored -> { });
			assertEquals(Long.valueOf(10_000L), delays.poll(3, TimeUnit.SECONDS));
			retries.take().run();
			assertEquals("an empty 200 is still a failure", Long.valueOf(20_000L), delays.poll(3, TimeUnit.SECONDS));
			retries.take().run();
			assertEquals("a line from the map resets it", Long.valueOf(10_000L), delays.poll(3, TimeUnit.SECONDS));
		}
		finally
		{
			stream.stop();
			client.dispatcher().executorService().shutdown();
		}
	}

	private static void start(DirectiveStream stream, java.util.function.Consumer<RelayResponse.Directive> callback)
	{
		stream.start("https://example.invalid", "ABC234", "member", null, callback);
	}

	private static ResponseBody body(Pipe pipe)
	{
		return ResponseBody.create(MediaType.get("text/event-stream"), -1, Okio.buffer(pipe.source()));
	}

	private static Response reply(Interceptor.Chain chain, int status, ResponseBody body)
	{
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
			   .code(status).message("").body(body).build();
	}

	private static void await(BooleanSupplier condition) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
		{
			Thread.sleep(5);
		}
		assertTrue("the held callback did not reach its expected state", condition.getAsBoolean());
	}
}
