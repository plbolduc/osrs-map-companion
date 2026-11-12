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

import java.awt.*;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/**
 * A voyage's line on the game's world map, from the mooring it boards at to where it docks.
 *
 * <p>The one route this plugin draws as a line. Everywhere else the numbered pins and Shortest Path
 * say the way, because Shortest Path draws a walk better than straight segments could. At sea there
 * is no Shortest Path, which has no row for sailing a player's own boat, and no landmark to steer by,
 * so the map sends the sail it traces, simplified to a few dozen tiles, and it is drawn here (the
 * map's plan, <i>A sailing leg in the game</i>). Drawn while the destination's step pins are, and in
 * the current step's amber while the voyage is the step being followed.
 *
 * <p>The tiles are the map's ground floor: the sea has no other, so a voyage whose landing is
 * upstairs still draws its whole sail. Rendered on the client thread, after the world map, clipped
 * to its view; a throw draws nothing rather than taking the frame with it.
 */
public class VoyageMapOverlay extends Overlay
{
	private static final Stroke STROKE = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
		10f, new float[]{7f, 5f}, 0f);

	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationState state;
	private final DestinationRoute route;
	private final WorldMapOverlay worldMap;

	@Inject
	private VoyageMapOverlay(Client client, MapCompanionConfig config, DestinationState state, DestinationRoute route,
		WorldMapOverlay worldMap)
	{
		this.client = client;
		this.config = config;
		this.state = state;
		this.route = route;
		this.worldMap = worldMap;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.WORLDMAP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RelayResponse.Destination destination = DestinationSceneOverlay.shown(config, state);
		DestinationRoute.View view = destination == null ? null : route.view();
		int[][] line = line(view, DestinationRoute.keyOf(destination));
		if (line == null)
		{
			return null;
		}
		try
		{
			Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
			if (map == null || map.isHidden())
			{
				return null;
			}
			Shape clip = graphics.getClip();
			graphics.setClip(map.getBounds());
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setStroke(STROKE);
			graphics.setColor(sailing(view) ? MapPin.AMBER : DestinationMarker.STEP);
			Point last = null;
			for (int[] tile : line)
			{
				Point at = worldMap.mapWorldPointToGraphicsPoint(new WorldPoint(tile[0], tile[1], 0));
				if (last != null && at != null)
				{
					graphics.drawLine(last.getX(), last.getY(), at.getX(), at.getY());
				}
				last = at;
			}
			graphics.setClip(clip);
		}
		catch (RuntimeException | LinkageError e)
		{
			/* Draw nothing. An overlay that throws is worse than one that is quiet. */
			return null;
		}
		return null;
	}

	/**
	 * The line to draw for this moment of the route, or null: none for a destination with no route,
	 * a view of another destination, a way that does not sail, or a route already arrived. Static and
	 * plain so which voyage is drawn is pinned by a test without a client.
	 *
	 * @param shownKey the key of the destination being shown, which the route must belong to
	 */
	@Nullable
	static int[][] line(@Nullable DestinationRoute.View view, @Nullable String shownKey)
	{
		if (view == null || shownKey == null || !view.key.equals(shownKey) || !view.hasRoute() || view.arrived())
		{
			return null;
		}
		RelayResponse.RouteOption way = view.way();
		return way == null || way.voyage == null ? null : way.voyage.line;
	}

	/** Whether the step being followed is one of the voyage's, so the line takes the current step's colour. */
	static boolean sailing(@Nullable DestinationRoute.View view)
	{
		RelayResponse.RouteOption way = view == null ? null : view.way();
		RelayResponse.RouteStep current = view == null ? null : view.current();
		if (way == null || current == null || way.voyage == null)
		{
			return false;
		}
		return RouteWire.voyageSlot(current.slot) || view.step == RouteStepsView.sailingRow(way);
	}
}
