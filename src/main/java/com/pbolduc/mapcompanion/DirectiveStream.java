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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * A held connection that receives what the map wants this client to know.
 *
 * <p>Destinations and meetups used to arrive on the reply to a post, which meant the heartbeat was
 * doing two jobs: keeping this member's dot alive on the map, and being the only moment anything
 * could be delivered. The second job set the pace — the heartbeat had to be short because
 * <i>nothing else</i> could bring a destination in — and a client standing still was posting every
 * six seconds mostly in order to listen.
 *
 * <p>With a stream the two jobs separate. Delivery is instant, and the heartbeat goes back to
 * being about liveness alone, so it can be slow. That is fewer requests <em>and</em> lower latency,
 * which is the unusual case where the more capable design is also the cheaper one.
 *
 * <p><b>Precedent, since a held connection from a game client deserves a note:</b> RuneLite's own
 * Party plugin keeps an OkHttp WebSocket open to a party server. A stream from here is the same
 * shape of thing and rather less capable — it only receives.
 *
 * <p><b>Why the protocol is hand-rolled.</b> Server-sent events are a line-oriented text format:
 * `data: ` lines, blank line ends a message, `:` is a comment. Reading that off a
 * {@link BufferedSource} is the thirty lines below and adds no dependency, which matters for a
 * Plugin Hub submission where every added artifact is something a reviewer has to accept.
 *
 * <p><b>What proves who is listening.</b> The group's code is in the query and the member's id is
 * beside it, and neither is a secret — every member's id is published to every other member on
 * every read. So a group the map keeps refuses this connection outright unless it also carries
 * {@code X-Companion-Member-Secret}, the credential the map issued this member on a snapshot
 * reply. A header is available here where it would not be to a browser: this is an OkHttp request
 * and not an {@code EventSource}, which is the same reason the code is in the query for the map's
 * own tab and need not be for this.
 *
 * <p><b>Inert without a pairing code</b>, exactly like {@link RelayClient}: {@link #start} refuses
 * to open anything when the code is blank, so the promise made in the README holds here too.
 */
@Slf4j
@Singleton
public class DirectiveStream
{
	/**
	 * The credential that says which member of the group is listening.
	 *
	 * <p>Spelled here as well as in {@link RelayClient} rather than shared, because these are two
	 * different doors that happen to want the same proof, and a constant borrowed across them would
	 * make it look as though one class was reaching into the other's protocol. The map's name for
	 * it is the same on all three: {@code X-Companion-Member-Secret}.
	 */
	private static final String MEMBER_SECRET_HEADER = "X-Companion-Member-Secret";

	/** Backoff after a dropped connection. Deliberately unhurried: the poll still works. */
	private static final long RECONNECT_DELAY_MS = 10_000L;

	/**
	 * The ceiling on that backoff once drops keep happening.
	 *
	 * <p>A server that refuses this endpoint outright — an older deployment of the map, a proxy in
	 * the way — refuses it every time, and retrying a flat ten seconds forever is a request every ten
	 * seconds forever for a connection that is never going to open. Doubling up to five minutes keeps
	 * a brief restart cheap to recover from while a permanent refusal costs almost nothing.
	 */
	private static final long MAX_RECONNECT_DELAY_MS = 5 * 60_000L;

	/**
	 * Three of the map's twenty-second keep-alive comments. A destination arrives rarely, so the
	 * shared client's ordinary timeout would sever a healthy stream, but with no deadline at all a
	 * connection that died without a reset — a laptop asleep, a Wi-Fi switch, a NAT that forgot it
	 * — blocked its read for ever on HTTP/1.1, where nothing pings it, and the group's dots froze.
	 */
	private static final long READ_TIMEOUT_MS = 60_000L;

	/** Longest stream line read. A frame for a full group is a few kilobytes. */
	static final long MAX_LINE_BYTES = 262_144L;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ReconnectScheduler reconnectScheduler;

	private final AtomicBoolean running = new AtomicBoolean(false);

	/** One reconnect per connection lifetime; stop cancels it and invalidates its generation. */
	@Nullable
	private Future<?> reconnectTask;
	private long generation;

	/** Consecutive failed opens, which is what the backoff grows on. Reset by a good connection. */
	private final AtomicInteger failures = new AtomicInteger(0);

	private volatile Call call;
	private volatile String activeKey;

	/** True while a stream is actually delivering, which is what lets the heartbeat relax. */
	@Getter
	private volatile boolean connected = false;

	/**
	 * Package-private rather than private, for the reason {@link RelayClient}'s is: a test can hand
	 * an {@link OkHttpClient} carrying an interceptor and read the request this class would send
	 * without a socket, a server or a client. Guice does not care either way.
	 */
	@Inject
	DirectiveStream(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor)
	{
		this(httpClient, gson, (action, delay) -> executor.schedule(action, delay, TimeUnit.MILLISECONDS));
	}

	/** Tests can deliver a queued task even after its cancellation. */
	DirectiveStream(OkHttpClient httpClient, Gson gson, ReconnectScheduler reconnectScheduler)
	{
		this.httpClient = CompanionHttp.pinnedToServer(httpClient);
		this.gson = gson;
		this.reconnectScheduler = reconnectScheduler;
	}

	@FunctionalInterface
	interface ReconnectScheduler
	{
		Future<?> schedule(Runnable action, long delayMs);
	}

	/**
	 * Open, or re-open, the stream for this pairing.
	 *
	 * <p>Called whenever the code or the map address changes, and after a drop. A stream already
	 * running for the same pairing is left alone rather than restarted, so the ordinary tick can
	 * call this freely.
	 *
	 * <p><b>The secret is part of what counts as the same pairing</b>, which is what makes this
	 * heal itself the moment one arrives. A group the map keeps refuses a stream with no secret,
	 * and the secret turns up a second or two later on a snapshot reply; without it in the key the
	 * connection that was refused would sit on its back-off — growing to five minutes — holding a
	 * credential that would have been accepted. The key is compared and never logged, here or
	 * anywhere else in this class.
	 */
	public synchronized void start(
		String baseUrl,
		String code,
		String memberId,
		@Nullable String memberSecret,
		Consumer<? super RelayResponse.StreamDirective> onDirective)
	{
		start(baseUrl, code, memberId, memberSecret, false, onDirective);
	}

	/**
	 * The same, and asking for the other members' dots as well when {@code withMembers}.
	 *
	 * <p>Part of the key, so turning <i>Show my group on the world map</i> on or off reopens the
	 * connection with the question changed rather than waiting for the next drop to ask it.
	 */
	public synchronized void start(
		String baseUrl,
		String code,
		String memberId,
		@Nullable String memberSecret,
		boolean withMembers,
		Consumer<? super RelayResponse.StreamDirective> onDirective)
	{
		if (code == null || code.isEmpty())
		{
			/** No code, no connection. The same guarantee RelayClient makes about posting. */
			stop();
			return;
		}

		String secret = JoinCode.isWellFormedSecret(memberSecret) ? memberSecret : null;
		String key = baseUrl + "|" + code + "|" + memberId + "|" + (secret == null ? "" : secret)
			+ (withMembers ? "|members" : "");
		if (running.get() && key.equals(activeKey))
		{
			return;
		}

		stop();
		activeKey = key;

		HttpUrl url = streamUrl(baseUrl, code, memberId, withMembers);
		if (url == null)
		{
			return;
		}

		running.set(true);
		open(url, generation, secret, onDirective);
	}

	public synchronized void stop()
	{
		generation++;
		running.set(false);
		connected = false;
		activeKey = null;
		Future<?> pending = reconnectTask;
		reconnectTask = null;
		if (pending != null)
		{
			pending.cancel(false);
		}
		failures.set(0);
		Call current = call;
		call = null;
		if (current != null)
		{
			current.cancel();
		}
	}

	/**
	 * Open one stream for this lifetime. A generation rather than the pairing text distinguishes
	 * stopping and restarting the same group; late callbacks must not affect the replacement.
	 */
	private synchronized void open(
		HttpUrl url, long streamGeneration, @Nullable String secret, Consumer<? super RelayResponse.StreamDirective> onDirective)
	{
		if (!isCurrent(streamGeneration))
		{
			return;
		}
		Request.Builder builder = new Request.Builder()
		.url(url)
		.header("Accept", "text/event-stream")
		.header("User-Agent", CompanionVersion.USER_AGENT);
		if (secret != null)
		{
			/** Already checked in {@link #start}; a stream is not the place to re-decide it. */
			builder.header(MEMBER_SECRET_HEADER, secret);
		}
		Request request = builder.build();

		/** A client of its own, for a read deadline measured in keep-alives; see READ_TIMEOUT_MS. */
		OkHttpClient streaming = httpClient.newBuilder()
								 .readTimeout(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
								 .retryOnConnectionFailure(true)
								 .build();

		Call started = streaming.newCall(request);
		call = started;

		started.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call c, IOException e)
			{
				synchronized (DirectiveStream.this)
				{
					if (!isCurrent(streamGeneration))
					{
						return;
					}
					connected = false;
					log.debug("companion stream unreachable", e);
					scheduleReconnect(url, streamGeneration, secret, onDirective);
				}
			}

			/**
			 * One exit, one reconnect.
			 *
			 * <p>A refused stream leaves posting available. Every exit schedules through the finally
			 * block so a refusal cannot queue a second reconnect alongside the ordinary close path.
			 */
			@Override
			public void onResponse(Call c, Response response)
			{
				try
					(Response r = response)
				{
					if (!r.isSuccessful())
					{
						/**
						 * Including the 403 a group the map keeps answers when the secret is missing
						 * or wrong, which is deliberately <em>not</em> given a retry of its own. The
						 * map issues a secret on a snapshot reply, and a plugin that has just joined
						 * is refused here for the second or two before one arrives — so a faster
						 * retry would spend requests on the one case that is about to fix itself,
						 * and would go on spending them at that rate against a map that never
						 * issues one. The growing back-off is right for both, and the arrival of a
						 * secret reopens this from {@link #start} without waiting for it.
						 */
						log.debug("companion stream refused: {}", r.code());
						return;
					}
					synchronized (DirectiveStream.this)
					{
						if (!isCurrent(streamGeneration))
						{
							return;
						}
						connected = true;
					}
					read(r, streamGeneration, onDirective);
				}
				catch (Exception e)
				{
					log.debug("companion stream ended", e);
				}
				finally
				{
					synchronized (DirectiveStream.this)
					{
						if (isCurrent(streamGeneration))
						{
							connected = false;
							scheduleReconnect(url, streamGeneration, secret, onDirective);
						}
					}
				}
			}
		});
	}

	/**
	 * The whole of the SSE format this needs.
	 *
	 * <p>Lines beginning `data: ` carry a message; a `:` line is a comment the server sends to
	 * keep the socket warm; anything else — `retry:`, `event:`, blank lines — is not used here and
	 * is skipped rather than parsed, because reading a field this plugin does not act on would be
	 * inventing a contract.
	 */
	private void read(Response response, long streamGeneration, Consumer<? super RelayResponse.StreamDirective> onDirective)
	throws IOException
	{
		ResponseBody body = response.body();
		if (body == null)
		{
			return;
		}
		BufferedSource source = body.source();
		boolean heard = false;

		while (isCurrent(streamGeneration) && !source.exhausted())
		{
			/* A frame is one group's dots; a line this long is a server that never sent a newline. */
			String line = source.readUtf8LineStrict(MAX_LINE_BYTES);
			if (!heard)
			{
				/*
				 * The back-off resets on the first line, a frame or the map's keep-alive comment,
				 * not on the 200. A proxy or an old map that answers 200 and closes at once would
				 * otherwise hold the retry at ten seconds forever.
				 */
				heard = true;
				failures.set(0);
			}
			if (line == null || !line.startsWith("data: "))
			{
				continue;
			}
			String json = line.substring(6).trim();
			if (json.isEmpty())
			{
				continue;
			}
			try
			{
				RelayResponse.StreamDirective directive =
					gson.fromJson(json, RelayResponse.StreamDirective.class);
				synchronized (this)
				{
					if (isCurrent(streamGeneration) && directive != null)
					{
						onDirective.accept(directive);
					}
				}
			}
			catch (RuntimeException e)
			{
				/** A frame this build cannot read is dropped; the next one is independent. */
				log.debug("unreadable directive frame", e);
			}
		}
	}

	/**
	 * Queue exactly one reconnect, after a delay that grows while failures keep coming.
	 *
	 * <p>Use RuneLite's shared scheduler without blocking it. Cancellation avoids a pending retry;
	 * the generation also rejects a task that had already started when stop was called.
	 */
	private synchronized void scheduleReconnect(
		HttpUrl url, long streamGeneration, @Nullable String secret, Consumer<? super RelayResponse.StreamDirective> onDirective)
	{
		if (!isCurrent(streamGeneration) || reconnectTask != null)
		{
			return;
		}

		long delay = backoffMs(failures.incrementAndGet());
		reconnectTask = reconnectScheduler.schedule(() ->
		{
			synchronized (DirectiveStream.this)
			{
				if (!isCurrent(streamGeneration))
				{
					return;
				}
				reconnectTask = null;
				open(url, streamGeneration, secret, onDirective);
			}
		}, delay);
	}

	private synchronized boolean isCurrent(long streamGeneration)
	{
		return running.get() && generation == streamGeneration;
	}

	/**
	 * Ten seconds, doubling, capped — and never overflowing however long this has been failing.
	 *
	 * <p>Package-private so the reconnect cadence can be tested without a socket or a long wait.
	 */
	static long backoffMs(int consecutiveFailures)
	{
		int steps = Math.min(Math.max(consecutiveFailures - 1, 0), 16);
		return Math.min(RECONNECT_DELAY_MS << steps, MAX_RECONNECT_DELAY_MS);
	}

	@Nullable
	static HttpUrl streamUrl(String baseUrl, String code, String memberId, boolean withMembers)
	{
		HttpUrl parsed = HttpUrl.parse(baseUrl == null ? "" : baseUrl.trim());
		if (parsed == null)
		{
			return null;
		}
		HttpUrl.Builder url = parsed.newBuilder()
			   .addPathSegments("api/companion/stream")
			   /**
			    * The code rides in the query here, which is the one place this feature allows it —
			    * see the stream route's own note. Same-origin, our own log, and a code is rotatable
			    * by design. The member secret does not: it is not rotatable, it is the one thing here
			    * that proves anything, and an access log is exactly where a standing credential must
			    * not end up. It goes in a header, which this can send and a browser's EventSource
			    * cannot.
			    */
			   .addQueryParameter("code", code)
			   .addQueryParameter("for", memberId);
		if (withMembers)
		{
			/**
			 * Asked only while the world-map setting is on. Without it the map sends this connection
			 * the two directive fields it always has, and nobody's position.
			 */
			url.addQueryParameter("members", "1");
		}
		return url.build();
	}
}
