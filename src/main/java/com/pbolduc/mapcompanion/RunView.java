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

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import static com.pbolduc.mapcompanion.MapCompanionPanel.label;

/**
 * One received run as the sidebar follows it: its stops, the current leg's steps, and Follow,
 * Reroute and End. Shared by the two cards that start a run, each showing only its own kind.
 *
 * <p>The farm run card shows every kind but {@code port-tasks}; the Port tasks card shows that one.
 * There is one run at a time — the map keeps one a member — so a Start on either card replaces the
 * run held. Neither offers one beside a run still being walked, planned or ended, its own or the
 * other card's, and the card that did not start a run simply stops showing it. What a stop says
 * under its name is its own evidence: a farm stop's way there and the Time Tracking note; a
 * port-task stop's way and one line a task, the words the map ticks it by. A stop behind the
 * player folds to one line with its Done or Visited, and the leg being walked sits under the stop
 * it leads to.
 *
 * <p>The list rebuilds when its content changes, not on the sidebar's one-second refresh, so an
 * unchanged frame cannot swallow a press or close the bank checklist under the pointer. EDT only.
 */
final class RunView extends JPanel
{
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	/** A stop's picture, at the size This leg draws a step's. */
	private static final int PICTURE = 16;
	/** The next stop's number, in its world-map pin's colour. */
	private static final String AMBER_HTML = String.format("#%02x%02x%02x",
		MapPin.AMBER.getRed(), MapPin.AMBER.getGreen(), MapPin.AMBER.getBlue());
	static final String CHANGED = "Your port tasks have changed since this run was planned. Reroute plans from what you hold now.";
	/** A birdhouse stop not yet done: what will tick it, in the player's words rather than the evidence's. */
	static final String BIRDHOUSE_PENDING = "Ticks when Time Tracking sees it seeded";

	private final FarmRunState state;
	private final FarmRunClient client;
	private final FarmRunRoute follower;
	private final DestinationRoute routes;
	private final ShortestPath shortest;
	private final GameSprites sprites;
	private final MapCompanionConfig config;
	/** True on the Port tasks card: it shows port-task runs, and the farm run card every other. */
	private final boolean port;
	private final JLabel title = label(12f, Color.WHITE);
	private final JLabel progress = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel changed = label(11f, MapPin.AMBER);
	private final JButton before = MapCompanionPanel.linkButton("▸ Before you leave");
	private final JPanel bring = column();
	private final JPanel stops = column();
	/*
	 * Small buttons, as the destination card's are: these act, and a link is a fold's own opener. A
	 * third of the card is too narrow for "Pause route", so the button says Pause and its tip the rest.
	 */
	private final JButton follow = MapCompanionPanel.smallButton("Follow");
	private final JButton reroute = MapCompanionPanel.smallButton("Reroute");
	private final JButton end = MapCompanionPanel.smallButton("End run");
	private final JPanel actions = new JPanel(new GridLayout(1, 0, 4, 0));
	private List<JButton> shownActions = Collections.emptyList();
	/** A run shown, or one being planned or ended: the card's own Start would replace it. EDT. */
	private boolean busy;
	/** The same of the other card's run, with stops left: a Start here would replace that one. EDT. */
	private boolean otherBusy;
	/*
	 * A hairline and its own heading over the steps of the leg being walked. Without them the leg's
	 * Walk, Board, Sail and Dock read as more stops. Shown with the steps, under the leg's own stop.
	 * The heading says the leg's length, which the steps' way line said under it in the words of the
	 * stop's own line just above.
	 */
	private final JLabel legHead = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private final RouteStepsView steps;
	/* And one under them, so the leg's last numbered step does not run on into the next stop's number. */
	private final JLabel legEnd = new JLabel();
	/*
	 * Where the leg was, while another destination has paused the run: what it waits for and the way
	 * back. Without it a detour reads as the run's opposite, the destination above being followed while
	 * this card offers Follow.
	 */
	private final JLabel paused = label(11f, MapPin.AMBER);
	private final JLabel message = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private boolean bringOpen;
	/** The checklist's teleports the client can see are not carried, as of its last rebuild. */
	private int missing;
	private String shown;
	private String shownRun;

