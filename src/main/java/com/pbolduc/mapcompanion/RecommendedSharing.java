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

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.border.EmptyBorder;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.MapCompanionPanel.label;
import static com.pbolduc.mapcompanion.MapCompanionPanel.wrapped;
import static com.pbolduc.mapcompanion.MapCompanionPanel.refont;
import static com.pbolduc.mapcompanion.MapCompanionPanel.setWrapped;
import static com.pbolduc.mapcompanion.SharingFields.Field;
import static net.runelite.client.ui.ColorScheme.LIGHT_GRAY_COLOR;

/**
 * The step after a first Connect: what the map uses, ticked, and one press to share it.
 *
 * <p>Every sharing setting still starts off. What this adds is the press: on the first Connect to a
 * map, a new player is shown the three sharing switches Settings has and the group drawn in their
 * game, all ticked, so one press leaves everything the plugin does working. A switch is all of its
 * fields or none, as it is in Settings; every field and the one stream they open are named in
 * <i>What each one sends</i>, one tap below the rows. Pressing <i>Share what's ticked</i> writes those
 * fields exactly as the switches in Settings write them, and the character name with them, which
 * the words over the rows say. Until then nothing is stored: ticking and unticking change this step
 * and nothing else, and <i>Not now</i> leaves every setting as it was.
 *
 * <p><b>Who sees it.</b> Only the first Connect to a map — the one that approves it, asking nothing
 * of osrs-map.com and a question of any other — and only while every choice it offers is still off.
 * A player who connected before this version approved their server already, so an update never
 * shows them the step, and one who chose anything in Settings first keeps their own choices.
 * RuneLite writes every default into the profile when a plugin loads, so "never set" cannot be read
 * back; "still at its default" is the only test that holds on a real client. The step is never
 * remembered: a closed client simply does not offer it again, and Settings holds every choice.
 *
 * <p><b>What it may share.</b> {@link #KEYS} is a list written out rather than read from the
 * groups, so a field added to a group later joins the press only when somebody adds it here, in the
 * same commit as its line in the disclosure documents. Meetup notifications are not offered: a
 * sound or a pop-up is the player's to ask for.
 */
