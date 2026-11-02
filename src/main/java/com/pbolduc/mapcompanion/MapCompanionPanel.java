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
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

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
 * status card, the orange button and the join field are {@link ConnectCard}, and the cards under Now
 * are {@link NowCards}.
 *
 * <p>Two of the buttons are about trust rather than about the map. <b>Show what's sent</b> prints
 * the actual snapshot, built by the same code that would post it, because a plugin that claims to
 * send only what a list says should be willing to show its working. <b>Leave group</b> is the
 * other half of joining one: it clears the code, forgets the id this installation was known by
 * along with every credential a map ever issued that id, and drops the group from the remembered
 * list, so that leaving is a thing a player can do rather than a thing they have to trust was
 * done.
 */
@Slf4j
public class MapCompanionPanel extends PluginPanel
{
	/** Swing's, not ours: this panel is never serialised, and the constant only quiets the warning. */
	private static final long serialVersionUID = 1L;

	/**
	 * The sidebar is 225 wide and this panel insets ten either side. Labels are told that width
	 * explicitly because a JLabel does not wrap on its own — without it a long place name is not
	 * shortened, it is simply cut off mid-word.
	 */
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 30;

	/** The same, inside a card, which takes eight pixels a side for its own inset. */
	static final int CARD_TEXT_WIDTH = TEXT_WIDTH - 16;

	private final MapCompanionConfig config;
	private final MapCompanionPlugin plugin;
	private final DestinationState destinationState;
	private final LootLog lootLog;
	private final SnapshotBuilder snapshotBuilder;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final MemberIdentity identity;
	private final Pairing pairing;
	private final Gson gson;

	private final JButton previewButton = smallButton("Show what's sent");

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
	private final FarmRunState farmRunState;
	private final FarmRunClient farmRunClient;
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

	private final SharingControls sharingControls;
	/** The step after a first Connect: the recommended set, ticked, and one press. See {@link RecommendedSharing}. */
	private final RecommendedSharing recommendedSharing;
	private final JTextField mapAddress = new JTextField();
	private String displayedAddress;

	/*
	 * The reset actions live in Advanced: neither is a thing a player does every session.
	 */
	private final JPanel foot = row(ColorScheme.DARK_GRAY_COLOR);
	private List<JButton> shownFoot = new ArrayList<>();
	private final JButton leaveButton = smallButton("Leave group");
	/** Only ever present once there is a log to forget. A log you cannot clear is one you cannot trust. */
	private final JButton forgetLootButton = smallButton("Forget kill log");

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
		this.snapshotBuilder = snapshotBuilder;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.identity = identity;
		this.pairing = pairing;
		this.gson = gson;
		this.farmRunPanel = farmRunPanel;
		this.farmRunState = farmRunState;
		this.farmRunClient = farmRunClient;
		this.slayerPanel = slayerPanel;
		this.portTasksPanel = portTasksPanel;
		this.searchPanel = new SearchPanel(searchClient, routePress, sprites);
		/* Before the connect card, which offers it after a first Connect. */
		this.recommendedSharing = new RecommendedSharing(config, ConfigStore.of(configManager), this::refresh);
		this.connectCard = new ConnectCard(this, config, plugin, identity, pairing, relay, directiveStream, connectClient,
			recommendedSharing, this::refresh, this::openSettings, this::applyMapAddress, this::mapUrl);
		this.groupPanel = new GroupPanel(destinationState, groupState, routePress, meetupClient, config, sprites, connectCard.copyInviteButton);
		this.nowCards = new NowCards(config, destinationState, deathWatcher, destinationMarker, destinationRoute, routeClient,
			shortestPath, routePress, clueReader, farmRunRoute, sprites, this::refresh, this::mapUrl);
		this.dropLuck = new DropLuckPanel(lootLog, DropLuckPanel.Pictures.of(itemManager));
		this.starsPanel = new StarsPanel(starsClient, destinationState, clientThread, destinationMarker, routePress, this::mapUrl);
		this.starsPanel.pictures(sprites);
		this.sharingControls = new SharingControls(config, ConfigStore.of(configManager));

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

