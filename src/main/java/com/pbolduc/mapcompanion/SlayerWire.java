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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** The site's ranked answer, bounded and cleaned independently of the surrounding reply. */
final class SlayerWire
{
	static final int MAX_SPOTS = 80;
	static final int MAX_KINDS = 128;

	public static final class Lenient implements TypeAdapterFactory
	{
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			return type.getRawType() != RelayResponse.SlayerOptions.class ? null : SocialWire.object(gson, RelayResponse.SlayerOptions.class, SlayerWire::normalize);
		}
	}

	@Nullable
	static RelayResponse.SlayerOptions normalize(@Nullable RelayResponse.SlayerOptions value)
	{
		if (value == null || RouteWire.text(value.taskName, 100) == null || value.rows == null
			|| !Arrays.asList("best", "single").contains(value.order))
		{
			return null;
		}
		value.taskName = RouteWire.text(value.taskName, 100);
		value.areaName = RouteWire.text(value.areaName, 100);
		List<RelayResponse.SlayerSpot> rows = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (RelayResponse.SlayerSpot row : value.rows.subList(0, Math.min(MAX_SPOTS, value.rows.size())))
		{
			if (row == null || row.id == null || row.id.length() > 200
				|| !row.id.matches("slayer:[^<>\\r\\n]{1,160}#[0-9]+:[0-9]+,[0-9]+,[0-3]")
				|| row.rank < 1 || row.rank > MAX_SPOTS || !ids.add(row.id))
			{
				continue;
			}
			row.name = RouteWire.text(row.name, 120);
			row.target = RouteWire.tile(row.target);
			if (row.name == null || row.target == null)
			{
				continue;
			}
			if (row.target.plane == null)
			{
				row.target.plane = 0;
			}
			row.tier = tier(row.tier);
			row.kinds = words(row.kinds, 32, 100);
			row.labels = words(row.labels, 12, 160);
			List<RelayResponse.SlayerAsk> asks = new ArrayList<>();
			if (row.asks != null)
				for (RelayResponse.SlayerAsk ask : row.asks.subList(0, Math.min(24, row.asks.size())))
				{
					if (ask == null || (ask.text = RouteWire.text(ask.text, 200)) == null)
					{
						continue;
					}
					ask.tier = tier(ask.tier);
					ask.skill = RouteWire.text(ask.skill, 24);
					asks.add(ask);
				}
			row.asks = asks;
			rows.add(row);
		}
		value.rows = rows;
		value.ranked = Math.max(0, Math.min(10_000, value.ranked));
		value.hiddenWilderness = Math.max(0, Math.min(10_000, value.hiddenWilderness));
		value.rate = RouteWire.text(value.rate, 300);
		value.page = RouteWire.text(value.page, 160);
		value.note = RouteWire.text(value.note, 300);
		value.notes = words(value.notes, 12, 300);
		List<RelayResponse.SlayerKind> kinds = new ArrayList<>();
		if (value.kinds != null)
			for (RelayResponse.SlayerKind kind : value.kinds.subList(0, Math.min(MAX_KINDS, value.kinds.size())))
			{
				if (kind == null || (kind.name = RouteWire.text(kind.name, 100)) == null)
				{
					continue;
				}
				kind.facts = RouteWire.text(kind.facts, 200);
				kind.best = RouteWire.text(kind.best, 160);
				kinds.add(kind);
			}
		value.kinds = kinds;
		List<RelayResponse.SlayerBoss> bosses = new ArrayList<>();
		if (value.bosses != null)
			for (RelayResponse.SlayerBoss boss : value.bosses.subList(0, Math.min(32, value.bosses.size())))
			{
				if (boss == null || (boss.name = RouteWire.text(boss.name, 100)) == null)
				{
					continue;
				}
				boss.where = RouteWire.text(boss.where, 160);
				boss.entrance = RouteWire.tile(boss.entrance);
				if (boss.entrance != null && boss.entrance.plane == null)
				{
					boss.entrance.plane = 0;
				}
				bosses.add(boss);
			}
		value.bosses = bosses;
		return value;
	}

	private static String tier(String value)
	{
		return Arrays.asList("met", "unmet").contains(value) ? value : "unknown";
	}
	private static List<String> words(@Nullable List<String> values, int count, int length)
	{
		List<String> result = new ArrayList<>();
		if (values != null)
			for (String value : values.subList(0, Math.min(count, values.size())))
			{
				String clean = RouteWire.text(value, length);
				if (clean != null)
				{
					result.add(clean);
				}
			}
		return result;
	}
}
