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
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Reading a route from the map without letting a bad one cost the reply it rides on.
 *
 * <p>A route arrives inside a snapshot reply and a stream frame, beside the claim receipt, the
 * member secret and the milestone acknowledgements. Gson throws on the first value of the wrong
 * kind, and a throw there loses all of it: the reply would be read as a failure and the
 * destination would not be drawn at all. So the route is read on its own — the value whole, then
 * the fields — and a route that cannot be read is null while everything around it stands.
 *
 * <p>Then it is bounded, the way the map bounds it before sending: every string folded and cut,
 * a step without a kind or words dropped, a way without steps dropped, a tile off the map or on no
 * floor dropped while its words stay, at most {@link #MAX_WAYS} ways of at most
 * {@link #MAX_STEPS} steps, and exactly one way marked best. The map already does this, and does
 * it again here because the text is typed by somebody in the group and drawn by this client.
 *
 * <p>A step's kind is any short lowercase word rather than a list of the ones known today, as on
 * the map: a kind added there tomorrow is printed here rather than dropped.
 */
final class RouteWire
{
	static final int MAX_WAYS = 4;
	static final int MAX_STEPS = 10;
	private static final int MAX_NOTES = 4;
	private static final int MAX_ID = 160;
	private static final int MAX_LABEL = 120;
	private static final int MAX_TITLE = 240;
	private static final int MAX_DETAIL = 200;
	private static final int MAX_NOTE = 200;
	/** The game's tile space, overworld and the underground beside it. */
	private static final int MAX_COORDINATE = 16_383;
	private static final Pattern SLOT = Pattern.compile("[a-z][a-z-]{0,31}");

	private RouteWire()
	{
	}

	/**
	 * Gson's hook for the one field, named by {@code @JsonAdapter} on {@code Destination.route}.
	 *
	 * <p>A factory on the field rather than an adapter registered on a Gson of our own: the Hub
	 * asks plugins to use the client's Gson, and this keeps every other field read exactly as it
	 * was. The class itself carries no annotation, so asking the Gson for a {@code Route} adapter
	 * here gets the ordinary reflective one and does not come back to this.
	 */
	public static final class Lenient implements TypeAdapterFactory
	{
		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			if (type.getRawType() != RelayResponse.Route.class)
			{
				return null;
			}
			TypeAdapter<RelayResponse.Route> fields = gson.getAdapter(RelayResponse.Route.class);
			TypeAdapter<JsonElement> whole = gson.getAdapter(JsonElement.class);
			return (TypeAdapter<T>) new TypeAdapter<RelayResponse.Route>()
			{
				@Override
				public void write(JsonWriter out, RelayResponse.Route value) throws IOException
				{
					fields.write(out, value);
				}

				@Override
				public RelayResponse.Route read(JsonReader in) throws IOException
				{
					/* The whole value first, so a bad field inside it leaves the reader where it should be. */
					JsonElement tree = whole.read(in);
					try
					{
						return tree == null || tree.isJsonNull() ? null : normalize(fields.fromJsonTree(tree));
					}
					catch (RuntimeException unreadable)
					{
						return null;
					}
				}
			};
		}
	}

	/** A route made safe to keep and to draw, or null when nothing in it is a way. */
	@Nullable
	static RelayResponse.Route normalize(@Nullable RelayResponse.Route route)
	{
		if (route == null || route.options == null)
		{
			return null;
		}
		List<RelayResponse.RouteOption> kept = new ArrayList<>();
		for (RelayResponse.RouteOption option : route.options)
		{
			RelayResponse.RouteOption clean = option(option);
			if (clean != null)
			{
				kept.add(clean);
			}
		}
		if (kept.isEmpty())
		{
			return null;
		}
		int bestAt = 0;
		for (int i = 0; i < kept.size(); i++)
		{
			if (kept.get(i).best)
			{
				bestAt = i;
				break;
			}
		}
		List<RelayResponse.RouteOption> ways = new ArrayList<>();
		for (int i = 0; i < kept.size(); i++)
		{
			RelayResponse.RouteOption option = kept.get(i);
			option.best = i == bestAt;
			/* The best way is always kept, so until it is reached one place is held for it. */
			int room = i < bestAt ? MAX_WAYS - 1 : MAX_WAYS;
			if (option.best || ways.size() < room)
			{
				ways.add(option);
			}
		}
		RelayResponse.Route clean = new RelayResponse.Route();
		clean.origin = "shared".equals(route.origin) || "request".equals(route.origin) ? route.origin : null;
		clean.options = ways;
		return clean;
	}

	@Nullable
	private static RelayResponse.RouteOption option(@Nullable RelayResponse.RouteOption option)
	{
		if (option == null || option.steps == null)
		{
			return null;
		}
		String id = option.id;
		if (id == null || id.isEmpty() || id.length() > MAX_ID || id.chars().anyMatch(RouteWire::control))
		{
			return null;
		}
		String label = text(option.label, MAX_LABEL);
		if (label == null)
		{
			return null;
		}
		List<RelayResponse.RouteStep> steps = new ArrayList<>();
		for (RelayResponse.RouteStep step : option.steps)
		{
			RelayResponse.RouteStep clean = step(step);
			if (clean != null && steps.size() < MAX_STEPS)
			{
				steps.add(clean);
			}
		}
		if (steps.isEmpty())
		{
			return null;
		}
		RelayResponse.RouteOption clean = new RelayResponse.RouteOption();
		clean.id = id;
		clean.label = label;
		clean.best = option.best;
		clean.blocked = option.blocked;
		clean.tiles = option.tiles != null && option.tiles >= 0 ? option.tiles : null;
		clean.steps = steps;
		return clean;
	}

	@Nullable
	private static RelayResponse.RouteStep step(@Nullable RelayResponse.RouteStep step)
	{
		if (step == null || step.slot == null || !SLOT.matcher(step.slot).matches())
		{
			return null;
		}
		String title = text(step.title, MAX_TITLE);
		if (title == null)
		{
			return null;
		}
		RelayResponse.RouteStep clean = new RelayResponse.RouteStep();
		clean.slot = step.slot;
		clean.title = title;
		clean.detail = text(step.detail, MAX_DETAIL);
		List<String> notes = new ArrayList<>();
		if (step.notes != null)
		{
			for (String note : step.notes)
			{
				String kept = text(note, MAX_NOTE);
				if (kept != null && notes.size() < MAX_NOTES)
				{
					notes.add(kept);
				}
			}
		}
		clean.notes = notes.isEmpty() ? null : notes;
		clean.walk = step.walk;
		clean.at = tile(step.at);
		return clean;
	}

	/** A step's end on the map and on one of the four floors, or null: the words stay, the pin goes. */
	@Nullable
	private static RelayResponse.RouteTile tile(@Nullable RelayResponse.RouteTile at)
	{
		if (at == null || at.x <= 0 || at.y <= 0 || at.x > MAX_COORDINATE || at.y > MAX_COORDINATE
			|| (at.plane != null && (at.plane < 0 || at.plane > 3)))
		{
			return null;
		}
		RelayResponse.RouteTile clean = new RelayResponse.RouteTile();
		clean.x = at.x;
		clean.y = at.y;
		clean.plane = at.plane;
		return clean;
	}

	/** Control bytes out, whitespace runs to one space, cut to a length; null when nothing is left. */
	@Nullable
	static String text(@Nullable String value, int max)
	{
		if (value == null)
		{
			return null;
		}
		StringBuilder folded = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			folded.append(control(c) ? ' ' : c);
		}
		String kept = folded.toString().replaceAll("\\s+", " ").trim();
		if (kept.length() > max)
		{
			kept = kept.substring(0, max).trim();
		}
		return kept.isEmpty() ? null : kept;
	}

	private static boolean control(int c)
	{
		return c < 0x20 || c == 0x7f;
	}
}
