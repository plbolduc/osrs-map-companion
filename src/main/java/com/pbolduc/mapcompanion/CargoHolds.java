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

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import static net.runelite.api.gameval.ObjectID.*;

/**
 * How many crates the hold of the boat you are standing on carries.
 *
 * <p>A courier run that collects every pickup before any drop-off is only a plan if the crates fit,
 * and the map must not assume they do. The hold says how many: its tier is the cargo hold object the
 * boat carries, and its size the hull's, both read as the Hub's Sailing plugin reads them
 * ({@code CargoHoldTier} and {@code SizeClass} at the commit pinned in the map's
 * {@code docs/review-evidence/sailing-reader-sources-2026-10-30.md}), and the capacities below are
 * that model's table. An Ultimate Ironman's hold carries the table's last column whatever its size.
 *
 * <p>Objects are kept per world view, because a boat is a world view of its own and every boat in
 * the scene spawns a hold. Only the one the local player stands in is believed: that is the boat
 * being sailed, and it is remembered as the last boat aboard until the session ends. A boat with no
 * hold, or one never stood on this session, has no capacity rather than zero; the map then plans as
 * if nothing more than one task's crates fit.
 *
 * <p>Client thread only, like the spawn events that feed it.
 */
@Singleton
public class CargoHolds
{
	/** Tier by tier, each hold in its raft, skiff and sloop forms with and without cargo drawn. */
	static final int[][] HOLDS = {
		{SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT, SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_REGULAR_2X5, SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE, SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_OAK_RAFT, SAILING_BOAT_CARGO_HOLD_OAK_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_OAK_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_OAK_2X5, SAILING_BOAT_CARGO_HOLD_OAK_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_OAK_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_OAK_LARGE, SAILING_BOAT_CARGO_HOLD_OAK_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_OAK_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_TEAK_RAFT, SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_TEAK_2X5, SAILING_BOAT_CARGO_HOLD_TEAK_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_TEAK_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_TEAK_LARGE, SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT, SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5, SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE, SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT, SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5, SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE, SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT, SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5, SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE, SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_NO_CARGO},
		{SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_CARGO, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_CARGO, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_NO_CARGO,
			SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_CARGO, SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO}
	};

	/** Raft, skiff and sloop hulls, each in the seven woods. */
	static final int[][] HULLS = {
		{SAILING_BOAT_HULL_KANDARIN_1X3_WOOD, SAILING_BOAT_HULL_KANDARIN_1X3_OAK, SAILING_BOAT_HULL_KANDARIN_1X3_TEAK, SAILING_BOAT_HULL_KANDARIN_1X3_MAHOGANY,
			SAILING_BOAT_HULL_KANDARIN_1X3_CAMPHOR, SAILING_BOAT_HULL_KANDARIN_1X3_IRONWOOD, SAILING_BOAT_HULL_KANDARIN_1X3_ROSEWOOD},
		{SAILING_BOAT_HULL_KANDARIN_2X5_WOOD, SAILING_BOAT_HULL_KANDARIN_2X5_OAK, SAILING_BOAT_HULL_KANDARIN_2X5_TEAK, SAILING_BOAT_HULL_KANDARIN_2X5_MAHOGANY,
			SAILING_BOAT_HULL_KANDARIN_2X5_CAMPHOR, SAILING_BOAT_HULL_KANDARIN_2X5_IRONWOOD, SAILING_BOAT_HULL_KANDARIN_2X5_ROSEWOOD},
		{SAILING_BOAT_HULL_KANDARIN_3X8_WOOD, SAILING_BOAT_HULL_KANDARIN_3X8_OAK, SAILING_BOAT_HULL_KANDARIN_3X8_TEAK, SAILING_BOAT_HULL_KANDARIN_3X8_MAHOGANY,
			SAILING_BOAT_HULL_KANDARIN_3X8_CAMPHOR, SAILING_BOAT_HULL_KANDARIN_3X8_IRONWOOD, SAILING_BOAT_HULL_KANDARIN_3X8_ROSEWOOD}
	};

	/** Crates by tier: a raft's, a skiff's, a sloop's, then an Ultimate Ironman's at any size. */
	static final int[][] CAPACITY = {
		{20, 30, 40, 4},
		{30, 45, 60, 5},
		{45, 60, 75, 6},
		{60, 90, 120, 7},
		{80, 120, 160, 8},
		{105, 150, 210, 9},
		{120, 180, 240, 10},
	};

	private static final int ULTIMATE = 3;
	private static final Map<Integer, Integer> TIER_OF = index(HOLDS);
	private static final Map<Integer, Integer> SIZE_OF = index(HULLS);

	private final Map<Integer, Integer> tierByView = new HashMap<>();
	private final Map<Integer, Integer> sizeByView = new HashMap<>();
	/** {tier, size} of the boat last stood on, or null. */
	private int[] aboard;

	void spawned(int view, int objectId)
	{
		Integer tier = TIER_OF.get(objectId);
		if (tier != null)
		{
			tierByView.put(view, tier);
		}
		Integer size = SIZE_OF.get(objectId);
		if (size != null)
		{
			sizeByView.put(view, size);
		}
	}

	void despawned(int view, int objectId)
	{
		if (TIER_OF.containsKey(objectId))
		{
			tierByView.remove(view);
		}
		if (SIZE_OF.containsKey(objectId))
		{
			sizeByView.remove(view);
		}
	}

	void unloaded(int view)
	{
		tierByView.remove(view);
		sizeByView.remove(view);
	}

	/**
	 * The player is in world view {@code view}; a boat whose hold and hull are both in sight becomes
	 * the one remembered. Standing ashore changes nothing: the boat moored there is still the boat.
	 */
	void standingIn(int view, boolean topLevel)
	{
		if (topLevel)
		{
			return;
		}
		Integer tier = tierByView.get(view);
		Integer size = sizeByView.get(view);
		if (tier != null && size != null)
		{
			aboard = new int[] {tier, size};
		}
	}

	/** Crates the remembered boat's hold carries, or null when no boat with a hold was stood on. */
	@Nullable
	Integer capacity(boolean ultimate)
	{
		return aboard == null ? null : CAPACITY[aboard[0]][ultimate ? ULTIMATE : aboard[1]];
	}

	/** A new session or character: no boat has been seen yet. */
	void reset()
	{
		tierByView.clear();
		sizeByView.clear();
		aboard = null;
	}

	private static Map<Integer, Integer> index(int[][] rows)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (int i = 0; i < rows.length; i++)
		{
			for (int id : rows[i])
			{
				out.put(id, i);
			}
		}
		return out;
	}
}
