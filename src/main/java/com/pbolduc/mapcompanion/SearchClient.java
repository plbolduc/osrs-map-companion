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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.AllArgsConstructor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import static com.pbolduc.mapcompanion.RouteWire.text;

/**
 * The sidebar's search box, asking the map's own search ({@code GET /api/companion/search}).
 *
 * <p>The map answers with the website's rows in the website's order — a player who searched on the
 * map and here gets one answer, not two — and a pick is a {@link RoutePress} by the row's id, so
 * the map resolves a monster standing in five places to the nearest and a boss to its way in. This
 * class decides when to ask and reads what comes back; it ranks nothing.
 *
 * <p><b>When it asks.</b> {@link #DEBOUNCE_MS} after the last key, or at once on Enter; a query of
 * fewer than two characters asks nothing. One search is in flight at a time, because the map
 * answers a second with "busy": a query typed while one is on its way waits for it and then goes,
 * and the answer to the older one is dropped by generation if the box has moved on. Clearing the
 * box cancels the call and forgets the answer. A {@code Retry-After} is honoured by not asking
 * again until it has passed; nothing is repeated on its own except the latest query once that
 * wait is over, which is the one request the map said it would take.
 *
 * <p><b>Never logged.</b> What a person types is theirs. This class has no logger, and the query
 * goes only in the request's address, which the map keeps out of its own logs.
 *
 * <p><b>Threads.</b> Everything here is Swing's. The call is OkHttp's, and its answer is handed
 * back to Swing before it touches anything.
 */
@Singleton
final class SearchClient
{
	static final String PATH = "/api/companion/search";
	static final long DEBOUNCE_MS = 350L;
	static final int MIN_QUERY = 2;
	static final int MAX_QUERY = 64;
	/** The map's own number: eight rows is the most a narrow panel shows. */
	static final int MAX_ROWS = 8;
	static final int MAX_ITEMS = 3;
	/** After a failure that named no wait of its own. */
	static final long FAILURE_GAP_MS = 10_000L;

	/** No character to search as: a search is asked as a member, and a member is a character. */
	static final String LOG_IN = "Log in to search.";
	static final String UNREACHABLE = RoutePress.UNREACHABLE;
	static final String FAILED = "The map could not search just now.";
	static final String UNREADABLE = RoutePress.UNREADABLE;
	static final String NOTHING = "Nothing on the map by that name.";
	/** Under a one-letter box: {@link #MIN_QUERY} is two, and a query of one asks nothing. */
	static final String ONE_MORE = "Type one more letter.";

	/** One row as the map sends it. Read leniently and then bounded by {@link #normalize}. */
	static final class Answer
	{
		List<Row> rows;
		List<Item> items;
		String suggestion;
		String origin;
	}

	static final class Row
	{
		String id;
		String kind;
		String name;
		String line;
		String section;
		int x;
		int y;
		Integer plane;
		Integer effortTiles;
	}

	static final class Item
	{
		String id;
		/** The game's id for the item, where the map's tables name one; it picks the sprite and nothing else. */
		Integer itemId;
		String name;
		int droppedBy;
		int soldAt;
	}

	/** The heading item rows sit under, so they are never read as one more shop or monster above them. */
	static final String ITEMS = "Items";

	/** One thing the list shows: a place, or an item routed to its nearest source. */
	@AllArgsConstructor
	static final class Hit
	{
		final String id;
		final String name;
		/** The grey line under the name, in the website's words. */
		final String line;
		/**
		 * What a pick writes under the destination's name: the line without the effort. That was
		 * measured from where the search was typed and stops being true as the player walks, while
		 * the route's own way line under it gives the length from where they stood at the press.
		 */
		final String note;
		/** "Sold at", "Dropped by" or {@link #ITEMS}; null for the ordinary rows. */
		@Nullable
		final String section;
		/** Where the row stood, kept for a destination the map names no tile for. Null for an item. */
		@Nullable
		final int[] tile;
		/** The map's kind for a place — {@code bank}, {@code monster} — which picks its icon; null for an item. */
		@Nullable
		final String kind;
		/** An item's game id, for its sprite; null for a place, or an item the map names no id for. */
		@Nullable
		final Integer itemId;

		Hit(String id, String name, String line, @Nullable String section, @Nullable int[] tile)
		{
			this(id, name, line, section, tile, null, null);
		}

		Hit(String id, String name, String line, @Nullable String section, @Nullable int[] tile,
			@Nullable String kind, @Nullable Integer itemId)
		{
			this(id, name, line, line, section, tile, kind, itemId);
		}
	}

