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

import java.awt.Color;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * The world-map markers for what the map sent: the destination, and the next meetup.
 *
 * <p>RuneLite already projects a world-map point onto the world map and clips it to the map
 * view. The overlay this replaced hand-projected a tile onto
 * {@code InterfaceID.Worldmap.MAP_CONTAINER}, whose bounds include the legend and search bar, so
 * the dot drew over chrome and the projection was approximate.
 *
 * <p>Both are {@link MapPin}s, so each stays on the map's edge, pointing the way, while its place
 * is out of view — which is when somebody needs a destination most. A click pans the world map;
 * it does not move the player. That is still drawing, not acting — the same line every other
 * part of this plugin holds.
 *
 * <p>The meetup is the one absolute time in the protocol, so its tooltip is computed when the map
 * asks for it rather than once: "Meetup in 12m" has to keep counting.
 *
 * <p>When the destination came with its route, each step's end has a numbered pin too, with the
 * number the sidebar and the website give that step. Only the current step's pin holds the map's
 * edge: a row of discs round the border would say nothing about which way to go. The steps behind
 * it fade, and the last step's pin is left out where it would sit on the destination's own.
 *
 * <p>At most one of each is live. Sync and remove run on the client thread because the manager
 * mutates the world map's overlay list, which is game state.
 */
@Singleton
public class DestinationMarker
{
	private final WorldMapPointManager points;
	private final DestinationState state;
	private final MapCompanionConfig config;
	private final ClientThread clientThread;
	private final DestinationRoute route;

	private MapPin current;
	private String currentKey;
	private MapPin meetup;
	private String meetupKey;
	private final List<MapPin> steps = new ArrayList<>();
	private String stepsKey;

	/** A step's disc ahead of the player: the map's light ink, so amber stays the place you are going to. */
	static final Color STEP = new Color(228, 228, 231);

	@Inject
	DestinationMarker(
		WorldMapPointManager points,
		DestinationState state,
		MapCompanionConfig config,
		ClientThread clientThread,
		DestinationRoute route)
	{
		this.points = points;
		this.state = state;
		this.config = config;
		this.clientThread = clientThread;
		this.route = route;
	}

	/** Rebuild the live points from {@link DestinationState}, or remove them. */
	void sync()
	{
		clientThread.invoke(this::syncNow);
	}

	/** Drop the points. Called from shutDown so a disabled plugin leaves the map clean. */
	void remove()
	{
		clientThread.invoke(this::removeNow);
	}

	private void syncNow()
	{
		syncDestination();
		syncMeetup();
		syncSteps();
	}

	private void syncDestination()
	{
		RelayResponse.Destination destination =
			config.showDestination() || state.hasLocalDestination() ? state.getDestination() : null;
		if (destination == null)
		{
			removeDestination();
			return;
		}

		WorldPoint where = new WorldPoint(destination.x, destination.y, destination.plane);
		String tooltip = destination.world == null
						 ? destination.name
						 : destination.name + " (W" + destination.world + ")";
		String key = where + "|" + tooltip;
		if (current != null && key.equals(currentKey))
		{
			return;
		}

		removeDestination();
		current = new MapPin(where, MapPin.AMBER, "", false, "Destination", () -> tooltip);
		currentKey = key;
		points.add(current);
	}

	/**
	 * The meetup the map says is next, while meetups are being received at all. A meetup with no
	 * place — an old map, or a time with nowhere attached — has nothing to pin.
	 */
	private void syncMeetup()
	{
		RelayResponse.Event event = config.showDestination() ? state.getEvent() : null;
		if (event == null || (event.x == 0 && event.y == 0))
		{
			removeMeetup();
			return;
		}
		WorldPoint where = new WorldPoint(event.x, event.y, event.plane);
		String title = event.title == null || event.title.trim().isEmpty() ? "Meetup" : event.title.trim();
		String key = where + "|" + title + "|" + event.startsAtMs + "|" + Objects.toString(event.world);
		if (meetup != null && key.equals(meetupKey))
		{
			return;
		}
		removeMeetup();
		long startsAtMs = event.startsAtMs;
		Integer world = event.world;
		meetup = new MapPin(where, MapPin.AMBER, "M", false, title, () -> title + " · "
			+ MapCompanionPanel.meetupWhen(startsAtMs, System.currentTimeMillis(), ZoneId.systemDefault(),
				Locale.getDefault(Locale.Category.FORMAT), world));
		meetupKey = key;
		points.add(meetup);
	}

