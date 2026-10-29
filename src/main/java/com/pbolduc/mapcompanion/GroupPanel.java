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

import java.awt.BasicStroke;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;

/**
 * The group in the sidebar: each friend's row, the week's meetups, and <i>Meet here</i>.
 *
 * <p><b>A friend's row</b> carries what the game's own friends list cannot say. Their disc in the
 * colour their dot has on the map, with presence as a small dot in its corner and never as words,
 * because online and world are the game's to say; where they are, <i>near</i> a landmark as the map
 * words it, and <i>Near you</i>; their newest level or drop. Then <b>Route</b>, once, to where they
 * stand now — pressed again it reads <b>Reroute</b> and asks again, and nothing follows them on its
 * own — and <b>World map</b>, which draws that friend's dot on the game's world map while its group layer is
 * off. A friend who shares no position shows a name and presence and nothing else.
 *
 * <p><b>A meetup</b> says its title, when in the player's own clock with a countdown, where in
 * words with its world, and who is coming as their discs. <b>Route</b> plans the way and tells
 * nobody; the <i>On my way</i> answer is the press that does. The four answers are the map's own
 * and answer for the browser this member is bound to.
 *
 * <p><b>Meet here</b>, on the Meetups heading, opens a short form under the meetups: a title, a day
 * and a time. It makes the meetup at the tile and world the player stands on and copies its link.
 * It is a link on the heading rather than a button of its own under the cards, so the heading is
 * drawn whenever the group is, with or without a meetup to head.
 *
 * <p>Every string was typed by somebody in the group and goes through
 * {@link MapCompanionPanel#setWrapped}, which escapes before Swing reads markup. The rows are
 * rebuilt only when what they say changes, because a rebuild under the pointer eats the click; the
 * form is never rebuilt, so typing in it is never interrupted.
 */
