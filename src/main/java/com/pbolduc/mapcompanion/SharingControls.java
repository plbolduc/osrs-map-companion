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

		JPanel presence = column();
		choice(presence, "sharePosition", "Position and world", config::sharePosition,
			"Your tile, world and instance type. Shows your dot where its location is known.");
		choice(presence, "shareActivity", "Training and idle time", config::shareActivity,
			"The skill last trained, recent experience and time since moving.");
		choice(presence, "shareAccountType", "Account type", config::shareAccountType,
			"Your ironman or other account badge.");
		addGroup("presence", "Map presence", presence);

		JPanel privacy = column();
		choice(privacy, "shareName", "Character name", config::shareName,
			"The display name of the character you are logged in as.");
		choice(privacy, "shareDeath", "Last death location", config::shareDeath,
			"The tile of your most recent death, forgotten after an hour.");
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

		JPanel progress = column();
		choice(progress, "shareSkills", "Skill levels", config::shareSkills, "Your levels, for usable routes.");
		choice(progress, "shareQuests", "Travel quest completion", config::shareQuests, "Progress in the quests that unlock travel; no individual steps.");
		choice(progress, "shareQuestLog", "Full quest progress", config::shareQuestLog, "Started or finished states for the full quest and miniquest log; no individual steps.");
		choice(progress, "shareDiaries", "Achievement diaries", config::shareDiaries, "Completed tiers and collected rewards; no individual tasks.");
		choice(progress, "sharePermanentTravel", "Permanent travel and house", config::sharePermanentTravel, "Verified permanent travel unlocks and the town your house is in.");
		choice(progress, "shareCapabilities", "Carried travel items", config::shareCapabilities, "Derived yes/no travel flags; your inventory is never sent.");
		choice(progress, "shareStash", "STASH construction", config::shareStash, "Built STASH units; stored equipment is never read.");
		addGroup("progress", "Progress and travel", progress);

		JPanel tasks = column();
		choice(tasks, "shareSlayer", "Slayer task", config::shareSlayer, "Your current assignment and remaining count.");
		choice(tasks, "shareClue", "Clue step", config::shareClue, "Your current clue scroll step.");
		choice(tasks, "shareFarming", "Farming patches", config::shareFarming, "Patch states you have observed, for planning runs.");
		choice(tasks, "shareBirdhouses", "Birdhouses", config::shareBirdhouses, "States observed by RuneLite Time Tracking, with their age.");
		choice(tasks, "shareHunterRumours", "Hunter rumours", config::shareHunterRumours, "The rumour observed after checking a quetzal whistle; recheck after login or hopping.");
		choice(tasks, "shareMahoganyHomes", "Mahogany Homes", config::shareMahoganyHomes, "The customer and tier observed in dialogue or game messages.");
		choice(tasks, "shareFarmingContracts", "Farming contracts", config::shareFarmingContracts, "The crop requested by your current Farming Guild contract.");
		addGroup("tasks", "Tasks and timers", tasks);

		JPanel loot = column();
		choice(loot, "shareLoot", "Observed kills and drops", config::shareLoot, "Locally observed tracked monster kills and rated drop totals on normal members worlds; not lifetime kill counts.");
		choice(loot, "shareMilestones", "Notable drops and levels", config::shareMilestones, "Notable observed rewards and real levels. Activity rewards require Loot Tracker.");
		choice(loot, "sharePets", "Include pet rewards", config::sharePets, "Observed pet rewards while Notable drops and levels is enabled; unknown pets stay unnamed.");
		choice(loot, "shareCollectionLog", "Include collection additions", config::shareCollectionLog, "New collection notifications while Notable drops and levels is enabled; existing slots are never read.");
		JLabel dependencies = new JLabel("<html><body width='150'>Pet rewards and collection additions need Notable drops and levels.</body></html>");
		dependencies.setFont(dependencies.getFont().deriveFont(11f));
		dependencies.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dependencies.setAlignmentX(Component.LEFT_ALIGNMENT);
		loot.add(dependencies);
		addGroup("loot", "Loot and milestones", loot);
		add(fold("Name and death location", privacy));

		JPanel client = column();
		choice(client, "showDestination", "Show map destinations", config::showDestination, "Receive destinations and meetup details. Nothing is clicked or walked for you.");
		choice(client, "showGroupOnWorldMap", "Group on the world map", config::showGroupOnWorldMap, "Receive the positions your group already shares and draw them on the world map.");
		choice(client, "destinationHintArrow", "Hint arrow to a destination", config::destinationHintArrow, "Point the game's own arrow at a destination once it is nearby. Nothing leaves this computer.");
		choice(client, "notifyMeetups", "Meetup notifications", config::notifyMeetups, "Receive a notification before a meetup starts.");
		choice(client, "dropLuckChat", "Drop luck in chat", config::dropLuckChat, "One chat line when a drop with a known rate lands. Nothing leaves this computer.");
		add(fold("In the client", client));
		refresh();
	}

	private void choice(JPanel parent, String key, String label, BooleanSupplier read, String disclosure)
	{
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
