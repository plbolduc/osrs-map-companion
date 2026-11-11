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

import java.awt.Rectangle;
import java.util.function.ToDoubleFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;

/**
 * Which way a place lies from where the world map holds its pin, so the pin's head can point there.
 *
 * <p>RuneLite tells a point that it has been moved to the map's edge and not where, so this works
 * the place out again from what the overlay itself reads: the tile the map is centred on, its
 * pixels to a tile, and the map's own widget, drawn in by half a head as RuneLite draws it in by
 * half a picture. It reads those and nothing else, and changes nothing.
 */
@Singleton
class MapEdge implements ToDoubleFunction<WorldPoint>
{
	private final Client client;

	@Inject
	MapEdge(Client client)
	{
		this.client = client;
	}

	/**
	 * The way to {@code place} in radians clockwise from east, as the screen counts them, or NaN
	 * while the map is closed or the head already sits on the place.
	 */
	@Override
	public double applyAsDouble(WorldPoint place)
	{
		WorldMap map = client.getWorldMap();
		Widget view = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		Point centre = map == null ? null : map.getWorldMapPosition();
		if (centre == null || view == null)
		{
			return Double.NaN;
		}
		Rectangle bounds = view.getBounds();
		float zoom = map.getWorldMapZoom();
		/* North is up the screen, which is down in canvas pixels. */
		return way((place.getX() - centre.getX()) * zoom, (centre.getY() - place.getY()) * zoom,
			(bounds.width - MapPin.HEAD) / 2.0, (bounds.height - MapPin.HEAD) / 2.0);
	}

	/**
	 * From a head held inside a view of these half-sizes to a place {@code dx, dy} pixels from the
	 * view's middle. A point off one side is moved along that axis alone, so the way is straight
	 * out through the edge everywhere but in a corner.
	 */
	static double way(double dx, double dy, double halfWidth, double halfHeight)
	{
		double x = dx - Math.max(-halfWidth, Math.min(halfWidth, dx));
		double y = dy - Math.max(-halfHeight, Math.min(halfHeight, dy));
		return x == 0 && y == 0 ? Double.NaN : Math.atan2(y, x);
	}
}
