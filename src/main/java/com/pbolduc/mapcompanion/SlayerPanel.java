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
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.linkButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.refont;

/**
 * The website's ranked places in the sidebar, with one local remaining count.
 * No score or task table lives here. Fold state survives ticks, and an unchanged list keeps
 * its buttons under the pointer. Every route is an explicit press through the shared gate.
 */
final class SlayerPanel extends JPanel
{
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color GOLD = new Color(237, 189, 100);
	static final String NO_TASK = "No Slayer task";
	static final String GET_ONE = Words.of("slayer.getOne");
	static final String NEXT_TASK = Words.of("slayer.nextTask");
	static final String NONE_OPEN = Words.of("slayer.noneOpen");
	private final SlayerState state;
	private final MapCompanionConfig config;
	private final ConfigStore settings;
	private final RoutePress routes;
	private final DestinationState destinations;
	private final DestinationMarker marker;
	private final ClientThread clientThread;
	private final GameSprites sprites;
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
	/* The place open to its facts and buttons, by id; null opens Best for you, or the first with none. */
	private String open;
	private boolean kindsOpen;
	private boolean connected;
	/** Whether a character is logged in, as of the last refresh: Route asks the map as one, and Pin does not. */
	private boolean loggedIn = true;

	@Inject
	SlayerPanel(SlayerState state, MapCompanionConfig config, ConfigStore settings, RoutePress routes,
				DestinationState destinations, DestinationMarker marker, ClientThread clientThread, GameSprites sprites)
	{
		this.state = state;
		this.config = config;
		this.settings = settings;
		this.routes = routes;
		this.destinations = destinations;
		this.marker = marker;
		this.clientThread = clientThread;
		this.sprites = sprites;
		JPanel card = MapCompanionPanel.taskCard(this, "slayer", "Slayer", sprites, "Slayer", settings);
		/* The task and its count stay on a folded card: they are what a glance at it is for. */
		add(task, 1);
		card.add(status);
		card.add(body);
	}

	void refresh(boolean ready)
	{
		refresh(ready, loggedIn);
	}

	/** The same, knowing whether a character is logged in: with none, Route waits for one and Pin stays. */
	void refresh(boolean ready, boolean loggedIn)
	{
		connected = ready;
		this.loggedIn = loggedIn;
		setVisible(ready && settings.on("shareSlayer"));
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
		MapCompanionPanel.show(task, title, WIDTH);
		/*
		 * Nothing left to kill: the card folds to its row and the step that changes it until the next
		 * task is read, since a place and its Route are no use to a finished task.
		 */
		boolean finished = reading.task != null && reading.remaining != null && reading.remaining == 0;
		String message = reading.task == null ? reading.settled ? GET_ONE : Words.of("slayer.settling")
						 : finished ? NEXT_TASK
						 : waiting ? rankingAgain(view.previous, view.order, view.hideWilderness)
						 : view.options == null ? "Waiting for places from the map…" : view.stale ? Words.of("slayer.stale") : reading.area;
		MapCompanionPanel.show(status, message, WIDTH);
		if (!Objects.equals(shownTask, reading.task))
		{
			expanded = false;
			kindsOpen = false;
			open = null;
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
			button.setEnabled(ready && loggedIn && !view.stale && !waiting && routes.ready());
			button.setText(said != null && said.planning ? "Planning…" : "Route");
			MapWait.tip(button, loggedIn ? null : RoutePress.LOG_IN, routes.waitMs(now));
		}
		for (JButton button : pinButtons)
		{
			button.setEnabled(ready && !view.stale && !waiting);
		}
		for (JLabel label : routeMessages)
		{
			RoutePress.Said said = routes.said(label.getName());
			String text = said == null || said.planning || said.message == null ? null : routes.words(said, now);
			MapCompanionPanel.show(label, text, WIDTH);
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
			/*
			 * One place open at a time, Best for you to begin with: its facts, what it asks and its two
			 * buttons. The others are a name and their labels, and a press opens one in its place, so
			 * six places are a list to read down and not twelve buttons alike.
			 */
			String chosen = open;
			if (shown.rows.subList(0, visible).stream().noneMatch(row -> row.id.equals(chosen)))
			{
				open = visible == 0 ? null : shown.rows.get(count - 1).id;
			}
			for (int i = 0; i < visible; i++)
			{
				spot(shown.rows.get(i));
			}
			/*
			 * Under a hairline of their own: what the list leaves out, the task's notes, the fold and the
			 * options it opens are about the whole list, not its last place.
			 */
			body.add(rule());
			if (shown.hiddenWilderness > 0)
			{
				body.add(line(shown.hiddenWilderness + (shown.hiddenWilderness == 1 ? " Wilderness place hidden" : " Wilderness places hidden"), MUTED));
			}
			for (String note : shown.notes)
			{
				body.add(line(note, MUTED));
			}
			JButton more = linkButton(expanded ? "▾ Show less" : "▸ " + (shown.rows.size() > visible ? "All " + shown.rows.size() + " places & options" : "Options & what counts"));
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
				refont(order, 11f);
				order.setPreferredSize(new java.awt.Dimension(WIDTH, order.getPreferredSize().height));
				order.setName("slayer.order");
				order.setToolTipText(Words.of("slayer.orderTip"));
				order.addActionListener(e -> choose(order.getSelectedIndex() == 1 ? "single" : "best", config.slayerHideWilderness()));
				body.add(MapCompanionPanel.left(order));
				JCheckBox wilderness = new JCheckBox("Hide Wilderness", shownHide);
				wilderness.setName("slayer.hideWilderness");
				wilderness.setOpaque(false);
				refont(wilderness, 11f);
				wilderness.addActionListener(e -> choose(config.slayerOrder(), wilderness.isSelected()));
				body.add(wilderness);
				body.add(rule());
				JButton kinds = linkButton((kindsOpen ? "▾ " : "▸ ") + shown.kinds.size() + (shown.kinds.size() == 1 ? " kind counts" : " kinds count"));
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
			JButton wiki = linkButton("OSRS Wiki ↗");
			wiki.setToolTipText(Words.of("slayer.wikiTip"));
			wiki.addActionListener(e -> LinkBrowser.browse(wikiUrl(shown.page)));
			/*
			 * The source on every card, the licence with the open list, where the website's Slayer block
			 * has it too: the closed card ends on its last useful row, and the terms are one press away.
			 * They share the card's last line, the source on the left and its terms on the right.
			 */
			JButton license = null;
			if (expanded)
			{
				license = linkButton("CC BY-NC-SA 3.0 ↗");
				refont(license, 10f);
				license.setToolTipText(Words.of("slayer.licenceTip"));
				license.addActionListener(e -> LinkBrowser.browse("https://creativecommons.org/licenses/by-nc-sa/3.0/"));
			}
			JPanel source = MapCompanionPanel.bar(0, null, wiki, null, license);
			source.setOpaque(false);
			body.add(MapCompanionPanel.left(MapCompanionPanel.capped(source)));
		}
		body.revalidate();
		body.repaint();
	}

