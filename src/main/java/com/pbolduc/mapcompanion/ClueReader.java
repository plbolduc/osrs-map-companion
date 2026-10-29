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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue;

/**
 * The clue step being carried, when one is.
 *
 * <p><b>What this deliberately does not do:</b> solve the clue. RuneLite's own clue plugin already
 * does that, and does it well. This reads the step's <i>text</i> so the map can answer the question
 * no in-client overlay does — how does this account, with its teleports and its levels, get there
 * from where it is standing right now.
 *
 * <p>Three shapes come out, and they need three different amounts of help:
 *
 * <ul>
 *   <li>A <b>coordinate</b> is arithmetic. It is solved here and a tile is sent, so the map needs
 *       no table and nothing can go stale.
 *   <li>A <b>map</b> clue is a picture with no text at all. The scroll's item id is sent instead.
 *   <li>Everything else is a <b>sentence</b>, and the map looks it up.
 * </ul>
 *
 * <p><b>Why the tier comes from the item's name and not a table of ids.</b> There are several
 * hundred clue scroll ids and they change with every treasure trail update, so a table written here
 * would be long, unverifiable, and wrong within a year — and wrong in the quiet way, where an easy
 * clue is reported as hard because two ranges were transcribed as overlapping. The game already
 * knows: every one of them is named "Clue scroll (hard)". Reading the name is exact, needs no
 * maintenance, and cannot drift.
 *
 * <p>The step text is sent as the interface printed it, with no cleverness applied: the map
 * normalizes both sides of the join, and a step whose wording has moved fails to match and is
 * reported as unrecognised. That is the intended failure — better than pointing at last year's
 * answer.
 */
@Singleton
public class ClueReader
{
	/** "Clue scroll (hard)", and the same shape for every tier. */
	private static final Pattern SCROLL_NAME =
		Pattern.compile("^clue scroll \\((beginner|easy|medium|hard|elite|master)\\)$");

	/**
	 * Four numbers and two bearings in order. Spaces and punctuation may separate them because the
	 * interface uses both; all values and both bearings still have to be present in sequence.
	 */
	private static final Pattern COORDINATE = Pattern.compile(
			"(\\d{1,2})[\\s,.]*degrees?[\\s,.]*(\\d{1,2})[\\s,.]*minutes?[\\s,.]*(north|south)[^0-9]{0,20}"
			+ "(\\d{1,2})[\\s,.]*degrees?[\\s,.]*(\\d{1,2})[\\s,.]*minutes?[\\s,.]*(east|west)",
			Pattern.CASE_INSENSITIVE);

	private final Client client;

	/** The last step text read out of the interface; a scroll only shows it when opened. */
	private String lastStepText = null;
	private CompanionSnapshot.Tile mapTile;
	private int lastScrollItemId = -1;

	/**
	 * The scroll the player last chose to read, until its interface opens. A player can carry one
	 * clue of every tier, and the interface does not say which scroll it belongs to.
	 */
	private int openingScrollItemId = -1;

	/**
	 * The clue as the last read found it, for the sidebar's clue card and its Route. Written on the
	 * client thread by {@link #read} and read on Swing's, so it is one immutable reading behind a
	 * volatile field. It is read whether or not the clue is shared: the snapshot carries it only with
	 * <i>Share clue</i>, and a Route press sends its tile once, in the request that asks for the way.
	 */
	@Nullable
	private volatile CompanionSnapshot.Clue current;

	@Inject
	ClueReader(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		current = null;
		lastStepText = null;
		mapTile = null;
		lastScrollItemId = -1;
		openingScrollItemId = -1;
	}

	/** A "Read" on an inventory item, so the next interface can be filed under the right scroll. */
	public void onScrollRead(int itemId)
	{
		if (tierOf(itemId) != null)
		{
			openingScrollItemId = itemId;
		}
	}

	/**
	 * Remember the step from an opened clue interface.
	 *
	 * <p>Called from a widget-loaded subscription. The text is kept because a player reads the
	 * scroll once and then walks for two minutes, and the map should keep pointing at the answer for
	 * that whole walk rather than only in the second the interface was open.
	 */
	public void onClueWidget(@Nullable Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		String text = widget.getText();
		if (text == null || text.trim().isEmpty())
		{
			return;
		}
		/* Establish the scroll before storing its text: the interface can precede the next snapshot. */
		int opening = openingScrollItemId;
		openingScrollItemId = -1;
		if (read() == null)
		{
			return;
		}
		if (opening > 0 && opening != lastScrollItemId && carried(opening))
		{
			lastScrollItemId = opening;
		}
		mapTile = null;
		lastStepText = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
	}

