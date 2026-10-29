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
import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar as two pages: the daily one — status, Open the map and the tabs — and Settings,
 * which takes its place whole while it is open and gives it back with the arrow at its top.
 *
 * <p>A page and not a fold, because what is in it — every sharing choice, the map address, Show
 * what's sent, Leave group — is long, and set once. Under the tabs it pushed the day's facts a
 * screen down; as a page it costs the daily view one gear. Both pages stay alive while hidden, so a
 * Customise left open, or a half-typed address, is still there when the reader comes back.
 */
final class SidebarPages extends JPanel
{
	private static final long serialVersionUID = 1L;

	private final JPanel daily;
	private final JPanel settings;

	SidebarPages(JPanel daily, JPanel settingsContent)
	{
		this.daily = daily;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		MapCompanionPanel.left(this);
		settings = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
		settings.setName("companionSettings");
		JButton back = new JButton("Settings", SidebarIcons.back());
		back.setName("settings.back");
		back.setToolTipText("Back");
		back.setFont(back.getFont().deriveFont(14f));
		back.setForeground(Color.WHITE);
		back.setIconTextGap(6);
		back.setHorizontalAlignment(SwingConstants.LEFT);
		back.setBorderPainted(false);
		back.setContentAreaFilled(false);
		back.setFocusPainted(false);
		back.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, back.getPreferredSize().height));
		back.addActionListener(e -> open(false));
		settings.add(MapCompanionPanel.left(back));
		settings.add(settingsContent);
		add(daily);
		add(settings);
		open(false);
	}

	/** Settings in place of the daily page, or the daily page back. */
	void open(boolean open)
	{
		daily.setVisible(!open);
		settings.setVisible(open);
		revalidate();
		repaint();
	}

	boolean settingsOpen()
	{
		return settings.isVisible();
	}
}
