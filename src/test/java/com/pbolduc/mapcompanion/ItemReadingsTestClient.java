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
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;

/**
 * Explicit inventory and item definitions for reader tests. Unexpected API calls fail rather than
 * supplying invented game state, and no fixture logic decodes a clue or grants a capability.
 */
final class ItemReadingsTestClient
{
	private final Map<Integer, ItemComposition> definitions = new HashMap<>();
	private Item[] inventory = new Item[0];
	private Item[] equipment = new Item[0];

	final Client client = proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "getItemDefinition":
				return definitions.get((int) args[0]);
			case "getItemContainer":
				int id = (int) args[0];
				if (id == InventoryID.INV)
				{
					return inventory == null ? null : container(inventory);
				}
				if (id == InventoryID.WORN)
				{
					return container(equipment);
				}
				throw new AssertionError("unexpected inventory " + id);
			default:
				throw new AssertionError("unexpected client method " + method);
		}
	});

	void define(int id, String name, boolean noted)
	{
		definitions.put(id, proxy(ItemComposition.class, (method, args) ->
		{
			switch (method)
			{
				case "getName":
					return name;
				case "getNote":
					return noted ? 799 : -1;
				default:
					throw new AssertionError("unexpected item method " + method);
			}
		}));
	}

	void carry(int... ids)
	{
		inventory = items(ids);
	}

	/** The client has not been sent the inventory yet, as right after a login or a hop. */
	void unreadInventory()
	{
		inventory = null;
	}

	void equip(int... ids)
	{
		equipment = items(ids);
	}

	static Widget widget(String text)
	{
		return proxy(Widget.class, (method, args) ->
		{
			if ("getText".equals(method))
			{
				return text;
			}
			throw new AssertionError("unexpected widget method " + method);
		});
	}

	private static Item[] items(int[] ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		return items;
	}

	private static ItemContainer container(Item[] items)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			if ("getItems".equals(method))
			{
				return items;
			}
			throw new AssertionError("unexpected container method " + method);
		});
	}

	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> answer)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
												(instance, method, args) -> answer.apply(method.getName(), args)));
	}
}
