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
 * A meetup's answer from the game, and <i>Meet here</i>: the two writes the sidebar makes.
 *
 * <p><b>Answering.</b> {@code PUT /api/companion/event/answer} with the meetup's id and one of the
 * map's four answers, under the member's own headers. The map writes it for the browser this member
 * is bound to, so one person has one answer on both screens. Only ever a press: Route to a meetup
 * says nothing to anybody, and <i>On my way</i> is an answer pressed like the other three.
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
	static final String EVENT = "/api/companion/event";
	static final String ANSWER = "/api/companion/event/answer";
	/** The map's own cap on a title. */
	static final int MAX_TITLE = 60;
	static final long MIN_GAP_MS = 2_000L;
	static final long FAILURE_GAP_MS = 30_000L;
	/** A path the map builds from a meetup: letters, digits and the punctuation a query needs. */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9/?=&%._~+\\-]{0,2000}");

	/** A press with no character logged in: an answer and a meetup are a member's, and a member is a character. */
	static final String LOG_IN = "Log in to answer or make a meetup.";
	/** The cause and the one step that fixes it: a meetup is made on the tile you stand on, which an instance does not give. */
	static final String NEEDS_TILE = Words.of("meetup.needsTile");
	static final String ONE_AT_A_TIME = "The last answer is still on its way.";
	static final String GONE = Words.of("meetup.gone");
	static final String REFUSED = Words.of("meetup.refused");
	static final String UNREACHABLE = RoutePress.UNREACHABLE;
	static final String FAILED = Words.of("meetup.failed");
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
		/** When it was said, so a confirmation is drawn only while it is news; see {@link #shown}. */
		final long atMs;

		Said(String source, boolean sending, @Nullable String message, long generation)
		{
			this.source = source;
			this.sending = sending;
			this.message = message;
			this.generation = generation;
			this.atMs = System.currentTimeMillis();
		}

		/** A meetup made: the one thing a write says that is done rather than still asking something of the player. */
		boolean confirms()
		{
			return MADE.equals(message) || MADE_NO_LINK.equals(message);
		}

		/**
		 * Whether there are words to draw at {@code now}: a confirmation for the ten seconds a run's
		 * card gives one ({@link FarmRunClient.Said#CONFIRMATION_MS}), then its line back, while a
		 * refusal is still true and stays until the next press says something else.
		 */
		boolean shown(long now)
		{
			return message != null && (!confirms() || now - atMs < FarmRunClient.Said.CONFIRMATION_MS);
		}

		/** A write that did not happen: everything said but one on its way and a meetup made. */
		boolean refused()
		{
			return !sending && message != null && !confirms();
		}
	}

	@AllArgsConstructor
	static final class Answer
	{
		final String eventId;
		final String answer;
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

	/** How long the map's wait before the next write has left at {@code now}, or 0. */
	synchronized long waitMs(long now)
	{
		return Math.max(0L, nextAllowedMs - now);
	}

	/** What a write said, as it is drawn at {@code now}: a wait it met counts down; see {@link MapWait}. */
	String words(Said said, long now)
	{
		return MapWait.words(said.message, waitMs(now));
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

	/**
	 * Meet here, at this title and time, on the player's world: at {@code spot}, picked on the game's
	 * world map, or on the tile the player is on when there is none. A press.
	 */
	void create(String title, long startsAtMs, @Nullable WorldPoint spot)
	{
		clientThread.invokeLater(() ->
		{
			boolean playing = client.getGameState() == GameState.LOGGED_IN;
			WorldPoint here = !playing ? null : spot != null ? spot : WorldTiles.isInstance(client) ? null : DestinationRoute.standing(client);
			if (here == null)
			{
				said = new Said("meet", false, playing ? NEEDS_TILE : LOG_IN, destinations.generation());
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
		Pairing.Signed signed = pairing.signed(identity.id());
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			said = new Said(source, false, LOG_IN, asked);
			return;
		}
		if (signed == null || HttpUrl.parse(signed.server + path) == null)
		{
			said = new Said(source, false, RoutePress.NOT_CONNECTED, asked);
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
				said = new Said(source, false, MapWait.ASKED, asked);
				return;
			}
			busy = true;
		}
		said = new Said(source, true, null, asked);
		CompanionHttp.enqueue(http.newCall(request(signed.server, signed.code, signed.member, signed.secret, path, body)),
			(failed, error) -> finish(source, asked, UNREACHABLE, System.currentTimeMillis() + FAILURE_GAP_MS),
			(completed, response) -> handle(source, asked, making, onAnswered, signed.server, response));
	}

	Request request(String server, String code, String member, String secret, String path, Object body)
	{
		return CompanionHttp.member(server + path, code, member, secret).put(CompanionHttp.json(gson, body))
			.build();
	}

	/** One reply into a sentence, and for Meet here the link. Package-private for its test; OkHttp's. */
	void handle(String source, long asked, boolean making, @Nullable Runnable onAnswered, String server, Response response)
	{
		try (Response close = response)
		{
			long now = System.currentTimeMillis();
			int status = close.code();
			if (!close.isSuccessful())
			{
				finish(source, asked, MapWait.why(status, status == 404 && !making ? GONE : status == 400 && making ? REFUSED : FAILED),
					now + MapWait.after(close, FAILURE_GAP_MS));
				return;
			}
			String text = CompanionHttp.readBounded(close.body());
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
