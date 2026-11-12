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
import java.awt.Dimension;
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
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
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
 *
 * <p>The ways were a fold under the steps, <i>▸ 3 other ways</i>, opening a table of the ways not
 * being followed, and these tests pinned the fold and the table. They are a strip of chips over the
 * steps now, one for every way, so the tests that pressed the fold press a chip and the ones that read
 * the table read the strip. A chip is a picture and a name, as the website's is: the tablet's is the
 * {@code face} the fixture sends, <i>Lumbridge Castle</i>, and the others are their labels.
 */
public class RouteStepsViewTest
{
	private final DestinationRoute route = new DestinationRoute();
	/** What the view asked the client's cache for, in order: "item 8008", "sprite 356". */
	private final List<String> asked = new ArrayList<>();
	/** Every press the view handed on, by the way's place in the map's list. */
	private final List<Integer> pressed = new ArrayList<>();
	private final RouteStepsView view = new RouteStepsView(way ->
	{
		pressed.add(way);
		route.choose(way);
	}, new GameSprites(null, null, null)
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
		route.sync(new Gson().fromJson(reply, RelayResponse.class).directive.destination, null);
		view.show(route.view());
	}

	/**
	 * The numbers stand in a column of their own, the digit alone, and the step you are on has its
	 * number in bold amber, its pin's colour on the world map, with its title bold and white. This
	 * pinned "▸ 1. Lumbridge Home Teleport" as one line: the mark the folds also use, before a
	 * number that started at a different x on every kind of row.
	 *
	 * <p>The line over the steps was the way's length alone, "52 tiles", because step 1 already names
	 * the way, and the other ways were a fold under the steps. With several ways the strip comes first,
	 * each chip a name, and the line under it is the chosen way's length again: its chip names it.
	 */
	@Test
	public void numbersTheStepsMarksTheCurrentOneUnderTheStripOfWays() throws IOException
	{
		receive();
		assertEquals(
			"Lumbridge Home Teleport | Walk | Lumbridge Castle | Docking point | 52 tiles | 1 | Lumbridge Home Teleport"
			+ " | Standard spellbook | Ten seconds to cast, and a combat hit stops it | 2 | Walk 12 tiles to the bank",
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
	 * route still being walked; now one amber line takes the way's place and the rest goes, the strip
	 * of ways with it: there is no way left to pick.
	 */
	@Test
	public void movesTheMarkOnAndSaysWhenYouAreThere() throws IOException
	{
		receive();
		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		view.show(route.view());
		/* A step behind is its title alone, so the step being walked is the first bright line. */
		assertEquals("Lumbridge Home Teleport | Walk | Lumbridge Castle | Docking point | 52 tiles | 1"
			+ " | Lumbridge Home Teleport | 2 | Walk 12 tiles to the bank", shown());
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, label("1").getForeground());
		assertEquals(MapPin.AMBER, label("2").getForeground());
		List<JLabel> places = named("route.step.picture");
		assertFalse("the passed teleport's picture fades, as its pin does", places.get(0).isEnabled());
		assertTrue(places.get(1).isEnabled());
		route.advance(new WorldPoint(3208, 3220, 2), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		view.show(route.view());
		assertEquals("the strip, the way and its steps go", "Arrived", shown());
		assertTrue(chips().isEmpty());
		assertEquals("in the colour a run gives a stop's own status", MapPin.AMBER, label("Arrived").getForeground());
	}

	/** On a run's leg the stop it led to already says You're here, so the leg goes on arrival. */
	@Test
	public void aRunLegSaysNothingOnceThere() throws IOException
	{
		receive();
		view.showRunLeg(route.view(), null);
		assertTrue(view.isVisible());
		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		route.advance(new WorldPoint(3208, 3220, 2), DestinationRoute.Sailing.ASHORE_UNKNOWN);
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
		assertTrue("and no strip: a leg's way is the run's to pick, not this view's", chips().isEmpty());
		assertEquals(Integer.valueOf(52), view.tiles());
	}

	/**
	 * A chip for every way the map sent, the one being followed among them, in the map's order. The
	 * ways were a table of the ones not being followed, so a press took the pressed row out and put
	 * the way left behind in, and the rows moved under the pointer. A press on a chip changes which one
	 * is filled and nothing else about the strip: the same chips, in the same places, the same size.
	 */
	@Test
	public void aChipForEveryWayInAnOrderAPressDoesNotChange() throws IOException
	{
		receive();
		List<String> ways = Arrays.asList("Lumbridge Home Teleport · 52 tiles · best", "Walk",
			"Lumbridge tablet · 58 tiles", "Docking point · 1340 tiles");
		assertEquals("each chip says its way in full when pointed at", ways, tips());
		assertEquals(Arrays.asList(ColorScheme.BRAND_ORANGE, ColorScheme.MEDIUM_GRAY_COLOR, ColorScheme.MEDIUM_GRAY_COLOR,
			ColorScheme.MEDIUM_GRAY_COLOR), outlines());
		assertEquals(Arrays.asList(true, false, false, false), filled());
		assertEquals("the chosen way's name is white", Color.WHITE, name(chips().get(0)).getForeground());
		assertEquals("Lumbridge Home Teleport", name(chips().get(0)).getText());
		assertEquals("the name the website's chip wears, where the map sent one", "Lumbridge Castle", name(chips().get(2)).getText());
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, name(chips().get(2)).getForeground());
		List<Dimension> sizes = sizes();

		pick("Lumbridge tablet");
		assertEquals(Arrays.asList(2), pressed);
		assertEquals("the order is the map's, whichever is chosen", ways, tips());
		assertEquals(Arrays.asList(ColorScheme.MEDIUM_GRAY_COLOR, ColorScheme.MEDIUM_GRAY_COLOR, ColorScheme.BRAND_ORANGE,
			ColorScheme.MEDIUM_GRAY_COLOR), outlines());
		assertEquals(Arrays.asList(false, false, true, false), filled());
		assertEquals("a chip is the same size chosen or not, so none of them moved", sizes, sizes());
		assertEquals(Color.WHITE, name(chips().get(2)).getForeground());
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, name(chips().get(0)).getForeground());
		assertTrue("and the steps are the pressed way's: " + shown(),
			shown().endsWith("Docking point | 58 tiles · 6 more than best | 1 | Use Lumbridge tablet | Lumbridge Castle"
				+ " | 2 | Walk 13 tiles to the bank"));

		chips().get(2).doClick();
		assertEquals("the chip of the way being followed is no press", Arrays.asList(2), pressed);
	}

