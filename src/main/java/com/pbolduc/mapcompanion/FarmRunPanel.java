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

import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * One ordered run: its next stop, confirmed progress and the things to bring.
 *
 * <p>Time Tracking remains the patch timer. This adds only the itinerary and the evidence that
 * advances it. The list rebuilds when its content changes, not on the sidebar's one-second refresh,
 * so an unchanged frame cannot swallow a press or close the bank checklist under the pointer.
 */
final class FarmRunPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final String[][] STARTS =
	{
		{"ready-now", "Ready now"}, {"preset:herb", "Herb run"}, {"preset:tree", "Tree run"},
		{"preset:fruit", "Fruit tree run"}, {"preset:hardwood", "Hardwood run"},
		{"preset:trees", "Tree + fruit tree"}, {"preset:herb-allotment", "Herb + allotment + flower"},
		{"birdhouses", "Birdhouses"}, {"contract", "Farming contract"}
	};

	private final transient FarmRunState state;
	private final transient FarmRunClient client;
	private final transient FarmRunRoute follower;
	private final transient DestinationRoute routes;
	private final transient ShortestPath shortest;
	private final transient GameSprites sprites;
	private final transient MapCompanionConfig config;
	private final JComboBox<String> kind = new JComboBox<>();
	private final JButton start = MapCompanionPanel.linkButton("Start run");
	private final JLabel title = label(12f, Color.WHITE);
	private final JLabel progress = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private final JButton before = MapCompanionPanel.linkButton("▸ Before you leave");
	private final JPanel bring = column();
	private final JPanel stops = column();
	private final JButton follow = MapCompanionPanel.linkButton("Follow");
	private final JButton reroute = MapCompanionPanel.linkButton("Reroute");
	private final JButton end = MapCompanionPanel.linkButton("End run");
	private final JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
	private final RouteStepsView steps = new RouteStepsView(ignored -> {});
	private final JLabel message = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private boolean bringOpen;
	private String shown;
	private String shownRun;

	@Inject
	FarmRunPanel(FarmRunState state, FarmRunClient client, FarmRunRoute follower, DestinationRoute routes,
				 ShortestPath shortest, GameSprites sprites, MapCompanionConfig config)
	{
		this.state = state;
		this.client = client;
		this.follower = follower;
		this.routes = routes;
		this.shortest = shortest;
		this.sprites = sprites;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		MapCompanionPanel.left(this);
		setName("farmRun");

		JLabel heading = label(13f, Color.WHITE);
		heading.setText("Farm run");
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		add(MapCompanionPanel.left(heading));
		JPanel picker = new JPanel(new BorderLayout(0, 4));
		picker.setBackground(getBackground());
		for (String[] choice : STARTS)
		{
			kind.addItem(choice[1]);
		}
		kind.setFont(kind.getFont().deriveFont(11f));
		kind.setToolTipText("Choose a run the map can build from your character's shared readings");
		picker.add(kind, BorderLayout.CENTER);
		picker.add(start, BorderLayout.SOUTH);
		picker.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		add(MapCompanionPanel.left(picker));
		add(MapCompanionPanel.left(title));
		add(MapCompanionPanel.left(progress));
		add(before);
		add(bring);
		add(stops);
		add(steps);
		actions.setBackground(getBackground());
		actions.add(follow);
		actions.add(reroute);
		actions.add(end);
		add(MapCompanionPanel.left(actions));
		add(MapCompanionPanel.left(message));

		start.addActionListener(e -> client.start(STARTS[kind.getSelectedIndex()][0]));
		reroute.addActionListener(e -> client.reroute());
		end.addActionListener(e -> client.end());
		follow.addActionListener(e ->
		{
			FarmRunState.View view = state.view();
			if (view != null) follower.follow(!view.following);
		});
		before.addActionListener(e ->
		{
			bringOpen = !bringOpen;
			showBring();
		});
	}

	/** Called on the EDT by the sidebar refresh; character state has already been read on a game tick. */
	void refresh(boolean connected)
	{
		FarmRunState.View view = state.view();
		boolean active = view != null;
		FarmRunClient.Said currentPress = client.said();
		boolean planning = currentPress != null && currentPress.planning;
		boolean ending = state.ending();
		setVisible(connected || active);
		boolean ready = connected && client.ready();
		start.setEnabled(ready);
		kind.setEnabled(ready);
		reroute.setEnabled(ready && active && view.next() != null);
		end.setEnabled(!client.endingRequest() && (active || planning || (ending && ready)));
		follow.setVisible(active);
		reroute.setVisible(active);
		title.setVisible(active);
		progress.setVisible(active);
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
			String status = view.next() == null ? "Route finished."
							: view.tracked() > 0 ? view.trackedDone() + " of " + view.tracked() + " tracked done"
							: view.visited.size() + " of " + view.run.stops.size() + " visited";
			MapCompanionPanel.setWrapped(progress, status, WIDTH);
			follow.setText(view.following ? "Pause route" : "Follow");
			follow.setEnabled(view.next() != null);
			String key = view.run.id + "|" + view.revision + "|" + view.done + "|" + view.visited + "|" + view.here + "|" + view.carried;
			if (!key.equals(shown))
			{
				shown = key;
				bring.removeAll();
				for (RelayResponse.FarmBring line : view.run.bring)
				{
					Boolean carrying = line.kind.equals("teleport") ? CapabilityEvaluator.carrying(line.name, view.carried) : null;
					String words = line.name + (line.uses == null ? "" : " · " + line.uses + (line.uses == 1 ? " leg" : " legs"))
								   + (Boolean.FALSE.equals(carrying) ? " · not on you" : "")
								   + (line.note == null ? "" : " · " + line.note);
					bring.add(MapCompanionPanel.left(wrapped(words, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
				}
				stops.removeAll();
				RelayResponse.FarmStop next = view.next();
				for (RelayResponse.FarmStop stop : view.run.stops)
				{
					boolean done = view.done.contains(stop.n);
					String at = done ? stop.birdhouseId == null ? "Done" : "Done · seeded sighting"
								: view.visited.contains(stop.n) ? "Visited"
								: view.here.contains(stop.n) ? "You're here"
								: next != null && next.n == stop.n ? "Next" : "";
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
					method.setBackground(getBackground());
					JLabel icon = new JLabel();
					if (stop.item != null)
					{
						sprites.farmTravelItem(icon, stop.item, 16);
					}
					method.add(icon, BorderLayout.WEST);
					JLabel way = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
					MapCompanionPanel.setWrapped(way, stop.method == null ? "Walk" : stop.method, WIDTH - 20);
					method.add(way, BorderLayout.CENTER);
					row.add(MapCompanionPanel.left(method));
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
		boolean ours = active && follower.ownsDestinationRoute(route);
		boolean drawable = ours && config.drawWithShortestPath() && shortest.installed() && !route.arrived();
		steps.showRunLeg(ours ? route : null, ours ? RouteStepsView.shortestPathLine(route, drawable, shortest.drawing()) : null);
		FarmRunClient.Said said = client.said();
		message.setVisible(said != null && said.message != null);
		MapCompanionPanel.setWrapped(message, said == null || said.message == null ? "" : said.message, WIDTH);
		revalidate();
		repaint();
	}

	private void showBring()
	{
		before.setText((bringOpen ? "▾ " : "▸ ") + "Before you leave");
		bring.setVisible(before.isVisible() && bringOpen);
	}

	private static JPanel column()
	{
		return MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
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
