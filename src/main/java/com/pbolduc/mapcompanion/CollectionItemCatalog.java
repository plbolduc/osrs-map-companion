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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.util.Text;

/**
 * The game's collection item vocabulary, read from cache rather than from a player's log.
 *
 * <p>The enum/struct path and replacement enum are documented by
 * <a href="https://github.com/weirdgloop/WikiSync/blob/1ff4ea0653395b552844d658d3668815d82e0a79/src/main/java/com/andmcadams/wikisync/WikiSyncPlugin.java#L479">WikiSync's parseCacheForClog</a>:
 * enum 2102 -> tab parameter 683 -> page parameter 690, then replacements from enum 3721.
 * This independently implemented traversal reads definitions only; it never runs the collection
 * interface, reads an obtained-item container, or contacts the Wiki. Page parameter 689 names the
 * "All pets" page, so pet identification grows with that page instead of with a guessed name list.
 *
 * <p>A half-loaded cache must not become a complete-looking catalog. Every branch and item name
 * has to resolve before a snapshot is published. The generous count floors catch an extraction
 * that collapsed to a single valid branch (the real log has well over a thousand unique items).
 * Cache reads retry locally at a bounded cadence; no retry is a network request by this plugin.
 */
@Singleton
public final class CollectionItemCatalog
{
	static final int ROOT_ENUM = 2102;
	static final int PAGE_ENUM_PARAM = 683;
	static final int ITEM_ENUM_PARAM = 690;
	static final int PAGE_NAME_PARAM = 689;
	static final int REPLACEMENTS_ENUM = 3721;
	static final int RETRY_TICKS = 10;
	static final int MAX_ATTEMPTS = 30;
	private static final int MIN_ITEMS = 1_000;
	private static final int MIN_PAGES = 50;
	private final Client client;
	/*
	 * Built on the client thread and read by the sidebar on the EDT. Volatile, and each assigned
	 * whole before complete, so the sidebar never sees complete beside a map that has not arrived.
	 */
	private volatile Map<Integer, Integer> replacements = Collections.emptyMap();
	private volatile Map<Integer, String> names = Collections.emptyMap();
	private volatile Map<String, Integer> idsByName = Collections.emptyMap();
	private volatile Set<Integer> pets = Collections.emptySet();
	private volatile boolean complete;
	private int attempts;
	private int nextAttemptTick = Integer.MIN_VALUE;

	@Inject
	public CollectionItemCatalog(Client client)
	{
		this.client = client;
	}

