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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.border.EmptyBorder;
import javax.swing.*;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import static com.pbolduc.mapcompanion.MapCompanionPanel.refont;
import static com.pbolduc.mapcompanion.SharingFields.Field;
import static net.runelite.client.ui.ColorScheme.LIGHT_GRAY_COLOR;

/**
 * Sharing as three switches, in RuneLite's narrow sidebar: each of {@link SharingFields#TOGGLES}
 * with what it covers under it, the name the group sees, what each one sends, folded, and what this
 * client draws.
 *
 * <p>A switch writes every field under it, on or off, and nothing else: the fields are what is
 * sent, and the switches RuneLite's list shows follow them in the plugin ({@link SharingGroups}).
 * A switch shows on only while every field under it is. One a player chose part of, in RuneLite's
 * list or before these switches, shows the dash and names what is on under it, rather than hiding
 * it, and a press turns all of it on, as a press of any switch that is off does.
 *
 * <p>The character name has no switch. It is on from the step after Connect, and a player who
 * would rather not be named asks for another here; one who hid it before keeps it hidden.
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
	static final String CLIENT_TITLE = "In your game";
	static final String KEYS_ELSEWHERE = Words.of("settings.keysElsewhere");
	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final List<Choice> choices = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private final JTextField nickname = new JTextField();
	private final JLabel nicknameLabel = new JLabel();
	/** Turns the character name off, for the box under it, or back on. */
	private final JButton nameLink = MapCompanionPanel.linkButton("");
	private final JLabel nicknameHint = MapCompanionPanel.label(11f, LIGHT_GRAY_COLOR);
	private String displayedNickname;

	/** Whether Shortest Path is installed: its box is drawn only then. */
	private final BooleanSupplier paths;
	private JCheckBox pathsBox;

	SharingControls(MapCompanionConfig config, ConfigStore store)
	{
		this(config, store, () -> true);
	}

	SharingControls(MapCompanionConfig config, ConfigStore store, BooleanSupplier paths)
	{
		this.config = config;
		this.store = store;
		this.paths = paths;
		MapCompanionPanel.column(this, ColorScheme.DARK_GRAY_COLOR);

		/* The three switches, as the step after Connect gives them. */
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			Group group = new Group(toggle);
			groups.add(group);
			add(group.panel);
		}
		/* Whose dot it is: the character's own name, or the one typed here once the player asks for another. */
		JPanel nameBox = column();
		nameBox.setName("sharing.nameBox");
		nameBox.setBorder(new EmptyBorder(4, TEXT_INSET, 2, 0));
		add(nameBox);
		refont(nicknameLabel, 11f);
		nameBox.add(nicknameLabel);
		nickname.setName("nickname");
		MapCompanionPanel.capped(nickname);
		MapCompanionPanel.left(nickname);
		nickname.setToolTipText(Words.of("settings.nicknameTip"));
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
		nameLink.setName("sharing.nameLink");
		nameLink.addActionListener(e ->
		{
			store.set(SharingFields.NAME.key, Boolean.toString(!config.shareName()));
			refresh();
		});
		nameBox.add(nameLink);
		add(fold("What each one sends", whatEachOneSends()));

		JPanel client = column();
		for (List<Field> row : SharingFields.CLIENT_ROWS)
		{
			String[] said = SharingFields.row(row);
			if (said != null && !said[1].isEmpty())
			{
				JLabel head = line(said[1], LIGHT_GRAY_COLOR, 4);
				refont(head, Font.BOLD);
				client.add(head);
			}
			JCheckBox box = choice(row, said == null ? MapCompanionPanel.escape(row.get(0).label) : said[2]);
			client.add(box);
			if (row.contains(SharingFields.SHORTEST_PATH))
			{
				pathsBox = box;
			}
		}
		/* The settings a box cannot hold live only in RuneLite's list, and the README sends players to them. */
		JLabel elsewhere = line(KEYS_ELSEWHERE, LIGHT_GRAY_COLOR, 2);
		elsewhere.setName("sharing.keysElsewhere");
		client.add(elsewhere);
		/*
		 * The two halves say what makes them different: the boxes above leave this computer, and these
		 * only change the player's own game. The line stays under the heading while the fold is closed.
		 */
		JPanel lower = fold(CLIENT_TITLE, client);
		lower.add(line(Words.of("client.sub"), LIGHT_GRAY_COLOR, 0), 1);
		lower.setBorder(new javax.swing.border.CompoundBorder(new EmptyBorder(6, 0, 0, 0),
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR)));
		add(lower);
		refresh();
	}

	/**
	 * A box over one of this client's settings or a row of them, named after the first, whose words
	 * are markup of this plugin's own. A press writes every setting under it; partly on, it is off
	 * with the dash a partly shared switch has, so a press turns the row on.
	 */
	private JCheckBox choice(List<Field> row, String words)
	{
		JCheckBox box = new JCheckBox("<html><body width='150'>" + words + "</body></html>");
		box.setName(row.get(0).key);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		refont(box, 11f);
		box.setToolTipText(row.get(0).detail);
		/* A checkbox chooses data; only Connect may approve a server or start requests. */
		box.addActionListener(e ->
		{
			for (Field field : row)
			{
				if (field.notification)
				{
					store.chooseNotification(field.key, box.isSelected());
				}
				else
				{
					store.set(field.key, Boolean.toString(box.isSelected()));
				}
			}
			refresh();
		});
		choices.add(new Choice(box, () ->
		{
			int on = 0;
			for (Field field : row)
			{
				on += field.on(config, store) ? 1 : 0;
			}
			box.putClientProperty("JButton.selectedState", on > 0 && on < row.size() ? "indeterminate" : null);
			return on == row.size();
		}));
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
		pathsBox.setVisible(paths.getAsBoolean());
		String alias = SnapshotBuilder.alias(config);
		if (!alias.equals(displayedNickname))
		{
			displayedNickname = alias;
			nickname.setText(alias);
		}
		boolean ownName = !config.shareName();
		nicknameLabel.setText(ownName ? "Name to show instead" : "Shown as your character name");
		nameLink.setText(ownName ? "Use my character name" : "Use another name");
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
		details.setBorder(new EmptyBorder(0, 0, 4, 0));
		sends(details, SharingFields.NAME.label, SharingFields.NAME.detail);
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			sends(details, toggle.title, labels(toggle.fields, " · "));
		}
		details.add(line(MapCompanionConfig.FEED_KEPT, LIGHT_GRAY_COLOR, 0));
		return details;
	}

	private static void sends(JPanel details, String name, String what)
	{
		JLabel heading = line(name, Color.WHITE, 4);
		refont(heading, Font.BOLD);
		details.add(heading);
		details.add(line(what, LIGHT_GRAY_COLOR, 0));
	}

	private static JLabel line(String text, Color colour, int above)
	{
		JLabel line = MapCompanionPanel.wrapped(text, 11f, colour, TEXT);
		line.setBorder(new EmptyBorder(above, 0, 0, 0));
		return line;
	}

	/**
	 * A partly shared switch in the fewer words: what is off after {@code allBut}, or what is on
	 * after {@code only}. A player who shared everything but where they died reads that one name.
	 */
	static String partly(List<Field> fields, List<Field> on, String allBut, String only)
	{
		List<Field> off = new ArrayList<>(fields);
		off.removeAll(on);
		return off.size() < on.size() ? allBut + labels(off, ", ") : only + labels(on, ", ");
	}

	static String labels(List<Field> fields, String between)
	{
		List<String> labels = new ArrayList<>();
		for (Field field : fields)
		{
			labels.add(field.label);
		}
		return String.join(between, labels);
	}

	/**
	 * A switch's tooltip: every field under it and what that field sends, nine to a column so the
	 * eighteen under one switch still fit a small client's window.
	 */
	static String tip(List<Field> fields)
	{
		StringBuilder tip = new StringBuilder("<html>On shares all of these, and off stops them:<table><tr>");
		for (int i = 0; i < fields.size(); i++)
		{
			tip.append(i % 9 == 0 ? "<td width='220' valign='top'>" : "<br>").append("<b>")
				.append(MapCompanionPanel.escape(fields.get(i).label)).append("</b>: ")
				.append(MapCompanionPanel.escape(fields.get(i).detail));
		}
		return tip.append("</table></html>").toString();
	}

	static JPanel column()
	{
		return MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	}

	/** RuneLite-style disclosure arrows; a header controls visibility alone. */
	static JPanel fold(String title, JPanel contents)
	{
		JPanel panel = column();
		JButton header = new JButton("▸ " + title);
		refont(header, 12f);
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setBorder(new EmptyBorder(5, 0, 5, 0));
		header.setContentAreaFilled(false);
		header.setForeground(LIGHT_GRAY_COLOR);
		header.setMargin(new Insets(0, 0, 0, 0));
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

	@AllArgsConstructor
	private static final class Choice
	{
		final JCheckBox box;
		final BooleanSupplier read;
	}

	/**
	 * One of the three switches, and under it what it covers or, while only part of it is on, which
	 * fields are. FlatLaf paints the dash; the box itself is off, so a press turns all of it on.
	 */
	private final class Group
	{
		final SharingFields.Toggle section;
		final JPanel panel = column();
		final JCheckBox box;
		final JLabel partly = MapCompanionPanel.label(11f, LIGHT_GRAY_COLOR);

		Group(SharingFields.Toggle section)
		{
			this.section = section;
			panel.setName("sharingGroup." + section.id);
			panel.setBorder(new EmptyBorder(2, 0, 2, 0));
			box = new JCheckBox(section.title);
			box.setName("share." + section.id);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			refont(box, Font.BOLD, 12f);
			box.setToolTipText(tip(section.fields));
			box.getAccessibleContext().setAccessibleDescription("Share or stop sharing every " + section.title + " field");
			box.addActionListener(e ->
			{
				SharingGroups.choose(section.fields, box.isSelected(), config, store);
				SharingControls.this.refresh();
			});
			panel.add(box);
			partly.setName("sharingPartly." + section.id);
			partly.setBorder(new EmptyBorder(0, TEXT_INSET, 2, 0));
			panel.add(partly);
		}

		void refresh()
		{
			List<Field> on = SharingGroups.shared(section.fields, config, store);
			boolean whole = on.size() == section.fields.size();
			boolean part = !on.isEmpty() && !whole;
			box.setSelected(whole);
			box.putClientProperty("JButton.selectedState", part ? "indeterminate" : null);
			MapCompanionPanel.showKeeping(partly, part ? partly(section.fields, on, "Not shared: ", "Partly shared: ") : section.note, TEXT - TEXT_INSET);
		}
	}
}
