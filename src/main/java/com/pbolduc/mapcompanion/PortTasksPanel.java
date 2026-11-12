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
import java.awt.Color;
import java.awt.GridLayout;
import java.util.*;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.PortTaskReader.Slot;

/**
 * The port tasks this character holds, what each still asks, and the run that does them, in Home.
 *
 * <p>The tasks are read in this client and drawn from the reading alone ({@link PortTaskReader}):
 * no table and no request. The Hub's Sailing plugins already draw the tasks over the game, so this
 * says the one line each that a player plans by — where the crates wait and where they go, or a
 * bounty's part and the board it goes back to — with how far along the task is as a count at the
 * right of its name, so the counts are read down one edge. Shown while the choice is on and
 * something is held or unreadable, through a hop from the reading before it; a slot the client
 * could not read is said, never folded into the free ones.
 *
 * <p>What those plugins do not draw is the order. <b>Start run</b> asks the map for it — the
 * couriers as one run within the hold, then each bounty out and back — and the run is followed
 * here as a farm run is followed on its card ({@link RunView}): numbered stops, the leg's steps
 * and its voyage on the world map, ticked by the slots' own counters. While a run is held the
 * per-task lines step aside, since every stop says its tasks in the order they are done. It is
 * offered only with Boats and moorings shared too: a run sails the player's own boat, and the map
 * plans no voyage from a boat it was never told about. Nor beside a farm run still being walked:
 * the map keeps one run a member, so a Start here would replace it.
 *
 * <p>EDT only. The reading is immutable and replaced whole, so an unchanged one rebuilds nothing.
 */
final class PortTasksPanel extends JPanel
{
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	static final String NEEDS_BOATS = Words.of("port.needsBoats");
	static final String AFTER_FARM_RUN = "Start run after the farm run ends.";
	static final String BOARD_ONCE = Words.of("port.boardOnce");
	private static final String START_TIP = Words.of("port.startTip");
	private final PortTaskReader reader;
	private final ConfigStore settings;
	private final FarmRunClient client;
	private final DestinationRoute routes;
	private final JPanel body = new JPanel();
	/* The card's width, as the farm run's Start run is: it acts, so it is a button and not a link. */
	private final JButton start = MapCompanionPanel.smallButton("Start run");
	private final JLabel hint = MapCompanionPanel.label(11f, MUTED);
	private final JPanel starter = new JPanel(new GridLayout(1, 1));
	private final RunView run;
	private List<Slot> shown;
	private Integer shownHold;
	private boolean shownRunning;

	@Inject
	PortTasksPanel(PortTaskReader reader, MapCompanionConfig config, ConfigStore settings, GameSprites sprites,
		FarmRunState state, FarmRunClient client, FarmRunRoute follower, DestinationRoute routes, ShortestPath shortest)
	{
		this.reader = reader;
		this.settings = settings;
		this.client = client;
		this.routes = routes;
		this.run = new RunView(true, state, client, follower, routes, shortest, sprites, config);
		JPanel card = MapCompanionPanel.taskCard(this, "portTasks", "Port tasks", sprites, "Sailing", settings);
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		card.add(MapCompanionPanel.left(body));
		starter.setOpaque(false);
		starter.setBorder(new EmptyBorder(4, 0, 0, 0));
		start.setToolTipText(START_TIP);
		starter.add(start);
		MapCompanionPanel.capped(starter);
		card.add(MapCompanionPanel.left(starter));
		card.add(hint);
		card.add(run);
		start.addActionListener(e -> client.start(FarmRunWire.PORT_TASKS));
		setVisible(false);
	}

	/** The button a press from the game presses on this card's run, or null with none shown here. EDT. */
	@Nullable
	JButton actButton(MapCompanionPanel.Act act)
	{
		return isVisible() ? run.actButton(act) : null;
	}

	/** Called on the EDT by the sidebar refresh, beside the farm run card. */
	void refresh(boolean connected)
	{
		refresh(connected, true);
	}