	/**
	 * A chip is the picture of what the way makes you use and its name, as the website's chip is. It
	 * was the picture and the way's length as a bare number, and a number alone did not say what it
	 * counted. The picture's place is the step picture's square whether or not the client's cache has
	 * answered, so a picture landing moves nothing. The chosen chip is filled with the website's
	 * orange, and the others are the card's own ground.
	 */
	@Test
	public void aChipIsAPictureAndAName() throws IOException
	{
		receive();
		List<JButton> chips = chips();
		for (JButton chip : chips)
		{
			assertEquals("a picture, then a name", 2, chip.getComponentCount());
			assertEquals(new Dimension(RouteStepsView.PICTURE, RouteStepsView.PICTURE), chip.getComponent(0).getPreferredSize());
			assertEquals(ColorScheme.BRAND_ORANGE, chip.getBackground());
		}

		RouteStepsView bare = new RouteStepsView(route::choose);
		bare.show(route.view());
		List<String> said = new ArrayList<>();
		collect(bare, said);
		assertEquals("without the client's pictures a chip is its name alone",
			Arrays.asList("Lumbridge Home Teleport", "Walk", "Lumbridge Castle", "Docking point"), said.subList(0, 4));
	}

	/**
	 * Four named ways are wider than the card. The strip starts a new row before a chip would pass
	 * the card's edge, so no chip is cut off and the column knows how tall the strip is. A name wider
	 * than the card by itself is cut to the card, and its tip still says it whole.
	 */
	@Test
	public void theStripStartsANewRowAtTheCardsEdge() throws IOException
	{
		RelayResponse.Destination fixture = fixture();
		String[] names = {"A", "B", "A way with a long name", new String(new char[100]).replace('\0', 'W')};
		for (int i = 0; i < names.length; i++)
		{
			fixture.route.options.get(i).label = names[i];
			fixture.route.options.get(i).face = null;
		}
		route.sync(fixture, null);
		view.show(route.view());
		List<JButton> chips = chips();
		assertEquals(4, chips.size());
		assertEquals("the widest name is cut to the card", MapCompanionPanel.CARD_TEXT_WIDTH, chips.get(3).getPreferredSize().width);
		assertTrue("and said whole when pointed at", tips().get(3).startsWith(names[3]));
		List<Container> rows = new ArrayList<>();
		for (JButton chip : chips)
		{
			if (!rows.contains(chip.getParent()))
			{
				rows.add(chip.getParent());
			}
		}
		assertTrue("more than one row: " + rows.size(), rows.size() > 1);
		for (Container row : rows)
		{
			assertTrue("a row fits the card: " + row.getPreferredSize().width,
				row.getPreferredSize().width <= MapCompanionPanel.CARD_TEXT_WIDTH);
		}
		assertSame("and a row is filled before the next is started", chips.get(0).getParent(), chips.get(1).getParent());
	}

