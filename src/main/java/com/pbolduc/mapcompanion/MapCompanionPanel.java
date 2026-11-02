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
import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar, kept deliberately small.
 *
 * <p>The server supplies ranked Slayer places and routes using the website's own functions.
 * This panel presents those answers and local progress; it owns no second ranking or travel
 * table that could drift from the map. Pairing and sharing choices stay in the same sidebar.
 *
 * <p>Status and the one orange button come first, then three tabs ({@link CompanionViews}): Home
 * holds the search, farm runs, Slayer, what is happening now and Drop luck; Group the friends, the
 * meetups and the group's own buttons; Stars the star list. Settings is a page behind the gear on the
 * status card ({@link SidebarPages}): every sharing choice, then Advanced, which keeps the address,
 * the snapshot preview and the reset actions out of the way even there.
 *
 * <p>This class is the frame around them: it builds each part, lays out the tabs and the pages, and
 * owns the one refresh that asks every part to redraw, in the order some of them depend on. The
 * status card, the orange button and the join field are {@link ConnectCard}, the cards under Now
 * are {@link NowCards}, and Settings is {@link SettingsPage}.
 */
public class MapCompanionPanel extends PluginPanel
{
	/** Swing's, not ours: this panel is never serialised, and the constant only quiets the warning. */

	/**
	 * The sidebar is 225 wide and this panel insets ten either side. Labels are told that width
	 * explicitly because a JLabel does not wrap on its own — without it a long place name is not
	 * shortened, it is simply cut off mid-word.
	 */
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 30;

	/** The same, inside a card, which takes eight pixels a side for its own inset. */
	static final int CARD_TEXT_WIDTH = TEXT_WIDTH - 16;

	/**
	 * The sidebar's one pink, for what did not happen or cannot be done: a press refused, said under
	 * the button that made it; a requirement not met; a group the map refuses, on the status card;
	 * a star site in the Wilderness. The map's own panel draws its refusals in rose and its progress
	 * and confirmations in ordinary ink, and so does this, so a glance after a press says whether it
	 * worked. The death card is not a refusal and keeps the card's ink.
	 */
	static final Color REFUSED = new Color(239, 150, 150);

	private final MapCompanionConfig config;
	private final MapCompanionPlugin plugin;
	private final DestinationState destinationState;
	private final LootLog lootLog;
	private final Pairing pairing;

	/** What is happening now: the destination, a meetup, a death and a clue. See {@link NowCards}. */
	private final NowCards nowCards;
	/**
	 * What there is to start, under what is happening: the farm run, Slayer and port tasks. Each card
	 * hides while it has nothing to offer, and the heading goes with the last of them.
	 */
	private final JPanel tasks = column(ColorScheme.DARK_GRAY_COLOR);
	/**
	 * Every button that opens the browser says so. "Show on map" opened the website here and drew on
	 * the game's world map on a friend's card, one label for two maps; the game's map is "World map".
	 */
	static final String OPEN_WEBSITE = "Open website";
	private final JComponent searchGap;
	private final FarmRunPanel farmRunPanel;
	private final SlayerPanel slayerPanel;
	private final PortTasksPanel portTasksPanel;
	/** The map's search, at the top of Home while paired. */
	private final SearchPanel searchPanel;
	/** Each friend's row, the week's meetups and Meet here: the Group tab. */
	private final GroupPanel groupPanel;

	/* Drop luck: one monster's card, or every monster tracked. See {@link DropLuckPanel}. */
	private final JPanel drySection = column(ColorScheme.DARK_GRAY_COLOR);
	private final DropLuckPanel dropLuck;
	private final JLabel dropLuckPaused = new JLabel();
	/** Drop luck's heading, given up while the card's own first row says it (see DropLuckPanel.namesItself). */
	private final JLabel dryHeading = sectionHead(DropLuckPanel.HEADING);
	private final JPanel dryHeadingGap = gap(3, ColorScheme.DARK_GRAY_COLOR);

	/** The status card, the orange button, the join field and the group's buttons. See {@link ConnectCard}. */
	private final ConnectCard connectCard;
	private final CompanionViews views;
	private final SidebarPages pages;
	private final StarsPanel starsPanel;
	/** Route, pressed anywhere in the sidebar; read here only to bring Home forward for its answer. */
	private final RoutePress routePress;

