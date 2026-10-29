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
import java.util.function.Consumer;
import java.util.regex.Pattern;
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
 * A meetup's answer from the game, and <i>Meet here</i>: the two writes the sidebar makes.
 *
 * <p><b>Answering.</b> {@code PUT /api/companion/event/answer} with the meetup's id and one of the
 * map's four answers, under the member's own headers. The map writes it for the browser this member
 * is bound to, so one person has one answer on both screens. Only ever a press: Route to a meetup
 * says nothing to anybody, and <i>Say on my way</i> beside it is a button, never a side effect.
 *
 * <p><b>Meet here.</b> {@code PUT /api/companion/event} with a title, a time, and the tile and
 * world the player is on — the same body a browser sends, made into the same immutable
 * {@code ?event=} link. The map answers with that link's path, which is checked for shape and
 * joined to the approved map address before it goes on the clipboard for a clan chat or Discord.
 *
 * <p><b>How often.</b> One write at a time, and none before the map's own wait: an answer and a
 * Meet here share a bucket of six a minute on the map. A refusal is a sentence; nothing is retried.
 *
 * <p><b>Threads.</b> Presses are Swing's; the tile and the credentials are read on the client
 * thread; the call is OkHttp's. What the last write said is one immutable value behind a volatile.
 */
@Singleton
final class MeetupClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	static final String EVENT = "/api/companion/event";
	static final String ANSWER = "/api/companion/event/answer";
	/** The map's own cap on a title. */
	static final int MAX_TITLE = 60;
	static final long MIN_GAP_MS = 2_000L;
	static final long FAILURE_GAP_MS = 30_000L;
	/** A path the map builds from a meetup: letters, digits and the punctuation a query needs. */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9/?=&%._~+\\-]{0,2000}");

	static final String NEEDS_CONNECTION = "Connect to your map to answer or make a meetup.";
	static final String NEEDS_TILE = "Meet here needs a place on the map, and inside an instance the game does not give one.";
	static final String BUSY = "The map asked to wait. Try again in a few seconds.";
	static final String ONE_AT_A_TIME = "The last answer is still on its way.";
	static final String GONE = "That meetup isn't in this group any more.";
	static final String REFUSED = "The map would not take that meetup. A title and a time within the month are needed.";
	static final String UNREACHABLE = "The map could not be reached.";
	static final String FAILED = "The map could not save that just now.";
	static final String MADE = "Meetup made. Its link is copied.";
	static final String MADE_NO_LINK = "Meetup made.";

	/** What the last write said, for the sidebar. */
	static final class Said
	{
		/** {@code answer:<id>} or {@code meet}. */
		final String source;
		final boolean sending;
		@Nullable
		final String message;
		final long generation;

		Said(String source, boolean sending, @Nullable String message, long generation)
		{
			this.source = source;
			this.sending = sending;
			this.message = message;
			this.generation = generation;
		}
	}

	static final class Answer
	{
		final String eventId;
		final String answer;

		Answer(String eventId, String answer)
		{
			this.eventId = eventId;
			this.answer = answer;
		}
	}

	static final class NewMeetup
	{
		String title;
		int x;
		int y;
		int plane;
		Integer world;
		long startsAtMs;
	}

	static final class Made
	{
		String path;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final DestinationState destinations;
	private final GroupState group;
	/** Where the link goes. The clipboard in the client; a list in a test. Swing's thread. */
	private final Consumer<String> copy;

	@Nullable
	private volatile Said said;
	private boolean busy;
	private long nextAllowedMs;

	@Inject
	MeetupClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
		MemberIdentity identity, DestinationState destinations, GroupState group)
	{
		this(http, gson, client, clientThread, pairing, identity, destinations, group, link ->
			javax.swing.SwingUtilities.invokeLater(() ->
				java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new java.awt.datatransfer.StringSelection(link), null)));
	}

	MeetupClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
		MemberIdentity identity, DestinationState destinations, GroupState group, Consumer<String> copy)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.pairing = pairing;
		this.identity = identity;
		this.destinations = destinations;
		this.group = group;
		this.copy = copy;
	}

	/** What the last write from this button said, or null; see {@link RoutePress#said}. */
	@Nullable
	Said said(String source)
	{
		Said now = said;
		return now != null && now.source.equals(source) && now.generation == destinations.generation() ? now : null;
	}

	synchronized boolean ready()
	{
		return !busy && System.currentTimeMillis() >= nextAllowedMs;
	}

	/** Answer a meetup: one of the map's four. A press, on any thread. */
	void answer(String eventId, String answer)
	{
		if (!SocialWire.ANSWERS.contains(answer))
		{
			return;
		}
		clientThread.invokeLater(() -> send("answer:" + eventId, ANSWER, new Answer(eventId, answer), false,
			() -> group.answered(eventId, answer, System.currentTimeMillis())));
	}

	/** Meet here, at this title and time, on the tile and world the player is on. A press. */
	void create(String title, long startsAtMs)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN || WorldTiles.isInstance(client))
			{
				said = new Said("meet", false, client.getGameState() == GameState.LOGGED_IN ? NEEDS_TILE : NEEDS_CONNECTION,
					destinations.generation());
				return;
			}
			WorldPoint here = DestinationRoute.standing(client);
			if (here == null)
			{
				said = new Said("meet", false, NEEDS_TILE, destinations.generation());
				return;
			}
			NewMeetup meetup = new NewMeetup();
			meetup.title = cleanTitle(title);
			meetup.x = here.getX();
			meetup.y = here.getY();
			meetup.plane = here.getPlane();
			meetup.world = client.getWorld() > 0 ? client.getWorld() : null;
			meetup.startsAtMs = startsAtMs;
			send("meet", EVENT, meetup, true, null);
		});
	}

	/** Client thread: the credentials, then the call. */
	private void send(String source, String path, Object body, boolean making, @Nullable Runnable onAnswered)
	{
		long asked = destinations.generation();
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (client.getGameState() != GameState.LOGGED_IN || server == null || code.isEmpty()
			|| !pairing.hasServerConsent(server, code) || secret == null || HttpUrl.parse(server + path) == null)
		{
			said = new Said(source, false, NEEDS_CONNECTION, asked);
			return;
		}
		synchronized (this)
		{
			if (busy)
			{
				said = new Said(source, false, ONE_AT_A_TIME, asked);
				return;
			}
			if (System.currentTimeMillis() < nextAllowedMs)
			{
				said = new Said(source, false, BUSY, asked);
				return;
			}
			busy = true;
		}
		said = new Said(source, true, null, asked);
		http.newCall(request(server, code, member, secret, path, body)).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failed, IOException error)
			{
				finish(source, asked, UNREACHABLE, System.currentTimeMillis() + FAILURE_GAP_MS);
			}

			@Override
			public void onResponse(Call completed, Response response)
			{
				handle(source, asked, making, onAnswered, server, response);
			}
		});
	}

	Request request(String server, String code, String member, String secret, String path, Object body)
	{
		return new Request.Builder()
			.url(server + path)
			.header("User-Agent", CompanionVersion.USER_AGENT)
			.header("X-Companion-Code", code)
			.header("X-Companion-Member", member)
			.header("X-Companion-Member-Secret", secret)
			.put(RequestBody.create(JSON, gson.toJson(body)))
			.build();
	}

	/** One reply into a sentence, and for Meet here the link. Package-private for its test; OkHttp's. */
	void handle(String source, long asked, boolean making, @Nullable Runnable onAnswered, String server, Response response)
	{
		try (Response close = response)
		{
			long now = System.currentTimeMillis();
			String wait = close.header("Retry-After");
			int status = close.code();
			if (status == 429 || status == 503)
			{
				finish(source, asked, BUSY, now + StarsClient.retryDelay(wait));
				return;
			}
			if (!close.isSuccessful())
			{
				String why = status == 403 || status == 410 ? NEEDS_CONNECTION
					: status == 404 && !making ? GONE
					: status == 400 && making ? REFUSED : FAILED;
				finish(source, asked, why, now + Math.max(FAILURE_GAP_MS, wait == null ? 0L : StarsClient.retryDelay(wait)));
				return;
			}
			String text = CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES);
			if (!making)
			{
				if (asked == destinations.generation() && onAnswered != null)
				{
					onAnswered.run();
				}
				finish(source, asked, null, now + MIN_GAP_MS);
				return;
			}
			Made made = gson.fromJson(text, Made.class);
			String link = made == null ? null : link(server, made.path);
			if (link != null && asked == destinations.generation())
			{
				copy.accept(link);
			}
			finish(source, asked, link != null ? MADE : MADE_NO_LINK, now + MIN_GAP_MS);
		}
		catch (IOException | RuntimeException unreadable)
		{
			finish(source, asked, making ? MADE_NO_LINK : FAILED, System.currentTimeMillis() + FAILURE_GAP_MS);
		}
	}

	private void finish(String source, long asked, @Nullable String why, long nextAllowed)
	{
		synchronized (this)
		{
			busy = false;
			nextAllowedMs = Math.max(nextAllowedMs, nextAllowed);
		}
		said = new Said(source, false, why, asked);
	}

	/**
	 * The link to copy: the map's path on the approved map address, or null for a path that is not
	 * one the map would build — an absolute address, another host, a script, anything with a space.
	 */
	@Nullable
	static String link(String server, @Nullable String path)
	{
		if (path == null || !PATH.matcher(path).matches() || path.startsWith("//"))
		{
			return null;
		}
		return server + path;
	}

	/** A title as the map stores one: control bytes out, whitespace folded, at most sixty characters. */
	static String cleanTitle(@Nullable String title)
	{
		String folded = RouteWire.text(title, MAX_TITLE);
		return folded == null ? "" : folded;
	}
}
