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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldType;

/**
 * A RuneLite client that is not one: enough of the interface for this plugin to read, and nothing
 * else.
 *
 * <p><b>What this is for.</b> Every reader in this plugin — the snapshot builder, the quest and
 * diary readers, the farming observer, the kill log — is a read of live game state, so a unit test
 * can reach very little of them and the whole of that half has only ever been exercised by playing.
 * This is the other way in: a client whose answers are set by a command on stdin, so the real
 * classes can be driven through a real story against a real map. See {@code HarnessMain}.
 *
 * <p><b>Why it is a proxy rather than a class.</b> {@link Client} declares several hundred methods
 * and this plugin calls fourteen of them. Implementing the interface would mean writing — and then
 * maintaining — five hundred method bodies that exist to return zero, and every client update would
 * add more. A {@link Proxy} answers those fourteen and gives everything else the zero value of its
 * return type, which is the same answer a client that has not loaded anything gives.
 *
 * <p><b>What it deliberately is not.</b> It is not a game. Nothing here simulates a world: a varbit
 * is whatever the last command said it was, a skill level is what the story set, and nothing
 * changes on its own. That line matters — a fake that started computing things would be a second,
 * unverified implementation of Old School RuneScape, and every answer this plugin drew from it
 * would be a fact about the fake rather than about the plugin. {@link #ANSWERED} is the whole list,
 * and {@code HarnessGameTest} pins its size for exactly that reason.
 */
final class HarnessGame
{
	/**
	 * Every method of {@link Client} and {@link Player} this fake has an answer for.
	 *
	 * <p>Written down as a set rather than left implicit in the map below so that a test can assert
	 * how short it is. A fake that grows is a fake that has started to be a game.
	 */
	static final Set<String> ANSWERED = Collections.unmodifiableSet(new LinkedHashSet<>(java.util.Arrays.asList(
			"getGameState", "getLocalPlayer", "getWorld", "getWorldType", "getVarbitValue", "getVarpValue",
			"getRealSkillLevel", "getTickCount", "getPlane", "getItemDefinition", "getComponentTable",
			"getName", "getWorldLocation", "getLocalLocation")));

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	/** Item ids the story has named, so the kill log can recognise a drop by the name the game gives it. */
	private final Map<Integer, String> itemNames = new LinkedHashMap<>();

	private volatile GameState gameState = GameState.LOGIN_SCREEN;
	private volatile String characterName = null;
	private volatile int world = 0;
	private volatile WorldPoint where = new WorldPoint(3222, 3218, 0);
	private volatile int tickCount = 0;
	private volatile EnumSet<WorldType> worldTypes = EnumSet.of(WorldType.MEMBERS);

	private final Client client;
	private final Player player;

	HarnessGame()
	{
		this.player = proxy(Player.class, playerAnswers());
		this.client = proxy(Client.class, clientAnswers());
	}

	Client client()
	{
		return client;
	}

	/** Logged in as somebody, somewhere, on some world — the state every command below assumes. */
	void login(String name, int world, int x, int y, int plane)
	{
		this.characterName = name;
		this.world = world;
		this.where = new WorldPoint(x, y, plane);
		this.gameState = GameState.LOGGED_IN;
	}

	void logout()
	{
		this.gameState = GameState.LOGIN_SCREEN;
	}

	/** Explicit fixture flags; a world number does not infer a game mode. */
	void worldTypes(EnumSet<WorldType> types)
	{
		worldTypes = types == null ? null : EnumSet.copyOf(types);
	}

	GameState gameState()
	{
		return gameState;
	}

	void moveTo(int x, int y, int plane)
	{
		this.where = new WorldPoint(x, y, plane);
	}

	WorldPoint where()
	{
		return where;
	}

	void setVarbit(int id, int value)
	{
		synchronized (varbits)
		{
			varbits.put(id, value);
		}
	}

	void setVarp(int id, int value)
	{
		synchronized (varps)
		{
			varps.put(id, value);
		}
	}

	void setLevel(Skill skill, int level)
	{
		synchronized (levels)
		{
			levels.put(skill, level);
		}
	}

	int level(Skill skill)
	{
		synchronized (levels)
		{
			Integer held = levels.get(skill);
			return held == null ? 1 : held;
		}
	}

	/** One tick of the game clock. The plugin reads this to tell a pickpocket from a kill. */
	void tick()
	{
		tickCount++;
	}

	/**
	 * Name an item id, the way a loaded client would.
	 *
	 * <p>The kill log matches drops by name against its bundled table, so a drop nobody has named
	 * is a drop it will not recognise — which is the same thing that happens against a real client
	 * that has not cached the definition.
	 */
	void nameItem(int itemId, String name)
	{
		synchronized (itemNames)
		{
			itemNames.put(itemId, name);
		}
	}