	/**
	 * The line under the strip is the chosen way's length and how it compares. Its chip names it, so
	 * the line does not: it is the length, then how much longer than the best the way is. The best
	 * way's line has nothing to compare and says so by saying nothing more, and a way the map priced
	 * no length for has no line.
	 */
	@Test
	public void theLineUnderTheStripIsTheChosenWaysLengthAndComparesIt() throws IOException
	{
		receive();
		assertEquals("52 tiles", way());
		pick("Lumbridge tablet");
		assertEquals("58 tiles · 6 more than best", way());
		pick("Walk");
		assertEquals("a way the map did not price has no line", "", way());

		List<RelayResponse.RouteOption> ways = fixture().route.options;
		RelayResponse.RouteOption tablet = ways.get(2);
		tablet.tiles = 40;
		assertEquals("a shorter way that is not the best says only its length", "40 tiles",
			RouteStepsView.chosenWayLine(ways, tablet));
		ways.get(0).tiles = null;
		tablet.tiles = 58;
		assertEquals("and so does one the best has no price to compare with", "58 tiles",
			RouteStepsView.chosenWayLine(ways, tablet));
	}

	/**
	 * A route with one way has nothing to pick, so it has no strip and its line is as it was: the
	 * name left out where the first step says it.
	 */
	@Test
	public void aSingleWayHasNoStrip()
	{
		RelayResponse.Route one = DestinationRouteTest.route("Ring of dueling", DestinationRouteTest.alKharid());
		one.options.get(0).tiles = 80;
		route.sync(DestinationRouteTest.destination("Al Kharid bank", one), null);
		view.show(route.view());
		assertTrue(chips().isEmpty());
		assertTrue(shown(), shown().startsWith("80 tiles | 1 | Ring of dueling | 2 | Walk 9 tiles to the stairs"));
	}

	/**
	 * The sidebar refreshes every second, and a strip built again under the pointer would eat the
	 * press between its down and its up. An unchanged second leaves the same chips standing.
	 */
	@Test
	public void anUnchangedSecondDoesNotRebuildTheStrip() throws IOException
	{
		receive();
		List<JButton> before = chips();
		view.show(route.view());
		List<JButton> after = chips();
		assertEquals(before.size(), after.size());
		for (int i = 0; i < before.size(); i++)
		{
			assertSame(before.get(i), after.get(i));
		}
		pick("Walk");
		assertNotSame("a pick does: the fill has moved", before.get(0), chips().get(0));
	}

