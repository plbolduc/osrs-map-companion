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
import java.util.Collections;
import javax.annotation.Nullable;
import net.runelite.client.callback.ClientThread;

/**
 * Give the run's current leg to the existing destination follower and Shortest Path bridge.
 *
 * <p>No second path planner or movement system: a leg becomes an ordinary local destination.
 * Another destination chosen by the player pauses this follower rather than having its target
 * overwritten on every tick. End removes only the destination this follower still owns.
 */
@Singleton
final class FarmRunRoute
{
	private final FarmRunState state;
	private final DestinationState destinations;
	private final DestinationRoute route;
	private final ClientThread clientThread;
	/** The sidebar reads ownership on the EDT; the client thread alone changes it. */
	private volatile String selectedKey;
	private volatile long selectedLifetime = -1;
	private String selectedRun;
	private long selectedRevision;

	@Inject
	FarmRunRoute(FarmRunState state, DestinationState destinations, DestinationRoute route, ClientThread clientThread)
	{
		this.state = state;
		this.destinations = destinations;
		this.route = route;
		this.clientThread = clientThread;
	}

	void follow(boolean on)
	{
		clientThread.invokeLater(() ->
		{
			synchronized (destinations)
			{
				if (on)
				{
					selectedKey = null;    // A deliberate Follow may replace another local route.
				}
				state.follow(on);
				if (!on && owns(destinations.getDestination()))
				{
					route.follow(false);
				}
				syncLocked();
			}
		});
	}

	/** Client thread, before the destination route advances and the world-map pins are synced. */
	void sync()
	{
		synchronized (destinations)
		{
			syncLocked();
		}
	}

	/** A path chosen outside this plugin pauses the itinerary as well as its current leg. */
	void takenOver()
	{
		synchronized (destinations)
		{
			if (owns(destinations.getDestination()))
			{
				state.follow(false);
			}
			route.stopFollowing(ShortestPath.TAKEN_OVER);
		}
	}

	private void syncLocked()
	{
		FarmRunState.View view = state.view();
		if (view == null)
		{
			if (owns(destinations.getDestination()))
			{
				destinations.dismiss();
				route.clear();
			}
			selectedKey = null;
			selectedRun = null;
			return;
		}
		if (!view.following)
		{
			if (owns(destinations.getDestination()))
			{
				route.follow(false);
			}
			return;
		}
		if (!view.run.id.equals(selectedRun))
		{
			selectedKey = null;
		}
		if (selectedKey != null && !owns(destinations.getDestination()))
		{
			state.follow(false);
			selectedKey = null;
			return;
		}
		RelayResponse.FarmStop next = view.next();
		if (next == null)
		{
			if (owns(destinations.getDestination()))
			{
				destinations.dismiss();
				route.clear();
			}
			selectedKey = null;
			return;
		}
		RelayResponse.Destination destination = destination(view, next);
		String key = DestinationRoute.keyOf(destination);
		if (key.equals(selectedKey) && selectedRevision == view.revision)
		{
			return;
		}
		destinations.selectLocal(destination);
		route.adoptPressed(destination);
		selectedLifetime = destinations.generation();
		selectedKey = key;
		selectedRun = view.run.id;
		selectedRevision = view.revision;
	}

	boolean owns(@Nullable RelayResponse.Destination destination)
	{
		return selectedKey != null && selectedLifetime == destinations.generation()
			   && selectedKey.equals(DestinationRoute.keyOf(destination));
	}

	boolean ownsDestinationRoute(@Nullable DestinationRoute.View route)
	{
		return route != null && selectedKey != null && selectedLifetime == destinations.generation() && selectedKey.equals(route.key);
	}

	@Nullable
	FarmRunState.View view()
	{
		return state.view();
	}

	static RelayResponse.Destination destination(FarmRunState.View view, RelayResponse.FarmStop stop)
	{
		RelayResponse.Destination destination = new RelayResponse.Destination();
		destination.name = stop.n + ". " + stop.name;
		destination.note = view.run.name;
		destination.x = stop.tile.x;
		destination.y = stop.tile.y;
		destination.plane = stop.tile.plane == null ? 0 : stop.tile.plane;
		if (!stop.steps.isEmpty())
		{
			RelayResponse.RouteOption way = new RelayResponse.RouteOption();
			way.id = "farm-stop-" + stop.n;
			way.label = stop.method == null ? "Walk" : stop.method;
			way.best = true;
			way.tiles = stop.costTiles;
			way.steps = stop.steps;
			destination.route = new RelayResponse.Route();
			// Legs after the first start at the preceding stop, not at a shared/requested position.
			destination.route.options = Collections.singletonList(way);
		}
		return destination;
	}
}