		/* Under Now, as the last polish pass placed them: what is happening comes before what to start. */
		tasks.add(sectionHead("Tasks"));
		tasks.add(gap(3, ColorScheme.DARK_GRAY_COLOR));
		tasks.add(farmRunPanel);
		tasks.add(slayerPanel);
		tasks.add(portTasksPanel);
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

		JPanel settings = column(ColorScheme.DARK_GRAY_COLOR);
		/*
		 * Where an update's changes are written down, the same page the once-per-version chat line
		 * names: RuneLite shows no changelog, and the README carries only the latest few lines.
		 */
		JButton whatsNew = linkButton("What's new in " + CompanionVersion.VERSION + " ›");
		whatsNew.setName("settings.whatsNew");
		whatsNew.setForeground(ColorScheme.BRAND_ORANGE);
		whatsNew.setToolTipText("Open the map's What's new page, where every update is written down");
		whatsNew.addActionListener(e -> LinkBrowser.browse(mapUrl() + "/whats-new"));
		settings.add(left(whatsNew));
		settings.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		settings.add(sectionHead("What you share"));
		settings.add(sharingControls);
		settings.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		settings.add(buildAdvanced());
		pages = new SidebarPages(body, settings);

		previewButton.setToolTipText("Print the exact snapshot this plugin would send right now");
		previewButton.addActionListener(e -> showSnapshotPreview());
		leaveButton.addActionListener(e -> leaveGroup());
		forgetLootButton.addActionListener(e ->
		{
			forgetKillLog(lootLog, this::confirmForgetKillLog);
			refresh();
		});

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
	private static JPanel hug(JComponent component, Color background)
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

	/** The actual address is editable here; there is no second default or explanatory footnote. */
	private JPanel buildAdvanced()
	{
		JPanel contents = column(ColorScheme.DARK_GRAY_COLOR);
		contents.add(sectionHead("Map address"));
		mapAddress.setText(config.relayUrl());
		mapAddress.addActionListener(e -> applyMapAddress());
		contents.add(fill(mapAddress, ColorScheme.DARK_GRAY_COLOR));
		JButton apply = smallButton("Apply");
		apply.addActionListener(e -> applyMapAddress());
		contents.add(hug(apply, ColorScheme.DARK_GRAY_COLOR));
		contents.add(gap(8, ColorScheme.DARK_GRAY_COLOR));
		contents.add(hug(previewButton, ColorScheme.DARK_GRAY_COLOR));
		contents.add(gap(8, ColorScheme.DARK_GRAY_COLOR));
		contents.add(left(foot));
		return SharingControls.fold("Advanced", contents);
	}

