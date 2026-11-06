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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The player's boats on the game's world map: which moorings get a pin, what each says, and when
 * they are drawn again or taken down.
 *
 * <p>The manager is a list and the client thread runs what it is handed at once, as in
 * {@link GroupMarkersTest}. The mooring table is the real bundled file.
 */
public class BoatPinsTest
{
	private final List<WorldMapPoint> drawn = new ArrayList<>();
	private final WorldMapPointManager manager = new WorldMapPointManager()
	{
		@Override
		public void add(WorldMapPoint point)
		{
			drawn.add(point);
		}

		@Override
		public void remove(WorldMapPoint point)
		{
			drawn.remove(point);
		}
	};
	private final ClientThread now = new ClientThread()
	{
		@Override
		public void invoke(Runnable r)
		{
			r.run();
		}
	};
	private final BoatPins pins = new BoatPins(manager, now);

	private static CompanionSnapshot.Boat boat(int slot, String port, String focus)
	{
		CompanionSnapshot.Boat boat = new CompanionSnapshot.Boat();
		boat.slot = slot;
		boat.port = port;
		boat.dock = port == null ? null : 0;
		boat.focus = focus;
		return boat;
	}

	@Test
	public void bundlesEveryMooringTheMapPublishes()
	{
		/* The map's table, which its contract test holds row for row; well short of it is a broken read. */
		assertTrue(BoatPins.MOORINGS.size() >= 50);
		assertEquals(new WorldPoint(3051, 3193, 0), BoatPins.MOORINGS.get("Port Sarim"));
		assertEquals(new WorldPoint(2796, 3412, 0), BoatPins.MOORINGS.get("Catherby"));
		/* The overworld harbour, where the game's world map draws Prifddinas, and not the city's twin. */
		assertEquals(new WorldPoint(2158, 3324, 0), BoatPins.MOORINGS.get("Prifddinas"));
		assertFalse(BoatPins.MOORINGS.containsKey("Prifddinas (city space)"));
	}

	@Test
	public void pinsEachMooringAMooredBoatIsAt()
	{
		pins.sync(Arrays.asList(boat(1, "Catherby", "greater"), boat(2, "Port Sarim", "none")));
		assertEquals(2, drawn.size());
		assertEquals(new WorldPoint(2796, 3412, 0), drawn.get(0).getWorldPoint());
		assertEquals("Your boat · Catherby · greater teleport focus", drawn.get(0).getTooltip());
		assertEquals("Your boat · Port Sarim", drawn.get(1).getTooltip());
		/* A moored boat is looked for, not pointed at: it leaves the map's edge to a destination. */
		assertFalse(drawn.get(0).isSnapToEdge());
		assertEquals("Your boat", drawn.get(0).getName());
	}

	@Test
	public void drawsTwoBoatsAtOneMooringAsOnePin()
	{
		pins.sync(Arrays.asList(boat(1, "Catherby", "greater"), boat(3, "Catherby", null)));
		assertEquals(1, drawn.size());
		assertEquals("2 of your boats · Catherby · 1 with a greater teleport focus", drawn.get(0).getTooltip());
	}

	@Test
	public void leavesOutABoatWithNoPortOrAPortTheTableDoesNotList()
	{
		/* Lost, capsized or bottled is sent with no port; a name the table lacks is never guessed at. */
		pins.sync(Arrays.asList(boat(1, null, "none"), boat(2, "Somewhere new", "none"), boat(3, "Catherby", "none")));
		assertEquals(1, drawn.size());
		assertEquals("Your boat · Catherby", drawn.get(0).getTooltip());
	}

	@Test
	public void keepsItsPinsWhileTheReadingIsUnchanged()
	{
		pins.sync(Collections.singletonList(boat(1, "Catherby", "none")));
		WorldMapPoint first = drawn.get(0);
		pins.sync(Collections.singletonList(boat(1, "Catherby", "none")));
		assertSame(first, drawn.get(0));
		assertEquals(1, drawn.size());

		/* The boat sailed: the old pin goes and one stands at its new mooring. */
		pins.sync(Collections.singletonList(boat(1, "Port Sarim", "none")));
		assertEquals(1, drawn.size());
		assertNotSame(first, drawn.get(0));
		assertEquals(new WorldPoint(3051, 3193, 0), drawn.get(0).getWorldPoint());

		/* A focus fitted changes what the pin says. */
		WorldMapPoint sarim = drawn.get(0);
		pins.sync(Collections.singletonList(boat(1, "Port Sarim", "greater")));
		assertNotSame(sarim, drawn.get(0));
		assertEquals("Your boat · Port Sarim · greater teleport focus", drawn.get(0).getTooltip());
	}

	@Test
	public void takesEveryPinAwayForNothingSaidAndOnClear()
	{
		pins.sync(Arrays.asList(boat(1, "Catherby", "none"), boat(2, "Port Sarim", "none")));
		pins.sync(null);
		assertTrue(drawn.isEmpty());
		assertEquals(0, pins.count());

		pins.sync(Collections.singletonList(boat(1, "Catherby", "none")));
		pins.clear();
		assertTrue(drawn.isEmpty());
		/* And draws again after a clear, though the reading is the one it drew before. */
		pins.sync(Collections.singletonList(boat(1, "Catherby", "none")));
		assertEquals(1, drawn.size());
	}

	@Test
	public void readsOnlyWellFormedRows()
	{
		assertEquals(
			Collections.singletonMap("Catherby", new WorldPoint(2796, 3412, 0)),
			BoatPins.moorings(Arrays.asList("Catherby|2796|3412", "Broken|x|1", "Too|few", "Too|many|1|2")));
	}

	@Test
	public void watchesTheVarbitsAMooringIsReadFromAndNoOthers()
	{
		for (int i = 0; i < BoatReader.SLOTS; i++)
		{
			assertTrue(BoatReader.watches(BoatReader.PORT[i]));
			assertTrue(BoatReader.watches(BoatReader.FOCUS[i]));
			assertTrue(BoatReader.watches(BoatReader.OWNED[i]));
			/* A name part changes nothing a pin says. */
			assertFalse(BoatReader.watches(BoatReader.NAME[i][0]));
		}
	}
}
