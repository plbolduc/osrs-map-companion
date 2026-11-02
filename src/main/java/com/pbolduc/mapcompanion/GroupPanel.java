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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongConsumer;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.MapCompanionPanel.label;

/**
 * The group in the sidebar: each friend's row, the week's meetups, and <i>Meet here</i>.
 *
 * <p><b>A friend's row</b> carries what the game's own friends list cannot say. Their disc as the
 * world map draws it, their map colour and their initials, with presence as a small dot in its corner
 * and never as words, because online and world are the game's to say; where they are, <i>near</i> a
 * landmark as the map words it, and <i>Near you</i>; their newest level or drop. <b>Route</b>,
 * beside their name, goes once to where they stand now — pressed again it reads <b>Reroute</b> and
 * asks again, and nothing follows them on its own — and <b>World map</b>, under the card, draws that
 * friend's dot on the game's world map while its group layer is off. A friend who shares no position
 * shows a name and presence and nothing else.
 *
 * <p><b>A meetup</b> says its title, with <b>Route</b> beside it, when in the player's own clock
 * with a countdown, where in words with its world, and who answered: each answer's word, then those
 * people's discs. Route plans the way and tells nobody; the <i>On my way</i> answer is the press that
 * does. The four answers are the map's own and answer for the browser this member is bound to. The
 * next meetup is drawn whole, and each after it as one line, title, day, clock and world, that opens
 * to the same card.
 *
 * <p><b>Meet here</b>, on the Meetups heading, opens a short form under the heading, above the
 * meetups: a title, a day and a time. It makes the meetup at the tile and world the player stands on
 * and copies its link. It is a link on the heading rather than a button of its own under the cards,
 * so the heading is drawn whenever the group is, with or without a meetup to head.
 *
 * <p>Every string was typed by somebody in the group and goes through
 * {@link MapCompanionPanel#setWrapped}, which escapes before Swing reads markup. The rows are
 * rebuilt only when what they say changes, because a rebuild under the pointer eats the click; the
 * form is never rebuilt, so typing in it is never interrupted.
 */
final class GroupPanel extends JPanel
{
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color FALLBACK = new Color(161, 161, 170);
	/*
	 * The website's presence colours, from its MemberDisc: Tailwind's emerald-500, amber-500 and
	 * zinc-400, as the map's stylesheet writes them for sRGB (#00bb7f, #f99c00, #9f9fa9). None is one of
	 * the map's eight disc colours, so a dot never takes the colour of the disc it sits on.
	 */
	private static final Color ONLINE = new Color(0, 187, 127);
	private static final Color IDLE = new Color(249, 156, 0);
	private static final Color OFFLINE = new Color(159, 159, 169);
	/** This member's own answer to a meetup, filled. */
	static final Color CHOSEN = new Color(117, 94, 52);
	/** How long before its start a meetup's countdown turns amber; see {@link #imminent}. */
	static final long IMMINENT_MS = 15 * 60_000L;
	/** The map's amber, as a font colour: the colour of a meetup's countdown once it is imminent. */
	private static final String AMBER = String.format("#%06x", MapPin.AMBER.getRGB() & 0xFFFFFF);
	/**
	 * A meetup's world while this client is on it: the green the website's friends list gives the
	 * player's own world, after the game's own friends list. On any other world it keeps the line's grey.
	 */
	private static final String HERE = "#34d399";
	/** A friend's button for the game's own world map; "Show on world map" does not fit beside Route. */
	static final String WORLD_MAP = "World map";
	private static final String MEETUP_ROUTE_TIP = "Plan the way to this meetup from where you stand. Nobody is told you are coming.";
	/** A news line's picture: the game's 16px skill icons, and an item's sprite shrunk to match. */
	private static final int NEWS_PICTURE = 16;
	/** The picture's place while the client's cache answers, as a route step keeps it. */
	private static final javax.swing.ImageIcon NO_PICTURE_YET = new javax.swing.ImageIcon(
		new java.awt.image.BufferedImage(NEWS_PICTURE, NEWS_PICTURE, java.awt.image.BufferedImage.TYPE_INT_ARGB));

	private final DestinationState destinations;
	private final GroupState group;
	private final RoutePress press;
	private final MeetupClient meetups;
	private final MapCompanionConfig config;
	private final GameSprites sprites;

	private final JPanel friends = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	private final JLabel meetupsHead = MapCompanionPanel.left(new JLabel("Meetups"));
	/**
	 * Under the Meetups heading while Destinations and meetups is off. The map's meetups are taken only
	 * with it on ({@link DestinationState#acceptSocial}), so the section is empty, and a meetup made
	 * with Meet here never shows; this says why, and where the setting is, in the sidebar's own names.
	 */
	static final String MEETUPS_OFF = "Meetups show here with Destinations and meetups on (Settings → In RuneLite).";
	private final JLabel meetupsOff = new JLabel();
	private final JPanel meetupCards = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	private final JButton meetHere = MapCompanionPanel.linkButton("Meet here…");
	private final JLabel meetSaid = new JLabel();
	private final JPanel form = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	private final JTextField title = new JTextField();
	private final JComboBox<String> day = new JComboBox<>();
	private final JComboBox<String> hour = new JComboBox<>();
	private final JComboBox<String> minute = new JComboBox<>();
	private final JLabel formSaid = new JLabel();
	private final JButton make = MapCompanionPanel.smallButton("Make meetup");
	private boolean formOpen;
	/**
	 * Meet here's last word as it stood when the player last opened the form or put it away, which
	 * neither the form nor the heading says again. A refusal is news to the form it answered, not to
	 * the next one, and a meetup made closes the form once rather than each time it is opened.
	 */
	@Nullable
	private MeetupClient.Said quietMeet;
	/** The days the form offers, from today, in the player's zone. */
	private final List<LocalDate> days = new ArrayList<>();
	@Nullable
	private String drawn;
	private boolean ready;
	/** Whether a character is logged in, as of the last refresh: every press here but World map asks as one. */
	private boolean loggedIn = true;
	/** The world this client is on, as the last refresh read it; zero for none. */
	private int here;
	/** The answer last pressed on each meetup, by its id: the pick while that answer is on its way. */
	private final Map<String, String> pressed = new HashMap<>();
	/** The later meetups opened from their line, by id; sorted, so the rows' signature reads them in one order. */
	private final Set<String> openMeetups = new TreeSet<>();
	/**
	 * What redraws itself on every refresh between two rebuilds of the rows: a Route's tooltip while
	 * the map's wait greys it, and a line counting a wait down. A rebuild each second would eat a
	 * press under the pointer, so the rows hand these over when they are built and they are run.
	 */
	private final List<LongConsumer> ticking = new ArrayList<>();
	/**
	 * Copy invite, which {@link ConnectCard} owns, presses and words. The card of a group of one
	 * holds it, under the sentence that is about it; otherwise it sits beside Switch ▾ under the tab.
	 */
	private final JButton invite;
	/** Whether the rows last drawn are that card, so the row under the tab leaves the button out. */
	private boolean holdsInvite;

