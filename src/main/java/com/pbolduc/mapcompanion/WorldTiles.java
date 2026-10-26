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

import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;

/**
 * Where a player actually is, in coordinates somebody else's map can draw.
 *
 * <p>Inside an instance the ordinary world location means nothing to anyone else: the client reports
 * a tile in the copy of the map the instance was built from, which on the real map is an empty field
 * in the middle of nowhere. The template tile is the version everybody agrees on, so it is resolved
 * where the client can and null is returned where it cannot. A boat's deck is the same kind of
 * private coordinate space, and {@link #of} says how that one is resolved.
 *
 * <p><b>Why this is shared rather than written twice.</b> It was written twice, and the second copy
 * was wrong: {@link DeathWatcher} recorded the raw world location, so dying anywhere instanced put
 * the marker in that empty field. Boss deaths are the ones worth a route back and almost all of them
 * are instanced, which made the feature wrong precisely where it mattered. One implementation, used
 * by both, is what stops that happening again.
 */
final class WorldTiles
{
	/** Whether the player is standing in an instance. */
	static boolean isInstance(Client client)
	{
		WorldView view = client.getTopLevelWorldView();
		return view != null && view.isInstance();
	}

	/**
	 * The player's position in real world coordinates, or null when it has none to express.
	 *
	 * <p><b>On a boat</b> the player is not standing in the world at all. Since Sailing a boat is a
	 * world of its own — a {@link WorldEntity} with a world view whose tiles are its deck — and a
	 * player aboard belongs to that view: the local tile carries the boat's view id, and the world
	 * location is worked out from the boat's scene rather than the sea's. That is a deck tile, the
	 * same one however far the boat has sailed, so a sailing friend's dot sat wherever the boat's
	 * scene is kept and never moved. The boat knows where on the sea its deck is, which is RuneLite's
	 * own way of putting a boat's tile on the minimap ({@code Perspective.localToMinimap}), and the
	 * answer is then an ordinary tile of the world around it, instanced or not. A boat the client no
	 * longer holds is null rather than the deck tile, for the reason an unresolved instance is.
	 *
	 * <p>Guarded end to end: both callers run inside event callbacks, and a throw in one of those
	 * takes down more than this plugin.
	 */
	@Nullable
	static WorldPoint of(Client client, Player player)
	{
		try
		{
			LocalPoint local = player.getLocalLocation();
			if (local != null && local.getWorldView() != WorldView.TOPLEVEL)
			{
				WorldView world = client.getTopLevelWorldView();
				WorldEntity boat = world == null ? null : world.worldEntities().byIndex(local.getWorldView());
				return boat == null ? null : WorldPoint.fromLocalInstance(client, boat.transformToMainWorld(local));
			}
			if (isInstance(client))
			{
				return local == null ? null : WorldPoint.fromLocalInstance(client, local);
			}
			return player.getWorldLocation();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** One place copied whole: {@link #of} is where the player stands, somewhere a map draws. */
	static final String COPY = "copy";

	/** Rooms put together: {@link #of} is a room in the store every variant of it is kept in. */
	static final String ASSEMBLED = "assembled";

	/**
	 * How the instance around the player was built, or null outside one or where the client will
	 * not say.
	 *
	 * <p>An instance is made of 8×8 chunks, each copied from a template chunk with a rotation of its
	 * own, and {@link #of} maps the player back into the template. That is the right answer for a
	 * boss lair or an arena, which is one place copied whole, and the wrong one for the Chambers of
	 * Xeric, a player-owned house or the Gauntlet, which are rooms picked, turned and laid out to one
	 * raid's or one house's plan. There the template tile is a room in the strip every variant of it
	 * is stored in, and two tiles side by side in the instance can be forty apart in the template.
	 * Nothing in the tile says which, so the map cannot tell a lair from a raid; the chunk table can,
	 * exactly, and this reads it.
	 */
	@Nullable
	static String layout(Client client)
	{
		try
		{
			WorldView view = client.getTopLevelWorldView();
			return view == null || !view.isInstance() ? null : layout(view.getInstanceTemplateChunks());
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * The chunk table, {@code [plane][x][y]}, read as one of the two.
	 *
	 * <p>Each entry is packed the way {@code WorldPoint.fromLocalInstance} unpacks it: the rotation in
	 * bits 1–2, the template chunk's y in bits 3–13, its x in bits 14–23 and its plane in bits 24–25,
	 * and −1 for a chunk the scene does not hold. A copy is every loaded chunk unturned and at one
	 * offset from its template in x, y and plane. Anything else is assembled — strictly, so an arena
	 * padded with repeated filler chunks reads as assembled and the map keeps saying "in an instance"
	 * for it, which is the safe way to be wrong.
	 *
	 * <p>Package-private and static so the rule is pinned by a test over plain arrays, without a
	 * client, the way {@link SnapshotBuilder#sanitizeCharacterName} is.
	 */
	@Nullable
	static String layout(@Nullable int[][][] chunks)
	{
		if (chunks == null)
		{
			return null;
		}
		boolean seen = false;
		int dx = 0;
		int dy = 0;
		int dz = 0;
		for (int z = 0; z < chunks.length; z++)
		{
			for (int x = 0; chunks[z] != null && x < chunks[z].length; x++)
			{
				for (int y = 0; chunks[z][x] != null && y < chunks[z][x].length; y++)
				{
					int data = chunks[z][x][y];
					if (data == -1)
					{
						continue;
					}
					if ((data >> 1 & 0x3) != 0)
					{
						return ASSEMBLED;
					}
					int ox = (data >> 14 & 0x3FF) - x;
					int oy = (data >> 3 & 0x7FF) - y;
					int oz = (data >> 24 & 0x3) - z;
					if (!seen)
					{
						dx = ox;
						dy = oy;
						dz = oz;
						seen = true;
					}
					else if (ox != dx || oy != dy || oz != dz)
					{
						return ASSEMBLED;
					}
				}
			}
		}
		return seen ? COPY : null;
	}

	private WorldTiles()
	{
	}
}
