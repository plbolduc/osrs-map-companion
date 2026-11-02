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
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sharing as six switches, in RuneLite's narrow sidebar: Character name with the name shown instead
 * under it, a switch for each group in {@link SharingFields}, and Where you died; then what each one
 * sends, folded, and what this client draws.
 *
 * <p>A group's switch writes every field in it, on or off, and nothing else: the fields are what is
 * sent, and the switch RuneLite's list shows for the group follows them in the plugin
 * ({@link SharingGroups}). A group shows on only while every field in it is. One a player chose part
 * of before sharing went by group shows the dash and names what is on under it, rather than hiding
 * it, and a press turns all of it on, as a press of any switch that is off does.
 *
 * <p>The warning is the install's, and a map other than osrs-map.com is approved by name at Connect,
 * before any request; these choices only select which fields may be sent after that approval.
 * Refresh cannot grant approval or change a preference. The fields and their words are
 * {@link SharingFields}, which the step after a first Connect reads too. The name box shows only
 * while the character name is off, because only then is it what the group sees (see
 * {@code SnapshotBuilder.nickname}).
 */
final class SharingControls extends JPanel
{
	/** Where a checkbox's words start, from its left edge: a line under one starts under its words. */
	static final int TEXT_INSET = 28;
	/** The column's width for a wrapped line, as the rest of the sidebar takes it. */
	private static final int TEXT = PluginPanel.PANEL_WIDTH - 30;
	/** What this client draws and does, named as RuneLite's list and the step after Connect name it. */
	static final String CLIENT_TITLE = "In RuneLite";
	static final String KEYS_ELSEWHERE = "Keys for Reroute and Follow, and the reminder's minutes, are in RuneLite's own "
		+ "settings for this plugin.";
	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final List<Choice> choices = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private final JTextField nickname = new JTextField();
	private final JLabel nicknameLabel = new JLabel("Name to show instead");
	private final JLabel nicknameHint = MapCompanionPanel.label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private String displayedNickname;