final class GroupPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color FALLBACK = new Color(161, 161, 170);
	private static final Color ONLINE = new Color(74, 222, 128);
	private static final Color IDLE = new Color(250, 204, 21);
	private static final Color OFFLINE = new Color(113, 113, 122);
	private static final Color CHOSEN = new Color(117, 94, 52);
	/** A friend's button for the game's own world map; "Show on world map" does not fit beside Route. */
	static final String WORLD_MAP = "World map";

	private final transient DestinationState destinations;
	private final transient GroupState group;
	private final transient RoutePress press;
	private final transient MeetupClient meetups;
	private final transient MapCompanionConfig config;

	private final JPanel friends = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	private final JLabel meetupsHead = MapCompanionPanel.left(new JLabel("Meetups"));
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
	/** The days the form offers, from today, in the player's zone. */
	private final List<LocalDate> days = new ArrayList<>();
	@Nullable
	private String drawn;
	private boolean ready;

	GroupPanel(DestinationState destinations, GroupState group, RoutePress press, MeetupClient meetups,
		MapCompanionConfig config)
	{
		this.destinations = destinations;
		this.group = group;
		this.press = press;
		this.meetups = meetups;
		this.config = config;
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
		JPanel head = new JPanel(new java.awt.BorderLayout());
		head.setName("group.meetupsHead");
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.add(meetupsHead, java.awt.BorderLayout.WEST);
		head.add(meetHere, java.awt.BorderLayout.EAST);
		head.setBorder(new EmptyBorder(4, 0, 3, 0));
		head.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
		add(MapCompanionPanel.left(head));
		add(meetupCards);
		buildForm();
		add(form);
		add(MapCompanionPanel.left(line(meetSaid)));
		form.setVisible(false);
	}

	/** The form: a title, a day and a time, and the button that makes it. Built once. */
	private void buildForm()
	{
		form.setBorder(new EmptyBorder(6, 8, 6, 8));
		JLabel heading = new JLabel("A meetup where you stand");
		heading.setFont(heading.getFont().deriveFont(11f));
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		form.add(MapCompanionPanel.left(heading));
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
		form.add(MapCompanionPanel.left(line(formSaid)));
	}

	/** Open the form at the next five minutes past half an hour from now, or close it. */
	void openForm(boolean open)
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
			say(formSaid, "Give the meetup a title.");
			return;
		}
		if (start <= System.currentTimeMillis())
		{
			say(formSaid, "Pick a time still to come.");
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
		this.ready = ready;
		long now = System.currentTimeMillis();
		List<RelayResponse.RosterRow> roster = destinations.getRoster();
		List<RelayResponse.Meetup> events = destinations.getEvents();
		String destinationKey = DestinationRoute.keyOf(destinations.getDestination());

		MeetupClient.Said meet = meetups.said("meet");
		if (formOpen && meet != null && !meet.sending && MeetupClient.MADE.equals(meet.message))
		{
			openForm(false);
		}
		if (formOpen && meet != null && !meet.sending && meet.message != null)
		{
			say(formSaid, meet.message);
		}
		boolean showMeet = !formOpen && meet != null && meet.message != null && !meet.sending;
		meetSaid.setVisible(showMeet);
		if (showMeet)
		{
			MapCompanionPanel.setWrapped(meetSaid, meet.message, WIDTH + 16);
		}
		make.setEnabled(ready && meetups.ready() && (meet == null || !meet.sending));
		meetHere.setEnabled(ready);

		String words = signature(roster, events, destinationKey, now);
		if (Objects.equals(words, drawn))
		{
			return;
		}
		drawn = words;
		friends.removeAll();
		if (roster != null)
		{
			for (RelayResponse.RosterRow row : roster)
			{
				friends.add(friend(row, destinationKey, now));
			}
		}
		meetupCards.removeAll();
		if (events != null)
		{
			for (RelayResponse.Meetup meetup : events)
			{
				meetupCards.add(meetup(meetup, destinationKey, now));
			}
		}
		friends.revalidate();
		meetupCards.revalidate();
		revalidate();
		repaint();
	}

	/** Everything a row says, so they are rebuilt only when that changes. */
	private String signature(@Nullable List<RelayResponse.RosterRow> roster, @Nullable List<RelayResponse.Meetup> events,
		@Nullable String destinationKey, long now)
	{
		StringBuilder sb = new StringBuilder().append(ready).append(press.ready()).append(meetups.ready())
			.append(config.showGroupOnWorldMap()).append('|').append(destinationKey).append('|').append(now / 60_000L);
		if (roster != null)
		{
			for (RelayResponse.RosterRow row : roster)
			{
				sb.append(row.id).append(row.name).append(row.colour).append(row.presence).append(row.near)
					.append(row.nearYou).append(row.routable).append(row.news == null ? "" : row.news.title + row.news.atMs)
					.append(group.shown(row.id)).append(group.finding(row.id, now)).append(group.lost(row.id));
				appendSaid(sb, press.said(GroupState.source(row.id)));
			}
		}
		sb.append(';');
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

	/** One friend: their disc and name, where they are, their news, and the two buttons. */
	private JPanel friend(RelayResponse.RosterRow row, @Nullable String destinationKey, long now)
	{
		JPanel card = card();
		card.setName("group.friend." + row.id);
		JLabel name = label(12f, Color.WHITE);
		name.setIcon(new Disc(colour(row.colour), presenceColour(row.presence)));
		name.setIconTextGap(6);
		MapCompanionPanel.setWrapped(name, row.name, WIDTH - 20);
		name.setToolTipText(presenceWord(row.presence));
		card.add(MapCompanionPanel.left(name));
		String where = whereLine(row);
		if (where != null)
		{
			JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			MapCompanionPanel.setWrapped(line, where, WIDTH);
			card.add(MapCompanionPanel.left(line));
		}
		if (row.news != null)
		{
			JLabel news = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			MapCompanionPanel.setWrapped(news, row.news.title + " · " + ago(now - row.news.atMs), WIDTH);
			card.add(MapCompanionPanel.left(news));
		}
		boolean positioned = row.routable || row.near != null;
		if (!positioned)
		{
			return slot(card);
		}
		String source = GroupState.source(row.id);
		RoutePress.Said said = press.said(source);
		boolean planning = said != null && said.planning;
		boolean finding = group.finding(row.id, now);
		boolean routedHere = said != null && said.key != null && said.key.equals(destinationKey);
		JPanel actions = actions();
		if (row.routable)
		{
			JButton route = MapCompanionPanel.smallButton(planning ? "Planning…" : finding ? "Finding…"
				: routedHere ? "Reroute" : "Route");
			route.setName("group.route." + row.id);
			route.setEnabled(ready && !planning && !finding && press.ready());
			route.setToolTipText("Plan the way to where " + row.name + " stands now. Nothing follows them; press again to ask again.");
			route.addActionListener(e -> routeToFriend(row.id));
			actions.add(route);
		}
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
		String message = said != null && said.message != null ? said.message
			: group.lost(row.id) ? row.name + " is not sharing a position just now." : null;
		if (message != null)
		{
			JLabel line = line(new JLabel());
			line.setVisible(true);
			MapCompanionPanel.setWrapped(line, message, WIDTH);
			card.add(MapCompanionPanel.left(line));
		}
		return slot(card);
	}

	/** Route to a friend: now, from their fresh dot, or once the stream that asks for it brings one. */
	void routeToFriend(String id)
	{
		RelayResponse.MemberDot dot = group.routeTo(id, System.currentTimeMillis());
		if (dot != null)
		{
			if (!dot.instance)
			{
				press.press(GroupState.routeTarget(dot, destinations.getRoster()));
			}
		}
		drawn = null;
		refresh(ready);
	}

	/** One meetup: what, when, where, who, then Route and the answers. */
	private JPanel meetup(RelayResponse.Meetup meetup, @Nullable String destinationKey, long now)
	{
		JPanel card = card();
		card.setName("group.meetup." + meetup.id);
		JLabel name = label(12f, Color.WHITE);
		MapCompanionPanel.setWrapped(name, meetup.title, WIDTH);
		card.add(MapCompanionPanel.left(name));
		JLabel when = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		MapCompanionPanel.setWrapped(when, when(meetup.startsAtMs, now, ZoneId.systemDefault(),
			Locale.getDefault(Locale.Category.FORMAT)), WIDTH);
		card.add(MapCompanionPanel.left(when));
		String where = meetupWhere(meetup);
		if (!where.isEmpty())
		{
			JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			MapCompanionPanel.setWrapped(line, where, WIDTH);
			card.add(MapCompanionPanel.left(line));
		}
		if (!meetup.who.isEmpty())
		{
			JLabel who = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			who.setIcon(new Discs(meetup.who));
			who.setIconTextGap(6);
			MapCompanionPanel.setWrapped(who, whoLine(meetup.who), WIDTH - Discs.width(meetup.who.size()) - 6);
			who.setToolTipText(whoTooltip(meetup.who));
			card.add(MapCompanionPanel.left(who));
		}
		String mine = group.mine(meetup, now);
		String source = source(meetup);
		RoutePress.Said said = press.said(source);
		boolean planning = said != null && said.planning;
		JPanel actions = actions();
		JButton route = MapCompanionPanel.smallButton(planning ? "Planning…" : "Route");
		route.setName("group.meetupRoute." + meetup.id);
		route.setEnabled(ready && !planning && press.ready());
		route.setToolTipText("Plan the way to this meetup from where you stand. Nobody is told you are coming.");
		route.addActionListener(e ->
		{
			press.press(RoutePress.Target.tile(source, new WorldPoint(meetup.x, meetup.y, meetup.plane), meetup.title,
				meetup.world, when(meetup.startsAtMs, System.currentTimeMillis(), ZoneId.systemDefault(),
					Locale.getDefault(Locale.Category.FORMAT))));
			drawn = null;
			refresh(ready);
		});
		actions.add(route);
		MeetupClient.Said answer = meetups.said("answer:" + meetup.id);
		boolean sending = answer != null && answer.sending;
		card.add(gap(5));
		card.add(MapCompanionPanel.left(actions));
		card.add(gap(4));
		JPanel answers = new JPanel(new GridLayout(2, 2, 4, 4));
		answers.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (String option : SocialWire.ANSWERS)
		{
			JButton button = MapCompanionPanel.smallButton(answerWord(option));
			button.setName("group.answer." + meetup.id + "." + option);
			boolean chosen = option.equals(mine);
			if (chosen)
			{
				button.setBackground(CHOSEN);
				button.setForeground(Color.WHITE);
			}
			button.setEnabled(ready && !sending && meetups.ready());
			button.addActionListener(e -> answer(meetup.id, option));
			answers.add(button);
		}
		answers.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, answers.getPreferredSize().height));
		card.add(MapCompanionPanel.left(answers));
		String message = said != null && said.message != null ? said.message : answer != null ? answer.message : null;
		if (message != null)
		{
			JLabel line = line(new JLabel());
			line.setVisible(true);
			MapCompanionPanel.setWrapped(line, message, WIDTH);
			card.add(MapCompanionPanel.left(line));
		}
		return slot(card);
	}

	private void answer(String eventId, String answer)
	{
		meetups.answer(eventId, answer);
		drawn = null;
		refresh(ready);
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

	/** "1 here · 2 coming · 1 maybe": the roll-up in counts, the names in the tooltip. */
	static String whoLine(List<RelayResponse.MeetupAnswer> who)
	{
		int here = 0;
		int coming = 0;
		int maybe = 0;
		for (RelayResponse.MeetupAnswer face : who)
		{
			if ("arrived".equals(face.answer))
			{
				here++;
			}
			else if ("going".equals(face.answer) || "on_my_way".equals(face.answer))
			{
				coming++;
			}
			else if ("maybe".equals(face.answer))
			{
				maybe++;
			}
		}
		List<String> parts = new ArrayList<>();
		if (here > 0)
		{
			parts.add(here + " here");
		}
		if (coming > 0)
		{
			parts.add(coming + " coming");
		}
		if (maybe > 0)
		{
			parts.add(maybe + " maybe");
		}
		return parts.isEmpty() ? "Nobody yet" : String.join(" · ", parts);
	}

	static String whoTooltip(List<RelayResponse.MeetupAnswer> who)
	{
		List<String> parts = new ArrayList<>();
		for (RelayResponse.MeetupAnswer face : who)
		{
			parts.add(face.name + (Boolean.TRUE.equals(face.you) ? " (you)" : "") + ": "
				+ ("arrived".equals(face.answer) ? "here" : answerWord(face.answer).toLowerCase(Locale.ENGLISH)));
		}
		return String.join(", ", parts);
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
	 * to the group, and how long until, as the map's own meetup card counts down.
	 */
	static String when(long startsAtMs, long nowMs, ZoneId zone, Locale locale)
	{
		ZonedDateTime at = Instant.ofEpochMilli(startsAtMs).atZone(zone);
		long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), at.toLocalDate());
		String day = days == 0 ? "Today"
			: days == 1 ? "Tomorrow"
			: days > 1 && days < 7 ? at.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
			: at.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
		String clock = at.format(DateTimeFormatter.ofPattern(MapCompanionPanel.clockPattern(locale), Locale.ENGLISH));
		long minutes = Math.round((startsAtMs - nowMs) / 60_000.0);
		String countdown;
		if (minutes <= 0)
		{
			countdown = minutes > -60 ? "now" : "started";
		}
		else if (minutes < 60)
		{
			countdown = "in " + minutes + "m";
		}
		else if (minutes < 24 * 60)
		{
			long rest = minutes % 60;
			countdown = "in " + (minutes / 60) + "h" + (rest == 0 ? "" : " " + rest + "m");
		}
		else
		{
			countdown = "in " + (minutes / (24 * 60)) + "d";
		}
		return day + " " + clock + " · " + countdown;
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

	private void say(JLabel line, String message)
	{
		line.setVisible(true);
		MapCompanionPanel.setWrapped(line, message, WIDTH);
	}

	private static JPanel card()
	{
		JPanel card = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		return card;
	}

	private static JPanel slot(JPanel card)
	{
		JPanel slot = new JPanel(new java.awt.BorderLayout());
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 3, 0));
		slot.add(card);
		slot.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, slot.getPreferredSize().height));
		return MapCompanionPanel.left(slot);
	}

	private static JPanel actions()
	{
		JPanel actions = new JPanel(new GridLayout(1, 0, 4, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return actions;
	}

	private static JPanel fill(Component component)
	{
		JPanel holder = new JPanel(new GridLayout(1, 1));
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.add(component);
		holder.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return MapCompanionPanel.left(holder);
	}

	private static JPanel gap(int height)
	{
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		spacer.setPreferredSize(new java.awt.Dimension(1, height));
		spacer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height));
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

	private static JLabel label(float size, Color colour)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(colour);
		return label;
	}

	/** A friend's disc in their map colour, with presence as a small dot in its corner. */
	static final class Disc implements Icon
	{
		private static final int SIZE = 13;
		private final Color colour;
		private final Color presence;

		Disc(Color colour, Color presence)
		{
			this.colour = colour;
			this.presence = presence;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(colour);
			g2.fillOval(x, y, SIZE, SIZE);
			int dot = 6;
			int dx = x + SIZE - dot + 1;
			int dy = y + SIZE - dot + 1;
			g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g2.fillOval(dx - 1, dy - 1, dot + 2, dot + 2);
			g2.setColor(presence);
			g2.fillOval(dx, dy, dot, dot);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE + 1;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE + 1;
		}
	}

	/** Who is coming, as their discs: solid for coming or here, a ring for maybe, faint for can't. */
	static final class Discs implements Icon
	{
		private static final int SIZE = 10;
		private static final int STEP = 8;
		private static final int MOST = 8;
		private final List<RelayResponse.MeetupAnswer> who;

		Discs(List<RelayResponse.MeetupAnswer> who)
		{
			this.who = who;
		}

		static int width(int count)
		{
			int shown = Math.min(MOST, Math.max(1, count));
			return SIZE + (shown - 1) * STEP;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int i = 0; i < Math.min(MOST, who.size()); i++)
			{
				RelayResponse.MeetupAnswer face = who.get(i);
				Color base = colour(face.colour);
				int left = x + i * STEP;
				g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
				g2.fillOval(left - 1, y - 1, SIZE + 2, SIZE + 2);
				if ("maybe".equals(face.answer))
				{
					g2.setColor(base);
					g2.setStroke(new BasicStroke(2f));
					g2.drawOval(left + 1, y + 1, SIZE - 2, SIZE - 2);
				}
				else
				{
					g2.setColor("no".equals(face.answer)
						? new Color(base.getRed(), base.getGreen(), base.getBlue(), 70) : base);
					g2.fillOval(left, y, SIZE, SIZE);
				}
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return width(who.size());
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
