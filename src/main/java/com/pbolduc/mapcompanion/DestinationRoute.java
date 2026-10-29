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
import java.util.List;
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
	private boolean rerouting;
	@Nullable
	private String message;
	private long nextRerouteMs;

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
		final long nextRerouteMs;

		View(String key, WorldPoint target, List<RelayResponse.RouteOption> ways, int chosen, int step, @Nullable String origin,
			boolean rerouted, boolean rerouting, @Nullable String message, long nextRerouteMs)
		{
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
			message = null;
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
		route = incoming;
		received = incoming == null ? "" : signature(incoming);
		chosen = best(incoming);
		step = 0;
	}

	/**
	 * Move on if the current step is done. Called on the game tick with {@link #standing}; null — an
	 * instance whose tiles cannot be trusted, or no player — moves nothing.
	 *
	 * @return whether the current step changed
	 */
	synchronized boolean advance(@Nullable WorldPoint where)
	{
		if (route == null || where == null)
		{
			return false;
		}
		int next = next(route.options.get(chosen).steps, step, where);
		if (next == step)
		{
			return false;
		}
		step = next;
		return true;
	}

	/** Show another of the ways, from its first step. The player's own press. */
	synchronized void choose(int way)
	{
		if (route != null && way >= 0 && way < route.options.size() && way != chosen)
		{
			chosen = way;
			step = 0;
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
		if (route == null || target == null || rerouting)
		{
			return null;
		}
		if (now < nextRerouteMs)
		{
			message = "The map asked to wait. Try again in " + ((nextRerouteMs - now + 999) / 1000) + "s.";
			return null;
		}
		rerouting = true;
		message = null;
		return new Ticket(generation, target);
	}

	/** A press that never reached the network, with the sentence saying why. */
	synchronized void refuse(String why)
	{
		message = why;
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
		if (answer != null)
		{
			route = answer;
			rerouted = true;
			chosen = best(answer);
			step = 0;
		}
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
		message = null;
		chosen = 0;
		step = 0;
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
		return new View(key, target, ways, chosen, step, route == null ? null : route.origin, rerouted, rerouting, message, nextRerouteMs);
	}

	/**
	 * The step after {@code current} that the player standing at {@code where} has made current.
	 * Static and plain so the rule is pinned by a test without a client.
	 */
	static int next(List<RelayResponse.RouteStep> steps, int current, WorldPoint where)
	{
		if (current >= steps.size())
		{
			return current;
		}
		int last = -1;
		for (int i = steps.size() - 1; i >= current; i--)
		{
			if (steps.get(i).at != null)
			{
				last = i;
				break;
			}
		}
		if (last < 0)
		{
			return current;
		}
		if (reached(steps.get(last).at, where))
		{
			return last + 1;
		}
		for (int i = current; i < last; i++)
		{
			RelayResponse.RouteTile at = steps.get(i).at;
			if (at != null)
			{
				return reached(at, where) ? i + 1 : current;
			}
		}
		return current;
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
