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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Where a player aboard a boat is: on the sea, not on the deck.
 *
 * <p>The fakes are strict. A method none of them answers fails the test rather than returning a
 * zero, so the boat cases also pin what is <em>not</em> read — above all the player's world
 * location, which aboard a boat is the deck tile this exists to stop sending. The conversions in
 * between are RuneLite's own {@link WorldPoint#fromLocalInstance}, run for real.
 */
public class WorldTilesBoatTest
{
	/** The index the boat has among the top level's world entities, which is its view's id. */
	private static final int BOAT = 5;

	/** Where the sea's scene starts, in world tiles. */
	private static final int BASE_X = 3008;
	private static final int BASE_Y = 2816;

	/** A tile's centre in local units, 128 to a tile. */
	private static int local(int sceneTile)
	{
		return sceneTile * 128 + 64;
	}

	/** Standing on the deck, three tiles from the stern and four from the port rail. */
	private static final LocalPoint DECK = new LocalPoint(local(3), local(4), BOAT);

	/** Where that deck tile is on the sea this tick: scene tile (40, 52) of the top level. */
	private static final LocalPoint AT_SEA = new LocalPoint(local(40), local(52), WorldView.TOPLEVEL);

	@Test
	public void aPlayerAboardIsWhereTheBoatHasSailedTo()
	{
		Client client = client(sea(false, null, boat()));

		assertEquals(new WorldPoint(BASE_X + 40, BASE_Y + 52, 0), WorldTiles.of(client, player(DECK)));
	}

	@Test
	public void aBoatOnAnInstancedSeaResolvesThroughItsTemplateLikeAnyOtherTile()
	{
		/**
		 * Scene tile (40, 52) is chunk (5, 6), tile (0, 4) within it. That chunk is copied unturned
		 * from the one whose corner is (2400, 2800), so the template tile is (2400, 2804).
		 */
		int[][][] chunks = new int[4][13][13];
		for (int[][] plane : chunks)
		{
			for (int[] column : plane)
			{
				Arrays.fill(column, -1);
			}
		}
		chunks[0][5][6] = (2400 / 8) << 14 | (2800 / 8) << 3;
		Client client = client(sea(true, chunks, boat()));

		assertEquals(new WorldPoint(2400, 2804, 0), WorldTiles.of(client, player(DECK)));
	}

	@Test
	public void aBoatTheClientNoLongerHoldsIsNoPositionRatherThanTheDeck()
	{
		Client client = client(sea(false, null, null));

		assertNull(WorldTiles.of(client, player(DECK)));
	}

	@Test
	public void onLandTheWorldLocationIsStillTheAnswer()
	{
		Client client = client(sea(false, null, null));
		WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getLocalLocation", args -> new LocalPoint(local(50), local(50), WorldView.TOPLEVEL));
		answers.put("getWorldLocation", args -> lumbridge);

		assertEquals(lumbridge, WorldTiles.of(client, fake(Player.class, answers)));
	}

	/** A player whose local tile is {@code at}, and who is never asked for a world location. */
	private static Player player(LocalPoint at)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getLocalLocation", args -> at);
		return fake(Player.class, answers);
	}

	/** The top-level view: the sea, instanced or not, holding {@code boat} at {@link #BOAT} if there is one. */
	private static WorldView sea(boolean instance, int[][][] chunks, WorldEntity boat)
	{
		Map<String, Function<Object[], Object>> entities = new HashMap<>();
		entities.put("byIndex", args -> (Integer) args[0] == BOAT ? boat : null);
		IndexedObjectSet<?> held = fake(IndexedObjectSet.class, entities);

		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("isInstance", args -> instance);
		answers.put("getInstanceTemplateChunks", args -> chunks);
		answers.put("getPlane", args -> 0);
		answers.put("getBaseX", args -> BASE_X);
		answers.put("getBaseY", args -> BASE_Y);
		answers.put("worldEntities", args -> held);
		return fake(WorldView.class, answers);
	}

	/**
	 * A boat that puts {@link #DECK} at {@link #AT_SEA}, and refuses a point from any other view the
	 * way the client's own {@code transformToMainWorld} does.
	 */
	private static WorldEntity boat()
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("transformToMainWorld", args ->
		{
			LocalPoint point = (LocalPoint) args[0];
			if (point.getWorldView() != BOAT)
			{
				throw new IllegalArgumentException("LocalPoint doesn't belong to this WorldEntity");
			}
			assertEquals(DECK, point);
			return AT_SEA;
		});
		return fake(WorldEntity.class, answers);
	}

	/**
	 * A client whose top level is {@code top}. Asked for any other view it fails the test: the deck's
	 * own view resolved by id is the conversion that yields a deck tile.
	 */
	private static Client client(WorldView top)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getTopLevelWorldView", args -> top);
		answers.put("getWorldView", args ->
		{
			if ((Integer) args[0] != WorldView.TOPLEVEL)
			{
				throw new AssertionError("resolved world view " + args[0] + ", which is not the top level");
			}
			return top;
		});
		return fake(Client.class, answers);
	}

	private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> answers)
	{
		InvocationHandler handler = (proxy, method, args) ->
		{
			if ("toString".equals(method.getName()))
			{
				return "fake " + type.getSimpleName();
			}
			Function<Object[], Object> answer = answers.get(method.getName());
			if (answer == null)
			{
				throw new AssertionError(type.getSimpleName() + "." + method.getName() + " is not read here");
			}
			return answer.apply(args);
		};
		return type.cast(Proxy.newProxyInstance(
							 WorldTilesBoatTest.class.getClassLoader(), new Class<?>[] {type}, handler));
	}
}