	private boolean applyMapAddress()
	{
		String server = Pairing.normalizeServer(mapAddress.getText());
		if (server == null)
		{
			connectCard.say("Enter an https:// map address without a login, query or fragment.");
			return false;
		}
		mapAddress.setText(server);
		if (!server.equals(pairing.serverUrl()))
		{
			configManager.setConfiguration(MapCompanionConfig.GROUP, MapCompanionConfig.KEY_RELAY_URL, server);
			connectCard.joinOpen = true;
			connectCard.say("Map address changed. Press Connect.");
			/* The field and the line that asks for it are on the daily page. */
			pages.open(false);
			refresh();
		}
		return true;
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

	private void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			starsPanel.refresh();
			Health health = plugin.getHealth();
			String code = pairingCode();
			boolean paired = !code.isEmpty();
			boolean ready = paired && pairing.hasServerConsent();

			connectCard.refreshStatus(health, code, paired, ready);

			/* The search is the map's, asked as a member: a box that could only say "connect" is not shown. */
			searchPanel.setVisible(ready);
			searchGap.setVisible(ready);
			farmRunPanel.refresh(ready);
			slayerPanel.refresh(ready);
			portTasksPanel.refresh(ready);
			tasks.setVisible(farmRunPanel.isVisible() || slayerPanel.isVisible() || portTasksPanel.isVisible());
			if (ready)
			{
				searchPanel.refresh();
			}

			nowCards.refresh(paired, ready);

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
			List<RelayResponse.RosterRow> roster = destinationState.getRoster();
			views.groupCount(ready && roster != null ? roster.size() : 0);
			groupPanel.setVisible(ready);
			if (ready)
			{
				groupPanel.refresh(true);
			}
			connectCard.refreshButtons(paired, ready, known, groupPanel);
			/* Offered for the group just joined; leaving it, or a changed map, takes the offer away. */
			if (!ready)
			{
				recommendedSharing.withdraw();
			}

			sharingControls.refresh();
			String configuredAddress = config.relayUrl();
			if (!java.util.Objects.equals(displayedAddress, configuredAddress))
			{
				displayedAddress = configuredAddress;
				mapAddress.setText(configuredAddress == null ? MapCompanionConfig.DEFAULT_MAP_URL : configuredAddress);
			}

			List<JButton> footRow = new ArrayList<>();
			if (paired)
			{
				footRow.add(leaveButton);
			}
			if (lootLog.hasLog())
			{
				footRow.add(forgetLootButton);
			}
			shownFoot = syncRow(foot, footRow, shownFoot);
			foot.setVisible(!footRow.isEmpty());
		});
	}

	/**
	 * A hotkey, taken as the card it acts on would take a click: a run's own buttons while a run is
	 * shown, otherwise the destination's. The key presses that very button, so it obeys every rule the
	 * button does — Reroute inside its wait is disabled, and Follow is offered only where the card
	 * offers it. EDT.
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
	 * Show the snapshot that would go out, built by the code that would build it.
	 *
	 * <p>Not a description of the wire format and not a second implementation of one: this calls
	 * {@link SnapshotBuilder} exactly as the game tick does, so anything the toggles let through is
	 * visible here and anything they do not cannot appear. The README asks a reviewer to read two
	 * files and believe them; this asks nobody to believe anything.
	 *
	 * <p>Built on the client thread because every field in it is live game state, then handed back to
	 * Swing to be shown.
	 */
	private void showSnapshotPreview()
	{
		clientThread.invoke(() ->
		{
			String json;
			try
			{
				CompanionSnapshot snapshot = snapshotBuilder.build(System.currentTimeMillis());
				json = snapshot == null
				? null
				: gson.newBuilder().setPrettyPrinting().create().toJson(snapshot);
			}
			catch (RuntimeException e)
			{
				log.debug("could not build a preview snapshot", e);
				json = null;
			}
			String finished = json;
			SwingUtilities.invokeLater(() -> presentPreview(finished));
		});
	}

	private void presentPreview(@Nullable String json)
	{
		String header = pairingCode().isEmpty() || !pairing.hasServerConsent()
						? "Not connected to the selected server, so this is a preview of what would go out if "
						+ "you pressed Connect.\n\n"
						: "This is what goes out, every couple of seconds, to " + mapUrl() + ".\n\n";
		/*
		 * The body is not quite the whole request, and the difference is a header — so it is
		 * printed here rather than left as the one thing this window does not show. It stops being
		 * sent the moment the map says it has connected this installation to a browser.
		 */
		String suffix = pairing.suffix();
		if (suffix != null)
		{
			header += "It also carries your RuneLite code, " + pairingCode() + "-" + masked(suffix)
					  + ", as a header — until the map answers that it has connected your dot to your "
					  + "browser, after which it is deleted from the settings and never sent again.\n\n";
		}
		/*
		 * Named, never printed. The suffix above is shown by its ends because it is on the reader's own card on
		 * the map and they may want to check the two agree; this is a standing credential, and a
		 * window whose whole purpose is to be read — and screenshotted into a Discord channel when
		 * somebody is asking for help — is the last place to spell one out. Which requests carry it
		 * and what it proves is the part a reader is owed.
		 */
		if (pairing.secret(identity.id()) != null)
		{
			header += "It also carries X-Companion-Member-Secret, a credential this group's map "
					  + "issued to this character, as a header — on this snapshot, on the connection "
					  + "that receives destinations, and on the one request Copy invite makes. It says "
					  + "which member of this group you are, so that somebody who was removed cannot "
					  + "make invites or read the group by knowing your id. It is not shown here and is "
					  + "never written to a log; Leave group deletes it.\n\n";
		}
		/* Named, never printed, for the same reason: it stands in for a pasted code. */
		header += "It also carries X-Companion-Installation, a random key this RuneLite made for itself, "
				  + "as a header on this snapshot only. It is how the map lets another account you log into "
				  + "join a group this one is in, on the same browser, without another code. The map keeps "
				  + "only a hash of it; Leave group replaces it.\n\n";
		String body = json == null
					  ? "Nothing can be built right now: a snapshot is only assembled while you are logged in."
					  : json;

		JTextArea area = new JTextArea(header + body, 22, 44);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setCaretPosition(0);
		JOptionPane.showMessageDialog(
			SwingUtilities.getWindowAncestor(this),
			new JScrollPane(area),
			"What this plugin sends",
			JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Leave, properly.
	 *
	 * <p>Clearing the code stops the sending; forgetting the id is the other half, and it is the half
	 * a player cannot do for themselves. Without it a member who left and came back arrived as the
	 * same dot they were before — a stable identifier for a person across every group they ever join,
	 * which is exactly the thing {@link MemberIdentity} is arranged not to create.
	 *
	 * <p>The member secrets go with the id rather than with the group, and every one of them goes.
	 * A secret proves that a particular member id belongs to this installation, so an id that no
	 * longer exists leaves nothing for any of them to prove — and a credential kept for a member
	 * nobody can be is the opposite of what this button says. {@link Pairing#leave} does it.
	 *
	 * <p>The kill log is deliberately untouched. It is the player's own, it never needed the map, and
	 * leaving a group is not a reason to throw away a year of counting. It has its own button.
	 *
	 * <p>The group also leaves the remembered list, because a one-press way back into a group
	 * somebody deliberately left is the opposite of what this button says.
	 */
	private void leaveGroup()
	{
		int answer = JOptionPane.showConfirmDialog(
						 SwingUtilities.getWindowAncestor(this),
						 "Clear your pairing code and forget the ids every account on this RuneLite had?\n\n"
						 + "Friends holding the code stop seeing you. Rejoining later arrives as a new\n"
						 + "person rather than as you, and needs an invite that still works. This group\n"
						 + "leaves your list of groups. Your kill log is not touched.",
						 "Leave group",
						 JOptionPane.OK_CANCEL_OPTION,
						 JOptionPane.QUESTION_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
		{
			return;
		}
		/* Clearing the code raises a ConfigChanged, which is what stops the stream and resets the relay. */
		pairing.leave();
		identity.forget();
		farmRunClient.clear();
		farmRunState.clear();
		destinationState.clear();
		connectCard.say("Left the group.");
		pages.open(false);
		refresh();
	}

	/**
	 * Forget kill log's question, put the way Leave group beside it puts its own.
	 *
	 * <p>Both buttons erase something and sit side by side, so both ask. One press would clear every
	 * count this character has and its pinned drop, and the plugin keeps no other copy to give back.
	 */
	private boolean confirmForgetKillLog()
	{
		return JOptionPane.showConfirmDialog(
				   SwingUtilities.getWindowAncestor(this),
				   "Forget every kill and drop counted for this character?\n\n"
				   + "Drop luck starts again from zero, and your pinned drop goes with it.\n"
				   + "This cannot be undone.",
				   "Forget kill log",
				   JOptionPane.OK_CANCEL_OPTION,
				   JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
	}

	/** Clear the kill log once the player has said yes, and only then. True when it was cleared. */
	static boolean forgetKillLog(LootLog log, BooleanSupplier confirmed)
	{
		if (!confirmed.getAsBoolean())
		{
			return false;
		}
		log.clear();
		return true;
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
	 * Enough of a suffix to check against the map's card, not enough to paste. With the group code
	 * it admits a character to a group, and this window is the one people screenshot for help.
	 */
	static String masked(String suffix)
	{
		return suffix.length() <= 4 ? "••••" : suffix.substring(0, 2) + "••••" + suffix.substring(suffix.length() - 2);
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
}
