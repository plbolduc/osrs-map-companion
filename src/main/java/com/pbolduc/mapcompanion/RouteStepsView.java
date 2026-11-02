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
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
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
 * <p>Arriving changes the shape rather than greying a route still drawn: the way, its steps and the
 * other ways go, and one amber line, <i>Arrived</i>, takes the way line's place, in the colour a run
 * gives a stop's own status. On a run's leg the whole view goes, because the stop it led to already
 * says <i>You're here</i>.
 *
 * <p>A teleport step carries the game's own picture of what to click, as the website's card hangs its
 * sprite on the same row: the item's sprite from {@link GameSprites#item}, the spell's icon from
 * {@link GameSprites#sprite}, by the id the map sent. Both come from this client's cache, never from
 * the map; a step the map names no id for is words alone, as it always was.
 *
 * <p>When Shortest Path is drawing a step's walk for this route, a line under the steps says so, so
 * the line on the ground is never taken for this plugin's own drawing.
 *
 * <p>A voyage's docking gates hang under the row that sails, in the tiers the Slayer list uses:
 * <i>Needs</i> for a level or quest the account lacks, <i>Unconfirmed</i> where nothing was shared,
 * a tick for one it has. When the boats read since the route was planned put none at the mooring the
 * way boards at, a line says so and points at Reroute.
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
	/** A step's picture: the size the website's chips draw a sprite, and the friends list's news. */
	static final int PICTURE = 16;
	private static final int PICTURE_GAP = 5;
	/** The picture's place while the client's cache answers. */
	private static final ImageIcon NO_PICTURE_YET = new ImageIcon(new BufferedImage(PICTURE, PICTURE, BufferedImage.TYPE_INT_ARGB));
	private static final Color DONE = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color AHEAD = ColorScheme.LIGHT_GRAY_COLOR;
	/** An unmet gate, in the Slayer list's colour for one. */
	private static final Color UNMET = new Color(239, 150, 150);

	private final transient IntConsumer choose;
	@Nullable
	private final transient GameSprites sprites;
	private final JLabel way = label(11f, AHEAD);
	private final JLabel origin = label(11f, AHEAD);
	private final JPanel steps = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	private final JLabel arrived = label(11f, MapPin.AMBER);
	private final JButton others = MapCompanionPanel.linkButton("");
	private final JPanel otherWays = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
	private final JLabel message = label(11f, AHEAD);
	private final JLabel boat = label(11f, UNMET);
	private final JLabel drawn = label(11f, AHEAD);
	@Nullable
	private String drawnLine;
	private boolean open;
	@Nullable
	private String said;
	@Nullable
	private transient DestinationRoute.View shown;

	/** @param choose shows another of the ways, by its place in the map's list */
	RouteStepsView(IntConsumer choose)
	{
		this(choose, null);
	}

	/**
	 * @param choose shows another of the ways, by its place in the map's list
	 * @param sprites the client's pictures for a step's item or spell, or null to draw words alone
	 */
	RouteStepsView(IntConsumer choose, @Nullable GameSprites sprites)
	{
		this.choose = choose;
		this.sprites = sprites;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		MapCompanionPanel.left(this);
		add(MapCompanionPanel.left(way));
		add(MapCompanionPanel.left(origin));
		add(steps);
		/* Under the steps it contradicts, whose board step names the mooring the boat has left. */
		add(MapCompanionPanel.left(boat));
		add(MapCompanionPanel.left(arrived));
		add(others);
		add(otherWays);
		add(MapCompanionPanel.left(drawn));
		add(MapCompanionPanel.left(message));
		setBorder(new EmptyBorder(5, 0, 0, 0));
		others.addActionListener(e ->
		{
			open = !open;
			DestinationRoute.View view = shown;
			said = null;
			show(view, drawnLine);
		});
		setVisible(false);
	}

	/** Say this route, or nothing. */
	void show(@Nullable DestinationRoute.View view)
	{
		show(view, null);
	}

	/** A run leg was planned from its preceding stop; the destination card's origin warning is inapplicable. */
	void showRunLeg(@Nullable DestinationRoute.View view, @Nullable String shortestPath)
	{
		show(view, shortestPath);
		origin.setVisible(false);
		/* Arrived, the stop the leg led to says You're here, and the leg has nothing left to say. */
		if (view != null && view.arrived())
		{
			setVisible(false);
		}
	}

	/** @param shortestPath what Shortest Path is drawing for it, in words, or null */
	void show(@Nullable DestinationRoute.View view, @Nullable String shortestPath)
	{
		drawnLine = shortestPath;
		String words = view == null ? null : words(view) + "|" + shortestPath;
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
		/* Arrived: the route has done its work, and one line in the way's place says so. */
		boolean there = view.arrived();
		way.setVisible(!there);
		MapCompanionPanel.setWrapped(way, wayLine(current), WIDTH);

		boolean nowhere = view.origin == null && !view.rerouted;
		origin.setVisible(nowhere && !there);
		MapCompanionPanel.setWrapped(origin, "Planned without your position. Reroute plans from where you stand.", WIDTH);

		steps.setVisible(!there);
		steps.removeAll();
		List<RelayResponse.RouteStep> list = view.steps();
		int sails = sailingRow(current);
		for (int i = 0; i < list.size(); i++)
		{
			int place = i < view.step ? -1 : i == view.step ? 0 : 1;
			JComponent row = step(i, list.get(i), place, sprites);
			if (i == sails && current.voyage.gates != null)
			{
				for (RelayResponse.Gate gate : current.voyage.gates)
				{
					RelayResponse.RouteStep sailed = list.get(i);
					boolean pictured = sprites != null && (sailed.itemId != null || sailed.spriteId != null);
					JLabel said = under(gateLine(gate), place, pictured ? PICTURE + PICTURE_GAP : 0);
					if ("unmet".equals(gate.tier) && place >= 0)
					{
						said.setForeground(UNMET);
					}
					row.add(said);
				}
			}
			steps.add(row);
		}

		boat.setVisible(view.boatAway != null);
		MapCompanionPanel.setWrapped(boat, view.boatAway == null ? "" : boatAwayLine(view.boatAway), WIDTH);

		arrived.setVisible(there);
		MapCompanionPanel.setWrapped(arrived, "Arrived", WIDTH);

		int more = view.ways.size() - 1;
		others.setVisible(more > 0 && !there);
		others.setText((open ? "▾ " : "▸ ") + more + (more == 1 ? " other way" : " other ways"));
		otherWays.removeAll();
		otherWays.setVisible(open && more > 0 && !there);
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

		drawn.setVisible(shortestPath != null);
		MapCompanionPanel.setWrapped(drawn, shortestPath == null ? "" : shortestPath, WIDTH);

		String status = view.rerouting ? "Planning from where you stand…" : view.message;
		message.setVisible(status != null);
		MapCompanionPanel.setWrapped(message, status == null ? "" : status, WIDTH);

		revalidate();
		repaint();
	}

	/**
	 * One step: its picture, number and words, then what the card prints under it.
	 *
	 * @param place negative behind the current step, zero for it, positive ahead of it
	 */
	private static JComponent step(int index, RelayResponse.RouteStep step, int place, @Nullable GameSprites sprites)
	{
		JPanel row = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 0, 0));
		JLabel title = label(12f, place == 0 ? Color.WHITE : place < 0 ? DONE : AHEAD);
		if (place == 0)
		{
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			title.setToolTipText("The step you are on");
		}
		/*
		 * The words are laid out at once beside an empty slot of the picture's width, and the picture
		 * fills it when the client's cache answers, so the row never reflows under the pointer.
		 */
		boolean pictured = sprites != null && (step.itemId != null || step.spriteId != null);
		int beside = pictured ? PICTURE + PICTURE_GAP : 0;
		if (pictured)
		{
			title.setIconTextGap(PICTURE_GAP);
			title.setIcon(NO_PICTURE_YET);
			if (step.itemId != null)
			{
				sprites.item(title, step.itemId, PICTURE);
			}
			else
			{
				sprites.sprite(title, step.spriteId, PICTURE);
			}
		}
		MapCompanionPanel.setWrapped(title, stepLine(index, step, place == 0), WIDTH - beside);
		row.add(MapCompanionPanel.left(title));
		if (step.detail != null)
		{
			row.add(under(step.detail, place, beside));
		}
		if (step.notes != null)
		{
			for (String note : step.notes)
			{
				row.add(under(note, place, beside));
			}
		}
		return row;
	}

	/** A line under a step, past its number and, on a step with a picture, past the picture too. */
	private static JLabel under(String text, int place, int beside)
	{
		JLabel label = label(11f, place < 0 ? DONE : AHEAD);
		label.setBorder(new EmptyBorder(0, INDENT + beside, 0, 0));
		MapCompanionPanel.setWrapped(label, text, WIDTH - INDENT - beside);
		return MapCompanionPanel.left(label);
	}

	/**
	 * The row a way's voyage is sailed on, which its gates hang under: the {@code sail} row of a boat
	 * the map knew, or the one ride before {@code dock} for a boat it did not. Minus one without one.
	 */
	static int sailingRow(@Nullable RelayResponse.RouteOption way)
	{
		if (way == null || way.voyage == null)
		{
			return -1;
		}
		for (int i = 0; i < way.steps.size(); i++)
		{
			if (RouteWire.SAIL.equals(way.steps.get(i).slot))
			{
				return i;
			}
		}
		for (int i = 1; i < way.steps.size(); i++)
		{
			if (RouteWire.DOCK.equals(way.steps.get(i).slot) && "method".equals(way.steps.get(i - 1).slot))
			{
				return i - 1;
			}
		}
		return -1;
	}

	/** One docking gate in its tier's words: "Needs 76 Sailing", "Unconfirmed: Swan Song", "✓ 20 Sailing". */
	static String gateLine(RelayResponse.Gate gate)
	{
		return ("unmet".equals(gate.tier) ? "Needs " : "unknown".equals(gate.tier) ? "Unconfirmed: " : "✓ ") + gate.text;
	}

	static String boatAwayLine(String port)
	{
		return "Your boat is no longer at " + port + ". Reroute plans from where it is.";
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

	/**
	 * What Shortest Path is doing for a followed route: drawing this step's walk, or waiting for the
	 * next walk. Null when the route is not followed or there is no Shortest Path to draw with.
	 */
	@Nullable
	static String shortestPathLine(DestinationRoute.View view, boolean available, int drawing)
	{
		if (!view.following || !available || view.arrived())
		{
			return null;
		}
		if (drawing == view.step + 1)
		{
			return "Shortest Path is drawing the walk for step " + drawing + ".";
		}
		return "Shortest Path draws each walk when you reach it.";
	}

	/** Everything this view would say, so an unchanged second does not rebuild it. */
	private String words(DestinationRoute.View view)
	{
		StringBuilder sb = new StringBuilder(view.key).append('|').append(view.chosen).append('|').append(view.step)
			.append('|').append(view.origin).append('|').append(view.rerouted).append('|').append(view.rerouting)
			.append('|').append(view.message).append('|').append(open).append('|').append(view.boatAway).append('|');
		for (RelayResponse.RouteOption option : view.ways)
		{
			sb.append(option.id).append(';').append(option.label).append(';').append(option.steps.size()).append(';');
			for (RelayResponse.RouteStep step : option.steps)
			{
				sb.append(step.title).append(';').append(step.itemId).append(';').append(step.spriteId).append(';');
			}
			if (option.voyage != null && option.voyage.gates != null)
			{
				for (RelayResponse.Gate gate : option.voyage.gates)
				{
					sb.append(gate.tier).append(':').append(gate.text).append(';');
				}
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
