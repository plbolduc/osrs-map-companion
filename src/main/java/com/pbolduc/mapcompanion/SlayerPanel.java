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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * The website's ranked places in the sidebar, with one local remaining count.
 * No score or task table lives here. Fold state survives ticks, and an unchanged list keeps
 * its buttons under the pointer. Every route is an explicit press through the shared gate.
 */
final class SlayerPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color GOLD = new Color(237, 189, 100);
	static final String NO_TASK = "No Slayer task";
	static final String GET_ONE = "Get one from a Slayer master and it shows here.";
	static final String NEXT_TASK = "Get your next task from a Slayer master.";
	static final String NONE_OPEN = "No place for this task is open to you yet.";
	private final transient SlayerState state;
	private final transient MapCompanionConfig config;
	private final transient ConfigManager configManager;
	private final transient RoutePress routes;
	private final transient DestinationState destinations;
	private final transient DestinationMarker marker;
	private final transient ClientThread clientThread;
	private final transient GameSprites sprites;
	private final JLabel task = line("", Color.WHITE);
	private final JLabel status = line("", MUTED);
	private final JPanel body = column();
	private final List<JButton> routeButtons = new ArrayList<>();
	private final List<JButton> pinButtons = new ArrayList<>();
	private final List<JLabel> routeMessages = new ArrayList<>();
	private RelayResponse.SlayerOptions shown;
	/* Whether the places drawn are the ones a new choice hid, waiting for the map to rank again. */
	private boolean shownWaiting;
	/* The choice the picker and the Wilderness box were drawn with: the state's, not the drawn list's. */
	private String shownOrder;
	private boolean shownHide;
	private String shownTask;
	private boolean expanded;
	private boolean kindsOpen;
	private boolean connected;

	@Inject
	SlayerPanel(SlayerState state, MapCompanionConfig config, ConfigManager configManager, RoutePress routes,
				DestinationState destinations, DestinationMarker marker, ClientThread clientThread, GameSprites sprites)
	{
		this.state = state;
		this.config = config;
		this.configManager = configManager;
		this.routes = routes;
		this.destinations = destinations;
		this.marker = marker;
		this.clientThread = clientThread;
		this.sprites = sprites;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setName("slayer");
		MapCompanionPanel.left(this);
		JLabel heading = new JLabel("Slayer");
		heading.setForeground(Color.WHITE);
		MapCompanionPanel.left(heading);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
		sprites.skill(heading, "Slayer", 16);
		add(heading);
		add(task);
		add(status);
		add(body);
	}

	void refresh(boolean ready)
	{
		connected = ready;
		setVisible(ready && config.shareSlayer());
		if (!isVisible())
		{
			return;
		}
		SlayerState.View view = state.view(System.currentTimeMillis());
		/* Through a hop's unsettled window, the last settled reading rather than the "no task" sent. */
		SlayerState.Reading reading = view.shown;
		/*
		 * A choice just made keeps the old places on the card, greyed, until the map has ranked them
		 * again: the picker stays under the pointer, and the card never empties itself on a slow map.
		 */
		boolean waiting = view.options == null && view.previous != null;
		RelayResponse.SlayerOptions places = waiting ? view.previous : view.options;
		/*
		 * "No Slayer task" only once the game has said so. Before it settles, or for a task this
		 * client cannot name, the card claims nothing: a player with a task is never told to get one.
		 */
		String title = reading.task == null ? reading.settled ? NO_TASK : null
					   : reading.task + (reading.remaining == null ? "" : reading.remaining == 0 ? " · Complete" : " · " + reading.remaining + " left");
		MapCompanionPanel.setWrapped(task, title, WIDTH);
		task.setVisible(title != null);
		/*
		 * Nothing left to kill: the card folds to its row and the step that changes it until the next
		 * task is read, since a place and its Route are no use to a finished task.
		 */
		boolean finished = reading.task != null && reading.remaining != null && reading.remaining == 0;
		String message = reading.task == null ? reading.settled ? GET_ONE : "The current assignment appears here once the game has settled."
						 : finished ? NEXT_TASK
						 : waiting ? rankingAgain(view.previous, view.order, view.hideWilderness)
						 : view.options == null ? "Waiting for places from the map…" : view.stale ? "Waiting for the map to reconnect. These are the last places received." : reading.area;
		MapCompanionPanel.setWrapped(status, message, WIDTH);
		status.setVisible(message != null);
		if (!Objects.equals(shownTask, reading.task))
		{
			expanded = false;
			kindsOpen = false;
			shownTask = reading.task;
		}
		if (shown != places || shownWaiting != waiting || !Objects.equals(shownOrder, view.order) || shownHide != view.hideWilderness)
		{
			shown = places;
			shownWaiting = waiting;
			shownOrder = view.order;
			shownHide = view.hideWilderness;
			render();
		}
		body.setVisible(!finished);
		long now = System.currentTimeMillis();
		for (JButton button : routeButtons)
		{
			RoutePress.Said said = routes.said(button.getName());
			button.setEnabled(ready && !view.stale && !waiting && routes.ready());
			button.setText(said != null && said.planning ? "Planning…" : "Route");
			MapWait.tip(button, null, routes.waitMs(now));
		}
		for (JButton button : pinButtons)
		{
			button.setEnabled(ready && !view.stale && !waiting);
		}
		for (JLabel label : routeMessages)
		{
			RoutePress.Said said = routes.said(label.getName());
			String text = said == null || said.planning || said.message == null ? null : routes.words(said, now);
			MapCompanionPanel.setWrapped(label, text, WIDTH);
			label.setVisible(text != null);
			/* Pink, so a Route that did not happen does not read as the card's next note. */
			MapCompanionPanel.tone(label, text != null);
		}
	}

	private void render()
	{
		body.removeAll();
		routeButtons.clear();
		pinButtons.clear();
		routeMessages.clear();
		if (shown != null)
		{
			if (shown.note != null)
			{
				body.add(line(shown.note, MUTED));
			}
			int count = 1;
			for (int i = 0; i < shown.rows.size(); i++)
				if (shown.rows.get(i).bestForYou)
				{
					count = i + 1;
					break;
				}
			/* Best for you is the first place not asking for something unmet; with none, say why the list stops. */
			if (!shown.rows.isEmpty() && shown.rows.stream().noneMatch(row -> row.bestForYou))
			{
				body.add(line(NONE_OPEN, MUTED));
			}
			int visible = expanded ? shown.rows.size() : Math.min(count, shown.rows.size());
			for (int i = 0; i < visible; i++)
			{
				spot(shown.rows.get(i));
			}
			if (shown.hiddenWilderness > 0)
			{
				body.add(line(shown.hiddenWilderness + (shown.hiddenWilderness == 1 ? " Wilderness place hidden" : " Wilderness places hidden"), MUTED));
			}
			for (String note : shown.notes)
			{
				body.add(line(note, MUTED));
			}
			JButton more = MapCompanionPanel.linkButton(expanded ? "▾ Show less" : "▸ " + (shown.rows.size() > visible ? "All " + shown.rows.size() + " places & options" : "Options & what counts"));
			more.setName("slayer.expand");
			more.addActionListener(e -> { expanded = !expanded; render(); refresh(connected); });
			body.add(more);
			if (expanded)
			{
				JComboBox<String> order = new JComboBox<>(new String[] {"Best spot", "One target at a time"});
				/* The closed box says what it chooses; the open list keeps the choices' own names. */
				ListCellRenderer<? super String> names = order.getRenderer();
				order.setRenderer((list, value, index, selected, focused) -> names.getListCellRendererComponent(list,
					index < 0 && value != null ? "Order: " + value : value, index, selected, focused));
				order.setSelectedIndex("single".equals(shownOrder) ? 1 : 0);
				order.setFont(order.getFont().deriveFont(11f));
				order.setPreferredSize(new java.awt.Dimension(WIDTH, order.getPreferredSize().height));
				order.setName("slayer.order");
				order.setToolTipText("Rank potential with an area attack, or one target at a time");
				order.addActionListener(e -> choose(order.getSelectedIndex() == 1 ? "single" : "best", config.slayerHideWilderness()));
				body.add(MapCompanionPanel.left(order));
				JCheckBox wilderness = new JCheckBox("Hide Wilderness", shownHide);
				wilderness.setName("slayer.hideWilderness");
				wilderness.setOpaque(false);
				wilderness.setFont(wilderness.getFont().deriveFont(11f));
				wilderness.addActionListener(e -> choose(config.slayerOrder(), wilderness.isSelected()));
				body.add(MapCompanionPanel.left(wilderness));
				JButton kinds = MapCompanionPanel.linkButton((kindsOpen ? "▾ " : "▸ ") + shown.kinds.size() + (shown.kinds.size() == 1 ? " kind counts" : " kinds count"));
				kinds.setName("slayer.kinds");
				kinds.addActionListener(e -> { kindsOpen = !kindsOpen; render(); refresh(connected); });
				body.add(kinds);
				if (kindsOpen)
				{
					for (RelayResponse.SlayerKind kind : shown.kinds)
					{
						body.add(line(kind.name, Color.WHITE));
						if (kind.facts != null)
						{
							body.add(line(kind.facts, MUTED));
						}
						if (kind.best != null)
						{
							body.add(line("Best at " + kind.best, MUTED));
						}
					}
					if (!shown.bosses.isEmpty())
					{
						body.add(line("Boss versions", GOLD));
					}
					for (RelayResponse.SlayerBoss boss : shown.bosses)
					{
						body.add(line(boss.name + (boss.where == null ? "" : " · " + boss.where), Color.WHITE));
						if (boss.entrance != null)
						{
							actions("slayer:boss:" + boss.name, null, boss.entrance, boss.name);
						}
					}
				}
				if (shown.rate != null)
				{
					body.add(line("Wiki XP/h: " + shown.rate, MUTED));
				}
			}
			JButton wiki = MapCompanionPanel.linkButton("OSRS Wiki ↗");
			wiki.setToolTipText("Open this task's source page in your browser");
			wiki.addActionListener(e -> LinkBrowser.browse(wikiUrl(shown.page)));
			body.add(wiki);
			/*
			 * The source on every card, the licence with the open list, where the website's Slayer block
			 * has it too: the closed card ends on its last useful row, and the terms are one press away.
			 */
			if (expanded)
			{
				JButton license = MapCompanionPanel.linkButton("CC BY-NC-SA 3.0 ↗");
				license.setFont(license.getFont().deriveFont(10f));
				license.setToolTipText("The licence for the Wiki's locations and rates");
				license.addActionListener(e -> LinkBrowser.browse("https://creativecommons.org/licenses/by-nc-sa/3.0/"));
				body.add(license);
			}
		}
		body.revalidate();
		body.repaint();
	}

	private void spot(RelayResponse.SlayerSpot spot)
	{
		boolean blocked = "unmet".equals(spot.tier);
		JLabel title = line(spot.rank + ". " + spot.name, blocked ? MUTED : Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		title.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
		body.add(title);
		if (spot.bestForYou)
		{
			body.add(line("Best for you", GOLD));
		}
		if (!spot.kinds.isEmpty())
		{
			body.add(line(String.join(" · ", spot.kinds), MUTED));
		}
		if (!spot.labels.isEmpty())
		{
			body.add(line(String.join(" · ", spot.labels), MUTED));
		}
		for (RelayResponse.SlayerAsk ask : spot.asks)
		{
			String words = ("unmet".equals(ask.tier) ? "Needs " : "unknown".equals(ask.tier) ? "Unconfirmed: " : "✓ ") + ask.text;
			JLabel requirement = line(words,
									  "unmet".equals(ask.tier) ? MapCompanionPanel.REFUSED : MUTED);
			if (ask.skill != null)
			{
				MapCompanionPanel.setWrapped(requirement, words, WIDTH - 16);
				sprites.skill(requirement, ask.skill, 12);
			}
			body.add(requirement);
		}
		actions(spot.id, spot.id, spot.target, spot.name);
	}

	private void actions(String source, String id, RelayResponse.RouteTile tile, String name)
	{
		/*
		 * Route and Pin side by side, as on a friend's card: they act, so they are buttons. Pin does
		 * what Stars' Pin destination does, Home's card, the arrow and the world map's pin, and says
		 * so: a friend's World map draws only that friend's dot, and one name for both would mislead.
		 */
		JPanel actions = new JPanel(new GridLayout(1, 0, 4, 0));
		actions.setOpaque(false);
		actions.setBorder(BorderFactory.createEmptyBorder(3, 0, 1, 0));
		JButton route = MapCompanionPanel.smallButton("Route");
		route.setName(source);
		WorldPoint at = new WorldPoint(tile.x, tile.y, tile.plane);
		route.addActionListener(e -> { routes.press(id == null ? RoutePress.Target.tile(source, at, name, null, null) : RoutePress.Target.place(source, id, name, at, null)); refresh(connected); });
		routeButtons.add(route);
		actions.add(route);
		JButton pin = MapCompanionPanel.smallButton("Pin");
		pin.setToolTipText("Pin this spot as your destination: world map, arrow and Home");
		pin.setName(source + ".map");
		pin.addActionListener(e ->
		{
			RelayResponse.Destination next = new RelayResponse.Destination();
			next.x = tile.x; next.y = tile.y; next.plane = tile.plane; next.name = name;
			destinations.selectLocal(next); clientThread.invokeLater(marker::sync);
		});
		pinButtons.add(pin);
		actions.add(pin);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
		body.add(MapCompanionPanel.left(actions));
		JLabel feedback = line("", MUTED);
		feedback.setName(source);
		feedback.setVisible(false);
		body.add(feedback);
		routeMessages.add(feedback);
	}

	private void choose(String order, boolean hide)
	{
		state.choices(order, hide);
		configManager.setConfiguration(MapCompanionConfig.GROUP, "slayerOrder", order);
		configManager.setConfiguration(MapCompanionConfig.GROUP, "slayerHideWilderness", hide);
		refresh(connected);
	}

	/**
	 * What the card says while the places a choice hid wait for their replacement: the choice in the
	 * picker's own words, and, where the old places already fit it, simply that the map is awaited.
	 */
	static String rankingAgain(RelayResponse.SlayerOptions previous, String order, boolean hide)
	{
		if (!Objects.equals(previous.order, order))
		{
			return "single".equals(order) ? "Ranking again for one target at a time…" : "Ranking again for best spot…";
		}
		if (previous.hideWilderness != hide)
		{
			return hide ? "Ranking again without Wilderness places…" : "Ranking again with Wilderness places…";
		}
		return "Waiting for places from the map…";
	}

	static String wikiUrl(String page)
	{
		try
		{
			return new URI("https", "oldschool.runescape.wiki", "/w/" + (page == null ? "Slayer_training" : page.replace(' ', '_')), null).toASCIIString();
		}
		catch (URISyntaxException invalid)
		{
			return "https://oldschool.runescape.wiki/w/Slayer_training";
		}
	}

	private static JLabel line(String text, Color color)
	{
		JLabel label = new JLabel();
		label.setForeground(color);
		label.setFont(label.getFont().deriveFont(11f));
		MapCompanionPanel.setWrapped(label, text, WIDTH);
		return MapCompanionPanel.left(label);
	}
	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return MapCompanionPanel.left(panel);
	}
}