	/** A chip's name is part of what the strip says, so a way whose name alone changed is drawn again. */
	@Test
	public void aChangedNameRedrawsTheStrip() throws IOException
	{
		receive();
		route.view().ways.get(2).face = "Castle";
		view.show(route.view());
		assertEquals("Castle", name(chips().get(2)).getText());
	}

	/**
	 * The spell's icon on its step, the tablet's sprite on its own, and the walking boot on a walk.
	 * Every step keeps the picture's place once one has a picture, so the words start at one x; the
	 * place was the teleport row's alone, and its words began 21 pixels right of the walk's.
	 *
	 * <p>A walk's row was left as words, and this asserted that only the teleport's picture was asked
	 * for. A step the map names no picture for now gets one by its kind, so the boot is asked too.
	 *
	 * <p>The other ways' pictures were asked for only once their fold was opened. Every way has a chip
	 * from the start, so each chip's picture is asked for before the steps': the spell, the walk's
	 * boot, the tablet, and the boat of the voyage, which starts with a walk and is still a voyage.
	 */
	@Test
	public void drawsTheGamesPictureOfWhatToClick() throws IOException
	{
		receive();
		String boot = "sprite " + net.runelite.api.gameval.SpriteID.OrbIcon._2 + " at " + RouteStepsView.PICTURE;
		String spell = "sprite 356 at " + RouteStepsView.PICTURE;
		String tablet = "item 8008 at " + RouteStepsView.PICTURE;
		String boat = "sprite " + net.runelite.api.gameval.SpriteID.IconSailingBoats35x35._1 + " at " + RouteStepsView.PICTURE;
		assertEquals(Arrays.asList(spell, boot, tablet, boat, spell, boot), asked);
		List<JLabel> places = named("route.step.picture");
		assertEquals("a place for the picture on the teleport's row and on the walk's", 2, places.size());

		asked.clear();
		pick("Lumbridge tablet");
		/* The four chips again, then the tablet's own step and its walk's boot. */
		assertEquals(Arrays.asList(spell, boot, tablet, boat, tablet, boot), asked);
	}

	/**
	 * A step the map names no item or spell for is drawn by its kind: a boot for a walk, a boat for
	 * boarding and docking, a ship at sea for the sail, and nothing for a step that is none of
	 * these. Only the list draws them, so a stop's picture and a way's lead stay a teleport's own:
	 * a leg that is all walking has no picture to lead with.
	 */
	@Test
	public void aStepWithNoPictureIsDrawnByItsKind()
	{
		RelayResponse.RouteStep walk = new RelayResponse.RouteStep();
		walk.slot = "arrival-walk";
		walk.walk = true;
		assertEquals(net.runelite.api.gameval.SpriteID.OrbIcon._2, RouteStepsView.way(walk));
		RelayResponse.RouteStep sail = new RelayResponse.RouteStep();
		sail.slot = RouteWire.SAIL;
		assertEquals(net.runelite.api.gameval.SpriteID.IconSailingStatus18x18._0, RouteStepsView.way(sail));
		RelayResponse.RouteStep board = new RelayResponse.RouteStep();
		board.slot = RouteWire.BOARD;
		RelayResponse.RouteStep dock = new RelayResponse.RouteStep();
		dock.slot = RouteWire.DOCK;
		assertEquals(net.runelite.api.gameval.SpriteID.IconSailingBoats35x35._1, RouteStepsView.way(board));
		assertEquals(RouteStepsView.way(board), RouteStepsView.way(dock));
		RelayResponse.RouteStep ride = new RelayResponse.RouteStep();
		ride.slot = "method";
		assertEquals("a ride the map drew no picture for stays words", 0, RouteStepsView.way(ride));
		org.junit.Assert.assertNull(RouteStepsView.firstPictured(java.util.Arrays.asList(walk, board, sail, dock)));
	}