	SharingControls(MapCompanionConfig config, ConfigStore store)
	{
		this.config = config;
		this.store = store;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		/* The six switches, in the order the step after Connect and RuneLite's list give them. */
		add(row(choice(SharingFields.NAME, true)));
		/* Under the switch's words, as a partly shared group's line is: it is what the switch leaves the group. */
		JPanel nameBox = column();
		nameBox.setName("sharing.nameBox");
		nameBox.setBorder(BorderFactory.createEmptyBorder(0, TEXT_INSET, 0, 0));
		add(nameBox);
		nicknameLabel.setFont(nicknameLabel.getFont().deriveFont(11f));
		nicknameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameBox.add(nicknameLabel);
		nickname.setName("nickname");
		MapCompanionPanel.capped(nickname);
		nickname.setAlignmentX(Component.LEFT_ALIGNMENT);
		nickname.setToolTipText("What your group sees beside your dot. Saves on Enter or when you leave the field.");
		nickname.addActionListener(e -> saveNickname());
		nickname.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent event)
			{
				saveNickname();
			}
		});
		nameBox.add(nickname);
		nicknameHint.setText("Leave it empty to show as Member.");
		nameBox.add(nicknameHint);
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			Group group = new Group(section);
			groups.add(group);
			add(group.panel);
		}
		add(row(choice(SharingFields.DEATH, true)));
		add(fold("What each one sends", whatEachOneSends()));

		JPanel client = column();
		for (SharingFields.Field field : SharingFields.CLIENT)
		{
			client.add(choice(field, false));
		}
		/* The settings a box cannot hold live only in RuneLite's list, and the README sends players to them. */
		JLabel elsewhere = line(KEYS_ELSEWHERE, ColorScheme.LIGHT_GRAY_COLOR, 2);
		elsewhere.setName("sharing.keysElsewhere");
		client.add(elsewhere);
		add(fold(CLIENT_TITLE, client));
		refresh();
	}

	/** A choice of its own: a switch drawn as the groups' are, or one of this client's in a smaller hand. */
	private JCheckBox choice(SharingFields.Field field, boolean sharing)
	{
		String key = field.key;
		BooleanSupplier read = () -> field.read.test(config);
		JCheckBox box = new JCheckBox(sharing ? field.label : "<html><body width='150'>" + html(field.label) + "</body></html>");
		box.setName(key);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(sharing ? box.getFont().deriveFont(Font.BOLD, 12f) : box.getFont().deriveFont(11f));
		box.setToolTipText(field.detail);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		/* A checkbox chooses data; only Connect may approve a server or start requests. */
		box.addActionListener(e ->
		{
			if (field.notification)
			{
				store.chooseNotification(key, box.isSelected());
			}
			else
			{
				store.set(key, Boolean.toString(box.isSelected()));
			}
			refresh();
		});
		choices.add(new Choice(box, read));
		return box;
	}

	void refresh()
	{
		for (Choice choice : choices)
		{
			choice.box.setSelected(choice.read.getAsBoolean());
		}
		for (Group group : groups)
		{
			group.refresh();
		}
		String alias = SnapshotBuilder.alias(config);
		if (!alias.equals(displayedNickname))
		{
			displayedNickname = alias;
			nickname.setText(alias);
		}
		boolean ownName = !config.shareName();
		nicknameLabel.setVisible(ownName);
		nickname.setVisible(ownName);
		nicknameHint.setVisible(ownName);
	}

	private void saveNickname()
	{
		String requested = nickname.getText().trim();
		if (!requested.equals(SnapshotBuilder.alias(config)))
		{
			store.set("nickname", requested);
		}
	}

	/**
	 * Each switch's name over what it sends, every field named in the sidebar's words, so the page
	 * says all of what a switch shares without a box per field.
	 */
	private static JPanel whatEachOneSends()
	{
		JPanel details = column();
		details.setName("sharing.details");
		details.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		sends(details, SharingFields.NAME.label, SharingFields.NAME.detail);
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			sends(details, section.title, labels(section.fields, " · "));
			if (section == SharingFields.LOOT)
			{
				details.add(line(MapCompanionConfig.FEED_KEPT, ColorScheme.LIGHT_GRAY_COLOR, 0));
			}
		}
		sends(details, SharingFields.DEATH.label, SharingFields.DEATH.detail);
		return details;
	}

	private static void sends(JPanel details, String name, String what)
	{
		JLabel heading = line(name, Color.WHITE, 4);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		details.add(heading);
		details.add(line(what, ColorScheme.LIGHT_GRAY_COLOR, 0));
	}

	private static JLabel line(String text, Color colour, int above)
	{
		JLabel line = MapCompanionPanel.wrapped(text, 11f, colour, TEXT);
		line.setBorder(BorderFactory.createEmptyBorder(above, 0, 0, 0));
		return line;
	}

	static String labels(List<SharingFields.Field> fields, String between)
	{
		List<String> labels = new ArrayList<>();
		for (SharingFields.Field field : fields)
		{
			labels.add(field.label);
		}
		return String.join(between, labels);
	}

	private static String html(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** One switch's row, with the room the groups' rows have. */
	private static JPanel row(JCheckBox box)
	{
		JPanel row = column();
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		row.add(box);
		return row;
	}

	static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** RuneLite-style disclosure arrows; a header controls visibility alone. */
	static JPanel fold(String title, JPanel contents)
	{
		JPanel panel = column();
		JButton header = new JButton("▸ " + title);
		header.setFont(header.getFont().deriveFont(12f));
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		header.setContentAreaFilled(false);
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.setMargin(new Insets(0, 0, 0, 0));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		MapCompanionPanel.capped(header);
		contents.setVisible(false);
		header.addActionListener(e ->
		{
			contents.setVisible(!contents.isVisible());
			header.setText((contents.isVisible() ? "▾ " : "▸ ") + title);
			panel.revalidate();
			panel.repaint();
		});
		panel.add(header);
		panel.add(contents);
		return panel;
	}

	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class Choice
	{
		final JCheckBox box;
		final BooleanSupplier read;
	}

	/**
	 * A group's switch, and under it, while only part of the group is on, which fields are. FlatLaf
	 * paints the dash; the box itself is off, so a press turns the whole group on.
	 */
	private final class Group
	{
		final SharingFields.Section section;
		final JPanel panel = column();
		final JCheckBox box;
		final JLabel partly = MapCompanionPanel.label(11f, ColorScheme.LIGHT_GRAY_COLOR);

		Group(SharingFields.Section section)
		{
			this.section = section;
			panel.setName("sharingGroup." + section.id);
			panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
			/* The loot row's reminder wraps under its title in the grey of a note, as the step's does. */
			box = new JCheckBox(section != SharingFields.LOOT ? section.title
				: "<html><body width='150'>" + section.title + " <font color='#a5a5a5'>· kept two weeks</font></body></html>");
			box.setName(section.key);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setFont(box.getFont().deriveFont(Font.BOLD, 12f));
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			StringBuilder tip = new StringBuilder("<html><body width='220'>On shares all of these, and off stops them:");
			for (SharingFields.Field field : section.fields)
			{
				tip.append("<br><b>").append(html(field.label)).append("</b>: ").append(html(field.detail));
			}
			box.setToolTipText(tip.append("</body></html>").toString());
			box.getAccessibleContext().setAccessibleDescription("Share or stop sharing every " + section.title + " field");
			box.addActionListener(e ->
			{
				SharingGroups.choose(section, box.isSelected(), config, store);
				SharingControls.this.refresh();
			});
			panel.add(box);
			partly.setName("sharingPartly." + section.id);
			partly.setBorder(BorderFactory.createEmptyBorder(0, TEXT_INSET, 2, 0));
			panel.add(partly);
		}

		void refresh()
		{
			List<SharingFields.Field> on = SharingGroups.shared(section, config);
			boolean whole = on.size() == section.fields.size();
			boolean part = !on.isEmpty() && !whole;
			box.setSelected(whole);
			box.putClientProperty("JButton.selectedState", part ? "indeterminate" : null);
			partly.setVisible(part);
			if (part)
			{
				MapCompanionPanel.setWrapped(partly, "Partly shared: " + labels(on, ", "), TEXT - TEXT_INSET);
			}
		}
	}
}
