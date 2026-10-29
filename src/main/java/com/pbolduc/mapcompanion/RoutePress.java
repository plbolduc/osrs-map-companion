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
 * Route, pressed beside something in the sidebar: a search result, a clue, a death, a star, a
 * meetup, a friend. One question to the map, and its answer shown as the destination.
 *
 * <p>The answer fills the destination's card and its steps exactly as a destination the map sent
 * does — it becomes this client's own pin ({@link DestinationState#selectLocal}) with its route
 * attached — so everything built for a sent destination, the steps, the pins, the arrow and
 * Shortest Path, works on it unchanged. Because the press was the player's, the route is followed
 * from the start ({@link DestinationRoute#adoptPressed}); nothing that merely arrives ever is.
 *
 * <p><b>What it sends.</b> {@code POST /api/companion/route} with the member's own credentials, a
 * target and the tile the player stands on. The target is a public place's id where the thing has
 * one — a search result — and otherwise a tile: a clue's, a gravestone's, a star's, a meetup's, a
 * friend's. A clue or a death the player does not share travels in this request only; the map
 * plans with it and keeps nothing, as its route endpoint says. Inside a raid's rooms the client
 * cannot say where the player stands, so no {@code from} is sent and the map ranks from the
 * position the member shares, or from nowhere and says so.
 *
 * <p><b>How often.</b> One question at a time and none before the map's own wait, shared with
 * Reroute through {@link DestinationRoute#beginPress}, since the map counts both against one
 * member. A press is never repeated on its own.
 *
 * <p><b>Threads.</b> The press is Swing's; the tile and the credentials are read on the client
 * thread; the call is OkHttp's. What a press said is one immutable value behind a volatile field,
 * read by the sidebar once a second.
 */
@Singleton
final class RoutePress
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	static final String ONE_AT_A_TIME = "The map is still planning the last route.";
	private static final int MAX_NAME = 120;
	/** The game's tile space, as {@link RouteWire} bounds a step's tile. */
	private static final int MAX_COORDINATE = 16_383;

	/** What a press asks for, and what the destination's card will call it. */
	static final class Target
	{
		/** Which button asked, so the sidebar can say what came of it beside that button. */
		final String source;
		/** A public place's id, which the map resolves; or null for a bare tile. */
		@Nullable
		final String id;
		/** The tile to plan to, or for an id the tile its row stood on, used if the map names none. */
		@Nullable
		final WorldPoint tile;
		final String name;
		@Nullable
		final Integer world;
		@Nullable
		final String note;

		private Target(String source, @Nullable String id, @Nullable WorldPoint tile, String name,
			@Nullable Integer world, @Nullable String note)
		{
			this.source = source;
			this.id = id;
			this.tile = tile;
			this.name = name;
			this.world = world;
			this.note = note;
		}

		static Target place(String source, String id, String name, @Nullable WorldPoint stood, @Nullable String note)
		{
			return new Target(source, id, stood, name, null, note);
		}

		static Target tile(String source, WorldPoint tile, String name, @Nullable Integer world, @Nullable String note)
		{
			return new Target(source, null, tile, name, world, note);
		}
	}

	/** What the last press said, for the sidebar. Never changes after it is made. */
	static final class Said
	{
		final String source;
		final boolean planning;
		@Nullable
		final String message;
		/** The destination the press made, so a row can tell that the card is its own. */
		@Nullable
		final String key;
		/** {@link DestinationState#generation} when the press was made. */
		final long generation;

		Said(String source, boolean planning, @Nullable String message, @Nullable String key, long generation)
		{
			this.generation = generation;
			this.source = source;
			this.planning = planning;
			this.message = message;
			this.key = key;
		}
	}

	/** The map's answer: the ways, and where it planned to. */
	static final class Answer extends RelayResponse.Route
	{
		AnswerTarget target;
	}

	static final class AnswerTarget
	{
		int x;
		int y;
		Integer plane;
		String name;
	}

	/** The body: where to, and where from. Never kept, here or by the map. */
	static final class Question
	{
		Object target;
		RouteClient.Tile from;
	}

	/** A public place, by the id the map's own search gave it. */
	static final class PlaceId
	{
		final String id;

		PlaceId(String id)
		{
			this.id = id;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final DestinationRoute route;
	private final DestinationState destinations;
	private final DestinationMarker marker;

	@Nullable
	private volatile Said said;

	@Inject
	RoutePress(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
		MemberIdentity identity, DestinationRoute route, DestinationState destinations, DestinationMarker marker)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.pairing = pairing;
		this.identity = identity;
		this.route = route;
		this.destinations = destinations;
		this.marker = marker;
	}

	/** The press. Any thread; the work happens on the client thread and OkHttp's. */
	void press(Target target)
	{
		clientThread.invokeLater(() -> start(target));
	}

	/** Whether a press would be sent now, for enabling a Route button. */
	boolean ready()
	{
		return route.pressReady(System.currentTimeMillis());
	}

	/**
	 * What the last press from this button said, or null when the last press was another's, or was
	 * made before the destinations were last cleared — in a group since left, or a lifetime ended.
	 */
	@Nullable
	Said said(String source)
	{
		Said now = said;
		return now != null && now.source.equals(source) && now.generation == destinations.generation() ? now : null;
	}

	/** Client thread: the credentials, the tile, then the call. */
	private void start(Target target)
	{
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (client.getGameState() != GameState.LOGGED_IN || server == null || code.isEmpty()
			|| !pairing.hasServerConsent(server, code) || secret == null || HttpUrl.parse(server + RouteClient.PATH) == null)
		{
			said = new Said(target.source, false, RouteClient.NEEDS_CONNECTION, null, destinations.generation());
			return;
		}
		if (target.id == null && target.tile == null)
		{
			return;
		}
		/* Null inside a raid's rooms: the map then ranks from the shared position, or from nowhere. */
		WorldPoint from = DestinationRoute.standing(client);
		long asked = destinations.generation();
		String refused = route.beginPress(System.currentTimeMillis());
		if (refused != null)
		{
			said = new Said(target.source, false, refused, null, asked);
			return;
		}
		said = new Said(target.source, true, null, null, asked);
		http.newCall(request(server, code, member, secret, target, from)).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failed, IOException error)
			{
				finish(asked, target, RouteClient.UNREACHABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS);
			}

			@Override
			public void onResponse(Call completed, Response response)
			{
				handle(asked, target, response);
			}
		});
	}

	/** One question, as the map's {@code POST /api/companion/route} reads it. */
	Request request(String server, String code, String member, String secret, Target target, @Nullable WorldPoint from)
	{
		Question question = new Question();
		question.target = target.id != null ? new PlaceId(target.id) : new RouteClient.Tile(target.tile);
		question.from = from == null ? null : new RouteClient.Tile(from);
		return new Request.Builder()
			.url(server + RouteClient.PATH)
			.header("User-Agent", CompanionVersion.USER_AGENT)
			.header("X-Companion-Code", code)
			.header("X-Companion-Member", member)
			.header("X-Companion-Member-Secret", secret)
			.post(RequestBody.create(JSON, gson.toJson(question)))
			.build();
	}

	/** Read one reply into a destination or a sentence. Package-private for its test; OkHttp's thread. */
	void handle(long asked, Target target, Response response)
	{
		try (Response close = response)
		{
			long now = System.currentTimeMillis();
			String wait = close.header("Retry-After");
			long waitMs = wait == null ? 0L : StarsClient.retryDelay(wait);
			if (close.isRedirect())
			{
				finish(asked, target, RouteClient.REDIRECTS, now + 300_000L);
				return;
			}
			int status = close.code();
			if (status == 429 || status == 503)
			{
				finish(asked, target, RouteClient.BUSY, now + StarsClient.retryDelay(wait));
				return;
			}
			if (!close.isSuccessful())
			{
				String why = status == 404 ? RouteClient.NOT_FOUND
					: status == 403 || status == 410 ? RouteClient.NEEDS_CONNECTION : RouteClient.FAILED;
				finish(asked, target, why,
					now + Math.max(status == 404 ? RouteClient.MIN_GAP_MS : RouteClient.FAILURE_GAP_MS, waitMs));
				return;
			}
			Answer answer = gson.fromJson(CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES), Answer.class);
			RelayResponse.Route ways = RouteWire.normalize(answer);
			long next = now + Math.max(RouteClient.MIN_GAP_MS, waitMs);
			RelayResponse.Destination destination = ways == null ? null : destination(target, answer.target, ways);
			if (destination == null)
			{
				finish(asked, target, RouteClient.NO_WAY, next);
				return;
			}
			route.finishPress(next);
			/*
			 * Checked and shown under the destinations' own lock, so a group left between the check
			 * and the pin cannot keep the old group's route.
			 */
			synchronized (destinations)
			{
				if (asked != destinations.generation())
				{
					return;
				}
				destinations.selectLocal(destination);
				route.adoptPressed(destination);
			}
			said = new Said(target.source, false, null, DestinationRoute.keyOf(destination), asked);
			marker.sync();
		}
		catch (IOException | RuntimeException unreadable)
		{
			finish(asked, target, RouteClient.UNREADABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS);
		}
	}

	private void finish(long asked, Target target, String why, long nextAllowedMs)
	{
		route.finishPress(nextAllowedMs);
		said = new Said(target.source, false, why, null, asked);
	}

	/**
	 * The destination the answer makes: where the map planned to, under the name the press knew it
	 * by — or the map's, for a place it resolved, since an item's route goes to whatever drops it.
	 * Null when neither the answer nor the press names a tile on the map.
	 */
	@Nullable
	static RelayResponse.Destination destination(Target target, @Nullable AnswerTarget planned, RelayResponse.Route ways)
	{
		RelayResponse.Destination destination = new RelayResponse.Destination();
		if (planned != null && planned.x > 0 && planned.y > 0 && planned.x <= MAX_COORDINATE && planned.y <= MAX_COORDINATE
			&& (planned.plane == null || (planned.plane >= 0 && planned.plane <= 3)))
		{
			destination.x = planned.x;
			destination.y = planned.y;
			destination.plane = planned.plane == null ? 0 : planned.plane;
		}
		else if (target.tile != null)
		{
			destination.x = target.tile.getX();
			destination.y = target.tile.getY();
			destination.plane = target.tile.getPlane();
		}
		else
		{
			return null;
		}
		String resolved = planned == null ? null : RouteWire.text(planned.name, MAX_NAME);
		destination.name = target.id != null && resolved != null ? resolved : RouteWire.text(target.name, MAX_NAME);
		destination.world = target.world;
		destination.note = RouteWire.text(target.note, MAX_NAME);
		destination.route = ways;
		return destination;
	}
}
