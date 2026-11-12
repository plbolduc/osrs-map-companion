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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import static com.pbolduc.mapcompanion.CompanionSnapshot.Boat;

/**
 * The player's own boats on the game's world map, a pin at each mooring one is at.
 *
 * <p>The website marks the same moorings, from the same reading, and every voyage this plugin and
 * the map plan starts at one of them. Here it needs nothing from the map at all: {@link BoatReader}
 * reads the game, voyages already tick by that reading without sending it, and the tile comes from
 * {@code /moorings.txt}, the map's mooring table bundled by its {@code sync:plugin-moorings} and
 * held to it by the map's contract test. So the pin works before Connect and with every sharing
 * choice off, and nothing leaves the machine for it.
 *
 * <p><b>The join is the map's.</b> The reader names a port by the game's own {@code SailingDock}
 * table, and the table here is keyed by that name, as the map's {@code mooringNamed} is. A port it
 * does not list draws no pin rather than a guessed one, and a boat lost, capsized or bottled has no
 * port to draw.
 *
 * <p><b>Nothing said is nothing drawn.</b> The reader says nothing for fifteen seconds after a
 * login or a hop, while the player stands on a deck, and from a slot it cannot read. Each of those
 * takes the pins away rather than leaving the last ones: on a deck, one of them is the boat under
 * the player and no longer at its mooring, and which one the reading cannot say.
 *
 * <p>A pin does not hold the map's edge. A destination is somewhere the player is going and wants
 * pointed at; a moored boat is somewhere they look for, and five discs round the border would crowd
 * out the one that is.
 */
@Singleton
public class BoatPins
{
	/** Each mooring by the name the game gives its port, at the map's tile. */
	static final Map<String, WorldPoint> MOORINGS = Collections.unmodifiableMap(moorings(Resources.lines("/moorings.txt")));

	private final WorldMapPointManager points;
	private final ClientThread clientThread;

	/** Client thread only. */
	private final List<MapPin> drawn = new ArrayList<>();
	/** What {@link #drawn} shows, so an unchanged reading every tick costs one comparison. */
	private String drawnKey = "";
	/** Whether any pin is up, for {@link #clear} on another thread: nothing to take down, nothing queued. */
	private volatile boolean anyDrawn;

	@Inject
	BoatPins(WorldMapPointManager points, ClientThread clientThread)
	{
		this.points = points;
		this.clientThread = clientThread;
	}

	/**
	 * Draw these boats, or none for null. Called on the client thread, each game tick; returns at
	 * once when the reading has not changed.
	 */
	void sync(@Nullable List<Boat> boats)
	{
		Map<String, List<Boat>> byPort = byMooring(boats);
		String key = key(byPort);
		if (key.equals(drawnKey))
		{
			return;
		}
		removeNow();
		for (Map.Entry<String, List<Boat>> entry : byPort.entrySet())
		{
			String tooltip = tooltip(entry.getKey(), entry.getValue());
			MapPin pin = MapPin.boat(MOORINGS.get(entry.getKey()), () -> tooltip);
			pin.setSnapToEdge(false);
			points.add(pin);
			drawn.add(pin);
		}
		drawnKey = key;
		anyDrawn = !drawn.isEmpty();
	}

	/** Take every pin away: a logout, the setting turned off, the plugin shut down. */
	void clear()
	{
		if (anyDrawn)
		{
			clientThread.invoke(this::removeNow);
		}
	}

	/** How many pins are on the map, for tests. */
	int count()
	{
		return drawn.size();
	}

	private void removeNow()
	{
		for (MapPin pin : drawn)
		{
			points.remove(pin);
		}
		drawn.clear();
		drawnKey = "";
		anyDrawn = false;
	}

	/** The boats the table can place, by mooring, in the order the slots list them. */
	static Map<String, List<Boat>> byMooring(@Nullable List<Boat> boats)
	{
		Map<String, List<Boat>> byPort = new LinkedHashMap<>();
		if (boats == null)
		{
			return byPort;
		}
		for (Boat boat : boats)
		{
			if (boat.port != null && MOORINGS.containsKey(boat.port))
			{
				byPort.computeIfAbsent(boat.port, port -> new ArrayList<>()).add(boat);
			}
		}
		return byPort;
	}

	/** Everything a pin is drawn from, so {@link #sync} redraws exactly when one would differ. */
	static String key(Map<String, List<Boat>> byPort)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, List<Boat>> entry : byPort.entrySet())
		{
			out.append(entry.getKey());
			for (Boat boat : entry.getValue())
			{
				out.append('|').append(boat.slot).append(':').append(boat.focus);
			}
			out.append(';');
		}
		return out.toString();
	}

	/**
	 * Whose, where, and the one thing about a boat that changes a route, in the website's words:
	 * "Your boat · Catherby · greater teleport focus". Names are read only as evidence that a slot
	 * holds a boat, so a boat is "your boat" here as it is there.
	 */
	static String tooltip(String port, List<Boat> boats)
	{
		int focused = 0;
		for (Boat boat : boats)
		{
			if ("greater".equals(boat.focus))
			{
				focused++;
			}
		}
		String whose = boats.size() == 1 ? "Your boat" : boats.size() + " of your boats";
		String focus = focused == 0
			? ""
			: boats.size() == 1 ? " · greater teleport focus" : " · " + focused + " with a greater teleport focus";
		return whose + " · " + port + focus;
	}

	/** {@code name|x|y} lines as tiles on the surface; a malformed line is left out. */
	static Map<String, WorldPoint> moorings(List<String> lines)
	{
		Map<String, WorldPoint> out = new HashMap<>();
		for (String line : lines)
		{
			String[] parts = line.split("\\|");
			if (parts.length != 3)
			{
				continue;
			}
			try
			{
				out.put(parts[0], new WorldPoint(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()), 0));
			}
			catch (NumberFormatException malformed)
			{
				// Left out: the tests read the real file, so a bad row fails the build first.
			}
		}
		return out;
	}
}
