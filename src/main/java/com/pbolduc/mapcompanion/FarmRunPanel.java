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

import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * One ordered farm run: the runs the map can build, and the run being followed ({@link RunView}).
 *
 * <p>Time Tracking remains the patch timer. This adds only the itinerary and the evidence that
 * advances it. A port-task run is followed on the Port tasks card instead; while one is held, this
 * card shows only its picker.
 */
final class FarmRunPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final String[][] STARTS =
	{
		{"ready-now", "Ready now"}, {"preset:herb", "Herb run"}, {"preset:tree", "Tree run"},
		{"preset:fruit", "Fruit tree run"}, {"preset:hardwood", "Hardwood run"},
		{"preset:trees", "Tree + fruit tree"}, {"preset:herb-allotment", "Herb + allotment + flower"},
		{"birdhouses", "Birdhouses"}, {"contract", "Farming contract"}
	};

	private final transient FarmRunClient client;
	private final JComboBox<String> kind = new JComboBox<>();
	/* A button under the picker, the picker's width: it acts, and a link is a fold's own opener. */
	private final JButton start = MapCompanionPanel.smallButton("Start run");
	private final RunView run;

	@Inject
	FarmRunPanel(FarmRunState state, FarmRunClient client, FarmRunRoute follower, DestinationRoute routes,
				 ShortestPath shortest, GameSprites sprites, MapCompanionConfig config)
	{
		this.client = client;
		this.run = new RunView(false, state, client, follower, routes, shortest, sprites, config);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		MapCompanionPanel.left(this);
		setName("farmRun");

		JLabel heading = new JLabel("Farm run");
		heading.setForeground(Color.WHITE);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
		add(MapCompanionPanel.left(heading));
		JPanel picker = new JPanel(new BorderLayout(0, 4));
		picker.setBackground(getBackground());
		for (String[] choice : STARTS)
		{
			kind.addItem(choice[1]);
		}
		kind.setFont(kind.getFont().deriveFont(11f));
		kind.setToolTipText("Choose a run the map can build from your character's shared readings");
		picker.add(kind, BorderLayout.CENTER);
		picker.add(start, BorderLayout.SOUTH);
		picker.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		add(MapCompanionPanel.left(picker));
		add(run);

		start.addActionListener(e -> client.start(STARTS[kind.getSelectedIndex()][0]));
	}

	/** Called on the EDT by the sidebar refresh; character state has already been read on a game tick. */
	void refresh(boolean connected)
	{
		boolean showing = run.refresh(connected);
		setVisible(connected || showing);
		boolean ready = connected && client.ready();
		start.setEnabled(ready);
		kind.setEnabled(ready);
		revalidate();
		repaint();
	}
}
