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

import java.util.Arrays;
import net.runelite.api.InstanceTemplates;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Which instances a map can draw a friend in: one place copied whole, or rooms put together.
 *
 * <p>Pinned over plain chunk tables, because nothing else reaches this without a game client. The
 * tables are packed the way RuneLite's own {@link InstanceTemplates#findMatch} unpacks them — the
 * first test holds that — so each one is a table the client could hand over.
 */
public class WorldTilesLayoutTest
{
	/** A scene is 104 tiles, thirteen chunks, on each of four floors. */
	private static final int SCENE_CHUNKS = 13;

	/** Rotation in bits 1–2, the template chunk's y in 3–13, its x in 14–23, its floor in 24–25. */
	private static int chunk(int templateX, int templateY, int templatePlane, int rotation)
	{
		return templatePlane << 24 | (templateX / 8) << 14 | (templateY / 8) << 3 | rotation << 1;
	}

	private static int[][][] emptyScene()
	{
		int[][][] table = new int[4][SCENE_CHUNKS][SCENE_CHUNKS];
		for (int[][] plane : table)
		{
			for (int[] column : plane)
			{
				Arrays.fill(column, -1);
			}
		}
		return table;
	}

	/** An 8×8-chunk block on one scene floor, every chunk from one template at one offset. */
	private static void copy(int[][][] table, int sceneX, int sceneY, int scenePlane, int templateX, int templateY, int templatePlane)
	{
		for (int x = 0; x < 8; x++)
		{
			for (int y = 0; y < 8; y++)
			{
				table[scenePlane][sceneX + x][sceneY + y] = chunk(templateX + x * 8, templateY + y * 8, templatePlane, 0);
			}
		}
	}

	@Test
	public void packsTheTableTheWayTheClientReadsIt()
	{
		/** Inside Tekton's strip of the Chambers of Xeric, as RuneLite's own table of raid rooms reads it back. */
		assertEquals(InstanceTemplates.RAIDS_TEKTON, InstanceTemplates.findMatch(chunk(3296, 5288, 1, 0)));
		assertEquals(InstanceTemplates.RAIDS_VASA, InstanceTemplates.findMatch(chunk(3296, 5288, 0, 3)));
	}

	@Test
	public void readsOnePlaceCopiedWholeAsACopy()
	{
		/** A lair stored a floor up and built on the ground floor, as the game labels the Inferno's. */
		int[][][] table = emptyScene();
		copy(table, 2, 2, 0, 2304, 5376, 1);
		assertEquals(WorldTiles.COPY, WorldTiles.layout(table));
	}

	@Test
	public void readsAPlaceWithStoreysAsACopy()
	{
		int[][][] table = emptyScene();
		copy(table, 2, 2, 0, 2304, 5376, 0);
		copy(table, 2, 2, 1, 2304, 5376, 1);
		assertEquals(WorldTiles.COPY, WorldTiles.layout(table));
	}

	@Test
	public void readsRoomsFromDifferentStripsAsAssembled()
	{
		/**
		 * Two raid rooms laid side by side, each from its own strip: Tekton's on the template's floor
		 * 1 and Vasa's on floor 0, both at y 5280. Each is a copy on its own; together they are a raid.
		 */
		int[][][] table = emptyScene();
		for (int x = 0; x < 4; x++)
		{
			for (int y = 0; y < 4; y++)
			{
				table[0][x][y] = chunk(3264 + x * 8, 5280 + y * 8, 1, 0);
				table[0][x + 4][y] = chunk(3264 + x * 8, 5280 + y * 8, 0, 0);
			}
		}
		assertEquals(WorldTiles.ASSEMBLED, WorldTiles.layout(table));
	}

	@Test
	public void readsOneTurnedChunkAsAssembled()
	{
		/** Every offset agrees, and a step east in that chunk is still a step north in the template. */
		int[][][] table = emptyScene();
		copy(table, 2, 2, 0, 2304, 5376, 1);
		table[0][5][5] = chunk(2304 + 3 * 8, 5376 + 3 * 8, 1, 1);
		assertEquals(WorldTiles.ASSEMBLED, WorldTiles.layout(table));
	}

	@Test
	public void saysNothingWhereTheClientHandsOverNothing()
	{
		assertNull(WorldTiles.layout((int[][][]) null));
		assertNull(WorldTiles.layout(emptyScene()));
	}
}
