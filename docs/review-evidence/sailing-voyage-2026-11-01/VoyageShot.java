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

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * The real {@link RouteStepsView} on the reply fixture's voyage, painted at 225px with RuneLite
 * 1.13.4's look and feel by {@link SidebarPolishShot#shoot}: boarding current, sailing with an unmet
 * docking gate, and the boat read at another port since the route was planned.
 * <p>Reproduce from the plugin root after {@code ./gradlew harnessJar}, as the polish shot's class
 * comment does, compiling this file beside {@code sidebar-polish-2026-10-29/*.java}, then
 * {@code java … com.pbolduc.mapcompanion.VoyageShot <out>}.
 */
public class VoyageShot
{
	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		RelayResponse.Destination destination = new Gson().fromJson(reply, RelayResponse.class).directive.destination;
		destination.route.options.get(3).voyage.gates.get(0).tier = "unmet";
		destination.route.options.get(3).voyage.gates.get(0).text = "76 Sailing";
		DestinationRoute route = new DestinationRoute();
		route.sync(destination);
		route.choose(3);
		Map<Integer, String> moored = new HashMap<>();
		moored.put(1, "Port Sarim");
		String out = args[0];
		/* Built first and painted after: a sprite lands on its label a turn of the event queue later. */
		Map<String, RouteStepsView> shots = new java.util.LinkedHashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				/* With the renderer's sprites, as the sidebar's own card has the client's: a step is drawn by its kind. */
				GameSprites sprites = com.google.inject.Guice.createInjector(Sprites::bind).getInstance(GameSprites.class);
				RouteStepsView view = new RouteStepsView(route::choose, sprites);
				route.advance(new WorldPoint(3051, 3192, 0), new DestinationRoute.Sailing(false, moored));
				view.show(route.view(), "Shortest Path draws each walk when you reach it.");
				shots.put(out + "/voyage-board.png", view);
				route.advance(new WorldPoint(3051, 3192, 0), new DestinationRoute.Sailing(true, null));
				view = new RouteStepsView(route::choose, sprites);
				view.show(route.view(), "Shortest Path draws each walk when you reach it.");
				shots.put(out + "/voyage-sail.png", view);
				DestinationRoute moved = new DestinationRoute();
				moved.sync(destination);
				moved.choose(3);
				Map<Integer, String> elsewhere = new HashMap<>();
				elsewhere.put(1, "Rimmington");
				moved.advance(new WorldPoint(3100, 3200, 0), new DestinationRoute.Sailing(false, elsewhere));
				view = new RouteStepsView(moved::choose, sprites);
				view.show(moved.view());
				shots.put(out + "/voyage-moved.png", view);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				for (Map.Entry<String, RouteStepsView> shot : shots.entrySet())
				{
					SidebarPolishShot.shoot(shot.getValue(), shot.getKey());
				}
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}
}