	/**
	 * A way not open yet comes after the ways that are, whatever its place in the map's list, its
	 * picture faded and its outline the card's own grey, its name as readable as any way's not
	 * chosen, and pressing it still shows its steps. Followed, it says on a
	 * line of its own that it cannot be taken yet, in the refusal pink.
	 *
	 * <p>This asserted the table's line "You can't take these yet:" over a group of rows. A strip has
	 * no room for a heading, so the chip is faded, its tip says why, and the sentence is under the way
	 * line once the way is the one shown.
	 */
	@Test
	public void aWayNotOpenYetIsLastAndFadedAndStillShowsItsSteps() throws IOException
	{
		RelayResponse.Destination fixture = fixture();
		fixture.route.options.get(2).blocked = true;
		route.sync(fixture, null);
		view.show(route.view());
		List<String> ways = Arrays.asList("Lumbridge Home Teleport · 52 tiles · best", "Walk", "Docking point · 1340 tiles",
			"Lumbridge tablet · 58 tiles. You can't take this way yet. Press to see its steps.");
		assertEquals("the ways that can be taken, then the one that cannot", ways, tips());
		JButton tablet = chips().get(3);
		assertFalse("its picture faded", tablet.getComponent(0).isEnabled());
		assertEquals("its outline the card's own grey", ColorScheme.DARK_GRAY_COLOR, outlines().get(3));
		assertEquals("and its name still readable", ColorScheme.LIGHT_GRAY_COLOR, name(tablet).getForeground());
		assertTrue("a way that can be taken keeps its picture", chips().get(2).getComponent(0).isEnabled());
		assertFalse("nothing says it cannot be taken while another way is shown: " + shown(), shown().contains("You can't"));

		pick("Lumbridge tablet");
		assertEquals(ways, tips());
		assertEquals("chosen, it is filled as any chosen way is", Arrays.asList(false, false, false, true), filled());
		assertFalse("and still faded", chips().get(3).getComponent(0).isEnabled());
		assertTrue(shown(), shown().contains(
			"58 tiles · 6 more than best | You can't take this way yet. | 1 | Use Lumbridge tablet"));
		assertEquals(MapCompanionPanel.REFUSED, label("You can't take this way yet.").getForeground());
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

	private static RelayResponse.Destination fixture() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		return new Gson().fromJson(reply, RelayResponse.class).directive.destination;
	}

