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
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import java.util.function.UnaryOperator;
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
 * the map: a kind added there tomorrow is printed here rather than dropped. So is a step's picture an
 * id rather than a list: the map names the item or the spell, and an id the client's cache has no
 * picture for draws nothing.
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
	/** Above every item and sprite id the game has: a bound, as on the map, not a list of ids. */
	static final int MAX_GAME_ID = 1_000_000;
	private static final Pattern SLOT = Pattern.compile("[a-z][a-z-]{0,31}");
	/** The map's cap on a voyage's line: a few dozen tiles is what a simplified sail needs. */
	static final int MAX_VOYAGE_POINTS = 64;
	private static final int MAX_GATES = 4;
	private static final int MAX_GATE = 80;
	/** A mooring's name, as the map caps a boat's port. */
	private static final int MAX_PORT = 48;
	/** The rows of a voyage: told and ticked here, never walked, so never Shortest Path's. */
	static final String BOARD = "board";
	static final String SAIL = "sail";
	static final String DOCK = "dock";

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
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			return type.getRawType() != RelayResponse.Route.class ? null : SocialWire.object(gson, RelayResponse.Route.class, RouteWire::normalize);
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
		List<RelayResponse.RouteOption> kept = cleaned(route.options, Integer.MAX_VALUE, RouteWire::option);
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

	/**
	 * Each of {@code from} that {@code clean} keeps, the first {@code max} of them: every list the
	 * map sends is read this way, so a bad entry costs only itself and a long list only its tail.
	 */
	static <T> List<T> cleaned(@Nullable List<T> from, int max, UnaryOperator<T> clean)
	{
		List<T> out = new ArrayList<>();
		if (from != null)
		{
			for (T item : from)
			{
				T kept = clean.apply(item);
				if (kept != null && out.size() < max)
				{
					out.add(kept);
				}
			}
		}
		return out;
	}

	/** The first {@code max} of a list the map sent, before any is read; none for a list it left out. */
	static <T> List<T> first(@Nullable List<T> from, int max)
	{
		return from == null ? Collections.emptyList() : from.subList(0, Math.min(max, from.size()));
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
		List<RelayResponse.RouteStep> steps = cleaned(option.steps, MAX_STEPS, RouteWire::step);
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
		clean.voyage = voyage(option.voyage);
		return clean;
	}

	/** A step of a voyage, which is told and ticked here and never handed to Shortest Path. */
	static boolean voyageSlot(@Nullable String slot)
	{
		return BOARD.equals(slot) || SAIL.equals(slot) || DOCK.equals(slot);
	}

	/**
	 * A voyage made safe to keep and to draw, or null: a voyage needs the mooring it leaves from. A
	 * line point off the map goes, and a line of fewer than two points goes whole; a gate whose tier
	 * is not one of the three goes, because a tier this cannot draw is one it would draw wrong.
	 */
	@Nullable
	static RelayResponse.Voyage voyage(@Nullable RelayResponse.Voyage voyage)
	{
		String from = voyage == null ? null : text(voyage.from, MAX_PORT);
		if (from == null)
		{
			return null;
		}
		RelayResponse.Voyage clean = new RelayResponse.Voyage();
		clean.from = from;
		clean.to = text(voyage.to, MAX_PORT);
		List<int[]> points = new ArrayList<>();
		if (voyage.line != null)
		{
			for (int[] point : voyage.line)
			{
				if (point != null && point.length == 2 && point[0] > 0 && point[1] > 0
					&& point[0] <= MAX_COORDINATE && point[1] <= MAX_COORDINATE && points.size() < MAX_VOYAGE_POINTS)
				{
					points.add(new int[]{point[0], point[1]});
				}
			}
		}
		clean.line = points.size() < 2 ? null : points.toArray(new int[0][]);
		List<RelayResponse.Gate> gates = cleaned(voyage.gates, MAX_GATES, RouteWire::gate);
		clean.gates = gates.isEmpty() ? null : gates;
		return clean;
	}

	/** A gate on a voyage as the map may send one: its words and one of the three tiers it knows. */
	@Nullable
	private static RelayResponse.Gate gate(@Nullable RelayResponse.Gate gate)
	{
		String said = gate == null ? null : text(gate.text, MAX_GATE);
		if (said == null || !Arrays.asList("met", "unmet", "unknown").contains(gate.tier))
		{
			return null;
		}
		RelayResponse.Gate kept = new RelayResponse.Gate();
		kept.text = said;
		kept.tier = gate.tier;
		return kept;
	}

	@Nullable
	static RelayResponse.RouteStep step(@Nullable RelayResponse.RouteStep step)
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
		List<String> notes = cleaned(step.notes, MAX_NOTES, note -> text(note, MAX_NOTE));
		clean.notes = notes.isEmpty() ? null : notes;
		/* A voyage's rows are sailed, never walked, whatever the wire says: Shortest Path cannot sail. */
		clean.walk = step.walk && !voyageSlot(step.slot);
		/* One picture a step, the item before the spell: decoration, so a bad id costs only itself. */
		clean.itemId = gameId(step.itemId);
		clean.spriteId = clean.itemId == null ? gameId(step.spriteId) : null;
		clean.at = tile(step.at);
		return clean;
	}

	/** A positive id under the bound, or null. */
	@Nullable
	static Integer gameId(@Nullable Integer id)
	{
		return id != null && id > 0 && id <= MAX_GAME_ID ? id : null;
	}

	/** A step's end on the map and on one of the four floors, or null: the words stay, the pin goes. */
	@Nullable
	static RelayResponse.RouteTile tile(@Nullable RelayResponse.RouteTile at)
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
		String kept = oneLine(value);
		if (kept.length() > max)
		{
			kept = kept.substring(0, max).trim();
		}
		return kept.isEmpty() ? null : kept;
	}

	/** A run of control bytes and whitespace, as {@link #oneLine} closes it up. Compiled once: a label is tidied every frame. */
	private static final Pattern BLANK = Pattern.compile("[\\p{Cntrl}\\s]+");

	/**
	 * Text somebody else typed, as one line: each run of control bytes and whitespace a single space,
	 * and none at either end. Never null. A name, a title and a search are all tidied by this.
	 */
	static String oneLine(@Nullable String value)
	{
		return value == null ? "" : BLANK.matcher(value).replaceAll(" ").trim();
	}

	private static boolean control(int c)
	{
		return c < 0x20 || c == 0x7f;
	}
}
