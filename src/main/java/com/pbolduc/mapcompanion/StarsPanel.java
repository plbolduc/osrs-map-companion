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
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
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
import static com.pbolduc.mapcompanion.MapCompanionPanel.refont;
import static com.pbolduc.mapcompanion.StarsModel.Star;

/**
 * One native, narrow list of landing sites, with their worlds underneath and favourites first.
 * The controls share the website's meanings; expansion and the picked world stay on this
 * screen. A star's world is printed, and hopping is left to the game's own world switcher.
 *
 * <p>A press on a star picks it, and its card opens on Route and Pin under the star itself: the
 * buttons are where the pointer already is, and the card above them says which star they act on.
 * Only those buttons pin or ask the map anything. A pick alone does neither, since a Route is a
 * question to the map that replaces whatever is pinned, and a list is there to be looked through.
 *
 * <p>The pinned star heads the tab, whatever is picked and whatever the filters hide, because what
 * is pinned is being walked to. Its way is drawn under it once the map has answered, the same steps
 * and buttons Home's destination card shows, because a star hunter lives on this tab while they hop
 * and sending them to Home for the steps lost their place in the list. It is one route seen twice:
 * the steps are read from {@link DestinationRoute}, and Follow and Reroute are Home's own buttons.
 *
 * <p>Every child of a vertical stack here is left-aligned and as wide as the column. BoxLayout
 * lines children up by their alignment point, so one centred child among left-aligned ones
 * shifts the whole stack right by half its width and the right-hand edge of every line is cut
 * off — which is what the first build of this tab looked like in RuneLite's own look and feel.
 */