	private void spot(RelayResponse.SlayerSpot spot)
	{
		boolean blocked = "unmet".equals(spot.tier);
		boolean opened = spot.id.equals(open);
		String name = spot.rank + ". " + spot.name;
		body.add(rule());
		if (opened)
		{
			JLabel title = line("", blocked ? MUTED : Color.WHITE);
			refont(title, Font.BOLD);
			title.setText(named(name, title.getFontMetrics(title.getFont())));
			body.add(title);
		}
		else
		{
			/* A closed place is its name as a link, the fold's own arrow before it: a press opens it. */
			JButton title = linkButton("");
			title.setText(named("▸ " + name, title.getFontMetrics(title.getFont())));
			title.setForeground(blocked ? MUTED : Color.WHITE);
			title.setBorder(BorderFactory.createEmptyBorder());
			title.addActionListener(e -> { open = spot.id; render(); refresh(connected); });
			body.add(title);
		}
		if (spot.bestForYou)
		{
			body.add(line("Best for you", GOLD));
		}
		if (opened && !spot.kinds.isEmpty())
		{
			body.add(line(String.join(" · ", spot.kinds), MUTED));
		}
		Box row = chips(spot.labels);
		/*
		 * A level a place asks for is a chip at the row's right: the skill's icon, the level, and a
		 * tick in green, a cross in the refusal pink or a question mark; the icon names the skill, and
		 * the tip says it in words. What a place still asks is why it is grey, so a closed one says
		 * it; what is met waits for the open one. A quest or an item has no icon, and keeps its line.
		 */
		row.add(Box.createHorizontalGlue());
		List<JLabel> lines = new ArrayList<>();
		for (RelayResponse.SlayerAsk ask : spot.asks)
		{
			boolean unmet = "unmet".equals(ask.tier);
			boolean unknown = "unknown".equals(ask.tier);
			if (!opened && !unmet)
			{
				continue;
			}
			String words = (unmet ? "Needs " : unknown ? "Unconfirmed: " : "✓ ") + ask.text;
			if (ask.skill == null)
			{
				lines.add(line(words, unmet ? MapCompanionPanel.REFUSED : MUTED));
				continue;
			}
			JLabel chip = pill(ask.text.replace(" " + ask.skill, "") + (unmet ? " ✗" : unknown ? " ?" : " ✓"),
				unmet ? MapCompanionPanel.REFUSED : unknown ? MUTED : ColorScheme.PROGRESS_COMPLETE_COLOR, words);
			sprites.skill(chip, ask.skill, 14);
			row.add(Box.createHorizontalStrut(3));
			row.add(chip);
		}
		if (row.getComponentCount() > 1)
		{
			body.add(MapCompanionPanel.left(row));
		}
		lines.forEach(body::add);
		if (opened)
		{
			actions(spot.id, spot.id, spot.target, spot.name);
		}
	}

