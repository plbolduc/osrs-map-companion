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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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

	/**
	 * The numbers stand in a column of their own, the digit alone, and the step you are on has its
	 * number in bold amber, its pin's colour on the world map, with its title bold and white. This
	 * pinned "▸ 1. Lumbridge Home Teleport" as one line: the mark the folds also use, before a
	 * number that started at a different x on every kind of row. The line over the steps is the way's
	 * length alone, because step 1 already names the way.
	 */
	@Test
	public void numbersTheStepsMarksTheCurrentOneAndFoldsTheOtherWays() throws IOException
	{
		receive();
		assertEquals(
			"52 tiles | 1 | Lumbridge Home Teleport | Standard spellbook"
			+ " | Ten seconds to cast, and a combat hit stops it | 2 | Walk 12 tiles to the bank | ▸ 3 other ways",
			shown());
		/* Colours, not weights: the test's look and feel draws every label bold already. */
		JLabel one = label("1");
		assertEquals(MapPin.AMBER, one.getForeground());
		assertEquals("right-aligned, so 9 and 10 end at one x", SwingConstants.RIGHT, one.getHorizontalAlignment());
		JLabel title = label("Lumbridge Home Teleport");
		assertEquals(Color.WHITE, title.getForeground());
		assertEquals("The step you are on", title.getToolTipText());
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label("2").getForeground());
		assertSame("a step's detail and notes start under its title's first letter",
			title.getParent(), label("Standard spellbook").getParent());
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
		/* A step behind is its title alone, so the step being walked is the first bright line. */
		assertEquals("52 tiles | 1 | Lumbridge Home Teleport | 2 | Walk 12 tiles to the bank"
			+ " | ▸ 3 other ways", shown());
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, label("1").getForeground());
		assertEquals(MapPin.AMBER, label("2").getForeground());
		List<JLabel> places = named("route.step.picture");
		assertFalse("the passed teleport's picture fades, as its pin does", places.get(0).isEnabled());
		assertTrue(places.get(1).isEnabled());
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

	/**
	 * A Reroute that did not happen was said in the grey of the steps above it, so it read as one more
	 * line of the route. It is said in the sidebar's refusal pink; Shortest Path's notice, which is
	 * news rather than a refusal, keeps the line's grey.
	 */
	@Test
	public void aRefusedRerouteIsSaidInPinkAndANoticeInGrey() throws IOException
	{
		receive();
		route.refuse(RoutePress.NEEDS_TILE);
		view.show(route.view());
		assertEquals(MapCompanionPanel.REFUSED, label(RoutePress.NEEDS_TILE).getForeground());

		route.follow(true);
		route.stopFollowing(ShortestPath.TAKEN_OVER);
		view.show(route.view());
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label(ShortestPath.TAKEN_OVER).getForeground());
	}

	/**
	 * Reroute's wait was written into its sentence once, "Try again in 4s." for good. It is the wait
	 * the map's gate holds as the card is drawn, so a press that makes it longer is counted, and the
	 * steps above are not built again each second to say it.
	 */
	@Test
	public void aReroutesWaitIsTheGatesAsTheCardIsDrawn() throws IOException
	{
		receive();
		long now = System.currentTimeMillis();
		route.finishReroute(route.beginReroute(now), null, MapWait.BUSY, now + 20_000L);
		view.show(route.view());
		JLabel line = starting("The map is busy.");
		assertTrue(line.getText(), line.getText().matches(".*The map is busy\\. Try again in (19|20)s\\..*"));
		JLabel step = label("Walk 12 tiles to the bank");

		route.finishPress(now + 40_000L);
		view.show(route.view());
		assertTrue(line.getText(), line.getText().matches(".*The map is busy\\. Try again in (39|40)s\\..*"));
		assertSame("the steps were not built again", step, label("Walk 12 tiles to the bank"));
	}

	/**
	 * On a run's leg the stop's own line names the way, so the way line said it a second time; the
	 * leg starts at its first step, and its length is the view's to hand This leg's heading.
	 */
	@Test
	public void aRunLegLeavesItsWayToTheStopAndItsLengthToTheHeading() throws IOException
	{
		receive();
		view.showRunLeg(route.view(), null);
		assertTrue(shown(), shown().startsWith("1 | Lumbridge Home Teleport | Standard spellbook"));
		assertEquals(Integer.valueOf(52), view.tiles());
	}

	@Test
	public void anotherWayShowsItsOwnSteps() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(shown(), shown().endsWith("▾ 3 other ways | Walk | Lumbridge tablet · 58 tiles | Docking point · 1340 tiles"));
		assertTrue(SidebarWords.press(view, "Walk"));
		view.show(route.view());
		/* The walk has no price and its step says Walk, so there is nothing to say over it. */
		assertTrue(shown(), shown().startsWith("1 | Walk 140 tiles"));
		assertTrue("the best way is now the other one", shown().contains("Lumbridge Home Teleport · 52 tiles · best"));
	}

	/**
	 * The spell's icon on its step, the tablet's sprite on its own, and a walk's row left as words.
	 * Every step keeps the picture's place once one has a picture, so the words start at one x; the
	 * place was the teleport row's alone, and its words began 21 pixels right of the walk's.
	 */
	@Test
	public void drawsTheGamesPictureOfWhatToClick() throws IOException
	{
		receive();
		assertEquals(java.util.Collections.singletonList("sprite 356 at " + RouteStepsView.PICTURE), asked);
		List<JLabel> places = named("route.step.picture");
		assertEquals("a place for the picture on the teleport's row and on the walk's", 2, places.size());

		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		asked.clear();
		assertTrue(SidebarWords.press(view, "Lumbridge tablet · 58 tiles"));
		view.show(route.view());
		/* The tablet's own step, then the home teleport's icon leading its way in the open fold. */
		assertEquals(java.util.Arrays.asList("item 8008 at " + RouteStepsView.PICTURE, "sprite 356 at " + RouteStepsView.PICTURE),
			asked);
	}

	/**
	 * Each other way starts with the picture of what it makes you use, the first of its steps that has
	 * one, as the website's chip leads with its teleport; a way with none keeps the picture's place, so
	 * the words start at one x. A way not open yet is grey with its picture faded, where it was as
	 * white as the rest, and pressing it still shows its steps.
	 */
	@Test
	public void eachOtherWayStartsWithWhatItMakesYouUse() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		RelayResponse.Destination fixture = new Gson().fromJson(reply, RelayResponse.class).directive.destination;
		fixture.route.options.get(2).blocked = true;
		route.sync(fixture);
		view.show(route.view());
		asked.clear();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(asked.toString(), asked.contains("item 8008 at " + RouteStepsView.PICTURE));

		JButton tablet = button("Lumbridge tablet · 58 tiles · not yet");
		assertEquals("a way not open yet is grey", ColorScheme.MEDIUM_GRAY_COLOR, tablet.getForeground());
		assertFalse("and its picture faded", wayPicture(tablet).isEnabled());
		JButton walk = button("Walk");
		assertEquals(Color.WHITE, walk.getForeground());
		assertTrue("a way with no picture keeps its place", wayPicture(walk).isEnabled());
		assertEquals(null, wayPicture(walk).getIcon());

		assertTrue(SidebarWords.press(view, "Lumbridge tablet · 58 tiles · not yet"));
		view.show(route.view());
		assertTrue(shown(), shown().startsWith("58 tiles | 1 | Use Lumbridge tablet"));
	}

	/**
	 * The way's name goes from the line over the steps only where the first step says it. A voyage's
	 * "Docking point" starts with a walk to the boat, so dropping it would leave the player not knowing
	 * which of the ways they are on.
	 */
	@Test
	public void theWaysNameStaysWhereTheFirstStepDoesNotSayIt()
	{
		RelayResponse.RouteOption docking = new RelayResponse.RouteOption();
		docking.label = "Docking point";
		docking.tiles = 1340;
		RelayResponse.RouteStep walk = new RelayResponse.RouteStep();
		walk.title = "Walk to Port Sarim";
		assertEquals("Docking point · 1340 tiles", RouteStepsView.currentWayLine(docking, Arrays.asList(walk)));
		RelayResponse.RouteStep board = new RelayResponse.RouteStep();
		board.title = "Use the docking point";
		assertEquals("whatever its capitals", "1340 tiles", RouteStepsView.currentWayLine(docking, Arrays.asList(board)));
		docking.tiles = null;
		assertEquals("", RouteStepsView.currentWayLine(docking, Arrays.asList(board)));
		assertEquals("Docking point", RouteStepsView.currentWayLine(docking, null));
	}

	/** The visible button with exactly these words. */
	private JButton button(String words)
	{
		List<JButton> buttons = new ArrayList<>();
		collectButtons(view, buttons);
		for (JButton button : buttons)
		{
			if (words.equals(button.getText()))
			{
				return button;
			}
		}
		throw new AssertionError("no button says " + words + ": " + shown());
	}

	private static void collectButtons(Component component, List<JButton> out)
	{
		if (!component.isVisible())
		{
			return;
		}
		if (component instanceof JButton)
		{
			out.add((JButton) component);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collectButtons(child, out);
			}
		}
	}

	/** The picture leading another way's words, in the row beside its button. */
	private static JLabel wayPicture(JButton way)
	{
		for (Component beside : way.getParent().getComponents())
		{
			if (beside instanceof JLabel && "route.way.picture".equals(beside.getName()))
			{
				return (JLabel) beside;
			}
		}
		throw new AssertionError("no picture beside " + way.getText());
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
		assertTrue(shown(), shown().contains("2 | Board your boat at Port Sarim | 3 | Sail to Catherby | About 650 tiles"
			+ " | ✓ 20 Sailing | 4 | Dock at Catherby | Your boat stays there"));
	}

	/**
	 * A sail the player has passed keeps only its title, and so drops its gates with its detail:
	 * they are a question about a mooring already reached.
	 */
	@Test
	public void aPassedSailKeepsOnlyItsTitle() throws IOException
	{
		receive();
		assertTrue(SidebarWords.press(view, "▸ 3 other ways"));
		assertTrue(SidebarWords.press(view, "Docking point · 1340 tiles"));
		/* At the pier, then on the deck, then alongside Catherby's mooring. */
		route.advance(new WorldPoint(3051, 3192, 0), new DestinationRoute.Sailing(false, null));
		route.advance(new WorldPoint(3051, 3192, 0), new DestinationRoute.Sailing(true, null));
		route.advance(new WorldPoint(2800, 3410, 0), new DestinationRoute.Sailing(true, null));
		view.show(route.view());
		assertEquals("the sail is behind, the dock is the step being walked", 3, route.view().step);
		assertTrue(shown(), shown().contains("3 | Sail to Catherby | 4 | Dock at Catherby | Your boat stays there"));
		assertFalse(shown(), shown().contains("20 Sailing"));
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

	/** The label whose words start with these, for a line whose end counts down. */
	private JLabel starting(String words)
	{
		List<JLabel> labels = new ArrayList<>();
		collectLabels(view, labels);
		for (JLabel label : labels)
		{
			if (label.getText().replaceAll("<[^>]+>", "").startsWith(words))
			{
				return label;
			}
		}
		throw new AssertionError("no label starts " + words + ": " + shown());
	}

	/** Every shown label with this name, in order. */
	private List<JLabel> named(String name)
	{
		List<JLabel> labels = new ArrayList<>();
		collectLabels(view, labels);
		List<JLabel> found = new ArrayList<>();
		for (JLabel label : labels)
		{
			if (name.equals(label.getName()))
			{
				found.add(label);
			}
		}
		return found;
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
