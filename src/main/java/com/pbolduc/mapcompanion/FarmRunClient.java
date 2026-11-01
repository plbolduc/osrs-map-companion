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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Explicit Start run, Reroute and End run presses, with no automatic planning requests.
 *
 * <p>The game thread supplies credentials and the current tile. OkHttp reads bounded replies
 * outside the reset lock; a connection generation and a call identity reject old callbacks.
 * Planning shares the destination router's one-at-a-time rule and wait. End is allowed while
 * planning: it cancels that call and tells the server to discard a plan that has not landed yet.
 */
@Singleton
final class FarmRunClient
{
	static final String PATH = "/api/companion/run";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final FarmRunState state;
	private final DestinationState destinations;
	private final DestinationRoute gate;
	private Call call;
	private Ticket active;
	private volatile Said said;

	static final class Said
	{
		final boolean planning;
		final String message;
		final long lifetime;

		Said(boolean planning, @Nullable String message, long lifetime)
		{
			this.planning = planning;
			this.message = message;
			this.lifetime = lifetime;
		}
	}

	private static final class Ticket
	{
		final long lifetime;
		final boolean end;
		final boolean holdsGate;
		final String member;

		Ticket(long lifetime, boolean end, boolean holdsGate, String member)
		{
			this.lifetime = lifetime;
			this.end = end;
			this.holdsGate = holdsGate;
			this.member = member;
		}
	}

	static final class Question
	{
		String start;
		Reroute reroute;
		RouteClient.Tile from;
	}

	static final class Reroute
	{
		String id;
		List<Integer> done;
		List<Integer> visited;
	}

