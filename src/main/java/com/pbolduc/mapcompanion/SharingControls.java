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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
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
import net.runelite.client.ui.ColorScheme;

/**
 * Individual opt-ins with shortcuts for selecting a known group, in RuneLite's narrow sidebar.
 * Customise changes visibility alone. Group checkboxes write only their explicitly listed fields;
 * name, death location and client actions stay separate. Native settings retain their warnings;
 * the sidebar discloses the server once at Connect, before any request. These choices only select
 * which fields may be sent after that approval. Refresh cannot grant approval or change a preference.
 * The fields and their words are {@link SharingFields}, which the step after a first Connect reads too.
 */
final class SharingControls extends JPanel
{
	private static final long serialVersionUID = 1L;
	private final MapCompanionConfig config;
	private final ConfigStore store;
	private final List<Choice> choices = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private final JTextField nickname = new JTextField();
	private String displayedNickname;

	SharingControls(MapCompanionConfig config, ConfigStore store)
	{
		this.config = config;
		this.store = store;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel privacy = column();
		choice(privacy, SharingFields.NAME);
		choice(privacy, SharingFields.DEATH);
		JLabel nicknameLabel = new JLabel("Name to show");
		nicknameLabel.setFont(nicknameLabel.getFont().deriveFont(11f));
		nicknameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		privacy.add(nicknameLabel);
		nickname.setText(config.nickname());
		nickname.setMaximumSize(new Dimension(Integer.MAX_VALUE, nickname.getPreferredSize().height));
		nickname.setAlignmentX(Component.LEFT_ALIGNMENT);
		nickname.setToolTipText("An alias shown instead of your character name. Saves on Enter or when you leave the field.");
		nickname.addActionListener(e -> saveNickname());
		nickname.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent event)
			{
				saveNickname();
			}
		});
		privacy.add(nickname);

		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			JPanel contents = column();
			for (SharingFields.Field field : section.fields) choice(contents, field);
			if (section == SharingFields.LOOT)
			{
				JLabel dependencies = new JLabel("<html><body width='150'>Pet rewards and collection additions need Notable drops and levels.</body></html>");
				dependencies.setFont(dependencies.getFont().deriveFont(11f));
				dependencies.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				dependencies.setAlignmentX(Component.LEFT_ALIGNMENT);
				contents.add(dependencies);
			}
			addGroup(section.id, section.title, contents);
		}
		add(fold("Name and death location", privacy));

		JPanel client = column();
		for (SharingFields.Field field : SharingFields.CLIENT) choice(client, field);
		add(fold("In the client", client));
		refresh();
	}

	private void choice(JPanel parent, SharingFields.Field field)
	{
		String key = field.key;
		String label = field.label;
		BooleanSupplier read = () -> field.read.test(config);
		String disclosure = field.detail;
		JCheckBox box = new JCheckBox("<html><body width='150'>" + label.replace("&", "&amp;") + "</body></html>");
		box.setName(key);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(box.getFont().deriveFont(11f));
		box.setToolTipText(disclosure);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		/* A checkbox chooses data; only Connect may approve a server or start requests. */
		box.addActionListener(e ->
		{
			store.set(key, Boolean.toString(box.isSelected()));
			refresh();
		});
		choices.add(new Choice(key, label, box, read));
		parent.add(box);
	}

	void refresh()
	{
		for (Choice choice : choices)
		{
			choice.box.setSelected(choice.read.getAsBoolean());
		}
		for (Group group : groups) group.refresh();
		if (!java.util.Objects.equals(displayedNickname, config.nickname()))
		{
			displayedNickname = config.nickname();
			nickname.setText(displayedNickname);
		}
	}

	/** A group is a shortcut over today's known fields, never a persisted permission of its own. */
	private void addGroup(String id, String title, JPanel contents)
	{
		List<Choice> members = new ArrayList<>();
		for (Choice choice : choices)
		{
			if (choice.box.getParent() == contents) members.add(choice);
		}
		Group group = new Group(id, title, contents, members);
		groups.add(group);
		add(group.panel);
	}

	private void saveNickname()
	{
		String requested = nickname.getText().trim();
		if (!requested.equals(config.nickname()))
		{
			store.set("nickname", requested);
		}
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
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
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

	private static final class Choice
	{
		final String key;
		final String label;
		final JCheckBox box;
		final BooleanSupplier read;
		Choice(String key, String label, JCheckBox box, BooleanSupplier read)
		{
			this.key = key;
			this.label = label;
			this.box = box;
			this.read = read;
		}
	}

	/** FlatLaf paints the dash; only an explicit press changes the individual stored choices. */
	private final class Group
	{
		final JPanel panel = column();
		final JCheckBox master;
		final JLabel count = new JLabel();
		final List<Choice> members;

		Group(String id, String title, JPanel contents, List<Choice> members)
		{
			this.members = members;
			panel.setName("sharingGroup." + id);
			panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 7, 0));
			master = new JCheckBox(title);
			master.setName("shareGroup." + id);
			master.setBackground(ColorScheme.DARK_GRAY_COLOR);
			master.setFont(master.getFont().deriveFont(Font.BOLD, 12f));
			master.setAlignmentX(Component.LEFT_ALIGNMENT);
			StringBuilder description = new StringBuilder("<html>Select or clear every field in this section:<br>");
			for (Choice member : members) description.append(member.label.replace("&", "&amp;")).append("<br>");
			master.setToolTipText(description.append("</html>").toString());
			master.getAccessibleContext().setAccessibleDescription("Select or clear all " + title + " fields");
			master.addActionListener(e ->
			{
				boolean selected = master.isSelected();
				for (Choice member : members)
				{
					if (member.read.getAsBoolean() != selected) store.set(member.key, Boolean.toString(selected));
				}
				SharingControls.this.refresh();
			});
			panel.add(master);
			JPanel meta = new JPanel(new BorderLayout(7, 0));
			meta.setBackground(ColorScheme.DARK_GRAY_COLOR);
			meta.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
			meta.setAlignmentX(Component.LEFT_ALIGNMENT);
			count.setName("shareCount." + id);
			count.setFont(count.getFont().deriveFont(11f));
			count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			meta.add(count, BorderLayout.WEST);
			JButton customise = new JButton("Customise");
			customise.setName("customise." + id);
			customise.setFont(customise.getFont().deriveFont(11f));
			customise.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			customise.setContentAreaFilled(false);
			customise.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			customise.setHorizontalAlignment(SwingConstants.LEFT);
			meta.add(customise, BorderLayout.CENTER);
			meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, meta.getPreferredSize().height));
			panel.add(meta);
			contents.setBorder(BorderFactory.createEmptyBorder(5, 20, 0, 0));
			contents.setName("sharingOptions." + id);
			contents.setVisible(false);
			customise.addActionListener(e ->
			{
				contents.setVisible(!contents.isVisible());
				customise.setText(contents.isVisible() ? "Done" : "Customise");
				panel.revalidate();
				panel.repaint();
			});
			panel.add(contents);
		}

		void refresh()
		{
			int selected = 0;
			for (Choice member : members) if (member.read.getAsBoolean()) selected++;
			master.setSelected(selected == members.size());
			master.putClientProperty("JButton.selectedState", selected > 0 && selected < members.size() ? "indeterminate" : null);
			count.setText(selected == 0 ? "Off" : selected == members.size()
				? "All " + selected + " selected" : selected + " of " + members.size() + " selected");
		}
	}
}
