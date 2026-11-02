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
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * The step being walked, in a small box over the game, so a followed route can be walked with the
 * sidebar closed: where it goes, the step the sidebar marks, and the step's own detail.
 *
 * <p>Only while the route is followed, which the player chose, and gone once arrived; the scene's
 * tile, the minimap's dot and the arrow already say where the step ends. Nothing inside an
 * instance: no directions inside a boss fight. The box is RuneLite's own, so it can be moved like
 * any other, and it draws only what the sidebar already holds; nothing leaves this computer.
 *
 * <p>The box is the one place the route is read with the sidebar shut, so it names the destination
 * in the tile label's words, whole, and with the world the label gives while the player is on
 * another. A destination's name was typed by somebody in the group, and an overlay draws colour
 * tags, so every line has its tags taken out first.
 *
 * <p>A step keeps the number its pin carries on the world map. A run's leg has no step pins, and its
 * stop's number heads the box as it heads the stop's pin, so on a leg the step is its words alone:
 * a second number under the first would point at nothing on the map.
 *
 * <p>A teleport step has the game's picture of what to click before its words, as the sidebar's
 * row does: the item's sprite or the spell's icon, by the id the map sent, from this client's own
 * cache through {@link GameSprites}, drawn bare. The box is drawn every frame and cannot wait for
 * it, so each picture is asked for once and drawn from the frame it arrives; until then its place
 * is kept empty, so the words never move. A walk, a ride, or a step the map names no id for stays
 * words alone, in a box no wider than before.
 */
class RouteStepOverlay extends OverlayPanel
{
	/** Wide enough for most names and steps on one line; longer ones wrap. */
	private static final int WIDTH = 180;
	/** A step's picture, at the sidebar's size, and the gap RuneLite's own XP box leaves beside its icon. */
	private static final int PICTURE = RouteStepsView.PICTURE;
	private static final int PICTURE_GAP = 4;
	private static final BufferedImage NO_PICTURE_YET = new BufferedImage(PICTURE, PICTURE, BufferedImage.TYPE_INT_ARGB);

	/**
	 * How much of a name the box keeps. It wraps, so it keeps a whole place name, a clue step's
	 * sentence included, where the tile's one line cuts it short; a name somebody typed is still cut
	 * here before it can fill the screen.
	 */
	private static final int NAME_MAX = 120;

	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationState destinations;
	private final DestinationRoute route;
	private final FarmRunRoute run;
	private final GameSprites sprites;
	/**
	 * Each picture asked for, by its id, holding null until it is drawn. Client thread only: the box
	 * is drawn there, and both caches answer there.
	 */
	private final Map<String, BufferedImage> pictures = new HashMap<>();

