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
import java.awt.Font;
import java.util.List;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The way to a destination, step by step, inside the destination's card.
 *
 * <p>The website's own words, numbered as the website numbers them, so a player reading the card
 * on the map and this list in the game reads one route. The current step is marked and drawn
 * bright; the steps behind it go grey; the ones ahead stay as they are. The way being followed is
 * named above its steps, and the others the map offered fold behind one line, as the card's closed
 * row folds them — a press on one shows its steps instead.
 *
 * <p>Every string here was typed by somebody in the group or built from what they sent, so each
 * goes through {@link MapCompanionPanel#setWrapped}, which escapes before Swing reads markup.
 * Rebuilt only when what it says changes: the sidebar refreshes every second, and a rebuild under
 * the pointer would eat a click.
 */
final class RouteStepsView extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	/** Where a step's detail starts: under its words, past its number. */
	private static final int INDENT = 14;
	private static final Color DONE = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color AHEAD = ColorScheme.LIGHT_GRAY_COLOR;

	private final transient IntConsumer choose;
	private final JLabel way = label(11f, AHEAD);
	private final JLabel origin = label(11f, AHEAD);
	private final JPanel steps = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	private final JLabel arrived = label(12f, Color.WHITE);
	private final JButton others = MapCompanionPanel.linkButton("");
	private final JPanel otherWays = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	private final JLabel message = label(11f, AHEAD);
	private boolean open;
	@Nullable
	private String said;
	@Nullable
	private transient DestinationRoute.View shown;

	/** @param choose shows another of the ways, by its place in the map's list */
	RouteStepsView(IntConsumer choose)
	{
		this.choose = choose;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		MapCompanionPanel.left(this);
		add(MapCompanionPanel.left(way));
		add(MapCompanionPanel.left(origin));
		add(steps);
		add(MapCompanionPanel.left(arrived));
		add(others);
		add(otherWays);
		add(MapCompanionPanel.left(message));
		setBorder(new EmptyBorder(5, 0, 0, 0));
		others.addActionListener(e ->
		{
			open = !open;
			DestinationRoute.View view = shown;
			said = null;
			show(view);
		});
		setVisible(false);
	}

	/** Say this route, or nothing. */
	void show(@Nullable DestinationRoute.View view)
	{
		String words = view == null ? null : words(view);
		if (words != null && words.equals(said))
		{
			return;
		}
		said = words;
		shown = view;
		setVisible(view != null && view.hasRoute());
		if (view == null || !view.hasRoute())
		{
			return;
		}
		RelayResponse.RouteOption current = view.way();
		MapCompanionPanel.setWrapped(way, wayLine(current), WIDTH);

		boolean nowhere = view.origin == null && !view.rerouted;
		origin.setVisible(nowhere);
		MapCompanionPanel.setWrapped(origin, "Planned without your position. Reroute plans from where you stand.", WIDTH);

		steps.removeAll();
		List<RelayResponse.RouteStep> list = view.steps();
		for (int i = 0; i < list.size(); i++)
		{
			steps.add(step(i, list.get(i), i < view.step ? -1 : i == view.step ? 0 : 1));
		}

		arrived.setVisible(view.arrived());
		MapCompanionPanel.setWrapped(arrived, "You're there.", WIDTH);

		int more = view.ways.size() - 1;
		others.setVisible(more > 0);
		others.setText((open ? "▾ " : "▸ ") + more + (more == 1 ? " other way" : " other ways"));
		otherWays.removeAll();
		otherWays.setVisible(open && more > 0);
		for (int i = 0; i < view.ways.size(); i++)
		{
			if (i == view.chosen)
			{
				continue;
			}
			int index = i;
			JButton pick = MapCompanionPanel.linkButton(otherLine(view.ways.get(i)));
			pick.setForeground(Color.WHITE);
			pick.setToolTipText("Show this way's steps instead");
			pick.addActionListener(e -> choose.accept(index));
			otherWays.add(pick);
		}

		String status = view.rerouting ? "Planning from where you stand…" : view.message;
		message.setVisible(status != null);
		MapCompanionPanel.setWrapped(message, status == null ? "" : status, WIDTH);

		revalidate();
		repaint();
	}

	/**
	 * One step: its number and words, then what the card prints under it.
	 *
	 * @param place negative behind the current step, zero for it, positive ahead of it
	 */
	private static JComponent step(int index, RelayResponse.RouteStep step, int place)
	{
		JPanel row = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 0, 0));
		JLabel title = label(12f, place == 0 ? Color.WHITE : place < 0 ? DONE : AHEAD);
		if (place == 0)
		{
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			title.setToolTipText("The step you are on");
		}
		MapCompanionPanel.setWrapped(title, stepLine(index, step, place == 0), WIDTH);
		row.add(MapCompanionPanel.left(title));
		if (step.detail != null)
		{
			row.add(under(step.detail, place));
		}
		if (step.notes != null)
		{
			for (String note : step.notes)
			{
				row.add(under(note, place));
			}
		}
		return row;
	}

	private static JLabel under(String text, int place)
	{
		JLabel label = label(11f, place < 0 ? DONE : AHEAD);
		label.setBorder(new EmptyBorder(0, INDENT, 0, 0));
		MapCompanionPanel.setWrapped(label, text, WIDTH - INDENT);
		return MapCompanionPanel.left(label);
	}

	/** "▸ 2. Walk 12 tiles to the bank": the website's number, and a mark on the step you are on. */
	static String stepLine(int index, RelayResponse.RouteStep step, boolean current)
	{
		return (current ? "▸ " : "") + (index + 1) + ". " + step.title;
	}

	/** The way being followed, with its length where the map priced one. */
	static String wayLine(@Nullable RelayResponse.RouteOption way)
	{
		if (way == null)
		{
			return "";
		}
		return way.label + (way.tiles == null ? "" : " · " + way.tiles + " tiles");
	}

	/** Another way, as a line to press: its name, its length, and whether it is the best or out of reach. */
	static String otherLine(RelayResponse.RouteOption way)
	{
		return wayLine(way) + (way.best ? " · best" : "") + (way.blocked ? " · not yet" : "");
	}

	/** Everything this view would say, so an unchanged second does not rebuild it. */
	private String words(DestinationRoute.View view)
	{
		StringBuilder sb = new StringBuilder(view.key).append('|').append(view.chosen).append('|').append(view.step)
			.append('|').append(view.origin).append('|').append(view.rerouted).append('|').append(view.rerouting)
			.append('|').append(view.message).append('|').append(open).append('|');
		for (RelayResponse.RouteOption option : view.ways)
		{
			sb.append(option.id).append(';').append(option.label).append(';').append(option.steps.size()).append(';');
			for (RelayResponse.RouteStep step : option.steps)
			{
				sb.append(step.title).append(';');
			}
		}
		return sb.toString();
	}

	private static JLabel label(float size, Color colour)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(colour);
		return label;
	}
}
