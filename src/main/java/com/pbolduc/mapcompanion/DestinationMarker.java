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
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
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
 * <p>Both are {@link MapPin}s, so each stays on the map's edge, pointing the way and saying what it
 * is, while its place is out of view — which is when somebody needs a destination most. A click pans the world map;
 * it does not move the player. That is still drawing, not acting — the same line every other
 * part of this plugin holds.
 *
 * <p>The meetup is the one absolute time in the protocol, so its tooltip is computed when the map
 * asks for it rather than once: "Meetup in 12m" has to keep counting.
 *
 * <p>When the destination came with its route, each step's end has a numbered pin too, with the
 * number the sidebar and the website give that step. Only the current step's pin holds the map's
 * edge: a row of discs round the border would say nothing about which way to go. The destination's
 * own pin leaves the edge to it, and holds it again when no step's pin is current, as on the last
 * step. The steps behind it fade, and the last step's pin is left out where it would sit on the
 * destination's own.
 *
 * <p>While Route here plans, the spot it was pressed on has the destination's pin, faded, so the
 * press shows it took on the map it was made on. It does not hold the edge, being where the player
 * is looking, and it goes with the answer: a refusal leaves nothing, and a route draws its own pins.
 * It belongs to the destinations it was pressed among, so a group left meanwhile takes it too.
 *
 * <p>At most one of each is live. Sync and remove run on the client thread because the manager
 * mutates the world map's overlay list, which is game state.
 */
