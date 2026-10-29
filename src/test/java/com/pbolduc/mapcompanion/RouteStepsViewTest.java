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
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the destination's card says about a route the map sent, read off the real components.
 *
 * <p>The reply fixture's own route: Lumbridge Home Teleport then a walk upstairs to the bank, with
 * walking all the way as the other way.
 */
public class RouteStepsViewTest
{
	private final DestinationRoute route = new DestinationRoute();
	private final RouteStepsView view = new RouteStepsView(route::choose);

	private void receive() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		route.sync(new Gson().fromJson(reply, RelayResponse.class).directive.destination);
		view.show(route.view());
	}

	@Test
	public void numbersTheStepsMarksTheCurrentOneAndFoldsTheOtherWays() throws IOException
	{
		receive();
		assertEquals(
			"Lumbridge Home Teleport · 52 tiles | ▸ 1. Lumbridge Home Teleport | Standard spellbook"
			+ " | Ten seconds to cast, and a combat hit stops it | 2. Walk 12 tiles to the bank | ▸ 1 other way",
			shown());
	}

	@Test
	public void movesTheMarkOnAndSaysWhenYouAreThere() throws IOException
	{
		receive();
		route.advance(new WorldPoint(3222, 3218, 0));
		view.show(route.view());
		assertTrue(shown(), shown().contains("▸ 2. Walk 12 tiles to the bank"));
		route.advance(new WorldPoint(3208, 3220, 2));
		view.show(route.view());
		assertTrue(shown(), shown().contains("You're there."));
		assertTrue("no step is marked once arrived", !shown().contains("▸ 2."));
	}

	@Test
	public void anotherWayShowsItsOwnSteps() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 1 other way"));
		assertTrue(shown(), shown().endsWith("▾ 1 other way | Walk"));
		assertTrue(SidebarWords.press(view, "Walk"));
		view.show(route.view());
		assertTrue(shown(), shown().startsWith("Walk | ▸ 1. Walk 140 tiles"));
		assertTrue("the best way is now the other one", shown().contains("Lumbridge Home Teleport · 52 tiles · best"));
	}

	@Test
	public void saysWhenTheRouteWasPlannedFromNowhere()
	{
		RelayResponse.Route nowhere = DestinationRouteTest.route("walk", DestinationRouteTest.alKharid());
		nowhere.origin = null;
		route.sync(DestinationRouteTest.destination("Al Kharid bank", nowhere));
		view.show(route.view());
		assertTrue(shown(), shown().contains("Planned without your position. Reroute plans from where you stand."));
	}

	/** The words on screen: visible labels and buttons only, markup taken out. */
	private String shown()
	{
		List<String> out = new ArrayList<>();
		collect(view, out);
		return String.join(" | ", out);
	}

	private static void collect(Component component, List<String> out)
	{
		if (!component.isVisible())
		{
			return;
		}
		String text = component instanceof JLabel ? ((JLabel) component).getText()
			: component instanceof JButton ? ((JButton) component).getText() : null;
		if (text != null && !text.isEmpty())
		{
			out.add(text.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, out);
			}
		}
	}
}