	/** The same, knowing whether a character is logged in: with none, Start run waits for one. */
	void refresh(boolean connected, boolean loggedIn)
	{
		List<Slot> slots = settings.on("sharePortTasks") ? reader.shown() : null;
		/* Through a hop's window these are the tasks from before it: shown, but not a reading to plan from. */
		boolean kept = slots != null && reader.latest() == null;
		boolean worth = slots != null && slots.stream().anyMatch(s -> s.held() || PortTaskReader.UNKNOWN.equals(s.state));
		boolean showing = run.refresh(connected, loggedIn, System.currentTimeMillis());
		setVisible(worth || showing);
		/*
		 * A run already held is the order: no second Start beside it. A refusal or "Run ended." is
		 * said under the card with no run behind it, so the tasks stay, and the Start that can try again.
		 */
		boolean running = run.busy();
		boolean offer = startable(slots) && !running;
		/* The farm card's run being walked holds Start back too, and the line where it was says so. */
		boolean after = offer && settings.on("shareBoats") && run.otherBusy();
		starter.setVisible(offer && settings.on("shareBoats") && !after);
		start.setEnabled(connected && loggedIn && client.ready() && !kept);
		/* Greyed by the wait every Route shares, it says how long is left. A hop's window is not that wait. */
		MapWait.tip(start, START_TIP, kept ? 0L : routes.pressWait(System.currentTimeMillis()));
		hint.setVisible(offer && (!settings.on("shareBoats") || after));
		MapCompanionPanel.setWrapped(hint, after ? AFTER_FARM_RUN : NEEDS_BOATS, WIDTH);
		if (!worth)
		{
			shown = null;
			body.setVisible(false);
			return;
		}
		body.setVisible(true);
		Integer hold = reader.shownHold();
		if (slots.equals(shown) && Objects.equals(hold, shownHold) && running == shownRunning)
		{
			return;
		}
		shown = slots;
		shownHold = hold;
		shownRunning = running;
		body.removeAll();
		for (Slot slot : slots)
		{
			if (!slot.held() || running)
			{
				continue;
			}
			/* A courier with every crate in is finished, and greyed as a finished stop on a run is. */
			boolean finished = delivered(slot);
			/*
			 * How far along the task is, as a count at the right edge of its name's row, where the
			 * tasks' counts make a column; the name gives up the count's width, so the two fit the card.
			 */
			String count = count(slot);
			JLabel tally = MapCompanionPanel.label(11f, finished ? ColorScheme.MEDIUM_GRAY_COLOR : MUTED);
			tally.setName("port.count");
			tally.setText(count);
			tally.setVerticalAlignment(JLabel.TOP);
			JLabel name = MapCompanionPanel.wrapped(title(slot), 12f, finished ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE,
				count == null ? WIDTH : WIDTH - tally.getPreferredSize().width - 4);
			JPanel head = MapCompanionPanel.left(MapCompanionPanel.bar(4, null, null, name, count == null ? null : tally));
			head.setOpaque(false);
			head.setBorder(new EmptyBorder(4, 0, 0, 0));
			/* A hairline between tasks, so two lines of one do not run on into the next one's name. */
			body.add(MapCompanionPanel.capped(body.getComponentCount() == 0 ? head : MapCompanionPanel.ruled(head, 5, 3)));
			body.add(line(next(slot), finished ? ColorScheme.MEDIUM_GRAY_COLOR : MUTED, 11f, 0));
		}
		body.add(line(footer(slots, hold), MUTED, 11f, running ? 4 : 6));
		revalidate();
		repaint();
	}

	/** Something a run can do: a courier or a bounty, read this session. */
	static boolean startable(@Nullable List<Slot> slots)
	{
		return slots != null && slots.stream().anyMatch(Slot::held);
	}

	/** A courier whose every crate is delivered: what {@link #next} says as "Delivered". */
	static boolean delivered(Slot slot)
	{
		return PortTaskReader.COURIER.equals(slot.state) && slot.needed > 0 && slot.delivered >= slot.needed;
	}

	/** The game's own name for the task, or its kind where the table gave none. */
	static String title(Slot slot)
	{
		if (slot.name != null)
		{
			return slot.name;
		}
		return PortTaskReader.COURIER.equals(slot.state) ? "Courier task" : "Bounty task";
	}

	/**
	 * What the task asks next, in the order it is done, under its name: the crates' port and where
	 * they go, then where to deliver them; a bounty's part and the board it goes back to. How many is
	 * {@link #count}'s to say, beside the name.
	 */
	static String next(Slot slot)
	{
		String to = slot.to == null ? "the board" : slot.to;
		if (PortTaskReader.COURIER.equals(slot.state))
		{
			if (delivered(slot))
			{
				return "Delivered";
			}
			return slot.taken < slot.needed ? "Collect" + (slot.from == null ? "" : " at " + slot.from) + " for " + to
				: "Deliver to " + to;
		}
		/* The part's own name, unpluralised: "Orca teeth" and "Narwhal blubber" have no plural to add. */
		return (slot.part == null ? "Parts" : slot.part) + " · hand in at " + to;
	}

	/**
	 * How far along the task is, as a count: a courier's crates taken of those asked while any is
	 * still to collect, "0/3", then delivered of those asked, up to "3/3"; a bounty's parts had of
	 * those asked, "6/15", or the parts still owed, "9 left", where the game gave no total. Null for
	 * a courier whose crates were not counted.
	 */
	@Nullable
	static String count(Slot slot)
	{
		if (PortTaskReader.COURIER.equals(slot.state))
		{
			return slot.needed <= 0 ? null
				: (delivered(slot) ? slot.needed : slot.taken < slot.needed ? slot.taken : slot.delivered) + "/" + slot.needed;
		}
		int owed = Math.max(0, slot.remaining);
		return slot.needed > 0 ? Math.max(0, slot.needed - owed) + "/" + slot.needed : owed + " left";
	}

	/**
	 * Unreadable tasks, free slots and what the hold fits; the order is Start run's, beneath it. A
	 * hold not seen yet is a step to take rather than a fact, so it is said as the step. The hold is
	 * a capacity, and "carries 30 crates" reads as cargo aboard.
	 */
	static String footer(List<Slot> slots, @Nullable Integer hold)
	{
		List<String> said = new ArrayList<>();
		long free = slots.stream().filter(s -> PortTaskReader.EMPTY.equals(s.state)).count();
		long unknown = slots.stream().filter(s -> PortTaskReader.UNKNOWN.equals(s.state)).count();
		if (unknown > 0)
		{
			said.add(unknown + (unknown == 1 ? " task" : " tasks") + " couldn't be read");
		}
		if (free > 0)
		{
			said.add(free + (free == 1 ? " slot" : " slots") + " free");
		}
		if (hold != null)
		{
			said.add("hold fits " + hold + " crates");
		}
		String line = String.join(" · ", said);
		line = line.isEmpty() ? "" : Character.toUpperCase(line.charAt(0)) + line.substring(1) + ".";
		return hold != null ? line : (line.isEmpty() ? "" : line + " ") + BOARD_ONCE;
	}

	private static JLabel line(String text, Color color, float size, int top)
	{
		JLabel label = MapCompanionPanel.wrapped(text, size, color, WIDTH);
		label.setBorder(new EmptyBorder(top, 0, 0, 0));
		return label;
	}
}