final class StarsPanel extends JPanel
{
	private static final Color GOLD = new Color(237, 189, 100);
	/** The world switcher's stars, at their own size. */
	private static final int WORLD_STAR = 14;
	/** The picked world's row: dark enough that every tier's colour on it still clears 4.5:1. */
	static final Color PICKED = new Color(48, 42, 30);
	/**
	 * A tier in the map's own colour for it, T1 to T9: the dark ramp of the website's
	 * lib/tier-color.ts, so a T7 is one colour here and in the map's star list.
	 */
	static final Color[] TIERS = {
		new Color(0x94a3b8), new Color(0x22c55e), new Color(0x84cc16), new Color(0xeab308), new Color(0xf97316),
		new Color(0xf87171), new Color(0xf472b6), new Color(0xc084fc), new Color(0x818cf8)
	};
	/**
	 * A time left under twenty minutes, and under five, in the website's colours for them: Tailwind's
	 * amber-400 and red-400 as the map's stylesheet writes them for sRGB, #fcbb00 and #ff6568. The red
	 * is deeper than the pink the sidebar writes a refusal and the Wilderness in, so neither is taken
	 * for the other. Twenty minutes or more keeps the line's own ink, so a colour only ever says hurry.
	 */
	static final Color FADING_TONE = new Color(252, 187, 0);
	static final Color DYING_TONE = new Color(255, 101, 104);
	/** A time left with time to spare, and the rest of a card's small print. */
	private static final Color CARD_INK = new Color(184, 184, 184);
	/** The world the player is on: the Group tab's green for a meetup's. */
	static final Color HERE = new Color(0x34d399);
	private static final Color EDGE = new Color(65, 65, 65);
	private static final Color MUTED = new Color(160, 160, 160);
	/** The column inside the sidebar's 10px border, and the text width that fits it. */
	private static final int COLUMN = PluginPanel.PANEL_WIDTH - 20;
	private static final int TEXT = COLUMN - 10;
	/** A line the width of a site card's inside. */
	private static final int CARD_WIDE = COLUMN - 14;
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
	 * null: {@link NowCards#actButton}. The buttons under a routed star press these very buttons, as a
	 * key does, so the rules for when each is offered and pressable are written once, on Home.
	 */
	private final Function<Boolean, JButton> homeButton;
	/** The sidebar's own refresh, so a way chosen here is the way Home's card shows at once. */
	private final Runnable sidebar;
	/** The client's pictures, for the star beside a world; null draws a W there. */
	@Nullable
	private final GameSprites sprites;
	/** The way to the picked star, kept across rebuilds of the block it sits in; see {@link #selection}. */
	private final RouteStepsView steps;
	private final JComboBox<String> tier = new JComboBox<>(new String[]{"T1+", "T2+", "T3+", "T4+", "T5+", "T6+", "T7+", "T8+", "T9+"});
	private final JToggleButton members = chip("P2P"), free = chip("F2P"), safe = chip("Safe");
	private final JToggleButton filters = toggle("⋯");
	private final JPanel more = column(), rows = column(), selection = column();
	private final JLabel levels = label(""), order = label("Most time left first"), count = label(""), status = label("");
	/**
	 * The game the list is about, in gold over it, only when it is not the main game: on a Hardcore
	 * or League world the list is a different one, and nothing else on the tab would say why.
	 */
	private final JLabel game = label("");
	private final JCheckBox wilderness = check("Wilderness", (f, on) -> f.hideWilderness = on),
		pvp = check("PvP and high-risk worlds", (f, on) -> f.hidePvpWorlds = on),
		deadman = check("Deadman worlds", (f, on) -> f.hideDeadmanWorlds = on),
		depleted = check("Depleted stars", (f, on) -> f.hideDepleted = on);
	private final Set<String> expanded = new HashSet<>();
	private boolean selected, active, updating;
	private String rendered;
	/**
	 * Each Route drawn and the line the last press left, while they are drawn: a wait counts down in
	 * them on every refresh, where rebuilding the list each second would eat a press.
	 */
	private final List<JButton> starRoutes = new java.util.ArrayList<>();
	@Nullable
	private JLabel starSaid;
	/** The star Route was last pressed for, which is where what the press said is drawn. */
	@Nullable
	private String asked;
	/** The star pinned at the last refresh; see {@link #refresh}. */
	@Nullable
	private String wasPinned;
	/** The picked star's card, to bring its buttons into view. */
	@Nullable
	private JComponent pickedCard;
	private static final String STAR_ROUTE_TIP = Words.of("stars.routeTip");

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
		this.sprites = sprites;
		this.steps = new RouteStepsView(way ->
		{
			route.choose(way);
			sidebar.run();
		}, sprites);
		setName("companionStars");
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		/* The pinned star first, above the filters: it is the one being walked to. */
		add(selection);
		levels.setName("stars.levels");
		count.setName("stars.count");
		status.setName("stars.status");
		game.setName("stars.game");

		/* The tier picker keeps the width its longest value needs; the three chips share the rest. */
		JPanel controls = new JPanel(new GridBagLayout());
		controls.setBackground(getBackground());
		tier.setName("stars.tier");
		refont(tier, 11f);
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
		safe.setToolTipText(Words.of("stars.safeTip"));
		filters.setToolTipText("More filters");
		filters.getAccessibleContext().setAccessibleName("More filters");
		filters.setName("stars.filters");

		/*
		 * The levels the list is filtered by are read once, so they sit with the filters set once
		 * rather than above the list as daily controls.
		 */
		more.setBorder(new EmptyBorder(6, 0, 0, 0));
		more.add(levels);
		more.add(gap(6));
		more.add(heading("Hide"));
		more.add(wilderness);
		more.add(pvp);
		more.add(deadman);
		more.add(depleted);
		more.setVisible(false);
		add(more);

		add(gap(6));
		game.setForeground(GOLD);
		refont(game, 11f);
		game.setVisible(false);
		add(fill(game));
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		refont(count, Font.BOLD, 11f);
		add(fill(MapCompanionPanel.bar(0, getBackground(), order, null, count)));
		add(status);
		add(gap(2));
		add(rows);
		add(gap(8));
		JLabel source = label("Data from Star Miners");
		source.setHorizontalAlignment(SwingConstants.CENTER);
		add(fill(source));

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
		redraw();
	}

	/** Draws the list again even where nothing it is drawn from has changed. */
	private void redraw()
	{
		rendered = null;
		refresh();
	}