	/** What the list says now. Never changes after it is made. */
	@AllArgsConstructor
	static final class Result
	{
		static final Result EMPTY = new Result("", Collections.<Hit>emptyList(), null, null, false);

		final String query;
		final List<Hit> hits;
		@Nullable
		final String suggestion;
		@Nullable
		final String message;
		final boolean searching;
		/**
		 * The message says the search did not happen: the map refused it, failed or could not be asked.
		 * Not a wait being kept, which goes by itself, nor an answer with nothing in it.
		 */
		final boolean refused;

		Result(String query, List<Hit> hits, @Nullable String suggestion, @Nullable String message, boolean searching)
		{
			this(query, hits, suggestion, message, searching, false);
		}
	}

	/** Runs an action after a delay, and cancels it. Swing's timer in the client; a list in a test. */
	interface Debouncer
	{
		/** @return what cancels it */
		Runnable schedule(Runnable action, long delayMs);
	}

	/** The credentials a search is asked with, read when it goes. Null when there are none to ask with. */
	interface Credentials
	{
		@Nullable
		Pairing.Signed signed();
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Credentials credentials;
	/** Whether there is a character to search as, so a search with no credentials says why. */
	private final BooleanSupplier character;
	private final Debouncer debouncer;
	private final Executor swing;

	private Result result = Result.EMPTY;
	@Nullable
	private Runnable onChange;
	/** What the box says, folded: the query the list should be answering. */
	private String wanted = "";
	@Nullable
	private Runnable pendingTimer;
	@Nullable
	private Call inFlight;
	/** Bumped by {@link #clear}: an answer from before it is thrown away whatever it asked. */
	private long generation;
	private long nextAllowedMs;
	/** Who the list was searched as: the map, the group and the character. See {@link #checkScope}. */
	@Nullable
	private String scope;

	@Inject
	SearchClient(OkHttpClient http, Gson gson, Pairing pairing, MemberIdentity identity)
	{
		this(http, gson, () -> pairing.signed(identity.id()), () -> identity.id() != null, (action, delayMs) ->
		{
			Timer timer = new Timer((int) delayMs, e -> action.run());
			timer.setRepeats(false);
			timer.start();
			return timer::stop;
		}, SwingUtilities::invokeLater);
	}

	/** A search with a character always there to ask as, for a test that is not about logging in. */
	SearchClient(OkHttpClient http, Gson gson, Credentials credentials, Debouncer debouncer, Executor swing)
	{
		this(http, gson, credentials, () -> true, debouncer, swing);
	}

	/** @param character whether a character is logged in, a member being a character; see {@link MemberIdentity#id} */
	SearchClient(OkHttpClient http, Gson gson, Credentials credentials, BooleanSupplier character, Debouncer debouncer,
		Executor swing)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
		this.credentials = credentials;
		this.character = character;
		this.debouncer = debouncer;
		this.swing = swing;
	}

	/** Told whenever {@link #result} changes. Swing. */
	void listen(@Nullable Runnable onChange)
	{
		this.onChange = onChange;
	}

	Result result()
	{
		return result;
	}

	/** The box changed. Asks {@link #DEBOUNCE_MS} after the last change, or after the map's wait. */
	void typed(String text)
	{
		String query = fold(text);
		if (query.isEmpty())
		{
			clear();
			return;
		}
		wanted = query;
		cancelTimer();
		if (query.length() < MIN_QUERY)
		{
			/* The map answers two letters or more; an empty list under one letter said nothing of why. */
			show(new Result(query, Collections.<Hit>emptyList(), null, ONE_MORE, false));
			return;
		}
		long wait = Math.max(DEBOUNCE_MS, nextAllowedMs - System.currentTimeMillis());
		pendingTimer = debouncer.schedule(this::due, wait);
	}

	/** Enter: ask now, unless the map asked to wait, in which case as soon as it allows. */
	void enter(String text)
	{
		String query = fold(text);
		if (query.length() < MIN_QUERY)
		{
			typed(text);
			return;
		}
		wanted = query;
		cancelTimer();
		long wait = nextAllowedMs - System.currentTimeMillis();
		if (wait > 0)
		{
			pendingTimer = debouncer.schedule(this::due, wait);
			/* Not refused: the search goes by itself, and the box's line counts the wait down; see MapWait. */
			show(new Result(query, result.hits, null, MapWait.ASKED, false));
			return;
		}
		due();
	}

