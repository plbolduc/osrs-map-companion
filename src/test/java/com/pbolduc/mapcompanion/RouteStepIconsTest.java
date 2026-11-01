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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The ids the map sends for a route step's picture, against the client's own names for them.
 *
 * <p>A step carries an item id or a spell's sprite id from the map's table ({@code lib/route-step-icons.ts}),
 * which this repository keeps a copy of in {@code contract/route-step-icons.json}; the map's contract test
 * holds that copy to the table row for row. Each row names the {@code gameval} constant its id was read
 * from, and this asserts the two are one number, so an id copied wrong on either side draws no wrong
 * picture in a player's sidebar: it fails here, naming the row.
 *
 * <p>Read by reflection because the constants are named by the table rather than by this source; the
 * plugin itself never reflects, and draws whatever id it is sent through {@link GameSprites}.
 */
public class RouteStepIconsTest
{
	private static final Path TABLE = Paths.get("src", "test", "resources", "contract", "route-step-icons.json");

	private static JsonObject table() throws IOException
	{
		return new JsonParser().parse(new String(Files.readAllBytes(TABLE), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	public void everyItemIsTheIdTheClientNames() throws IOException, ReflectiveOperationException
	{
		List<String> wrong = new ArrayList<>();
		int rows = 0;
		for (String part : new String[]{"itemsByLabel", "itemsByHead"})
		{
			for (Map.Entry<String, JsonElement> row : table().getAsJsonObject(part).entrySet())
			{
				JsonArray item = row.getValue().getAsJsonArray();
				int id = item.get(1).getAsInt();
				String constant = item.get(2).getAsString();
				int named = ItemID.class.getField(constant).getInt(null);
				if (named != id)
				{
					wrong.add(row.getKey() + ": " + id + " but ItemID." + constant + " is " + named);
				}
				rows++;
			}
		}
		assertEquals(new ArrayList<String>(), wrong);
		/* A table that collapsed to nothing would pass the loop above; the map ships about 135 items. */
		assertTrue("only " + rows + " item rows", rows > 100);
	}

	@Test
	public void everySpellIsTheSpriteTheClientNames() throws IOException, ReflectiveOperationException
	{
		List<String> wrong = new ArrayList<>();
		int rows = 0;
		for (Map.Entry<String, JsonElement> row : table().getAsJsonObject("spellsByHead").entrySet())
		{
			JsonArray spell = row.getValue().getAsJsonArray();
			int id = spell.get(0).getAsInt();
			/* "Magicon2.LUMBRIDGE_HOME_TELEPORT": a spellbook's class inside SpriteID, then the spell. */
			String[] name = spell.get(1).getAsString().split("[.]");
			assertEquals(row.getKey(), 2, name.length);
			int named = Class.forName(SpriteID.class.getName() + "$" + name[0]).getField(name[1]).getInt(null);
			if (named != id)
			{
				wrong.add(row.getKey() + ": " + id + " but SpriteID." + name[0] + "." + name[1] + " is " + named);
			}
			rows++;
		}
		assertEquals(new ArrayList<String>(), wrong);
		assertTrue("only " + rows + " spell rows", rows > 30);
	}

	/** The pictures on the reply's and the stream's routes and run are rows of the table, so the fixtures draw ids this test vouches for. */
	@Test
	public void theFixturesPicturesAreRowsOfTheTable() throws IOException
	{
		Set<Integer> items = new HashSet<>();
		for (String part : new String[]{"itemsByLabel", "itemsByHead"})
		{
			for (Map.Entry<String, JsonElement> row : table().getAsJsonObject(part).entrySet())
			{
				items.add(row.getValue().getAsJsonArray().get(1).getAsInt());
			}
		}
		Set<Integer> spells = new HashSet<>();
		for (Map.Entry<String, JsonElement> row : table().getAsJsonObject("spellsByHead").entrySet())
		{
			spells.add(row.getValue().getAsJsonArray().get(0).getAsInt());
		}
		List<Integer> drawn = new ArrayList<>();
		for (String fixture : new String[]{"reply.json", "stream.json"})
		{
			String json = new String(Files.readAllBytes(TABLE.resolveSibling(fixture)), StandardCharsets.UTF_8);
			RelayResponse.Directive directive = fixture.equals("reply.json")
				? new Gson().fromJson(json, RelayResponse.class).directive
				: new Gson().fromJson(json, RelayResponse.StreamDirective.class);
			List<RelayResponse.RouteStep> steps = new ArrayList<>();
			for (RelayResponse.RouteOption option : directive.destination.route.options)
			{
				steps.addAll(option.steps);
			}
			for (RelayResponse.FarmStop stop : directive.run.stops)
			{
				steps.addAll(stop.steps);
			}
			for (RelayResponse.RouteStep step : steps)
			{
				if (step.itemId != null)
				{
					assertTrue(fixture + ": item " + step.itemId + " is no row", items.contains(step.itemId));
					drawn.add(step.itemId);
				}
				if (step.spriteId != null)
				{
					assertTrue(fixture + ": sprite " + step.spriteId + " is no row", spells.contains(step.spriteId));
					drawn.add(step.spriteId);
				}
			}
		}
		List<Integer> each = Arrays.asList(SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT, ItemID.POH_TABLET_LUMBRIDGETELEPORT,
			ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.LUNAR_TABLET_CATHERBY_TELEPORT, SpriteID.MagicNecroOn.DRAYNOR_MANOR_TELEPORT);
		List<Integer> both = new ArrayList<>(each);
		both.addAll(each);
		assertEquals("in each fixture: the home teleport and the tablet on the route, the ring, the tablet and the spell on the run",
			both, drawn);
	}
}
