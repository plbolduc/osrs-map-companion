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

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ThinProgressBar;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.card;
import static com.pbolduc.mapcompanion.MapCompanionPanel.CARD_TEXT_WIDTH;
import static com.pbolduc.mapcompanion.MapCompanionPanel.gap;
import static com.pbolduc.mapcompanion.MapCompanionPanel.left;
import static com.pbolduc.mapcompanion.MapCompanionPanel.OPEN_WEBSITE;
import static com.pbolduc.mapcompanion.MapCompanionPanel.row;
import static com.pbolduc.mapcompanion.MapCompanionPanel.sectionHead;
import static com.pbolduc.mapcompanion.MapCompanionPanel.setWrapped;
import static com.pbolduc.mapcompanion.MapCompanionPanel.slot;
import static com.pbolduc.mapcompanion.MapCompanionPanel.smallButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.syncRow;

/**
 * Now, on Home: the destination the map sent, with its route steps and Follow, Reroute and
 * Dismiss, or Route while it has no way yet; where you died; the clue being carried; and Quest
 * Helper's step, each with its Route. Meetups are the Group tab's, every one of the week with its
 * answers.
 *
 * <p>What is happening now: what the map asked for, and where you died. Each is a card that is
 * hidden while there is nothing in it, and the heading goes with the last of them — see
 * {@link #refresh(boolean, boolean)}.
 *
 * <p>A class of its own because these cards answer one question, what is happening, each from state
 * it reads afresh at every refresh, and nothing in them is about the connection or Settings.
 * {@link MapCompanionPanel} places the column under the search, calls
 * {@link #refresh(boolean, boolean)} with the connection it has just read, and asks {@link #hotkey}
 * for the destination card's buttons.
 */
final class NowCards extends JPanel
{
	/** The clue scroll's sprite beside the card's title, at the size the inventory draws it. */
	private static final int CLUE_PICTURE = 32;
	/** The picture beside a destination's name, at the size a route step's is drawn, and its gap. */
	private static final int DESTINATION_PICTURE = RouteStepsView.PICTURE;
	private static final int DESTINATION_PICTURE_GAP = 5;

	private final MapCompanionConfig config;
	private final DestinationState destinationState;
	private final DeathWatcher deathWatcher;
	private final DestinationMarker destinationMarker;
	/** The sidebar's refresh, run after a press has changed what it should say. */
	private final Runnable refresh;
	private final Supplier<String> mapUrl;

	private final JLabel destinationName = sized(12f);
	/**
	 * What the destination leads to, drawn by the row that asked for it: the clue's scroll, the
	 * search row's sprite or icon, the friend's disc. A slot of fixed size beside the name, so the
	 * name does not move when the client's cache answers. See {@link RoutePress#picture}.
	 */
	private final JLabel destinationPicture = new JLabel();
	@Nullable
	private RoutePress.Picture shownPicture;
	private final JLabel destinationDetail = detail();
	private final JPanel destinationActions = row(ColorScheme.DARKER_GRAY_COLOR);
	private List<JButton> shownDestinationActions = new ArrayList<>();
	/*
	 * The death in the same ink and size as the destination above it, with its own
	 * action, rather than a line of 11px red. Red here says "error", and nothing has gone wrong
	 * with the plugin; the map's panel keeps red for refusals and prints this line plainly too.
	 */
	private final JLabel deathLabel = sized(12f);
	/**
	 * The gravestone's time left, by the game's own timer ({@link DeathWatcher#gravestone}), with a
	 * bar full at the first reading and draining, as RuneLite's Time Tracking draws one. In the card's
	 * own ink and the bar's in-progress orange, never red: nothing has gone wrong with the plugin.
	 */
	private final JLabel graveLabel = detail();
	private final ThinProgressBar graveBar = new ThinProgressBar();
	private final JPanel grave = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	/**
	 * The map at the tile where you died, which is the question the line above it raises.
	 *
	 * <p>Offered whether or not the death spot is shared, because it does not share it: the tile goes
	 * into the address of a page opened in this reader's own browser, and only when they press this.
	 * With sharing on, the group's map has the tile already and this is simply the short way to it.
	 */
	private final JButton deathMapButton = smallButton(OPEN_WEBSITE);
	/**
	 * The way back from where you stand. With the death spot not shared, its tile leaves this client
	 * only in this request, once per press, and the map plans with it and keeps nothing.
	 */
	private final JButton deathRouteButton = smallButton("Route back");
	private final JLabel deathSaid = new JLabel();
	/* The card's width, as Dismiss is in the card above: the same shape for "act on this card". */
	private final JPanel deathActions = row(ColorScheme.DARKER_GRAY_COLOR, deathMapButton);
	private List<JButton> shownDeathActions = new ArrayList<>();
	private final JPanel clueActions = row(ColorScheme.DARKER_GRAY_COLOR);
	/*
	 * The clue being carried: its tier, its step in a few words, and Route when the map can place the
	 * step — a coordinate's dig spot, a map's picture, or words. RuneLite's clue plugin draws the tile
	 * and the arrow already; what this adds is which teleport.
	 */
	private final JLabel clueTitle = sized(12f);
	private final JLabel clueDetail = detail();
	private final JButton clueRouteButton = smallButton("Route");
	private final JLabel clueSaid = new JLabel();
	private final JComponent clueSlot;
	/*
	 * Quest Helper's step, heard as it tells Shortest Path ({@link ShortestPath#questStep}), beside the
	 * world map's quest-start star. Quest Helper draws the step itself; this adds the map's way there.
	 */
	private final JLabel questTitle = sized(12f);
	private final JButton questRouteButton = smallButton("Route");
	private final JLabel questSaid = new JLabel();
	private final JComponent questSlot;
	/** Whether the star was asked for: once, when the card first shows. */
	private boolean questPictured;
	private final JComponent destinationSlot;
	private final JComponent deathSlot;
	/** Route, pressed beside anything in the sidebar; see {@link RoutePress}. */
	private final RoutePress routePress;
	private final FarmRunRoute farmRunRoute;
	private final ClueReader clueReader;
	private final GameSprites sprites;
	/** The scroll whose sprite the clue card shows, so a refresh asks for it once rather than every second. */
	@Nullable
	private Integer shownClueScroll;