	/**
	 * The clue in the inventory, or null.
	 *
	 * <p>Must run on the client thread. Returns null the moment the scroll leaves the inventory,
	 * which is what makes a finished clue stop showing on the map without anything having to say so.
	 */
	@Nullable
	public CompanionSnapshot.Clue read()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return null;
		}

		/*
		 * The scroll whose text is held, while it is still carried; otherwise the first in the
		 * inventory. Keeping the held one means a second scroll picked up into an earlier slot
		 * cannot take its text, and a shuffle of the inventory cannot wipe it.
		 */
		int scrollId = -1;
		String tier = null;
		for (Item item : inventory.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			String found = tierOf(item.getId());
			if (found == null)
			{
				continue;
			}
			if (item.getId() == lastScrollItemId)
			{
				scrollId = item.getId();
				tier = found;
				break;
			}
			if (tier == null)
			{
				scrollId = item.getId();
				tier = found;
			}
		}

		if (tier == null)
		{
			reset();
			return null;
		}

		/** A different scroll is a different clue; the remembered text belongs to the old one. */
		if (scrollId != lastScrollItemId)
		{
			lastStepText = null;
			mapTile = null;
			lastScrollItemId = scrollId;
		}

		CompanionSnapshot.Clue clue = new CompanionSnapshot.Clue();
		clue.tier = tier;
		clue.text = lastStepText;
		clue.itemId = scrollId;
		clue.mapTile = mapTile;
		clue.coordTile = lastStepText == null ? null : parseCoordinate(lastStepText);
		current = clue;
		return clue;
	}

	/** The clue as last read on the client thread, or null. Any thread; see {@link #current}. */
	@Nullable
	CompanionSnapshot.Clue current()
	{
		return current;
	}

	/**
	 * Where a Route press for this clue goes: the coordinate's dig spot or the beginner map's tile,
	 * or null for a step that is words, which the map cannot yet be asked about from here.
	 */
	@Nullable
	static CompanionSnapshot.Tile routeTile(@Nullable CompanionSnapshot.Clue clue)
	{
		if (clue == null)
		{
			return null;
		}
		return clue.coordTile != null ? clue.coordTile : clue.mapTile;
	}

	private boolean carried(int itemId)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return false;
		}
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				return true;
			}
		}
		return false;
	}

	/** Beginner pictures share an item id; only the opened interface identifies the destination. */
	public void onMapWidget(int groupId)
	{
		BeginnerMapClue picture = BeginnerMapClue.forWidgetID(groupId);
		if (picture == null) return;
		int opening = openingScrollItemId;
		openingScrollItemId = -1;
		if (read() == null) return;
		/* The picture belongs to the scroll that was Read, wherever another tier sits. */
		if (opening > 0 && opening != lastScrollItemId && carried(opening))
		{
			lastScrollItemId = opening;
			lastStepText = null;
		}
		if (!"beginner".equals(tierOf(lastScrollItemId))) return;
		// This implementation returns its stored world point; it does not use a clue-plugin instance.
		WorldPoint point = picture.getLocation(null);
		lastStepText = null;
		mapTile = new CompanionSnapshot.Tile(point.getX(), point.getY(), point.getPlane());
	}

	/**
	 * The tier this item is a clue scroll of, or null if it is not one.
	 *
	 * <p>Guarded, because an item composition lookup for an id the client has not cached can throw,
	 * and a plugin that throws inside a game-thread callback takes more than itself down. A failure
	 * means one snapshot without a clue, which the map already renders correctly.
	 */
	@Nullable
	private String tierOf(int itemId)
	{
		try
		{
			ItemComposition composition = client.getItemDefinition(itemId);
			String name = composition == null ? null : composition.getName();
			if (name == null)
			{
				return null;
			}
			Matcher m = SCROLL_NAME.matcher(name.toLowerCase().trim());
			return m.matches() ? m.group(1) : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * Degrees and minutes to a game tile.
	 *
	 * <p>Fixed game geometry: the coordinate grid's origin sits at tile (2440, 3161) and a degree is
	 * 32 tiles, so this arithmetic is exact rather than approximate — which is why a coordinate clue
	 * needs no lookup table on either side of the wire.
	 *
	 * <p>Package-private so it can be unit tested without a running client; it is pure arithmetic on
	 * a string and deserves to be checked that way.
	 */
	@Nullable
	static CompanionSnapshot.Tile parseCoordinate(String text)
	{
		Matcher m = COORDINATE.matcher(text);
		if (!m.find())
		{
			return null;
		}
		try
		{
			int latDeg = Integer.parseInt(m.group(1));
			int latMin = Integer.parseInt(m.group(2));
			boolean north = "north".equalsIgnoreCase(m.group(3));
			int lonDeg = Integer.parseInt(m.group(4));
			int lonMin = Integer.parseInt(m.group(5));
			boolean east = "east".equalsIgnoreCase(m.group(6));

			double latMinutes = (latDeg * 60.0 + latMin) * (north ? 1 : -1);
			double lonMinutes = (lonDeg * 60.0 + lonMin) * (east ? 1 : -1);

			int y = (int) Math.round(3161 + latMinutes * 32.0 / 60.0);
			int x = (int) Math.round(2440 + lonMinutes * 32.0 / 60.0);
			return new CompanionSnapshot.Tile(x, y, 0);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
