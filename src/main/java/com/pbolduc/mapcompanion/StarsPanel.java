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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * One native, narrow list of landing sites, with their worlds underneath and favourites first.
 * The controls share the website's meanings; expansion and the selected world stay on this
 * screen. Only a button can pin. A star's world is printed, and hopping is left to
 * the game's own world switcher.
 *
 * <p>The way to the picked star is drawn under it once the map has answered, the same steps and
 * buttons Home's destination card shows, because a star hunter lives on this tab while they hop and
 * sending them to Home for the steps lost their place in the list. It is one route seen twice: the
 * steps are read from {@link DestinationRoute}, and Follow and Reroute are Home's own buttons.
 *
 * <p>Every child of a vertical stack here is left-aligned and as wide as the column. BoxLayout
 * lines children up by their alignment point, so one centred child among left-aligned ones
 * shifts the whole stack right by half its width and the right-hand edge of every line is cut
 * off — which is what the first build of this tab looked like in RuneLite's own look and feel.
 */
final class StarsPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final Color GOLD = new Color(237, 189, 100);
	/** The world switcher's stars, at their own size. */
	private static final int WORLD_STAR = 14;
	private static final Color PICKED = new Color(68, 59, 43);
	private static final Color EDGE = new Color(65, 65, 65);
	private static final Color MUTED = new Color(160, 160, 160);
	private static final Color DANGER = new Color(239, 150, 150);
	/** The column inside the sidebar's 10px border, and the text width that fits it. */
	private static final int COLUMN = PluginPanel.PANEL_WIDTH - 20;
	private static final int TEXT = COLUMN - 10;
	/** A site card's text, beside the favourite and world-count buttons. */
	private static final int CARD_TEXT = COLUMN - 62;
	private final StarsClient service;
	private final DestinationState destinations;
	private final ClientThread clientThread;
	private final DestinationMarker marker;
	private final RoutePress press;
	private final java.util.function.Supplier<String> mapUrl;
	private final DestinationRoute route;
	@Nullable
	private final ShortestPath shortestPath;
	@Nullable
	private final MapCompanionConfig config;
	/**
	 * Home's destination card's Follow (false) or Reroute (true) while that card offers it, else
	 * null: {@link NowCards#hotkey}. The buttons under a routed star press these very buttons, as a
	 * key does, so the rules for when each is offered and pressable are written once, on Home.
	 */
	private final Function<Boolean, JButton> homeButton;
	/** The sidebar's own refresh, so a way chosen here is the way Home's card shows at once. */
	private final Runnable sidebar;
	/** The way to the picked star, kept across rebuilds of the block it sits in; see {@link #selection}. */
	private final RouteStepsView steps;
	private final JCheckBox sync = check("Sync filters with website");
	private final JComboBox<String> tier = new JComboBox<>(new String[]{"T1+", "T2+", "T3+", "T4+", "T5+", "T6+", "T7+", "T8+", "T9+"});
	private final JToggleButton members = toggle("P2P"), free = toggle("F2P"), safe = toggle("Safe");
	private final JToggleButton filters = toggle("⋯");
	private final JPanel more = column(), rows = column(), selection = column();
	private final JLabel levels = label(""), order = label("Most time left first"), count = label(""), status = label("");
	private final JCheckBox wilderness = check("Wilderness"), pvp = check("PvP and high-risk worlds"), deadman = check("Deadman worlds"), depleted = check("Depleted stars");
	/* Under the Hide boxes, each choice says it shows, so the box needs no label of its own. */
	private final JComboBox<String> league = new JComboBox<>(new String[]{"Show main game worlds", "Show League worlds"});
	private final Set<String> expanded = new HashSet<>();
	private boolean selected, active, updating;
	private String rendered;

	/**
	 * @param shortestPath and {@code config}, for the line under the steps saying Shortest Path draws
	 *     a walk; null draws no such line
	 * @param sprites the client's pictures for a step's item or spell, or null to draw words alone
	 * @param homeButton Home's Follow or Reroute while its card offers one; see {@link #homeButton}
	 * @param sidebar the sidebar's refresh
	 */
	StarsPanel(StarsClient service, DestinationState destinations, ClientThread clientThread,
		DestinationMarker marker, RoutePress press, java.util.function.Supplier<String> mapUrl, DestinationRoute route,
		@Nullable ShortestPath shortestPath, @Nullable MapCompanionConfig config, @Nullable GameSprites sprites,
		Function<Boolean, JButton> homeButton, Runnable sidebar)
	{
		this.press = press;
		this.mapUrl = mapUrl;
		this.service = service;
		this.destinations = destinations;
		this.clientThread = clientThread;
		this.marker = marker;
		this.route = route;
		this.shortestPath = shortestPath;
		this.config = config;
		this.homeButton = homeButton;
		this.sidebar = sidebar;
		this.steps = new RouteStepsView(way ->
		{
			route.choose(way);
			sidebar.run();
		}, sprites);
		setName("companionStars");
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		/*
		 * The picked world first, above the filters: its Pin destination and Route are what a pick is
		 * for, and under a list of a dozen sites they were a scroll away from every row but the last.
		 */
		add(selection);
		sync.setName("stars.sync");
		levels.setName("stars.levels");
		count.setName("stars.count");
		status.setName("stars.status");
		league.setName("stars.league");

		/* The tier picker keeps the width its longest value needs; the three chips share the rest. */
		JPanel controls = new JPanel(new GridBagLayout());
		controls.setBackground(getBackground());
		tier.setName("stars.tier");
		tier.setFont(tier.getFont().deriveFont(11f));
		tier.setPrototypeDisplayValue("T9+");
		tier.setToolTipText("Lowest tier shown");
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new Insets(0, 0, 0, 2);
		controls.add(tier, c);
		c.weightx = 1;
		controls.add(members, c);
		controls.add(free, c);
		controls.add(safe, c);
		c.weightx = 0;
		c.insets = new Insets(0, 0, 0, 0);
		controls.add(filters, c);
		add(fill(controls));
		members.setName("stars.members");
		free.setName("stars.free");
		safe.setName("stars.safe");
		members.setToolTipText("Show members worlds");
		free.setToolTipText("Show free-to-play worlds");
		safe.setToolTipText("Hide Wilderness, PvP, high-risk and Deadman");
		filters.setToolTipText("More filters");
		filters.getAccessibleContext().setAccessibleName("More filters");
		filters.setName("stars.filters");

		/*
		 * Sync and the levels the list is filtered by are set once, so they sit with the other
		 * filters set once rather than above the list as daily controls.
		 */
		more.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		more.add(fill(sync));
		more.add(levels);
		more.add(gap(6));
		more.add(heading("Hide"));
		more.add(wilderness);
		more.add(pvp);
		more.add(deadman);
		more.add(depleted);
		league.setFont(league.getFont().deriveFont(11f));
		more.add(fill(league));
		more.setVisible(false);
		add(more);

		add(gap(6));
		JPanel summary = new JPanel(new BorderLayout());
		summary.setBackground(getBackground());
		summary.add(order, BorderLayout.WEST);
		summary.add(count, BorderLayout.EAST);
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setFont(count.getFont().deriveFont(Font.BOLD, 11f));
		add(fill(summary));
		add(status);
		add(gap(2));
		add(rows);
		add(gap(8));
		JLabel source = label("Data from Star Miners");
		source.setHorizontalAlignment(SwingConstants.CENTER);
		add(fill(source));

		sync.addActionListener(e ->
		{
			if (!updating)
			{
				service.sync(sync.isSelected());
				rendered = null;
				refresh();
			}
		});
		tier.addActionListener(e -> change(() -> service.model.preferences.filters.minTier = tier.getSelectedIndex() + 1));
		members.addActionListener(e -> change(() -> {
			StarsModel.Filters f = service.model.preferences.filters;
			f.showMembers = members.isSelected();
			if (!f.showMembers && !f.showFreeToPlay) f.showFreeToPlay = true;
		}));
		free.addActionListener(e -> change(() -> {
			StarsModel.Filters f = service.model.preferences.filters;
			f.showFreeToPlay = free.isSelected();
			if (!f.showMembers && !f.showFreeToPlay) f.showMembers = true;
		}));
		safe.addActionListener(e -> change(() -> service.model.preferences.filters.safe(safe.isSelected())));
		filters.addActionListener(e ->
		{
			more.setVisible(filters.isSelected());
			revalidate();
			repaint();
		});
		wilderness.addActionListener(e -> change(() -> service.model.preferences.filters.hideWilderness = wilderness.isSelected()));
		pvp.addActionListener(e -> change(() -> service.model.preferences.filters.hidePvpWorlds = pvp.isSelected()));
		deadman.addActionListener(e -> change(() -> service.model.preferences.filters.hideDeadmanWorlds = deadman.isSelected()));
		depleted.addActionListener(e -> change(() -> service.model.preferences.filters.hideDepleted = depleted.isSelected()));
		league.addActionListener(e -> change(() -> service.model.preferences.filters.leagueWorlds = league.getSelectedIndex() == 1 ? "only" : "exclude"));
	}

	void selected(boolean on)
	{
		selected = on;
		service.active(active && selected);
	}

	void active(boolean on)
	{
		active = on;
		service.active(active && selected);
	}

	private void change(Runnable change)
	{
		if (updating) return;
		change.run();
		service.changed();
		rendered = null;
		refresh();
	}

	void refresh()
	{
		if (active && selected) service.tick();
		StarsModel model = service.model;
		StarsModel.Filters f = model.preferences.filters;
		updating = true;
		sync.setSelected(service.sync);
		tier.setSelectedIndex(Math.max(0, Math.min(8, f.minTier - 1)));
		members.setSelected(f.showMembers);
		free.setSelected(f.showFreeToPlay);
		safe.setSelected(f.safe());
		wilderness.setSelected(f.hideWilderness);
		pvp.setSelected(f.hidePvpWorlds);
		deadman.setSelected(f.hideDeadmanWorlds);
		depleted.setSelected(f.hideDepleted);
		league.setSelectedIndex("only".equals(f.leagueWorlds) ? 1 : 0);
		league.setVisible(model.feed != null && model.feed.leagueSeason);
		updating = false;
		sync.setToolTipText(syncTip());
		wrap(levels, levelsLine(model.miningLevel, model.skillTotal), TEXT);
		List<StarsModel.Site> sites = model.sites();
		long elapsed = model.elapsed();
		int stars = 0, locations = 0;
		boolean loggedOut = !service.eligible() && active;
		boolean empty = model.feed != null && model.feed.rows.isEmpty();
		StringBuilder signature = new StringBuilder().append(model.feed != null).append(loggedOut).append(empty).append(service.message);
		for (StarsModel.Site site : sites)
		{
			stars += site.worlds.size();
			if (!site.worlds.isEmpty()) locations++;
			signature.append(site.key).append(site.name).append(site.favorite).append(site.hiddenByFilters).append(expanded.contains(site.key));
			for (StarsModel.Star star : site.worlds) signature.append(star.id).append(star.tier(elapsed)).append(StarsModel.minutes(star.left(elapsed))).append((star.ageMs + elapsed) / 60_000);
		}
		/* Nothing is counted, and no age given, until the map has answered once: there is no list yet. */
		count.setText(model.feed == null ? "" : locations + (locations == 1 ? " site · " : " sites · ") + stars + (stars == 1 ? " star" : " stars"));
		/* A list it holds says how old it is, the same when Star Miners or the last refresh failed. */
		String notice = loggedOut ? "Log in and connect to your map to see stars."
			: model.feed == null ? service.message
			: f.safe() && !model.feed.worldsAvailable ? "World list unavailable: PvP and Deadman filters cannot be checked."
			: "Updated " + age(model.feed.snapshotAgeSeconds * 1000 + elapsed) + " · "
				+ (service.message != null ? "couldn't refresh, retrying" : model.feed.stale ? "Star Miners not answering" : "estimates");
		status.setVisible(notice != null);
		if (notice != null)
		{
			wrap(status, notice, TEXT);
		}
		/* Why the refresh failed is a hover away, so the line over the list stays short. */
		status.setToolTipText(model.feed != null && service.message != null ? service.message : null);
		RelayResponse.Destination destination = destinations.getDestination();
		signature.append(model.selected).append(destination == null ? "" : destination.name + destination.world + destination.x + destination.y);
		RoutePress.Said said = press.said(SOURCE);
		signature.append(press.ready()).append(said == null ? "" : said.planning + "|" + said.message);
		/*
		 * The steps are drawn only under the star they lead to, and only the route of the destination
		 * being shown: for a tick after a change the two can differ, as on Home's card. The view says
		 * itself on every refresh, as Home's does, and rebuilds only when its words change.
		 */
		StarsModel.Star picked = model.find(model.selected);
		DestinationRoute.View way = route.view();
		boolean routed = picked != null && model.passes(picked, elapsed) && pins(destination, picked)
			&& way != null && way.hasRoute() && way.key.equals(DestinationRoute.keyOf(destination));
		boolean drawable = routed && config != null && shortestPath != null && config.drawWithShortestPath()
			&& shortestPath.installed() && !way.arrived();
		steps.show(routed ? way : null, routed ? RouteStepsView.shortestPathLine(way, drawable, drawable ? shortestPath.drawing() : 0) : null);
		signature.append(routed).append(offered(routed, false)).append(offered(routed, true));
		if (signature.toString().equals(rendered)) return;
		rendered = signature.toString();
		rows.removeAll();
		selection.removeAll();
		selection.setBorder(null);
		if (model.feed == null)
		{
			if (notice == null) rows.add(label("Loading stars…"));
			revalidate();
			repaint();
			return;
		}
		boolean favoritesHeading = false, restHeading = false;
		for (StarsModel.Site site : sites)
		{
			if (site.favorite && !favoritesHeading)
			{
				rows.add(heading("Favourites"));
				favoritesHeading = true;
			}
			if (!site.favorite && !restHeading && favoritesHeading)
			{
				rows.add(heading("Other stars"));
				restHeading = true;
			}
			if (!favoritesHeading && !restHeading)
			{
				rows.add(gap(6));
				restHeading = true;
			}
			rows.add(site(site, elapsed));
			rows.add(gap(4));
		}
		/* An hour with no stars at all is not the player's filters, and says so. */
		if (stars == 0) rows.add(label(empty ? "No stars reported right now." : "No stars match these filters."));
		/*
		 * The pick heads the tab only while the player's own filters show it: what sits there is what
		 * a press acts on, so it never offers a star the list has just hidden. It is kept, though, and
		 * comes back when the filter lets it.
		 */
		if (picked != null && model.passes(picked, elapsed))
		{
			selection(picked, elapsed, destination, routed);
		}
		revalidate();
		repaint();
	}

	/** Whether the destination is this star: its tile and its world, which a name cannot tell apart. */
	private static boolean pins(@Nullable RelayResponse.Destination destination, StarsModel.Star star)
	{
		return destination != null && destination.world != null && destination.world == star.world
			&& destination.x == star.x && destination.y == star.y;
	}

	/** What Home's Follow or Reroute says now, so a change to it redraws the copy under the star. */
	private String offered(boolean routed, boolean rerouting)
	{
		JButton own = routed ? homeButton.apply(rerouting) : null;
		return own == null ? "|" : "|" + own.getText() + own.isEnabled() + own.getToolTipText();
	}

	private JComponent site(StarsModel.Site site, long elapsed)
	{
		boolean open = expanded.contains(site.key);
		StarsModel.Star primary = site.worlds.isEmpty() ? null : site.worlds.get(0);
		boolean holdsSelection = site.worlds.stream().anyMatch(star -> star.id.equals(service.model.selected));
		JPanel box = column();
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(holdsSelection ? GOLD.darker() : EDGE),
			BorderFactory.createEmptyBorder(5, 6, 5, 4)));
		JPanel head = new JPanel(new BorderLayout(2, 0));
		head.setBackground(box.getBackground());
		String name = "<font color='" + (primary == null ? "#a0a0a0" : primary.wilderness ? "#ef9696" : "#ffffff") + "'>" + escape(site.name) + "</font>";
		String detail = primary == null ? "<font color='#a0a0a0'>" + (site.hiddenByFilters ? "Hidden by filters" : "No star right now") + "</font>"
			: "<font color='#edbd64'>T" + primary.tier(elapsed) + "</font><font color='#b8b8b8'> · W" + primary.world + " · "
				+ StarsModel.timeLeft(primary.left(elapsed)) + (primary.wilderness ? " · Wilderness" : "") + "</font>";
		String text = "<html><body width='" + CARD_TEXT + "'>" + name + "<br>" + detail + "</body></html>";
		if (primary == null)
		{
			JLabel idle = label(text);
			idle.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			head.add(idle, BorderLayout.CENTER);
		}
		else
		{
			JButton pick = flat(text);
			pick.setName("stars.site." + site.key);
			pick.setToolTipText("Select W" + primary.world + " at " + site.name);
			pick.addActionListener(e -> select(primary));
			head.add(pick, BorderLayout.CENTER);
		}
		JPanel actions = new JPanel(new GridBagLayout());
		actions.setBackground(box.getBackground());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTH;
		JButton favorite = flat(site.favorite ? "★" : "☆");
		favorite.setForeground(site.favorite ? GOLD : MUTED);
		favorite.setToolTipText((site.favorite ? "Unfavourite " : "Favourite ") + site.name);
		favorite.setName("stars.favorite." + site.key);
		favorite.getAccessibleContext().setAccessibleName(favorite.getToolTipText());
		favorite.setPreferredSize(new Dimension(20, 20));
		favorite.addActionListener(e -> change(() -> service.model.toggleFavorite(primary == null ? site.key.substring("favorite:".length()) : primary.favoriteKey, site.name, primary == null ? null : primary.siteKey)));
		actions.add(favorite, c);
		if (site.worlds.size() > 1)
		{
			JButton expand = flat(site.worlds.size() + (open ? " ▴" : " ▾"));
			expand.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			expand.setName("stars.expand." + site.key);
			expand.setToolTipText((open ? "Collapse " : "Show ") + site.worlds.size() + " worlds at " + site.name);
			expand.getAccessibleContext().setAccessibleName(expand.getToolTipText());
			expand.setPreferredSize(new Dimension(30, 20));
			expand.addActionListener(e ->
			{
				if (!expanded.remove(site.key)) expanded.add(site.key);
				rendered = null;
				refresh();
			});
			actions.add(expand, c);
		}
		else actions.add(spacer(30, 20), c);
		head.add(actions, BorderLayout.EAST);
		box.add(fill(head));
		if (site.worlds.size() > 1 && open)
		{
			JPanel list = column();
			list.setBackground(box.getBackground());
			list.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, EDGE), BorderFactory.createEmptyBorder(3, 0, 0, 0)));
			for (StarsModel.Star star : site.worlds) list.add(fill(world(site, star, elapsed, box.getBackground())));
			box.add(gap(4, box.getBackground()));
			box.add(fill(list));
		}
		return fill(box);
	}

	/** One world under its site: tier, world, age and time left, in columns that line up. */
	private JButton world(StarsModel.Site site, StarsModel.Star star, long elapsed, Color background)
	{
		boolean picked = star.id.equals(service.model.selected);
		JButton world = new JButton();
		world.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 4, 0, 0);
		c.ipadx = 0;
		world.add(cell("T" + star.tier(elapsed), GOLD, 22), c);
		world.add(cell("W" + star.world, Color.WHITE, 40), c);
		world.add(cell(age(star.ageMs + elapsed), MUTED, 60), c);
		c.weightx = 1;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(0, 0, 0, 4);
		world.add(cell(StarsModel.minutes(star.left(elapsed)), Color.WHITE, 0), c);
		world.setName("stars.world." + star.id);
		world.setBackground(picked ? PICKED : background);
		world.setBorder(picked ? BorderFactory.createLineBorder(GOLD.darker()) : BorderFactory.createEmptyBorder(1, 1, 1, 1));
		world.setContentAreaFilled(picked);
		world.setOpaque(picked);
		world.setFocusPainted(false);
		world.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		world.setPreferredSize(new Dimension(1, 22));
		world.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		world.setToolTipText(site.name + " · W" + star.world + (star.requiredTotal > 0 ? " · " + star.requiredTotal + "+ skill total" : "")
			+ (Boolean.FALSE.equals(star.members) ? " · Free to play" : "") + (star.pvp ? " · PvP / high-risk" : "") + (star.deadman ? " · Deadman" : ""));
		world.getAccessibleContext().setAccessibleName(world.getToolTipText());
		world.addActionListener(e -> select(star));
		return world;
	}

	private void select(StarsModel.Star star)
	{
		service.model.selected = star.id;
		rendered = null;
		refresh();
		/* A pick far down the list scrolls up to its actions; one already in view stays put. */
		SwingUtilities.invokeLater(() -> selection.scrollRectToVisible(new Rectangle(0, 0, selection.getWidth(), selection.getHeight())));
	}

	private void selection(StarsModel.Star star, long elapsed, @Nullable RelayResponse.Destination destination, boolean routed)
	{
		boolean pinned = pins(destination, star);
		selection.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0), BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE)),
			BorderFactory.createEmptyBorder(0, 0, 8, 0)));
		selection.add(heading(pinned ? "Pinned destination" : "Selected world"));
		JLabel name = label("");
		wrap(name, star.name, TEXT);
		name.setForeground(GOLD);
		name.setFont(name.getFont().deriveFont(12f));
		selection.add(name);
		selection.add(label("W" + star.world + " · T" + star.tier(elapsed) + " · " + StarsModel.timeLeft(star.left(elapsed))));
		selection.add(gap(5));
		if (routed)
		{
			/*
			 * The way there in a card, as Home draws it, between the star's own live line and the
			 * buttons that act on the way. On Home the view's top inset parts it from the card's name;
			 * here it opens the card, so the card's own is the smaller.
			 */
			JPanel way = MapCompanionPanel.card();
			way.setBorder(BorderFactory.createEmptyBorder(2, 8, 7, 8));
			way.add(steps);
			selection.add(fill(way));
			selection.add(gap(5));
			JPanel actions = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
			actions.setBackground(getBackground());
			home(actions, false, "stars.follow");
			home(actions, true, "stars.reroute");
			actions.add(unpin());
			selection.add(fill(actions));
		}
		else
		{
			JPanel actions = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
			actions.setBackground(getBackground());
			if (!pinned)
			{
				JButton pin = new JButton("Pin destination");
				pin.setName("stars.pin");
				pin.setEnabled(star.precise && star.left(elapsed) > 0);
				pin.addActionListener(e -> {
					RelayResponse.Destination next = new RelayResponse.Destination();
					next.x = star.x;
					next.y = star.y;
					next.plane = star.plane;
					next.name = star.name;
					next.world = star.world;
					/*
					 * No note. The destination's card writes it once and never again, and a tier and a time
					 * left age by the minute; the world is printed from its own field, and this tab keeps the
					 * star's line live.
					 */
					destinations.selectLocal(next);
					clientThread.invokeLater(marker::sync);
					rendered = null;
					refresh();
				}); primary(pin);
				actions.add(pin);
			}
			/*
			 * Beside the pin, the way there: by the star's own tile, which is the one the game draws.
			 * A site whose tile is a guess gets the map instead, which can say where it might be. A star
			 * pinned with no way yet keeps it, beside Unpin, so the way there is still one press.
			 */
			if (star.precise)
			{
				RoutePress.Said said = press.said(SOURCE);
				boolean planning = said != null && said.planning;
				JButton route = new JButton(planning ? "Planning…" : "Route");
				route.setName("stars.route");
				route.setFont(route.getFont().deriveFont(11f));
				route.setMargin(new Insets(4, 4, 4, 4));
				route.setEnabled(star.left(elapsed) > 0 && !planning && press.ready());
				route.setToolTipText("Plan the way to this star from where you stand. The world is printed in the steps; "
					+ "hopping is left to the game's world switcher.");
				route.addActionListener(e ->
				{
					/* No note, as Pin destination writes none: nothing on the card may age. */
					press.press(RoutePress.Target.tile(SOURCE, new net.runelite.api.coords.WorldPoint(star.x, star.y, star.plane),
						star.name, star.world, null));
					rendered = null;
					refresh();
				});
				actions.add(route);
			}
			else
			{
				JButton open = new JButton(MapCompanionPanel.OPEN_WEBSITE);
				open.setName("stars.open");
				open.setFont(open.getFont().deriveFont(11f));
				open.setMargin(new Insets(4, 4, 4, 4));
				open.setToolTipText("Show the possible spots on osrs-map.com in your browser");
				open.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(mapUrl.get() + "/map?q="
					+ java.net.URLEncoder.encode(star.name, java.nio.charset.StandardCharsets.UTF_8)));
				actions.add(open);
			}
			if (pinned)
			{
				actions.add(unpin());
			}
			selection.add(fill(actions));
			if (!star.precise && !pinned)
			{
				JLabel note = label("");
				wrap(note, "This spot is approximate. Open website shows where it might be.", TEXT);
				selection.add(note);
			}
			RoutePress.Said said = press.said(SOURCE);
			if (said != null && said.message != null)
			{
				JLabel line = label("");
				wrap(line, said.message, TEXT);
				selection.add(line);
			}
		}
	}

	/** Unpin, where Dismiss sits on Home's card: the same act, and the map is not told. */
	private JButton unpin()
	{
		JButton unpin = button("Unpin");
		unpin.setName("stars.unpin");
		unpin.setToolTipText("Unpin destination");
		unpin.addActionListener(e ->
		{
			destinations.dismiss();
			clientThread.invokeLater(marker::sync);
			rendered = null;
			refresh();
		});
		return unpin;
	}

	/**
	 * Home's own Follow or Reroute under the routed star, while Home's card offers it: its words, its
	 * tip and whether it can be pressed now, and a press of that very button, as a key presses it.
	 */
	private void home(JPanel actions, boolean rerouting, String name)
	{
		JButton own = homeButton.apply(rerouting);
		if (own == null)
		{
			return;
		}
		JButton here = button(own.getText());
		here.setName(name);
		here.setToolTipText(own.getToolTipText());
		here.setEnabled(own.isEnabled());
		here.addActionListener(e -> MapCompanionPanel.press(homeButton.apply(rerouting)));
		actions.add(here);
	}

	/** Which Route button a press came from, so its answer is said beside it. */
	static final String SOURCE = "star";

	private static void primary(JButton button)
	{
		button.setFont(button.getFont().deriveFont(Font.BOLD, 11f));
		button.setMargin(new Insets(4, 4, 4, 4));
		button.setBackground(new Color(117, 94, 52));
		button.setForeground(new Color(255, 240, 209));
	}

	/** What the sync box does now, for the map this sidebar is actually paired with. */
	private String syncTip()
	{
		String map = mapUrl.get().replaceFirst("^https?://", "");
		if (!service.sync)
		{
			return "These filters are kept for this character. Tick to keep them the same here and on " + map + ".";
		}
		return service.syncReady ? "Your filters and favourites are the same here and on " + map + "."
			: "Not synced yet; these filters are kept for this character.";
	}

	/**
	 * The levels said as what they hide, since that is why they are shown: a star needs ten Mining a
	 * tier, and a total world its total. A level not read yet hides nothing.
	 */
	static String levelsLine(Integer mining, Integer total)
	{
		String tiers = mining == null ? null
			: "For Mining " + mining + ": " + (mining >= 90 ? "every tier" : mining < 10 ? "T1 needs 10" : "up to T" + mining / 10);
		if (total == null)
		{
			return tiers == null ? "Levels not read yet: every tier and world shows" : tiers;
		}
		return tiers == null ? "Worlds up to " + total + " total" : tiers + " · worlds up to " + total + " total";
	}

	private static String age(long ms)
	{
		return ms < 60_000 ? "just now" : ms / 60_000 + "m ago";
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/** The width is an HTML attribute, not CSS: the CSS form does not constrain a JLabel and clips. */
	private static void wrap(JLabel label, String text, int width)
	{
		String html = "<html><body width='" + width + "'>" + escape(text) + "</body></html>";
		if (!html.equals(label.getText())) label.setText(html);
	}

	private static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return left(panel);
	}

	/** One component the full width of the column, at its own height. */
	private static JComponent fill(JComponent component)
	{
		JPanel holder = new Row();
		holder.setOpaque(false);
		holder.add(component, BorderLayout.CENTER);
		return left(holder);
	}

	/**
	 * A holder whose height is its component's as laid out, not as built. The P2P and F2P stars land
	 * after the filter row is built and make it taller; under a maximum fixed before that, BoxLayout
	 * would cut the row and squeeze the rest of the tab, Data from Star Miners off the bottom first.
	 */
	private static final class Row extends JPanel
	{
		private static final long serialVersionUID = 1L;

		Row()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static JComponent gap(int height)
	{
		return gap(height, ColorScheme.DARK_GRAY_COLOR);
	}

	private static JComponent gap(int height, Color background)
	{
		JPanel gap = new JPanel();
		gap.setBackground(background);
		gap.setPreferredSize(new Dimension(1, height));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		/* A plain panel's FlowLayout asks for 10px at least, and a squeezed column grows a gap to that. */
		gap.setMinimumSize(new Dimension(0, height));
		return left(gap);
	}

	private static JComponent spacer(int width, int height)
	{
		JPanel box = new JPanel();
		box.setOpaque(false);
		box.setPreferredSize(new Dimension(width, height));
		return box;
	}

	private static JLabel cell(String text, Color color, int width)
	{
		JLabel cell = new JLabel(text);
		cell.setFont(cell.getFont().deriveFont(11f));
		cell.setForeground(color);
		if (width > 0) cell.setPreferredSize(new Dimension(width, cell.getPreferredSize().height));
		return cell;
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(11f));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return left(label);
	}

	private static JComponent heading(String text)
	{
		JLabel label = label(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
		return label;
	}

	/** A button that reads as its text, for a card's own controls. */
	private static JButton flat(String text)
	{
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(11f));
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	/** One of the picked world's buttons, in small type with the inset Route has always had. */
	private static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(11f));
		button.setMargin(new Insets(4, 4, 4, 4));
		return button;
	}

	/**
	 * The game's own world-switcher stars on the P2P and F2P chips, as the website's chips carry them:
	 * the gold star a members world shows and the silver one a free world shows. The words stay, so
	 * a chip still says what it filters with no picture loaded.
	 */
	void pictures(GameSprites sprites)
	{
		members.setIconTextGap(3);
		free.setIconTextGap(3);
		sprites.sprite(members, SpriteID.WorldswitcherStars.MEMBERS, WORLD_STAR);
		sprites.sprite(free, SpriteID.WorldswitcherStars.FREE, WORLD_STAR);
	}

	private static JToggleButton toggle(String text)
	{
		JToggleButton button = new JToggleButton(text);
		button.setFont(button.getFont().deriveFont(11f));
		button.setMargin(new Insets(3, 2, 3, 2));
		return button;
	}

	private static JCheckBox check(String text)
	{
		JCheckBox check = new JCheckBox(text);
		check.setFont(check.getFont().deriveFont(11f));
		check.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return left(check);
	}
}
