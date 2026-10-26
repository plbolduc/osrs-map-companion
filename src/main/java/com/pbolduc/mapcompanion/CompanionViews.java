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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;

/**
 * Daily facts and sharing settings use the same narrow column at different times.
 * Only the visible view contributes height: a CardLayout or ordinary tabbed pane would reserve
 * the tallest settings page's space even on a quiet Overview. Both views stay alive, so switching
 * changes neither a sharing choice nor an open Customise section.
 */
final class CompanionViews extends JPanel
{
	private static final long serialVersionUID = 1L;
	private final JPanel overview;
	private final JPanel sharing;
	private final JPanel stars;
	private final java.util.function.Consumer<Boolean> starsVisibility;
	private final JToggleButton overviewTab = tab("Overview", "overview");
	private final JToggleButton sharingTab = tab("Sharing", "sharing");
	private final JToggleButton starsTab = tab("Stars", "stars");

	CompanionViews(JPanel overview, JPanel sharing)
	{
		this(overview, sharing, null, on ->
		{
		});
	}

	CompanionViews(JPanel overview, JPanel sharing, JPanel stars, java.util.function.Consumer<Boolean> starsVisibility)
	{
		this.overview = overview;
		this.sharing = sharing;
		this.stars = stars;
		this.starsVisibility = starsVisibility;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel tabs = new JPanel(new GridLayout(1, stars == null ? 2 : 3, 3, 0));
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		ButtonGroup selection = new ButtonGroup();
		selection.add(overviewTab);
		selection.add(sharingTab);
		tabs.add(overviewTab);
		tabs.add(sharingTab);
		if (stars != null)
		{
			selection.add(starsTab);
			tabs.add(starsTab);
		}
		add(tabs, BorderLayout.NORTH);
		JPanel content = new JPanel();
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(overview);
		content.add(sharing);
		if (stars != null) content.add(stars);
		add(content, BorderLayout.CENTER);
		overviewTab.addActionListener(e -> selectView(false));
		sharingTab.addActionListener(e -> selectView(true));
		starsTab.addActionListener(e -> {
			overview.setVisible(false);
			sharing.setVisible(false);
			stars.setVisible(true);
			overviewTab.setSelected(false);
			sharingTab.setSelected(false);
			starsTab.setSelected(true);
			starsVisibility.accept(true);
			revalidate();
			repaint();
		});
		selectView(false);
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

	private void selectView(boolean showSharing)
	{
		overview.setVisible(!showSharing);
		sharing.setVisible(showSharing);
		overviewTab.setSelected(!showSharing);
		sharingTab.setSelected(showSharing);
		if (stars != null)
		{
			stars.setVisible(false);
			starsTab.setSelected(false);
			starsVisibility.accept(false);
		}
		revalidate();
		repaint();
	}
}
