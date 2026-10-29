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
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar's three tabs, in three words: Home, what you are doing now; Group, your friends and
 * meetups, with how many there are in its name; Stars.
 *
 * <p>Settings is not a tab. It is set once, at the press after Connect, and then rarely, so it sits
 * behind the gear on the status card ({@link SidebarPages}), as RuneLite's own plugins keep their
 * configuration out of their panels. A tab of its own spent a third of the strip on it every day.
 *
 * <p>Only the visible view contributes height: a CardLayout or an ordinary tabbed pane would
 * reserve the tallest view's space even on a quiet Home. Every view stays alive, so switching loses
 * nothing typed or opened. Before a group is joined the strip is not drawn and Home is the page:
 * Group and Stars both ask the map as a member, so before then they could only say "connect".
 */
final class CompanionViews extends JPanel
{
	private static final long serialVersionUID = 1L;

	enum View
	{
		HOME,
		GROUP,
		STARS
	}

	private final JPanel home;
	private final JPanel group;
	private final JPanel stars;
	private final Consumer<Boolean> starsVisibility;
	private final JPanel tabs = new JPanel(new GridLayout(1, 3, 3, 0));
	private final JToggleButton homeTab = tab("Home", "home");
	private final JToggleButton groupTab = tab("Group", "group");
	private final JToggleButton starsTab = tab("Stars", "stars");
	private View selected = View.HOME;

	CompanionViews(JPanel home, JPanel group, JPanel stars, Consumer<Boolean> starsVisibility)
	{
		this.home = home;
		this.group = group;
		this.stars = stars;
		this.starsVisibility = starsVisibility;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		ButtonGroup selection = new ButtonGroup();
		for (JToggleButton tab : new JToggleButton[]{homeTab, groupTab, starsTab})
		{
			selection.add(tab);
			tabs.add(tab);
		}
		add(tabs, BorderLayout.NORTH);
		JPanel content = new JPanel();
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(home);
		content.add(group);
		content.add(stars);
		add(content, BorderLayout.CENTER);
		homeTab.addActionListener(e -> select(View.HOME));
		groupTab.addActionListener(e -> select(View.GROUP));
		starsTab.addActionListener(e -> select(View.STARS));
		select(View.HOME);
	}

	private static JToggleButton tab(String text, String id)
	{
		JToggleButton button = new JToggleButton(text);
		button.setName("companionView." + id);
		button.setFont(button.getFont().deriveFont(12f));
		button.setMargin(new Insets(4, 4, 4, 4));
		button.putClientProperty("JButton.buttonType", "tab");
		return button;
	}

	void select(View view)
	{
		selected = view;
		home.setVisible(view == View.HOME);
		group.setVisible(view == View.GROUP);
		stars.setVisible(view == View.STARS);
		homeTab.setSelected(view == View.HOME);
		groupTab.setSelected(view == View.GROUP);
		starsTab.setSelected(view == View.STARS);
		starsVisibility.accept(view == View.STARS);
		revalidate();
		repaint();
	}

	View selected()
	{
		return selected;
	}

	/**
	 * Draw the strip, or leave Home as the whole page. Taking the strip away goes back to Home, so
	 * leaving a group from Stars does not leave the reader on a tab they can no longer reach.
	 */
	void tabsShown(boolean shown)
	{
		if (shown == tabs.isVisible())
		{
			return;
		}
		tabs.setVisible(shown);
		if (!shown)
		{
			select(View.HOME);
		}
		revalidate();
	}

	/** "Group · 3", the friends the map lists; "Group" while it lists none. */
	void groupCount(int friends)
	{
		String text = friends > 0 ? "Group · " + friends : "Group";
		if (!text.equals(groupTab.getText()))
		{
			groupTab.setText(text);
		}
	}
}
