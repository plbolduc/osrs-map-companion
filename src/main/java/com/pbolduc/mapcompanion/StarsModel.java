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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Stars tab's local view of the map's resolved feed. The server supplies site identities,
 * world gates and risk classifications; this client only applies the same independent filter
 * axes. Account levels never leave RuneLite merely because its reader opens this list.
 * Relative durations age on a monotonic clock, so a clock correction cannot revive a star.
 */
final class StarsModel
{
	static final long TIER_MS = 7 * 60_000L;
	static final class Filters
	{
		int minTier = 1;
		boolean showMembers = true;
		boolean showFreeToPlay;
		boolean hideWilderness;
		boolean hidePvpWorlds;
		boolean hideDeadmanWorlds;
		boolean hideDepleted = true;
		String leagueWorlds = "exclude";

		boolean safe()
		{
			return hideWilderness && hidePvpWorlds && hideDeadmanWorlds;
		}

		void safe(boolean on)
		{
			hideWilderness = on;
			hidePvpWorlds = on;
			hideDeadmanWorlds = on;
		}
	}

	static final class Favorite
	{
		String key;
		String name;
		String siteKey;

		Favorite(String key, String name)
		{
			this.key = key;
			this.name = name;
		}
	}

	static final class Preferences
	{
		Filters filters = new Filters();
		List<Favorite> favorites = new ArrayList<>();
	}

	static final class PreferenceReply
	{
		Long revision;
		Preferences preferences;
	}

	static final class Feed
	{
		int version;
		boolean worldsAvailable;
		boolean leagueSeason;
		boolean stale;
		long snapshotAgeSeconds;
		long pollIntervalMs = 60_000;
		List<Star> rows;
	}

	static final class Star
	{
		String id;
		String siteKey;
		String name;
		String favoriteKey;
		int rank;
		int world;
		int calledTier;
		long ageMs;
		long remainingMs;
		boolean precise;
		int x, y, plane;
		Boolean members;
		int requiredTotal;
		boolean wilderness, pvp, deadman, league;
		String country;

		int tier(long elapsed)
		{
			return Math.max(1, Math.min(9, calledTier) - (int) ((Math.max(0, ageMs) + elapsed) / TIER_MS));
		}

		long left(long elapsed)
		{
			return remainingMs - elapsed;
		}
		boolean valid() { return id != null && siteKey != null && name != null && name.length() <= 200
			&& favoriteKey != null && world >= 300 && world <= 2000 && calledTier >= 1 && calledTier <= 9
			&& ageMs >= 0 && ageMs < 7 * 24 * 60 * 60_000L && remainingMs <= 9 * TIER_MS;
			}
	}

	static final class Site
	{
		final String key, name;
		final boolean favorite;
		final List<Star> worlds = new ArrayList<>();
		boolean hiddenByFilters;

		Site(String key, String name, boolean favorite)
		{
			this.key = key;
			this.name = name;
			this.favorite = favorite;
		}
	}

	Preferences preferences = new Preferences();
	Feed feed;
	String selected;
	Integer miningLevel, skillTotal;
	private long receivedNanos;

	void accept(Feed incoming)
	{
		if (incoming == null || incoming.version != 1 || incoming.rows == null || incoming.rows.size() > 2000)
			throw new IllegalArgumentException("Unreadable star feed");
		List<Star> valid = new ArrayList<>();
		for (Star star : incoming.rows) if (star != null && star.valid()) valid.add(star);
		if (!incoming.rows.isEmpty() && valid.isEmpty()) throw new IllegalArgumentException("No readable stars");
		incoming.rows = valid;
		feed = incoming;
		receivedNanos = System.nanoTime();
		if (find(selected) == null) selected = valid.isEmpty() ? null : valid.get(0).id;
	}

	long elapsed()
	{
		return feed == null ? 0 : Math.max(0, (System.nanoTime() - receivedNanos) / 1_000_000L);
	}

	Star find(String id)
	{
		if (feed != null && id != null) for (Star star : feed.rows) if (id.equals(star.id)) return star;
		return null;
	}

	boolean passes(Star star, long elapsed)
	{
		Filters f = preferences.filters;
		int tier = star.tier(elapsed);
		if (tier < f.minTier || miningLevel != null && tier * 10 > miningLevel) return false;
		if (f.hideDepleted && star.left(elapsed) <= 0 || f.hideWilderness && star.wilderness
			|| f.hidePvpWorlds && star.pvp || f.hideDeadmanWorlds && star.deadman) return false;
		if (feed.leagueSeason && ("only".equals(f.leagueWorlds) != star.league)) return false;
		if (star.members == null) return true;
		if (skillTotal != null && star.requiredTotal > skillTotal) return false;
		return star.members ? f.showMembers : f.showFreeToPlay;
	}

	boolean favorite(String key, String tile)
	{
		for (Favorite favorite : preferences.favorites) if (favorite.key.equals(key) || tile.equals(favorite.siteKey)) return true;
		return false;
	}

	void toggleFavorite(String key, String name, String tile)
	{
		if (!preferences.favorites.removeIf(favorite -> favorite.key.equals(key) || tile != null && tile.equals(favorite.siteKey)) && preferences.favorites.size() < 200)
		{
			Favorite favorite = new Favorite(key, name);
			favorite.siteKey = tile;
			preferences.favorites.add(favorite);
		}
	}

	List<Site> sites()
	{
		if (feed == null) return Collections.emptyList();
		long elapsed = elapsed();
		List<Star> ranked = new ArrayList<>(feed.rows);
		ranked.sort(Comparator.comparingLong((Star star) -> star.left(elapsed)).reversed().thenComparingInt(star -> star.rank));
		Map<String, Site> groups = new LinkedHashMap<>();
		for (Star star : ranked)
		{
			if (!passes(star, elapsed)) continue;
			Site site = groups.computeIfAbsent(star.siteKey, key -> new Site(key, star.name, favorite(star.favoriteKey, star.siteKey)));
			site.worlds.add(star);
		}
		List<Site> favorites = new ArrayList<>(), rest = new ArrayList<>();
		for (Site site : groups.values()) (site.favorite ? favorites : rest).add(site);
		for (Favorite favorite : preferences.favorites)
		{
			boolean visible = favorites.stream().anyMatch(site -> site.worlds.stream().anyMatch(star -> star.favoriteKey.equals(favorite.key) || star.siteKey.equals(favorite.siteKey)));
			if (visible) continue;
			Site cold = new Site("favorite:" + favorite.key, favorite.name, true);
			cold.hiddenByFilters = ranked.stream().anyMatch(star -> star.favoriteKey.equals(favorite.key) || star.siteKey.equals(favorite.siteKey));
			favorites.add(cold);
		}
		favorites.addAll(rest);
		return favorites;
	}

	static String minutes(long remaining)
	{
		return remaining <= 0 ? "Depleted" : "~" + Math.max(1, (remaining + 59_999) / 60_000) + "m";
	}
}