	GroupPanel(DestinationState destinations, GroupState group, RoutePress press, MeetupClient meetups,
		MapCompanionConfig config, GameSprites sprites, JButton invite)
	{
		this.destinations = destinations;
		this.group = group;
		this.press = press;
		this.meetups = meetups;
		this.config = config;
		this.sprites = sprites;
		this.invite = invite;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		MapCompanionPanel.left(this);
		setName("companionGroup");
		meetupsHead.setFont(meetupsHead.getFont().deriveFont(java.awt.Font.BOLD, 11f));
		meetupsHead.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(friends);
		meetHere.setName("group.meetHere");
		meetHere.setForeground(ColorScheme.BRAND_ORANGE);
		meetHere.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		meetHere.setToolTipText("Make a meetup where you stand, on this world, and copy its link for your clan chat or Discord");
		meetHere.addActionListener(e -> openForm(!formOpen));
		JPanel head = MapCompanionPanel.bar(0, ColorScheme.DARK_GRAY_COLOR, meetupsHead, null, meetHere);
		head.setName("group.meetupsHead");
		head.setBorder(new EmptyBorder(4, 0, 3, 0));
		MapCompanionPanel.capped(head);
		add(MapCompanionPanel.left(head));
		/*
		 * The form opens under the link that opened it, above the meetups, and its answer is said there;
		 * each keeps a card's gap from the meetup under it.
		 */
		buildForm();
		add(form);
		meetSaid.setName("group.meetSaid");
		add(line(meetSaid));
		meetSaid.setBorder(new EmptyBorder(4, 0, 3, 0));
		form.setVisible(false);
		meetupsOff.setName("group.meetupsOff");
		add(line(meetupsOff));
		MapCompanionPanel.setWrapped(meetupsOff, MEETUPS_OFF, WIDTH + 16);
		add(meetupCards);
	}

