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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The map's ordered run, bounded independently of the envelope it arrived in.
 *
 * <p>A bad run must not lose a pairing secret or a reward acknowledgement beside it. The field's
 * adapter consumes the whole value before decoding and leaves unreadable runs absent. Patch ids
 * describe evidence, not game actions. Unknown ids are kept but cannot produce local receipts.
 *
 * <p>One kind is not a farm run: {@code port-tasks}, the Sailing port tasks this character holds.
 * Its stops carry task ticks instead of patch ids, and the voyage each leg sails. A tick naming a
 * counter this build does not know is dropped rather than read as some other counter.
 */
final class FarmRunWire
{
	static final int MAX_STOPS = 40;
	static final int MAX_STEPS = 8;
	/** Hosidius has twelve grape patches, which must all finish before its stop is done. */
	static final int MAX_PATCH_IDS = 16;
	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{4,64}");
	private static final Pattern PATCH = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
	/** The one run kind that is not a farm run: the Sailing port tasks this character holds. */
	static final String PORT_TASKS = "port-tasks";
	static final List<String> KINDS = Arrays.asList("sent", "ready-now", "preset", "birdhouses", "contract", PORT_TASKS);
	/** The slot counters a port-task stop is ticked by; see {@link RelayResponse.RunTask}. */
	static final List<String> COUNTERS = Arrays.asList("taken", "delivered", "remaining", "held");
	/** Five task slots, each served at most twice at one port: a pickup and a drop-off. */
	static final int MAX_TICKS = 10;

	/** A malformed counter is unknown, not a reason to lose the surrounding pairing reply. */
	public static final class Revision implements TypeAdapterFactory
	{
		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			if (type.getRawType() != long.class && type.getRawType() != Long.class)
			{
				return null;
			}
			TypeAdapter<JsonElement> whole = gson.getAdapter(JsonElement.class);
			return (TypeAdapter<T>) new TypeAdapter<Long>()
			{
				@Override
				public void write(JsonWriter out, Long value) throws IOException
				{
					out.value(value);
				}

				@Override
				public Long read(JsonReader in) throws IOException
				{
					JsonElement value = whole.read(in);
					try
					{
						if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
						{
							return 0L;
						}
						long revision = value.getAsBigDecimal().longValueExact();
						return revision > 0 && revision <= 9_007_199_254_740_991L ? revision : 0L;
					}
					catch (ArithmeticException | NumberFormatException unreadable)
					{
						return 0L;
					}
				}
			};
		}
	}

	public static final class Lenient implements TypeAdapterFactory
	{
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			return type.getRawType() != RelayResponse.FarmRun.class ? null : SocialWire.object(gson, RelayResponse.FarmRun.class, FarmRunWire::normalize);
		}
	}

	@Nullable
	static RelayResponse.FarmRun normalize(@Nullable RelayResponse.FarmRun run)
	{
		if (run == null || run.id == null || !ID.matcher(run.id).matches() || run.stops == null
			|| !KINDS.contains(run.kind)
			|| run.startedAtMs <= 0 || run.startedAtMs > 9_007_199_254_740_991L)
		{
			return null;
		}
		String name = RouteWire.text(run.name, 60);
		if (name == null)
		{
			return null;
		}
		List<RelayResponse.FarmStop> stops = new ArrayList<>();
		Set<Integer> taken = new HashSet<>();
		for (RelayResponse.FarmStop stop : RouteWire.first(run.stops, MAX_STOPS))
		{
			RelayResponse.FarmStop clean = stop(stop);
			if (clean != null && taken.add(clean.n))
			{
				stops.add(clean);
			}
		}
		if (stops.isEmpty())
		{
			return null;
		}
		stops.sort(Comparator.comparingInt(stop -> stop.n));
		RelayResponse.FarmRun clean = new RelayResponse.FarmRun();
		clean.id = run.id;
		clean.kind = run.kind;
		clean.name = name;
		clean.startedAtMs = run.startedAtMs;
		clean.stops = stops;
		clean.bring = new ArrayList<>();
		if (run.bring != null)
		{
			for (RelayResponse.FarmBring line : run.bring.subList(0, Math.min(24, run.bring.size())))
			{
				if (line == null || !Arrays.asList("teleport", "supply").contains(line.kind))
				{
					continue;
				}
				String item = RouteWire.text(line.name, 80);
				if (item == null)
				{
					continue;
				}
				RelayResponse.FarmBring kept = new RelayResponse.FarmBring();
				kept.kind = line.kind;
				kept.name = item;
				kept.note = RouteWire.text(line.note, 120);
				kept.uses = line.uses != null && line.uses >= 1 && line.uses <= MAX_STOPS ? line.uses : null;
				clean.bring.add(kept);
			}
		}
		return clean;
	}

	@Nullable
	private static RelayResponse.FarmStop stop(@Nullable RelayResponse.FarmStop stop)
	{
		if (stop == null || stop.n < 1 || stop.n > MAX_STOPS)
		{
			return null;
		}
		String name = RouteWire.text(stop.name, 60);
		RelayResponse.RouteTile tile = RouteWire.tile(stop.tile);
		if (name == null || tile == null)
		{
			return null;
		}
		RelayResponse.FarmStop clean = new RelayResponse.FarmStop();
		clean.n = stop.n;
		clean.name = name;
		clean.tile = tile;
		clean.patchIds = new ArrayList<>();
		if (stop.patchIds != null)
		{
			for (String id : stop.patchIds)
			{
				if (id != null && id.length() <= 80 && PATCH.matcher(id).matches()
					&& !clean.patchIds.contains(id) && clean.patchIds.size() < MAX_PATCH_IDS)
				{
					clean.patchIds.add(id);
				}
			}
		}
		clean.birdhouseId = stop.birdhouseId != null && stop.birdhouseId.length() <= 40
							&& PATCH.matcher(stop.birdhouseId).matches() ? stop.birdhouseId : null;
		clean.method = RouteWire.text(stop.method, 120);
		clean.item = RouteWire.text(stop.item, 80);
		clean.costTiles = Math.max(0, stop.costTiles);
		clean.tasks = RouteWire.cleaned(stop.tasks, MAX_TICKS, FarmRunWire::tick);
		clean.voyage = RouteWire.voyage(stop.voyage);
		boolean tracked = !clean.patchIds.isEmpty() || clean.birdhouseId != null || !clean.tasks.isEmpty();
		clean.done = stop.done && tracked;
		clean.visited = stop.visited && !tracked;
		clean.steps = RouteWire.cleaned(stop.steps, MAX_STEPS, RouteWire::step);
		return clean;
	}

	@Nullable
	private static RelayResponse.RunTask tick(@Nullable RelayResponse.RunTask tick)
	{
		String text = tick == null ? null : RouteWire.text(tick.text, 120);
		if (text == null || tick.slot < 0 || tick.slot >= PortTaskReader.SLOT_IDS.length || tick.taskId <= 0 || tick.taskId > 100_000
			|| !COUNTERS.contains(tick.counter) || tick.n < 0 || tick.n > 255)
		{
			return null;
		}
		RelayResponse.RunTask clean = new RelayResponse.RunTask();
		clean.slot = tick.slot;
		clean.taskId = tick.taskId;
		clean.counter = tick.counter;
		clean.n = tick.n;
		clean.ends = tick.ends;
		clean.text = text;
		return clean;
	}

	private FarmRunWire() {}
}