	/** Hide the destination on this client. Does not tell the relay; see {@link DestinationState}. */
	private final JButton dismissButton = smallButton("Dismiss");
	/** The way there, step by step, when the map sent one. */
	private final RouteStepsView routeSteps;
	private final DestinationRoute destinationRoute;
	/** Ask the map for the way again from where the player stands. See {@link RoutePress#reroute}. */
	private final JButton rerouteButton = smallButton("Reroute");
	/**
	 * Follow this route, or pause it: the step in the box over the game, and Shortest Path's walks
	 * where it is running. It says Pause while following, as a run's card and the key's name call the
	 * same act. The only door to Shortest Path besides Reroute, so a destination that merely arrives
	 * never draws on the player's own path.
	 */
	private final JButton followButton = smallButton("Follow");
	private final ShortestPath shortestPath;
	/**
	 * The way to a destination that came without one: a star pinned on Stars, a spot Slayer pinned, a
	 * place the map sent bare. It asks by the destination's own tile, as a star's Route does, from the
	 * cell Reroute takes once there are steps, so the card that shows a place can always lead there.
	 */
	private final JButton routeButton = smallButton("Route");
	private final JLabel destinationSaid = new JLabel();
	/** Which Route button a press came from, so its answer is said under this card. */
	static final String DESTINATION = "destination";
	/* Each Route's own tooltip, which the map's wait stands in for while it greys the button; see MapWait#tip. */
	private static final String CLUE_ROUTE_TIP = Words.of("now.clueRouteTip");
	private static final String REROUTE_TIP = Words.of("now.rerouteTip");
	private static final String ROUTE_TIP = Words.of("now.routeTip");

