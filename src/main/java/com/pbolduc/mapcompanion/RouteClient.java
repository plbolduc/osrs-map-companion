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
 * Reroute: the player's own press, asking the map for the way again from where they stand.
 *
 * <p>The map's routes are never recomputed on their own, which keeps the server's work to presses
 * (the map's plan, § 4). So this asks once per press, one press at a time, and not before the wait
 * the map last asked for. The answer replaces the steps in {@link DestinationRoute}.
 *
 * <p><b>What it sends.</b> The destination's tile, which the map sent, and the tile the player is
 * standing on, which it did not have if position sharing is off: a route from here is the whole of
 * what the press asks for, and the map plans with it and keeps neither. The README says so. With
 * the member's own credentials, like the star filters; nothing goes before Connect.
 *
 * <p><b>Threads.</b> The press is on Swing; the tile is read on the client thread; the call is
 * OkHttp's. Nothing waits on the network, and the reply only lands through
 * {@link DestinationRoute#finishReroute}, which throws away an answer for a destination since
 * replaced.
 */
@Singleton
final class RouteClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	static final String PATH = "/api/companion/route";
	/** The least time between two presses that reached the map, whatever it answered. */
	static final long MIN_GAP_MS = 5_000L;
	/** After a reply that was not an answer and named no wait of its own. */
	static final long FAILURE_GAP_MS = 30_000L;

	static final String NEEDS_CONNECTION = "Connect to your map to plan a route.";
	static final String NEEDS_TILE = "Reroute needs to know where you stand, and inside this instance the game does not say.";
	static final String BUSY = "The map is busy. Try Reroute again in a few seconds.";
	static final String NO_WAY = "The map found no way there from here.";
	static final String NOT_FOUND = "The map has no way to plan to this place.";
	static final String UNREACHABLE = "The map could not be reached.";
	static final String FAILED = "The map could not plan a route just now.";
	static final String REDIRECTS = "This map address redirects. Check Settings → Advanced → Map address.";
	static final String UNREADABLE = "The map returned unreadable data.";

	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final DestinationRoute route;

	@Inject
	RouteClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
		MemberIdentity identity, DestinationRoute route)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.pairing = pairing;
		this.identity = identity;
		this.route = route;
	}

	/** The press. Swing; everything else happens on the client thread and OkHttp's. */
	void reroute()
	{
		clientThread.invokeLater(this::start);
	}

	/** Client thread: the tile, the credentials, then the call. */
	private void start()
	{
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (client.getGameState() != GameState.LOGGED_IN || server == null || code.isEmpty()
			|| !pairing.hasServerConsent(server, code) || secret == null)
		{
			route.refuse(NEEDS_CONNECTION);
			return;
		}
		WorldPoint from = DestinationRoute.standing(client);
		if (from == null)
		{
			route.refuse(NEEDS_TILE);
			return;
		}
		if (HttpUrl.parse(server + PATH) == null)
		{
			route.refuse(NEEDS_CONNECTION);
			return;
		}
		DestinationRoute.Ticket ticket = route.beginReroute(System.currentTimeMillis());
		if (ticket == null)
		{
			return;
		}
		http.newCall(request(server, code, member, secret, ticket.target, from)).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failed, IOException error)
			{
				route.finishReroute(ticket, null, UNREACHABLE, System.currentTimeMillis() + FAILURE_GAP_MS);
			}

			@Override
			public void onResponse(Call completed, Response response)
			{
				handle(ticket, response);
			}
		});
	}

	/** One question, as the map's {@code POST /api/companion/route} reads it. */
	Request request(String server, String code, String member, String secret, WorldPoint target, WorldPoint from)
	{
		Question question = new Question();
		question.target = new Tile(target);
		question.from = new Tile(from);
		return new Request.Builder()
			.url(server + PATH)
			.header("User-Agent", CompanionVersion.USER_AGENT)
			.header("X-Companion-Code", code)
			.header("X-Companion-Member", member)
			.header("X-Companion-Member-Secret", secret)
			.post(RequestBody.create(JSON, gson.toJson(question)))
			.build();
	}

	/** Read one reply into the route or a sentence. Package-private for its test; OkHttp's thread. */
	void handle(DestinationRoute.Ticket ticket, Response response)
	{
		try (Response close = response)
		{
			long now = System.currentTimeMillis();
			String wait = close.header("Retry-After");
			long asked = wait == null ? 0L : StarsClient.retryDelay(wait);
			if (close.isRedirect())
			{
				route.finishReroute(ticket, null, REDIRECTS, now + 300_000L);
				return;
			}
			int code = close.code();
			if (code == 429 || code == 503)
			{
				route.finishReroute(ticket, null, BUSY, now + StarsClient.retryDelay(wait));
				return;
			}
			if (!close.isSuccessful())
			{
				String why = code == 404 ? NOT_FOUND : code == 403 || code == 410 ? NEEDS_CONNECTION : FAILED;
				route.finishReroute(ticket, null, why, now + Math.max(code == 404 ? MIN_GAP_MS : FAILURE_GAP_MS, asked));
				return;
			}
			RelayResponse.Route answer = RouteWire.normalize(
				gson.fromJson(CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES), RelayResponse.Route.class));
			route.finishReroute(ticket, answer, answer == null ? NO_WAY : null, now + Math.max(MIN_GAP_MS, asked));
		}
		catch (IOException | RuntimeException unreadable)
		{
			route.finishReroute(ticket, null, UNREADABLE, System.currentTimeMillis() + FAILURE_GAP_MS);
		}
	}

	/** The body: where to, and where from. Never kept, here or by the map. */
	static final class Question
	{
		Tile target;
		Tile from;
	}

	static final class Tile
	{
		final int x;
		final int y;
		final int plane;

		Tile(WorldPoint point)
		{
			this.x = point.getX();
			this.y = point.getY();
			this.plane = point.getPlane();
		}
	}
}
