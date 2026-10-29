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
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Reading the group's friends and meetups from the map without letting a bad row cost the reply.
 *
 * <p>The roster and the meetups ride on the same reply and frame as the claim receipt, the member
 * secret and the milestone acknowledgements, as a route does ({@link RouteWire}), so they are read
 * the same way: the value whole, then each row on its own, a row that cannot be read dropped while
 * the rest and everything around them stand. A value that is not a list at all reads as absent,
 * which is today's sidebar.
 *
 * <p>Then bounded, because every string here was typed by somebody in the group and is drawn by
 * this client: names, landmarks, titles and news folded and cut; a colour that is not
 * {@code #rrggbb} dropped for the fallback; a presence or an answer outside the map's vocabulary
 * dropped rather than guessed; a meetup off the map dropped; at most a group's worth of rows.
 */
final class SocialWire
{
	/** The map's own limit on a group (MAX_MEMBERS_PER_GROUP in its relay). */
	static final int MAX_ROSTER = GroupMarkers.MAX_MEMBERS;
	/** A week of meetups; the map caps a group's at far fewer. */
	static final int MAX_MEETUPS = 20;
	private static final int MAX_ID = 64;
	private static final int MAX_NAME = 40;
	private static final int MAX_NEAR = 80;
	private static final int MAX_TITLE = 60;
	private static final int MAX_NEWS = 80;
	private static final int MAX_COORDINATE = 16_383;
	private static final Pattern COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");
	static final Set<String> PRESENCES = new HashSet<>(Arrays.asList("online", "idle", "offline"));
	/** What a member may answer, as the map's {@code EVENT_ANSWERS} spells it. */
	static final List<String> ANSWERS = Arrays.asList("going", "maybe", "no", "on_my_way");
	private static final Set<String> ROLL_UP = new HashSet<>(Arrays.asList("going", "maybe", "no", "on_my_way", "arrived"));

	private SocialWire()
	{
	}

	/** Gson's hook for {@code Directive.roster}; see {@link RouteWire.Lenient} for why a field factory. */
	public static final class LenientRoster implements TypeAdapterFactory
	{
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			return list(gson, RelayResponse.RosterRow.class, SocialWire::row);
		}
	}

	/** Gson's hook for {@code Directive.events}. */
	public static final class LenientMeetups implements TypeAdapterFactory
	{
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			return list(gson, RelayResponse.Meetup.class, SocialWire::meetup);
		}
	}

	private interface Clean<R>
	{
		@Nullable
		R clean(@Nullable R row);
	}

	@SuppressWarnings("unchecked")
	private static <T, R> TypeAdapter<T> list(Gson gson, Class<R> rowType, Clean<R> clean)
	{
		TypeAdapter<R> rows = gson.getAdapter(rowType);
		TypeAdapter<JsonElement> whole = gson.getAdapter(JsonElement.class);
		int cap = rowType == RelayResponse.RosterRow.class ? MAX_ROSTER : MAX_MEETUPS;
		return (TypeAdapter<T>) new TypeAdapter<List<R>>()
		{
			@Override
			public void write(JsonWriter out, List<R> value) throws IOException
			{
				if (value == null)
				{
					out.nullValue();
					return;
				}
				out.beginArray();
				for (R row : value)
				{
					rows.write(out, row);
				}
				out.endArray();
			}

			@Override
			public List<R> read(JsonReader in) throws IOException
			{
				JsonElement tree = whole.read(in);
				if (tree == null || !tree.isJsonArray())
				{
					return null;
				}
				List<R> kept = new ArrayList<>();
				for (JsonElement element : tree.getAsJsonArray())
				{
					if (kept.size() >= cap)
					{
						break;
					}
					try
					{
						R row = clean.clean(rows.fromJsonTree(element));
						if (row != null)
						{
							kept.add(row);
						}
					}
					catch (RuntimeException unreadable)
					{
						/* This row only; the next is independent. */
					}
				}
				return kept;
			}
		};
	}

	@Nullable
	static RelayResponse.RosterRow row(@Nullable RelayResponse.RosterRow row)
	{
		if (row == null)
		{
			return null;
		}
		String id = id(row.id);
		if (id == null)
		{
			return null;
		}
		RelayResponse.RosterRow clean = new RelayResponse.RosterRow();
		clean.id = id;
		String name = RouteWire.text(row.name, MAX_NAME);
		clean.name = name == null ? "Member" : name;
		clean.colour = colour(row.colour);
		clean.presence = row.presence != null && PRESENCES.contains(row.presence) ? row.presence : null;
		clean.world = row.world != null && row.world > 0 && row.world <= 2500 ? row.world : null;
		clean.near = RouteWire.text(row.near, MAX_NEAR);
		clean.nearYou = Boolean.TRUE.equals(row.nearYou) ? Boolean.TRUE : null;
		clean.routable = row.routable;
		if (row.news != null)
		{
			String title = RouteWire.text(row.news.title, MAX_NEWS);
			if (title != null && row.news.atMs > 0)
			{
				RelayResponse.News news = new RelayResponse.News();
				news.kind = RouteWire.text(row.news.kind, 20);
				news.title = title;
				news.atMs = row.news.atMs;
				news.skill = RouteWire.text(row.news.skill, 20);
				news.itemId = row.news.itemId != null && row.news.itemId > 0 ? row.news.itemId : null;
				clean.news = news;
			}
		}
		return clean;
	}

	@Nullable
	static RelayResponse.Meetup meetup(@Nullable RelayResponse.Meetup meetup)
	{
		if (meetup == null)
		{
			return null;
		}
		String id = id(meetup.id);
		String title = RouteWire.text(meetup.title, MAX_TITLE);
		if (id == null || title == null || meetup.startsAtMs <= 0
			|| meetup.x <= 0 || meetup.y <= 0 || meetup.x > MAX_COORDINATE || meetup.y > MAX_COORDINATE
			|| meetup.plane < 0 || meetup.plane > 3)
		{
			return null;
		}
		RelayResponse.Meetup clean = new RelayResponse.Meetup();
		clean.id = id;
		clean.title = title;
		clean.x = meetup.x;
		clean.y = meetup.y;
		clean.plane = meetup.plane;
		clean.world = meetup.world != null && meetup.world > 0 && meetup.world <= 2500 ? meetup.world : null;
		clean.startsAtMs = meetup.startsAtMs;
		clean.near = RouteWire.text(meetup.near, MAX_NEAR);
		clean.mine = meetup.mine != null && ANSWERS.contains(meetup.mine) ? meetup.mine : null;
		List<RelayResponse.MeetupAnswer> who = new ArrayList<>();
		if (meetup.who != null)
		{
			for (RelayResponse.MeetupAnswer face : meetup.who)
			{
				if (face == null || face.answer == null || !ROLL_UP.contains(face.answer) || who.size() >= MAX_ROSTER + 1)
				{
					continue;
				}
				RelayResponse.MeetupAnswer kept = new RelayResponse.MeetupAnswer();
				String name = RouteWire.text(face.name, MAX_NAME);
				kept.name = name == null ? "Member" : name;
				kept.colour = colour(face.colour);
				kept.answer = face.answer;
				kept.you = Boolean.TRUE.equals(face.you) ? Boolean.TRUE : null;
				who.add(kept);
			}
		}
		clean.who = who;
		return clean;
	}

	@Nullable
	private static String colour(@Nullable String colour)
	{
		return colour != null && COLOUR.matcher(colour).matches() ? colour : null;
	}

	@Nullable
	private static String id(@Nullable String id)
	{
		if (id == null || id.isEmpty() || id.length() > MAX_ID)
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
		return id;
	}
}