	/** The strip's chips, in the order they are drawn: the only buttons this view has. */
	private List<JButton> chips()
	{
		List<JButton> buttons = new ArrayList<>();
		collectButtons(view, buttons);
		return buttons;
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
			return;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collectButtons(child, out);
			}
		}
	}

	/** What each chip says when pointed at, in the strip's order. */
	private List<String> tips()
	{
		List<String> tips = new ArrayList<>();
		for (JButton chip : chips())
		{
			tips.add(SidebarWords.plain(chip.getToolTipText()));
		}
		return tips;
	}

	/** The colour of each chip's outline, in the strip's order. */
	private List<Color> outlines()
	{
		List<Color> outlines = new ArrayList<>();
		for (JButton chip : chips())
		{
			outlines.add(((LineBorder) ((CompoundBorder) chip.getBorder()).getOutsideBorder()).getLineColor());
		}
		return outlines;
	}

	/** Where each chip would stand: its row and its size, in the strip's order. */
	private List<Dimension> sizes()
	{
		List<Dimension> sizes = new ArrayList<>();
		for (JButton chip : chips())
		{
			sizes.add(chip.getPreferredSize());
		}
		return sizes;
	}

	/** Whether each chip is filled, in the strip's order: the chosen one is, with the website's orange. */
	private List<Boolean> filled()
	{
		List<Boolean> filled = new ArrayList<>();
		for (JButton chip : chips())
		{
			filled.add(chip.isContentAreaFilled());
		}
		return filled;
	}

	/** A chip's name, the last thing in it. */
	private static JLabel name(JButton chip)
	{
		return (JLabel) chip.getComponent(chip.getComponentCount() - 1);
	}

	/** Press the chip of the way with this name, and draw what the sidebar's refresh then would. */
	private void pick(String way)
	{
		for (JButton chip : chips())
		{
			if (SidebarWords.plain(chip.getToolTipText()).startsWith(way))
			{
				chip.doClick();
				view.show(route.view());
				return;
			}
		}
		throw new AssertionError("no chip for " + way + ": " + tips());
	}

	/** The line under the strip: the one after the chips' names, or none where the first step's number follows them. */
	private String way()
	{
		List<String> out = new ArrayList<>();
		collect(view, out);
		String line = out.get(chips().size());
		return line.equals("1") ? "" : line;
	}

	@Test
	public void drawsWordsAloneWithoutTheClientsPictures() throws IOException
	{
		RouteStepsView bare = new RouteStepsView(route::choose);
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		route.sync(new Gson().fromJson(reply, RelayResponse.class).directive.destination, null);
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
		pick("Docking point");
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
		pick("Docking point");
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
	 * after the other ways' fold and the Shortest Path line, below every step. The fold is gone, so
	 * what this holds now is that only the steps stand between the way line and the boat's line.
	 */
	@Test
	public void aBoatThatHasMovedIsSaidUnderTheSteps() throws IOException
	{
		receive();
		pick("Docking point");
		Map<Integer, String> moored = new HashMap<>();
		moored.put(1, "Rimmington");
		route.advance(new WorldPoint(3100, 3200, 0), new DestinationRoute.Sailing(false, moored));
		view.show(route.view(), "Shortest Path draws each walk when you reach it.");
		String said = shown();
		int boat = said.indexOf(RouteStepsView.boatAwayLine("Port Sarim"));
		assertTrue(said, boat > said.indexOf("Your boat stays there"));
		assertTrue("and under the way line, with the strip: " + said,
			boat > said.indexOf("1340 tiles · 1288 more than best"));
		assertTrue("and before Shortest Path's line: " + said, boat < said.indexOf("Shortest Path draws"));
	}

	@Test
	public void saysWhenTheRouteWasPlannedFromNowhere()
	{
		RelayResponse.Route nowhere = DestinationRouteTest.route("walk", DestinationRouteTest.alKharid());
		nowhere.origin = null;
		route.sync(DestinationRouteTest.destination("Al Kharid bank", nowhere), null);
		view.show(route.view());
		assertTrue(shown(), shown().contains("Planned without your position. Reroute plans from where you stand."));
	}

	/** The label shown with exactly these words, under the strip: a chip's name can be a step's title too. */
	private JLabel label(String words)
	{
		List<JLabel> labels = new ArrayList<>();
		collectLabels(view, labels);
		for (JLabel label : labels)
		{
			if (!(label.getParent() instanceof JButton) && label.getText().replaceAll("<[^>]+>", "").equals(words))
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

	/**
	 * Shortest Path draws walks only. A sea charting run's legs are sails from end to end, and the
	 * line under them said it would draw each walk when you reached it, on a leg with none to draw.
	 */
	@Test
	public void saysShortestPathDrawsAWalkOnlyWhereOneIsLeft()
	{
		RelayResponse.RouteStep sail = new RelayResponse.RouteStep();
		sail.slot = "sail";
		RelayResponse.RouteStep walk = new RelayResponse.RouteStep();
		walk.slot = "walk";
		walk.walk = true;
		assertFalse("a leg that is all sail", RouteStepsView.walkLeft(Arrays.asList(sail, sail), 0));
		assertTrue("a walk still ahead", RouteStepsView.walkLeft(Arrays.asList(sail, walk), 0));
		assertTrue("the walk you are on", RouteStepsView.walkLeft(Arrays.asList(sail, walk), 1));
		assertFalse("a walk already behind you", RouteStepsView.walkLeft(Arrays.asList(walk, sail), 1));
	}
}
