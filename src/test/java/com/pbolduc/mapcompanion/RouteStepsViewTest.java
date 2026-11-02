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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the destination's card says about a route the map sent, read off the real components.
 *
 * <p>The reply fixture's own route: Lumbridge Home Teleport then a walk upstairs to the bank, with
 * walking all the way, a Lumbridge tablet and a voyage from Port Sarim as the other ways. The
 * tablet is there so the fixture carries an item's picture beside the spell's, and the voyage so it
 * carries a sail's rows and its docking gate.
 */
public class RouteStepsViewTest
{
	private final DestinationRoute route = new DestinationRoute();
	/** What the view asked the client's cache for, in order: "item 8008", "sprite 356". */
	private final List<String> asked = new ArrayList<>();
	private final RouteStepsView view = new RouteStepsView(route::choose, new GameSprites(null, null, null)
	{
		@Override
		void item(JLabel label, int itemId, int box)
		{
			asked.add("item " + itemId + " at " + box);
		}

		@Override
		void sprite(JLabel label, int spriteId, int box)
		{
			asked.add("sprite " + spriteId + " at " + box);
		}
	});

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
			+ " | Ten seconds to cast, and a combat hit stops it | 2. Walk 12 tiles to the bank | ▸ 3 other ways",
			shown());
	}

	/**
	 * Arriving changes the card's shape. It used to keep the way and every step, greyed, with "You're
	 * there." squeezed between the last step and the other ways' fold, so a route done looked like a
	 * route still being walked; now one amber line takes the way's place and the rest goes.
	 */
	@Test
	public void movesTheMarkOnAndSaysWhenYouAreThere() throws IOException
	{
		receive();
		route.advance(new WorldPoint(3222, 3218, 0));
		view.show(route.view());
		assertTrue(shown(), shown().contains("▸ 2. Walk 12 tiles to the bank"));
		route.advance(new WorldPoint(3208, 3220, 2));
		view.show(route.view());
		assertEquals("the way, its steps and the other ways go", "Arrived", shown());
		assertEquals("in the colour a run gives a stop's own status", MapPin.AMBER, label("Arrived").getForeground());
	}

	/** On a run's leg the stop it led to already says You're here, so the leg goes on arrival. */
	@Test
	public void aRunLegSaysNothingOnceThere() throws IOException
	{
		receive();
		view.showRunLeg(route.view(), null);
		assertTrue(view.isVisible());
		route.advance(new WorldPoint(3222, 3218, 0));
		route.advance(new WorldPoint(3208, 3220, 2));
		view.showRunLeg(route.view(), null);
		assertFalse("and This leg's heading, which follows it, with it", view.isVisible());
	}

	@Test
	public void anotherWayShowsItsOwnSteps() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(shown(), shown().endsWith("▾ 3 other ways | Walk | Lumbridge tablet · 58 tiles | Docking point · 1340 tiles"));
		assertTrue(SidebarWords.press(view, "Walk"));
		view.show(route.view());
		assertTrue(shown(), shown().startsWith("Walk | ▸ 1. Walk 140 tiles"));
		assertTrue("the best way is now the other one", shown().contains("Lumbridge Home Teleport · 52 tiles · best"));
	}

	/** The spell's icon on its step, the tablet's sprite on its own, and a walk's row left as words. */
	@Test
	public void drawsTheGamesPictureOfWhatToClick() throws IOException
	{
		receive();
		assertEquals(java.util.Collections.singletonList("sprite 356 at " + RouteStepsView.PICTURE), asked);
		List<JLabel> titles = stepTitles();
		assertTrue("the teleport's row holds a place for its picture", titles.get(0).getIcon() != null);
		assertEquals("the walk's row has none", null, titles.get(1).getIcon());

		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		asked.clear();
		assertTrue(SidebarWords.press(view, "Lumbridge tablet · 58 tiles"));
		view.show(route.view());
		assertEquals(java.util.Collections.singletonList("item 8008 at " + RouteStepsView.PICTURE), asked);
	}

	@Test
	public void drawsWordsAloneWithoutTheClientsPictures() throws IOException
	{
		RouteStepsView bare = new RouteStepsView(route::choose);
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		route.sync(new Gson().fromJson(reply, RelayResponse.class).directive.destination);
		bare.show(route.view());
		List<JLabel> labels = new ArrayList<>();
		collectLabels(bare, labels);
		for (JLabel label : labels)
		{
			assertEquals(label.getText(), null, label.getIcon());
		}
	}

	/** The fixture's voyage: its three rows, and the docking mooring's gate under the one that sails. */
	@Test
	public void aVoyageSaysItsGateUnderTheSail() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(SidebarWords.press(view, "Docking point · 1340 tiles"));
		view.show(route.view());
		assertTrue(shown(), shown().contains("2. Board your boat at Port Sarim | 3. Sail to Catherby | About 650 tiles"
			+ " | ✓ 20 Sailing | 4. Dock at Catherby | Your boat stays there"));
	}

	/**
	 * A boat moved since the route was planned is said under the steps it contradicts. It came last,
	 * after the other ways' fold and the Shortest Path line, below every step.
	 */
	@Test
	public void aBoatThatHasMovedIsSaidUnderTheSteps() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(SidebarWords.press(view, "Docking point · 1340 tiles"));
		Map<Integer, String> moored = new HashMap<>();
		moored.put(1, "Rimmington");
		route.advance(new WorldPoint(3100, 3200, 0), new DestinationRoute.Sailing(false, moored));
		view.show(route.view(), "Shortest Path draws each walk when you reach it.");
		String said = shown();
		int boat = said.indexOf(RouteStepsView.boatAwayLine("Port Sarim"));
		assertTrue(said, boat > said.indexOf("Your boat stays there"));
		assertTrue("before the other ways: " + said, boat < said.indexOf("3 other ways"));
		assertTrue("and before Shortest Path's line: " + said, boat < said.indexOf("Shortest Path draws"));
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

	/** The label shown with exactly these words. */
	private JLabel label(String words)
	{
		List<JLabel> labels = new ArrayList<>();
		collectLabels(view, labels);
		for (JLabel label : labels)
		{
			if (label.getText().replaceAll("<[^>]+>", "").equals(words))
			{
				return label;
			}
		}
		throw new AssertionError("no label says " + words + ": " + shown());
	}

	/** Each step's numbered line, in order. */
	private List<JLabel> stepTitles()
	{
		List<JLabel> labels = new ArrayList<>();
		collectLabels(view, labels);
		List<JLabel> titles = new ArrayList<>();
		for (JLabel label : labels)
		{
			if (label.getText().replaceAll("<[^>]+>", "").matches("(▸ )?[0-9]+[.] .*"))
			{
				titles.add(label);
			}
		}
		return titles;
	}

	private static void collectLabels(Component component, List<JLabel> out)
	{
		if (component instanceof JLabel && component.isVisible() && ((JLabel) component).getText() != null)
		{
			out.add((JLabel) component);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collectLabels(child, out);
			}
		}
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
