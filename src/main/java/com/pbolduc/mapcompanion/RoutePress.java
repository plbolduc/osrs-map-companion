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
import javax.swing.JLabel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
 * friend's. A clue step that is words names no tile, so it goes as the clue itself, its tier,
 * words and scroll, and the map places it with its own clue table; so does a map above beginner,
 * whose scroll alone names the picture's place in that table. A clue or a death the player
 * does not share travels in this request only; the map plans with it and keeps nothing, as its
 * route endpoint says. Inside a raid's rooms the client
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
	static final String ONE_AT_A_TIME = "The map is still planning the last route.";
	private static final int MAX_NAME = 120;
	/** The longest refusal the map's words may make, as a farm run's are cut. */
	private static final int MAX_REFUSAL = 240;
	/** The game's tile space, as {@link RouteWire} bounds a step's tile. */
	private static final int MAX_COORDINATE = 16_383;

	/** What a press asks for, and what the destination's card will call it. */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
		/**
		 * A clue step that is words, or a map known by its scroll, sent as the snapshot carries it so
		 * the map places it from its own clue table; a step with a tile of its own goes as that tile
		 * instead.
		 */
		@Nullable
		final CompanionSnapshot.Clue clue;
		/** What the row that asked drew beside it, for the destination's card; never sent. */
		@Nullable
		final Picture picture;

		static Target place(String source, String id, String name, @Nullable WorldPoint stood, @Nullable String note)
		{
			return new Target(source, id, stood, name, null, note, null, null);
		}

		static Target tile(String source, WorldPoint tile, String name, @Nullable Integer world, @Nullable String note)
		{
			return new Target(source, null, tile, name, world, note, null, null);
		}

		static Target clue(String source, CompanionSnapshot.Clue clue, String name, @Nullable String note)
		{
			return new Target(source, null, null, name, null, note, clue, null);
		}

		/** The same target, with the picture the destination's card draws beside the answer. */
		Target showing(@Nullable Picture shown)
		{
			return new Target(source, id, tile, name, world, note, clue, shown);
		}
	}

	/**
	 * The picture a press's own row drew beside what it asked about — a clue's scroll, a search row's
	 * sprite or icon, a friend's disc — for the destination's card to draw beside the answer, so the
	 * card shows what it leads to before it is read. Local only: it stays here and on the card, and
	 * {@link #request} builds the body from the id, the clue and the tile alone.
	 */
	interface Picture
	{
		/** Put the picture on {@code label}, fitted to {@code box} square, now or when the cache answers. EDT. */
		void draw(JLabel label, int box);
	}

	/** The picture of the destination the last answered press made, by that destination's key. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class Shown
	{
		final String key;
		final Picture picture;
		final long generation;
	}

	/** What the last press said, for the sidebar. Never changes after it is made. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

		/** A press that did not happen. A route that came back says nothing: the card it filled is the answer. */
		boolean refused()
		{
			return !planning && message != null;
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

	/** A clue step by its words or its scroll, which the map turns into a place with its own clue table. */
	static final class ClueTarget
	{
		final CompanionSnapshot.Clue clue;

		ClueTarget(CompanionSnapshot.Clue clue)
		{
			this.clue = clue;
		}
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
	/**
	 * Kept apart from {@link #said}, which the next press replaces even when it is refused: the card
	 * still shows this answer then, and its picture with it.
	 */
	@Nullable
	private volatile Shown shown;

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

	/**
	 * A press that never reached the network, said beside the button it came from as the map's own
	 * refusal would be: for one the sidebar turned away before it named a {@link Target}. Any thread.
	 */
	void refuse(String source, String why)
	{
		said = new Said(source, false, why, null, destinations.generation());
	}

	/** Whether a press would be sent now, for enabling a Route button. */
	boolean ready()
	{
		return route.pressReady(System.currentTimeMillis());
	}

	/** How long the wait shared by every Route, Reroute and run has left at {@code now}, or 0. */
	long waitMs(long now)
	{
		return route.pressWait(now);
	}

	/**
	 * What a press said, as it is drawn at {@code now}: a wait it met counts down from the wait the
	 * gate holds, "The map is busy. Try again in 25s.", and every other sentence is said as it is.
	 */
	String words(Said said, long now)
	{
		return MapWait.waits(said.message) ? MapWait.tryAgain(said.message, waitMs(now)) : said.message;
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

	/** What the last press said, whichever button made it; null as {@link #said} would be after a clear. */
	@Nullable
	Said last()
	{
		Said now = said;
		return now != null && now.generation == destinations.generation() ? now : null;
	}

	/**
	 * The picture for the destination on the card, while it is the one a press made: the same place,
	 * by {@link DestinationRoute#keyOf}, since the destinations were last cleared. Null otherwise, so
	 * a destination sent since, or a press that drew nothing, shows no picture.
	 */
	@Nullable
	Picture picture(@Nullable RelayResponse.Destination destination)
	{
		Shown now = shown;
		return now != null && destination != null && now.generation == destinations.generation()
			&& now.key.equals(DestinationRoute.keyOf(destination)) ? now.picture : null;
	}

	/** Client thread: the credentials, the tile, then the call. */
	private void start(Target target)
	{
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			refuse(target.source, RouteClient.LOG_IN);
			return;
		}
		if (server == null || code.isEmpty() || !pairing.hasServerConsent(server, code) || secret == null
			|| HttpUrl.parse(server + RouteClient.PATH) == null)
		{
			refuse(target.source, RouteClient.NOT_CONNECTED);
			return;
		}
		if (target.id == null && target.tile == null && target.clue == null)
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
		CompanionHttp.enqueue(http.newCall(request(server, code, member, secret, target, from)),
			(failed, error) -> finish(asked, target, RouteClient.UNREACHABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS),
			(completed, response) -> handle(asked, target, response));
	}

	/** One question, as the map's {@code POST /api/companion/route} reads it. */
	Request request(String server, String code, String member, String secret, Target target, @Nullable WorldPoint from)
	{
		Question question = new Question();
		question.target = target.id != null ? new PlaceId(target.id)
			: target.clue != null ? new ClueTarget(target.clue)
			: new RouteClient.Tile(target.tile);
		question.from = from == null ? null : new RouteClient.Tile(from);
		return CompanionHttp.member(server + RouteClient.PATH, code, member, secret).post(CompanionHttp.json(gson, question))
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
				finish(asked, target, MapWait.BUSY, now + StarsClient.retryDelay(wait));
				return;
			}
			if (!close.isSuccessful())
			{
				String why = status == 404 ? notFound(close)
					: status == 403 || status == 410 ? RouteClient.NOT_CONNECTED : RouteClient.FAILED;
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
				shown = target.picture == null ? null : new Shown(DestinationRoute.keyOf(destination), target.picture, asked);
			}
			said = new Said(target.source, false, null, DestinationRoute.keyOf(destination), asked);
			marker.sync();
		}
		catch (IOException | RuntimeException unreadable)
		{
			finish(asked, target, RouteClient.UNREADABLE, System.currentTimeMillis() + RouteClient.FAILURE_GAP_MS);
		}
	}

	/**
	 * Why the map has no way there, in its own words when its 404 gives them: a clue step whose words
	 * it does not know and one that names no single place are different answers, and the map says
	 * which. Its sentence is cleaned as anything the map sends is, and a 404 without one, from an
	 * older map, keeps the plugin's own.
	 */
	private String notFound(Response response)
	{
		try
		{
			String said = CompanionHttp.said(gson, CompanionHttp.readBounded(response.body(), CompanionHttp.MAX_REPLY_BYTES), MAX_REFUSAL);
			return said != null ? said : RouteClient.NOT_FOUND;
		}
		catch (IOException | RuntimeException unreadable)
		{
			return RouteClient.NOT_FOUND;
		}
	}

	private void finish(long asked, Target target, String why, long nextAllowedMs)
	{
		route.finishPress(nextAllowedMs);
		said = new Said(target.source, false, why, null, asked);
	}

	/**
	 * The destination the answer makes: where the map planned to, under the name the press knew it
	 * by — or the map's, for a place it resolved, since an item's route goes to whatever drops it,
	 * and for a spot picked on the world map, which the press had no name for. Null when neither the
	 * answer nor the press names a tile on the map.
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
		/* The map names what it resolved: an item's drop, or the place a clue's words lead to. */
		destination.name = (target.id != null || target.clue != null) && resolved != null ? resolved : RouteWire.text(target.name, MAX_NAME);
		if (resolved != null && WorldMapRouteHere.SOURCE.equals(target.source))
		{
			/*
			 * A spot picked on the world map takes the landmark words the map's roster gives a friend's
			 * spot, "near Lumbridge Castle", capitalised to head the card. A map that names none leaves
			 * the press's own words.
			 */
			destination.name = Character.toUpperCase(resolved.charAt(0)) + resolved.substring(1);
		}
		destination.world = target.world;
		destination.note = RouteWire.text(target.note, MAX_NAME);
		destination.route = ways;
		return destination;
	}
}
