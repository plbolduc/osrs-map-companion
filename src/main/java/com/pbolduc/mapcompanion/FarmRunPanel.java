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
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import net.runelite.client.ui.ColorScheme;

/**
 * One ordered farm run: the runs the map can build, and the run being followed ({@link RunView}).
 *
 * <p>Time Tracking remains the patch timer. This adds only the itinerary and the evidence that
 * advances it. A port-task run is followed on the Port tasks card instead; while one is going, this
 * card says a farm run waits for it, since a Start here would replace it.
 *
 * <p>Ready now and a contract are planned from what this client shares, so with that choice off the
 * map could only refuse. The card checks first, as the Port tasks card checks Boats and moorings:
 * Start run waits, a line under it names the choice to turn on, and nothing is sent.
 */
final class FarmRunPanel extends JPanel
{
	static final String AFTER_PORT_RUN = Words.of("farm.afterPortRun");
	/* Settings names its groups, not their fields: patches and contracts both share under Tasks and timers. */
	static final String NEEDS_PATCHES = Words.of("farm.needsPatches");
	static final String NEEDS_CONTRACT = Words.of("farm.needsContract");
	static final String PICKED = "Run: ";
	private static final String[][] STARTS =
	{
		{"ready-now", "Ready now"}, {"preset:herb", "Herb run"}, {"preset:tree", "Tree run"},
		{"preset:fruit", "Fruit tree run"}, {"preset:hardwood", "Hardwood run"},
		{"preset:trees", "Tree + fruit tree"}, {"preset:herb-allotment", "Herb + allotment + flower"},
		{"birdhouses", "Birdhouses"}, {"contract", "Farming contract"}
	};

	private final FarmRunClient client;
	private final ConfigStore settings;
	private final JComboBox<String> kind = new JComboBox<>();
	/* A button under the picker, the picker's width: it acts, and a link is a fold's own opener. */
	private final JButton start = MapCompanionPanel.smallButton("Start run");
	/* Under Start run while the kind chosen plans from a sharing choice that is off. */
	private final JLabel needs = MapCompanionPanel.label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	/** Connected and free to press, as of the last refresh: a new pick of kind is offered by it. EDT. */
	private boolean ready;
	/*
	 * The kind of run and Start run, hidden while a run is going or being planned: there is one run at
	 * a time, so a press here would replace the run being walked. The Port tasks card holds the same
	 * rule; the picker comes back when the run ends.
	 */
	private final JPanel picker = new JPanel(new BorderLayout(0, 4));
	/* Where the picker was while the Port tasks card's run is going, so its absence is explained. */
	private final JLabel waiting = MapCompanionPanel.wrapped(AFTER_PORT_RUN, 11f, ColorScheme.LIGHT_GRAY_COLOR,
		MapCompanionPanel.CARD_TEXT_WIDTH);
	private final RunView run;

	@Inject
	FarmRunPanel(FarmRunState state, FarmRunClient client, FarmRunRoute follower, DestinationRoute routes,
				 ShortestPath shortest, GameSprites sprites, MapCompanionConfig config, ConfigStore settings)
	{
		this.client = client;
		this.settings = settings;
		this.run = new RunView(false, state, client, follower, routes, shortest, sprites, config);
		MapCompanionPanel.taskCard(this, "farmRun", "Farm run", sprites, "Farming");
		picker.setBackground(getBackground());
		for (String[] choice : STARTS)
		{
			kind.addItem(choice[1]);
		}
		kind.setFont(kind.getFont().deriveFont(11f));
		kind.setToolTipText(Words.of("farm.kindTip"));
		/* Closed, the picker names what it picks: "Ready now" alone reads as a status. The list stays plain. */
		ListCellRenderer<? super String> rows = kind.getRenderer();
		kind.setRenderer((list, value, index, selected, focused) ->
			rows.getListCellRendererComponent(list, index < 0 && value != null ? PICKED + value : value, index, selected, focused));
		picker.add(kind, BorderLayout.CENTER);
		picker.add(start, BorderLayout.SOUTH);
		picker.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		add(MapCompanionPanel.left(picker));
		needs.setVisible(false);
		add(needs);
		waiting.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		add(waiting);
		add(run);

		start.addActionListener(e ->
		{
			String[] chosen = STARTS[kind.getSelectedIndex()];
			if (needs(chosen[0], settings) == null)
			{
				client.start(chosen[0], chosen[1]);
			}
		});
		/* At once, not at the next refresh: a pick that needs a choice off must not leave Start pressable. */
		kind.addActionListener(e ->
		{
			offer();
			revalidate();
			repaint();
		});
	}

	/**
	 * The line a kind of run says while the sharing choice it is planned from is off, or null when it
	 * needs none or that choice is on. Ready now reads the patches this client has shared and a contract
	 * the contract it shared; the presets and Birdhouses are planned from the map's own tables.
	 */
	@Nullable
	static String needs(String kind, ConfigStore settings)
	{
		if ("ready-now".equals(kind) && !settings.on("shareFarming"))
		{
			return NEEDS_PATCHES;
		}
		if ("contract".equals(kind) && !settings.on("shareFarmingContracts"))
		{
			return NEEDS_CONTRACT;
		}
		return null;
	}

	/** Start run, and the line under it, for the kind chosen now. EDT. */
	private void offer()
	{
		String missing = needs(STARTS[kind.getSelectedIndex()][0], settings);
		start.setEnabled(ready && missing == null);
		needs.setVisible(picker.isVisible() && missing != null);
		MapCompanionPanel.setWrapped(needs, missing == null ? "" : missing, MapCompanionPanel.CARD_TEXT_WIDTH);
	}

	/** The button a press from the game presses on this card's run, or null with none shown here. EDT. */
	@Nullable
	JButton actButton(MapCompanionPanel.Act act)
	{
		return isVisible() ? run.actButton(act) : null;
	}

	/** Called on the EDT by the sidebar refresh; character state has already been read on a game tick. */
	void refresh(boolean connected)
	{
		refresh(connected, true);
	}

	/** The same, knowing whether a character is logged in: with none, no run is offered to start. */
	void refresh(boolean connected, boolean loggedIn)
	{
		boolean showing = run.refresh(connected, loggedIn, System.currentTimeMillis());
		setVisible(connected || showing);
		picker.setVisible(!run.busy() && !run.otherBusy());
		waiting.setVisible(!run.busy() && run.otherBusy());
		ready = connected && loggedIn && client.ready();
		kind.setEnabled(ready);
		offer();
		revalidate();
		repaint();
	}
}