	NowCards(MapCompanionConfig config, DestinationState destinationState, DeathWatcher deathWatcher,
		DestinationMarker destinationMarker, DestinationRoute destinationRoute,
		ShortestPath shortestPath, RoutePress routePress, ClueReader clueReader, FarmRunRoute farmRunRoute,
		GameSprites sprites, Runnable refresh, Supplier<String> mapUrl)
	{
		this.config = config;
		this.destinationState = destinationState;
		this.deathWatcher = deathWatcher;
		this.destinationMarker = destinationMarker;
		this.destinationRoute = destinationRoute;
		this.shortestPath = shortestPath;
		this.routePress = routePress;
		this.farmRunRoute = farmRunRoute;
		this.clueReader = clueReader;
		this.sprites = sprites;
		this.refresh = refresh;
		this.mapUrl = mapUrl;
		this.routeSteps = new RouteStepsView(way ->
		{
			destinationRoute.choose(way);
			refresh.run();
		}, sprites);
		MapCompanionPanel.column(this, ColorScheme.DARK_GRAY_COLOR);

		/*
		 * Now: a destination with its dismiss, a death, a clue. "From the map" named only the first —
		 * the death and the clue are this client's own observations — so the heading says when rather
		 * than where from, which is true of all of them.
		 */
		add(sectionHead("Now"));
		add(gap(3, ColorScheme.DARK_GRAY_COLOR));
		destinationPicture.setName("now.destination.picture");
		destinationPicture.setHorizontalAlignment(SwingConstants.CENTER);
		destinationPicture.setVerticalAlignment(SwingConstants.TOP);
		Dimension square = new Dimension(DESTINATION_PICTURE, DESTINATION_PICTURE);
		destinationPicture.setPreferredSize(square);
		destinationPicture.setMinimumSize(square);
		destinationPicture.setVisible(false);
		destinationSlot = nowCard(destinationActions, destinationSaid, MapCompanionPanel.bar(DESTINATION_PICTURE_GAP,
			ColorScheme.DARKER_GRAY_COLOR, destinationPicture, destinationName, null), destinationDetail, routeSteps);
		graveLabel.setName("now.death.grave");
		grave.add(graveLabel);
		grave.add(gap(3, ColorScheme.DARKER_GRAY_COLOR));
		graveBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		grave.add(left(graveBar));
		grave.setVisible(false);
		deathSlot = nowCard(deathActions, deathSaid, deathLabel, grave);
		clueActions.add(clueRouteButton);
		clueSlot = nowCard(clueActions, clueSaid, clueTitle, clueDetail);
		questTitle.setText(Words.of("now.questStep"));
		questTitle.setIconTextGap(6);
		questSlot = nowCard(row(ColorScheme.DARKER_GRAY_COLOR, questRouteButton), questSaid, questTitle);
		questRouteButton.setName("now.quest.route");
		questRouteButton.addActionListener(e ->
		{
			WorldPoint step = shortestPath.questStep();
			if (step != null)
			{
				/* Each step is its own source, so Reroute is offered only while Quest Helper is still on it. */
				routePress.press(RoutePress.Target.tile("quest" + step, step, questTitle.getText(), null, null)
					.showing((label, box) -> sprites.sprite(label, SpriteID.Mapfunction.QUEST_START, 15)));
			}
			refresh.run();
		});
		add(gap(7, ColorScheme.DARK_GRAY_COLOR));

		deathMapButton.addActionListener(e -> showDeathOnMap());
		deathRouteButton.addActionListener(e -> routeToDeath());
		clueRouteButton.addActionListener(e -> routeToClue());
		deathRouteButton.setName("now.death.route");
		deathSaid.setName("now.death.said");
		clueRouteButton.setName("now.clue.route");
		clueRouteButton.setToolTipText(CLUE_ROUTE_TIP);
		rerouteButton.setToolTipText(REROUTE_TIP);
		rerouteButton.addActionListener(e ->
		{
			routePress.reroute();
			refresh.run();
		});
		routeButton.setToolTipText(ROUTE_TIP);
		routeButton.addActionListener(e -> routeToDestination());
		followButton.addActionListener(e ->
		{
			DestinationRoute.View route = destinationRoute.view();
			destinationRoute.follow(route == null || !route.following);
			refresh.run();
		});
		dismissButton.setToolTipText(Words.of("now.dismissTip"));
		dismissButton.addActionListener(e ->
		{
			destinationState.dismiss();
			destinationMarker.sync();
			refresh.run();
		});
	}

	/** The destination card's button for a press from the game, while the card shows it; else null. EDT. */
	@Nullable
	JButton actButton(MapCompanionPanel.Act act)
	{
		if (!destinationSlot.isVisible())
		{
			return null;
		}
		JButton own = act == MapCompanionPanel.Act.REROUTE ? rerouteButton : act == MapCompanionPanel.Act.FOLLOW ? followButton : dismissButton;
		return shownDestinationActions.contains(own) ? own : null;
	}

