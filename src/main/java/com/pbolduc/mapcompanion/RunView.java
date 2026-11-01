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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * One received run as the sidebar follows it: its stops, the current leg's steps, and Follow,
 * Reroute and End. Shared by the two cards that start a run, each showing only its own kind.
 *
 * <p>The farm run card shows every kind but {@code port-tasks}; the Port tasks card shows that one.
 * There is one run at a time — the map keeps one a member — so starting either replaces the other,
 * and the card that did not start it simply stops showing it. What a stop says under its name is
 * its own evidence: a farm stop's way there and the Time Tracking note; a port-task stop's way and
 * one line a task, the words the map ticks it by.
 *
 * <p>The list rebuilds when its content changes, not on the sidebar's one-second refresh, so an
 * unchanged frame cannot swallow a press or close the bank checklist under the pointer. EDT only.
 */
final class RunView extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	static final String CHANGED = "Your port tasks have changed since this run was planned. Reroute plans from what you hold now.";

	private final transient FarmRunState state;
	private final transient FarmRunClient client;
	private final transient FarmRunRoute follower;
	private final transient DestinationRoute routes;
	private final transient ShortestPath shortest;
	private final transient GameSprites sprites;
	private final transient MapCompanionConfig config;
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
	private final RouteStepsView steps;
	private final JLabel message = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private boolean bringOpen;
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
		add(steps);
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

	/**
	 * Called on the EDT by the card's refresh. True when this card has a run, a press or a message of
	 * its own to show; the card decides its own visibility with that.
	 */
	boolean refresh(boolean connected)
	{
		FarmRunState.View any = state.view();
		FarmRunState.View view = any != null && ours(any.run) ? any : null;
		boolean active = view != null;
		FarmRunClient.Said said = client.said();
		boolean mine = said != null && said.port == port;
		boolean planning = mine && said.planning;
		// An End whose run id never arrived has no run to say whose it was; the farm card keeps it, as before.
		boolean ending = state.ending() && (said == null ? !port : said.port == port);
		boolean ready = connected && client.ready();
		reroute.setEnabled(ready && active && view.next() != null);
		end.setEnabled(!client.endingRequest() && (active || planning || (ending && ready)));
		List<JButton> wanted = new ArrayList<>();
		if (active)
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
			MapCompanionPanel.setWrapped(changed, CHANGED, WIDTH);
			follow.setText(view.following ? "Pause" : "Follow");
			follow.setToolTipText(view.following ? "Keep this run and stop following it until you press Follow" : null);
			follow.setEnabled(view.next() != null);
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
		}
		showBring();
		DestinationRoute.View route = routes.view();
		boolean following = active && follower.ownsDestinationRoute(route);
		boolean drawable = following && config.drawWithShortestPath() && shortest.installed() && !route.arrived();
		steps.showRunLeg(following ? route : null, following ? RouteStepsView.shortestPathLine(route, drawable, shortest.drawing()) : null);
		boolean speaks = mine && said.message != null;
		message.setVisible(speaks);
		MapCompanionPanel.setWrapped(message, speaks ? said.message : "", WIDTH);
		return active || speaks || planning || ending;
	}

	/** A farm run counts the patches it can witness; a port-task run counts its stops, all ticked by counters. */
	static String progressLine(FarmRunState.View view)
	{
		if (view.next() == null)
		{
			return "Route finished.";
		}
		if (FarmRunWire.PORT_TASKS.equals(view.run.kind))
		{
			return view.trackedDone() + " of " + view.tracked() + " stops done";
		}
		return view.tracked() > 0 ? view.trackedDone() + " of " + view.tracked() + " tracked done"
			: view.visited.size() + " of " + view.run.stops.size() + " visited";
	}

	/** What a stop says it is at: done, visited, under the player, or next. */
	static String status(FarmRunState.View view, RelayResponse.FarmStop stop, RelayResponse.FarmStop next)
	{
		if (view.done.contains(stop.n))
		{
			return stop.birdhouseId == null ? "Done" : "Done · seeded sighting";
		}
		return view.visited.contains(stop.n) ? "Visited"
			: view.here.contains(stop.n) ? "You're here"
			: next != null && next.n == stop.n ? "Next" : "";
	}

	private void showBringList(FarmRunState.View view)
	{
		bring.removeAll();
		for (RelayResponse.FarmBring line : view.run.bring)
		{
			Boolean carrying = line.kind.equals("teleport") ? CapabilityEvaluator.carrying(line.name, view.carried) : null;
			String words = line.name + (line.uses == null ? "" : " · " + line.uses + (line.uses == 1 ? " leg" : " legs"))
						   + (Boolean.FALSE.equals(carrying) ? " · not on you" : "")
						   + (line.note == null ? "" : " · " + line.note);
			bring.add(MapCompanionPanel.left(wrapped(words, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		}
	}

	private void showStops(FarmRunState.View view)
	{
		stops.removeAll();
		RelayResponse.FarmStop next = view.next();
		for (RelayResponse.FarmStop stop : view.run.stops)
		{
			boolean done = view.done.contains(stop.n);
			String at = status(view, stop, next);
			JPanel row = column();
			row.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			JLabel name = wrapped(stop.n + ". " + stop.name, 12f, done ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
			if (done)
			{
				String safe = MapCompanionPanel.escape(stop.n + ". " + stop.name);
				name.setText("<html><body width='" + WIDTH + "'><strike>" + safe + "</strike></body></html>");
			}
			row.add(MapCompanionPanel.left(name));
			JPanel method = new JPanel(new BorderLayout(4, 0));
			method.setOpaque(false);
			// A farm stop keeps its item's place even when it has none; a port stop names a boat, never an item.
			if (!port)
			{
				JLabel icon = new JLabel();
				if (stop.item != null)
				{
					sprites.farmTravelItem(icon, stop.item, 16);
				}
				method.add(icon, BorderLayout.WEST);
			}
			JLabel way = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			MapCompanionPanel.setWrapped(way, stop.method == null ? "Walk" : stop.method, WIDTH - 20);
			method.add(way, BorderLayout.CENTER);
			row.add(MapCompanionPanel.left(method));
			if (stop.tasks != null)
			{
				for (RelayResponse.RunTask task : stop.tasks)
				{
					row.add(MapCompanionPanel.left(wrapped(task.text, 11f, done ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR)));
				}
			}
			if (!at.isEmpty())
			{
				row.add(MapCompanionPanel.left(wrapped(at, 11f, done ? ColorScheme.MEDIUM_GRAY_COLOR : MapPin.AMBER)));
			}
			if (stop.birdhouseId != null && !done)
			{
				row.add(MapCompanionPanel.left(wrapped("Tracked by Time Tracking sightings", 10f, ColorScheme.LIGHT_GRAY_COLOR)));
			}
			stops.add(MapCompanionPanel.left(row));
		}
	}

	private void showBring()
	{
		before.setText((bringOpen ? "▾ " : "▸ ") + "Before you leave");
		bring.setVisible(before.isVisible() && bringOpen);
	}

	private static JPanel column()
	{
		JPanel column = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		column.setOpaque(false);
		return column;
	}

	private static JLabel label(float size, Color colour)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(colour);
		return label;
	}

	private static JLabel wrapped(String words, float size, Color colour)
	{
		JLabel label = label(size, colour);
		MapCompanionPanel.setWrapped(label, words, WIDTH);
		return label;
	}
}
