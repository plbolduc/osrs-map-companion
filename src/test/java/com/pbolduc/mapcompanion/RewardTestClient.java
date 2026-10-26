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

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.StructComposition;

/** Definition fixtures only: a call to a player, inventory or network method fails the test. */
final class RewardTestClient
{
	final Map<Integer, int[]> enums = new HashMap<>();
	final Map<Integer, Integer> structEnums = new HashMap<>();
	final Map<Integer, String> pageNames = new HashMap<>();
	final Map<Integer, String> itemNames = new HashMap<>();
	final Map<Integer, String> varcs = new HashMap<>();
	int[] replacementKeys = {10000};
	int[] replacementValues = {20000};
	int tick;
	int enumReads;
	int collectionMode = 1;
	GameState state = GameState.LOGIN_SCREEN;
	boolean clientThread = true;
	final Client client = proxy(Client.class, this::answer);

	RewardTestClient()
	{
		enums.put(2102, new int[] {100, 101, 102, 103, 104});
		for (int tab = 0; tab < 5; tab++)
		{
			structEnums.put(100 + tab, 1000 + tab);
			int[] pages = new int[10];
			for (int n = 0; n < 10; n++)
			{
				int page = tab * 10 + n;
				pages[n] = 3000 + page;
				structEnums.put(3000 + page, 4000 + page);
				pageNames.put(3000 + page, page == 0 ? "All pets" : "Page " + page);
				int[] ids = new int[20];
				for (int i = 0; i < 20; i++)
				{
					ids[i] = 10000 + page * 20 + i;
					itemNames.put(ids[i], "Item " + ids[i]);
				}
				enums.put(4000 + page, ids);
			}
			enums.put(1000 + tab, pages);
		}
		itemNames.put(20000, "Pet replacement");
		itemNames.put(30000, "Noted reward");
		itemNames.put(40000, "Ordinary supply");
	}

	CollectionItemCatalog catalog()
	{
		CollectionItemCatalog catalog = new CollectionItemCatalog(client);
		if (!catalog.initialize())
		{
			throw new AssertionError("Complete definition fixture did not initialize");
		}
		return catalog;
	}

	private Object answer(String method, Object[] args)
	{
		switch (method)
		{
			case "isClientThread":
				return clientThread;
			case "getGameState":
				return state;
			case "getTickCount":
				return tick;
			case "getVarbitValue":
				return ((Integer) args[0]) == VarbitID.OPTION_COLLECTION_NEW_ITEM
					   ? collectionMode : unexpected("unrelated varbit " + args[0]);
			case "getVarcStrValue":
				return varcs.get((Integer) args[0]);
			case "getEnum":
				int enumId = (Integer) args[0];
				enumReads++;
				if (enumId == 3721)
				{
					return proxy(EnumComposition.class, (name, ignored) ->
								 "getKeys".equals(name) ? replacementKeys : "getIntVals".equals(name) ? replacementValues : unexpected(name));
				}
				return enums.containsKey(enumId) ? proxy(EnumComposition.class, (name, ignored) ->
						"getIntVals".equals(name) ? enums.get(enumId) : unexpected(name)) : null;
			case "getStructComposition":
				int structId = (Integer) args[0];
				return structEnums.containsKey(structId) ? proxy(StructComposition.class, (name, values) ->
				{
					int parameter = (Integer) values[0];
					if ("getIntValue".equals(name) && parameter == (structId < 1000 ? 683 : 690))
					{
						return structEnums.get(structId);
					}
					return "getStringValue".equals(name) && parameter == 689 ? pageNames.get(structId) : unexpected(name);
				}) : null;
			case "getItemDefinition":
				int itemId = (Integer) args[0];
				return itemNames.containsKey(itemId) ? proxy(ItemComposition.class, (name, ignored) ->
				{
					switch (name)
					{
						case "getId":
							return itemId;
						case "getName":
						case "getMembersName":
							return itemNames.get(itemId);
						case "getNote":
							return itemId == 30000 ? 799 : -1;
						case "getLinkedNoteId":
							return itemId == 30000 ? 10020 : -1;
						default:
							return unexpected(name);
					}
				}) : null;
			default:
				return unexpected(method);
		}
	}

	static NPCComposition npc(String name)
	{
		return proxy(NPCComposition.class, (method, ignored) ->
					 "getName".equals(method) ? name : unexpected(method));
	}

	private static Object unexpected(String method)
	{
		throw new AssertionError("Unexpected client access: " + method);
	}

	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> answer)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
												(instance, method, args) -> answer.apply(method.getName(), args)));
	}
}
