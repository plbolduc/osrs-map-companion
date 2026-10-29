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
import net.runelite.client.ui.ColorScheme;

/**
 * The step after a first Connect: what the map uses, ticked, and one press to share it.
 *
 * <p>Every sharing setting still starts off, and RuneLite's warning stays on each one. What this
 * adds is the press: after the server notice, a new player is shown the four sharing groups, their
 * character name and the two things this client draws from the map, all ticked, one short row each
 * so the press is in reach without scrolling. Every field and the one stream they open are named in
 * <i>What each one sends</i>, one tap below the rows, and pressing <i>Share what's ticked</i> writes
 * those settings exactly as the group shortcuts in Settings write them. Until then nothing is
 * stored: ticking, unticking and <i>Customise</i> change this step and nothing else, and <i>Not
 * now</i> leaves every setting as it was.
 *
 * <p><b>Who sees it.</b> Only a Connect that showed the server notice, which is the first Connect
 * to that map, and only while every choice it offers is still off. A player who connected before
 * this version approved their server already, so an update never shows them the step, and one who
 * chose anything in Settings first keeps their own choices. RuneLite writes every default
 * into the profile when a plugin loads, so "never set" cannot be read back; "still at its default"
 * is the only test that holds on a real client. The step is never remembered: a closed client
 * simply does not offer it again, and Settings holds every choice.
 *
 * <p><b>What it may share.</b> {@link #KEYS} is a list written out rather than read from the
 * groups, so a field added to a group later joins the press only when somebody adds it here, in the
 * same commit as its line in the disclosure documents. <i>Where you died</i> is offered and left
 * unticked, because Route back sends the tile in its own request when it is asked for. Meetup
 * notifications are not offered: a sound or a pop-up is the player's to ask for.
 */
final class RecommendedSharing extends JPanel
{
	private static final long serialVersionUID = 1L;

