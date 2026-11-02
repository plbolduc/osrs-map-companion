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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.MapCompanionPanel.label;
import static com.pbolduc.mapcompanion.MapCompanionPanel.wrapped;

/**
 * The step after a first Connect: what the map uses, ticked, and one press to share it.
 *
 * <p>Every sharing setting still starts off. What this adds is the press: on the first Connect to a
 * map, a new player is shown the six sharing switches Settings has — their character name, the four
 * groups and where they died — and the two things this client draws from the map, all but where they
 * died ticked, one short row each so the press is in reach without scrolling. A group is all of its
 * fields or none, as its switch in Settings is; every field and the one stream they open are named in
 * <i>What each one sends</i>, one tap below the rows. Pressing <i>Share what's ticked</i> writes those
 * fields exactly as the switches in Settings write them. Until then nothing is stored: ticking and
 * unticking change this step and nothing else, and <i>Not now</i> leaves every setting as it was.
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
 * same commit as its line in the disclosure documents. <i>Where you died</i> is offered and left
 * unticked, because Route back sends the tile in its own request when it is asked for. Meetup
 * notifications are not offered: a sound or a pop-up is the player's to ask for.
 */
final class RecommendedSharing extends JPanel
{
	/** What one press shares: the four groups' fields, the character name, and the client's drawing. */
	static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
		"sharePosition", "shareActivity", "shareAccountType",
		"shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash", "shareBoats",
		"shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts",
		"sharePortTasks",
		"shareLoot", MapCompanionConfig.KEY_SHARE_MILESTONES, MapCompanionConfig.KEY_SHARE_PETS, MapCompanionConfig.KEY_SHARE_COLLECTION,
		MapCompanionConfig.KEY_SHARE_NAME,
		"showDestination", "showGroupOnWorldMap"));

	/**
	 * Two short sentences over the rows: who sees what is ticked, the one fact a player weighs before
	 * sharing, and where to change it afterwards. The press under them says what one press does.
	 */
	static final String INTRO = "Your group sees what's ticked. Change it any time in Settings.";

	/** The connection the two drawing choices keep, in one line under them, to the map it goes to. */
	static String streamShort(String server)
	{
		return "Either keeps a connection to " + server + " open while you're logged in.";
	}

	/**
	 * The same connection in full, in the fold under the rows: what it brings and all it carries, the
	 * member key included, in words a player knows rather than "receive stream" and "member id".
	 */
	static String stream(String server)
	{
		return "While you're logged in, either one keeps a connection to " + server + " open. It brings the places "
			+ "your group sends, meetups and your friends' positions, and carries your group's code, your member id "
			+ "and its key, and nothing about your character.";
	}

	static final String DEATH_DETAIL = "Your last death's tile, kept for an hour. Route back works without it.";
	static final String DESTINATIONS_DETAIL = "Places your group sends you, and your group's meetups, in the sidebar "
		+ "and on the world map. Nothing is clicked or walked for you.";
	static final String GROUP_DETAIL = "Your group's dots on the game's world map, from what they already share.";

	private static final int TEXT = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final int INDENT = 20;
	private static final Color BACK = ColorScheme.DARKER_GRAY_COLOR;
	/** The loot row's own reminder, short enough to sit on the row; the fold says it in full. */
	static final String LOOT_ROW = SharingFields.LOOT.title + " · kept two weeks";

	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final Runnable closed;
	/** Each row's box, in the order drawn, and the fields a tick of it writes: one, or a group's all. */
	private final Map<JCheckBox, List<SharingFields.Field>> rows = new LinkedHashMap<>();
	private final List<Group> groups = new ArrayList<>();
	private final JLabel where = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	/** The connection under the drawing choices, and in full in the fold under its own heading. */
	private final JLabel opens = indented(label(11f, ColorScheme.LIGHT_GRAY_COLOR));
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
		setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel card = column();
		card.setBorder(BorderFactory.createEmptyBorder(7, 8, 8, 8));
		add(card, java.awt.BorderLayout.CENTER);

		JLabel title = wrapped("Choose what to share", 12f, Color.WHITE, TEXT);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		card.add(title);
		card.add(wrapped(INTRO, 11f, ColorScheme.LIGHT_GRAY_COLOR, TEXT));
		card.add(gap(6));

		/*
		 * One short row per choice, so the press is in reach without scrolling, and what each row
		 * sends one tap below them in the fold: nothing is hidden before the press.
		 */
		JPanel details = column();
		details.setName("recommended.details");
		/* The six switches, in the order Settings and RuneLite's list give them. */
		card.add(single(SharingFields.NAME, SharingFields.NAME.detail, details));
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			Group group = new Group(section, details);
			groups.add(group);
			card.add(group.panel);
		}
		card.add(single(SharingFields.DEATH, DEATH_DETAIL, details));
		card.add(gap(4));

		JLabel drawn = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		drawn.setFont(drawn.getFont().deriveFont(Font.BOLD));
		drawn.setText(SharingControls.CLIENT_TITLE);
		card.add(drawn);
		card.add(single(SharingFields.DESTINATIONS, DESTINATIONS_DETAIL, details));
		card.add(single(SharingFields.GROUP_ON_WORLD_MAP, GROUP_DETAIL, details));
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
		share.setFont(share.getFont().deriveFont(12f));
		orange(share, true);
		share.setToolTipText("Turn on the ticked settings, as the switches in Settings do. Nothing else changes.");
		share.addActionListener(e -> share());
		card.add(MapCompanionPanel.fill(share, BACK));

		JPanel links = new JPanel(new GridLayout(1, 2, 4, 0));
		links.setBackground(BACK);
		links.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton skip = MapCompanionPanel.linkButton("Not now");
		skip.setName("recommended.skip");
		skip.setToolTipText("Share nothing yet. Every choice stays off until you turn it on in Settings.");
		skip.addActionListener(e -> close());
		links.add(skip);
		MapCompanionPanel.capped(links);
		card.add(links);
		setVisible(false);
	}

	/**
	 * Whether a Connect that just approved its map should offer the step.
	 *
	 * <p>Every choice the step could turn on, and meetup notifications beside them, must still be
	 * off: anything else is a player who has chosen already, and their choice stands.
	 */
	static boolean offered(MapCompanionConfig config)
	{
		List<SharingFields.Field> asked = new ArrayList<>(all());
		asked.add(SharingFields.NOTIFY_MEETUPS);
		for (SharingFields.Field field : asked)
		{
			if (field.read.test(config))
			{
				return false;
			}
		}
		return true;
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
	static boolean connect(Pairing pairing, JoinCode joined, BooleanSupplier notice, MapCompanionConfig config, Runnable offer)
	{
		boolean[] shown = {false};
		boolean connected = pairing.connect(joined, () ->
		{
			shown[0] = true;
			return notice.getAsBoolean();
		});
		if (connected && shown[0] && offered(config))
		{
			offer.run();
		}
		return connected;
	}

	/** Every field the step names, in the order it draws them. */
	static List<SharingFields.Field> all()
	{
		List<SharingFields.Field> all = new ArrayList<>();
		all.add(SharingFields.NAME);
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			all.addAll(section.fields);
		}
		all.add(SharingFields.DEATH);
		all.add(SharingFields.DESTINATIONS);
		all.add(SharingFields.GROUP_ON_WORLD_MAP);
		return all;
	}

	/** Show the step with the recommended set ticked, naming the map the fields will go to. */
	void offer(String server)
	{
		for (Map.Entry<JCheckBox, List<SharingFields.Field>> row : rows.entrySet())
		{
			boolean recommended = true;
			for (SharingFields.Field field : row.getValue())
			{
				recommended &= KEYS.contains(field.key);
			}
			row.getKey().setSelected(recommended);
		}
		/* Who sees it is the intro's first sentence, so the line over the press says only where it goes. */
		MapCompanionPanel.setWrapped(where, "Sent to " + server + " while you're logged in.", TEXT);
		MapCompanionPanel.setWrapped(opens, streamShort(server), TEXT - INDENT);
		MapCompanionPanel.setWrapped(connection, "The connection to " + server, TEXT);
		MapCompanionPanel.setWrapped(streamLine, stream(server), TEXT);
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

	/** The keys a press would turn on now: what is ticked, a group's every field, in the step's order. */
	Set<String> ticked()
	{
		Set<String> ticked = new LinkedHashSet<>();
		for (SharingFields.Field field : tickedFields())
		{
			ticked.add(field.key);
		}
		return ticked;
	}

	private List<SharingFields.Field> tickedFields()
	{
		List<SharingFields.Field> ticked = new ArrayList<>();
		for (Map.Entry<JCheckBox, List<SharingFields.Field>> row : rows.entrySet())
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
		for (SharingFields.Field field : tickedFields())
		{
			if (!field.read.test(config))
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

	/** A choice of its own: a row in the group's weight, and what it sends in the fold. */
	private JPanel single(SharingFields.Field field, String detail, JPanel details)
	{
		JPanel panel = column();
		panel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		JCheckBox box = box(field, 12f);
		box.setFont(box.getFont().deriveFont(Font.BOLD));
		panel.add(box);
		details.add(heading(field.label));
		details.add(detailLine("recommended.detail." + field.key, detail));
		return panel;
	}

	/** The fold under the rows: what each one sends, and the stream in full. */
	private JPanel fold(JPanel details)
	{
		JPanel panel = column();
		JButton toggle = MapCompanionPanel.linkButton("▸ What each one sends");
		toggle.setName("recommended.detailsToggle");
		details.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
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
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		heading.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		return heading;
	}

	private static JLabel detailLine(String name, String text)
	{
		JLabel line = wrapped(text, 11f, ColorScheme.LIGHT_GRAY_COLOR, TEXT);
		line.setName(name);
		return line;
	}

	private JCheckBox box(SharingFields.Field field, float size)
	{
		JCheckBox box = new JCheckBox("<html><body width='" + (TEXT - INDENT - 10) + "'>"
			+ field.label.replace("&", "&amp;") + "</body></html>");
		box.setName("recommended." + field.key);
		box.setBackground(BACK);
		box.setFont(box.getFont().deriveFont(size));
		box.setToolTipText(field.detail);
		rows.put(box, Collections.singletonList(field));
		return box;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(BACK);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static <T extends JComponent> T indented(T component)
	{
		component.setBorder(BorderFactory.createEmptyBorder(0, INDENT, 0, 0));
		return component;
	}

	private static JPanel gap(int height)
	{
		JPanel spacer = new JPanel();
		spacer.setBackground(BACK);
		spacer.setPreferredSize(new Dimension(1, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		/* A plain panel's FlowLayout asks for 10px at least, and a squeezed column grows a gap to that. */
		spacer.setMinimumSize(new Dimension(0, height));
		spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
		return spacer;
	}

	/**
	 * One sharing group: its row, which stands for every field in it as its switch in Settings does,
	 * and in the fold the fields a press would share, named in a line. The line follows the tick, so
	 * it always says exactly what the press will send.
	 */
	private final class Group
	{
		final SharingFields.Section section;
		final JPanel panel = column();
		final JCheckBox master;
		final JLabel summary = label(11f, ColorScheme.LIGHT_GRAY_COLOR);

		Group(SharingFields.Section section, JPanel details)
		{
			this.section = section;
			panel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			/* The loot row's reminder wraps under its title in the grey of a note, rather than being cut. */
			master = new JCheckBox(section != SharingFields.LOOT ? section.title
				: "<html><body width='" + (TEXT - 30) + "'>" + SharingFields.LOOT.title
				+ " <font color='#a5a5a5'>· kept two weeks</font></body></html>");
			master.setName("recommended.group." + section.id);
			master.setBackground(BACK);
			master.setFont(master.getFont().deriveFont(Font.BOLD, 12f));
			master.getAccessibleContext().setAccessibleDescription("Tick or untick every " + section.title + " field");
			master.addActionListener(e -> refresh());
			rows.put(master, section.fields);
			panel.add(master);
			summary.setName("recommended.summary." + section.id);
			details.add(heading(section.title));
			details.add(summary);
			if (section == SharingFields.LOOT)
			{
				/* In full beside the loot fields; the row itself says "kept two weeks". */
				JLabel kept = wrapped(MapCompanionConfig.FEED_KEPT, 11f, ColorScheme.LIGHT_GRAY_COLOR, TEXT);
				kept.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
				kept.setName("recommended.kept");
				details.add(kept);
			}
		}

		void refresh()
		{
			MapCompanionPanel.setWrapped(summary, master.isSelected() ? SharingControls.labels(section.fields, " · ")
				: "Nothing from this group", TEXT);
		}
	}
}