	/** The step after a first Connect: the recommended set, ticked, and one press. See {@link RecommendedSharing}. */
	private final RecommendedSharing recommendedSharing;
	/** Every sharing choice, the map address, Show what's sent and the reset actions. See {@link SettingsPage}. */
	private final SettingsPage settingsPage;

	/**
	 * Ticks only while the sidebar is open.
	 *
	 * <p>Started from {@link #onActivate} and stopped from {@link #onDeactivate} rather than run
	 * from the constructor forever. A Swing timer holds a strong reference to its listener, so the
	 * constructor version kept this panel and everything it points at alive after the plugin was
	 * turned off, and went on recomputing a status line nobody could see.
	 */
	private final Timer timer = new Timer(1000, e -> refresh());

	@Inject
	private MapCompanionPanel(
		MapCompanionConfig config,
		MapCompanionPlugin plugin,
		DestinationState destinationState,
		LootLog lootLog,
		DeathWatcher deathWatcher,
		SnapshotBuilder snapshotBuilder,
		ClientThread clientThread,
		ConfigManager configManager,
		MemberIdentity identity,
		Pairing pairing,
		Gson gson,
		DestinationMarker destinationMarker,
		RelayClient relay,
		DirectiveStream directiveStream,
		ItemManager itemManager,
		StarsClient starsClient,
		DestinationRoute destinationRoute,
		RouteClient routeClient,
		ShortestPath shortestPath,
		SearchClient searchClient,
		RoutePress routePress,
		ClueReader clueReader,
		GroupState groupState,
		MeetupClient meetupClient,
		GameSprites sprites,
		FarmRunPanel farmRunPanel,
		FarmRunRoute farmRunRoute,
		FarmRunState farmRunState,
		FarmRunClient farmRunClient,
		SlayerPanel slayerPanel,
		PortTasksPanel portTasksPanel,
		ConnectClient connectClient)
	{
		this.config = config;
		this.plugin = plugin;
		this.destinationState = destinationState;
		this.lootLog = lootLog;
		this.pairing = pairing;
		this.farmRunPanel = farmRunPanel;
		this.slayerPanel = slayerPanel;
		this.portTasksPanel = portTasksPanel;
		this.routePress = routePress;
		this.searchPanel = new SearchPanel(searchClient, routePress, sprites);
		/* Before the connect card, which offers it after a first Connect. */
		this.recommendedSharing = new RecommendedSharing(config, ConfigStore.of(configManager), this::refresh);
		this.connectCard = new ConnectCard(this, config, plugin, identity, pairing, relay, directiveStream, connectClient,
			recommendedSharing, this::refresh, this::openSettings, this::applyMapAddress, this::mapUrl);
		this.groupPanel = new GroupPanel(destinationState, groupState, routePress, meetupClient, config, sprites, connectCard.copyInviteButton);
		this.nowCards = new NowCards(config, destinationState, deathWatcher, destinationMarker, destinationRoute, routeClient,
			shortestPath, routePress, clueReader, farmRunRoute, sprites, this::refresh, this::mapUrl);
		this.dropLuck = new DropLuckPanel(lootLog, DropLuckPanel.Pictures.of(itemManager));
		this.starsPanel = new StarsPanel(starsClient, destinationState, clientThread, destinationMarker, routePress, this::mapUrl,
			destinationRoute, shortestPath, config, sprites, nowCards::hotkey, this::refresh);
		this.starsPanel.pictures(sprites);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		/*
		 * No title. RuneLite's sidebar button already names the plugin, and the row it took is the
		 * one the status card now starts on.
		 */
		JPanel body = column(ColorScheme.DARK_GRAY_COLOR);
		body.setName("companionDaily");

		body.add(connectCard);
		body.add(recommendedSharing);
		JPanel overview = column(ColorScheme.DARK_GRAY_COLOR);
		overview.setName("companionHome");
		/* First, because "where is it and how do I get there" is the question the map answers best. */
		overview.add(searchPanel);
		searchGap = gap(8, ColorScheme.DARK_GRAY_COLOR);
		overview.add(searchGap);

		overview.add(nowCards);

		/*
		 * Under Now, as the last polish pass placed them: what is happening comes before what to start.
		 * Each card in a slot of its own, as Now's are, so three cards do not read as one dark block
		 * with two Start runs in it.
		 */
		tasks.add(sectionHead("Tasks"));
		tasks.add(gap(3, ColorScheme.DARK_GRAY_COLOR));
		tasks.add(slot(farmRunPanel));
		tasks.add(slot(slayerPanel));
		tasks.add(slot(portTasksPanel));
		tasks.add(gap(7, ColorScheme.DARK_GRAY_COLOR));
		overview.add(tasks);

		/* Drop luck works with no pairing code set, so it sits above everything the group needs. */
		drySection.add(dryHeading);
		drySection.add(dryHeadingGap);
		dropLuckPaused.setFont(dropLuckPaused.getFont().deriveFont(11f));
		dropLuckPaused.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		setWrapped(dropLuckPaused, LootLog.PAUSED);
		drySection.add(left(dropLuckPaused));
		drySection.add(dropLuck);
		drySection.add(gap(10, ColorScheme.DARK_GRAY_COLOR));
		overview.add(drySection);

		JPanel group = column(ColorScheme.DARK_GRAY_COLOR);
		group.setName("companionGroupView");
		group.add(groupPanel);
		group.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		group.add(left(connectCard.groupActions));
		views = new CompanionViews(overview, group, starsPanel, starsPanel::selected);
		body.add(views);

		settingsPage = new SettingsPage(config, destinationState, lootLog, snapshotBuilder, clientThread, configManager,
			identity, pairing, gson, farmRunState, farmRunClient, connectCard, this::refresh, this::openSettings, this::mapUrl);
		pages = new SidebarPages(body, settingsPage);

		add(pages, BorderLayout.NORTH);

		refresh();
	}