	/** What one press shares: the four groups' fields, the character name, and the client's drawing. */
	static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
		"sharePosition", "shareActivity", "shareAccountType",
		"shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash",
		"shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts",
		"shareLoot", MapCompanionConfig.KEY_SHARE_MILESTONES, MapCompanionConfig.KEY_SHARE_PETS, MapCompanionConfig.KEY_SHARE_COLLECTION,
		MapCompanionConfig.KEY_SHARE_NAME,
		"showDestination", "showGroupOnWorldMap"));

	/**
	 * Two short sentences over the rows. The press under them says what one press does, so the
	 * intro no longer says it too; what it keeps is where to change it afterwards.
	 */
	static final String INTRO = "Ticked is what the map uses. Change any of it later in Settings.";

	/** The stream the two drawing choices open, in one line under them. */
	static final String STREAM_SHORT = "Opens one connection while you're logged in.";

	/** The same stream in full, in the fold under the rows. */
	static final String STREAM = "Either one opens the map's receive stream: one connection, held open while you "
		+ "are logged in, that brings destinations, meetups and your group's positions. It carries your group "
		+ "code and member id, and nothing about your character.";

	static final String DEATH_DETAIL = "Your last death's tile, kept for an hour. Route back works without it.";
	static final String DESTINATIONS_DETAIL = "Places your group sends, and meetup cards, on the world map. "
		+ "Nothing is clicked or walked for you.";
	static final String GROUP_DETAIL = "Your group's dots on the game's world map, from what they already share.";

	private static final int TEXT = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final int INDENT = 20;
	private static final Color BACK = ColorScheme.DARKER_GRAY_COLOR;
	/** The loot row's own reminder, short enough to sit on the row; the fold says it in full. */
	static final String LOOT_ROW = SharingFields.LOOT.title + " · kept two weeks";

	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final Runnable closed;
	private final Map<String, SharingFields.Field> fields = new LinkedHashMap<>();
	private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
	private final List<Group> groups = new ArrayList<>();
	private final JLabel where = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private final JButton customise = MapCompanionPanel.linkButton("Customise");
	private boolean customising = false;

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

		JLabel title = label(12f, Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		MapCompanionPanel.setWrapped(title, "Choose what to share", TEXT);
		card.add(title);
		JLabel intro = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		MapCompanionPanel.setWrapped(intro, INTRO, TEXT);
		card.add(intro);
		card.add(gap(6));

		/*
		 * One short row per choice, so the press is in reach without scrolling, and what each row
		 * sends one tap below them in the fold: nothing is hidden before the press.
		 */
		JPanel details = column();
		details.setName("recommended.details");
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			Group group = new Group(section, details);
			groups.add(group);
			card.add(group.panel);
		}
		card.add(single(SharingFields.NAME, SharingFields.NAME.detail, details));
		card.add(single(SharingFields.DEATH, DEATH_DETAIL, details));
		card.add(gap(4));

		JLabel drawn = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		drawn.setFont(drawn.getFont().deriveFont(Font.BOLD));
		drawn.setText("Drawn in this client");
		card.add(drawn);
		card.add(single(SharingFields.DESTINATIONS, DESTINATIONS_DETAIL, details));
		card.add(single(SharingFields.GROUP_ON_WORLD_MAP, GROUP_DETAIL, details));
		JLabel opens = indented(label(11f, ColorScheme.LIGHT_GRAY_COLOR));
		opens.setName("recommended.opens");
		MapCompanionPanel.setWrapped(opens, STREAM_SHORT, TEXT - INDENT);
		card.add(opens);
		JLabel stream = detailLine("recommended.stream", STREAM);
		details.add(heading("The connection"));
		details.add(stream);
		card.add(gap(4));
		card.add(fold(details));
		card.add(gap(4));

		where.setName("recommended.where");
		card.add(where);
		card.add(gap(6));
		JButton share = new JButton("Share what's ticked");
		share.setName("recommended.share");
		share.setFont(share.getFont().deriveFont(12f));
		share.setToolTipText("Turn on the ticked settings, as the group shortcuts in Settings do. Nothing else changes.");
		share.addActionListener(e -> share());
		JPanel shareRow = new JPanel(new GridLayout(1, 1));
		shareRow.setBackground(BACK);
		shareRow.add(share);
		shareRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, share.getPreferredSize().height));
		shareRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(shareRow);

		JPanel links = new JPanel(new GridLayout(1, 2, 4, 0));
		links.setBackground(BACK);
		links.setAlignmentX(Component.LEFT_ALIGNMENT);
		customise.setName("recommended.customise");
		customise.setToolTipText("Show each field to tick or untick. Nothing is saved until you press Share.");
		customise.addActionListener(e -> customise(!customising));
		links.add(customise);
		JButton skip = MapCompanionPanel.linkButton("Not now");
		skip.setName("recommended.skip");
		skip.setToolTipText("Share nothing yet. Every choice stays off until you turn it on in Settings.");
		skip.addActionListener(e -> close());
		links.add(skip);
		links.setMaximumSize(new Dimension(Integer.MAX_VALUE, links.getPreferredSize().height));
		card.add(links);
		setVisible(false);
	}

	/**
	 * Whether a Connect that just showed the server notice should offer the step.
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
	 * Connect, and run {@code offer} only when this Connect was the one that showed the server
	 * notice and {@link #offered} still holds.
	 *
	 * <p>The notice runs once per map, which is what keeps an update from offering anything to a
	 * player who connected before it: their map is approved, so {@code notice} is never asked.
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
		for (SharingFields.Section section : SharingFields.GROUPS) all.addAll(section.fields);
		all.add(SharingFields.NAME);
		all.add(SharingFields.DEATH);
		all.add(SharingFields.DESTINATIONS);
		all.add(SharingFields.GROUP_ON_WORLD_MAP);
		return all;
	}

	/** Show the step with the recommended set ticked, naming the map the fields will go to. */
	void offer(String server)
	{
		for (Map.Entry<String, JCheckBox> box : boxes.entrySet())
		{
			box.getValue().setSelected(KEYS.contains(box.getKey()));
		}
		customise(false);
		MapCompanionPanel.setWrapped(where, "Sent to " + server + " while you're logged in; your group sees it.", TEXT);
		refreshGroups();
		setVisible(true);
		revalidate();
	}

	/** Hide the step without a word, as when the group it was offered for is left. */
	void withdraw()
	{
		setVisible(false);
	}

	/** The keys a press would turn on now: what is ticked, in the step's order. */
	Set<String> ticked()
	{
		Set<String> ticked = new LinkedHashSet<>();
		for (Map.Entry<String, JCheckBox> box : boxes.entrySet())
		{
			if (box.getValue().isSelected()) ticked.add(box.getKey());
		}
		return ticked;
	}

	/** Write the ticked settings that are off, the way a group shortcut writes its fields, and nothing else. */
	private void share()
	{
		for (String key : ticked())
		{
			if (!fields.get(key).read.test(config))
			{
				store.set(key, "true");
			}
		}
		close();
	}

	private void close()
	{
		setVisible(false);
		closed.run();
	}

	private void customise(boolean open)
	{
		customising = open;
		customise.setText(open ? "Fewer details" : "Customise");
		for (Group group : groups) group.customise(open);
		revalidate();
		repaint();
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
		JLabel heading = label(11f, Color.WHITE);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		heading.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		MapCompanionPanel.setWrapped(heading, text, TEXT);
		return heading;
	}

	private static JLabel detailLine(String name, String text)
	{
		JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		line.setName(name);
		MapCompanionPanel.setWrapped(line, text, TEXT);
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
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.addActionListener(e -> refreshGroups());
		fields.put(field.key, field);
		boxes.put(field.key, box);
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

	private static JLabel label(float size, Color colour)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
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
		spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
		return spacer;
	}

	/**
	 * One sharing group: its row, a checkbox each under Customise, and in the fold the fields a
	 * press would share, named in a line. The line names only what is ticked, so it always says
	 * exactly what the press will send.
	 */
	private final class Group
	{
		final SharingFields.Section section;
		final JPanel panel = column();
		final JCheckBox master;
		final JLabel summary = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
		final JPanel options = column();
		final List<JCheckBox> members = new ArrayList<>();

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
			master.setAlignmentX(Component.LEFT_ALIGNMENT);
			master.getAccessibleContext().setAccessibleDescription("Tick or untick every " + section.title + " field");
			master.addActionListener(e ->
			{
				for (JCheckBox member : members) member.setSelected(master.isSelected());
				refresh();
			});
			panel.add(master);
			summary.setName("recommended.summary." + section.id);
			details.add(heading(section.title));
			details.add(summary);
			options.setBorder(BorderFactory.createEmptyBorder(0, INDENT, 0, 0));
			for (SharingFields.Field field : section.fields)
			{
				JCheckBox box = box(field, 11f);
				members.add(box);
				options.add(box);
				if (field.key.equals(MapCompanionConfig.KEY_SHARE_MILESTONES))
				{
					/* Inside the indented options, lined up with the checkbox labels. */
					options.add(keptLine("recommended.keptOpen", 24, TEXT - INDENT - 24));
				}
			}
			options.setVisible(false);
			panel.add(options);
			if (section == SharingFields.LOOT)
			{
				/* In full beside the loot fields; the row itself says "kept two weeks". */
				details.add(keptLine("recommended.kept", 0, TEXT));
			}
		}

		void customise(boolean open)
		{
			options.setVisible(open);
		}

		JLabel keptLine(String name, int left, int width)
		{
			JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			line.setBorder(BorderFactory.createEmptyBorder(0, left, 2, 0));
			line.setName(name);
			MapCompanionPanel.setWrapped(line, MapCompanionConfig.FEED_KEPT, width);
			return line;
		}

		void refresh()
		{
			List<String> on = new ArrayList<>();
			for (int i = 0; i < members.size(); i++)
			{
				if (members.get(i).isSelected()) on.add(section.fields.get(i).label);
			}
			master.setSelected(on.size() == members.size());
			master.putClientProperty("JButton.selectedState", !on.isEmpty() && on.size() < members.size() ? "indeterminate" : null);
			MapCompanionPanel.setWrapped(summary, on.isEmpty() ? "Nothing from this group" : String.join(" · ", on), TEXT);
		}
	}
}