	/**
	 * A place's labels as chips, so six places are compared at a glance and not by reading six
	 * sentences: one small picture each on a row, its label in full as its tip. Multi is the game's
	 * multi-combat swords and Cannon a cannon's barrels. The game has no picture of its own for a
	 * safespot or for aggression, so those borrow one: the Defence skill's shield and the Attack
	 * skill's sword. Wilderness is its word in the sidebar's pink, as a Wilderness star site is.
	 * Unknown, the wiki not saying whether a place is multi-combat or takes a cannon, has no chip: it
	 * marks nothing a player can use, and the place's other chips say what is known.
	 *
	 * <p>A chip is its word until its picture arrives, so a picture the client cannot give leaves
	 * the word and never an empty box. Then it keeps only what the picture cannot say, "in part" or
	 * "maybe"; a reason after a dot, "best with an area attack", is left to the tip.
	 */
	private Box chips(List<String> labels)
	{
		Box row = Box.createHorizontalBox();
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 1, 0));
		for (String label : labels)
		{
			String word = label.split(" ")[0];
			if ("Unknown".equals(word))
			{
				continue;
			}
			JLabel chip = pill(word, "Wilderness".equals(word) ? MapCompanionPanel.REFUSED : MUTED, label);
			chip.addPropertyChangeListener("icon", e -> chip.setText(label.contains(" · ") ? "" : label.substring(word.length()).trim()));
			if ("Multi".equals(word))
			{
				sprites.sprite(chip, SpriteID.OVERLAY_MULTIWAY, 14);
			}
			else if ("Cannon".equals(word))
			{
				sprites.item(chip, ItemID.TWPART3, 14);
			}
			else if ("Safespot".equals(word) || "Aggressive".equals(word))
			{
				sprites.skill(chip, "Safespot".equals(word) ? "Defence" : "Attack", 14);
			}
			row.add(chip);
			row.add(Box.createHorizontalStrut(3));
		}
		return row;
	}

	/** A chip: a few words or a picture on the lighter grey, with what it means as its tip. */
	private static JLabel pill(String text, Color ink, String tip)
	{
		JLabel chip = MapCompanionPanel.label(11f, ink);
		chip.setText(text);
		chip.setToolTipText(tip);
		chip.setOpaque(true);
		chip.setBackground(ColorScheme.DARK_GRAY_COLOR);
		chip.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
		return chip;
	}

	/**
	 * A place's name as {@code metrics} draws it: on one line where it fits the card, and otherwise
	 * broken before its last two words. Left to wrap where it ran out, "Morytania Spider Cave eastern
	 * room" broke before "room" and its neighbour before "eastern room"; each now breaks before what
	 * tells it from the others.
	 */
	static String named(String name, FontMetrics metrics)
	{
		int cut = name.lastIndexOf(' ', name.lastIndexOf(' ') - 1);
		if (cut < 0 || metrics.stringWidth(name) < WIDTH)
		{
			return MapCompanionPanel.html(name, WIDTH);
		}
		return "<html><body width='" + WIDTH + "'>" + MapCompanionPanel.escape(name.substring(0, cut))
			+ "<br>" + MapCompanionPanel.escape(name.substring(cut + 1)) + "</body></html>";
	}

	/** A hairline between two places, and between the places, the options and the sources. */
	private static JLabel rule()
	{
		return MapCompanionPanel.capped(MapCompanionPanel.ruled(new JLabel(), 6, 4));
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
		pin.setToolTipText(Words.of("slayer.pinTip"));
		pin.setName(source + ".map");
		pin.addActionListener(e ->
		{
			RelayResponse.Destination next = new RelayResponse.Destination();
			next.x = tile.x; next.y = tile.y; next.plane = tile.plane; next.name = name;
			destinations.selectLocal(next); clientThread.invokeLater(marker::sync);
		});
		pinButtons.add(pin);
		actions.add(pin);
		MapCompanionPanel.capped(actions);
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
		settings.set("slayerOrder", order);
		settings.set("slayerHideWilderness", Boolean.toString(hide));
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
			return "single".equals(order) ? Words.of("slayer.rankingSingle") : "Ranking again for best spot…";
		}
		if (previous.hideWilderness != hide)
		{
			return hide ? Words.of("slayer.rankingSafe") : Words.of("slayer.rankingAll");
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
		return MapCompanionPanel.wrapped(text, 11f, color, WIDTH);
	}
	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return MapCompanionPanel.left(panel);
	}
}