	/** An NPC composition carrying nothing but a name, which is all {@link LootLog} reads. */
	NPCComposition npc(String name)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", args -> name);
		return proxy(NPCComposition.class, answers);
	}

	private Map<String, Function<Object[], Object>> clientAnswers()
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getGameState", args -> gameState);
		answers.put("getLocalPlayer", args -> gameState == GameState.LOGGED_IN ? player : null);
		answers.put("getWorld", args -> world);
		answers.put("getWorldType", args -> worldTypes == null ? null : EnumSet.copyOf(worldTypes));
		answers.put("getVarbitValue", args -> read(varbits, args));
		answers.put("getVarpValue", args -> read(varps, args));
		answers.put("getRealSkillLevel", args -> args[0] instanceof Skill ? level((Skill) args[0]) : 0);
		answers.put("getTickCount", args -> tickCount);
		answers.put("getPlane", args -> where.getPlane());
		answers.put("getItemDefinition", args -> item(args));
		// The farming observer rejects stale varbits while a modal is open.
		answers.put("getComponentTable", args -> new HashTable<WidgetNode>()
		{
			public WidgetNode get(long key)
			{
				return null;
			}

			public java.util.Iterator<WidgetNode> iterator()
			{
				return Collections.emptyIterator();
			}
		});
		return answers;
	}

	private Map<String, Function<Object[], Object>> playerAnswers()
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", args -> characterName);
		answers.put("getWorldLocation", args -> where);
		/**
		 * Null. {@link WorldTiles} asks for it first, to see whether the player is aboard a boat,
		 * and null reads as standing in the world itself; the only other reader is the instance
		 * branch, and this client's world view is absent, which reads as not being in one. So the
		 * world location above is the answer every time. A local tile invented here would be a
		 * coordinate space the fake had made up.
		 */
		answers.put("getLocalLocation", args -> null);
		/**
		 * And there is deliberately no {@code isInScene} here. It reads as one — the plugin calls
		 * it — but it is a method of {@code LocalPoint}, not of {@link Player}, so an entry under
		 * that name on this proxy would never be reached by anything. It was here for a while and
		 * {@code HarnessGameTest} found it: an answer nobody can ask for is the quiet half of a
		 * fake growing, because it looks like coverage in this file and is not.
		 */
		return answers;
	}

	@Nullable
	private ItemComposition item(Object[] args)
	{
		int id = args != null && args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : -1;
		String name;
		synchronized (itemNames)
		{
			name = itemNames.get(id);
		}
		if (name == null)
		{
			return null;
		}
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", ignored -> name);
		answers.put("getId", ignored -> id);
		return proxy(ItemComposition.class, answers);
	}

	private static int read(Map<Integer, Integer> values, Object[] args)
	{
		if (args == null || args.length == 0 || !(args[args.length - 1] instanceof Integer))
		{
			return 0;
		}
		Integer id = (Integer) args[args.length - 1];
		synchronized (values)
		{
			Integer held = values.get(id);
			return held == null ? 0 : held;
		}
	}

	/**
	 * One interface, answered by name.
	 *
	 * <p>Anything not in the map gets the zero value of its return type — the answer a client
	 * gives for a thing it has not loaded, and the reason this file is two hundred lines rather
	 * than five thousand. {@code equals}, {@code hashCode} and {@code toString} are answered here
	 * because a proxy that let them fall through to the zero value would be an object that is not
	 * equal to itself, which breaks collections in ways that look like a bug in the plugin.
	 */
	private static <T> T proxy(Class<T> type, Map<String, Function<Object[], Object>> answers)
	{
		InvocationHandler handler = (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "equals":
					return proxy == args[0];
				case "hashCode":
					return System.identityHashCode(proxy);
				case "toString":
					return "fake " + type.getSimpleName();
				default:
					break;
			}
			Function<Object[], Object> answer = answers.get(method.getName());
			return answer != null ? answer.apply(args) : zero(method.getReturnType());
		};
		return type.cast(Proxy.newProxyInstance(HarnessGame.class.getClassLoader(), new Class<?>[] {type}, handler));
	}

	/** What a method returns when this fake has nothing to say about it. */
	@Nullable
	static Object zero(Class<?> type)
	{
		if (!type.isPrimitive())
		{
			return null;
		}
		if (type == boolean.class)
		{
			return false;
		}
		if (type == char.class)
		{
			return (char) 0;
		}
		if (type == byte.class)
		{
			return (byte) 0;
		}
		if (type == short.class)
		{
			return (short) 0;
		}
		if (type == int.class)
		{
			return 0;
		}
		if (type == long.class)
		{
			return 0L;
		}
		if (type == float.class)
		{
			return 0f;
		}
		if (type == double.class)
		{
			return 0d;
		}
		/** void, which a proxy answers with null. */
		return null;
	}
}