	@Inject
	RouteStepOverlay(Client client, MapCompanionConfig config, DestinationState destinations, DestinationRoute route,
		FarmRunRoute run, GameSprites sprites)
	{
		this.client = client;
		this.config = config;
		this.destinations = destinations;
		this.route = route;
		this.run = run;
		this.sprites = sprites;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStepOnScreen())
		{
			return null;
		}
		WorldView view = client.getTopLevelWorldView();
		if (view != null && view.isInstance())
		{
			return null;
		}
		RelayResponse.Destination destination = destinations.getDestination();
		Box box = box(destination);
		if (box == null)
		{
			return null;
		}
		boolean pictured = box.itemId != null || box.spriteId != null;
		/* Wider by the picture, so the words keep the width they have without one. */
		panelComponent.setPreferredSize(new Dimension(WIDTH + (pictured ? PICTURE + PICTURE_GAP : 0), 0));
		/*
		 * A line, which wraps: RuneLite's title component centres a single line, and a long name
		 * overruns it on both sides. Bold keeps it the heading, where a farm run's "2. Catherby" over
		 * "Use Catherby tablet" in one weight reads as a list.
		 */
		panelComponent.getChildren().add(LineComponent.builder()
			.left(title(box, destination, client.getWorld()))
			.leftFont(FontManager.getRunescapeBoldFont())
			.build());
		LineComponent step = LineComponent.builder().left(box.step).build();
		LineComponent detail = box.detail == null ? null : LineComponent.builder().left(box.detail).leftColor(Color.LIGHT_GRAY).build();
		if (pictured)
		{
			/* The picture before the words, and the detail under them past it, as the sidebar's row has them. */
			panelComponent.getChildren().add(SplitComponent.builder()
				.orientation(ComponentOrientation.HORIZONTAL)
				.gap(new Point(PICTURE_GAP, 0))
				.first(new ImageComponent(picture(box)))
				.second(detail == null ? step : SplitComponent.builder().first(step).second(detail).build())
				.build());
		}
		else
		{
			panelComponent.getChildren().add(step);
			if (detail != null)
			{
				panelComponent.getChildren().add(detail);
			}
		}
		return super.render(graphics);
	}

	/** The step's picture once the client has drawn it, centred in its square, or an empty square until then. */
	private BufferedImage picture(Box box)
	{
		String key = box.itemId != null ? "item " + box.itemId : "sprite " + box.spriteId;
		if (!pictures.containsKey(key))
		{
			pictures.put(key, null);
			Consumer<BufferedImage> drawn = picture -> pictures.put(key, ImageUtil.resizeCanvas(picture, PICTURE, PICTURE));
			if (box.itemId != null)
			{
				sprites.item(box.itemId, PICTURE, drawn);
			}
			else
			{
				sprites.sprite(box.spriteId, PICTURE, drawn);
			}
		}
		BufferedImage picture = pictures.get(key);
		return picture == null ? NO_PICTURE_YET : picture;
	}

	/** What the box says. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Box
	{
		final String title;
		final String step;
		@Nullable
		final String detail;
		/** The step's picture by the id the map sent, the item clicked or the spell's icon; both null for words alone. */
		@Nullable
		final Integer itemId;
		@Nullable
		final Integer spriteId;
	}

	/** The box as {@link #render} draws it: the route followed now, a run's leg read as one. */
	@Nullable
	Box box(@Nullable RelayResponse.Destination destination)
	{
		DestinationRoute.View view = route.view();
		return box(view, destination == null ? null : destination.name, run.ownsDestinationRoute(view));
	}

	/** The box for a destination's route as it stands, or null when nothing is being followed to a step. */
	@Nullable
	static Box box(@Nullable DestinationRoute.View view, @Nullable String name)
	{
		return box(view, name, false);
	}

	/**
	 * The box for a route as it stands, or null when nothing is being followed to a step.
	 *
	 * @param leg whether the route is a run's leg, whose steps have no pins to share a number with
	 */
	@Nullable
	static Box box(@Nullable DestinationRoute.View view, @Nullable String name, boolean leg)
	{
		if (view == null || !view.following || !view.hasRoute() || view.arrived())
		{
			return null;
		}
		List<RelayResponse.RouteStep> steps = view.steps();
		if (view.step < 0 || view.step >= steps.size())
		{
			return null;
		}
		RelayResponse.RouteStep step = steps.get(view.step);
		String named = DestinationSceneOverlay.name(name, NAME_MAX);
		String title = named == null ? "Route" : named;
		String detail = step.detail == null || Text.removeTags(step.detail).trim().isEmpty() ? null : Text.removeTags(step.detail).trim();
		String line = leg ? step.title : RouteStepsView.stepLine(view.step, step);
		return new Box(title, Text.removeTags(line), detail, step.itemId, step.spriteId);
	}

	/** The box's first line: the destination's name, and its world while the player is on another. */
	static String title(Box box, @Nullable RelayResponse.Destination destination, int currentWorld)
	{
		return destination == null ? box.title : box.title + DestinationSceneOverlay.elsewhere(destination, currentWorld);
	}
}
