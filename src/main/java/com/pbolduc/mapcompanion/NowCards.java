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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.card;
import static com.pbolduc.mapcompanion.MapCompanionPanel.CARD_TEXT_WIDTH;
import static com.pbolduc.mapcompanion.MapCompanionPanel.gap;
import static com.pbolduc.mapcompanion.MapCompanionPanel.left;
import static com.pbolduc.mapcompanion.MapCompanionPanel.meetupWhen;
import static com.pbolduc.mapcompanion.MapCompanionPanel.OPEN_WEBSITE;
import static com.pbolduc.mapcompanion.MapCompanionPanel.row;
import static com.pbolduc.mapcompanion.MapCompanionPanel.sectionHead;
import static com.pbolduc.mapcompanion.MapCompanionPanel.setWrapped;
import static com.pbolduc.mapcompanion.MapCompanionPanel.slot;
import static com.pbolduc.mapcompanion.MapCompanionPanel.smallButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.syncRow;

/**
 * Now, on Home: the destination the map sent, with its route steps and Follow, Reroute and
 * Dismiss; the next meetup, from a map that sends only the one; where you died; and the clue being
 * carried, each with its Route.
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
	private static final long serialVersionUID = 1L;
	/** The clue scroll's sprite beside the card's title, at the size the inventory draws it. */
	private static final int CLUE_PICTURE = 32;

	private final MapCompanionConfig config;
	private final DestinationState destinationState;
	private final DeathWatcher deathWatcher;
	private final DestinationMarker destinationMarker;
	/** The sidebar's refresh, run after a press has changed what it should say. */
	private final Runnable refresh;
	private final Supplier<String> mapUrl;

	private final JLabel destinationName = new JLabel();
	private final JLabel destinationDetail = new JLabel();
	private final JPanel destinationActions = row(ColorScheme.DARKER_GRAY_COLOR);
	private List<JButton> shownDestinationActions = new ArrayList<>();
	private final JLabel eventWhen = new JLabel();
	private final JLabel eventTitle = new JLabel();
	private final JLabel deathLabel = new JLabel();
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
	private final JPanel deathActions = row(ColorScheme.DARKER_GRAY_COLOR);
	private List<JButton> shownDeathActions = new ArrayList<>();
	private final JPanel clueActions = row(ColorScheme.DARKER_GRAY_COLOR);
	/*
	 * The clue being carried: its tier, its step in a few words, and Route when the map can place the
	 * step — a coordinate's dig spot, a map's picture, or words. RuneLite's clue plugin draws the tile
	 * and the arrow already; what this adds is which teleport.
	 */
	private final JLabel clueTitle = new JLabel();
	private final JLabel clueDetail = new JLabel();
	private final JButton clueRouteButton = smallButton("Route");
	private final JLabel clueSaid = new JLabel();
	private final JComponent clueSlot;
	private final JButton eventRouteButton = smallButton("Route");
	private final JLabel eventSaid = new JLabel();
	private final JComponent destinationSlot;
	private final JComponent eventSlot;
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
	/** Ask the map for the way again from where the player stands. See {@link RouteClient}. */
	private final JButton rerouteButton = smallButton("Reroute");
	/**
	 * Follow this route with Shortest Path, or stop. The only door to Shortest Path besides Reroute,
	 * so a destination that merely arrives never draws on the player's own path.
	 */
	private final JButton followButton = smallButton("Follow");
	private final ShortestPath shortestPath;

	NowCards(MapCompanionConfig config, DestinationState destinationState, DeathWatcher deathWatcher,
		DestinationMarker destinationMarker, DestinationRoute destinationRoute, RouteClient routeClient,
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
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		left(this);

		/*
		 * Now: a destination with its dismiss, a meetup, a death. "From the map" named two of
		 * the three — the death is this client's own observation — so the heading says when rather
		 * than where from, which is true of all of them.
		 */
		add(sectionHead("Now"));
		add(gap(3, ColorScheme.DARK_GRAY_COLOR));
		JPanel destination = card();
		destinationName.setFont(destinationName.getFont().deriveFont(12f));
		destination.add(left(destinationName));
		destinationDetail.setFont(destinationDetail.getFont().deriveFont(11f));
		destinationDetail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		destination.add(left(destinationDetail));
		destination.add(routeSteps);
		destination.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		destination.add(left(destinationActions));
		destinationSlot = slot(destination);
		add(destinationSlot);
		JPanel meetup = card();
		eventWhen.setFont(eventWhen.getFont().deriveFont(12f));
		meetup.add(left(eventWhen));
		eventTitle.setFont(eventTitle.getFont().deriveFont(11f));
		eventTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		meetup.add(left(eventTitle));
		meetup.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		JPanel eventActions = row(ColorScheme.DARKER_GRAY_COLOR);
		eventActions.add(eventRouteButton);
		meetup.add(left(eventActions));
		meetup.add(left(said(eventSaid)));
		eventSlot = slot(meetup);
		add(eventSlot);
		/*
		 * The death in the same ink and size as the destination and the meetup above it, with its own
		 * action, rather than a line of 11px red. Red here says "error", and nothing has gone wrong
		 * with the plugin; the map's panel keeps red for refusals and prints this line plainly too.
		 */
		JPanel death = card();
		deathLabel.setFont(deathLabel.getFont().deriveFont(12f));
		death.add(left(deathLabel));
		death.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		/* The card's width, as Dismiss is in the card above: the same shape for "act on this card". */
		deathActions.add(deathMapButton);
		death.add(left(deathActions));
		death.add(left(said(deathSaid)));
		deathSlot = slot(death);
		add(deathSlot);
		JPanel clue = card();
		clueTitle.setFont(clueTitle.getFont().deriveFont(12f));
		clue.add(left(clueTitle));
		clueDetail.setFont(clueDetail.getFont().deriveFont(11f));
		clueDetail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clue.add(left(clueDetail));
		clue.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		clueActions.add(clueRouteButton);
		clue.add(left(clueActions));
		clue.add(left(said(clueSaid)));
		clueSlot = slot(clue);
		add(clueSlot);
		add(gap(7, ColorScheme.DARK_GRAY_COLOR));

		deathMapButton.addActionListener(e -> showDeathOnMap());
		deathRouteButton.addActionListener(e -> routeToDeath());
		clueRouteButton.addActionListener(e -> routeToClue());
		eventRouteButton.addActionListener(e -> routeToMeetup());
		deathRouteButton.setName("now.death.route");
		clueRouteButton.setName("now.clue.route");
		eventRouteButton.setName("now.meetup.route");
		clueRouteButton.setToolTipText("Plan the way to this step from where you stand. Its tile, or for a step that is words "
			+ "or a map the clue itself, go to the map in this request only, whether or not you share your clue; the map "
			+ "plans with them and does not keep them.");
		eventRouteButton.setToolTipText("Plan the way to this meetup from where you stand. Nobody is told you are coming.");
		rerouteButton.setToolTipText("Plan the way again from where you stand. Sends your tile to the map once; "
									 + "the map plans with it and does not keep it.");
		rerouteButton.addActionListener(e ->
		{
			routeClient.reroute();
			refresh.run();
		});
		followButton.addActionListener(e ->
		{
			DestinationRoute.View route = destinationRoute.view();
			destinationRoute.follow(route == null || !route.following);
			refresh.run();
		});
		dismissButton.setToolTipText("Hide this destination on this client. The map is not told.");
		dismissButton.addActionListener(e ->
		{
			destinationState.dismiss();
			destinationMarker.sync();
			refresh.run();
		});
	}

	/** The destination card's Reroute or Follow, for a hotkey, while the card shows it; else null. EDT. */
	@Nullable
	JButton hotkey(boolean rerouting)
	{
		if (!destinationSlot.isVisible())
		{
			return null;
		}
		JButton own = rerouting ? rerouteButton : followButton;
		return shownDestinationActions.contains(own) ? own : null;
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
	 * card above it.
	 */
	private void routeState(JButton button, JLabel line, String source, String label, boolean ready)
	{
		RoutePress.Said said = routePress.said(source);
		boolean planning = said != null && said.planning;
		boolean routedHere = said != null && said.key != null
			&& said.key.equals(DestinationRoute.keyOf(destinationState.getDestination()));
		button.setText(planning ? "Planning…" : routedHere ? "Reroute" : label);
		button.setEnabled(ready && !planning && routePress.ready());
		String message = said == null ? null : said.message;
		line.setVisible(message != null);
		if (message != null)
		{
			setWrapped(line, message, CARD_TEXT_WIDTH);
		}
	}

	/** The line a press's refusal is said in, under the buttons it came from. */
	private static JLabel said(JLabel line)
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
				clueTitle(clue.tier), null, clue.coordTile != null ? "Coordinate" : "Beginner map"));
		}
		else if (ClueReader.routesByClue(clue))
		{
			routePress.press(RoutePress.Target.clue("clue", clue, clueTitle(clue.tier), clueTitle(clue.tier)));
		}
		refresh.run();
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

	/** The way to the meetup, with its world and when it starts in the card's words. Nobody is told. */
	private void routeToMeetup()
	{
		RelayResponse.Event event = destinationState.getEvent();
		if (event != null)
		{
			routePress.press(RoutePress.Target.tile("meetup", new WorldPoint(event.x, event.y, event.plane),
				event.title == null ? "Meetup" : event.title, event.world,
				meetupWhen(event.startsAtMs, System.currentTimeMillis(), ZoneId.systemDefault(),
					Locale.getDefault(Locale.Category.FORMAT), null)));
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

	/** The four cards and the heading, from the connection the sidebar's refresh has just read. EDT. */
	void refresh(boolean paired, boolean ready)
	{
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
			setWrapped(destinationName, destination.name, CARD_TEXT_WIDTH);
			String detail = destinationDetail(destination);
			destinationDetail.setVisible(!detail.isEmpty());
			setWrapped(destinationDetail, detail, CARD_TEXT_WIDTH);
		}
		/* Only the route of the destination on the card: for a tick after a change they can differ. */
		DestinationRoute.View route = destinationRoute.view();
		boolean steps = destination != null && route != null && route.hasRoute()
			&& route.key.equals(DestinationRoute.keyOf(destination));
		boolean drawable = steps && config.drawWithShortestPath() && shortestPath.installed() && !route.arrived();
		routeSteps.show(steps ? route : null,
			steps ? RouteStepsView.shortestPathLine(route, drawable, shortestPath.drawing()) : null);
		List<JButton> destinationRow = new ArrayList<>();
		if (drawable)
		{
			followButton.setText(route.following ? "Stop" : "Follow");
			followButton.setToolTipText(route.following
				? "Stop drawing this route with Shortest Path, and clear the path it drew"
				: "Have Shortest Path draw each walking step of this route. It is sent nothing for a teleport or a ride.");
			destinationRow.add(followButton);
		}
		if (steps)
		{
			destinationRow.add(rerouteButton);
			rerouteButton.setEnabled(ready && !route.rerouting && System.currentTimeMillis() >= route.nextRerouteMs);
		}
		destinationRow.add(dismissButton);
		shownDestinationActions = syncRow(destinationActions, destinationRow, shownDestinationActions);

		/*
		 * The one meetup under Now is the map's older word for the next meetup. A map that sends the
		 * week's meetups shows them all under Group instead, with their answers, so Now leaves it out.
		 */
		RelayResponse.Event event = destinationState.getEvents() == null ? destinationState.getEvent() : null;
		eventSlot.setVisible(event != null);
		long now = System.currentTimeMillis();
		if (event != null)
		{
			setWrapped(eventWhen, meetupWhen(event.startsAtMs, now, ZoneId.systemDefault(),
											 Locale.getDefault(Locale.Category.FORMAT), event.world), CARD_TEXT_WIDTH);
			setWrapped(eventTitle, event.title, CARD_TEXT_WIDTH);
			routeState(eventRouteButton, eventSaid, "meetup", "Route", ready);
		}

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
			deathMapButton.setToolTipText(config.shareDeath()
										  ? "Open the map at the tile where you died"
										  : "Open the map at the tile where you died. Sharing it is off, so it leaves this "
										  + "client only in the address of the page this opens, and only when you press it.");
			deathRouteButton.setToolTipText(config.shareDeath()
				? "Plan the way back from where you stand"
				: "Plan the way back from where you stand. Sharing it is off, so the tile goes to the map in "
				+ "this request only, and the map plans with it and does not keep it.");
			List<JButton> deathRow = new ArrayList<>();
			deathRow.add(deathMapButton);
			if (ready)
			{
				deathRow.add(deathRouteButton);
			}
			shownDeathActions = syncRow(deathActions, deathRow, shownDeathActions);
			routeState(deathRouteButton, deathSaid, "death", "Route back", ready);
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
			routeState(clueRouteButton, clueSaid, "clue", "Route", ready && routable);
		}
		/** The heading goes with the last card under it, rather than naming nothing. */
		setVisible((destination != null && !farmDestination) || event != null || death != null || (clue != null && paired));
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
}
