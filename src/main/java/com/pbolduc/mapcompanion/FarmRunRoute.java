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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.callback.ClientThread;

/**
 * Give the run's current leg to the existing destination follower and Shortest Path bridge.
 *
 * <p>No second path planner or movement system: a leg becomes an ordinary local destination.
 * Another destination chosen by the player pauses this follower rather than having its target
 * overwritten on every tick. End removes only the destination this follower still owns.
 */
@Singleton
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
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
	/** The destination whose arrival paused the run, so the card can name it; see {@link #pausedFor}. */
	@Nullable
	private volatile RelayResponse.Destination detour;

	void follow(boolean on)
	{
		clientThread.invokeLater(() ->
		{
			synchronized (destinations)
			{
				if (on)
				{
					selectedKey = null;    // A deliberate Follow may replace another local route.
					detour = null;
				}
				state.follow(on);
				if (!on && ownsShown())
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
			if (ownsShown())
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
			dropOwned();
			selectedKey = null;
			selectedRun = null;
			detour = null;
			return;
		}
		if (!view.following)
		{
			if (ownsShown())
			{
				route.follow(false);
			}
			return;
		}
		if (!view.run.id.equals(selectedRun))
		{
			selectedKey = null;
		}
		if (selectedKey != null && !ownsShown())
		{
			detour = destinations.getDestination();
			state.follow(false);
			selectedKey = null;
			return;
		}
		RelayResponse.FarmStop next = view.next();
		if (next == null)
		{
			dropOwned();
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

	/** Whether the destination shown now is the run's own stop. Caller holds the destinations lock. */
	private boolean ownsShown()
	{
		return owns(destinations.getDestination());
	}

	/** The run's stop goes from the map and its route with it; another destination stays. */
	private void dropOwned()
	{
		if (ownsShown())
		{
			destinations.dismiss();
			route.clear();
		}
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

	/**
	 * The name of the destination that paused the run by taking its place — a search pick, Route here,
	 * a friend's — while the run is still paused and that destination is still the one shown. Null
	 * once it is dismissed or replaced, or the run is followed again: the card then says only that the
	 * run is paused. Any thread.
	 */
	@Nullable
	String pausedFor()
	{
		RelayResponse.Destination other = detour;
		FarmRunState.View view = state.view();
		if (other == null || view == null || view.following)
		{
			return null;
		}
		String shown = DestinationRoute.keyOf(destinations.getDestination());
		return shown != null && shown.equals(DestinationRoute.keyOf(other)) ? other.name : null;
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
			// A voyage's cost mixes its walks with the sail in run tiles, and its Sail step already says
			// the sailed distance, so a second, larger figure on the heading would read as a contradiction.
			way.tiles = stop.voyage == null ? stop.costTiles : null;
			way.steps = stop.steps;
			// A port-task leg's voyage: drawn on the world map and ticked by the deck, as a destination's is.
			way.voyage = stop.voyage;
			destination.route = new RelayResponse.Route();
			// Legs after the first start at the preceding stop, not at a shared/requested position.
			destination.route.options = Collections.singletonList(way);
		}
		return destination;
	}
}