	/**
	 * A second's refresh is enough for a status line, and only while somebody is looking at it.
	 *
	 * <p>The plugin does its own work on game ticks; this only reads what that published, so it can
	 * afford to be lazy, and to stop entirely when the sidebar is closed.
	 */
	@Override
	public void onActivate()
	{
		starsPanel.active(true);
		refresh();
		timer.start();
	}

	@Override
	public void onDeactivate()
	{
		starsPanel.active(false);
		timer.stop();
	}

	/** Also called when the plugin shuts down, in case the sidebar was never opened or closed. */
	void stopRefreshing()
	{
		starsPanel.active(false);
		timer.stop();
	}

	/**
	 * Pin a child to the left edge of the vertical stack.
	 *
	 * <p>A BoxLayout aligns its children by their own alignmentX, and JComponent's default is 0.5 —
	 * centred. Every row here is a left-edge row, so saying so once per child is what keeps the
	 * title, the status line and the buttons on the same margin instead of each finding its own.
	 */
	static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	static JPanel gap(int height, Color background)
	{
		JPanel spacer = new JPanel();
		spacer.setBackground(background);
		spacer.setPreferredSize(new Dimension(1, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		/* A plain panel's FlowLayout asks for 10px at least, and a squeezed column grows a gap to that. */
		spacer.setMinimumSize(new Dimension(0, height));
		return left(spacer);
	}

	/** A vertical stack whose children keep the left margin — see {@link #left}. */
	static JPanel column(Color background)
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(background);
		return left(column);
	}

	/**
	 * A block of the darker grey RuneLite's own panels group things with — the loot tracker's
	 * boxes, the XP tracker's — so a fact and the action that belongs to it read as one thing.
	 */
	static JPanel card()
	{
		JPanel card = column(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 7, 8));
		return card;
	}