	/** How long the map's wait before the next search has left at {@code now}, or 0. */
	long waitMs(long now)
	{
		return Math.max(0L, nextAllowedMs - now);
	}

	/**
	 * Forget the list if it was searched as somebody else: another group, another character, another
	 * map. Its rows were that member's — measured from their position — and a pick from it would be
	 * asked as this one. Called by the sidebar as it redraws.
	 */
	void checkScope()
	{
		if (scope != null && !scope.equals(scopeOf(credentials.signed())))
		{
			clear();
		}
	}

	@Nullable
	private static String scopeOf(@Nullable Pairing.Signed held)
	{
		return held == null ? null : held.server + "|" + held.code + "|" + held.member;
	}

	/** The box was emptied: cancel what is on its way and forget the answer. */
	void clear()
	{
		generation++;
		wanted = "";
		scope = null;
		cancelTimer();
		Call call = inFlight;
		inFlight = null;
		if (call != null)
		{
			call.cancel();
		}
		show(Result.EMPTY);
	}

	private void cancelTimer()
	{
		Runnable cancel = pendingTimer;
		pendingTimer = null;
		if (cancel != null)
		{
			cancel.run();
		}
	}

	/** The wait is over: ask for what the box says now, if nothing else is on its way. */
	private void due()
	{
		pendingTimer = null;
		String query = wanted;
		if (query.length() < MIN_QUERY || inFlight != null)
		{
			return;
		}
		long wait = nextAllowedMs - System.currentTimeMillis();
		if (wait > 0)
		{
			pendingTimer = debouncer.schedule(this::due, wait);
			return;
		}
		Pairing.Signed held = credentials.signed();
		HttpUrl base = held == null ? null : HttpUrl.parse(held.server + PATH);
		if (base == null)
		{
			show(new Result(query, Collections.<Hit>emptyList(), null,
				character.getAsBoolean() ? RoutePress.NOT_CONNECTED : LOG_IN, false, true));
			return;
		}
		Request request = CompanionHttp.member(base.newBuilder().addQueryParameter("q", query).build(), held.code, held.member, held.secret).get()
			.build();
		long asked = generation;
		scope = scopeOf(held);
		show(new Result(query, result.hits, null, null, true));
		Call call = http.newCall(request);
		inFlight = call;
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failed, IOException error)
			{
				swing.execute(() -> landed(call, asked, query, null, UNREACHABLE, System.currentTimeMillis() + FAILURE_GAP_MS));
			}

