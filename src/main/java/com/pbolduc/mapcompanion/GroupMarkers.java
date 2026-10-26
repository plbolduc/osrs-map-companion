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
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * The other members of your group, on the game's own world map.
 *
 * <p>RuneLite's Party plugin draws party members on the world map, and a listed Plugin Hub plugin
 * draws friends there through a server of its own, so the feature itself is ordinary. What this
 * class holds to is narrower than either:
 *
 * <ul>
 *   <li><b>It draws only what the map hands it.</b> Who appears is decided on the server, in
 *       {@code lib/companion-plugin-members.ts}: members of this group who share their position,
 *       never this character, never a raid room the template tile would misplace. Nothing here
 *       reads another player from the game — RuneLite refuses plugins that crowdsource other
 *       players' locations, and this one does not see anybody it was not sent.</li>
 *   <li><b>Off until asked.</b> {@code showGroupOnWorldMap} starts off. With it off the stream
 *       does not ask for members, and the map sends none.</li>
 *   <li><b>Nothing outlives the connection.</b> A dropped stream, a logout, a group change or the
 *       setting turned off clears every point; a stale dot claiming somebody is still there is
 *       the one thing worse than no dot.</li>
 *   <li><b>It draws.</b> A point pans the world map when clicked. It does not walk, click, hop or
 *       follow anybody.</li>
 * </ul>
 *
 * <p>Points are moved rather than rebuilt when only the tile changed, because a walking group
 * changes tiles every second and a new picture per step is garbage for nothing. Everything that
 * touches the manager runs on the client thread, which owns the world map's overlay list.
 */
@Singleton
public class GroupMarkers
{
	private static final Color FALLBACK = new Color(161, 161, 170);

	/** The map's own limit on a group (MAX_MEMBERS_PER_GROUP in its relay). */
	static final int MAX_MEMBERS = 25;

	private final WorldMapPointManager points;
	private final ClientThread clientThread;

	/** Client thread only. */
	private final Map<String, Drawn> drawn = new HashMap<>();
	/** Queued work applies only while it is still the latest requested state. */
	private long revision;
	private boolean anyDrawn;

	@Inject
	GroupMarkers(WorldMapPointManager points, ClientThread clientThread)
	{
		this.points = points;
		this.clientThread = clientThread;
	}

	/** Replace what is drawn with these members. Null is "not asked", which changes nothing. */
	synchronized void show(@Nullable List<RelayResponse.MemberDot> members)
	{
		if (members == null)
		{
			return;
		}
		List<RelayResponse.MemberDot> copy = new ArrayList<>(members);
		long frameRevision = ++revision;
		clientThread.invoke(() ->
		{
			synchronized (GroupMarkers.this)
			{
				if (frameRevision == revision)
				{
					showNow(copy);
				}
			}
		});
	}

	synchronized void clear()
	{
		// A frame may be queued even when no point has reached the client thread yet.
		long clearRevision = ++revision;
		if (anyDrawn)
		{
			clientThread.invoke(() ->
			{
				synchronized (GroupMarkers.this)
				{
					if (clearRevision == revision)
					{
						clearNow();
					}
				}
			});
		}
	}

	/** How many points are on the map, for tests; the map itself is the only reader that matters. */
	int count()
	{
		return drawn.size();
	}

	private void showNow(List<RelayResponse.MemberDot> members)
	{
		Set<String> seen = new HashSet<>();
		for (RelayResponse.MemberDot dot : members)
		{
			if (seen.size() >= MAX_MEMBERS)
			{
				/* More than a group can hold is not a group; the map draws no more points than that. */
				break;
			}
			if (dot == null || dot.id == null || dot.id.isEmpty() || !seen.add(dot.id))
			{
				continue;
			}
			WorldPoint where = new WorldPoint(dot.x, dot.y, dot.plane);
			Look look = Look.of(dot);
			Drawn existing = drawn.get(dot.id);
			if (existing != null && existing.look.equals(look))
			{
				existing.pin.setWorldPoint(where);
				continue;
			}
			if (existing != null)
			{
				points.remove(existing.pin);
			}
			MapPin pin = new MapPin(
				where,
				MapPin.colour(dot.colour, FALLBACK),
				MapPin.initials(look.name),
				look.stale,
				look.name,
				() -> look.tooltip);
			points.add(pin);
			drawn.put(dot.id, new Drawn(pin, look));
		}
		drawn.entrySet().removeIf(entry ->
		{
			if (seen.contains(entry.getKey()))
			{
				return false;
			}
			points.remove(entry.getValue().pin);
			return true;
		});
		anyDrawn = !drawn.isEmpty();
	}

	private void clearNow()
	{
		for (Drawn d : drawn.values())
		{
			points.remove(d.pin);
		}
		drawn.clear();
		anyDrawn = false;
	}

	/** What a point is drawn from, apart from where. Two equal looks share a picture. */
	static final class Look
	{
		final String name;
		final String colour;
		final boolean stale;
		final String tooltip;

		private Look(String name, String colour, boolean stale, String tooltip)
		{
			this.name = name;
			this.colour = colour;
			this.stale = stale;
			this.tooltip = tooltip;
		}

		static Look of(RelayResponse.MemberDot dot)
		{
			String name = MapPin.safe(dot.name);
			if (name.isEmpty())
			{
				name = "Member";
			}
			return new Look(name, String.valueOf(dot.colour), dot.stale, tooltip(name, dot));
		}

		/** "Zezima · W302", then what the dot cannot say on its own. */
		static String tooltip(String name, RelayResponse.MemberDot dot)
		{
			StringBuilder text = new StringBuilder(name);
			if (dot.world != null)
			{
				text.append(" · W").append(dot.world);
			}
			if (dot.plane > 0)
			{
				text.append(" · floor ").append(dot.plane);
			}
			if (dot.instance)
			{
				text.append(" · in their own instance");
			}
			if (dot.stale)
			{
				text.append(" · not heard from lately");
			}
			return text.toString();
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Look))
			{
				return false;
			}
			Look other = (Look) o;
			return stale == other.stale && name.equals(other.name) && colour.equals(other.colour)
				&& tooltip.equals(other.tooltip);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, colour, stale, tooltip);
		}
	}

	private static final class Drawn
	{
		final MapPin pin;
		final Look look;

		Drawn(MapPin pin, Look look)
		{
			this.pin = pin;
			this.look = look;
		}
	}
}