	/**
	 * The route's step pins, while the destination they lead to is drawn and its route is the one
	 * {@link DestinationRoute} holds for it.
	 */
	private void syncSteps()
	{
		RelayResponse.Destination destination =
			config.showDestination() || state.hasLocalDestination() ? state.getDestination() : null;
		DestinationRoute.View view = destination == null ? null : route.view();
		List<StepPin> wanted = view == null || !view.key.equals(DestinationRoute.keyOf(destination))
			? new ArrayList<>()
			: stepPins(view);
		String key = wanted.toString();
		if (key.equals(stepsKey))
		{
			return;
		}
		removeSteps();
		/* The current step last, so it is drawn over the ones it sits near. */
		for (StepPin pin : wanted)
		{
			if (pin.place != 0)
			{
				add(pin);
			}
		}
		for (StepPin pin : wanted)
		{
			if (pin.place == 0)
			{
				add(pin);
			}
		}
		stepsKey = key;
	}

	private void add(StepPin pin)
	{
		String number = String.valueOf(pin.number);
		String tooltip = pin.number + ". " + pin.title;
		MapPin drawn = new MapPin(pin.where, pin.place == 0 ? MapPin.AMBER : STEP, number, pin.place < 0,
			"Step " + number, () -> tooltip);
		drawn.setSnapToEdge(pin.place == 0);
		steps.add(drawn);
		points.add(drawn);
	}

	/** One step's pin, as plain values, so which pins a route gets is pinned by a test. */
	static final class StepPin
	{
		final int number;
		final WorldPoint where;
		final String title;
		/** Negative for a step done, zero for the current one, positive ahead. */
		final int place;

		StepPin(int number, WorldPoint where, String title, int place)
		{
			this.number = number;
			this.where = where;
			this.title = title;
			this.place = place;
		}

		@Override
		public String toString()
		{
			return number + "@" + where.getX() + "," + where.getY() + "," + where.getPlane() + (place < 0 ? "-" : place == 0 ? "*" : "+");
		}
	}

	/**
	 * A pin for each step of the way being shown that names where it ends, numbered as the sidebar
	 * numbers it — none once arrived, and none on the destination's own tile, where its pin is.
	 */
	static List<StepPin> stepPins(DestinationRoute.View view)
	{
		List<StepPin> pins = new ArrayList<>();
		if (!view.hasRoute() || view.arrived())
		{
			return pins;
		}
		List<RelayResponse.RouteStep> list = view.steps();
		for (int i = 0; i < list.size(); i++)
		{
			RelayResponse.RouteTile at = list.get(i).at;
			if (at == null)
			{
				continue;
			}
			WorldPoint where = DestinationRoute.point(at);
			if (where.equals(view.target))
			{
				continue;
			}
			pins.add(new StepPin(i + 1, where, list.get(i).title, Integer.compare(i, view.step)));
		}
		return pins;
	}

	private void removeNow()
	{
		removeDestination();
		removeMeetup();
		removeSteps();
	}

	private void removeSteps()
	{
		for (MapPin pin : steps)
		{
			points.remove(pin);
		}
		steps.clear();
		stepsKey = null;
	}

	private void removeDestination()
	{
		if (current != null)
		{
			points.remove(current);
			current = null;
			currentKey = null;
		}
	}

	private void removeMeetup()
	{
		if (meetup != null)
		{
			points.remove(meetup);
			meetup = null;
			meetupKey = null;
		}
	}
}