	/** Call on the client thread; failed initialization never replaces the published snapshot. */
	public boolean initialize()
	{
		if (complete)
		{
			return true;
		}
		if (!ready())
		{
			return false;
		}
		try
		{
			Set<Integer> items = new LinkedHashSet<>();
			Set<Integer> petItems = new HashSet<>();
			Set<Integer> pages = new HashSet<>();
			int[] tabs = enumValues(ROOT_ENUM);
			require(tabs.length >= 5);
			for (int tab : tabs)
			{
				for (int page : enumValues(struct(tab).getIntValue(PAGE_ENUM_PARAM)))
				{
					pages.add(page);
					StructComposition definition = struct(page);
					String pageName = clean(definition.getStringValue(PAGE_NAME_PARAM));
					require(pageName != null);
					boolean petPage = "all pets".equals(key(pageName));
					for (int id : enumValues(definition.getIntValue(ITEM_ENUM_PARAM)))
					{
						require(id > 0);
						items.add(id);
						if (petPage)
						{
							petItems.add(id);
						}
					}
				}
			}
			EnumComposition replacementEnum = client.getEnum(REPLACEMENTS_ENUM);
			require(replacementEnum != null);
			int[] from = replacementEnum.getKeys();
			int[] to = replacementEnum.getIntVals();
			require(from != null && to != null && from.length == to.length);
			Map<Integer, Integer> aliases = new HashMap<>();
			for (int i = 0; i < from.length; i++)
			{
				require(from[i] > 0 && to[i] > 0 && aliases.put(from[i], to[i]) == null);
				items.remove(from[i]);
				items.add(to[i]);
				if (petItems.remove(from[i]))
				{
					petItems.add(to[i]);
				}
			}
			require(items.size() >= MIN_ITEMS && pages.size() >= MIN_PAGES && !petItems.isEmpty());
			Map<Integer, String> foundNames = new HashMap<>();
			Map<String, Integer> foundIds = new HashMap<>();
			Set<String> ambiguous = new HashSet<>();
			for (int id : items)
			{
				String name = definitionName(id);
				require(name != null);
				foundNames.put(id, name);
				indexName(foundIds, ambiguous, name, id);
			}
			for (Map.Entry<Integer, Integer> alias : aliases.entrySet())
			{
				// A replacement chain or cycle is an unknown new grammar, not permission to guess.
				require(!aliases.containsKey(alias.getValue()));
				String name = definitionName(alias.getKey());
				require(name != null);
				indexName(foundIds, ambiguous, name, alias.getValue());
			}
			ambiguous.forEach(foundIds::remove);
			replacements = Collections.unmodifiableMap(aliases);
			names = Collections.unmodifiableMap(foundNames);
			idsByName = Collections.unmodifiableMap(foundIds);
			pets = Collections.unmodifiableSet(petItems);
			complete = true;
			return true;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	public void onGameTick(int tick)
	{
		if (!complete && attempts < MAX_ATTEMPTS && tick >= nextAttemptTick && ready())
		{
			attempts++;
			nextAttemptTick = tick + RETRY_TICKS;
			initialize();
		}
	}

	public boolean isComplete()
	{
		return complete;
	}

	public boolean contains(int itemId)
	{
		Integer id = canonicalId(itemId);
		return id != null && names.containsKey(id);
	}

	public boolean isPet(int itemId)
	{
		Integer id = canonicalId(itemId);
		return id != null && pets.contains(id);
	}

	/** Notes and the cache's explicit replacements name the same reward; other variants do not. */
	@Nullable
	public Integer canonicalId(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		try
		{
			ItemComposition item = client.getItemDefinition(itemId);
			if (item == null)
			{
				return null;
			}
			int id = item.getNote() != -1 ? item.getLinkedNoteId() : itemId;
			return id > 0 ? replacements.getOrDefault(id, id) : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	@Nullable
	public Integer findItemId(String exactName)
	{
		String cleaned = clean(exactName);
		return cleaned == null ? null : idsByName.get(key(cleaned));
	}

	@Nullable
	public String itemName(int itemId)
	{
		Integer id = canonicalId(itemId);
		if (id == null)
		{
			return null;
		}
		String known = names.get(id);
		return known != null ? known : definitionName(id);
	}

	private boolean ready()
	{
		GameState state = client.getGameState();
		return client.isClientThread() && state != null && state.getState() >= GameState.LOGIN_SCREEN.getState();
	}

	private int[] enumValues(int id)
	{
		require(id > 0);
		EnumComposition value = client.getEnum(id);
		require(value != null && value.getIntVals() != null && value.getIntVals().length > 0);
		return value.getIntVals();
	}

	private StructComposition struct(int id)
	{
		require(id > 0);
		StructComposition value = client.getStructComposition(id);
		require(value != null);
		return value;
	}

	@Nullable
	private String definitionName(int id)
	{
		ItemComposition item = client.getItemDefinition(id);
		return item == null ? null : clean(item.getMembersName());
	}

	static String clean(String text)
	{
		if (text == null)
		{
			return null;
		}
		String result = Text.removeTags(text).replace('\u00a0', ' ').trim();
		return result.isEmpty() || "null".equalsIgnoreCase(result) ? null : result;
	}

	private static String key(String text)
	{
		return text.toLowerCase(Locale.ROOT);
	}

	private static void indexName(Map<String, Integer> into, Set<String> ambiguous, String name, int id)
	{
		String key = key(name);
		Integer previous = into.putIfAbsent(key, id);
		if (previous != null && previous != id)
		{
			ambiguous.add(key);
		}
	}

	private static void require(boolean valid)
	{
		if (!valid)
		{
			throw new IllegalStateException("Collection item definitions are not complete");
		}
	}
}
