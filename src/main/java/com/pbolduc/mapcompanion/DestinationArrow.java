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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * The game's own hint arrow, pointed at the destination once the game has loaded it.
 *
 * <p>{@link DestinationSceneOverlay} paints the tile and a minimap dot, and draws nothing past the
 * minimap's edge, because an arrow computed from a few pixels of projection is wrong by a few
 * degrees. The game's arrow is not computed here at all: the client draws it, over the tile and
 * on the minimap's rim, exactly as it does for a quest. RuneLite's clue plugin uses it the same
 * way and under the same condition — only for a tile in the loaded scene, which is the only kind
 * the arrow can point at.
 *
 * <p><b>Somebody else's arrow is never touched.</b> The game sets these for quests and other
 * plugins set them too, and there is one. So this sets the arrow only when there is none, clears
 * it only while it is still the one this set, and forgets it the moment anything else replaces it.
 * Client thread only: the arrow is game state.
 */
@Singleton
public class DestinationArrow
{
	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationState state;

	/** The point this class set and believes is still showing, or null. */
	@Nullable
	private WorldPoint ours;

	@Inject
	DestinationArrow(Client client, MapCompanionConfig config, DestinationState state)
	{
		this.client = client;
		this.config = config;
		this.state = state;
	}

	/** Bring the arrow in line with the destination. Called on the game tick. */
	void sync()
	{
		if (ours != null && (!client.hasHintArrow() || !ours.equals(client.getHintArrowPoint())))
		{
			/** Cleared or replaced by the game or another plugin; it is theirs now. */
			ours = null;
		}

		WorldPoint wanted = wanted();
		if (wanted == null)
		{
			clear();
			return;
		}
		if (wanted.equals(ours) || (ours == null && client.hasHintArrow()))
		{
			return;
		}
		client.setHintArrow(wanted);
		ours = wanted;
	}

	/** Take down the arrow if it is still the one this set. */
	void clear()
	{
		if (ours != null && client.hasHintArrow() && ours.equals(client.getHintArrowPoint()))
		{
			client.clearHintArrow();
		}
		ours = null;
	}

	@Nullable
	private WorldPoint wanted()
	{
		if (!config.destinationHintArrow() || (!config.showDestination() && !state.hasLocalDestination()))
		{
			return null;
		}
		RelayResponse.Destination destination = state.getDestination();
		if (destination == null)
		{
			return null;
		}
		WorldView view = client.getTopLevelWorldView();
		if (view == null || view.getPlane() != destination.plane
			|| !WorldPoint.isInScene(view, destination.x, destination.y))
		{
			return null;
		}
		return new WorldPoint(destination.x, destination.y, destination.plane);
	}
}
