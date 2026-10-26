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

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.WorldType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The world a hop button can reach is named by a group member, so its type decides, not its number.
 */
public class WorldHopRefusalTest
{
	@Test
	public void ordinaryWorldsAreReachable()
	{
		assertNull(WorldHopper.refusalFor(EnumSet.noneOf(WorldType.class)));
		assertNull(WorldHopper.refusalFor(EnumSet.of(WorldType.MEMBERS)));
		/** The game refuses these itself, with its own message, so this does not second-guess it. */
		assertNull(WorldHopper.refusalFor(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL)));
	}

	@Test
	public void aWorldWhereDeathCostsItemsIsRefused()
	{
		for (WorldType type : new WorldType[]{WorldType.PVP, WorldType.DEADMAN, WorldType.BOUNTY})
		{
			assertEquals(type.name(), "Won't hop to a PvP world",
				WorldHopper.refusalFor(EnumSet.of(WorldType.MEMBERS, type)));
		}
		/** Not PvP, but Protect Item is off there, which is the loss the button must not cause. */
		assertEquals("Won't hop to a high-risk world",
			WorldHopper.refusalFor(EnumSet.of(WorldType.MEMBERS, WorldType.HIGH_RISK)));
	}

	@Test
	public void aWorldWithItsOwnSaveOrRulesIsRefused()
	{
		for (WorldType type : new WorldType[]{WorldType.SEASONAL, WorldType.BETA_WORLD, WorldType.TOURNAMENT_WORLD,
			WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE, WorldType.QUEST_SPEEDRUNNING,
			WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA})
		{
			assertNotNull(type.name(), WorldHopper.refusalFor(EnumSet.of(type)));
		}
	}

	/** The press-to-hop path itself, so the check cannot be dropped from the tick unnoticed. */
	@Test
	public void theTickRefusesAPvpWorldAndHopsToAnOrdinaryOne() throws Exception
	{
		List<Integer> hops = new ArrayList<>();
		World[] worlds = {world(302, EnumSet.of(WorldType.MEMBERS)), world(325, EnumSet.of(WorldType.MEMBERS, WorldType.PVP))};
		Widget open = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
			(proxy, method, args) -> null);
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return GameState.LOGGED_IN;
					case "getWorld":
						return 301;
					case "getWidget":
						return open;
					case "getWorldList":
						return worlds;
					case "hopToWorld":
						hops.add(((World) args[0]).getId());
						return null;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		Constructor<WorldHopper> make = WorldHopper.class.getDeclaredConstructor(Client.class, ClientThread.class);
		make.setAccessible(true);
		WorldHopper hopper = make.newInstance(client, new ClientThread());

		assertEquals(true, hopper.requestHop(325));
		assertEquals("pending from the press itself", true, hopper.isHopPending());
		hopper.onGameTick();
		assertEquals(0, hops.size());
		assertNull("a refusal is not shown against another world", hopper.takeRefusal(302));
		assertEquals("Won't hop to a PvP world", hopper.takeRefusal(325));
		assertEquals(false, hopper.isHopPending());

		hopper.requestHop(302);
		hopper.onGameTick();
		assertEquals(1, hops.size());
		assertEquals(Integer.valueOf(302), hops.get(0));
	}

	private static World world(int id, EnumSet<WorldType> types)
	{
		return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getId":
						return id;
					case "getTypes":
						return types;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
