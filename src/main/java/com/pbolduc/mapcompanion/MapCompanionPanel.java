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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar, kept deliberately small.
 *
 * <p>Everything interesting about this feature — the ranked slayer locations, the routes, the
 * party, the meetups — lives on the map, where there is room for it and where it can be read on a
 * second screen while the game has the first. Reproducing any of that here would mean two versions
 * of the same answer, drifting.
 *
 * <p>So this answers only the questions that belong in the client: is it connected, what is being
 * shared, and what has the map asked me to look at. Plus the one button that matters — a way to
 * open the map — and a way to hand a friend the code.
 *
 * <p>Status and Open the map come first, then three tabs ({@link CompanionViews}): Home holds the
 * search, what is happening now and Drop luck; Group the friends, the meetups and the group's own
 * buttons; Stars the star list. Settings is a page behind the gear on the status card
 * ({@link SidebarPages}): every sharing choice, then Advanced, which keeps the address, the snapshot
 * preview and the reset actions out of the way even there. The connection field takes a complete
 * RuneLite code or an existing invite; the suffix is internal state rather than a second field.
 * Once paired, the field folds away until switching or reconnecting needs it.
 *
 * <p><b>Reading the clipboard is a button, never a background job.</b> Nothing here looks at the
 * clipboard unless somebody pressed <i>Paste</i>, which is the same standard the invite button has
 * always held to in the other direction — copying, not typing into the game.
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
	private final DeathWatcher deathWatcher;
	private final SnapshotBuilder snapshotBuilder;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final MemberIdentity identity;
	private final Pairing pairing;
	private final Gson gson;
	private final DestinationMarker destinationMarker;
	private final RelayClient relay;
	private final DirectiveStream directiveStream;

	/* The status card: connection, group and a short summary; the snapshot preview is in Advanced. */
	private final JLabel dot = new JLabel("●");
	private final JLabel statusLabel = new JLabel();
	private final JLabel groupLabel = new JLabel();
	private final JLabel sharingLabel = new JLabel();
	private final JButton previewButton = smallButton("Show what's sent");
	/** Settings, as a page in place of this one. See {@link SidebarPages}. */
	private final JButton gearButton = new JButton(SidebarIcons.gear());

	/** The one filled button. See the constructor for why it is filled and nothing else is. */
	private final JButton openMapButton = new JButton("Open the map");

	/*
	 * What is happening now: what the map asked for, and where you died. Each is a card that is
	 * hidden while there is nothing in it, and the heading goes with the last of them — see
	 * {@link #refresh}.
	 */
	private final JPanel fromMap = column(ColorScheme.DARK_GRAY_COLOR);
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
	 * Every button that opens the browser says so. "Show on map" opened the website here and drew on
	 * the game's world map on a friend's card, one label for two maps; the game's map is "World map".
	 */
	static final String OPEN_WEBSITE = "Open website";
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
	 * The clue being carried: its tier, its step in a few words, and Route when the step names a tile
	 * — a coordinate's dig spot or a beginner map's. RuneLite's clue plugin draws the tile and the
	 * arrow already; what this adds is which teleport.
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
	private final JComponent searchGap;
	/** Route, pressed beside anything in the sidebar; see {@link RoutePress}. */
	private final RoutePress routePress;
	private final ClueReader clueReader;

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
	/** The map's search, at the top of Home while paired. */
	private final SearchPanel searchPanel;
	/** Each friend's row, the week's meetups and Meet here: the Group tab. */
	private final GroupPanel groupPanel;

	/* Drop luck: one monster's card, or every monster tracked. See {@link DropLuckPanel}. */
	private final JPanel drySection = column(ColorScheme.DARK_GRAY_COLOR);
	private final DropLuckPanel dropLuck;
	private final JLabel dropLuckPaused = new JLabel();

	/*
	 * The group: its invite and its switch when there is one, the join field when there is not.
	 */
	private final JPanel groupActions = row(ColorScheme.DARK_GRAY_COLOR);
	private List<JButton> shownGroupActions = new ArrayList<>();
	/**
	 * A size down, like every other button that is not <i>Open the map</i>. At the default size
	 * these two were as large as the one filled button, so its colour was the only thing marking
	 * which of the three was the one to press.
	 */
	private final JButton copyInviteButton = smallButton("Copy invite");
	/**
	 * The remembered groups, as a menu rather than a column of buttons. They were one full-width
	 * button each under their own heading, which on a player with three groups was four rows to
	 * answer a question asked once a week. The list is read when the menu opens, so it cannot be
	 * stale, and the group being posted to is in it, marked and disabled — the answer to "which
	 * one am I in".
	 */
	private final JButton switchButton = smallButton("Switch ▾");
	/**
	 * The same menu before a group is joined, under the code field: the tabs are not drawn then,
	 * and a player who left one group may still be remembered in others.
	 */
	private final JButton knownGroupsButton = smallButton("Your groups ▾");
	private final JPanel knownGroupsRow;
	private final JButton joinToggle = linkButton("▸ Join another group");
	private boolean joinOpen = false;
	private final JPanel joinBlock;
	private final CompanionViews views;
	private final SidebarPages pages;
	private final StarsPanel starsPanel;

	/**
	 * The join field, and what it says back.
	 *
	 * <p>One line of text and two buttons, in the order a player uses them. The message label is
	 * hidden until there is something to say, because an empty label still takes a line — the same
	 * lesson the destination card carries a comment about.
	 */
	private final JTextField joinField = new JTextField();
	static final String JOIN_PLACEHOLDER = "Paste your connection code";
	private final JButton pasteButton = smallButton("Paste");
	private final JButton joinButton = smallButton("Connect");
	private final JLabel joinMessage = new JLabel();

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

	/**
	 * True while the map is being asked for an invite link.
	 *
	 * <p>The once-a-second refresh would otherwise re-enable the button under the cursor while the
	 * request it started is still in flight, which is an invitation to send three of them.
	 */
	private boolean copyingInvite = false;

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
		GameSprites sprites)
	{
		this.config = config;
		this.plugin = plugin;
		this.destinationState = destinationState;
		this.lootLog = lootLog;
		this.deathWatcher = deathWatcher;
		this.snapshotBuilder = snapshotBuilder;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.identity = identity;
		this.pairing = pairing;
		this.gson = gson;
		this.destinationMarker = destinationMarker;
		this.relay = relay;
		this.directiveStream = directiveStream;
		this.destinationRoute = destinationRoute;
		this.shortestPath = shortestPath;
		this.routePress = routePress;
		this.clueReader = clueReader;
		this.searchPanel = new SearchPanel(searchClient, routePress, sprites);
		this.groupPanel = new GroupPanel(destinationState, groupState, routePress, meetupClient, config);
		this.routeSteps = new RouteStepsView(way ->
		{
			destinationRoute.choose(way);
			refresh();
		});
		this.dropLuck = new DropLuckPanel(lootLog, DropLuckPanel.Pictures.of(itemManager));
		this.starsPanel = new StarsPanel(starsClient, destinationState, clientThread, destinationMarker, routePress, this::mapUrl);
		this.sharingControls = new SharingControls(config, ConfigStore.of(configManager));
		this.recommendedSharing = new RecommendedSharing(config, ConfigStore.of(configManager), this::refresh);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		/*
		 * No title. RuneLite's sidebar button already names the plugin, and the row it took is the
		 * one the status card now starts on.
		 */
		JPanel body = column(ColorScheme.DARK_GRAY_COLOR);
		body.setName("companionDaily");

		/* The status card. Dot beside the sentence, so the state reads at a glance and again in words. */
		JPanel status = card();
		JPanel statusRow = new JPanel(new BorderLayout(6, 0));
		statusRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		dot.setFont(dot.getFont().deriveFont(12f));
		statusRow.add(dot, BorderLayout.WEST);
		statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
		statusRow.add(statusLabel, BorderLayout.CENTER);
		gearButton.setName("companion.settings");
		gearButton.setToolTipText("Settings: what you share, the map address and Show what's sent");
		gearButton.getAccessibleContext().setAccessibleName("Settings");
		gearButton.setBorderPainted(false);
		gearButton.setContentAreaFilled(false);
		gearButton.setFocusPainted(false);
		gearButton.setBorder(new EmptyBorder(0, 4, 0, 0));
		gearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		statusRow.add(gearButton, BorderLayout.EAST);
		status.add(left(statusRow));
		groupLabel.setFont(groupLabel.getFont().deriveFont(12f));
		status.add(left(groupLabel));
		sharingLabel.setFont(sharingLabel.getFont().deriveFont(11f));
		sharingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.add(left(sharingLabel));
		previewButton.setToolTipText("Print the exact snapshot this plugin would send right now");
		body.add(status);
		body.add(gap(6, ColorScheme.DARK_GRAY_COLOR));

		/*
		 * The one button that matters, in the brand's colour, and the only filled one. It was the
		 * first of seven identical grey buttons, and a panel where everything is a button of the same
		 * weight is a panel with no answer to "what do I press".
		 */
		openMapButton.setBackground(ColorScheme.BRAND_ORANGE);
		openMapButton.setForeground(Color.WHITE);
		body.add(fill(openMapButton, ColorScheme.DARK_GRAY_COLOR));
		body.add(gap(10, ColorScheme.DARK_GRAY_COLOR));

		/* The connection is reachable from every tab, including after changing the server. */
		joinBlock = buildJoinBlock();
		body.add(joinBlock);
		body.add(left(joinMessage));
		knownGroupsRow = row(ColorScheme.DARK_GRAY_COLOR);
		knownGroupsRow.add(knownGroupsButton);
		knownGroupsRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		knownGroupsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, knownGroupsButton.getPreferredSize().height + 4));
		body.add(left(knownGroupsRow));
		body.add(recommendedSharing);
		JPanel overview = column(ColorScheme.DARK_GRAY_COLOR);
		overview.setName("companionHome");
		/* First, because "where is it and how do I get there" is the question the map answers best. */
		overview.add(searchPanel);
		searchGap = gap(8, ColorScheme.DARK_GRAY_COLOR);
		overview.add(searchGap);

		/*
		 * Now: a destination with its dismiss, a meetup, a death. "From the map" named two of
		 * the three — the death is this client's own observation — so the heading says when rather
		 * than where from, which is true of all of them.
		 */
		fromMap.add(sectionHead("Now"));
		fromMap.add(gap(3, ColorScheme.DARK_GRAY_COLOR));
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
		fromMap.add(destinationSlot);
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
		fromMap.add(eventSlot);
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
		fromMap.add(deathSlot);
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
		fromMap.add(clueSlot);
		fromMap.add(gap(7, ColorScheme.DARK_GRAY_COLOR));
		overview.add(fromMap);

		/* Drop luck works with no pairing code set, so it sits above everything the group needs. */
		drySection.add(sectionHead("Drop luck"));
		drySection.add(gap(3, ColorScheme.DARK_GRAY_COLOR));
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
		group.add(left(groupActions));
		group.add(joinToggle);
		views = new CompanionViews(overview, group, starsPanel, starsPanel::selected);
		body.add(views);

		JPanel settings = column(ColorScheme.DARK_GRAY_COLOR);
		settings.add(sectionHead("What you share"));
		settings.add(sharingControls);
		settings.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		settings.add(buildAdvanced());
		pages = new SidebarPages(body, settings);

		openMapButton.addActionListener(e -> LinkBrowser.browse(mapUrl() + "/map?task=companion"));
		pasteButton.setToolTipText("Read the clipboard. Only ever when you press this.");
		pasteButton.addActionListener(e -> pasteIntoJoinField());
		joinButton.addActionListener(e -> joinFromField());
		copyInviteButton.addActionListener(e -> copyInvite());
		switchButton.addActionListener(e -> showGroupMenu(switchButton));
		knownGroupsButton.addActionListener(e -> showGroupMenu(knownGroupsButton));
		gearButton.addActionListener(e -> pages.open(true));
		joinToggle.addActionListener(e ->
		{
			joinOpen = !joinOpen;
			refresh();
		});
		previewButton.addActionListener(e -> showSnapshotPreview());
		deathMapButton.addActionListener(e -> showDeathOnMap());
		deathRouteButton.addActionListener(e -> routeToDeath());
		clueRouteButton.addActionListener(e -> routeToClue());
		eventRouteButton.addActionListener(e -> routeToMeetup());
		clueRouteButton.setToolTipText("Plan the way to this step from where you stand. The tile goes to the map in this "
			+ "request only, whether or not you share your clue; the map plans with it and does not keep it.");
		eventRouteButton.setToolTipText("Plan the way to this meetup from where you stand. Nobody is told you are coming.");
		leaveButton.addActionListener(e -> leaveGroup());
		rerouteButton.setToolTipText("Plan the way again from where you stand. Sends your tile to the map once; "
									 + "the map plans with it and does not keep it.");
		rerouteButton.addActionListener(e ->
		{
			routeClient.reroute();
			refresh();
		});
		followButton.addActionListener(e ->
		{
			DestinationRoute.View route = destinationRoute.view();
			destinationRoute.follow(route == null || !route.following);
			refresh();
		});
		dismissButton.setToolTipText("Hide this destination on this client. The map is not told.");
		dismissButton.addActionListener(e ->
		{
			destinationState.dismiss();
			destinationMarker.sync();
			refresh();
		});
		forgetLootButton.addActionListener(e ->
		{
			lootLog.clear();
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

	private static JPanel gap(int height, Color background)
	{
		JPanel spacer = new JPanel();
		spacer.setBackground(background);
		spacer.setPreferredSize(new Dimension(1, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
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
	private static JPanel card()
	{
		JPanel card = column(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 7, 8));
		return card;
	}

	/**
	 * A card with the three pixels under it that separate it from the next, in one piece, so that
	 * hiding a card hides its gap too — a gap left behind by a hidden card is a hole in the column.
	 */
	private static JComponent slot(JPanel card)
	{
		JPanel slot = new JPanel(new BorderLayout());
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 3, 0));
		slot.add(card, BorderLayout.CENTER);
		return left(slot);
	}

	/** Buttons side by side, sharing the width equally — however many of them are showing. */
	private static JPanel row(Color background)
	{
		JPanel row = new JPanel(new GridLayout(1, 0, 4, 0));
		row.setBackground(background);
		return row;
	}

	/** One component the full width of the column, at its own height. */
	private static JPanel fill(JComponent component, Color background)
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
	private static JLabel sectionHead(String text)
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
	 * A full-width code field, then Paste and Connect, in the order the reader uses them.
	 *
	 * <p>No heading and no card around it: the placeholder says what goes in the field, and the
	 * tooltip where it comes from. A heading that repeated the placeholder, the map's address and
	 * "Character sharing paused" above it were three lines about a connection that did not exist yet.
	 */
	private JPanel buildJoinBlock()
	{
		JPanel block = column(ColorScheme.DARK_GRAY_COLOR);
		joinField.setName("join.field");
		joinField.setToolTipText("On osrs-map.com, open the Companion panel and press Copy connection code, "
			+ "or paste a friend's invite");
		joinField.putClientProperty("JTextField.placeholderText", JOIN_PLACEHOLDER);
		joinField.addActionListener(e -> joinFromField());
		block.add(fill(joinField, ColorScheme.DARK_GRAY_COLOR));
		block.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		JPanel buttons = row(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(pasteButton);
		buttons.add(joinButton);
		block.add(left(buttons));
		joinMessage.setFont(joinMessage.getFont().deriveFont(11f));
		joinMessage.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		joinMessage.setVisible(false);
		return left(block);
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
			say("Enter an https:// map address without a login, query or fragment.");
			return false;
		}
		mapAddress.setText(server);
		if (!server.equals(pairing.serverUrl()))
		{
			configManager.setConfiguration(MapCompanionConfig.GROUP, MapCompanionConfig.KEY_RELAY_URL, server);
			joinOpen = true;
			say("Map address changed. Press Connect.");
			/* The field and the line that asks for it are on the daily page. */
			pages.open(false);
			refresh();
		}
		return true;
	}

	private boolean confirmServer()
	{
		return JOptionPane.showOptionDialog(this,
			"Connect to " + pairing.serverUrl() + "?\n\n"
			+ MapCompanionConfig.NETWORK_WARNING + ".\n\n"
			+ "Connecting sends a random member ID, a random key for this\n"
			+ "RuneLite, your name to show and the connection code.\n"
			+ "Selected sharing data is sent while logged in and is\n"
			+ "visible to your group's members.\n\n"
			+ "You can change sharing or leave the group in this sidebar.",
			"Connect to the map", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
			null, new Object[]{"Connect", "Cancel"}, "Cancel") == JOptionPane.OK_OPTION;
	}

	/**
	 * Read the clipboard, once, because somebody pressed the button.
	 *
	 * <p>It fills the field rather than joining, so an accidental press cannot move somebody out of
	 * the group they are in — and it says what it found, because a paste that silently did nothing
	 * is indistinguishable from a paste that worked.
	 */
	private void pasteIntoJoinField()
	{
		String pasted = null;
		try
		{
			Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor))
			{
				pasted = (String) contents.getTransferData(DataFlavor.stringFlavor);
			}
		}
		catch (IOException | UnsupportedFlavorException | RuntimeException e)
		{
			/** A clipboard held by another application, or holding a file. Not worth a stack trace. */
			log.debug("could not read the clipboard", e);
		}

		if (pasted == null || pasted.trim().isEmpty())
		{
			say("There is nothing to paste.");
			return;
		}
		joinField.setText(pasted.trim());
		JoinCode found = JoinCode.parse(pasted);
		say(found == null
			? "No connection code found. Copy it from the map's Companion panel."
			: "Found " + JoinCode.forDisplay(found.code) + ". Press Connect.");
	}

	/**
	 * Join whatever is in the field.
	 *
	 * <p>Everything that makes this work is in {@link JoinCode}; this is the half that has to be
	 * honest when it fails. One sentence, naming both things that are accepted, because "invalid
	 * input" tells somebody holding a Discord message nothing at all.
	 */
	private void joinFromField()
	{
		if (!applyMapAddress())
		{
			return;
		}
		String entered = joinField.getText().trim();
		JoinCode joined = JoinCode.parse(entered.isEmpty() ? pairingCode() : entered);
		if (joined == null)
		{
			say("Paste your connection code from the map's Companion panel.");
			return;
		}
		if (!connect(joined))
		{
			say("Connection cancelled.");
			return;
		}
		joinField.setText("");
		joinOpen = false;
		quiet();
		refresh();
	}

	/** Connect, then offer the recommended set if this was a first Connect to the map. */
	private boolean connect(JoinCode joined)
	{
		return RecommendedSharing.connect(pairing, joined, this::confirmServer, config,
			() -> recommendedSharing.offer(pairing.serverLabel()));
	}

	/** One line under the join field, hidden when there is nothing to say. */
	private void say(String message)
	{
		setWrapped(joinMessage, message);
		joinMessage.setVisible(true);
	}

	/**
	 * Nothing to say under the field. A Connect used to say "Connecting to … Waiting for the map."
	 * here while the status card said the same beside its dot; the card says it, once.
	 */
	private void quiet()
	{
		joinMessage.setVisible(false);
	}

	/**
	 * The remembered groups, as a menu under the button that opened it.
	 *
	 * <p>Built at the press rather than kept up to date, because the list is only ever read here:
	 * the old column of buttons was rebuilt whenever the list or the active code changed so that a
	 * row would not move under the cursor, and a menu that exists only while it is open has no such
	 * problem. The group currently being posted to is in it, marked and disabled.
	 *
	 * <p>Each group by the name the map last sent for it, beside its code — see {@link #groupLabel}.
	 */
	private void showGroupMenu(JButton anchor)
	{
		String active = pairingCode();
		JPopupMenu menu = new JPopupMenu();
		for (String code : pairing.knownCodes())
		{
			boolean current = code.equalsIgnoreCase(active);
			String label = groupLabel(code, pairing.name(code));
			JMenuItem item = groupMenuItem((current ? "● " : "") + label);
			if (current)
			{
				item.setEnabled(false);
			}
			else
			{
				item.setToolTipText("Post to this group instead. One group at a time, like a party.");
				item.addActionListener(e ->
				{
					if (!connect(JoinCode.parse(code)))
					{
						return;
					}
					quiet();
					refresh();
				});
			}
			menu.add(item);
		}
		/*
		 * Hung from the button's right edge, so it opens into the sidebar. Names made it wider than
		 * the button, and hung from the left edge the way a column of codes could be, it ran off the
		 * sidebar's right side.
		 */
		menu.show(anchor, anchor.getWidth() - menu.getPreferredSize().width, anchor.getHeight());
	}

	/**
	 * One group as the menu says it: "Sunday raids · KQX 482", or the code alone.
	 *
	 * <p>The code stays beside the name because it is what the status line prints and what a friend
	 * is told to type; the name is what a person recognises. A name that is only the code — what the
	 * map calls an adopted legacy group until its owner names it — is not printed twice, and a group
	 * the map has not named is its code, which is all this menu used to say about any of them.
	 */
	static String groupLabel(String code, @Nullable String name)
	{
		String printed = JoinCode.forDisplay(code);
		if (name == null || name.replace(" ", "").equalsIgnoreCase(code.replace(" ", "")))
		{
			return printed;
		}
		return name + " · " + printed;
	}

	/**
	 * A menu row that shows its words as words.
	 *
	 * <p>A Swing menu item renders any text that starts with {@code <html>} as markup, and a group's
	 * name is whatever its owner typed on the map — so a name could lay out anything inside this
	 * client, or load an image from anywhere. {@code html.disable} is Swing's own switch for that,
	 * and it is set before the text because the renderer is chosen when the text is. The wrapped
	 * labels elsewhere in this panel escape instead ({@link #setWrapped}); a menu row has no width to
	 * wrap to, so it has no reason to be markup at all.
	 */
	static JMenuItem groupMenuItem(String text)
	{
		JMenuItem item = new JMenuItem();
		item.putClientProperty("html.disable", Boolean.TRUE);
		item.setText(text);
		return item;
	}

	/**
	 * The group as the status line says it: the name alone once the map has sent one, else the code.
	 *
	 * <p>Not {@link #groupLabel}'s "name · code". The line is the card's width less the dot, and the
	 * code is the half of that pair nobody recognises, so on this line it is the tooltip. A name
	 * that is only the code — an adopted legacy group's, until its owner renames it — is printed as
	 * the code.
	 */
	static String statusGroup(String code, @Nullable String name)
	{
		if (name == null || name.trim().isEmpty()
			|| name.replace(" ", "").equalsIgnoreCase(code.replace(" ", "")))
		{
			return JoinCode.forDisplay(code);
		}
		return name;
	}

	/**
	 * The status card's first line. "Not connected" before a group, where the plugin's own word was
	 * "Not paired": pairing is this code's word for it, and nobody joins a map to be paired.
	 */
	static String statusWords(boolean paired, boolean ready, Health health, String status)
	{
		if (!paired)
		{
			return "Not connected";
		}
		if (!ready)
		{
			return "Press Connect";
		}
		return health == Health.LIVE ? "Connected" : status;
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
	 * answers, its refusal in the line, and pressable only when a press would be sent.
	 */
	private void routeState(JButton button, JLabel line, String source, String label, boolean ready)
	{
		RoutePress.Said said = routePress.said(source);
		boolean planning = said != null && said.planning;
		button.setText(planning ? "Planning…" : label);
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
	 * that is words has no Route here yet, because the map is asked by tile and this one names none.
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
			String text = clue.text.length() <= 80 ? clue.text : clue.text.substring(0, 79).trim() + "…";
			return text + " · open the map for this step";
		}
		return "Read the scroll to see its step";
	}

	/** Route to the clue's tile, read again at the press: the scroll may have changed since the paint. */
	private void routeToClue()
	{
		CompanionSnapshot.Clue clue = clueReader.current();
		CompanionSnapshot.Tile tile = ClueReader.routeTile(clue);
		if (tile != null)
		{
			routePress.press(RoutePress.Target.tile("clue", new WorldPoint(tile.x, tile.y, tile.plane),
				clueTitle(clue.tier), null, clue.coordTile != null ? "Coordinate" : "Beginner map"));
		}
		refresh();
	}

	/** The way back to where you died, read again at the press, as Open website reads it. */
	private void routeToDeath()
	{
		long now = System.currentTimeMillis();
		CompanionSnapshot.Death death = deathWatcher.snapshot(now);
		if (death != null)
		{
			routePress.press(RoutePress.Target.tile("death", new WorldPoint(death.tile.x, death.tile.y, death.tile.plane),
				"Where you died", null, deathLine(death.msAgo, death.tile.plane)));
		}
		refresh();
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
		refresh();
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
			LinkBrowser.browse(mapUrl() + deathMapPath(death.tile.x, death.tile.y, death.tile.plane));
		}
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

			dot.setForeground(health.getColor());
			dot.setToolTipText(plugin.getStatus()
							   + " · last post " + ageOf(relay.getLastSuccessMs())
							   + (directiveStream.isConnected() ? " · stream open" : " · stream closed"));
			/* Connection and group each get a line; a long server name cannot bury either. */
			setWrapped(statusLabel, statusWords(paired, ready, health, plugin.getStatus()), CARD_TEXT_WIDTH - 18);
			statusLabel.setToolTipText(dot.getToolTipText());
			/* Before a group, no second line: the map's address is in Settings, and is not news. */
			groupLabel.setVisible(paired);
			if (paired)
			{
				setWrapped(groupLabel, statusGroup(code, pairing.name(code)), CARD_TEXT_WIDTH);
			}
			groupLabel.setToolTipText(paired ? "Group code " + JoinCode.forDisplay(code) : null);

			/*
			 * Character fields are separate from membership: all opt-ins off still sends the latter
			 * after Connect. The tooltip and Advanced preview keep the precise disclosure reachable.
			 */
			sharingLabel.setVisible(paired);
			if (paired)
			{
				setWrapped(sharingLabel, ready ? sharingLine() : "Character sharing paused", CARD_TEXT_WIDTH);
			}
			sharingLabel.setToolTipText(paired ? sharingList() : null);

			/* The search is the map's, asked as a member: a box that could only say "connect" is not shown. */
			searchPanel.setVisible(ready);
			searchGap.setVisible(ready);
			if (ready)
			{
				searchPanel.refresh();
			}

			RelayResponse.Destination destination = destinationState.getDestination();
			/*
			 * Hidden rather than blanked. A label holding a space still takes a line, which is how
			 * the sidebar ended up with gaps where nothing was ever going to be written — and the
			 * same goes for a card, whose padding is a line's worth of nothing.
			 */
			destinationSlot.setVisible(destination != null);
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
				setWrapped(clueTitle, clueTitle(clue.tier), CARD_TEXT_WIDTH);
				setWrapped(clueDetail, clueDetail(clue), CARD_TEXT_WIDTH);
				boolean routable = ClueReader.routeTile(clue) != null;
				clueActions.setVisible(routable);
				routeState(clueRouteButton, clueSaid, "clue", "Route", ready && routable);
			}
			/** The heading goes with the last card under it, rather than naming nothing. */
			fromMap.setVisible(destination != null || event != null || death != null || (clue != null && paired));

			/* Redraws only when its words change, so a click or a search is never painted over. */
			boolean paused = LootLog.PAUSED.equals(lootLog.summary());
			dropLuck.refresh();
			dropLuckPaused.setVisible(paused);
			drySection.setVisible(paused || dropLuck.hasContent());

			/*
			 * The group. In one: its invite, and the switch when there is anywhere to switch to. In
			 * none: the join field outright, and the remembered groups under their own name.
			 */
			List<String> known = pairing.knownCodes();
			boolean others = known.stream().anyMatch(c -> !c.equalsIgnoreCase(code));
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
			copyInviteButton.setEnabled(paired && pairing.hasServerConsent() && !copyingInvite);
			knownGroupsRow.setVisible(!paired && !known.isEmpty());
			List<JButton> groupRow = new ArrayList<>();
			if (paired)
			{
				groupRow.add(copyInviteButton);
			}
			if (others)
			{
				groupRow.add(switchButton);
			}
			shownGroupActions = syncRow(groupActions, groupRow, shownGroupActions);
			groupActions.setVisible(!groupRow.isEmpty());
			joinToggle.setVisible(paired);
			joinToggle.setText(joinOpen ? "▾ Join another group" : "▸ Join another group");
			joinBlock.setVisible(!paired || !pairing.hasServerConsent() || joinOpen);
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
	 * Put a row of buttons in one known order, and only touch it when that order changes.
	 *
	 * <p>A grid appends, so a button that comes back after being hidden would come back at the end
	 * of the row instead of where the player last saw it; comparing the whole list and rebuilding
	 * in one fixed order costs nothing at one refresh a second and keeps the layout still. It was
	 * one row of seven, which is where the rule was learned; it is three short rows now, each
	 * beside the facts it acts on, and the rule is the same.
	 */
	private static List<JButton> syncRow(JPanel row, List<JButton> wanted, List<JButton> shown)
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
		destinationState.clear();
		say("Left the group.");
		pages.open(false);
		refresh();
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

	/**
	 * Every kind of data a snapshot can carry, each with whether it is going out — from the same
	 * settings that gate the fields, in the order the README lists them.
	 *
	 * <p>Pets and collection additions ride on drops and levels, so they are off whenever it is,
	 * whatever their own toggle says — which is what the snapshot does.
	 */
	private List<SharedKind> sharedKinds()
	{
		List<SharedKind> kinds = new ArrayList<>();
		/** First, because it is the only one of these that names the account rather than describing it. */
		kinds.add(new SharedKind("name", config.shareName()));
		kinds.add(new SharedKind("position", config.sharePosition()));
		kinds.add(new SharedKind("levels", config.shareSkills()));
		kinds.add(new SharedKind("activity", config.shareActivity()));
		kinds.add(new SharedKind("account type", config.shareAccountType()));
		kinds.add(new SharedKind("quests", config.shareQuests()));
		kinds.add(new SharedKind("diaries", config.shareDiaries()));
		kinds.add(new SharedKind("carried items", config.shareCapabilities()));
		kinds.add(new SharedKind("permanent travel", config.sharePermanentTravel()));
		kinds.add(new SharedKind("slayer", config.shareSlayer()));
		kinds.add(new SharedKind("clue", config.shareClue()));
		kinds.add(new SharedKind("STASH construction", config.shareStash()));
		kinds.add(new SharedKind("patches", config.shareFarming()));
		kinds.add(new SharedKind("birdhouses", config.shareBirdhouses()));
		kinds.add(new SharedKind("hunter rumours", config.shareHunterRumours()));
		kinds.add(new SharedKind("Mahogany Homes", config.shareMahoganyHomes()));
		kinds.add(new SharedKind("farming contracts", config.shareFarmingContracts()));
		kinds.add(new SharedKind("full quest progress", config.shareQuestLog()));
		kinds.add(new SharedKind("observed loot kills", config.shareLoot()));
		kinds.add(new SharedKind("drops & levels", config.shareMilestones()));
		kinds.add(new SharedKind("pets", config.shareMilestones() && config.sharePets()));
		kinds.add(new SharedKind("collection additions", config.shareMilestones() && config.shareCollectionLog()));
		kinds.add(new SharedKind("death spot", config.shareDeath()));
		return kinds;
	}

	/** A short summary of opt-in character fields, never a claim that membership sends nothing. */
	private String sharingLine()
	{
		List<SharedKind> kinds = sharedKinds();
		List<String> on = new ArrayList<>();
		for (SharedKind kind : kinds)
		{
			if (kind.on)
			{
				on.add(kind.label);
			}
		}
		if (on.isEmpty())
		{
			return "Character sharing off";
		}
		return on.size() <= 2 ? "Sharing: " + String.join(", ", on) : "Sharing " + on.size() + " character fields";
	}

	/** The whole list, for the line's tooltip: "Sharing: name, position, …". */
	private String sharingList()
	{
		List<String> on = new ArrayList<>();
		for (SharedKind kind : sharedKinds())
		{
			if (kind.on)
			{
				on.add(kind.label);
			}
		}
		return on.isEmpty() ? "Sharing nothing" : "Sharing: " + String.join(", ", on);
	}

	/** One kind of data, and whether it is going out. */
	private static final class SharedKind
	{
		final String label;
		final boolean on;

		SharedKind(String label, boolean on)
		{
			this.label = label;
			this.on = on;
		}
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
	private static void setWrapped(JLabel label, String text)
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
	 * How long since a post actually landed, for the tooltip.
	 *
	 * <p>The colour of the dot is a summary; this is the sentence that makes a green that
	 * has not posted in a minute visible as a number rather than a feeling.
	 */
	static String ageOf(long lastSuccessMs)
	{
		if (lastSuccessMs <= 0L)
		{
			return "never";
		}
		long ago = System.currentTimeMillis() - lastSuccessMs;
		if (ago < 2_000L)
		{
			return "just now";
		}
		if (ago < 60_000L)
		{
			return (ago / 1000L) + "s ago";
		}
		return (ago / 60_000L) + "m ago";
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
	private static String escape(@Nullable String text)
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

	/**
	 * Copy an invitation to the clipboard — asking the map for a link that expires, if it can.
	 *
	 * <p>The clipboard, not the chat box: a plugin that typed into the game would be generating
	 * input, which is exactly the line this feature does not cross. Pasting it is the player's own
	 * action, in whatever channel they choose.
	 *
	 * <p><b>Why it asks the map first.</b> A message that spells out the code admits anybody who
	 * finds it in a Discord channel three months later, and there is nothing the holder of the
	 * group can do about that short of closing the group. An invite link carries a token the map
	 * minted, which expires and can be revoked, so the group keeps a way to stop admitting people
	 * without ending the group. Only the map can mint one.
	 *
	 * <p><b>And why the old message is still here.</b> A map that has not shipped that endpoint
	 * answers this request with a 404, and a friend waiting to be invited is not the person to make
	 * pay for that. The fallback is today's text, unchanged, and it still works against today's
	 * server — which is exactly the server that would refuse the request.
	 *
	 * <p>The request carries the member secret the map issued this installation, because minting an
	 * invite is the one thing a member can do that admits a stranger and a member id is not a
	 * secret. A map that wants one and does not get it refuses, and a refusal here is already
	 * handled: it is the same null the callback gets from a 404.
	 */
	private void copyInvite()
	{
		String code = pairingCode();
		String server = pairing.serverUrl();
		if (code.isEmpty() || !pairing.hasServerConsent(server, code) || copyingInvite)
		{
			return;
		}
		/* An invite is asked for as a member, and a member is a character: nobody before login. */
		String memberId = identity.id();
		if (memberId == null)
		{
			say("Log in to a character to copy an invite.");
			return;
		}
		copyingInvite = true;
		copyInviteButton.setEnabled(false);
		copyInviteButton.setText("Asking the map…");
		relay.requestInvite(server, code, memberId, pairing.secret(server, code, memberId), invite ->
							SwingUtilities.invokeLater(() -> finishInvite(server, code, invite)));
	}

	/** Put the invitation on the clipboard, whichever kind of invitation we ended up with. */
	private void finishInvite(String server, String code, @Nullable RelayClient.Invite invite)
	{
		copyingInvite = false;
		copyInviteButton.setText("Copy invite");
		copyInviteButton.setEnabled(!pairingCode().isEmpty() && pairing.hasServerConsent());
		/* An invite arriving after a switch belongs to the old connection, not the current clipboard action. */
		if (!server.equals(mapUrl()) || !code.equalsIgnoreCase(pairingCode()))
		{
			return;
		}

		boolean linked = invite != null && invite.url != null && !invite.url.isEmpty();
		/*
		 * The code is spelled out even on the linked form, because a friend on an older release of
		 * this plugin still has to type six characters into a settings field, and a message that
		 * only works for people who already upgraded is not much of an invitation.
		 */
		String message = linked
						 ? "Join my OSRS map: " + invite.url + " — code " + code
						 : "Join my OSRS map: " + mapUrl() + "/map?join=" + code + "&task=companion"
						 + " — then install the \"OSRS Map Companion\" RuneLite plugin and enter code " + code;

		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(message), null);
		}
		catch (RuntimeException e)
		{
			log.debug("could not copy invite", e);
			say("Couldn't put the invite on the clipboard.");
			return;
		}
		say("Invite copied."
			+ (linked ? describeExpiry(invite.expiresAt, System.currentTimeMillis()) : ""));
	}

	/**
	 * "It expires in 6 days.", from the instant the map stated — or nothing at all.
	 *
	 * <p>Nothing at all is the important half. The expiry is the map's number in the map's units,
	 * and this only ever prints it: anything that is not a future instant in milliseconds produces
	 * no sentence rather than a confident wrong one, which is the right way to be wrong about
	 * somebody else's field.
	 *
	 * <p>Package-private so the cases can be pinned without a clipboard or a server.
	 */
	static String describeExpiry(@Nullable String expiresAt, long now)
	{
		if (expiresAt == null || expiresAt.trim().isEmpty())
		{
			return "";
		}
		long at;
		try
		{
			at = Long.parseLong(expiresAt.trim());
		}
		catch (NumberFormatException e)
		{
			return "";
		}
		long remaining = at - now;
		if (remaining <= 0L)
		{
			return "";
		}
		long days = remaining / 86_400_000L;
		if (days >= 1L)
		{
			return " It expires in " + days + (days == 1L ? " day." : " days.");
		}
		long hours = remaining / 3_600_000L;
		if (hours >= 1L)
		{
			return " It expires in " + hours + (hours == 1L ? " hour." : " hours.");
		}
		return " It expires within the hour.";
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
}
