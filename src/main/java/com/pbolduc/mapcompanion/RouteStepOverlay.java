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
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

/**
 * The step being walked, in a small box over the game, so a followed route can be walked with the
 * sidebar closed: where it goes, the step as the sidebar numbers it, and the step's own detail.
 *
 * <p>Only while the route is followed, which the player chose, and gone once arrived; the scene's
 * tile, the minimap's dot and the arrow already say where the step ends. Nothing inside an
 * instance: no directions inside a boss fight. The box is RuneLite's own, so it can be moved like
 * any other, and it draws only what the sidebar already holds; nothing leaves this computer.
 *
 * <p>A destination's name was typed by somebody in the group, and an overlay draws colour tags, so
 * every line has its tags taken out first.
 */
class RouteStepOverlay extends OverlayPanel
{
	/** Wide enough for most step titles on one line; longer ones wrap. */
	private static final int WIDTH = 180;

	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationState destinations;
	private final DestinationRoute route;

	@Inject
	RouteStepOverlay(Client client, MapCompanionConfig config, DestinationState destinations, DestinationRoute route)
	{
		this.client = client;
		this.config = config;
		this.destinations = destinations;
		this.route = route;
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
		Box box = box(route.view(), destination == null ? null : destination.name);
		if (box == null)
		{
			return null;
		}
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		panelComponent.getChildren().add(TitleComponent.builder().text(box.title).color(Color.WHITE).build());
		panelComponent.getChildren().add(LineComponent.builder().left(box.step).build());
		if (box.detail != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(box.detail).leftColor(Color.LIGHT_GRAY).build());
		}
		return super.render(graphics);
	}

	/** What the box says. */
	static final class Box
	{
		final String title;
		final String step;
		@Nullable
		final String detail;

		Box(String title, String step, @Nullable String detail)
		{
			this.title = title;
			this.step = step;
			this.detail = detail;
		}
	}

	/** The box for a route as it stands, or null when nothing is being followed to a step. */
	@Nullable
	static Box box(@Nullable DestinationRoute.View view, @Nullable String name)
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
		String title = name == null || Text.removeTags(name).trim().isEmpty() ? "Route" : Text.removeTags(name).trim();
		String detail = step.detail == null || Text.removeTags(step.detail).trim().isEmpty() ? null : Text.removeTags(step.detail).trim();
		return new Box(title, Text.removeTags(RouteStepsView.stepLine(view.step, step, false)), detail);
	}
}
