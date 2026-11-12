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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import static com.pbolduc.mapcompanion.RoutePress.Said;

/**
 * Route here, on a right-click of the game's own world map: the question Route asks beside a search
 * result, for the spot under the pointer, so the way somewhere needs no typing.
 *
 * <p>The entry goes below Cancel, as Shortest Path's Set target does. A left-click takes the top
 * entry, and over empty map that would otherwise be this one, so a click meant to drag the map would
 * plan a route. It is offered only with a group, because a route is a member's question, and only
 * while the pointer is over the map itself.
 *
 * <p>The spot is worked out from where the menu opened rather than where the pointer is when the
 * entry is chosen, because the menu sits between the two. The world map draws one floor, so the spot
 * is on floor 0.
 *
 * <p>What it sends is what Route back sends: that tile and the one the player stands on, once per
 * press ({@link RoutePress}). The press has no row in the sidebar to say a refusal beside, so a
 * refusal is said once in the chat box; a route that comes back shows itself as the destination, its
 * arrow and its pins. Until then the spot has a faded pin ({@link DestinationMarker#planning}), so the
 * map the press was made on shows that it took. Client thread throughout.
 */
@Singleton
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
class WorldMapRouteHere
{
	static final String OPTION = "Route here";
	/** The press's source, which {@link RoutePress#said} answers by. */
	static final String SOURCE = "worldmap";
	/** The route's name when the map's answer names no landmark near the spot. */
	static final String NAME = "Picked on the world map";

	private final Client client;
	private final MapCompanionConfig config;
	private final WorldMapOverlay worldMap;
	private final RoutePress routePress;
	private final Pairing pairing;
	private final ChatMessageManager chat;
	private final DestinationMarker marker;
	/** Where the last menu opened, in canvas pixels. */
	@Nullable
	private Point opened;
	/** Whether a press is waiting for its answer, and what this source said before it. */
	private boolean awaiting;
	@Nullable
	private Said before;

	void menuOpened()
	{
		opened = client.getMouseCanvasPosition();
	}

	/** Each entry the game adds while a menu is built; cheap, because that is every frame. */
	void entryAdded()
	{
		if (!config.routeHereOnWorldMap() || pairing.code().isEmpty())
		{
			return;
		}
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		Point pointer = client.getMouseCanvasPosition();
		if (map == null || map.isHidden() || pointer == null || !map.getBounds().contains(pointer.getX(), pointer.getY()))
		{
			return;
		}
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (OPTION.equals(entry.getOption()))
			{
				return;
			}
		}
		client.getMenu().createMenuEntry(0)
			.setOption(OPTION)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(chosen -> press());
	}

	private void press()
	{
		WorldPoint spot = spotAt(opened != null ? opened : client.getMouseCanvasPosition());
		if (spot == null)
		{
			return;
		}
		press(spot);
	}

	/** Ask for the way to a spot, marked on the world map until the answer is in. */
	void press(WorldPoint spot)
	{
		before = routePress.said(SOURCE);
		awaiting = true;
		marker.planning(spot);
		routePress.press(RoutePress.Target.tile(SOURCE, spot, NAME, null, null));
	}

	/** Game tick: the spot's mark goes once the answer is in, and a refused press is said once, in the chat box. */
	void tick()
	{
		if (!awaiting)
		{
			return;
		}
		Said said = routePress.said(SOURCE);
		String refusal = refusal(before, said);
		if (refusal == null && !answered(before, said))
		{
			return;
		}
		awaiting = false;
		marker.planning(null);
		if (refusal != null)
		{
			/* Said once, so a wait is the seconds left as it is said: "The map is busy. Try again in 25s." */
			chat.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT).append(OPTION + ": ")
					.append(ChatColorType.NORMAL).append(routePress.words(said, System.currentTimeMillis()))
					.build())
				.build());
		}
	}

	/** True once this source holds a finished answer that is not the one it held before the press. */
	static boolean answered(@Nullable Said before, @Nullable Said now)
	{
		return now != null && now != before && !now.planning;
	}

	/** The sentence a finished, refused press left, or null while waiting or after a route came back. */
	@Nullable
	static String refusal(@Nullable Said before, @Nullable Said now)
	{
		return answered(before, now) ? now.message : null;
	}

	@Nullable
	private WorldPoint spotAt(@Nullable Point pointer)
	{
		WorldMap map = client.getWorldMap();
		if (pointer == null || map == null || map.getWorldMapPosition() == null)
		{
			return null;
		}
		WorldPoint centre = new WorldPoint(map.getWorldMapPosition().getX(), map.getWorldMapPosition().getY(), 0);
		Point middle = worldMap.mapWorldPointToGraphicsPoint(centre);
		return middle == null ? null : tileAt(pointer.getX(), pointer.getY(), middle.getX(), middle.getY(), map.getWorldMapZoom(), centre);
	}

	/**
	 * The tile under the pointer. RuneLite draws a tile's centre at the point it maps the tile to, so
	 * the nearest tile is the rounded offset; truncating would take the tile beside it on one side.
	 * North is up the screen, which is down in canvas pixels.
	 */
	static WorldPoint tileAt(int x, int y, int middleX, int middleY, float pixelsPerTile, WorldPoint centre)
	{
		int dx = Math.round((x - middleX) / pixelsPerTile);
		int dy = Math.round((middleY - y) / pixelsPerTile);
		return centre.dx(dx).dy(dy);
	}
}