@Singleton
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public class DestinationMarker
{
	private final WorldMapPointManager points;
	private final DestinationState state;
	private final MapCompanionConfig config;
	private final ClientThread clientThread;
	private final DestinationRoute route;
	private final FarmRunRoute farming;
	/** Which way a held pin points. A field so the many tests that build this without a map need not hand it one. */
	@Inject
	MapEdge edge;
	private final List<MapPin> farmStops = new ArrayList<>();
	private String farmKey;

	private MapPin current;
	private String currentKey;
	private MapPin meetup;
	private String meetupKey;
	private final List<MapPin> steps = new ArrayList<>();
	private String stepsKey;
	/** Route here's spot while its press plans, and the destinations' generation it was pressed in. */
	private WorldPoint planningSpot;
	private long planningGeneration;
	private MapPin planning;

	/** A step's disc ahead of the player: the map's light ink, so amber stays the place you are going to. */
	static final Color STEP = new Color(228, 228, 231);

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

	/** Mark the spot Route here was pressed on while its press plans, or take the mark away with null. */
	void planning(@Nullable WorldPoint spot)
	{
		clientThread.invoke(() ->
		{
			planningSpot = spot;
			planningGeneration = state.generation();
			syncPlanning();
		});
	}

	private void syncNow()
	{
		List<StepPin> shown = shownSteps();
		syncDestination(shown.stream().anyMatch(pin -> pin.place == 0));
		syncMeetup();
		syncSteps(shown);
		syncFarmStops();
		syncPlanning();
	}

	private void syncPlanning()
	{
		WorldPoint spot = planningGeneration == state.generation() ? planningSpot : null;
		if (planning != null && planning.getWorldPoint().equals(spot))
		{
			return;
		}
		removePlanning();
		if (spot == null)
		{
			return;
		}
		planning = new MapPin(spot, MapPin.AMBER, "", true, WorldMapRouteHere.OPTION, () -> "Planning the way here…");
		planning.setSnapToEdge(false);
		points.add(planning);
	}

	/** @param stepLeads whether the current step's pin is drawn, holding the map's edge */
	private void syncDestination(boolean stepLeads)
	{
		RelayResponse.Destination destination =
			config.showDestination() || state.hasLocalDestination() ? state.getDestination() : null;
		if (destination == null || (farming != null && farming.owns(destination)))
		{
			removeDestination();
			return;
		}

		WorldPoint where = new WorldPoint(destination.x, destination.y, destination.plane);
		String tooltip = destination.world == null
						 ? destination.name
						 : destination.name + " (W" + destination.world + ")";
		String key = where + "|" + tooltip + "|" + stepLeads;
		if (current != null && key.equals(currentKey))
		{
			return;
		}

		removeDestination();
		current = new MapPin(where, MapPin.AMBER, "", false, "Destination", () -> tooltip).atEdge(edge, destination.name);
		/*
		 * Rebuilt rather than switched when this changes: a point let go of while held at the edge
		 * keeps the edge's disc for a picture, because RuneLite only swaps it back while it snaps.
		 */
		current.setSnapToEdge(!stepLeads);
		currentKey = key;
		points.add(current);
	}

	/**
	 * The week's soonest meetup, while meetups are being received at all. A meetup with no place —
	 * a time with nowhere attached — has nothing to pin.
	 */
	private void syncMeetup()
	{
		List<RelayResponse.Meetup> events = config.showDestination() ? state.getEvents() : null;
		RelayResponse.Meetup event = events == null || events.isEmpty() ? null : events.get(0);
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
				Locale.getDefault(Locale.Category.FORMAT), world)).atEdge(edge, title);
		meetupKey = key;
		points.add(meetup);
	}

	/**
	 * The route's step pins, while the destination they lead to is drawn and its route is the one
	 * {@link DestinationRoute} holds for it.
	 */
	private List<StepPin> shownSteps()
	{
		// Run ordinals label its stops; a second set of step ordinals would make each number ambiguous.
		if (farming != null && farming.owns(state.getDestination()))
		{
			return new ArrayList<>();
		}
		RelayResponse.Destination destination =
			config.showDestination() || state.hasLocalDestination() ? state.getDestination() : null;
		DestinationRoute.View view = destination == null ? null : route.view();
		return view == null || !view.key.equals(DestinationRoute.keyOf(destination))
			? new ArrayList<>()
			: stepPins(view);
	}

	private void syncSteps(List<StepPin> wanted)
	{
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
			"Step " + number, () -> tooltip).atEdge(edge, pin.title);
		drawn.setSnapToEdge(pin.place == 0);
		steps.add(drawn);
		points.add(drawn);
	}

	/** One step's pin, as plain values, so which pins a route gets is pinned by a test. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class StepPin
	{
		final int number;
		final WorldPoint where;
		final String title;
		/** Negative for a step done, zero for the current one, positive ahead. */
		final int place;

		/** The first step this pin stands for, where several end on its tile. */
		final int first;
		/** The last step this pin stands for: the next pin's number follows it. */
		final int lastStep;

		StepPin(int number, WorldPoint where, String title, int place)
		{
			this(number, where, title, place, number, number);
		}

		StepPin(int number, WorldPoint where, String title, int place, int first)
		{
			this(number, where, title, place, first, number);
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
	 *
	 * <p>Steps that end on the same tile one after another share one pin: a voyage's walk to the
	 * mooring and its boarding both end at the mooring, and two numbers stacked there would hide each
	 * other. The shared pin carries the current step's number while one of them is current, and the
	 * first's otherwise.
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
			StepPin before = pins.isEmpty() ? null : pins.get(pins.size() - 1);
			if (before != null && before.where.equals(where) && before.lastStep == i)
			{
				if (i == view.step)
				{
					pins.set(pins.size() - 1, new StepPin(i + 1, where, list.get(i).title, 0, before.first));
				}
				else
				{
					pins.set(pins.size() - 1, new StepPin(before.number, where, before.title, before.place, before.first, i + 1));
				}
				continue;
			}
			pins.add(new StepPin(i + 1, where, list.get(i).title, Integer.compare(i, view.step)));
		}
		return pins;
	}

	private void syncFarmStops()
	{
		FarmRunState.View view = farming == null ? null : farming.view();
		String key = view == null ? null : view.run.id + "|" + view.revision + "|" + view.done + "|" + view.visited + "|" + view.following;
		if (Objects.equals(key, farmKey))
		{
			return;
		}
		removeFarmStops();
		farmKey = key;
		if (view == null)
		{
			return;
		}
		RelayResponse.FarmStop next = view.next();
		List<RelayResponse.FarmStop> ordered = new ArrayList<>(view.run.stops);
		if (next != null)
		{
			ordered.remove(next);
			ordered.add(next); // The current stop draws over another stop on the same tile.
		}
		for (RelayResponse.FarmStop stop : ordered)
		{
			boolean done = view.done.contains(stop.n);
			boolean visited = view.visited.contains(stop.n);
			boolean current = next != null && stop.n == next.n;
			String words = stop.n + ". " + stop.name + (done ? " · done" : visited ? " · visited" : current ? " · next" : "");
			MapPin pin = new MapPin(FarmRunState.point(stop), current ? MapPin.AMBER : STEP, String.valueOf(stop.n),
				done || visited, "Farm stop " + stop.n, () -> words).atEdge(edge, stop.name);
			pin.setSnapToEdge(current && view.following);
			farmStops.add(pin);
			points.add(pin);
		}
	}

	private void removeFarmStops()
	{
		for (MapPin pin : farmStops)
		{
			points.remove(pin);
		}
		farmStops.clear();
		farmKey = null;
	}

	private void removeNow()
	{
		removeDestination();
		removeMeetup();
		removeSteps();
		removeFarmStops();
		planningSpot = null;
		removePlanning();
	}

	private void removePlanning()
	{
		if (planning != null)
		{
			points.remove(planning);
			planning = null;
		}
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
