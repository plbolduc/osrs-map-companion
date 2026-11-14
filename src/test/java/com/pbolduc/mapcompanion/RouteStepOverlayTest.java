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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The box over the game says the step the sidebar marks, and only while the route is followed. */
public class RouteStepOverlayTest
{
	private final DestinationRoute route = new DestinationRoute();

	private RelayResponse.Destination received() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		RelayResponse.Destination destination = new Gson().fromJson(reply, RelayResponse.class).directive.destination;
		route.sync(destination, null);
		return destination;
	}

	/** A route somebody sent is not followed until Follow, and the box waits with it. */
	@Test
	public void saysTheStepBeingWalkedOnlyWhileTheRouteIsFollowed() throws IOException
	{
		RelayResponse.Destination destination = received();
		assertNull("received, not yet followed", RouteStepOverlay.box(route.view(), destination.name));

		route.follow(true);
		RouteStepOverlay.Box box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("Lumbridge", box.title);
		/* The step's number left its words for the count at the right, where it says how many there are too. */
		assertEquals("Lumbridge Home Teleport", box.step);
		assertEquals("1/2", box.count);
		assertEquals("Standard spellbook", box.detail);

		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("Walk 12 tiles to the bank", box.step);
		assertEquals("2/2", box.count);
		assertNull("a walk with no detail has no second line", box.detail);

		route.advance(new WorldPoint(3208, 3220, 2), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertNull("gone once arrived", RouteStepOverlay.box(route.view(), destination.name));
	}

	/** A destination's name was typed by somebody in the group; it cannot colour the box. */
	@Test
	public void takesTheTagsOutOfWhatTheGroupTyped() throws IOException
	{
		received();
		route.follow(true);
		assertEquals("Bank", RouteStepOverlay.box(route.view(), "<col=ff0000>Bank</col>").title);
		assertEquals("Route", RouteStepOverlay.box(route.view(), "<col=ff0000></col>").title);
		assertEquals("Route", RouteStepOverlay.box(route.view(), null).title);
	}

	/**
	 * A long name wraps inside the box, as the step line does, rather than running past both of its
	 * sides as one centred line, cut off where the box sits at the screen's edge.
	 */
	@Test
	public void aLongNameWrapsInsideTheBox() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		Dimension one = drawn(destination);
		destination.name = "Civitas illa Fortis east bank deposit box";
		Dimension wrapped = drawn(destination);
		assertEquals("no letter is drawn outside the box", 0, lettersOutside);
		assertTrue("the name took another line rather than being dropped", wrapped.height > one.height);
	}

	/**
	 * The box names the destination in the tile label's words, and adds its world as the label does:
	 * only while the player is on another, where it says which world to hop to.
	 */
	@Test
	public void namesTheWorldOnlyWhileThePlayerIsOnAnother() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		RouteStepOverlay.Box box = RouteStepOverlay.box(route.view(), destination.name);
		assertEquals("Lumbridge (W302)", RouteStepOverlay.title(box, destination, 444));
		assertEquals("Lumbridge", RouteStepOverlay.title(box, destination, 302));
		assertEquals("the tile's label says the same", RouteStepOverlay.title(box, destination, 444),
			DestinationSceneOverlay.label(destination) + DestinationSceneOverlay.elsewhere(destination, 444));
		assertEquals(RouteStepOverlay.title(box, destination, 302),
			DestinationSceneOverlay.label(destination) + DestinationSceneOverlay.elsewhere(destination, 302));
		destination.world = null;
		assertEquals("a destination on any world names none", "Lumbridge", RouteStepOverlay.title(box, destination, 444));
	}

	/**
	 * A run's leg has its stop's number, which the stop's own pin carries, heading the box, and under
	 * it the step's words with how far through the leg at the right, as any other route's step has
	 * how far through its way. This held a leg's step to its words alone and gave any other step its
	 * pin's number before them; the count is on both now, so a leg says how many steps it has left.
	 */
	@Test
	public void aRunsLegNumbersItsStopAndCountsItsSteps() throws IOException
	{
		DestinationState destinations = new DestinationState(new Gson());
		FarmRunState farm = new FarmRunState();
		FarmRunRoute run = new FarmRunRoute(farm, destinations, route, new ClientThread());
		String json = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "farm-run.json")), StandardCharsets.UTF_8);
		farm.accept(new Gson().fromJson(json, RelayResponse.FarmRun.class), 1, 1_000);
		run.sync();
		RouteStepOverlay.Box box = RouteStepOverlay.box(route.view(), destinations.getDestination().name);
		assertEquals("1. Falador farm", box.title);
		assertEquals("Use Explorer's ring 2: Teleport", box.step);
		assertEquals("the leg's first step of its two", "1/2", box.count);

		/* A place picked instead of the run's next stop: its steps are counted the same way. */
		RelayResponse.Destination place = received();
		destinations.selectLocal(place);
		route.follow(true);
		box = RouteStepOverlay.box(route.view(), destinations.getDestination().name);
		assertEquals("Lumbridge Home Teleport", box.step);
		assertEquals("1/2", box.count);
	}

	/**
	 * How far through is drawn in the map's amber at the box's right edge, on the step's line, in a
	 * box with a picture and in one without. A step too long for its line still wraps inside the box
	 * beside the count, under the picture's column as well.
	 */
	@Test
	public void howFarThroughIsAmberAtTheRightAndALongStepStillWraps() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		Dimension pictured = drawn(destination);
		assertTrue("the count is drawn", amberPixels > 0);
		assertTrue("at the box's right edge: " + amberRight + " in " + pictured.width,
			amberRight >= 200 + pictured.width - 8 && amberRight < 200 + pictured.width);
		assertTrue("on the step's line, right of its words", amberLeft > wordsRightOnAmberRows);

		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		Dimension walk = drawn(destination);
		assertTrue("a walk, with no picture, has it too", amberPixels > 0);
		assertTrue(amberRight >= 200 + walk.width - 8 && amberRight < 200 + walk.width);

		Dimension oneLine = drawn(cast("Cast Home Teleport"));
		Dimension wrapped = drawn(cast("Cast Lumbridge Home Teleport from the standard spellbook and wait out the cast"));
		assertEquals("no letter is drawn outside the box", 0, lettersOutside);
		assertTrue("the step took more lines rather than being cut", wrapped.height > oneLine.height);
		assertEquals("the box is no wider for it", oneLine.width, wrapped.width);
		assertTrue("the count keeps the right edge", amberRight >= 200 + wrapped.width - 8 && amberRight < 200 + wrapped.width);
		assertTrue("and the words stop short of it", amberLeft > wordsRightOnAmberRows);
	}

	/** A followed route whose first step is a pictured cast with these words, and a walk after it. */
	private RelayResponse.Destination cast(String words)
	{
		RelayResponse.RouteStep cast = DestinationRouteTest.step("method", words, false, 3222, 3218, 0);
		cast.spriteId = 356;
		RelayResponse.Destination destination = DestinationRouteTest.destination("Lumbridge", DestinationRouteTest.route("Home",
			Arrays.asList(cast, DestinationRouteTest.step("arrival-walk", "Walk to the bank", true, 3208, 3220, 2))));
		route.sync(destination, null);
		route.follow(true);
		return destination;
	}

	/** The tile's one line cuts a long name short; the box, which wraps, keeps it whole, up to a bound. */
	@Test
	public void keepsTheWholeNameTheTileCutsShort() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		destination.name = "Civitas illa Fortis east bank deposit box";
		assertEquals(destination.name, RouteStepOverlay.box(route.view(), destination.name).title);
		assertEquals("Civitas illa Fortis east bank deposit b…", DestinationSceneOverlay.label(destination));
		assertEquals("a name somebody typed is still bounded", 120,
			RouteStepOverlay.box(route.view(), "a".repeat(500)).title.length());
	}

	/**
	 * A teleport step has the game's picture of what to click before its words, as the sidebar's row
	 * does, from the client's own cache. It is asked for once however often the box is drawn, nothing
	 * stands in its place until it arrives, and the box is wider by it from the first frame, so it
	 * keeps its size when the picture comes; a walk is words alone in a box of the old width.
	 */
	@Test
	public void aTeleportStepHasItsPictureBeforeItsWords() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		List<String> asked = new ArrayList<>();
		List<Consumer<BufferedImage>> answers = new ArrayList<>();
		RouteStepOverlay overlay = overlay(destination, new GameSprites(null, null, null)
		{
			@Override
			void sprite(int spriteId, int box, Consumer<BufferedImage> use)
			{
				asked.add("sprite " + spriteId + " at " + box);
				answers.add(use);
			}
		});
		Dimension waiting = drawn(overlay);
		assertEquals("asked once, at the sidebar's size", Collections.singletonList("sprite 356 at 16"), asked);
		assertEquals("nothing stands in its place yet", 0, picturePixels);

		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D paint = icon.createGraphics();
		paint.setColor(Color.MAGENTA);
		paint.fillRect(0, 0, 16, 16);
		paint.dispose();
		answers.get(0).accept(icon);
		Dimension shown = drawn(overlay);
		assertEquals("the whole picture, bare", 256, picturePixels);
		assertTrue("before the words", pictureRight < wordsLeft);
		assertEquals("the box keeps its size when the picture comes", waiting, shown);

		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		Dimension walk = drawn(overlay);
		assertEquals("a walk is words alone", 0, picturePixels);
		assertEquals("in a box narrower by the picture and its gap", shown.width - 20, walk.width);
		assertEquals(1, asked.size());
	}

	/**
	 * Shift and a right-click on the box offer the card's own buttons in the card's words, so a route
	 * can be paused, planned again or put away with the sidebar shut: Dismiss under a destination's
	 * steps, End run under a run's leg. Each entry presses that button, as a route key does.
	 */
	@Test
	public void itsMenuPressesTheCardsOwnButtons() throws Exception
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		RouteStepOverlay overlay = overlay(destination, new GameSprites(null, null, null));
		List<MapCompanionPanel.Act> pressed = new ArrayList<>();
		overlay.pressWith(pressed::add);
		drawn(overlay);
		assertEquals(Arrays.asList("Pause", "Reroute", "Dismiss"), options(overlay));
		for (OverlayMenuEntry entry : overlay.getMenuEntries())
		{
			choose(entry);
		}
		assertEquals(Arrays.asList(MapCompanionPanel.Act.FOLLOW, MapCompanionPanel.Act.REROUTE, MapCompanionPanel.Act.END), pressed);

		DestinationState destinations = new DestinationState(new Gson());
		FarmRunState farm = new FarmRunState();
		FarmRunRoute run = new FarmRunRoute(farm, destinations, route, new ClientThread());
		HarnessGame game = new HarnessGame();
		game.login("Example", 444, 3222, 3218, 0);
		RouteStepOverlay leg = new RouteStepOverlay(game.client(),
			new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL), destinations, route, run,
			new GameSprites(null, null, null));
		String json = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "farm-run.json")), StandardCharsets.UTF_8);
		farm.accept(new Gson().fromJson(json, RelayResponse.FarmRun.class), 1, 1_000);
		run.sync();
		drawn(leg);
		assertEquals(Arrays.asList("Pause", "Reroute", "End run"), options(leg));
	}

	/**
	 * The lines the sidebar prints under a step are in the box too, where a player with the sidebar shut
	 * reads them, and a step that lands in the Wilderness says so in the sidebar's pink for it, as the
	 * map's card says it in red. A note was typed nowhere near this client, so its tags come out.
	 */
	@Test
	public void aStepsNotesAreInTheBoxAndTheWildernessIsPink() throws IOException
	{
		RelayResponse.Destination destination = received();
		route.follow(true);
		assertEquals(Collections.singletonList("Ten seconds to cast, and a combat hit stops it"),
			RouteStepOverlay.box(route.view(), destination.name).notes);
		drawn(destination);
		assertEquals("a cast in Lumbridge warns of nothing", 0, pinkPixels);

		RelayResponse.RouteStep amulet = DestinationRouteTest.step("method", "Use Burning amulet: Lava Maze", false, 3028, 3842, 0);
		amulet.notes = Arrays.asList("<col=00ff00>Lands in the Wilderness</col>", " ");
		RelayResponse.Destination maze = DestinationRouteTest.destination("Lava Maze",
			DestinationRouteTest.route("Burning amulet", Collections.singletonList(amulet)));
		route.sync(maze, null);
		route.follow(true);
		assertEquals(Collections.singletonList("Lands in the Wilderness"), RouteStepOverlay.box(route.view(), maze.name).notes);
		drawn(maze);
		assertTrue("the warning is the Wilderness's pink", pinkPixels > 0);
	}

	private static List<String> options(RouteStepOverlay overlay)
	{
		return overlay.getMenuEntries().stream().map(OverlayMenuEntry::getOption).collect(Collectors.toList());
	}

	/** A choice of an entry, as RuneLite's menu makes one: the callback the overlay gave the entry. */
	@SuppressWarnings("unchecked")
	private static void choose(OverlayMenuEntry entry) throws ReflectiveOperationException
	{
		Field callback = OverlayMenuEntry.class.getDeclaredField("callback");
		callback.setAccessible(true);
		((Consumer<MenuEntry>) callback.get(entry)).accept(null);
	}

	/** White pixels drawn outside the box by the last {@link #drawn}. */
	private int lettersOutside;
	/** Pixels in the Wilderness's pink in the last {@link #drawn}. */
	private int pinkPixels;
	/** The picture's pixels in the last {@link #drawn}, the right-most of them, and the left-most letter on their rows. */
	private int picturePixels;
	private int pictureRight;
	private int wordsLeft;
	/** The count's amber pixels in the last {@link #drawn}, their left and right edges, and the right-most letter on their rows. */
	private int amberPixels;
	private int amberLeft;
	private int amberRight;
	private int wordsRightOnAmberRows;

	/** The box as the game draws it, twice, because a panel sizes its background from the frame before. */
	private Dimension drawn(RelayResponse.Destination destination)
	{
		return drawn(overlay(destination, new GameSprites(null, null, null)));
	}

	/** The box as the plugin wires it, on a route followed to this destination. */
	private RouteStepOverlay overlay(RelayResponse.Destination destination, GameSprites sprites)
	{
		DestinationState destinations = new DestinationState(new Gson());
		destinations.selectLocal(destination);
		HarnessGame game = new HarnessGame();
		game.login("Example", 444, 3222, 3218, 0);
		return new RouteStepOverlay(game.client(),
			new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL), destinations, route,
			new FarmRunRoute(new FarmRunState(), destinations, route, new ClientThread()), sprites);
	}

	private Dimension drawn(RouteStepOverlay overlay)
	{
		int left = 200;
		BufferedImage image = new BufferedImage(600, 240, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		graphics.translate(left, 20);
		overlay.render(graphics);
		Dimension size = overlay.render(graphics);
		graphics.dispose();
		lettersOutside = 0;
		pinkPixels = 0;
		picturePixels = 0;
		pictureRight = -1;
		wordsLeft = Integer.MAX_VALUE;
		amberPixels = 0;
		amberLeft = Integer.MAX_VALUE;
		amberRight = -1;
		wordsRightOnAmberRows = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			boolean pictureRow = false;
			boolean amberRow = false;
			int firstLetter = Integer.MAX_VALUE;
			int lastLetter = -1;
			for (int x = 0; x < image.getWidth(); x++)
			{
				int pixel = image.getRGB(x, y);
				if (pixel == Color.WHITE.getRGB())
				{
					firstLetter = Math.min(firstLetter, x);
					lastLetter = x;
					if (x < left || x >= left + size.width)
					{
						lettersOutside++;
					}
				}
				pinkPixels += pixel == MapCompanionPanel.REFUSED.getRGB() ? 1 : 0;
				if (pixel == MapPin.AMBER.getRGB())
				{
					amberPixels++;
					amberLeft = Math.min(amberLeft, x);
					amberRight = Math.max(amberRight, x);
					amberRow = true;
				}
				if (pixel == Color.MAGENTA.getRGB())
				{
					picturePixels++;
					pictureRight = Math.max(pictureRight, x);
					pictureRow = true;
				}
			}
			if (pictureRow)
			{
				wordsLeft = Math.min(wordsLeft, firstLetter);
			}
			if (amberRow)
			{
				wordsRightOnAmberRows = Math.max(wordsRightOnAmberRows, lastLetter);
			}
		}
		return size;
	}
}