	@Inject
	FarmRunClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
				  MemberIdentity identity, FarmRunState state, DestinationState destinations, DestinationRoute gate)
	{
		this.http = CompanionHttp.pinnedToServer(http).newBuilder()
					.readTimeout(60, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build();
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.pairing = pairing;
		this.identity = identity;
		this.state = state;
		this.destinations = destinations;
		this.gate = gate;
	}

	void start(String kind)
	{
		clientThread.invokeLater(() -> press(kind, false, false));
	}

	void reroute()
	{
		clientThread.invokeLater(() -> press(null, true, false));
	}

	void end()
	{
		clientThread.invokeLater(() -> press(null, false, true));
	}

	synchronized boolean ready()
	{
		return active == null && gate.pressReady(System.currentTimeMillis());
	}

	synchronized boolean endingRequest()
	{
		return active != null && active.end;
	}

	@Nullable
	Said said()
	{
		Said current = said;
		return current != null && current.lifetime == destinations.generation() ? current : null;
	}

	/** Client thread, including the local stop; nothing reads a game tile in a network callback. */
	private void press(@Nullable String start, boolean reroute, boolean end)
	{
		synchronized (this)
		{
			// End may cancel a planner, but a repeated press must leave its deletion in flight.
			if (active != null && active.end)
			{
				return;
			}
		}
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		if (member != null)
		{
			state.bind(member);
		}
		FarmRunState.View current = state.view();
		if (reroute && current == null)
		{
			return;
		}
		String id = current == null ? null : current.run.id;
		if (end)
		{
			state.end(id == null);
		}
		String secret = member == null ? null : pairing.secret(server, code, member);
		long lifetime = destinations.generation();
		boolean connected = client.getGameState() == GameState.LOGGED_IN && server != null && !code.isEmpty()
							&& pairing.hasServerConsent(server, code) && secret != null && HttpUrl.parse(server + PATH) != null;
		Question question = new Question();
		question.start = start;
		if (reroute)
		{
			question.reroute = new Reroute();
			question.reroute.id = id;
			question.reroute.done = new ArrayList<>(current.done);
			question.reroute.visited = new ArrayList<>(current.visited);
		}
		WorldPoint where = connected ? DestinationRoute.standing(client) : null;
		question.from = where == null ? null : new RouteClient.Tile(where);

		Ticket ticket;
		synchronized (this)
		{
			boolean held = false;
			if (end && active != null)
			{
				held = active.holdsGate;
				active = null;
				if (call != null)
				{
					call.cancel();
				}
				call = null;
			}
			else if (active != null)
			{
				return;
			}
			if (!connected)
			{
				if (held)
				{
					gate.finishPress(System.currentTimeMillis() + RouteClient.MIN_GAP_MS);
				}
				said = new Said(false, end ? "Run ended here. Connect to end it on the map too." : RouteClient.NEEDS_CONNECTION, lifetime);
				return;
			}
			if (!held)
			{
				String refused = gate.beginPress(System.currentTimeMillis());
				if (refused != null && !end)
				{
					said = new Said(false, refused, lifetime);
					return;
				}
				held = refused == null;
			}
			ticket = new Ticket(lifetime, end, held, member);
			active = ticket;
			said = new Said(true, end ? "Ending run…" : "Planning run…", lifetime);
			call = http.newCall(request(server, code, member, secret, question, end, id));
			call.enqueue(new Callback()
			{
				@Override
				public void onFailure(Call failed, IOException error)
				{
					finish(ticket, null, 0, RouteClient.UNREACHABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS);
				}

				@Override
				public void onResponse(Call completed, Response response)
				{
					handle(ticket, response);
				}
			});
		}
	}

	Request request(String server, String code, String member, String secret, Question question, boolean end, @Nullable String id)
	{
		HttpUrl.Builder url = HttpUrl.parse(server + PATH).newBuilder();
		if (end && id != null)
		{
			url.addQueryParameter("run", id);
		}
		Request.Builder request = new Request.Builder().url(url.build())
		.header("User-Agent", CompanionVersion.USER_AGENT)
		.header("X-Companion-Code", code)
		.header("X-Companion-Member", member)
		.header("X-Companion-Member-Secret", secret);
		return (end ? request.delete() : request.post(RequestBody.create(JSON, gson.toJson(question)))).build();
	}

	private void handle(Ticket ticket, Response response)
	{
		try
			(Response close = response)
		{
			long now = System.currentTimeMillis();
			String wait = close.header("Retry-After");
			long next = now + Math.max(RouteClient.MIN_GAP_MS, wait == null ? 0L : StarsClient.retryDelay(wait));
			if (close.isRedirect())
			{
				finish(ticket, null, 0, RouteClient.REDIRECTS, now + 300_000L);
				return;
			}
			if (!close.isSuccessful())
			{
				String why = close.code() == 429 || close.code() == 503 ? "The map is busy. Try the run again shortly."
							 : close.code() == 403 || close.code() == 410 ? RouteClient.NEEDS_CONNECTION : "The map could not prepare that run.";
				String body = CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES);
				try
				{
					JsonObject error = gson.fromJson(body, JsonObject.class);
					if (error != null && error.has("error") && error.get("error").isJsonPrimitive()
						&& error.get("error").getAsJsonPrimitive().isString())
					{
						String words = RouteWire.text(MapPin.plain(error.get("error").getAsString()), 240);
						if (words != null)
						{
							why = words;
						}
					}
				}
				catch (RuntimeException ignored) { /* The status still has a useful sentence. */ }
				finish(ticket, null, 0, why, Math.max(next, now + RouteClient.FAILURE_GAP_MS));
				return;
			}
			if (ticket.end)
			{
				String removed = close.header("X-Companion-Run-Revision");
				long removalRevision = removed == null ? 0 : Long.parseLong(removed);
				if (removalRevision < 0 || removalRevision > 9_007_199_254_740_991L)
				{
					removalRevision = 0;
				}
				finish(ticket, null, removalRevision, null, next);
				return;
			}
			RelayResponse.Directive answer = gson.fromJson(
												 CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES), RelayResponse.Directive.class);
			if (answer == null || answer.run == null || answer.runRevision <= 0)
			{
				finish(ticket, null, 0, "The map returned no readable run.", next);
				return;
			}
			finish(ticket, answer.run, answer.runRevision, null, next);
		}
		catch (IOException | RuntimeException unreadable)
		{
			finish(ticket, null, 0, RouteClient.UNREADABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS);
		}
	}

	private synchronized void finish(Ticket ticket, @Nullable RelayResponse.FarmRun run, long revision,
									 @Nullable String why, long next)
	{
		if (active != ticket)
		{
			return;
		}
		active = null;
		call = null;
		if (ticket.holdsGate)
		{
			gate.finishPress(next);
		}
		if (ticket.lifetime != destinations.generation())
		{
			return;
		}
		if (run != null)
		{
			state.acceptPlannedFor(ticket.member, run, revision, System.currentTimeMillis());
		}
		else if (ticket.end && revision > 0)
		{
			state.acceptFor(ticket.member, null, revision, System.currentTimeMillis());
		}
		said = new Said(false, why == null ? ticket.end ? "Run ended." : "Run ready." : why, ticket.lifetime);
	}

	/** A connection or profile ended. A cancelled reply must not release a newer press's gate. */
	synchronized void clear()
	{
		Ticket old = active;
		active = null;
		if (call != null)
		{
			call.cancel();
		}
		call = null;
		if (old != null && old.holdsGate)
		{
			gate.finishPress(System.currentTimeMillis() + RouteClient.MIN_GAP_MS);
		}
		said = null;
	}
}
