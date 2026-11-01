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
 */
final class FarmRunWire
{
	static final int MAX_STOPS = 40;
	static final int MAX_STEPS = 8;
	/** Hosidius has twelve grape patches, which must all finish before its stop is done. */
	static final int MAX_PATCH_IDS = 16;
	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{4,64}");
	private static final Pattern PATCH = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

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
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			if (type.getRawType() != RelayResponse.FarmRun.class)
			{
				return null;
			}
			TypeAdapter<RelayResponse.FarmRun> fields = gson.getAdapter(RelayResponse.FarmRun.class);
			TypeAdapter<JsonElement> whole = gson.getAdapter(JsonElement.class);
			return (TypeAdapter<T>) new TypeAdapter<RelayResponse.FarmRun>()
			{
				@Override
				public void write(JsonWriter out, RelayResponse.FarmRun value) throws IOException
				{
					fields.write(out, value);
				}

				@Override
				public RelayResponse.FarmRun read(JsonReader in) throws IOException
				{
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

	@Nullable
	static RelayResponse.FarmRun normalize(@Nullable RelayResponse.FarmRun run)
	{
		if (run == null || run.id == null || !ID.matcher(run.id).matches() || run.stops == null
			|| !Arrays.asList("sent", "ready-now", "preset", "birdhouses", "contract").contains(run.kind)
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
		for (RelayResponse.FarmStop stop : run.stops.subList(0, Math.min(MAX_STOPS, run.stops.size())))
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
		boolean tracked = !clean.patchIds.isEmpty() || clean.birdhouseId != null;
		clean.done = stop.done && tracked;
		clean.visited = stop.visited && !tracked;
		clean.steps = new ArrayList<>();
		if (stop.steps != null)
		{
			for (RelayResponse.RouteStep step : stop.steps)
			{
				RelayResponse.RouteStep kept = RouteWire.step(step);
				if (kept != null && clean.steps.size() < MAX_STEPS)
				{
					clean.steps.add(kept);
				}
			}
		}
		return clean;
	}

	private FarmRunWire() {}
}
