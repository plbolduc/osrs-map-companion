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

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * The way to the current destination, and how far along it the player is.
 *
 * <p>The map ranks the route and words every step; this holds which way is shown, which step is
 * the current one, and whether a Reroute is on its way. Nothing here plans anything. A step is done
 * when the player stands within {@link #REACHED} tiles of where it ends, on its floor, and the next
 * step becomes the current one. The game tick asks; the sidebar and the pins read a {@link View}.
 *
 * <p><b>Never ahead on a guess.</b> Only the current step's end moves the list on, with two
 * exceptions that are not guesses: a step that names no tile is passed when the step after it is
 * reached, since nothing else could ever show it done, and standing at the end of the last step is
 * arriving whatever way was taken. A player who goes another way is not followed down it; Reroute
 * plans again from where they stand. Inside an instance the tile is trusted only where the instance
 * is one place copied whole, see {@link #standing}.
 *
 * <p><b>A voyage is ticked by the game, not by a tile.</b> Its rows say what the player does on a
 * boat, and a boat's deck is no tile of the map, so {@link #done} asks the game instead: <i>Board</i>
 * is done when the player stands on a deck; <i>Sail to …</i> when, still aboard, they are within a
 * mooring's reach of where it docks; <i>Dock at …</i> when they have stepped ashore at its end, or
 * when one of their boats now reads moored at that port where it did not before they boarded, which
 * is what settles a voyage whose landing is on another floor. A later row of a voyage that is done
 * passes the rows before it, so a deck the client would not report never strands the list. The
 * game's own last-dock varbit is deliberately unused until a live client has shown when it changes.
 *
 * <p><b>One destination, one route.</b> The route belongs to the place it was sent with, told apart
 * by the same fields {@link DestinationState} tells places apart by. A new place starts over; the
 * same place gaining its route a moment after it arrived keeps the step it is on; and once the
 * player has rerouted, the route that keeps riding in with the destination is the old answer and is
 * not put back.
 *
 * <p>Read and written from three threads — the game tick, the sidebar and the Reroute reply — so
 * every field is behind this object's lock and the sidebar reads a copy.
 */
@Singleton
public class DestinationRoute
{
	/** How close counts as there: a teleport's landing scatters by a tile or two. */
	static final int REACHED = 4;
	/**
	 * How close a boat counts as arrived at a mooring: the map's own match radius between a mooring's
	 * name and its pin, which no two moorings come within sixty tiles of.
	 */
	static final int MOORED = 15;

	/**
	 * What the game says about the player's boats this tick: whether they stand on a deck, and each
	 * boat's port by slot from {@link BoatReader}, or null where it cannot say. Read here whether or
	 * not the boats are shared, to follow a voyage; nothing of it is sent.
	 */
	static final class Sailing
	{
		static final Sailing ASHORE_UNKNOWN = new Sailing(false, null);

		final boolean aboard;
		@Nullable
		final Map<Integer, String> moored;

		Sailing(boolean aboard, @Nullable Map<Integer, String> moored)
		{
			this.aboard = aboard;
			this.moored = moored;
		}

		/** The ports the boats read, by slot; a boat at no dock or with no name is left out. */
		static Sailing of(boolean aboard, @Nullable List<CompanionSnapshot.Boat> boats)
		{
			if (boats == null)
			{
				return new Sailing(aboard, null);
			}
			Map<Integer, String> moored = new HashMap<>();
			for (CompanionSnapshot.Boat boat : boats)
			{
				if (boat.port != null)
				{
					moored.put(boat.slot, boat.port);
				}
			}
			return new Sailing(aboard, moored);
		}
	}

	private String key;
	@Nullable
	private WorldPoint target;
	@Nullable
	private RelayResponse.Route route;
	private String received;
	private boolean rerouted;
	private int chosen;
	private int step;
	/** Bumped whenever the route stops belonging to what a Reroute in flight asked about. */
	private long generation;
	/** Changes when a route or an explicit Follow/Reroute replaces the path being drawn. */
	private long revision;
	private boolean rerouting;
	/**
	 * A press of Route somewhere in the sidebar is on its way ({@link RoutePress}). It shares the
	 * one-at-a-time rule and the wait with Reroute, because the map counts both against one member.
	 */
	private boolean pressing;
	@Nullable
	private String message;
	/** Whether {@link #message} says a Reroute did not happen, rather than a notice such as Shortest Path's. */
	private boolean refused;
	private long nextRerouteMs;
	/**
	 * The player asked to follow this route: pressed Follow, or Reroute. Only then is Shortest Path
	 * drawn on, because a destination that merely arrived — a friend's included — must never replace
	 * the target the player gave it themselves.
	 */
	private boolean following;
	/** Where the boats were moored on the last tick ashore before this voyage docked; see {@link #done}. */
	@Nullable
	private Map<Integer, String> ashore;
	/** The port the chosen way boards a boat at, while no boat reads moored there. */
	@Nullable
	private String boatAway;

	/** One moment of the route, for the sidebar and the pins. Never changes after it is made. */
	static final class View
	{
		/** The destination it belongs to; see {@link #keyOf}. */
		final String key;
		/** That destination's own tile, where its own pin already stands. */
		final WorldPoint target;
		final List<RelayResponse.RouteOption> ways;
		final int chosen;
		/** The current step of the chosen way; its step count once arrived. */
		final int step;
		@Nullable
		final String origin;
		final boolean rerouted;
		final boolean rerouting;
		@Nullable
		final String message;
		/** The message says a Reroute did not happen, and is drawn in the refusal pink. */
		final boolean refused;
		final long nextRerouteMs;
		final boolean following;
		final long revision;
		/**
		 * The mooring the way boards a boat at, when the boats have been read since and none is there
		 * any more: a voyage planned from where the boat was. Reroute plans from where it is.
		 */
		@Nullable
		final String boatAway;

		View(String key, WorldPoint target, List<RelayResponse.RouteOption> ways, int chosen, int step, @Nullable String origin,
			boolean rerouted, boolean rerouting, @Nullable String message, long nextRerouteMs, boolean following, long revision)
		{
			this(key, target, ways, chosen, step, origin, rerouted, rerouting, message, false, nextRerouteMs, following, revision, null);
		}

		View(String key, WorldPoint target, List<RelayResponse.RouteOption> ways, int chosen, int step, @Nullable String origin,
			boolean rerouted, boolean rerouting, @Nullable String message, boolean refused, long nextRerouteMs, boolean following,
			long revision, @Nullable String boatAway)
		{
			this.refused = refused;
			this.boatAway = boatAway;
			this.revision = revision;
			this.following = following;
			this.key = key;
			this.target = target;
			this.ways = ways;
			this.chosen = chosen;
			this.step = step;
			this.origin = origin;
			this.rerouted = rerouted;
			this.rerouting = rerouting;
			this.message = message;
			this.nextRerouteMs = nextRerouteMs;
		}

		boolean hasRoute()
		{
			return !ways.isEmpty();
		}

		@Nullable
		RelayResponse.RouteOption way()
		{
			return ways.isEmpty() ? null : ways.get(chosen);
		}

		List<RelayResponse.RouteStep> steps()
		{
			RelayResponse.RouteOption way = way();
			return way == null ? Collections.emptyList() : way.steps;
		}

		boolean arrived()
		{
			return hasRoute() && step >= steps().size();
		}

		@Nullable
		RelayResponse.RouteStep current()
		{
			List<RelayResponse.RouteStep> steps = steps();
			return step < steps.size() ? steps.get(step) : null;
		}

		/**
		 * Where the player is headed next: the current step's end, or the end of the first step after
		 * it that names one. Null once arrived, or when nothing ahead names a tile.
		 */
		@Nullable
		WorldPoint next()
		{
			List<RelayResponse.RouteStep> steps = steps();
			for (int i = step; i < steps.size(); i++)
			{
				if (steps.get(i).at != null)
				{
					return point(steps.get(i).at);
				}
			}
			return null;
		}
	}

	/** What a Reroute press asked about, so an answer for a place since replaced is thrown away. */
	static final class Ticket
	{
		final long generation;
		final WorldPoint target;

		Ticket(long generation, WorldPoint target)
		{
			this.generation = generation;
			this.target = target;
		}
	}

	/** A place as {@link DestinationState} tells places apart: everything but its route. */
	@Nullable
	static String keyOf(@Nullable RelayResponse.Destination destination)
	{
		if (destination == null)
		{
			return null;
		}
		return destination.x + "," + destination.y + "," + destination.plane + "|" + destination.world
			+ "|" + destination.name + "|" + destination.note;
	}

	/** Bring the route in line with the destination being shown, or none. Called on the game tick. */
	synchronized void sync(@Nullable RelayResponse.Destination destination)
	{
		String incoming = keyOf(destination);
		if (!Objects.equals(incoming, key))
		{
			key = incoming;
			target = destination == null ? null : new WorldPoint(destination.x, destination.y, destination.plane);
			generation++;
			rerouted = false;
			rerouting = false;
			following = false;
			message = null;
			refused = false;
			adopt(destination == null ? null : destination.route);
			return;
		}
		if (destination != null && destination.route != null && !rerouted
			&& !signature(destination.route).equals(received))
		{
			adopt(destination.route);
		}
	}

	private void adopt(@Nullable RelayResponse.Route incoming)
	{
		revision++;
		route = incoming;
		received = incoming == null ? "" : signature(incoming);
		chosen = best(incoming);
		step = 0;
		ashore = null;
		boatAway = null;
	}

	/**
	 * Move on if the current step is done. Called on the game tick with {@link #standing}; null — an
	 * instance whose tiles cannot be trusted, or no player — moves nothing.
	 *
	 * @return whether the current step changed
	 */
	synchronized boolean advance(@Nullable WorldPoint where)
	{
		return advance(where, Sailing.ASHORE_UNKNOWN);
	}

	/**
	 * Move on if the current step is done, asking the game about a voyage's rows.
	 *
	 * @return whether the current step changed
	 */
	synchronized boolean advance(@Nullable WorldPoint where, Sailing sailing)
	{
		if (route == null)
		{
			return false;
		}
		RelayResponse.RouteOption way = route.options.get(chosen);
		int next = where == null && !sailing.aboard ? step : next(way.steps, step, where, sailing, ashore, way.voyage);
		int dock = firstSlot(way.steps, RouteWire.DOCK);
		/* Kept only before the boat docks, and after the step is judged, so a landing compares with the boarding. */
		if (!sailing.aboard && sailing.moored != null && next <= (dock < 0 ? way.steps.size() : dock))
		{
			ashore = sailing.moored;
		}
		boatAway = boatAway(way, next, sailing);
		if (next == step)
		{
			return false;
		}
		step = next;
		return true;
	}

	/**
	 * The port a way boards the player's own boat at, when the boats read ashore are known and none
	 * of them is there, while the boarding is still ahead. Null otherwise, including for a way whose
	 * voyage boards no known boat, which has no {@code board} row.
	 */
	@Nullable
	static String boatAway(RelayResponse.RouteOption way, int step, Sailing sailing)
	{
		int board = firstSlot(way.steps, RouteWire.BOARD);
		if (board < 0 || step > board || way.voyage == null || sailing.aboard || sailing.moored == null)
		{
			return null;
		}
		return sailing.moored.containsValue(way.voyage.from) ? null : way.voyage.from;
	}

	private static int firstSlot(List<RelayResponse.RouteStep> steps, String slot)
	{
		for (int i = 0; i < steps.size(); i++)
		{
			if (slot.equals(steps.get(i).slot))
			{
				return i;
			}
		}
		return -1;
	}

	/** Follow this route, or stop. The player's own press; see {@link #following}. */
	synchronized void follow(boolean on)
	{
		following = on && route != null;
		if (following)
		{
			revision++;
		}
		message = null;
		refused = false;
	}

	/** Stop following because something else took Shortest Path over, and say so. */
	synchronized void stopFollowing(String why)
	{
		if (following)
		{
			following = false;
			message = why;
			refused = false;
		}
	}

	/** Show another of the ways, from its first step. The player's own press. */
	synchronized void choose(int way)
	{
		if (route != null && way >= 0 && way < route.options.size() && way != chosen)
		{
			chosen = way;
			step = 0;
			boatAway = null;
		}
	}

	/**
	 * Start a Reroute, or say why not. Only one at a time, and none before the map's own wait is over.
	 *
	 * @return what to ask about, or null when nothing is to be sent
	 */
	@Nullable
	synchronized Ticket beginReroute(long now)
	{
		if (route == null || target == null || rerouting || pressing)
		{
			return null;
		}
		if (now < nextRerouteMs)
		{
			message = "The map asked to wait. Try again in " + ((nextRerouteMs - now + 999) / 1000) + "s.";
			refused = true;
			return null;
		}
		rerouting = true;
		message = null;
		refused = false;
		return new Ticket(generation, target);
	}

	/** A press that never reached the network, with the sentence saying why. */
	synchronized void refuse(String why)
	{
		message = why;
		refused = true;
	}

	/**
	 * The answer to a Reroute: the new ways, or null with a sentence. The wait the map asked for is
	 * kept even when the destination has changed since, because it is the member's and not the place's.
	 */
	synchronized void finishReroute(Ticket ticket, @Nullable RelayResponse.Route answer, @Nullable String why, long nextAllowedMs)
	{
		nextRerouteMs = Math.max(nextRerouteMs, nextAllowedMs);
		if (ticket.generation != generation)
		{
			return;
		}
		rerouting = false;
		message = why;
		refused = why != null;
		if (answer != null)
		{
			revision++;
			route = answer;
			rerouted = true;
			/* Reroute is a press of the player's own, as Follow is. */
			following = true;
			chosen = best(answer);
			step = 0;
			ashore = null;
			boatAway = null;
		}
	}

	/**
	 * Start a press of Route, or say why not: the same rule as {@link #beginReroute}, one question at
	 * a time and none before the map's own wait is over.
	 *
	 * @return null when the press may go, else the sentence to show beside the button
	 */
	@Nullable
	synchronized String beginPress(long now)
	{
		if (pressing || rerouting)
		{
			return RoutePress.ONE_AT_A_TIME;
		}
		if (now < nextRerouteMs)
		{
			return "The map asked to wait. Try again in " + ((nextRerouteMs - now + 999) / 1000) + "s.";
		}
		pressing = true;
		return null;
	}

	/** A press's answer landed, or failed; the wait the map asked for is the member's either way. */
	synchronized void finishPress(long nextAllowedMs)
	{
		pressing = false;
		nextRerouteMs = Math.max(nextRerouteMs, nextAllowedMs);
	}

	/** Whether a press of Route would be sent now, for enabling its button. */
	synchronized boolean pressReady(long now)
	{
		return !pressing && !rerouting && now >= nextRerouteMs;
	}

	/**
	 * Show a route the player asked for with a press here, as the destination's own, and follow it.
	 *
	 * <p>The destination is the one {@link DestinationState#selectLocal} now holds, so the next tick's
	 * {@link #sync} finds the same place with the same route and keeps both, the step it is on and
	 * the following. Following is on because the press was the player's own, as Follow and Reroute
	 * are; a destination that merely arrives still never draws in Shortest Path.
	 */
	synchronized void adoptPressed(RelayResponse.Destination destination)
	{
		key = keyOf(destination);
		target = new WorldPoint(destination.x, destination.y, destination.plane);
		generation++;
		rerouted = false;
		rerouting = false;
		message = null;
		refused = false;
		adopt(destination.route);
		following = route != null;
	}

	/** Forget everything: a group left, a lifetime ended. */
	synchronized void clear()
	{
		key = null;
		target = null;
		route = null;
		received = null;
		rerouted = false;
		rerouting = false;
		following = false;
		message = null;
		refused = false;
		chosen = 0;
		step = 0;
		ashore = null;
		boatAway = null;
		generation++;
	}

	synchronized boolean hasRoute()
	{
		return route != null;
	}

	/** The route as it stands, or null when the destination being shown has none. */
	@Nullable
	synchronized View view()
	{
		if (key == null)
		{
			return null;
		}
		List<RelayResponse.RouteOption> ways = route == null
			? Collections.<RelayResponse.RouteOption>emptyList()
			: Collections.unmodifiableList(route.options);
		return new View(key, target, ways, chosen, step, route == null ? null : route.origin, rerouted, rerouting,
			message, refused, nextRerouteMs, following, revision, boatAway);
	}

	/**
	 * The step after {@code current} that the player standing at {@code where} has made current.
	 * Static and plain so the rule is pinned by a test without a client.
	 */
	static int next(List<RelayResponse.RouteStep> steps, int current, WorldPoint where)
	{
		return next(steps, current, where, Sailing.ASHORE_UNKNOWN, null, null);
	}

	/**
	 * The same rule with a voyage's evidence: a row of a voyage is done by {@link #done}, and one not
	 * yet done is passed by a later row of the same voyage that is.
	 *
	 * @param where where the player stands, or null where that cannot be known
	 * @param ashore the boats' ports read before boarding, or null
	 */
	static int next(List<RelayResponse.RouteStep> steps, int current, @Nullable WorldPoint where, Sailing sailing,
		@Nullable Map<Integer, String> ashore, @Nullable RelayResponse.Voyage voyage)
	{
		if (current >= steps.size())
		{
			return current;
		}
		int last = -1;
		for (int i = steps.size() - 1; i >= current; i--)
		{
			if (steps.get(i).at != null || RouteWire.voyageSlot(steps.get(i).slot))
			{
				last = i;
				break;
			}
		}
		if (last < 0)
		{
			return current;
		}
		if (done(steps.get(last), where, sailing, ashore, voyage))
		{
			return last + 1;
		}
		for (int i = current; i < last; i++)
		{
			RelayResponse.RouteStep step = steps.get(i);
			boolean voyageRow = RouteWire.voyageSlot(step.slot);
			if (step.at == null && !voyageRow)
			{
				continue;
			}
			if (done(step, where, sailing, ashore, voyage))
			{
				return i + 1;
			}
			for (int j = i + 1; voyageRow && j < last && RouteWire.voyageSlot(steps.get(j).slot); j++)
			{
				if (done(steps.get(j), where, sailing, ashore, voyage))
				{
					return j + 1;
				}
			}
			return current;
		}
		return current;
	}

	/**
	 * Whether one row is done: by its tile for every row but a voyage's, which the game says; see the
	 * class comment. Static and plain so each rule is pinned by a test without a client.
	 */
	static boolean done(RelayResponse.RouteStep step, @Nullable WorldPoint where, Sailing sailing,
		@Nullable Map<Integer, String> ashore, @Nullable RelayResponse.Voyage voyage)
	{
		if (RouteWire.BOARD.equals(step.slot))
		{
			return sailing.aboard;
		}
		if (RouteWire.SAIL.equals(step.slot))
		{
			return sailing.aboard && where != null && step.at != null
				&& Math.abs(where.getX() - step.at.x) <= MOORED && Math.abs(where.getY() - step.at.y) <= MOORED;
		}
		if (RouteWire.DOCK.equals(step.slot))
		{
			if (sailing.aboard)
			{
				return false;
			}
			if (where != null && step.at != null && reached(step.at, where))
			{
				return true;
			}
			return voyage != null && voyage.to != null && mooredAnew(voyage.to, ashore, sailing.moored);
		}
		return where != null && step.at != null && reached(step.at, where);
	}

	/** A boat now reads moored at {@code port} that did not before the voyage boarded. */
	static boolean mooredAnew(String port, @Nullable Map<Integer, String> before, @Nullable Map<Integer, String> now)
	{
		if (before == null || now == null)
		{
			return false;
		}
		for (Map.Entry<Integer, String> boat : now.entrySet())
		{
			if (port.equals(boat.getValue()) && !port.equals(before.get(boat.getKey())))
			{
				return true;
			}
		}
		return false;
	}

	/** Within {@link #REACHED} tiles each way, on the step's own floor; no floor is the ground floor. */
	static boolean reached(RelayResponse.RouteTile at, WorldPoint where)
	{
		int plane = at.plane == null ? 0 : at.plane;
		return where.getPlane() == plane
			&& Math.abs(where.getX() - at.x) <= REACHED
			&& Math.abs(where.getY() - at.y) <= REACHED;
	}

	/** A step's end as a tile of the world, on its floor. */
	static WorldPoint point(RelayResponse.RouteTile at)
	{
		return new WorldPoint(at.x, at.y, at.plane == null ? 0 : at.plane);
	}

	/**
	 * Where the player stands, for comparing with the map's tiles, or null where that cannot be known.
	 *
	 * <p>Inside an instance the client's tile is a copy's; {@link WorldTiles} maps it back to the
	 * place it was copied from. That is the real place when the instance is one place copied whole
	 * — a boss lair, an arena — and a room in a store of rooms when it was put together from them, as
	 * a raid is. There, two tiles side by side can be forty apart, so nothing is moved on from it.
	 * Client thread only.
	 */
	@Nullable
	static WorldPoint standing(Client client)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		if (WorldTiles.isInstance(client) && !WorldTiles.COPY.equals(WorldTiles.layout(client)))
		{
			return null;
		}
		return WorldTiles.of(client, player);
	}

	private static int best(@Nullable RelayResponse.Route route)
	{
		if (route == null)
		{
			return 0;
		}
		for (int i = 0; i < route.options.size(); i++)
		{
			if (route.options.get(i).best)
			{
				return i;
			}
		}
		return 0;
	}

	/** Enough of a route to tell a different answer from the same one arriving again. */
	private static String signature(RelayResponse.Route route)
	{
		StringBuilder sb = new StringBuilder();
		for (RelayResponse.RouteOption option : route.options)
		{
			sb.append(option.id).append('|').append(option.label).append('|');
			for (RelayResponse.RouteStep step : option.steps)
			{
				sb.append(step.title).append('|');
				if (step.at != null)
				{
					sb.append(step.at.x).append(',').append(step.at.y).append(',').append(step.at.plane).append('|');
				}
			}
			sb.append(';');
		}
		return sb.toString();
	}
}