			@Override
			public void onResponse(Call completed, Response response)
			{
				Answer answer = null;
				String why = null;
				long next;
				try (Response close = response)
				{
					long now = System.currentTimeMillis();
					String wait = close.header("Retry-After");
					int status = close.code();
					if (status == 429 || status == 503)
					{
						next = now + StarsClient.retryDelay(wait);
						/* Nothing is asked again until the player types or presses Enter; the line counts the wait down. */
						why = MapWait.BUSY;
					}
					else if (!close.isSuccessful())
					{
						next = now + Math.max(FAILURE_GAP_MS, wait == null ? 0L : StarsClient.retryDelay(wait));
						why = status == 403 || status == 410 ? RoutePress.NOT_CONNECTED : FAILED;
					}
					else
					{
						next = 0L;
						answer = gson.fromJson(CompanionHttp.readBounded(close.body()), Answer.class);
						if (answer == null)
						{
							why = UNREADABLE;
						}
					}
				}
				catch (IOException | RuntimeException unreadable)
				{
					next = System.currentTimeMillis() + FAILURE_GAP_MS;
					answer = null;
					why = UNREADABLE;
				}
				Answer read = answer;
				String said = why;
				long allowed = next;
				swing.execute(() -> landed(call, asked, query, read, said, allowed));
			}
		});
	}

	/** Swing: the answer to {@code query}, or why there is none. */
	private void landed(Call call, long asked, String query, @Nullable Answer answer, @Nullable String why, long nextAllowed)
	{
		if (inFlight == call)
		{
			inFlight = null;
		}
		nextAllowedMs = Math.max(nextAllowedMs, nextAllowed);
		if (asked != generation)
		{
			return;
		}
		if (!query.equals(wanted))
		{
			/* The box moved on while this was out: the answer is to a question nobody is asking. */
			if (wanted.length() >= MIN_QUERY && pendingTimer == null)
			{
				due();
			}
			return;
		}
		if (answer == null)
		{
			show(new Result(query, result.hits, null, why, false, true));
			return;
		}
		List<Hit> hits = normalize(answer);
		String suggestion = hits.isEmpty() ? text(answer.suggestion, MAX_QUERY) : null;
		show(new Result(query, hits, suggestion, hits.isEmpty() && suggestion == null ? NOTHING : null, false));
	}

	private void show(Result next)
	{
		result = next;
		Runnable listener = onChange;
		if (listener != null)
		{
			listener.run();
		}
	}

	/**
	 * The rows and items made safe to draw: every string folded and cut, an id with no characters
	 * the map would refuse, at most {@link #MAX_ROWS} places and {@link #MAX_ITEMS} items. Every one
	 * was typed by somebody at some point — a place's name is the wiki's — and is drawn by this
	 * client, so it is bounded here whatever the map did.
	 */
	static List<Hit> normalize(Answer answer)
	{
		List<Hit> hits = new ArrayList<>();
		if (answer.rows != null)
		{
			for (Row row : answer.rows)
			{
				if (hits.size() >= MAX_ROWS)
				{
					break;
				}
				String id = id(row == null ? null : row.id);
				String name = row == null ? null : text(row.name, 120);
				if (id == null || name == null)
				{
					continue;
				}
				int plane = row.plane == null ? 0 : row.plane;
				boolean onMap = row.x > 0 && row.y > 0 && row.x <= 16_383 && row.y <= 16_383 && plane >= 0 && plane <= 3;
				String section = "Places".equals(row.section) ? null : text(row.section, 20);
				String kind = text(row.kind, 20);
				hits.add(new Hit(id, name, rowLine(row), placeLine(row), section, onMap ? new int[]{row.x, row.y, plane} : null, kind, null));
			}
		}
		if (answer.items != null)
		{
			int items = 0;
			for (Item item : answer.items)
			{
				String id = id(item == null ? null : item.id);
				String name = item == null ? null : text(item.name, 120);
				if (id == null || name == null || items >= MAX_ITEMS)
				{
					continue;
				}
				items++;
				Integer itemId = item.itemId != null && item.itemId > 0 ? item.itemId : null;
				hits.add(new Hit(id, name, itemLine(item), ITEMS, null, null, itemId));
			}
		}
		return hits;
	}

	/** "Character · Shilo Village": the website's own subtitle, without the effort; see {@link Hit#note}. */
	static String placeLine(Row row)
	{
		String line = text(row.line, 120);
		return line == null ? "" : line;
	}

	/** "Character · Shilo Village · ~15 tiles": the website's own subtitle, then the effort it measured. */
	static String rowLine(Row row)
	{
		StringBuilder sb = new StringBuilder(placeLine(row));
		if (row.effortTiles != null && row.effortTiles >= 0)
		{
			if (sb.length() > 0)
			{
				sb.append(" · ");
			}
			sb.append('~').append(row.effortTiles).append(" tiles");
		}
		return sb.toString();
	}

	/**
	 * "Dropped by 3 monsters · sold in 1 shop": what picking a name a partial query could mean would
	 * answer. It does not start "Item ·", because the row sits under the Items heading that says so.
	 * Each count names what it counts: the row can follow a Dropped by heading that lists fewer
	 * sources than its count, and a bare number there reads as a count of something on the screen.
	 */
	static String itemLine(Item item)
	{
		StringBuilder sb = new StringBuilder();
		if (item.droppedBy > 0)
		{
			sb.append("dropped by ").append(item.droppedBy).append(item.droppedBy == 1 ? " monster" : " monsters");
		}
		if (item.soldAt > 0)
		{
			sb.append(sb.length() > 0 ? " · " : "").append("sold in ").append(item.soldAt).append(item.soldAt == 1 ? " shop" : " shops");
		}
		return sb.length() == 0 ? "" : Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
	}

	/** An id the route endpoint would accept: non-empty, short, and no control bytes. */
	@Nullable
	private static String id(@Nullable String id)
	{
		if (id == null || id.trim().isEmpty() || id.length() > 160)
		{
			return null;
		}
		for (int i = 0; i < id.length(); i++)
		{
			char c = id.charAt(i);
			if (c < 0x20 || c == 0x7f)
			{
				return null;
			}
		}
		return id.trim();
	}

	/** The box's text as the map reads a query: whitespace folded, trimmed, and no longer than it takes. */
	static String fold(@Nullable String text)
	{
		if (text == null)
		{
			return "";
		}
		String folded = text.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
		return folded.length() > MAX_QUERY ? folded.substring(0, MAX_QUERY).trim() : folded;
	}
}