	/**
	 * What Follow does here, or Pause while following: the step in the box over the game, Shortest
	 * Path's walks, or both, as far as each is on. Pause's words are a run's own, and still say that
	 * the path Shortest Path drew goes with it.
	 *
	 * @param drawable Shortest Path is running and the setting lets it draw
	 * @param box the step on screen is on
	 */
	static String followTip(boolean following, boolean drawable, boolean box)
	{
		if (following)
		{
			return Words.of("now.unfollowTip") + (drawable ? Words.of("now.clearPath") : "");
		}
		return !drawable ? Words.of("now.boxTip") : box ? Words.of("now.boxAndPathTip") : Words.of("now.pathTip");
	}

	/**
	 * "You died 7m ago", and the floor when it was not the ground, which the map's own panel says
	 * too: a tile upstairs is a different walk from the one under it.
	 */
	static String deathLine(long msAgo, int plane)
	{
		long minutes = msAgo / 60_000L;
		return "You died " + (minutes < 1 ? "just now" : minutes + "m ago")
			   + (plane > 0 ? " on floor " + plane : "");
	}

	/** "Gravestone · 8m left": the line under the death, while the game says a stone stands. */
	static String graveLine(DeathWatcher.Stone stone)
	{
		return "Gravestone · " + stone.left();
	}

	/** The tile for the tooltip: "3222, 3218", with "(floor 1)" after it when it is upstairs. */
	static String deathTile(int x, int y, int plane)
	{
		return x + ", " + y + (plane > 0 ? " (floor " + plane + ")" : "");
	}

	/**
	 * The map's pinned-tile grammar carries the floor as a third coordinate. A death on an upper
	 * storey cannot be inferred from its x/y, and must not borrow the place or route below it.
	 */
	static String deathMapPath(int x, int y, int plane)
	{
		return "/map?p=" + x + "," + y + "," + plane;
	}

	/**
	 * A Route button and the line under it, from what its last press said: "Planning…" while the map
	 * answers, its refusal in the line, and pressable only when a press would be sent. While the
	 * destination on Home is the answer this card's last press made, it reads Reroute, as a friend's
	 * row does: pressed, it asks again from where you stand, where Route would offer as new the very
	 * card above it. A wait the press met counts down in the line, and greys the button with how long
	 * it has left in place of its own tooltip, {@code tip}.
	 */
	private void routeState(JButton button, JLabel line, String source, String label, boolean ready, String tip, long now)
	{
		RoutePress.Said said = routePress.said(source);
		boolean planning = said != null && said.planning;
		button.setText(planning ? "Planning…" : answeredHere(source) ? "Reroute" : label);
		button.setEnabled(ready && !planning && routePress.ready());
		MapWait.tip(button, tip, routePress.waitMs(now));
		String message = said == null ? null : said.message;
		line.setVisible(message != null);
		if (message != null)
		{
			setWrapped(line, routePress.words(said, now), CARD_TEXT_WIDTH);
			MapCompanionPanel.tone(line, said.refused());
		}
	}

	/** Whether the destination on Home is the answer the last press from {@code source} made. */
	private boolean answeredHere(String source)
	{
		RoutePress.Said said = routePress.said(source);
		return said != null && said.key != null && said.key.equals(DestinationRoute.keyOf(destinationState.getDestination()));
	}

	/**
	 * One card of Now, in its slot at the foot of the column: what it is about, a gap, its buttons and the
	 * line a press is answered in. Each part is pinned to the card's left edge, as every row here is.
	 */
	private JComponent nowCard(JPanel actions, JLabel said, JComponent... above)
	{
		JPanel card = card();
		for (JComponent part : above)
		{
			card.add(left(part));
		}
		card.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		card.add(left(actions));
		card.add(said(said));
		JComponent slot = slot(card);
		add(slot);
		return slot;
	}

