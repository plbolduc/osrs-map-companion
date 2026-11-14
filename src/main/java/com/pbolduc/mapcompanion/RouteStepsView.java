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
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.MapCompanionPanel.*;
import static com.pbolduc.mapcompanion.RelayResponse.RouteOption;
import static com.pbolduc.mapcompanion.RelayResponse.RouteStep;
import static net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR;

/**
 * The way to a destination, step by step, inside the destination's card.
 *
 * <p>The website's own words, numbered as the website numbers them, so a player reading the card
 * on the map and this list in the game reads one route. The numbers stand in a column of their own,
 * as the website's journey row puts them, and the words of every step start at one x, wrapped lines
 * and details under the title's first letter. The step you are on has its number in amber, the
 * colour its pin has on the world map, and its title bright; a step behind it is its title alone, in
 * grey; the ones ahead stay as they are. A ▸ or ▾ is only ever a fold.
 *
 * <p>When the map offered more than one way, a strip of chips comes first, one for every way and the
 * one being followed among them, so the ways are compared in one place and picked in one place. A
 * chip is drawn as the website draws its own: the picture of its first step that has one, as the
 * website's route chip leads with its teleport, or else a boat for a voyage and its first step's
 * kind for any other, a boot for a walk; then the name the website's chip wears, where the teleport
 * lands. The chosen chip is filled with the website's orange, and a press on another shows that
 * way's steps instead. The order never follows the choice: the ways the account can take in the
 * map's order, then the ones it cannot yet, their pictures faded and their lines grey, which still
 * show their steps when pressed. A chip that moved when it was pressed would put another way under
 * the pointer. The strip starts a new row before a chip would pass the card's edge.
 *
 * <p>The chips name the ways, so the line under them is the chosen way's length and how many tiles
 * more than the best it is; that a way is the best is in its chip's tip, beside its whole label. A
 * way the account cannot take yet says so on a line of its own, in the refusal pink. A route with
 * one way has no strip, and its line leaves the name out where the first step already says it.
 *
 * <p>Arriving changes the shape rather than greying a route still drawn: the strip, the way and its
 * steps go, and one amber line, <i>Arrived</i>, takes the way line's place, in the colour a run
 * gives a stop's own status. On a run's leg the whole view goes, because the stop it led to already
 * says <i>You're here</i>.
 *
 * <p>A teleport step carries the game's own picture of what to click, as the website's card hangs its
 * sprite on the same row: the item's sprite from {@link GameSprites#item}, the spell's icon from
 * {@link GameSprites#sprite}, by the id the map sent. Both come from this client's cache, never from
 * the map; a step the map names no id for is drawn by its kind, see {@link #way}.
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
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	/** The step numbers' column: two digits, right-aligned, so 9 and 10 end at one x. */
	private static final int NUMBER = 14;
	/** A step's picture: the size the website's chips draw a sprite, and the friends list's news. */
	static final int PICTURE = 16;
	private static final int PICTURE_GAP = 5;
	/** Between two chips of the strip, and under each of its rows. */
	private static final int CHIP_GAP = 4;
	private static final String BEST = " · best";
	private static final Color DONE = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color AHEAD = ColorScheme.LIGHT_GRAY_COLOR;
	/** An unmet gate, in the Slayer list's colour for one: the sidebar's refusal pink. */
	private static final Color UNMET = MapCompanionPanel.REFUSED;

	private final IntConsumer choose;
	@Nullable
	private final GameSprites sprites;
	private final JPanel strip = column(DARKER_GRAY_COLOR);
	private final JLabel way = label(11f, AHEAD);
	private final JLabel cannot = label(11f, UNMET);
	private final JLabel origin = label(11f, AHEAD);
	private final JPanel steps = column(DARKER_GRAY_COLOR);
	private final JLabel arrived = label(11f, MapPin.AMBER);
	private final JLabel message = label(11f, AHEAD);
	private final JLabel boat = label(11f, UNMET);
	private final JLabel drawn = label(11f, AHEAD);
	@Nullable
	private String said;
	@Nullable
	private DestinationRoute.View shown;

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
		column(this, DARKER_GRAY_COLOR);
		add(strip);
		add(way);
		add(cannot);
		add(origin);
		add(steps);
		/* Under the steps it contradicts, whose board step names the mooring the boat has left. */
		add(boat);
		add(arrived);
		add(drawn);
		add(message);
		setBorder(new EmptyBorder(5, 0, 0, 0));
		setVisible(false);
	}

	/** Say this route, or nothing. */
	void show(@Nullable DestinationRoute.View view)
	{
		show(view, null);
	}

	/**
	 * A run leg was planned from its preceding stop, so the destination card's origin warning is
	 * inapplicable. Its way line goes too: the stop's own line just above names the same way, and
	 * the leg's length is This leg's heading's to say, from {@link #tiles}.
	 */
	void showRunLeg(@Nullable DestinationRoute.View view, @Nullable String shortestPath)
	{
		show(view, shortestPath);
		origin.setVisible(false);
		way.setVisible(false);
		/* Nothing here picks a leg's way: the run planned it, and the stop above names it. */
		strip.setVisible(false);
		cannot.setVisible(false);
		/* Arrived, the stop the leg led to says You're here, and the leg has nothing left to say. */
		if (view != null && view.arrived())
		{
			setVisible(false);
		}
	}

	/** The length of the way being shown, in run tiles, where the map priced one; null otherwise. */
	@Nullable
	Integer tiles()
	{
		RouteOption current = shown == null ? null : shown.way();
		return current == null ? null : current.tiles;
	}

	/** @param shortestPath what Shortest Path is drawing for it, in words, or null */
	void show(@Nullable DestinationRoute.View view, @Nullable String shortestPath)
	{
		String words = view == null ? null : words(view) + "|" + shortestPath;
		if (words != null && words.equals(said))
		{
			/* Nothing it says has changed but how long a wait has left, which is said on every refresh. */
			sayMessage(view);
			return;
		}
		said = words;
		shown = view;
		setVisible(view != null && view.hasRoute());
		if (view == null || !view.hasRoute())
		{
			return;
		}
		RouteOption current = view.way();
		/* Arrived: the route has done its work, and one line in the way's place says so. */
		boolean there = view.arrived();
		boolean many = view.ways.size() > 1 && !there;
		strip.setVisible(many);
		strip.removeAll();
		if (many)
		{
			chips(view);
		}
		String line = many ? chosenWayLine(view.ways, current) : currentWayLine(current, view.steps());
		way.setVisible(!there && !line.isEmpty());
		setWrapped(way, line, WIDTH);
		MapCompanionPanel.show(cannot, many && current.blocked ? Words.of("steps.blocked") : null, WIDTH);

		boolean nowhere = view.origin == null && !view.rerouted;
		origin.setVisible(nowhere && !there);
		setWrapped(origin, Words.of("steps.noOrigin"), WIDTH);

		steps.setVisible(!there);
		steps.removeAll();
		List<RouteStep> list = view.steps();
		int sails = sailingRow(current);
		/* The picture's place is kept on every step once one has a picture, so the words start at one x. */
		boolean slot = false;
		for (RouteStep step : list)
		{
			slot |= sprites != null && (step.itemId != null || step.spriteId != null || way(step) != 0);
		}
		for (int i = 0; i < list.size(); i++)
		{
			int place = i < view.step ? -1 : i == view.step ? 0 : 1;
			steps.add(step(i, list.get(i), place, slot, sprites, i == sails ? current.voyage.gates : null));
		}

		MapCompanionPanel.show(boat, view.boatAway == null ? null : boatAwayLine(view.boatAway), WIDTH);

		arrived.setVisible(there);
		setWrapped(arrived, "Arrived", WIDTH);

		MapCompanionPanel.show(drawn, shortestPath, WIDTH);

		sayMessage(view);

		revalidate();
		repaint();
	}

	/**
	 * The line under the steps: Planning… while a Reroute is out, else what the last one said, a wait
	 * it met counted down from the map's wait the view carries. A Reroute refused in pink; its
	 * Planning… and Shortest Path's notices in the line's grey.
	 */
	private void sayMessage(DestinationRoute.View view)
	{
		String status = view.rerouting ? "Planning from where you stand…"
			: MapWait.words(view.message, view.nextRerouteMs - System.currentTimeMillis());
		MapCompanionPanel.show(message, status, WIDTH);
		MapCompanionPanel.tone(message, !view.rerouting && view.refused);
	}

	/**
	 * One step, as one row: its number in the numbers' column, the picture's place when the way keeps
	 * one, then its words, so a wrapped title and the lines under it start under the title's first
	 * letter. A step behind the current one is its title alone: what is done stops pushing the step
	 * being walked down the card.
	 *
	 * @param place negative behind the current step, zero for it, positive ahead of it
	 * @param slot whether every step of the way keeps a picture's place
	 * @param gates a voyage's docking gates, under the row that sails, or null
	 */
	private static JComponent step(int index, RouteStep step, int place, boolean slot,
		@Nullable GameSprites sprites, @Nullable List<RelayResponse.Gate> gates)
	{
		/* The digit alone, in the amber of the current step's pin on the world map. */
		JLabel number = label(12f, place == 0 ? MapPin.AMBER : place < 0 ? DONE : AHEAD);
		number.setText(String.valueOf(index + 1));
		number.setHorizontalAlignment(SwingConstants.RIGHT);
		number.setVerticalAlignment(SwingConstants.TOP);
		JLabel title = label(12f, place == 0 ? Color.WHITE : place < 0 ? DONE : AHEAD);
		if (place == 0)
		{
			MapCompanionPanel.refont(number, Font.BOLD);
			MapCompanionPanel.refont(title, Font.BOLD);
			title.setToolTipText("The step you are on");
		}
		Dimension column = new Dimension(NUMBER, number.getPreferredSize().height);
		number.setPreferredSize(column);
		number.setMinimumSize(column);
		JComponent lead = number;
		if (slot)
		{
			JLabel picture = pictureSlot();
			picture.setName("route.step.picture");
			picture.setVerticalAlignment(SwingConstants.TOP);
			if (sprites != null && (step.itemId != null || step.spriteId != null))
			{
				draw(picture, step, sprites);
			}
			else if (sprites != null && way(step) != 0)
			{
				sprites.sprite(picture, way(step), PICTURE);
			}
			/* Faded behind the current step, as the world map fades the pins of steps done. */
			picture.setEnabled(place >= 0);
			lead = MapCompanionPanel.bar(PICTURE_GAP, DARKER_GRAY_COLOR, number, picture, null);
		}
		int width = WIDTH - NUMBER - PICTURE_GAP - (slot ? PICTURE + PICTURE_GAP : 0);
		JPanel words = column(DARKER_GRAY_COLOR);
		setWrapped(title, step.title, width);
		words.add(title);
		if (place >= 0 && step.detail != null)
		{
			words.add(under(step.detail, width));
		}
		if (place >= 0 && step.notes != null)
		{
			for (String note : step.notes)
			{
				words.add(under(note, width));
			}
		}
		if (place >= 0 && gates != null)
		{
			for (RelayResponse.Gate gate : gates)
			{
				JLabel said = under(gateLine(gate), width);
				if ("unmet".equals(gate.tier))
				{
					said.setForeground(UNMET);
				}
				words.add(said);
			}
		}
		JPanel row = MapCompanionPanel.bar(PICTURE_GAP, DARKER_GRAY_COLOR, lead, words, null);
		row.setBorder(new EmptyBorder(3, 0, 0, 0));
		return MapCompanionPanel.left(row);
	}

	/** A line under a step's title, in its column: its detail, a note, a docking gate. */
	private static JLabel under(String text, int width)
	{
		return MapCompanionPanel.wrapped(text, 11f, AHEAD, width);
	}

	/**
	 * The strip: a chip for every way, left to right, a new row started before a chip would pass the
	 * card's edge. Measured here because a row that wraps by itself does not tell the column above
	 * how tall it has become. The ways the account can take, then the ones it cannot yet, each in the
	 * map's own order, whichever is chosen.
	 */
	private void chips(DestinationRoute.View view)
	{
		JPanel row = null;
		int used = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			for (int i = 0; i < view.ways.size(); i++)
			{
				RouteOption option = view.ways.get(i);
				if (option.blocked != (pass == 1))
				{
					continue;
				}
				JButton chip = chip(i, option, i == view.chosen);
				used += CHIP_GAP + chip.getPreferredSize().width;
				if (row == null || used > WIDTH)
				{
					row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
					row.setBackground(DARKER_GRAY_COLOR);
					row.setBorder(new EmptyBorder(0, 0, CHIP_GAP, 0));
					strip.add(left(row));
					used = chip.getPreferredSize().width;
				}
				else
				{
					row.add(Box.createHorizontalStrut(CHIP_GAP));
				}
				row.add(chip);
			}
		}
	}

	/**
	 * One way, as a chip to press: the picture of the first of its steps that has one, or a voyage's
	 * boat, or its first step's kind, in a place whose size is fixed, then the name the website's chip
	 * for this way wears: the map's {@code face}, which is where a teleport lands or what a ride is
	 * dialled to, and the whole label from a map that sends none. A length alone would
	 * be a number that does not say what it counts. The way being followed is filled with the
	 * website's orange; the others are the card's own ground inside a line. A way not open yet has its
	 * picture drawn disabled, which the look and feel greys and dims as it does any control that
	 * cannot be used, and its line is the card's own dark grey, so it reads as no button though it can
	 * still be pressed. Its name keeps the grey of any way not chosen, because the fainter grey could
	 * not be read. Its size is its parts' and is the same chosen or not, so a press moves nothing; a
	 * name wider than the card is cut to it, and the tip says it whole.
	 */
	private JButton chip(int index, RouteOption option, boolean on)
	{
		JButton chip = new JButton();
		chip.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 0));
		chip.setContentAreaFilled(on);
		chip.setBackground(ColorScheme.BRAND_ORANGE);
		chip.setBorder(new CompoundBorder(new LineBorder(on ? ColorScheme.BRAND_ORANGE : option.blocked ? ColorScheme.DARK_GRAY_COLOR : DONE), new EmptyBorder(2, 1, 2, 2)));
		RouteStep shows = firstPictured(option.steps);
		/* A voyage is its boat, though it starts with the walk to it: a boot would say it is a walk. */
		int kind = shows != null || option.steps.isEmpty() ? 0
			: option.voyage != null ? SpriteID.IconSailingBoats35x35._1 : way(option.steps.get(0));
		if (sprites != null && (shows != null || kind != 0))
		{
			JLabel picture = pictureSlot();
			if (shows != null)
			{
				draw(picture, shows, sprites);
			}
			else
			{
				sprites.sprite(picture, kind, PICTURE);
			}
			picture.setEnabled(!option.blocked);
			chip.add(picture);
		}
		JLabel words = label(11f, on ? Color.WHITE : AHEAD);
		/* A name the map sent is drawn as it was typed, never read as markup. */
		words.putClientProperty("html.disable", true);
		words.setText(option.face == null ? option.label : option.face);
		chip.add(words);
		Dimension size = chip.getLayout().preferredLayoutSize(chip);
		if (size.width > WIDTH)
		{
			Dimension name = words.getPreferredSize();
			words.setPreferredSize(new Dimension(name.width - size.width + WIDTH, name.height));
			size.width = WIDTH;
		}
		chip.setPreferredSize(size);
		String tip = wayLine(option) + (option.best ? BEST : "");
		chip.setToolTipText("<html>" + escape(option.blocked ? tip + ". " + Words.of("steps.blockedTip") : tip));
		/* The way being followed is already shown, so its chip is no press. */
		if (!on)
		{
			chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			chip.addActionListener(e -> choose.accept(index));
		}
		return chip;
	}

	/** The first step with the game's picture of what to click, or null when none has one: a way's or a run stop's. */
	@Nullable
	static RouteStep firstPictured(List<RouteStep> steps)
	{
		for (RouteStep step : steps)
		{
			if (step.itemId != null || step.spriteId != null)
			{
				return step;
			}
		}
		return null;
	}

	/**
	 * A picture's place, its size fixed, so a row is laid out once at its final width and the picture
	 * fills it when the client's cache answers. Its picture is centred, as a sprite smaller than the
	 * square is drawn at its own size.
	 */
	private static JLabel pictureSlot()
	{
		JLabel slot = new JLabel();
		slot.setHorizontalAlignment(SwingConstants.CENTER);
		Dimension square = new Dimension(PICTURE, PICTURE);
		slot.setPreferredSize(square);
		slot.setMinimumSize(square);
		slot.setMaximumSize(square);
		return slot;
	}

	/**
	 * The picture of a step the map names no item or spell for, by the kind of step it is: the
	 * minimap's walking boot for a walk, and from the Sailing interface a boat for boarding and
	 * docking and a ship at sea for the sail. A teleport's row had a picture and these rows were
	 * words alone, so a leg of walk, board, sail, dock, walk was five lines to read. Only the list
	 * of steps and the strip's chips draw them: a stop's picture and the box over the game stay the
	 * step's own.
	 *
	 * <p>A step {@code inside}, through a city's gate or down a dungeon's ladder, is a walk too, and
	 * gets the boot. The map marks {@code walk} only on the rows Shortest Path may be aimed at, which a
	 * step past an entrance is not, and a way that is that step alone would otherwise be a chip with no
	 * picture.
	 *
	 * <p>A {@code descend} step is the way in when the map has not measured the walk inside it: the
	 * ladder, trapdoor or cave mouth itself. The game has no ladder picture, so it gets the interface's
	 * down arrow, which says "down" at chip size whatever the entrance is. The rope or a spade would
	 * read as an item to bring, as every other item picture on a chip does.
	 */
	static int way(RouteStep step)
	{
		return step.walk || "inside".equals(step.slot) ? SpriteID.OrbIcon._2 : RouteWire.SAIL.equals(step.slot) ? SpriteID.IconSailingStatus18x18._0
			: RouteWire.BOARD.equals(step.slot) || RouteWire.DOCK.equals(step.slot) ? SpriteID.IconSailingBoats35x35._1
			: "descend".equals(step.slot) ? SpriteID.Arrows25._2 : 0;
	}

	/** A step's own picture: its item's sprite, or its spell's icon. */
	static void draw(JLabel slot, RouteStep step, GameSprites sprites)
	{
		if (step.itemId != null)
		{
			sprites.item(slot, step.itemId, PICTURE);
		}
		else
		{
			sprites.sprite(slot, step.spriteId, PICTURE);
		}
	}

	/**
	 * The row a way's voyage is sailed on, which its gates hang under: the {@code sail} row of a boat
	 * the map knew, or the one ride before {@code dock} for a boat it did not. Minus one without one.
	 */
	static int sailingRow(@Nullable RouteOption way)
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

	/**
	 * The line over the steps of the way being followed: its length, and its name only when the first
	 * step does not already say it. "Lumbridge Home Teleport · 52 tiles" sat over "Cast Lumbridge Home
	 * Teleport", the same words twice in two lines; a way named for something its first step does not
	 * mention, such as a voyage's "Docking point", keeps its name. Empty when there is nothing to add.
	 */
	static String currentWayLine(@Nullable RouteOption way, @Nullable List<RouteStep> steps)
	{
		if (way == null)
		{
			return "";
		}
		String first = steps == null || steps.isEmpty() || steps.get(0).title == null ? "" : steps.get(0).title;
		boolean named = way.label != null && first.toLowerCase(Locale.ROOT).contains(way.label.toLowerCase(Locale.ROOT));
		if (!named)
		{
			return wayLine(way);
		}
		return way.tiles == null ? "" : way.tiles + " tiles";
	}

	/**
	 * The line under the chips: the chosen way's length, then how it compares, "80 tiles · 28 more
	 * than best". Its chip names it, so the line does not. A longer way says by how much where the map
	 * priced both; the best way's line has nothing to compare, and its chip's tip says it is the best.
	 * Empty for a way the map priced no length for.
	 */
	static String chosenWayLine(List<RouteOption> ways, RouteOption way)
	{
		String line = way.tiles == null ? "" : way.tiles + " tiles";
		for (RouteOption best : ways)
		{
			if (best.best && !way.best && best.tiles != null && way.tiles != null && way.tiles > best.tiles)
			{
				line += " · " + (way.tiles - best.tiles) + " more than best";
			}
		}
		return line;
	}

	/** A way, with its length where the map priced one. */
	static String wayLine(@Nullable RouteOption way)
	{
		if (way == null)
		{
			return "";
		}
		return way.label + (way.tiles == null ? "" : " · " + way.tiles + " tiles");
	}

	/**
	 * What Shortest Path is doing for a followed route: drawing this step's walk, or waiting for the
	 * next walk. Null when the route is not followed, there is no Shortest Path to draw with, or no
	 * walk is left on it: a sea charting run's legs are sails from end to end, and Shortest Path
	 * draws walks only, so the line promised something that never came.
	 */
	@Nullable
	static String shortestPathLine(DestinationRoute.View view, boolean available, int drawing)
	{
		if (!view.following || !available || view.arrived() || !walkLeft(view.steps(), view.step))
		{
			return null;
		}
		if (drawing == view.step + 1)
		{
			return Words.of("steps.drawing") + " " + drawing + ".";
		}
		return Words.of("steps.drawsEach");
	}

	/** Whether a step from {@code from} on is one Shortest Path draws. */
	static boolean walkLeft(List<RouteStep> steps, int from)
	{
		for (int i = Math.max(0, from); i < steps.size(); i++)
		{
			if (steps.get(i).walk)
			{
				return true;
			}
		}
		return false;
	}

	/** Everything this view would say, so an unchanged second does not rebuild it. */
	private String words(DestinationRoute.View view)
	{
		StringBuilder sb = new StringBuilder(view.key).append('|').append(view.chosen).append('|').append(view.step)
			.append('|').append(view.origin).append('|').append(view.rerouted).append('|').append(view.rerouting)
			.append('|').append(view.message).append('|').append(view.refused).append('|').append(view.boatAway)
			.append('|');
		for (RouteOption option : view.ways)
		{
			sb.append(option.id).append(';').append(option.label).append(';').append(option.face).append(option.tiles).append(option.best)
				.append(option.blocked).append(option.steps.size()).append(';');
			for (RouteStep step : option.steps)
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
}