	/** The form: a title, a day and a time, and the button that makes it. Built once. */
	private void buildForm()
	{
		form.setBorder(javax.swing.BorderFactory.createCompoundBorder(
			new javax.swing.border.MatteBorder(0, 0, 3, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(6, 8, 6, 8)));
		JLabel heading = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		heading.setText("A meetup where you stand");
		form.add(heading);
		title.setName("group.meetTitle");
		title.putClientProperty("JTextField.placeholderText", "What for");
		title.setToolTipText("The meetup's title, up to sixty characters. Everybody in the group sees it.");
		form.add(fill(title));
		form.add(gap(4));
		day.setName("group.meetDay");
		hour.setName("group.meetHour");
		minute.setName("group.meetMinute");
		day.setFont(day.getFont().deriveFont(11f));
		hour.setFont(hour.getFont().deriveFont(11f));
		minute.setFont(minute.getFont().deriveFont(11f));
		form.add(fill(day));
		form.add(gap(4));
		JPanel time = new JPanel(new GridLayout(1, 0, 4, 0));
		time.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		time.add(hour);
		time.add(minute);
		form.add(fill(time));
		form.add(gap(6));
		JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		make.setName("group.meetMake");
		make.setToolTipText("Make the meetup at the tile and world you are on, and copy its link");
		make.addActionListener(e -> makeMeetup());
		JButton cancel = MapCompanionPanel.smallButton("Cancel");
		cancel.addActionListener(e -> openForm(false));
		buttons.add(make);
		buttons.add(cancel);
		form.add(fill(buttons));
		form.add(line(formSaid));
	}

	/** Open the form, or put it away: the player's own press, after which the form's last word is not news. */
	void openForm(boolean open)
	{
		quietMeet = meetups.said("meet");
		showForm(open);
	}

	/** Open the form at the next five minutes past half an hour from now, or close it. */
	private void showForm(boolean open)
	{
		formOpen = open;
		formSaid.setVisible(false);
		if (open)
		{
			fillTimes(System.currentTimeMillis(), ZoneId.systemDefault(), Locale.getDefault(Locale.Category.FORMAT));
			title.setText("");
		}
		form.setVisible(open);
		meetHere.setText(open ? "Meet here ▾" : "Meet here…");
		revalidate();
	}

	/** The choices, from today in the player's zone, with a sensible time already picked. */
	void fillTimes(long now, ZoneId zone, Locale locale)
	{
		ZonedDateTime at = Instant.ofEpochMilli(now).atZone(zone).plusMinutes(30);
		int rounded = ((at.getMinute() + 4) / 5) * 5;
		at = at.withMinute(0).plusMinutes(rounded).withSecond(0).withNano(0);
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		days.clear();
		day.removeAllItems();
		for (int i = 0; i < 7; i++)
		{
			LocalDate date = today.plusDays(i);
			days.add(date);
			day.addItem(i == 0 ? "Today" : i == 1 ? "Tomorrow"
				: date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)));
		}
		boolean twelve = MapCompanionPanel.clockPattern(locale).startsWith("h");
		hour.removeAllItems();
		for (int h = 0; h < 24; h++)
		{
			hour.addItem(twelve ? ((h + 11) % 12 + 1) + (h < 12 ? " AM" : " PM") : String.format(Locale.ENGLISH, "%02d", h));
		}
		minute.removeAllItems();
		for (int m = 0; m < 60; m += 5)
		{
			minute.addItem(String.format(Locale.ENGLISH, ":%02d", m));
		}
		int index = (int) ChronoUnit.DAYS.between(today, at.toLocalDate());
		day.setSelectedIndex(Math.max(0, Math.min(6, index)));
		hour.setSelectedIndex(at.getHour());
		minute.setSelectedIndex(at.getMinute() / 5);
	}

	/** The instant the form says, in the player's zone, or -1 when nothing is picked. */
	long chosenStart(ZoneId zone)
	{
		int d = day.getSelectedIndex();
		int h = hour.getSelectedIndex();
		int m = minute.getSelectedIndex();
		if (d < 0 || d >= days.size() || h < 0 || m < 0)
		{
			return -1L;
		}
		return LocalDateTime.of(days.get(d), LocalTime.of(h, m * 5)).atZone(zone).toInstant().toEpochMilli();
	}

	private void makeMeetup()
	{
		String name = MeetupClient.cleanTitle(title.getText());
		long start = chosenStart(ZoneId.systemDefault());
		if (name.isEmpty())
		{
			refuse(formSaid, "Give the meetup a title.");
			return;
		}
		if (start <= System.currentTimeMillis())
		{
			refuse(formSaid, "Pick a time still to come.");
			return;
		}
		meetups.create(name, start);
		say(formSaid, "Making the meetup…");
	}

	/** The form's own fields, for the panel's test. */
	JTextField title()
	{
		return title;
	}

	/** Redraw from what the map last said and what the presses since have said. Swing. */
	void refresh(boolean ready)
	{
		refresh(ready, System.currentTimeMillis());
	}

	/** The same, as of {@code now}: a confirmation is drawn only while it is news. */
	void refresh(boolean ready, long now)
	{
		refresh(ready, loggedIn, now);
	}

	/**
	 * The same, knowing whether a character is logged in. With none, Route, the answers, Meet here
	 * and Make meetup wait for one, greyed, since each asks the map as a member and a member is a
	 * character; World map draws here and stays.
	 */
	void refresh(boolean ready, boolean loggedIn, long now)
	{
		this.ready = ready;
		this.loggedIn = loggedIn;
		boolean asks = ready && loggedIn;
		String waited = loggedIn ? null : group.stopFinding();
		if (waited != null)
		{
			/* A Route pressed before the logout waits on a stream the logout closed: it says why it stopped. */
			press.refuse(GroupState.source(waited), RouteClient.LOG_IN);
		}
		here = group.world();
		List<RelayResponse.RosterRow> roster = destinations.getRoster();
		List<RelayResponse.Meetup> events = destinations.getEvents();
		String destinationKey = DestinationRoute.keyOf(destinations.getDestination());

		MeetupClient.Said meet = meetups.said("meet");
		/* Meet here's answer to a press made since the form was last opened or put away. */
		MeetupClient.Said fresh = meet == null || meet == quietMeet || meet.sending ? null : meet;
		if (formOpen && fresh != null && fresh.confirms())
		{
			/* Made: the form has done its work, and the heading says so for a moment. */
			showForm(false);
		}
		if (formOpen && fresh != null && fresh.message != null)
		{
			refuse(formSaid, fresh.message);
		}
		boolean showMeet = !formOpen && fresh != null && fresh.shown(now);
		meetSaid.setVisible(showMeet);
		if (showMeet)
		{
			MapCompanionPanel.setWrapped(meetSaid, fresh.message, WIDTH + 16);
			MapCompanionPanel.tone(meetSaid, fresh.refused());
		}
		make.setEnabled(asks && meetups.ready() && (meet == null || !meet.sending));
		meetHere.setEnabled(asks);
		meetupsOff.setVisible(events == null && !config.showDestination());
		if (events != null)
		{
			/* A meetup gone from the list goes from the open ones too, so the set never outgrows the list. */
			openMeetups.removeIf(id -> events.stream().noneMatch(meetup -> id.equals(meetup.id)));
		}

		String words = signature(roster, events, destinationKey, now);
		if (Objects.equals(words, drawn))
		{
			tick(now);
			return;
		}
		drawn = words;
		ticking.clear();
		friends.removeAll();
		holdsInvite = roster != null && roster.isEmpty();
		if (roster != null)
		{
			for (RelayResponse.RosterRow row : roster)
			{
				friends.add(friend(row, destinationKey, now));
			}
			if (roster.isEmpty())
			{
				friends.add(alone());
			}
		}
		meetupCards.removeAll();
		if (events != null)
		{
			/*
			 * The next meetup whole, with its answers; each after it one line that opens to the same
			 * card, as the website lists them, so meetups days away keep the one about to start, and
			 * its answers, on the first screen.
			 */
			for (int i = 0; i < events.size(); i++)
			{
				RelayResponse.Meetup meetup = events.get(i);
				boolean open = i == 0 || openMeetups.contains(meetup.id);
				if (i > 0)
				{
					meetupCards.add(fold(meetup, open, now));
				}
				if (open)
				{
					meetupCards.add(meetup(meetup, destinationKey, now));
				}
			}
		}
		tick(now);
		friends.revalidate();
		meetupCards.revalidate();
		revalidate();
		repaint();
	}

	private void tick(long now)
	{
		for (LongConsumer redraw : ticking)
		{
			redraw.accept(now);
		}
	}

	/** Everything a row says, so they are rebuilt only when that changes. */
	private String signature(@Nullable List<RelayResponse.RosterRow> roster, @Nullable List<RelayResponse.Meetup> events,
		@Nullable String destinationKey, long now)
	{
		StringBuilder sb = new StringBuilder().append(ready).append(loggedIn).append(press.ready()).append(meetups.ready())
			.append(config.showGroupOnWorldMap()).append('|').append(destinationKey).append('|').append(now / 60_000L);
		/* No roster draws nothing and an empty one draws the card of a group of one, so they sign apart. */
		sb.append('|').append(roster == null ? "none" : Integer.toString(roster.size()));
		if (roster != null)
		{
			for (RelayResponse.RosterRow row : roster)
			{
				sb.append(row.id).append(row.name).append(row.colour).append(row.presence).append(row.near)
					.append(row.nearYou).append(row.routable).append(row.news == null ? "" : row.news.title + row.news.atMs)
					.append(group.shown(row.id)).append(group.finding(row.id, now)).append(group.lost(row.id));
				sb.append(group.inside(row.id));
				appendSaid(sb, press.said(GroupState.source(row.id)));
			}
		}
		sb.append(';').append(openMeetups).append(here);
		if (events != null)
		{
			for (RelayResponse.Meetup meetup : events)
			{
				sb.append(meetup.id).append(meetup.title).append(meetup.startsAtMs).append(meetup.near).append(meetup.world)
					.append(group.mine(meetup, now));
				for (RelayResponse.MeetupAnswer face : meetup.who)
				{
					sb.append(face.name).append(face.colour).append(face.answer);
				}
				appendSaid(sb, press.said(source(meetup)));
				MeetupClient.Said answer = meetups.said("answer:" + meetup.id);
				sb.append(answer == null ? "" : answer.sending + "|" + answer.message);
			}
		}
		return sb.toString();
	}

	private static void appendSaid(StringBuilder sb, @Nullable RoutePress.Said said)
	{
		sb.append(said == null ? "" : said.planning + "|" + said.message + "|" + said.key);
	}

	/**
	 * A group of one: what the tab is for, said where the friends will be.
	 *
	 * <p>Most groups start this way now, because Connect makes one for a player alone. An empty tab
	 * reads as "nothing works"; this reads as "nobody yet", and holds the button that changes that,
	 * Copy invite, under the sentence that is about it. It comes with the map's empty roster, and
	 * goes, giving the button back to the row under the tab, the moment a friend's row arrives.
	 */
	static final String ALONE = "Just you so far. Send a friend an invite; they paste it into their sidebar "
		+ "and appear here and on osrs-map.com.";

	private JPanel alone()
	{
		JPanel card = card();
		card.setName("group.alone");
		card.add(MapCompanionPanel.wrapped(ALONE, 11f, ColorScheme.LIGHT_GRAY_COLOR, WIDTH));
		JPanel actions = actions();
		actions.add(invite);
		card.add(gap(5));
		card.add(MapCompanionPanel.left(actions));
		return slot(card);
	}

	/**
	 * Whether the card of a group of one is drawn, holding Copy invite, so the row under the tab leaves
	 * it out. It says what was drawn rather than what the roster says now, so the two places never both
	 * want the button and never both let it go.
	 */
	boolean holdsInvite()
	{
		return holdsInvite;
	}

	/** One friend: their disc and name with Route beside it, where they are, their news, and World map. */
	private JPanel friend(RelayResponse.RosterRow row, @Nullable String destinationKey, long now)
	{
		JPanel card = card();
		card.setName("group.friend." + row.id);
		String source = GroupState.source(row.id);
		RoutePress.Said said = press.said(source);
		JButton route = null;
		if (row.routable)
		{
			boolean planning = said != null && said.planning;
			boolean finding = group.finding(row.id, now);
			boolean routedHere = said != null && said.key != null && said.key.equals(destinationKey);
			route = MapCompanionPanel.smallButton(planning ? "Planning…" : finding ? "Finding…"
				: routedHere ? "Reroute" : "Route");
			route.setName("group.route." + row.id);
			route.setEnabled(ready && loggedIn && !planning && !finding && press.ready());
			String own = !loggedIn ? RouteClient.LOG_IN
				: "Plan the way to where " + row.name + " stands now. Nothing follows them; press again to ask again.";
			route.setToolTipText(own);
			JButton held = route;
			ticking.add(at -> MapWait.tip(held, own, press.waitMs(at)));
			route.addActionListener(e -> routeToFriend(row.id));
		}
		JLabel name = label(12f, Color.WHITE);
		name.setIcon(new Disc(colour(row.colour), MapPin.initials(row.name), presenceColour(row.presence)));
		name.setIconTextGap(6);
		MapCompanionPanel.setWrapped(name, row.name, WIDTH - name.getIcon().getIconWidth() - name.getIconTextGap()
			- (route == null ? 0 : route.getPreferredSize().width + 4));
		name.setToolTipText(presenceWord(row.presence));
		/* Route on the name's line, as a meetup's sits beside its title; World map keeps the row under the card. */
		card.add(MapCompanionPanel.left(route == null ? name : beside(name, route)));
		String where = whereLine(row);
		if (where != null)
		{
			card.add(MapCompanionPanel.wrapped(where, 11f, ColorScheme.LIGHT_GRAY_COLOR, WIDTH));
		}
		if (row.news != null)
		{
			JLabel news = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			news.setName("group.news." + row.id);
			/*
			 * The news's own picture, as the website's friend rows draw it: the item a drop or reward
			 * names, else the skill a level names, else the mark a quest or a diary has. Every news
			 * line then starts in the same column.
			 */
			int progress = progressSprite(row.news.kind);
			boolean pictured = row.news.itemId != null || row.news.skill != null || progress > 0;
			MapCompanionPanel.setWrapped(news, row.news.title + " · " + ago(now - row.news.atMs),
				pictured ? WIDTH - NEWS_PICTURE - 5 : WIDTH);
			news.setIconTextGap(5);
			/*
			 * The picture arrives later, and the card is measured now: a blank of its size keeps the
			 * line as tall as it will be, so the card never grows under its own slot when it lands.
			 */
			if (pictured)
			{
				news.setIcon(NO_PICTURE_YET);
			}
			if (row.news.itemId != null)
			{
				sprites.item(news, row.news.itemId, NEWS_PICTURE);
			}
			else if (row.news.skill != null)
			{
				sprites.skill(news, row.news.skill, NEWS_PICTURE);
			}
			else if (progress > 0)
			{
				sprites.sprite(news, progress, NEWS_PICTURE);
			}
			card.add(news);
		}
		boolean positioned = row.routable || row.near != null;
		if (!positioned)
		{
			return slot(card);
		}
		JPanel actions = actions();
		boolean layer = config.showGroupOnWorldMap();
		boolean shown = group.shown(row.id);
		/*
		 * Named for the map it draws on. "Show on map" meant the game's world map here and the website
		 * on the death card; the website's buttons say Open website, and this one says which map.
		 */
		JButton map = MapCompanionPanel.smallButton(layer ? "On world map" : shown ? "Hide on map" : WORLD_MAP);
		map.setName("group.show." + row.id);
		map.setEnabled(!layer && ready);
		map.setToolTipText(layer ? "Your whole group is on the world map already"
			: "Draw " + row.name + "'s dot on the game's world map, without the rest of the group");
		map.addActionListener(e ->
		{
			group.toggleShown(row.id);
			drawn = null;
			refresh(ready);
		});
		actions.add(map);
		card.add(gap(5));
		card.add(MapCompanionPanel.left(actions));
		/* Inside an instance nothing was asked, so this is newer than any answer the press last gave. */
		boolean pressed = !group.inside(row.id) && said != null && said.message != null;
		String message = group.inside(row.id) ? row.name + " is inside an instance. Route works once they're out."
			: pressed ? said.message
			: group.lost(row.id) ? row.name + " is not sharing a position just now." : null;
		if (message != null)
		{
			/* Each says why a Route did not happen. */
			JLabel line = line(new JLabel());
			line.setVisible(true);
			MapCompanionPanel.setWrapped(line, message, WIDTH);
			MapCompanionPanel.tone(line, true);
			card.add(line);
			if (pressed)
			{
				ticking.add(at -> MapCompanionPanel.setWrapped(line, press.words(said, at), WIDTH));
			}
		}
		return slot(card);
	}

	/**
	 * Route to a friend: now, from their fresh dot, or once the stream that asks for it brings one.
	 * Logged out, there is no stream to bring one, so it says to log in rather than wait half a minute
	 * and then say the friend shares no position.
	 */
	void routeToFriend(String id)
	{
		if (!loggedIn)
		{
			press.refuse(GroupState.source(id), RouteClient.LOG_IN);
			drawn = null;
			refresh(ready, loggedIn, System.currentTimeMillis());
			return;
		}
		RelayResponse.MemberDot dot = group.routeTo(id, System.currentTimeMillis());
		if (dot != null)
		{
			if (!dot.instance)
			{
				press.press(GroupState.routeTarget(dot, destinations.getRoster()));
			}
		}
		drawn = null;
		refresh(ready, loggedIn, System.currentTimeMillis());
	}

	/** One meetup: what, when, where, who, then Route and the answers. */
	private JPanel meetup(RelayResponse.Meetup meetup, @Nullable String destinationKey, long now)
	{
		JPanel card = card();
		card.setName("group.meetup." + meetup.id);
		String source = source(meetup);
		RoutePress.Said said = press.said(source);
		boolean planning = said != null && said.planning;
		JButton route = MapCompanionPanel.smallButton(planning ? "Planning…" : "Route");
		route.setName("group.meetupRoute." + meetup.id);
		route.setEnabled(ready && loggedIn && !planning && press.ready());
		String own = loggedIn ? MEETUP_ROUTE_TIP : RouteClient.LOG_IN;
		route.setToolTipText(own);
		ticking.add(at -> MapWait.tip(route, own, press.waitMs(at)));
		route.addActionListener(e ->
		{
			press.press(RoutePress.Target.tile(source, new WorldPoint(meetup.x, meetup.y, meetup.plane), meetup.title,
				meetup.world, MapCompanionPanel.meetupStarts(meetup.startsAtMs, System.currentTimeMillis(), ZoneId.systemDefault(),
					Locale.getDefault(Locale.Category.FORMAT))));
			drawn = null;
			refresh(ready);
		});
		/*
		 * Route beside the title rather than on a row of its own over the answers: a meetup was three
		 * rows of buttons, and the title's line had room for one.
		 */
		JLabel name = label(12f, Color.WHITE);
		MapCompanionPanel.setWrapped(name, meetup.title, WIDTH - route.getPreferredSize().width - 4);
		card.add(MapCompanionPanel.left(beside(name, route)));
		JLabel when = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		setWhen(when, when(meetup.startsAtMs, now, ZoneId.systemDefault(),
			Locale.getDefault(Locale.Category.FORMAT)), meetup.startsAtMs, now, WIDTH);
		card.add(when);
		String where = meetupWhere(meetup);
		if (!where.isEmpty())
		{
			JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			setMarkup(line, markup(where, onItsWorld(meetup) ? "W" + meetup.world : null, HERE, WIDTH));
			card.add(line);
		}
		if (!rollUp(meetup.who).isEmpty())
		{
			RollUp who = new RollUp(meetup.who, WIDTH);
			who.setName("group.who." + meetup.id);
			card.add(gap(3));
			card.add(MapCompanionPanel.left(who));
		}
		String mine = group.mine(meetup, now);
		MeetupClient.Said answer = meetups.said("answer:" + meetup.id);
		boolean sending = answer != null && answer.sending;
		/*
		 * The pick is the answer pressed while it is on its way, else the one given, and it keeps its
		 * fill while the others cannot be pressed. FlatLaf paints a disabled button in its own disabled
		 * colour whatever its background, so the pick stays enabled and ignores the press instead.
		 */
		String pick = sending ? pressed.getOrDefault(meetup.id, mine) : mine;
		boolean pressable = ready && loggedIn && !sending && meetups.ready();
		card.add(gap(5));
		JPanel answers = new JPanel(new GridLayout(2, 2, 4, 4));
		answers.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (String option : SocialWire.ANSWERS)
		{
			JButton button = MapCompanionPanel.smallButton(answerWord(option));
			button.setName("group.answer." + meetup.id + "." + option);
			boolean chosen = option.equals(pick);
			if (chosen)
			{
				button.setBackground(CHOSEN);
				button.setForeground(Color.WHITE);
			}
			button.setEnabled(pressable || chosen);
			button.addActionListener(e ->
			{
				if (pressable)
				{
					answer(meetup.id, option);
				}
			});
			answers.add(button);
		}
		MapCompanionPanel.capped(answers);
		card.add(MapCompanionPanel.left(answers));
		boolean routed = said != null && said.message != null;
		String message = routed ? said.message : answer != null ? answer.message : null;
		if (message != null)
		{
			/* A Route or an answer that did not happen: an answer that lands says nothing, its fill does. */
			JLabel line = line(new JLabel());
			line.setVisible(true);
			MapCompanionPanel.setWrapped(line, message, WIDTH);
			MapCompanionPanel.tone(line, true);
			card.add(line);
			/* Each wait from its own gate: a Route's is the routes' wait, an answer's the meetups'. */
			ticking.add(at -> MapCompanionPanel.setWrapped(line, routed ? press.words(said, at) : meetups.words(answer, at), WIDTH));
		}
		return slot(card);
	}

	private void answer(String eventId, String answer)
	{
		pressed.put(eventId, answer);
		meetups.answer(eventId, answer);
		drawn = null;
		refresh(ready);
	}

	/**
	 * A meetup after the next, as one line: its title, day, clock and world, which opens to its card in
	 * place and reads ▾ while open. Its answers are in the card, not on the line, as on the website.
	 */
	private JButton fold(RelayResponse.Meetup meetup, boolean open, long now)
	{
		String words = (open ? "▾ " : "▸ ") + meetup.title + " · " + dayAndClock(meetup.startsAtMs, now, ZoneId.systemDefault(),
			Locale.getDefault(Locale.Category.FORMAT)) + (meetup.world == null ? "" : " · W" + meetup.world);
		JButton line = MapCompanionPanel.linkButton("");
		line.setName("group.meetupFold." + meetup.id);
		/* Wrapped as a label is, since the title is whatever the group typed and a button's own text would widen the tab. */
		line.setText(markup(words, onItsWorld(meetup) ? "W" + meetup.world : null, HERE, WIDTH + 16));
		line.setToolTipText(open ? "Fold this meetup to its line" : "Open this meetup: who is coming, Route and your answer");
		line.addActionListener(e ->
		{
			if (!openMeetups.remove(meetup.id))
			{
				openMeetups.add(meetup.id);
			}
			drawn = null;
			refresh(ready);
		});
		return line;
	}

	/** Whether this client is on the meetup's world now, so its world is drawn green. */
	private boolean onItsWorld(RelayResponse.Meetup meetup)
	{
		return meetup.world != null && here > 0 && meetup.world == here;
	}

	static String source(RelayResponse.Meetup meetup)
	{
		return "meetup:" + meetup.id;
	}

	/** "near Lumbridge Castle · Near you", or null for a friend who shares no position. */
	@Nullable
	static String whereLine(RelayResponse.RosterRow row)
	{
		List<String> parts = new ArrayList<>();
		if (row.near != null)
		{
			parts.add(row.near);
		}
		if (Boolean.TRUE.equals(row.nearYou))
		{
			parts.add("Near you");
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	/** "near Lumbridge Castle · W302": where in words, and the world to be on. */
	static String meetupWhere(RelayResponse.Meetup meetup)
	{
		List<String> parts = new ArrayList<>();
		if (meetup.near != null)
		{
			parts.add(meetup.near);
		}
		if (meetup.world != null)
		{
			parts.add("W" + meetup.world);
		}
		return String.join(" · ", parts);
	}

	/** The roll-up's answers in the order a reader asks them, who is coming before who is not. */
	private static final List<String> ROLL_UP = Arrays.asList("going", "on_my_way", "maybe", "no", "arrived");

	/**
	 * Who answered what, in the roll-up's order and by the word it is said with, Here for those already
	 * there: only the answers somebody gave, each with its people in the map's order.
	 */
	static Map<String, List<RelayResponse.MeetupAnswer>> rollUp(List<RelayResponse.MeetupAnswer> who)
	{
		Map<String, List<RelayResponse.MeetupAnswer>> answers = new LinkedHashMap<>();
		for (String answer : ROLL_UP)
		{
			List<RelayResponse.MeetupAnswer> people = new ArrayList<>();
			for (RelayResponse.MeetupAnswer face : who)
			{
				if (answer.equals(face.answer))
				{
					people.add(face);
				}
			}
			if (!people.isEmpty())
			{
				answers.put("arrived".equals(answer) ? "Here" : answerWord(answer), people);
			}
		}
		return answers;
	}

	/** "Going: Rob, Dax · Maybe: Sunday (you) · Here: Cara": the roll-up in words, for a screen reader. */
	static String rollUpWords(List<RelayResponse.MeetupAnswer> who)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, List<RelayResponse.MeetupAnswer>> answer : rollUp(who).entrySet())
		{
			parts.add(answer.getKey() + ": " + names(answer.getValue()));
		}
		return String.join(" · ", parts);
	}

	private static String names(List<RelayResponse.MeetupAnswer> people)
	{
		List<String> names = new ArrayList<>();
		for (RelayResponse.MeetupAnswer face : people)
		{
			names.add(name(face));
		}
		return String.join(", ", names);
	}

	/**
	 * A member's name for a tooltip, with any tag taken out: Swing reads a tooltip that starts with
	 * {@code <html>} as markup, and a disc's tooltip starts with whatever name the member typed.
	 */
	private static String name(RelayResponse.MeetupAnswer face)
	{
		return MapPin.plain(face.name) + (Boolean.TRUE.equals(face.you) ? " (you)" : "");
	}

	static String answerWord(String answer)
	{
		switch (answer)
		{
			case "going":
				return "Going";
			case "maybe":
				return "Maybe";
			case "no":
				return "Can't";
			case "on_my_way":
				return "On my way";
			default:
				return answer;
		}
	}

	/**
	 * "Today 7:42 PM · in 1h 20m": the day and the clock in the player's zone, as they would say it
	 * to the group, and how long until, as the map's own meetup card counts down. Past twelve hours
	 * the day and the clock say it alone, as the meetup line under Now does: a count of days beside
	 * the day's own name would say the day twice.
	 */
	static String when(long startsAtMs, long nowMs, ZoneId zone, Locale locale)
	{
		String at = dayAndClock(startsAtMs, nowMs, zone, locale);
		String countdown = countdown(startsAtMs, nowMs);
		return countdown == null ? at : at + " · " + countdown;
	}

	/**
	 * "Today 7:42 PM", "Wednesday 8:59 PM", "4 Nov 18:00": the day and the clock alone, as a folded
	 * meetup's line says them. They are the words a meetup's Route writes under the destination's
	 * name, so they are {@link MapCompanionPanel#meetupStarts}'s, and the two can never disagree.
	 */
	static String dayAndClock(long startsAtMs, long nowMs, ZoneId zone, Locale locale)
	{
		return MapCompanionPanel.meetupStarts(startsAtMs, nowMs, zone, locale);
	}

	/** The countdown a when line ends with: "in 25m", "in 3h 20m", "now", "started", or null past twelve hours. */
	@Nullable
	static String countdown(long startsAtMs, long nowMs)
	{
		long minutes = Math.round((startsAtMs - nowMs) / 60_000.0);
		if (minutes <= 0)
		{
			return minutes > -60 ? "now" : "started";
		}
		if (minutes < 60)
		{
			return "in " + minutes + "m";
		}
		if (minutes < 12 * 60)
		{
			long rest = minutes % 60;
			return "in " + (minutes / 60) + "h" + (rest == 0 ? "" : " " + rest + "m");
		}
		return null;
	}

	/**
	 * Whether a meetup is about to start or under way: from a quarter of an hour before its start
	 * until its line stops saying "now". The map's meetup card turns its countdown amber inside the
	 * same window, where amber means a thing to act on, and outside it a countdown is only a time.
	 */
	static boolean imminent(long startsAtMs, long nowMs)
	{
		return startsAtMs - nowMs < IMMINENT_MS && !"started".equals(countdown(startsAtMs, nowMs));
	}

	/**
	 * A meetup's when line — the Group tab's, or the one meetup's under Now — with its countdown in the
	 * map's amber while the meetup is imminent, and the rest in the label's own colour.
	 */
	static void setWhen(JLabel label, String line, long startsAtMs, long nowMs, int width)
	{
		setMarkup(label, markup(line, imminent(startsAtMs, nowMs) ? countdown(startsAtMs, nowMs) : null, AMBER, width));
	}

	/**
	 * A line wrapped as {@link MapCompanionPanel#setWrapped} wraps it, with one part, its last
	 * occurrence, in a colour of its own; without the part, exactly what setWrapped would set. Every
	 * word is escaped, since some of these lines carry what somebody in the group typed.
	 */
	static String markup(String line, @Nullable String part, String colour, int width)
	{
		int at = part == null ? -1 : line.lastIndexOf(part);
		String words = at < 0 ? MapCompanionPanel.escape(line)
			: MapCompanionPanel.escape(line.substring(0, at)) + "<font color='" + colour + "'>" + MapCompanionPanel.escape(part)
				+ "</font>" + MapCompanionPanel.escape(line.substring(at + part.length()));
		return "<html><body width='" + width + "'>" + words + "</body></html>";
	}

	/** Set a label's markup only when it changed: the same text set again still repaints it. */
	private static void setMarkup(JLabel label, String html)
	{
		if (!html.equals(label.getText()))
		{
			label.setText(html);
		}
	}

	/** "5m ago", "2h ago": how old a friend's news is. */
	static String ago(long ms)
	{
		long minutes = Math.max(0, ms / 60_000L);
		if (minutes < 1)
		{
			return "just now";
		}
		return minutes < 60 ? minutes + "m ago" : (minutes / 60) + "h ago";
	}

	/**
	 * The game's own mark for a finished quest or diary, as the website's news line draws them: the
	 * world map's quest-start star and the diary tab's icon. Zero for any other news.
	 */
	static int progressSprite(@Nullable String kind)
	{
		return "quest".equals(kind) ? SpriteID.Mapfunction.QUEST_START
			: "diary".equals(kind) ? SpriteID.SideIcons.ACHIEVEMENT_DIARIES : 0;
	}

	static String presenceWord(@Nullable String presence)
	{
		return "online".equals(presence) ? "Online" : "idle".equals(presence) ? "Idle" : "Offline";
	}

	static Color presenceColour(@Nullable String presence)
	{
		return "online".equals(presence) ? ONLINE : "idle".equals(presence) ? IDLE : OFFLINE;
	}

	static Color colour(@Nullable String hex)
	{
		return MapPin.colour(hex, FALLBACK);
	}

	/** A line under a press, for a press on its way. */
	private void say(JLabel line, String message)
	{
		line.setVisible(true);
		MapCompanionPanel.setWrapped(line, message, WIDTH);
		MapCompanionPanel.tone(line, false);
	}

	/** The same line, for a press that did not happen. */
	private void refuse(JLabel line, String message)
	{
		say(line, message);
		MapCompanionPanel.tone(line, true);
	}

	private static JPanel card()
	{
		JPanel card = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		return card;
	}

	private static JPanel slot(JPanel card)
	{
		JPanel slot = new Slot();
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 3, 0));
		slot.add(card);
		return MapCompanionPanel.left(slot);
	}

	/**
	 * A card's place in the column: the column's width, and the card's height as it is when laid
	 * out. A height fixed when the card was built falls short once anything in it grows, and
	 * BoxLayout cuts a row to its maximum and squeezes the column under it.
	 */
	private static final class Slot extends JPanel
	{
		Slot()
		{
			super(new java.awt.BorderLayout());
		}

		@Override
		public java.awt.Dimension getMaximumSize()
		{
			return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static JPanel actions()
	{
		JPanel actions = new JPanel(new GridLayout(1, 0, 4, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return actions;
	}

	/**
	 * A card's first line with its one button: the words, wrapped to the room the button leaves, and
	 * the button at the right, held to the top when the words take two lines.
	 */
	private static JPanel beside(JLabel words, JButton button)
	{
		JPanel hug = new JPanel(new java.awt.BorderLayout());
		hug.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hug.add(button, java.awt.BorderLayout.NORTH);
		return MapCompanionPanel.capped(MapCompanionPanel.bar(4, ColorScheme.DARKER_GRAY_COLOR, null, words, hug));
	}

	private static JPanel fill(Component component)
	{
		JPanel holder = new JPanel(new GridLayout(1, 1));
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.add(component);
		return MapCompanionPanel.left(MapCompanionPanel.capped(holder));
	}

	private static JPanel gap(int height)
	{
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		spacer.setPreferredSize(new java.awt.Dimension(1, height));
		spacer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height));
		/* A plain panel's FlowLayout asks for 10px at least, and a squeezed column grows a gap to that. */
		spacer.setMinimumSize(new java.awt.Dimension(0, height));
		return MapCompanionPanel.left(spacer);
	}

	private static JLabel line(JLabel line)
	{
		line.setFont(line.getFont().deriveFont(11f));
		line.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		line.setBorder(new EmptyBorder(4, 0, 0, 0));
		line.setVisible(false);
		return line;
	}

	/**
	 * A friend's disc as the world map draws them — {@link MapPin}'s own, in their map colour with a
	 * black edge and their initials, so two friends of one colour are told apart by their letters — with
	 * presence as a small dot in its corner.
	 *
	 * <p>The dot sits in a halo of the card's own colour two pixels wide, as the website's does, so it
	 * reads as a dot on the disc rather than as a second disc touching it, whatever the disc's colour.
	 * It hangs a third of itself past the disc's edge, as the website's does, which keeps it off the
	 * second letter; past the edge the halo is the card's colour on the card, so the icon is no larger
	 * than the disc's own picture.
	 */
	static final class Disc implements Icon
	{
		/** The presence dot's size, and where its corner sits on the icon, across and down. */
		static final int DOT = 6;
		static final int DOT_AT = 14;
		private final java.awt.image.BufferedImage disc;
		private final Color presence;

		Disc(Color colour, String initials, Color presence)
		{
			this.disc = MapPin.disc(colour, initials, false);
			this.presence = presence;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.drawImage(disc, x, y, null);
			int dx = x + DOT_AT;
			int dy = y + DOT_AT;
			g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g2.fillOval(dx - 2, dy - 2, DOT + 4, DOT + 4);
			g2.setColor(presence);
			g2.fillOval(dx, dy, DOT, DOT);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return disc.getWidth();
		}

		@Override
		public int getIconHeight()
		{
			return disc.getHeight();
		}
	}

	/**
	 * Who answered a meetup, as the website rolls them up: each answer somebody gave, its word, then
	 * their discs with initials — Going, On my way, Maybe, Can't, then Here — so whether Rob is coming
	 * is read rather than hovered for. A new line starts where the next answer would pass the card's
	 * edge, and each disc's tooltip is its member's name.
	 *
	 * <p>Words and discs are labels placed by hand: the card's width is known, and a flow layout in a
	 * column reports one line's height however many it wraps to.
	 */
	static final class RollUp extends JPanel
	{
		/** A disc's picture, which leaves three pixels between two discs' edges. */
		private static final int STEP = 20;
		private static final int AFTER_WORD = 4;
		private static final int BETWEEN = 10;
		private static final int LINE = STEP + 2;

		RollUp(List<RelayResponse.MeetupAnswer> who, int width)
		{
			super(null);
			setOpaque(false);
			int x = 0;
			int y = 0;
			for (Map.Entry<String, List<RelayResponse.MeetupAnswer>> answer : rollUp(who).entrySet())
			{
				JLabel word = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
				word.setText(answer.getKey());
				word.setToolTipText(answer.getKey() + ": " + names(answer.getValue()));
				java.awt.Dimension size = word.getPreferredSize();
				if (x > 0 && x + size.width + AFTER_WORD + STEP * answer.getValue().size() > width)
				{
					x = 0;
					y += LINE;
				}
				word.setBounds(x, y + (STEP - size.height) / 2, size.width, size.height);
				add(word);
				x += size.width + AFTER_WORD;
				for (RelayResponse.MeetupAnswer face : answer.getValue())
				{
					/* More people than one line holds carry on under it, word or no word. */
					if (x + STEP > width)
					{
						x = 0;
						y += LINE;
					}
					JLabel disc = new JLabel(new javax.swing.ImageIcon(
						MapPin.disc(colour(face.colour), MapPin.initials(face.name), false)));
					disc.setToolTipText(name(face));
					disc.setBounds(x, y, STEP, STEP);
					add(disc);
					x += STEP;
				}
				x += BETWEEN;
			}
			java.awt.Dimension size = new java.awt.Dimension(width, y + STEP);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, size.height));
			getAccessibleContext().setAccessibleName(rollUpWords(who));
		}
	}
}
