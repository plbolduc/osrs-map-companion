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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * The destination, drawn where the player is actually looking.
 *
 * <p>The world-map point is only visible with the world map open. When the target is in the
 * loaded scene this paints the tile, and {@link DestinationMinimapOverlay} a minimap dot, so you
 * can walk to it without opening the map. They draw. That is all they do — no menu entry, no
 * click handling.
 *
 * <p>Off the minimap, nothing. An arrow computed from a few pixels of projection is wrong by
 * a few degrees, which is worse than none, and RuneLite's own clue plugin makes the same
 * choice.
 */
public class DestinationSceneOverlay extends Overlay
{
	static final Color MARKER = new Color(245, 158, 11);

	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationState state;

	@Inject
	private DestinationSceneOverlay(Client client, MapCompanionConfig config, DestinationState state)
	{
		this.client = client;
		this.config = config;
		this.state = state;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(Overlay.PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RelayResponse.Destination destination = shown(config, state);
		if (destination == null)
		{
			return null;
		}
		try
		{
			String label = label(destination);
			for (LocalPoint local : localPoints(client, destination))
			{
				Polygon poly = Perspective.getCanvasTilePoly(client, local);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(graphics, poly, MARKER);
				}
				Point text = Perspective.getCanvasTextLocation(client, graphics, local, label, 0);
				if (text != null)
				{
					OverlayUtil.renderTextLocation(graphics, text, label, Color.WHITE);
				}
			}
		}
		catch (RuntimeException | LinkageError e)
		{
			/** Draw nothing. An overlay that throws is worse than one that is quiet. */
			return null;
		}
		return null;
	}

	/** The destination to draw, or null when there is none or it is not to be shown. */
	@Nullable
	static RelayResponse.Destination shown(MapCompanionConfig config, DestinationState state)
	{
		if (!config.showDestination() && !state.hasLocalDestination())
		{
			return null;
		}
		return state.getDestination();
	}

	/**
	 * The name as drawn in the game scene, where RuneLite reads {@code <col=…>} tags. Any member of the
	 * group can set the destination, so its name is stripped of tags and control characters and cut
	 * to a label's length before it can colour or crowd a friend's screen.
	 */
	static String label(RelayResponse.Destination destination)
	{
		String name = destination.name == null ? "" : Text.removeTags(destination.name)
			.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
		if (name.isEmpty())
		{
			name = "Destination";
		}
		else if (name.length() > 40)
		{
			name = name.substring(0, 39) + "…";
		}
		return destination.world == null ? name : name + " (W" + destination.world + ")";
	}

	/**
	 * Where the destination is in the loaded scene, on the floor being looked at: none when it is
	 * elsewhere, and more than one inside an instance that copies its chunk more than once.
	 *
	 * <p>The top-level world view: the destination is a tile of the world, not of a boat's deck,
	 * and it is drawn on the scene the world is loaded into. Read through {@code WorldView} rather
	 * than the client's deprecated plane and instance helpers, which assume there is only one view
	 * and are on their way out of the API.
	 */
	static List<LocalPoint> localPoints(Client client, RelayResponse.Destination destination)
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return Collections.emptyList();
		}
		WorldPoint target = new WorldPoint(destination.x, destination.y, destination.plane);
		Collection<WorldPoint> locals = WorldPoint.toLocalInstance(view, target);
		List<LocalPoint> out = new ArrayList<>(locals.size());
		for (WorldPoint wp : locals)
		{
			if (wp.getPlane() != view.getPlane())
			{
				continue;
			}
			LocalPoint local = LocalPoint.fromWorld(view, wp);
			if (local != null && view.contains(local))
			{
				out.add(local);
			}
		}
		return out;
	}
}