	/**
	 * A card with the three pixels under it that separate it from the next, in one piece, so that
	 * hiding a card hides its gap too — a gap left behind by a hidden card is a hole in the column.
	 */
	static JComponent slot(JPanel card)
	{
		JPanel slot = new JPanel(new BorderLayout());
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 3, 0));
		slot.add(card, BorderLayout.CENTER);
		return left(slot);
	}

	/** Buttons side by side, sharing the width equally — however many of them are showing. */
	static JPanel row(Color background)
	{
		JPanel row = new JPanel(new GridLayout(1, 0, 4, 0));
		row.setBackground(background);
		return row;
	}

	/** One component the full width of the column, at its own height. */
	static JPanel fill(JComponent component, Color background)
	{
		JPanel holder = new JPanel(new GridLayout(1, 1));
		holder.setBackground(background);
		holder.add(component);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return left(holder);
	}

	/** One component at its own width, on the left. */
	static JPanel hug(JComponent component, Color background)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(background);
		holder.add(component, BorderLayout.WEST);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return left(holder);
	}

	/** A section's name: bold, small and grey, the way the old join heading was. */
	static JLabel sectionHead(String text)
	{
		JLabel heading = new JLabel(text);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f));
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return left(heading);
	}

	/** A button a size down, for the actions that sit inside a card or beside a field. */
	static JButton smallButton(String text)
	{
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(11f));
		button.setMargin(new Insets(1, 6, 1, 6));
		return button;
	}

	/** A button that reads as a line of text, for a fold's own opener. */
	static JButton linkButton(String text)
	{
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(11f));
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return left(button);
	}

	/**
	 * When a meetup is, the way somebody would say it: "Meetup in 45m", "Meetup in 3h 20m", "Meetup
	 * tomorrow 6:42 AM", then the world when the map named one, " · W444".
	 *
	 * <p>It said "Meetup in 1560m", which nobody reads as a time. Under twelve hours this is the
	 * countdown the map's own meetup card prints. Past that a countdown is the wrong answer: what a
	 * player needs is the day and the clock in their own zone, which is also what they will say to
	 * the rest of the group. The day is a weekday within the week and a date past it, as the map's
	 * feed names its days, because a weekday that could mean either of two weeks is a guess.
	 *
	 * <p>The clock counts twelve hours or twenty-four as the reader's locale does, but is written
	 * from a plain pattern rather than the locale's own: Java's English one now puts a narrow
	 * no-break space before "AM", and the sidebar's font has no glyph for it.
	 */
	static String meetupWhen(long startsAtMs, long nowMs, ZoneId zone, Locale locale, @Nullable Integer world)
	{
		long minutes = Math.round((startsAtMs - nowMs) / 60_000.0);
		String when;
		if (minutes <= 0)
		{
			when = "Meetup now";
		}
		else if (minutes < 60)
		{
			when = "Meetup in " + minutes + "m";
		}
		else if (minutes < 12 * 60)
		{
			long rest = minutes % 60;
			when = "Meetup in " + (minutes / 60) + "h" + (rest == 0 ? "" : " " + rest + "m");
		}
		else
		{
			ZonedDateTime at = Instant.ofEpochMilli(startsAtMs).atZone(zone);
			long days = ChronoUnit.DAYS.between(
							Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), at.toLocalDate());
			String day = days == 0 ? "today"
						 : days == 1 ? "tomorrow"
						 : days < 7 ? at.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
						 : at.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
			when = "Meetup " + day + " "
				   + at.format(DateTimeFormatter.ofPattern(clockPattern(locale), Locale.ENGLISH));
		}
		return world == null ? when : when + " · W" + world;
	}

	/** "h:mm a" where the locale's own short time counts twelve hours, "HH:mm" where it counts twenty-four. */
	static String clockPattern(Locale locale)
	{
		String localized = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
							   null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale);
		return localized.contains("h") || localized.contains("K") ? "h:mm a" : "HH:mm";
	}

	/**
	 * "Today 7:52 PM": a meetup's day and clock in the player's zone, as the Group tab's line begins.
	 * That line and a later meetup's folded one take these very words, through
	 * {@link GroupPanel#dayAndClock}.
	 *
	 * <p>The note a Route press writes under the destination's name is written once and never
	 * redrawn, so it carries no countdown: a day and a clock stay true however long the card is up.
	 */
	static String meetupStarts(long startsAtMs, long nowMs, ZoneId zone, Locale locale)
	{
		ZonedDateTime at = Instant.ofEpochMilli(startsAtMs).atZone(zone);
		long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), at.toLocalDate());
		String day = days == 0 ? "Today"
			: days == 1 ? "Tomorrow"
			: days > 1 && days < 7 ? at.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
			: at.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
		return day + " " + at.format(DateTimeFormatter.ofPattern(clockPattern(locale), Locale.ENGLISH));
	}

	private void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			Health health = plugin.getHealth();
			String code = pairingCode();
			boolean paired = !code.isEmpty();
			boolean ready = paired && pairing.hasServerConsent();

			connectCard.refreshStatus(health, code, paired, ready);
			/*
			 * At the login screen the status card says Logged out, the one true thing to say, and the
			 * presses that ask the map as a member wait for a character, greyed, rather than each
			 * answering a press with a fix for a connection nothing is wrong with. What acts here or as
			 * the player stays: Follow, Pause, Dismiss, End run, World map, Pin, Open the map, Switch ▾
			 * and Rename group….
			 */
			boolean loggedIn = plugin.loggedIn();

			/*
			 * The search is the map's, asked as a member, and a member is a character: a box that could
			 * only say "connect" or "log in" is not shown.
			 */
			boolean searching = ready && loggedIn;
			searchPanel.setVisible(searching);
			searchGap.setVisible(searching);
			farmRunPanel.refresh(ready, loggedIn);
			slayerPanel.refresh(ready, loggedIn);
			portTasksPanel.refresh(ready, loggedIn);
			/* Each card decides its own visibility; its slot follows, so a hidden card leaves no gap. */
			boolean anyTask = false;
			for (JComponent card : new JComponent[] {farmRunPanel, slayerPanel, portTasksPanel})
			{
				card.getParent().setVisible(card.isVisible());
				anyTask |= card.isVisible();
			}
			tasks.setVisible(anyTask);
			if (searching)
			{
				searchPanel.refresh();
			}

			nowCards.refresh(paired, ready, loggedIn);
			/* After Now: under a routed star, Stars offers the destination card's own buttons as they stand. */
			starsPanel.refresh();

			/* Redraws only when its words change, so a click or a search is never painted over. */
			boolean paused = LootLog.PAUSED.equals(lootLog.summary());
			dropLuck.refresh();
			dropLuckPaused.setVisible(paused);
			boolean headed = !paused && dropLuck.namesItself();
			dryHeading.setVisible(!headed);
			dryHeadingGap.setVisible(!headed);
			drySection.setVisible(paused || dropLuck.hasContent());

			/*
			 * The group. In one: its invite beside the groups menu, which also joins another, or in the
			 * Group tab's card while nobody else is in it. In none: the join field outright, and the
			 * remembered groups under it.
			 */
			List<String> known = pairing.knownCodes();
			/*
			 * The tabs are a member's: before a group is joined, Home is the whole page. While the step
			 * after a first Connect is up it is the page, with nothing under it to scroll past.
			 */
			views.tabsShown(ready);
			views.setVisible(!recommendedSharing.isVisible());
			/* A friend's or a meetup's way, landed while Group still shows: Home, once, with the card in view. */
			if (views.showAnswer(routePress.last()))
			{
				SwingUtilities.invokeLater(this::showNow);
			}
			List<RelayResponse.RosterRow> roster = destinationState.getRoster();
			views.groupCount(ready && roster != null ? roster.size() : 0);
			groupPanel.setVisible(ready);
			if (ready)
			{
				groupPanel.refresh(true, loggedIn, System.currentTimeMillis());
			}
			connectCard.refreshButtons(paired, ready, loggedIn, known, groupPanel, System.currentTimeMillis());
			/* Offered for the group just joined; leaving it, or a changed map, takes the offer away. */
			if (!ready)
			{
				recommendedSharing.withdraw();
			}

			settingsPage.refresh(paired);
		});
	}

	/**
	 * Now's top in view, where the destination's card is, after the tab has turned to Home: as much of
	 * it as the sidebar shows at once. Asked after the turn's own layout, which is queued before it. A
	 * taller rectangle would be lined up by its foot when the column had been scrolled past it, and the
	 * card at its head would stay out of sight.
	 */
	private void showNow()
	{
		JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, nowCards);
		int height = viewport == null ? nowCards.getHeight() : Math.min(nowCards.getHeight(), viewport.getExtentSize().height);
		nowCards.scrollRectToVisible(new Rectangle(0, 0, nowCards.getWidth(), height));
	}

	/**
	 * A hotkey, taken as the card it acts on would take a click: a run's own buttons while a run is
	 * shown and no other route is followed, otherwise the destination's. The key presses that very
	 * button, so it obeys every rule the button does — Reroute inside its wait is disabled, and Follow
	 * is offered only where the card offers it. EDT.
	 */
	void pressHotkey(boolean rerouting)
	{
		JButton button = farmRunPanel.hotkey(rerouting);
		if (button == null)
		{
			button = portTasksPanel.hotkey(rerouting);
		}
		if (button == null)
		{
			button = nowCards.hotkey(rerouting);
		}
		press(button);
	}

	/** Click a button only as a player could: one in its row and enabled. True when it was pressed. */
	static boolean press(@Nullable JButton button)
	{
		if (button == null || !button.isEnabled())
		{
			return false;
		}
		button.doClick();
		return true;
	}

	/**
	 * Put a row of buttons in one known order, and only touch it when that order changes.
	 *
	 * <p>A grid appends, so a button that comes back after being hidden would come back at the end
	 * of the row instead of where the player last saw it; comparing the whole list and rebuilding
	 * in one fixed order costs nothing at one refresh a second and keeps the layout still. It was
	 * one row of seven, which is where the rule was learned; it is three short rows now, each
	 * beside the facts it acts on, and the rule is the same.
	 */
	static List<JButton> syncRow(JPanel row, List<JButton> wanted, List<JButton> shown)
	{
		if (wanted.equals(shown))
		{
			return shown;
		}
		row.removeAll();
		for (JButton button : wanted)
		{
			row.add(button);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.revalidate();
		row.repaint();
		return wanted;
	}

	/**
	 * Set a label's text so that it wraps, and only when it changed.
	 *
	 * <p>Wrapping a JLabel means handing it HTML, which means the text has to be escaped first.
	 * Destination names, notes and meetup titles arrive over the network from whoever holds the
	 * pairing code, and Swing's HTML renderer is a real one: an unescaped image tag in a meetup
	 * title would have the client fetch a remote address on sight. Escaping is what keeps this a
	 * label rather than a document.
	 *
	 * <p>The equality check is not for speed. Re-setting identical text still fires a property
	 * change and a repaint, once a second, for as long as the sidebar is open.
	 *
	 * <p>The width is an HTML attribute rather than the CSS {@code style='width:195px'} that this
	 * idiom is usually written with, because the CSS form does not actually constrain the label.
	 * Measured on the sharing line: styled, it asks for 253 pixels inside a 205-pixel column and
	 * the last word is cut off; as an attribute it asks for exactly 195 and fits. It wraps either
	 * way, which is what makes the difference easy to miss — the text is not obviously broken, just
	 * quietly missing its right-hand edge.
	 */
	static void setWrapped(JLabel label, String text)
	{
		setWrapped(label, text, TEXT_WIDTH);
	}

	/** The same, for a label narrower than the column — inside a card, beside the dot. */
	static void setWrapped(JLabel label, String text, int width)
	{
		String html = "<html><body width='" + width + "'>" + escape(text) + "</body></html>";
		if (!html.equals(label.getText()))
		{
			label.setText(html);
		}
	}

	/**
	 * The ink of a line a press left under its button: {@link #REFUSED} while it says the press did
	 * not happen, and the line's grey for a press on its way, a wait being kept, or one that worked.
	 */
	static void tone(JLabel line, boolean refused)
	{
		line.setForeground(refused ? REFUSED : ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** Null-safe: names and titles arrive from the map, and Gson leaves an absent field null. */
	static String escape(@Nullable String text)
	{
		if (text == null)
		{
			return "";
		}
		return text
			   .replace("&", "&amp;")
			   .replace("<", "&lt;")
			   .replace(">", "&gt;");
	}

	private String pairingCode()
	{
		return config.pairingCode() == null ? "" : config.pairingCode().trim();
	}

	private String mapUrl()
	{
		String server = pairing.serverUrl();
		return server == null ? MapCompanionConfig.DEFAULT_MAP_URL : server;
	}

	/** Settings in place of the daily page, or the daily page back, for a part whose press turns the page. */
	private void openSettings(boolean open)
	{
		pages.open(open);
	}

	/** The address typed in Settings, applied: Connect and Join apply it before they ask the map. */
	private boolean applyMapAddress()
	{
		return settingsPage.applyMapAddress();
	}
}