final class RecommendedSharing extends JPanel
{
	/** What one press shares: the three switches' fields, the character name, and the client's drawing. */
	static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
		"sharePosition", "shareActivity", "shareAccountType", "shareDeath",
		"shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash", "shareBoats", "shareSeaCharts",
		"shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts",
		"sharePortTasks", "shareDailies",
		"shareLoot", MapCompanionConfig.KEY_SHARE_MILESTONES, MapCompanionConfig.KEY_SHARE_PETS, MapCompanionConfig.KEY_SHARE_COLLECTION,
		MapCompanionConfig.KEY_SHARE_NAME,
		"showDestination", "showGroupOnWorldMap"));

	/**
	 * Two short sentences over the rows: who sees what is ticked, the one fact a player weighs before
	 * sharing, and where to change it afterwards. The press under them says what one press does.
	 */
	static final String INTRO = Words.of("share.intro");

	/** The connection the drawing choice keeps, in one line under it, to the map it goes to. */
	static String streamShort(String server)
	{
		return "It keeps a connection to " + server + " open while you're logged in.";
	}

	/**
	 * The same connection in full, in the fold under the rows: what it brings and all it carries, the
	 * member key included, in words a player knows rather than "receive stream" and "member id".
	 */
	static String stream(String server)
	{
		return "While you're logged in, it keeps a connection to " + server + Words.of("share.stream");
	}

	static final String DESTINATIONS_DETAIL = Words.of("share.destinationsDetail");
	static final String GROUP_DETAIL = Words.of("share.groupDetail");

	private static final int TEXT = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final int INDENT = 20;
	private static final Color BACK = ColorScheme.DARKER_GRAY_COLOR;

	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final Runnable closed;
	/** Each row's box, in the order drawn, and the fields a tick of it writes. */
	private final Map<JCheckBox, List<Field>> rows = new LinkedHashMap<>();
	private final List<Group> groups = new ArrayList<>();
	private final JLabel where = label(11f, LIGHT_GRAY_COLOR);
	/** The connection under the drawing choices, and in full in the fold under its own heading. */
	private final JLabel opens = indented(label(11f, LIGHT_GRAY_COLOR));
	private final JLabel connection = heading("The connection");
	private final JLabel streamLine = detailLine("recommended.stream", "");

	RecommendedSharing(MapCompanionConfig config, ConfigStore store, Runnable closed)
	{
		this.config = config;
		this.store = store;
		this.closed = closed;
		setName("recommendedSharing");
		/* A card on the sidebar's own grey, with the gap under it that keeps it off the tabs. */
		setLayout(new java.awt.BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 0, 8, 0));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel card = column();
		card.setBorder(new EmptyBorder(7, 8, 8, 8));
		add(card, java.awt.BorderLayout.CENTER);

		JLabel title = wrapped("Choose what to share", 12f, Color.WHITE, TEXT);
		refont(title, Font.BOLD);
		card.add(title);
		card.add(wrapped(INTRO, 11f, LIGHT_GRAY_COLOR, TEXT));
		card.add(gap(6));

		/*
		 * One short row per choice, so the press is in reach without scrolling, and what each row
		 * sends one tap below them in the fold: nothing is hidden before the press.
		 */
		JPanel details = column();
		details.setName("recommended.details");
		/* The name goes with the press, so it is said first in the fold; then the three switches, as Settings gives them. */
		details.add(heading(SharingFields.NAME.label));
		details.add(detailLine("recommended.detail.shareName", SharingFields.NAME.detail));
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			card.add(new Group(toggle, details).panel);
		}
		card.add(gap(4));

		JLabel drawn = label(11f, LIGHT_GRAY_COLOR);
		refont(drawn, Font.BOLD);
		drawn.setText(SharingControls.CLIENT_TITLE);
		card.add(drawn);
		card.add(new Group(SharingFields.IN_GAME, details).panel);
		details.add(detailLine("recommended.detail.showDestination", DESTINATIONS_DETAIL));
		details.add(detailLine("recommended.detail.showGroupOnWorldMap", GROUP_DETAIL));
		/* Their words name the map, so offer() writes them. */
		opens.setName("recommended.opens");
		card.add(opens);
		details.add(connection);
		details.add(streamLine);
		card.add(gap(4));
		card.add(fold(details));
		card.add(gap(4));

		where.setName("recommended.where");
		card.add(where);
		card.add(gap(6));
		JButton share = new JButton("Share what's ticked");
		share.setName("recommended.share");
		refont(share, 12f);
		orange(share, true);
		share.setToolTipText(Words.of("share.shareTip"));
		share.addActionListener(e -> share());
		card.add(MapCompanionPanel.fill(share, BACK));

		JButton skip = MapCompanionPanel.linkButton("Not now");
		skip.setName("recommended.skip");
		skip.setToolTipText(Words.of("share.skipTip"));
		skip.addActionListener(e -> close());
		card.add(MapCompanionPanel.fill(skip, BACK));
		setVisible(false);
	}

	/**
	 * Whether a Connect that just approved its map should offer the step.
	 *
	 * <p>Every choice the step could turn on, and meetup notifications beside them, must still be
	 * off: anything else is a player who has chosen already, and their choice stands.
	 */
	static boolean offered(MapCompanionConfig config, ConfigStore store)
	{
		return all().stream().noneMatch(field -> field.on(config, store)) && !SharingFields.NOTIFY_MEETUPS.on(config, store);
	}

	/**
	 * Connect, and run {@code offer} only when this Connect was the one that approved its map and
	 * {@link #offered} still holds.
	 *
	 * <p>A map is approved once, which is what keeps an update from offering anything to a player who
	 * connected before it: their map is approved, so {@code notice} is never asked. For osrs-map.com
	 * the approval asks nothing, because the install warning was the question; the step is what the
	 * player is shown instead.
	 */
	static boolean connect(Pairing pairing, JoinCode joined, BooleanSupplier notice, MapCompanionConfig config, ConfigStore store,
		Runnable offer)
	{
		boolean[] shown = {false};
		boolean connected = pairing.connect(joined, () ->
		{
			shown[0] = true;
			return notice.getAsBoolean();
		});
		if (connected && shown[0] && offered(config, store))
		{
			offer.run();
		}
		return connected;
	}

	/** Every field the step names, in the order it names them. */
	static List<Field> all()
	{
		List<Field> all = new ArrayList<>();
		all.add(SharingFields.NAME);
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			all.addAll(toggle.fields);
		}
		all.addAll(SharingFields.IN_GAME.fields);
		return all;
	}

	/** Show the step with the recommended set ticked, naming the map the fields will go to. */
	void offer(String server)
	{
		for (Map.Entry<JCheckBox, List<Field>> row : rows.entrySet())
		{
			boolean recommended = true;
			for (Field field : row.getValue())
			{
				recommended &= KEYS.contains(field.key);
			}
			row.getKey().setSelected(recommended);
		}
		/* Who sees it is the intro's first sentence, so the line over the press says only where it goes. */
		setWrapped(where, "Sent to " + server + " while you're logged in.", TEXT);
		setWrapped(opens, streamShort(server), TEXT - INDENT);
		setWrapped(connection, "The connection to " + server, TEXT);
		setWrapped(streamLine, stream(server), TEXT);
		refreshGroups();
		setVisible(true);
		revalidate();
	}

	/**
	 * The page's one filled button, or a plain one. While this step is up its own press is the filled
	 * one, and Open the map waits above it under its own name, drawn plain rather than hidden; Share or
	 * Not now closes the step and gives the orange back. So each moment has one answer to "what do I
	 * press", and a new player shares or chooses before opening a map that would show them sharing
	 * nothing. Plain is the look and feel's own button colours, as resources, so the look and feel
	 * paints it as any other button rather than in the panel's grey.
	 */
	static void orange(JButton button, boolean filled)
	{
		button.setBackground(filled ? ColorScheme.BRAND_ORANGE : UIManager.getColor("Button.background"));
		button.setForeground(filled ? Color.WHITE : UIManager.getColor("Button.foreground"));
	}

	/** Hide the step without a word, as when the group it was offered for is left. */
	void withdraw()
	{
		setVisible(false);
	}

	/** The keys a press would turn on now: the name, and every field of what is ticked, in the step's order. */
	Set<String> ticked()
	{
		Set<String> ticked = new LinkedHashSet<>();
		for (Field field : tickedFields())
		{
			ticked.add(field.key);
		}
		return ticked;
	}

	private List<Field> tickedFields()
	{
		/* The name goes with the press, as the words over the rows say: a dot the group sees is somebody's. */
		List<Field> ticked = new ArrayList<>();
		ticked.add(SharingFields.NAME);
		for (Map.Entry<JCheckBox, List<Field>> row : rows.entrySet())
		{
			if (row.getKey().isSelected())
			{
				ticked.addAll(row.getValue());
			}
		}
		return ticked;
	}

	/** Write the ticked settings that are off, the way a switch in Settings writes its fields, and nothing else. */
	private void share()
	{
		for (Field field : tickedFields())
		{
			if (!field.on(config, store))
			{
				store.set(field.key, "true");
			}
		}
		close();
	}

	private void close()
	{
		setVisible(false);
		closed.run();
	}

	private void refreshGroups()
	{
		for (Group group : groups) group.refresh();
	}

	/** The fold under the rows: what each one sends, and the stream in full. */
	private JPanel fold(JPanel details)
	{
		JPanel panel = column();
		JButton toggle = MapCompanionPanel.linkButton("▸ What each one sends");
		toggle.setName("recommended.detailsToggle");
		details.setBorder(new EmptyBorder(0, 0, 4, 0));
		details.setVisible(false);
		toggle.addActionListener(e ->
		{
			details.setVisible(!details.isVisible());
			toggle.setText((details.isVisible() ? "▾ " : "▸ ") + "What each one sends");
			revalidate();
			repaint();
		});
		panel.add(toggle);
		panel.add(details);
		return panel;
	}

	/** A row's name in the fold, small and bold, over the line that says what it sends. */
	private static JLabel heading(String text)
	{
		JLabel heading = wrapped(text, 11f, Color.WHITE, TEXT);
		refont(heading, Font.BOLD);
		heading.setBorder(new EmptyBorder(4, 0, 0, 0));
		return heading;
	}

	private static JLabel detailLine(String name, String text)
	{
		JLabel line = wrapped(text, 11f, LIGHT_GRAY_COLOR, TEXT);
		line.setName(name);
		return line;
	}

	private static JPanel column()
	{
		return MapCompanionPanel.column(BACK);
	}

	private static <T extends JComponent> T indented(T component)
	{
		component.setBorder(new EmptyBorder(0, INDENT, 0, 0));
		return component;
	}

	private static JPanel gap(int height)
	{
		return MapCompanionPanel.gap(height, BACK);
	}

	/**
	 * One switch: its row, which stands for every field under it as it does in Settings, what it
	 * covers in grey under it, and in the fold the fields a press would share, named in a line. The
	 * line follows the tick, so it always says exactly what the press will send.
	 */
	private final class Group
	{
		final SharingFields.Toggle section;
		final JPanel panel = column();
		final JCheckBox master;
		final JLabel summary = label(11f, LIGHT_GRAY_COLOR);

		Group(SharingFields.Toggle section, JPanel details)
		{
			this.section = section;
			groups.add(this);
			panel.setBorder(new EmptyBorder(1, 0, 1, 0));
			master = new JCheckBox(section.title);
			master.setName("recommended.group." + section.id);
			master.setBackground(BACK);
			refont(master, Font.BOLD, 12f);
			master.setToolTipText(SharingControls.tip(section.fields));
			master.getAccessibleContext().setAccessibleDescription("Tick or untick every " + section.title + " field");
			master.addActionListener(e -> refresh());
			rows.put(master, section.fields);
			panel.add(master);
			panel.add(indented(wrapped(section.note, 11f, LIGHT_GRAY_COLOR, TEXT - INDENT)));
			summary.setName("recommended.summary." + section.id);
			details.add(heading(section.title));
			details.add(summary);
			if (section.fields.containsAll(SharingFields.LOOT.fields))
			{
				/* In full beside the loot fields; the row itself says "kept two weeks". */
				JLabel kept = wrapped(MapCompanionConfig.FEED_KEPT, 11f, LIGHT_GRAY_COLOR, TEXT);
				kept.setBorder(new EmptyBorder(0, 0, 2, 0));
				kept.setName("recommended.kept");
				details.add(kept);
			}
		}

		void refresh()
		{
			setWrapped(summary, master.isSelected() ? SharingControls.labels(section.fields, " · ")
				: "Nothing from this group", TEXT);
		}
	}
}