	void refresh()
	{
		if (active && selected) service.tick();
		StarsModel model = service.model;
		StarsModel.Filters f = model.preferences.filters;
		updating = true;
		tier.setSelectedIndex(Math.max(0, Math.min(8, f.minTier - 1)));
		members.setSelected(f.showMembers);
		free.setSelected(f.showFreeToPlay);
		safe.setSelected(f.safe());
		outline(members);
		outline(free);
		outline(safe);
		wilderness.setSelected(f.hideWilderness);
		pvp.setSelected(f.hidePvpWorlds);
		deadman.setSelected(f.hideDeadmanWorlds);
		depleted.setSelected(f.hideDepleted);
		updating = false;
		String gameName = gameName(model.playerGame);
		game.setText(gameName == null ? "" : gameName);
		game.setVisible(gameName != null && model.feed != null);
		wrap(levels, levelsLine(model.miningLevel, model.skillTotal), TEXT);
		List<StarsModel.Site> sites = model.sites();
		long elapsed = model.elapsed();
		int stars = 0, locations = 0;
		boolean loggedOut = !service.eligible() && active;
		boolean empty = model.feed != null && model.feed.rows.isEmpty();
		StringBuilder signature = new StringBuilder().append(model.feed != null).append(loggedOut).append(empty).append(service.message)
			.append(model.playerGame).append(model.playerWorld);
		for (StarsModel.Site site : sites)
		{
			stars += site.worlds.size();
			if (!site.worlds.isEmpty()) locations++;
			signature.append(site.key).append(site.name).append(site.favorite).append(site.hiddenByFilters).append(expanded.contains(site.key));
			for (Star star : site.worlds) signature.append(star.id).append(star.tier(elapsed)).append(StarsModel.minutes(star.left(elapsed))).append((star.ageMs + elapsed) / 60_000);
		}
		/* Nothing is counted, and no age given, until the map has answered once: there is no list yet. */
		count.setText(model.feed == null ? "" : locations + (locations == 1 ? " site · " : " sites · ") + stars + (stars == 1 ? " star" : " stars"));
		/* A list it holds says how old it is, the same when Star Miners or the last refresh failed. */
		/* The tab is a member's, so the group is there: logging in is the one thing left to do. */
		String notice = loggedOut ? LOG_IN
			: model.feed == null ? service.message
			: f.safe() && !model.feed.worldsAvailable ? Words.of("stars.noWorldList")
			: "Updated " + age(model.feed.snapshotAgeSeconds * 1000 + elapsed) + " · "
				+ (service.message != null ? "couldn't refresh, retrying" : model.feed.stale ? "Star Miners not answering" : "estimates");
		status.setVisible(notice != null);
		if (notice != null)
		{
			wrap(status, notice, TEXT);
		}
		/* Pink only for why there is no list: a held list, and how old it is, is not a refusal. */
		MapCompanionPanel.tone(status, !loggedOut && model.feed == null && service.message != null);
		/* Why the refresh failed is a hover away, so the line over the list stays short. */
		status.setToolTipText(model.feed != null && service.message != null ? service.message : null);
		RelayResponse.Destination destination = destinations.getDestination();
		Star pinned = null;
		for (Star star : model.feed == null ? List.<Star>of() : model.feed.rows)
		{
			if (pinned == null && pins(destination, star))
			{
				pinned = star;
			}
		}
		/*
		 * A pick ends with its pin: reached, unpinned or replaced, the star goes back to a plain row
		 * rather than opening on Route and Pin for the place the player has just left or let go.
		 */
		String pinnedNow = pinned == null ? null : pinned.id;
		if (wasPinned != null && !wasPinned.equals(pinnedNow) && wasPinned.equals(model.selected))
		{
			model.selected = null;
		}
		/* A star pinned from far down the list brings its way, at the head of the tab, into view. */
		boolean headed = selected && pinnedNow != null && !pinnedNow.equals(wasPinned);
		wasPinned = pinnedNow;
		signature.append(model.selected).append(destination == null ? "" : destination.name + destination.world + destination.x + destination.y);
		RoutePress.Said said = press.said(SOURCE);
		signature.append(press.ready()).append(said == null ? "" : said.planning + "|" + said.message).append(asked);
		/*
		 * The steps are drawn only under the star they lead to, and only the route of the destination
		 * being shown: for a tick after a change the two can differ, as on Home's card. The view says
		 * itself on every refresh, as Home's does, and rebuilds only when its words change.
		 */
		DestinationRoute.View way = route.view();
		boolean routed = pinned != null && way != null && way.hasRoute() && way.key.equals(DestinationRoute.keyOf(destination));
		boolean drawable = routed && config != null && shortestPath != null && config.drawWithShortestPath()
			&& shortestPath.installed() && !way.arrived();
		steps.show(routed ? way : null, routed ? RouteStepsView.shortestPathLine(way, drawable, drawable ? shortestPath.drawing() : 0) : null);
		signature.append(routed).append(offered(routed, false)).append(offered(routed, true));
		if (signature.toString().equals(rendered))
		{
			countDown(said);
			return;
		}
		rendered = signature.toString();
		rows.removeAll();
		selection.removeAll();
		starRoutes.clear();
		starSaid = null;
		pickedCard = null;
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
			rows.add(site(site, elapsed, pinned));
			rows.add(gap(4));
		}
		/* An hour with no stars at all, or none in the player's game, is not the player's filters, and says so. */
		if (stars == 0)
		{
			/* Wrapped, because the game's sentence is longer than the column the other two fit in. */
			JLabel none = label("");
			wrap(none, empty ? "No stars reported right now."
				: model.noneInGame() ? noneInGame(model.playerGame) : "No stars match these filters.", TEXT);
			rows.add(none);
		}
		if (pinned != null)
		{
			pinned(pinned, elapsed, routed);
		}
		countDown(said);
		revalidate();
		repaint();
		if (headed)
		{
			SwingUtilities.invokeLater(() -> selection.scrollRectToVisible(new Rectangle(0, 0, selection.getWidth(), selection.getHeight())));
		}
	}

	/** The picked star's Route and its line as of now: how long the map's wait has left, in each. */
	private void countDown(@Nullable RoutePress.Said said)
	{
		long now = System.currentTimeMillis();
		for (JButton starRoute : starRoutes)
		{
			MapWait.tip(starRoute, STAR_ROUTE_TIP, press.waitMs(now));
		}
		if (starSaid != null && said != null && said.message != null)
		{
			wrap(starSaid, press.words(said, now), CARD_WIDE);
		}
	}

	/** Whether the destination is this star: its tile and its world, which a name cannot tell apart. */
	private static boolean pins(@Nullable RelayResponse.Destination destination, Star star)
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

	private JComponent site(StarsModel.Site site, long elapsed, @Nullable Star pinned)
	{
		boolean open = expanded.contains(site.key);
		Star primary = site.worlds.isEmpty() ? null : site.worlds.get(0);
		/* The world picked here, which the list shows: the first, or any of them once they are listed. */
		Star picked = site.worlds.stream().filter(star -> star.id.equals(service.model.selected)).findFirst().orElse(null);
		JPanel box = column();
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new CompoundBorder(
			new LineBorder(picked != null || site.worlds.contains(pinned) ? GOLD.darker() : EDGE),
			new EmptyBorder(4, 6, 4, 4)));
		Color ground = box.getBackground();
		JLabel name = label("");
		wrap(name, site.name, CARD_WIDE - 22);
		refont(name, 12f);
		name.setForeground(primary == null ? MUTED : primary.wilderness ? MapCompanionPanel.REFUSED : Color.WHITE);
		JButton favorite = flat(site.favorite ? "★" : "☆");
		favorite.setForeground(site.favorite ? GOLD : MUTED);
		favorite.setToolTipText((site.favorite ? "Unfavourite " : "Favourite ") + site.name);
		favorite.setName("stars.favorite." + site.key);
		favorite.getAccessibleContext().setAccessibleName(favorite.getToolTipText());
		favorite.setPreferredSize(new Dimension(20, 16));
		favorite.addActionListener(e -> change(() -> service.model.toggleFavorite(primary == null ? site.key.substring("favorite:".length()) : primary.favoriteKey, site.name, primary == null ? null : primary.siteKey)));
		JPanel top = MapCompanionPanel.bar(2, ground, null, name, MapCompanionPanel.hug(favorite, ground));
		JComponent head;
		if (primary == null)
		{
			JLabel idle = cell(site.hiddenByFilters ? "Hidden by filters" : "No star right now", MUTED, 0);
			head = MapCompanionPanel.bar(0, ground, null, top, null);
			head.add(idle, BorderLayout.SOUTH);
		}
		else
		{
			JButton expand = null;
			if (site.worlds.size() > 1)
			{
				/* How many more worlds than the one printed beside it. */
				expand = flat("+" + (site.worlds.size() - 1) + (open ? " ▴" : " ▾"));
				expand.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				expand.setName("stars.expand." + site.key);
				expand.setToolTipText((open ? "Collapse " : "Show ") + site.worlds.size() + " worlds at " + site.name);
				expand.getAccessibleContext().setAccessibleName(expand.getToolTipText());
				expand.addActionListener(e ->
				{
					if (!expanded.remove(site.key))
					{
						expanded.add(site.key);
					}
					else if (picked != primary)
					{
						/* Folded, the card names its first world alone, so a pick of another lets go. */
						service.model.selected = null;
					}
					redraw();
				});
			}
			/*
			 * The whole card is the press, with the favourite and the fold inside it as buttons of
			 * their own: the name alone was a target two lines tall beside two more.
			 */
			JButton pick = MapCompanionPanel.bare(new Whole(), 0, 0, 0);
			pick.setFocusPainted(false);
			pick.setLayout(new BorderLayout(0, 2));
			pick.setName("stars.site." + site.key);
			pick.setToolTipText("W" + primary.world + " at " + site.name + ": press for Route and Pin");
			pick.getAccessibleContext().setAccessibleName(pick.getToolTipText());
			pick.addActionListener(e -> select(primary));
			pick.add(top, BorderLayout.CENTER);
			pick.add(facts(primary, elapsed, ground, expand, primary.wilderness ? cell("Wilderness", MapCompanionPanel.REFUSED, 0) : null), BorderLayout.SOUTH);
			head = pick;
		}
		box.add(fill(head));
		if (site.worlds.size() > 1 && open)
		{
			JPanel list = column();
			list.setBackground(box.getBackground());
			list.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, EDGE), new EmptyBorder(3, 0, 0, 0)));
			for (Star star : site.worlds) list.add(fill(world(site, star, elapsed, box.getBackground())));
			box.add(MapCompanionPanel.gap(4, box.getBackground()));
			box.add(fill(list));
		}
		/* The pinned star's buttons head the tab, so its card offers none of its own. */
		if (picked != null && picked != pinned)
		{
			act(box, picked, elapsed);
			pickedCard = box;
		}
		return fill(box);
	}

	/** One world under its site: the card's own line for it, with its age where a card says Wilderness. */
	private JButton world(StarsModel.Site site, Star star, long elapsed, Color background)
	{
		boolean picked = star.id.equals(service.model.selected);
		Color ground = picked ? PICKED : background;
		JButton world = new Whole();
		world.setLayout(new BorderLayout());
		world.add(facts(star, elapsed, ground, null, cell(age(star.ageMs + elapsed), MUTED, 0)));
		world.setName("stars.world." + star.id);
		world.setBackground(ground);
		world.setBorder(new CompoundBorder(
			picked ? new LineBorder(GOLD.darker()) : new EmptyBorder(1, 1, 1, 1),
			new EmptyBorder(2, 3, 2, 3)));
		world.setContentAreaFilled(picked);
		world.setOpaque(picked);
		world.setFocusPainted(false);
		world.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		world.setToolTipText(site.name + " · W" + star.world + (star.requiredTotal > 0 ? " · " + star.requiredTotal + "+ skill total" : "")
			+ (Boolean.FALSE.equals(star.members) ? " · Free to play" : "") + (star.pvp ? " · PvP / high-risk" : "") + (star.deadman ? " · Deadman" : ""));
		world.getAccessibleContext().setAccessibleName(world.getToolTipText());
		world.addActionListener(e -> select(star));
		return world;
	}

	/**
	 * A star's three facts on one line, the same wherever a star is printed: its tier in bold in the
	 * tier's own colour, its world in bold, and its time left at the far end. The tier and the world
	 * are what a list of stars is scanned for, so they are the two things set in bold; the tiers line
	 * up down the list's left edge and the times down its right. The tier is letters alone, on no
	 * ground of its own: beside the world's star, a filled block of colour on every row would be
	 * the loudest thing in the list.
	 *
	 * <p>The world is its number behind the world switcher's own star for its kind, as the game lists
	 * a world, and green while the player is on it, as the Group tab draws a meetup's: a star that
	 * needs no hop. A world of no known kind keeps a W in the star's place. Labels only, so a press
	 * anywhere on the line is a press on the row it is in.
	 *
	 * @param after the fold beside the world, or null
	 * @param between what sits between the world and the time, cut short before either is: a site's
	 *     Wilderness, a world's age, or null
	 */
	private JComponent facts(Star star, long elapsed, Color ground, @Nullable JComponent after, @Nullable JLabel between)
	{
		int level = star.tier(elapsed);
		JLabel tier = cell("T" + level, tierColour(level), 0);
		tier.setName("star.tier");
		refont(tier, Font.BOLD, 12f);
		tier.setPreferredSize(new Dimension(15, 16));
		Integer here = service.model.playerWorld;
		Integer kind = sprites == null ? null : worldStar(star);
		JLabel world = cell((kind == null ? "W" : "") + star.world, here != null && here == star.world ? HERE : Color.WHITE, 0);
		world.setName("star.world");
		refont(world, Font.BOLD, 12f);
		JComponent at = world;
		if (kind != null)
		{
			/* Its place is kept at the star's size, so the number does not move when the cache answers. */
			JLabel mark = new JLabel();
			mark.setName("star.kind");
			mark.setPreferredSize(new Dimension(WORLD_STAR, WORLD_STAR));
			sprites.sprite(mark, kind, WORLD_STAR);
			at = MapCompanionPanel.bar(3, ground, mark, world, null);
			at.setOpaque(false);
		}
		JLabel left = cell(StarsModel.timeLeft(star.left(elapsed)), lifeColour(star.left(elapsed), CARD_INK), 0);
		left.setName("star.left");
		JPanel lead = MapCompanionPanel.bar(6, ground, tier, at, after);
		JPanel line = MapCompanionPanel.bar(6, ground, lead, between, left);
		line.setOpaque(false);
		lead.setOpaque(false);
		return line;
	}

	/**
	 * The world switcher's star for a star's world, or null where the map's world list did not have the
	 * world. The website's rule, lib/world-badges.ts: Deadman before PvP and high-risk before members,
	 * because the most particular kind is the one that matters, and no star rather than the free one
	 * for a world nobody has described.
	 */
	@Nullable
	static Integer worldStar(Star star)
	{
		if (star.deadman)
		{
			return SpriteID.WorldswitcherStars.DEADMAN;
		}
		if (star.pvp)
		{
			return SpriteID.WorldswitcherStars.PVP;
		}
		if (star.members == null)
		{
			return null;
		}
		return star.members ? SpriteID.WorldswitcherStars.MEMBERS : SpriteID.WorldswitcherStars.FREE;
	}

	/** A button that is the components laid out in it, and as big as they are; a button's own size is its text's. */
	private static final class Whole extends JButton
	{
		@Override
		public Dimension getPreferredSize()
		{
			return getLayout().preferredLayoutSize(this);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** A press on a star picks it, and a second press lets it go. */
	private void select(Star star)
	{
		service.model.selected = star.id.equals(service.model.selected) ? null : star.id;
		redraw();
		/* A pick at the foot of the view scrolls its buttons into it; one already in view stays put. */
		SwingUtilities.invokeLater(() ->
		{
			if (pickedCard != null)
			{
				pickedCard.scrollRectToVisible(new Rectangle(0, 0, pickedCard.getWidth(), pickedCard.getHeight()));
			}
		});
	}

	/**
	 * The picked star's buttons, at the foot of its own card. Route first: it pins the star as well
	 * and is what most picks are for. Pin is the arrow and the world-map pin alone, with no question
	 * to the map. A site whose tile is a guess gets the map instead, which can say where it might be.
	 */
	private void act(JPanel box, Star star, long elapsed)
	{
		JPanel actions = MapCompanionPanel.row(box.getBackground());
		if (star.precise)
		{
			actions.add(route(star, elapsed));
			JButton pin = button("Pin");
			pin.setName("stars.pin");
			pin.setToolTipText(Words.of("stars.pinTip"));
			pin.setEnabled(star.left(elapsed) > 0);
			pin.addActionListener(e ->
			{
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
				redraw();
			});
			actions.add(pin);
		}
		else
		{
			JButton open = button(MapCompanionPanel.OPEN_WEBSITE);
			open.setName("stars.open");
			open.setToolTipText(Words.of("stars.openTip"));
			open.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(mapUrl.get() + "/map?q="
				+ java.net.URLEncoder.encode(star.name, java.nio.charset.StandardCharsets.UTF_8)));
			actions.add(open);
		}
		box.add(MapCompanionPanel.gap(5, box.getBackground()));
		box.add(fill(actions));
		if (!star.precise)
		{
			JLabel note = label("");
			wrap(note, Words.of("stars.approximate"), CARD_WIDE);
			box.add(note);
		}
		said(box, star);
	}

	/**
	 * Route, by the star's own tile, which is the one the game draws. The answer pins the star, so the
	 * way there is one press whether or not it was pinned first.
	 */
	private JButton route(Star star, long elapsed)
	{
		RoutePress.Said said = press.said(SOURCE);
		boolean planning = said != null && said.planning && star.id.equals(asked);
		JButton route = button(planning ? "Planning…" : "Route");
		route.setName("stars.route");
		route.setEnabled(star.left(elapsed) > 0 && (said == null || !said.planning) && press.ready());
		route.setToolTipText(STAR_ROUTE_TIP);
		starRoutes.add(route);
		route.addActionListener(e ->
		{
			asked = star.id;
			/* No note, as Pin writes none: nothing on the card may age. */
			press.press(RoutePress.Target.tile(SOURCE, new net.runelite.api.coords.WorldPoint(star.x, star.y, star.plane),
				star.name, star.world, null));
			redraw();
		});
		return route;
	}

	/** What the last press of Route said, under the buttons of the star it was pressed for. */
	private void said(JPanel into, Star star)
	{
		RoutePress.Said said = press.said(SOURCE);
		if (said != null && said.message != null && star.id.equals(asked))
		{
			JLabel line = label("");
			wrap(line, said.message, CARD_WIDE);
			MapCompanionPanel.tone(line, said.refused());
			into.add(line);
			starSaid = line;
		}
	}

	/** The pinned star at the head of the tab: its live line, then its way, or Route while it has none. */
	private void pinned(Star star, long elapsed, boolean routed)
	{
		selection.setBorder(new CompoundBorder(
			new CompoundBorder(new EmptyBorder(0, 0, 8, 0), BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE)),
			new EmptyBorder(0, 0, 8, 0)));
		selection.add(heading("Pinned destination"));
		JLabel name = label("");
		wrap(name, star.name, TEXT);
		name.setForeground(GOLD);
		refont(name, 12f);
		selection.add(name);
		selection.add(gap(3));
		selection.add(fill(facts(star, elapsed, getBackground(), null, null)));
		selection.add(gap(6));
		JPanel actions = MapCompanionPanel.row(getBackground());
		if (routed)
		{
			/*
			 * The way there in a card, as Home draws it, between the star's own live line and the
			 * buttons that act on the way. On Home the view's top inset parts it from the card's name;
			 * here it opens the card, so the card's own is the smaller.
			 */
			JPanel way = MapCompanionPanel.card();
			way.setBorder(new EmptyBorder(2, 8, 7, 8));
			way.add(steps);
			selection.add(fill(way));
			selection.add(gap(5));
			home(actions, false, "stars.follow");
			home(actions, true, "stars.reroute");
		}
		else if (star.precise)
		{
			/* Pinned with no way yet, the way there is still one press. */
			actions.add(route(star, elapsed));
		}
		actions.add(unpin());
		selection.add(fill(actions));
		if (!routed)
		{
			said(selection, star);
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
			redraw();
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
	/** The tab while no character is logged in, since the list is asked for as one. */
	static final String LOG_IN = "Log in to see stars.";

	/** What the line over the list calls the player's game, or null for the main game's list. */
	static String gameName(String game)
	{
		return StarsModel.HARDCORE.equals(game) ? "Hardcore Worlds"
			: StarsModel.LEAGUE.equals(game) ? "League worlds"
			: StarsModel.NONE.equals(game) ? "Speedrunning worlds" : null;
	}

	/** The empty list when stars are up but none in the player's game. */
	static String noneInGame(String game)
	{
		String name = gameName(game);
		return StarsModel.NONE.equals(game) ? Words.of("stars.speedrunning")
			: "No star has been called on " + (name == null ? "the main game" : name) + " yet.";
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
			return tiers == null ? Words.of("stars.noLevels") : tiers;
		}
		return tiers == null ? "Worlds up to " + total + " total" : tiers + " · worlds up to " + total + " total";
	}

	/** A tier's colour on the map's ramp; a tier the feed could never send is held to the ramp's ends. */
	static Color tierColour(int tier)
	{
		return TIERS[Math.max(1, Math.min(9, tier)) - 1];
	}

	/** A time left's colour: amber under twenty minutes, red under five, grey once depleted, else the line's own ink. */
	static Color lifeColour(long remaining, Color ink)
	{
		switch (StarsModel.life(remaining))
		{
			case FADING:
				return FADING_TONE;
			case DYING:
				return DYING_TONE;
			case DEPLETED:
				return ColorScheme.MEDIUM_GRAY_COLOR;
			default:
				return ink;
		}
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

	private static JPanel column()
	{
		return MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	}

	/** One component the full width of the column, at its own height. */
	private static JComponent fill(JComponent component)
	{
		JPanel holder = new Row();
		holder.setOpaque(false);
		holder.add(component, BorderLayout.CENTER);
		return MapCompanionPanel.left(holder);
	}

	/**
	 * A holder whose height is its component's as laid out, not as built. The P2P and F2P stars land
	 * after the filter row is built and make it taller; under a maximum fixed before that, BoxLayout
	 * would cut the row and squeeze the rest of the tab, Data from Star Miners off the bottom first.
	 */
	private static final class Row extends JPanel
	{
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
		return MapCompanionPanel.gap(height, ColorScheme.DARK_GRAY_COLOR);
	}

	private static JLabel cell(String text, Color color, int width)
	{
		JLabel cell = new JLabel(text);
		refont(cell, 11f);
		cell.setForeground(color);
		if (width > 0) cell.setPreferredSize(new Dimension(width, cell.getPreferredSize().height));
		return cell;
	}

	private static JLabel label(String text)
	{
		return cell(text, ColorScheme.LIGHT_GRAY_COLOR, 0);
	}

	private static JComponent heading(String text)
	{
		JLabel label = label(text);
		refont(label, Font.BOLD, 11f);
		label.setBorder(new EmptyBorder(6, 0, 4, 0));
		return label;
	}

	/** A button that reads as its text, for a card's own controls. */
	private static JButton flat(String text)
	{
		JButton button = new JButton(text);
		refont(button, 11f);
		button.setFocusPainted(false);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		return MapCompanionPanel.bare(button, 1, 0, 1);
	}

	/** One of the picked world's buttons, in small type with the inset Route has always had. */
	private static JButton button(String text)
	{
		JButton button = new JButton(text);
		refont(button, 11f);
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

	/** Where a filter chip keeps its two borders; see {@link #chip} and {@link #outline}. */
	private static final String CHIP_OFF = "stars.chipOff", CHIP_ON = "stars.chipOn";

	/**
	 * A filter chip. On, it carries the orange outline Drop luck's chosen sort has, beside the look and
	 * feel's lighter fill, which alone told P2P on from F2P off by a few shades. The outline takes the
	 * place of the border's own line and keeps its insets, so a chip is the same size on as off.
	 */
	private static JToggleButton chip(String text)
	{
		JToggleButton chip = toggle(text);
		Border off = chip.getBorder();
		Insets inside = off.getBorderInsets(chip);
		chip.putClientProperty(CHIP_OFF, off);
		chip.putClientProperty(CHIP_ON, new CompoundBorder(new LineBorder(ColorScheme.BRAND_ORANGE),
			new EmptyBorder(Math.max(0, inside.top - 1), Math.max(0, inside.left - 1),
				Math.max(0, inside.bottom - 1), Math.max(0, inside.right - 1))));
		return chip;
	}

	/** A chip's border for whether it is on, set only when that changes: the tab refreshes every second. */
	private static void outline(JToggleButton chip)
	{
		Object wanted = chip.getClientProperty(chip.isSelected() ? CHIP_ON : CHIP_OFF);
		if (chip.getBorder() != wanted)
		{
			chip.setBorder((Border) wanted);
		}
	}

	private static JToggleButton toggle(String text)
	{
		JToggleButton button = new JToggleButton(text);
		refont(button, 11f);
		button.setMargin(new Insets(3, 2, 3, 2));
		return button;
	}

	/** A filter's box, which sets its own one of the filters when it is pressed. */
	private JCheckBox check(String text, BiConsumer<StarsModel.Filters, Boolean> set)
	{
		JCheckBox check = new JCheckBox(text);
		refont(check, 11f);
		check.setBackground(ColorScheme.DARK_GRAY_COLOR);
		check.addActionListener(e -> change(() -> set.accept(service.model.preferences.filters, check.isSelected())));
		return check;
	}
}