	RunView(boolean port, FarmRunState state, FarmRunClient client, FarmRunRoute follower, DestinationRoute routes,
			ShortestPath shortest, GameSprites sprites, MapCompanionConfig config)
	{
		this.port = port;
		this.steps = new RouteStepsView(ignored -> {}, sprites);
		this.state = state;
		this.client = client;
		this.follower = follower;
		this.routes = routes;
		this.shortest = shortest;
		this.sprites = sprites;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		MapCompanionPanel.left(this);
		setName(port ? "portTaskRun" : "farmRunView");
		add(MapCompanionPanel.left(title));
		add(MapCompanionPanel.left(progress));
		add(MapCompanionPanel.left(changed));
		add(before);
		add(bring);
		add(stops);
		/* The paused line, This leg and its steps go under the next stop's row: see showStops. */
		paused.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		paused.setVisible(false);
		MapCompanionPanel.left(paused);
		legHead.setText(legHeading(null));
		legHead.setFont(legHead.getFont().deriveFont(Font.BOLD));
		legHead.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(6, 0, 0, 0),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(5, 0, 1, 0))));
		legHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, legHead.getPreferredSize().height));
		legHead.setVisible(false);
		MapCompanionPanel.left(legHead);
		legEnd.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(6, 0, 0, 0),
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR)));
		legEnd.setMaximumSize(new Dimension(Integer.MAX_VALUE, legEnd.getPreferredSize().height));
		legEnd.setVisible(false);
		MapCompanionPanel.left(legEnd);
		actions.setOpaque(false);
		actions.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		add(MapCompanionPanel.left(actions));
		add(MapCompanionPanel.left(message));

		reroute.addActionListener(e -> client.reroute());
		end.addActionListener(e -> client.end());
		follow.addActionListener(e ->
		{
			FarmRunState.View view = state.view();
			if (view != null)
			{
				follower.follow(!view.following);
			}
		});
		before.addActionListener(e ->
		{
			bringOpen = !bringOpen;
			showBring();
		});
	}

	/** Whether a run is one this card shows. */
	boolean ours(RelayResponse.FarmRun run)
	{
		return port == FarmRunWire.PORT_TASKS.equals(run.kind);
	}

	/** Whether this card holds a run, or one being planned or ended, as of its last refresh. EDT. */
	boolean busy()
	{
		return busy;
	}

	/**
	 * Whether the other card holds a run with stops left, or one being planned or ended, as of this
	 * card's last refresh. A finished run holds nothing back: there is nothing left of it to lose. EDT.
	 */
	boolean otherBusy()
	{
		return otherBusy;
	}

	/**
	 * The button a hotkey presses here: Reroute, or Follow and Pause, while this card shows a run and
	 * no other route is being followed. During a detour — a search pick, Route here, a friend's
	 * destination followed — the keys belong to the route being walked, so the destination card's own
	 * buttons answer them and the paused run is left as it is; its Follow is still a click away. EDT.
	 */
	@Nullable
	JButton hotkey(boolean rerouting)
	{
		if (!shownActions.contains(follow))
		{
			return null;
		}
		DestinationRoute.View route = routes.view();
		if (route != null && route.following && !follower.ownsDestinationRoute(route))
		{
			return null;
		}
		return rerouting ? reroute : follow;
	}

	/**
	 * Called on the EDT by the card's refresh. True when this card has a run, a press or a message of
	 * its own to show; the card decides its own visibility with that.
	 */
	boolean refresh(boolean connected)
	{
		return refresh(connected, true, System.currentTimeMillis());
	}

	/** The same, as of {@code now}: a confirmation is drawn only while it is news. */
	boolean refresh(boolean connected, long now)
	{
		return refresh(connected, true, now);
	}

	/**
	 * The same, knowing whether a character is logged in: with none, Reroute is not offered, since it
	 * asks the map as one. Follow, Pause and End run stay, because each acts here; End run tells the
	 * map too once it can.
	 */
	boolean refresh(boolean connected, boolean loggedIn, long now)
	{
		FarmRunState.View any = state.view();
		FarmRunState.View view = any != null && ours(any.run) ? any : null;
		boolean active = view != null;
		FarmRunClient.Said said = client.said();
		boolean mine = said != null && said.port == port;
		boolean planning = mine && said.planning;
		// An End whose run id never arrived has no run to say whose it was; the farm card keeps it, as before.
		boolean ending = state.ending() && (said == null ? !port : said.port == port);
		boolean ready = connected && loggedIn && client.ready();
		/* A run with every stop behind it has nothing to follow or reorder: End run is its one press. */
		boolean left = active && view.next() != null;
		reroute.setEnabled(ready && left);
		/* Greyed by the wait every Route shares, it says how long that wait has left. */
		MapWait.tip(reroute, null, routes.pressWait(now));
		end.setEnabled(!client.endingRequest() && (active || planning || (ending && ready)));
		/* While a new run is planned there is no run to end yet: the same press calls the plan off. */
		end.setText(!active && planning && !client.endingRequest() ? "Cancel" : "End run");
		List<JButton> wanted = new ArrayList<>();
		if (left)
		{
			wanted.add(follow);
			wanted.add(reroute);
		}
		wanted.add(end);
		shownActions = MapCompanionPanel.syncRow(actions, wanted, shownActions);
		// The Port tasks card's own heading already names its one kind of run.
		title.setVisible(active && !port);
		progress.setVisible(active);
		changed.setVisible(active && view.changed);
		before.setVisible(active && !view.run.bring.isEmpty());
		actions.setVisible(active || planning || ending);
		busy = active || planning || ending;
		// The other card's held run, its press in flight, or its End whose run id never arrived.
		otherBusy = (any != null && !ours(any.run) && any.next() != null)
			|| (said != null && said.port != port && said.planning)
			|| (state.ending() && (said == null ? port : said.port != port));
		if (active)
		{
			boolean newRun = !Objects.equals(shownRun, view.run.id);
			if (newRun)
			{
				bringOpen = false;
			}
			shownRun = view.run.id;
			MapCompanionPanel.setWrapped(title, view.run.name, WIDTH);
			MapCompanionPanel.setWrapped(progress, progressLine(view), WIDTH);
			progress.setForeground(left ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
			MapCompanionPanel.setWrapped(changed, CHANGED, WIDTH);
			follow.setText(view.following ? "Pause" : "Follow");
			follow.setToolTipText(view.following ? "Keep this run and stop following it until you press Follow" : null);
			follow.setEnabled(left);
			String key = view.run.id + "|" + view.revision + "|" + view.done + "|" + view.visited + "|" + view.here + "|" + view.carried;
			if (!key.equals(shown))
			{
				shown = key;
				showBringList(view);
				showStops(view);
			}
		}
		else
		{
			shown = null;
			shownRun = null;
			stops.removeAll();
			bring.removeAll();
			missing = 0;
		}
		showBring();
		DestinationRoute.View route = routes.view();
		boolean following = active && follower.ownsDestinationRoute(route);
		boolean drawable = following && config.drawWithShortestPath() && shortest.installed() && !route.arrived();
		steps.showRunLeg(following ? route : null, following ? RouteStepsView.shortestPathLine(route, drawable, shortest.drawing()) : null);
		legHead.setVisible(steps.isVisible());
		String heading = legHeading(steps.tiles());
		if (!heading.equals(legHead.getText()))
		{
			legHead.setText(heading);
		}
		legEnd.setVisible(steps.isVisible());
		String detour = left && !view.following ? follower.pausedFor() : null;
		paused.setVisible(detour != null);
		MapCompanionPanel.setWrapped(paused, detour == null ? "" : pausedLine(detour), WIDTH);
		boolean speaks = mine && said.shown(now);
		message.setVisible(speaks);
		MapCompanionPanel.setWrapped(message, speaks ? client.words(said, now) : "", WIDTH);
		/* A refusal in pink; Planning…, Ending run… and Run ended. in the line's grey. */
		MapCompanionPanel.tone(message, speaks && said.refused());
		return active || speaks || planning || ending;
	}

	/**
	 * The run's state, then what its count counts: a farm run the patches it can witness, a port-task
	 * run its stops, all ticked by counters. A run not followed says so first, since Pause or another
	 * route changes nothing else on the card; a finished one says what it came to, done and visited
	 * kept apart as the stops keep them.
	 */
	static String progressLine(FarmRunState.View view)
	{
		if (view.next() == null)
		{
			int done = 0;
			int visited = 0;
			for (RelayResponse.FarmStop stop : view.run.stops)
			{
				done += view.done.contains(stop.n) ? 1 : 0;
				visited += view.visited.contains(stop.n) ? 1 : 0;
			}
			List<String> came = new ArrayList<>();
			if (done > 0)
			{
				came.add(done + " done");
			}
			if (visited > 0)
			{
				came.add(visited + " visited");
			}
			return "Run finished" + (came.isEmpty() ? "" : " · " + String.join(", ", came));
		}
		String count;
		if (FarmRunWire.PORT_TASKS.equals(view.run.kind))
		{
			count = view.trackedDone() + " of " + view.tracked() + " stops done";
		}
		else
		{
			count = view.tracked() > 0 ? view.trackedDone() + " of " + view.tracked() + " tracked done"
				: view.visited.size() + " of " + view.run.stops.size() + " visited";
		}
		return view.following ? count : "Paused · " + count;
	}

	/**
	 * "This leg · 26 tiles": the heading over the leg's steps, with the leg's length where the map
	 * priced one. A voyage's leg has none, since its Sail step says the sailed distance.
	 */
	static String legHeading(@Nullable Integer tiles)
	{
		return "This leg" + (tiles == null ? "" : " · " + tiles + " tiles");
	}

	/** Where the leg was, while a destination shown since has paused the run. */
	static String pausedLine(String destination)
	{
		return "Paused for " + destination + ". Follow takes the run back.";
	}

	/**
	 * What a stop says it is at: done, visited, or under the player. The next stop says it by its
	 * number, amber as its pin is on the world map, rather than by a line of its own.
	 */
	static String status(FarmRunState.View view, RelayResponse.FarmStop stop)
	{
		if (view.done.contains(stop.n))
		{
			// A birdhouse is ticked by weaker evidence than a replant, so it says which.
			return stop.birdhouseId == null ? "Done" : "Done · seen by Time Tracking";
		}
		return view.visited.contains(stop.n) ? "Visited"
			: view.here.contains(stop.n) ? "You're here" : "";
	}

	/** The stop's name, its number bold and amber when it is the next stop, as the world map draws its pin. */
	static String stopName(RelayResponse.FarmStop stop, boolean next)
	{
		String name = MapCompanionPanel.escape(stop.name);
		String number = stop.n + ".";
		return "<html><body width='" + WIDTH + "'>" + (next ? "<b><font color='" + AMBER_HTML + "'>" + number + "</font></b>" : number)
			+ " " + name + "</body></html>";
	}

	/**
	 * The bank checklist, a picture to a line. A teleport the client can see is not carried is drawn
	 * amber, the colour the bank overlay outlines it in, beside its picture greyed; one carried, or
	 * one nothing here can check, is drawn as it is, since no claim is made about it.
	 */
	private void showBringList(FarmRunState.View view)
	{
		bring.removeAll();
		missing = 0;
		for (RelayResponse.FarmBring line : view.run.bring)
		{
			Boolean carrying = line.kind.equals("teleport") ? CapabilityEvaluator.carrying(line.name, view.carried) : null;
			boolean absent = Boolean.FALSE.equals(carrying);
			missing += absent ? 1 : 0;
			// A teleport for one leg is every teleport's default, as the website's checklist leaves it unsaid.
			String words = line.name + (line.uses == null || line.uses == 1 ? "" : " · " + line.uses + " legs")
						   + (absent ? " · not on you" : "")
						   + (line.note == null ? "" : " · " + line.note);
			Picture picture = new Picture(absent);
			bringPicture(picture, line, view.run);
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			JPanel top = new JPanel(new BorderLayout());
			top.setOpaque(false);
			top.add(picture, BorderLayout.NORTH);
			row.add(top, BorderLayout.WEST);
			row.add(MapCompanionPanel.wrapped(words, 11f, absent ? MapPin.AMBER : ColorScheme.LIGHT_GRAY_COLOR,
				WIDTH - PICTURE - 4), BorderLayout.CENTER);
			bring.add(MapCompanionPanel.left(row));
		}
	}

	/**
	 * A checklist line's picture: for a teleport, the one its stop's own step carries, the map naming
	 * the stop's item as the checklist names it, else the item by name; for a supply, the item by its
	 * exact name, so "Rake" has one and "4 herb seeds" none.
	 */
	private void bringPicture(JLabel picture, RelayResponse.FarmBring line, RelayResponse.FarmRun run)
	{
		if (!line.kind.equals("teleport"))
		{
			sprites.itemNamed(picture, line.name, PICTURE);
			return;
		}
		for (RelayResponse.FarmStop stop : run.stops)
		{
			if (line.name.equalsIgnoreCase(stop.item) && firstPictured(stop) != null)
			{
				picture(picture, stop);
				return;
			}
		}
		sprites.farmTravelItem(picture, line.name, PICTURE);
	}

	private void showStops(FarmRunState.View view)
	{
		stops.removeAll();
		RelayResponse.FarmStop next = view.next();
		/* A picture's place on every stop when any has one, so the ways line up; none on a run with none. */
		boolean pictures = view.run.stops.stream().anyMatch(RunView::pictured);
		for (RelayResponse.FarmStop stop : view.run.stops)
		{
			boolean done = view.done.contains(stop.n);
			String at = status(view, stop);
			JPanel row = column();
			row.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			/*
			 * A stop behind the player is one line that keeps its status word: its way there and its
			 * tasks are spent, and the rows left are the ones still to walk. Done and visited are drawn
			 * alike, struck and grey, as the world map fades both their pins; the word tells them apart.
			 */
			if (done || view.visited.contains(stop.n))
			{
				String named = MapCompanionPanel.escape(stop.n + ". " + stop.name);
				JLabel line = label(12f, ColorScheme.MEDIUM_GRAY_COLOR);
				line.setText("<html><body width='" + WIDTH + "'><strike>" + named + "</strike>"
					+ MapCompanionPanel.escape(" · " + at) + "</body></html>");
				row.add(MapCompanionPanel.left(line));
				stops.add(MapCompanionPanel.left(row));
				continue;
			}
			JLabel name = label(12f, Color.WHITE);
			name.setText(stopName(stop, next != null && stop.n == next.n));
			row.add(MapCompanionPanel.left(name));
			JPanel method = new JPanel(new BorderLayout(4, 0));
			method.setOpaque(false);
			if (pictures)
			{
				JLabel icon = new JLabel();
				Dimension slot = new Dimension(PICTURE, PICTURE);
				icon.setPreferredSize(slot);
				icon.setMinimumSize(slot);
				icon.setMaximumSize(slot);
				picture(icon, stop);
				method.add(icon, BorderLayout.WEST);
			}
			method.add(MapCompanionPanel.wrapped(stop.method == null ? "Walk" : stop.method, 11f, ColorScheme.LIGHT_GRAY_COLOR,
				pictures ? WIDTH - PICTURE - 4 : WIDTH), BorderLayout.CENTER);
			row.add(MapCompanionPanel.left(method));
			if (stop.tasks != null)
			{
				for (RelayResponse.RunTask task : stop.tasks)
				{
					row.add(MapCompanionPanel.wrapped(task.text, 11f, ColorScheme.LIGHT_GRAY_COLOR, WIDTH));
				}
			}
			if (!at.isEmpty())
			{
				row.add(MapCompanionPanel.wrapped(at, 11f, MapPin.AMBER, WIDTH));
			}
			if (stop.birdhouseId != null)
			{
				row.add(MapCompanionPanel.wrapped(BIRDHOUSE_PENDING, 11f, ColorScheme.LIGHT_GRAY_COLOR, WIDTH));
			}
			stops.add(MapCompanionPanel.left(row));
			if (next != null && stop.n == next.n)
			{
				/*
				 * The leg being walked, under the stop it leads to, so the step a player reads rises as
				 * stops are finished instead of sinking under them. Single components, re-parented here
				 * at every rebuild; with no stop left they belong to no row and show nowhere.
				 */
				stops.add(paused);
				stops.add(legHead);
				stops.add(steps);
				stops.add(legEnd);
			}
		}
	}

	/** Whether a stop has a picture to show: a step the map gave an item or spell id, or an item it names. */
	private static boolean pictured(RelayResponse.FarmStop stop)
	{
		return stop.item != null || firstPictured(stop) != null;
	}

	/** The stop's first step with an item or spell id: the teleport This leg draws for it. */
	@Nullable
	private static RelayResponse.RouteStep firstPictured(RelayResponse.FarmStop stop)
	{
		for (RelayResponse.RouteStep step : stop.steps)
		{
			if (step.itemId != null || step.spriteId != null)
			{
				return step;
			}
		}
		return null;
	}

	/**
	 * The stop's picture, by the id its own step carries, the one This leg draws, so a teleport the
	 * map names no item for (a spell) still has one. The item's name is only the fallback.
	 */
	private void picture(JLabel icon, RelayResponse.FarmStop stop)
	{
		RelayResponse.RouteStep step = firstPictured(stop);
		if (step != null && step.itemId != null)
		{
			sprites.item(icon, step.itemId, PICTURE);
		}
		else if (step != null)
		{
			sprites.sprite(icon, step.spriteId, PICTURE);
		}
		else if (stop.item != null)
		{
			sprites.farmTravelItem(icon, stop.item, PICTURE);
		}
	}

	/**
	 * The fold's opener: closed, it carries its answer, how many teleports are not on you, when any
	 * are, and is drawn amber while it does. One run of one colour: a coloured span after the words is
	 * placed by their width at one scale, so when the sidebar is painted at another, as the README's
	 * pictures are, a gap opens in front of it.
	 */
	private void showBring()
	{
		boolean counts = !bringOpen && missing > 0;
		before.setText((bringOpen ? "▾ " : "▸ ") + "Before you leave" + (counts ? " · " + missing + " not on you" : ""));
		before.setForeground(counts ? MapPin.AMBER : ColorScheme.LIGHT_GRAY_COLOR);
		bring.setVisible(before.isVisible() && bringOpen);
	}

	/**
	 * A 16-pixel picture in a fixed slot, so the words beside it start in one column whether or not a
	 * picture comes. One for an item not on you is drawn grey and faint, as Drop luck draws a drop not
	 * had yet, the collection log's own way of saying "not yet"; greyed on every paint, since the
	 * sprite may land after the row is built. Nothing is drawn behind it.
	 */
	static final class Picture extends JLabel
	{
		final boolean faded;

		Picture(boolean faded)
		{
			this.faded = faded;
			Dimension size = new Dimension(PICTURE, PICTURE);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			setHorizontalAlignment(CENTER);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Icon icon = getIcon();
			if (!faded || !(icon instanceof ImageIcon) || !(((ImageIcon) icon).getImage() instanceof BufferedImage))
			{
				super.paintComponent(g);
				return;
			}
			BufferedImage image = (BufferedImage) ((ImageIcon) icon).getImage();
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			g2.drawImage(ImageUtil.grayscaleImage(image), (getWidth() - image.getWidth()) / 2,
				(getHeight() - image.getHeight()) / 2, null);
			g2.dispose();
		}
	}

	private static JPanel column()
	{
		JPanel column = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		column.setOpaque(false);
		return column;
	}
}