	/** A label in one size and the look and feel's own ink: a card's first line. */
	private static JLabel sized(float size)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		return label;
	}

	/** The line under a card's first, small and grey. */
	private static JLabel detail()
	{
		JLabel label = sized(11f);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	/** The line a press's refusal is said in, under the buttons it came from, in the refusal pink. */
	static JLabel said(JLabel line)
	{
		line.setFont(line.getFont().deriveFont(11f));
		line.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		line.setBorder(new EmptyBorder(4, 0, 0, 0));
		line.setVisible(false);
		return line;
	}

	/** "Hard clue", from the tier the scroll's own name gives. */
	static String clueTitle(@Nullable String tier)
	{
		if (tier == null || tier.isEmpty())
		{
			return "Clue scroll";
		}
		return Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + " clue";
	}

	/**
	 * The step in a few words: where a Route goes, or the scroll's own words cut to two lines. A step
	 * that is words is routed by those words, so the card shows them and Route under them. A map
	 * above beginner is known by its scroll before and after it is opened, so the card says Route
	 * finds the place rather than asking for a scroll the player has just read.
	 */
	static String clueDetail(CompanionSnapshot.Clue clue)
	{
		if (clue.coordTile != null)
		{
			return "Coordinate · dig at " + clue.coordTile.x + ", " + clue.coordTile.y;
		}
		if (clue.mapTile != null)
		{
			return "Beginner map · dig where it shows";
		}
		if (clue.text != null && !clue.text.isEmpty())
		{
			return clue.text.length() <= 80 ? clue.text : clue.text.substring(0, 79).trim() + "…";
		}
		if (ClueReader.mapScroll(clue))
		{
			return "Map · Route finds where it shows";
		}
		return "Read the scroll to see its step";
	}

	/**
	 * Route to the clue, read again at the press: the scroll may have changed since the paint. A step
	 * with a tile goes as that tile; one that is words, or a map known by its scroll, goes as the
	 * clue, and the map names its place.
	 */
	private void routeToClue()
	{
		CompanionSnapshot.Clue clue = clueReader.current();
		CompanionSnapshot.Tile tile = ClueReader.routeTile(clue);
		if (tile != null)
		{
			routePress.press(RoutePress.Target.tile("clue", new WorldPoint(tile.x, tile.y, tile.plane),
				clueTitle(clue.tier), null, clue.coordTile != null ? "Coordinate" : "Beginner map").showing(scroll(clue)));
		}
		else if (ClueReader.routesByClue(clue))
		{
			routePress.press(RoutePress.Target.clue("clue", clue, clueTitle(clue.tier), clueTitle(clue.tier))
				.showing(scroll(clue)));
		}
		refresh.run();
	}

	/** The clue's scroll, as its own card draws it, for the destination's card beside the answer. */
	@Nullable
	private RoutePress.Picture scroll(CompanionSnapshot.Clue clue)
	{
		Integer itemId = clue.itemId != null && clue.itemId > 0 ? clue.itemId : null;
		return itemId == null ? null : (label, box) -> sprites.item(label, itemId, box);
	}

	/**
	 * The way back to where you died, read again at the press, as Open website reads it. The answer's
	 * card carries no line of its own: the death card under it counts the age on every refresh, and
	 * an age written at the press would sit above it, older by the minute.
	 */
	private void routeToDeath()
	{
		long now = System.currentTimeMillis();
		CompanionSnapshot.Death death = deathWatcher.snapshot(now);
		if (death != null)
		{
			routePress.press(RoutePress.Target.tile("death", new WorldPoint(death.tile.x, death.tile.y, death.tile.plane),
				"Where you died", null, null));
		}
		refresh.run();
	}

	/**
	 * The way to the destination on the card, read again at the press: by its tile, under its own
	 * name, world and note, so the answer is the same place with its way, as a star's Route makes one.
	 */
	private void routeToDestination()
	{
		RelayResponse.Destination destination = destinationState.getDestination();
		if (destination != null && destination.route == null)
		{
			routePress.press(RoutePress.Target.tile(DESTINATION, new WorldPoint(destination.x, destination.y, destination.plane),
				destination.name, destination.world, destination.note));
		}
		refresh.run();
	}

	/**
	 * Open that path in the reader's browser.
	 *
	 * <p>The death is read again at the press rather than taken from the label, because it can age
	 * out between a paint and a click, and a link to a tile the sidebar has just stopped showing
	 * would be the wrong answer delivered confidently.
	 */
	private void showDeathOnMap()
	{
		CompanionSnapshot.Death death = deathWatcher.snapshot(System.currentTimeMillis());
		if (death != null)
		{
			LinkBrowser.browse(mapUrl.get() + deathMapPath(death.tile.x, death.tile.y, death.tile.plane));
		}
	}

	/** The five cards and the heading, from the connection the sidebar's refresh has just read. EDT. */
	void refresh(boolean paired, boolean ready)
	{
		refresh(paired, ready, true);
	}

	/**
	 * The same, knowing whether a character is logged in. With none, every Route here waits for one,
	 * greyed, its tooltip saying so, since a route is asked for as a member and a member is a
	 * character; Follow, Pause and Dismiss act here and stay.
	 */
	void refresh(boolean paired, boolean ready, boolean loggedIn)
	{
		boolean asks = ready && loggedIn;
		RelayResponse.Destination destination = destinationState.getDestination();
		/*
		 * Hidden rather than blanked. A label holding a space still takes a line, which is how
		 * the sidebar ended up with gaps where nothing was ever going to be written — and the
		 * same goes for a card, whose padding is a line's worth of nothing.
		 */
		boolean farmDestination = farmRunRoute.owns(destination);
		destinationSlot.setVisible(destination != null && !farmDestination);
		if (destination != null)
		{
			/* Drawn once per press's answer rather than every second, and gone with another destination. */
			RoutePress.Picture picture = routePress.picture(destination);
			if (picture != shownPicture)
			{
				shownPicture = picture;
				destinationPicture.setIcon(null);
				if (picture != null)
				{
					picture.draw(destinationPicture, DESTINATION_PICTURE);
				}
			}
			destinationPicture.setVisible(picture != null);
			setWrapped(destinationName, destination.name,
				picture != null ? CARD_TEXT_WIDTH - DESTINATION_PICTURE - DESTINATION_PICTURE_GAP : CARD_TEXT_WIDTH);
			String detail = destinationDetail(destination);
			destinationDetail.setVisible(!detail.isEmpty());
			setWrapped(destinationDetail, detail, CARD_TEXT_WIDTH);
		}
		/* Only the route of the destination on the card: for a tick after a change they can differ. */
		DestinationRoute.View route = destinationRoute.view();
		boolean steps = destination != null && route != null && route.hasRoute()
			&& route.key.equals(DestinationRoute.keyOf(destination));
		boolean drawable = steps && config.drawWithShortestPath() && shortestPath.installed() && !route.arrived();
		/*
		 * Following puts the step in the box over the game as well as drawing in Shortest Path, so
		 * Follow is offered for either. A route pressed here is followed from the start, and without
		 * Shortest Path the box it put up had no button to take it down.
		 */
		boolean box = config.showStepOnScreen();
		boolean followable = steps && !route.arrived() && (drawable || box);
		routeSteps.show(steps ? route : null,
			steps ? RouteStepsView.shortestPathLine(route, drawable, shortestPath.drawing()) : null);
		List<JButton> destinationRow = new ArrayList<>();
		if (followable)
		{
			followButton.setText(route.following ? "Pause" : "Follow");
			followButton.setToolTipText(followTip(route.following, drawable, box));
			destinationRow.add(followButton);
		}
		long now = System.currentTimeMillis();
		if (steps)
		{
			destinationRow.add(rerouteButton);
			rerouteButton.setEnabled(asks && !route.rerouting && now >= route.nextRerouteMs);
			MapWait.tip(rerouteButton, loggedIn ? REROUTE_TIP : RoutePress.LOG_IN, route.nextRerouteMs - now);
		}
		/*
		 * No way yet, and none arriving with the destination itself for the tick before the route
		 * takes it: the card asks for one, from Reroute's cell.
		 */
		boolean wayless = destination != null && !steps && destination.route == null && ready;
		if (wayless)
		{
			destinationRow.add(routeButton);
			routeState(routeButton, destinationSaid, DESTINATION, "Route", loggedIn, loggedIn ? ROUTE_TIP : RoutePress.LOG_IN, now);
		}
		else
		{
			destinationSaid.setVisible(false);
		}
		destinationRow.add(dismissButton);
		shownDestinationActions = syncRow(destinationActions, destinationRow, shownDestinationActions);

		CompanionSnapshot.Death death = deathWatcher.snapshot(now);
		deathSlot.setVisible(death != null);
		if (death != null)
		{
			setWrapped(deathLabel, deathLine(death.msAgo, death.tile.plane), CARD_TEXT_WIDTH);
			/**
			 * The tile moves into the tooltip, as it did on the map's panel: a coordinate pair is
			 * a fact nobody can act on — there is nowhere in the game to type one — and the button
			 * under it does the one thing anybody wanted it for.
			 */
			deathLabel.setToolTipText(deathTile(death.tile.x, death.tile.y, death.tile.plane));
			DeathWatcher.Stone stone = deathWatcher.gravestone(now);
			grave.setVisible(stone != null);
			if (stone != null)
			{
				setWrapped(graveLabel, graveLine(stone), CARD_TEXT_WIDTH);
				graveBar.setMaximumValue(stone.ticksFull);
				graveBar.setValue(stone.ticksLeft);
			}
			deathMapButton.setToolTipText(config.shareDeath() ? Words.of("now.deathMapTip") : Words.of("now.deathMapUnsharedTip"));
			String routeBackTip = config.shareDeath() ? Words.of("now.routeBackTip") : Words.of("now.routeBackUnsharedTip");
			List<JButton> deathRow = new ArrayList<>();
			deathRow.add(deathMapButton);
			if (ready)
			{
				deathRow.add(deathRouteButton);
			}
			shownDeathActions = syncRow(deathActions, deathRow, shownDeathActions);
			routeState(deathRouteButton, deathSaid, "death", "Route back", asks, loggedIn ? routeBackTip : RoutePress.LOG_IN, now);
		}

		CompanionSnapshot.Clue clue = clueReader.current();
		clueSlot.setVisible(clue != null && paired);
		if (clue != null && paired)
		{
			/* The scroll's own sprite beside its tier, as the game draws it in the inventory. */
			Integer scroll = clue.itemId != null && clue.itemId > 0 ? clue.itemId : null;
			if (!java.util.Objects.equals(scroll, shownClueScroll))
			{
				shownClueScroll = scroll;
				clueTitle.setIcon(null);
				clueTitle.setIconTextGap(6);
				if (scroll != null)
				{
					sprites.item(clueTitle, scroll, CLUE_PICTURE);
				}
			}
			setWrapped(clueTitle, clueTitle(clue.tier), scroll != null ? CARD_TEXT_WIDTH - CLUE_PICTURE - 6 : CARD_TEXT_WIDTH);
			setWrapped(clueDetail, clueDetail(clue), CARD_TEXT_WIDTH);
			boolean routable = ClueReader.routeTile(clue) != null || ClueReader.routesByClue(clue);
			clueActions.setVisible(routable);
			routeState(clueRouteButton, clueSaid, "clue", "Route", asks && routable, loggedIn ? CLUE_ROUTE_TIP : RoutePress.LOG_IN, now);
		}
		WorldPoint step = shortestPath == null ? null : shortestPath.questStep();
		/*
		 * Not while the destination above is the answer to this step: that card already says "Quest
		 * Helper step" and offers Reroute. Quest Helper's next step, or the destination dismissed,
		 * brings it back.
		 */
		questSlot.setVisible(step != null && paired && !answeredHere("quest" + step));
		if (questSlot.isVisible())
		{
			if (!questPictured)
			{
				questPictured = true;
				sprites.sprite(questTitle, SpriteID.Mapfunction.QUEST_START, 15);
			}
			routeState(questRouteButton, questSaid, "quest" + step, "Route", asks, loggedIn ? Words.of("now.questRouteTip") : RoutePress.LOG_IN, now);
		}
		/** The heading goes with the last card under it, rather than naming nothing. */
		setVisible((destination != null && !farmDestination) || death != null || (clue != null && paired) || questSlot.isVisible());
	}

	/**
	 * A destination's second line: which world, and why — its name is the line above.
	 *
	 * <p>The world is the half that matters for a star and the half a place name cannot carry —
	 * "Rimmington mine" is on every world, and the one somebody scouted is not. Printed, never
	 * acted on: hopping is left to the game's own world switcher.
	 */
	private static String destinationDetail(RelayResponse.Destination destination)
	{
		StringBuilder sb = new StringBuilder();
		if (destination.world != null)
		{
			sb.append("W").append(destination.world);
		}
		if (destination.note != null && !destination.note.isEmpty())
		{
			if (sb.length() > 0)
			{
				sb.append(" · ");
			}
			sb.append(destination.note);
		}
		return sb.toString();
	}

	/**
	 * What the chat box says after "Destination:" when a place arrives from the map: the place in the
	 * tile label's words, its world while the player is on another, and where Follow is, with the
	 * player's own key for it once they have chosen one. A place the player sent this RuneLite
	 * themselves ({@code yours}) is already being followed, so the line says that instead.
	 */
	static String arrivalLine(RelayResponse.Destination destination, int world, Keybind follow, boolean yours)
	{
		String name = DestinationSceneOverlay.name(destination.name, 40);
		String place = (name == null ? "A place" : name) + DestinationSceneOverlay.elsewhere(destination, world);
		if (yours)
		{
			return place + Words.of("now.arrivalYours") + ".";
		}
		return place + Words.of("now.arrival")
			+ (Keybind.NOT_SET.equals(follow) ? "" : Words.of("now.arrivalKey") + " " + follow) + ".";
	}
}
