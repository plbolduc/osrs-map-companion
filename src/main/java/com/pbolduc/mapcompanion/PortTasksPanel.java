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
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The port tasks this character holds, and what each still asks, in Home.
 *
 * <p>Read in this client and drawn from the reading alone ({@link PortTaskReader}): no table and
 * no request. The Hub's Sailing plugins already draw the tasks over the game, so this says the one
 * line each that a player plans by — the crates still to collect and where, or the parts still owed
 * and where they go — and leaves the order of a courier run to the map's Port tasks page, which can
 * price the sail between every port. Shown while the choice is on and something is held or
 * unreadable; a slot the client could not read is said, never folded into the free ones.
 *
 * <p>EDT only. The reading is immutable and replaced whole, so an unchanged one rebuilds nothing.
 */
final class PortTasksPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private final transient PortTaskReader reader;
	private final transient MapCompanionConfig config;
	private final JPanel body = new JPanel();
	private transient List<PortTaskReader.Slot> shown;
	private transient Integer shownHold;

	@Inject
	PortTasksPanel(PortTaskReader reader, MapCompanionConfig config, GameSprites sprites)
	{
		this.reader = reader;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setName("portTasks");
		MapCompanionPanel.left(this);
		JLabel heading = new JLabel("Port tasks");
		heading.setForeground(Color.WHITE);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
		/* The bold face's last "s" draws past its advance; two pixels keep it inside the label. */
		heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
		sprites.skill(heading, "Sailing", 16);
		add(MapCompanionPanel.left(heading));
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		add(MapCompanionPanel.left(body));
		setVisible(false);
	}

	void refresh()
	{
		List<PortTaskReader.Slot> slots = config.sharePortTasks() ? reader.latest() : null;
		boolean worth = slots != null && slots.stream().anyMatch(s -> s.held() || PortTaskReader.UNKNOWN.equals(s.state));
		setVisible(worth);
		if (!worth)
		{
			shown = null;
			return;
		}
		Integer hold = reader.latestHold();
		if (slots == shown && Objects.equals(hold, shownHold))
		{
			return;
		}
		shown = slots;
		shownHold = hold;
		body.removeAll();
		for (PortTaskReader.Slot slot : slots)
		{
			if (!slot.held())
			{
				continue;
			}
			body.add(line(title(slot), Color.WHITE, 12f, 4));
			body.add(line(next(slot), MUTED, 11f, 0));
		}
		body.add(line(footer(slots, hold), MUTED, 11f, 6));
		revalidate();
		repaint();
	}

	/** The game's own name for the task, or its kind where the table gave none. */
	static String title(PortTaskReader.Slot slot)
	{
		if (slot.name != null)
		{
			return slot.name;
		}
		return PortTaskReader.COURIER.equals(slot.state) ? "Courier task" : "Bounty task";
	}

	/**
	 * What the task still asks, in the order it is done: the crates to collect and where they go,
	 * then the crates aboard to deliver; the parts still owed, then the board they go back to.
	 */
	static String next(PortTaskReader.Slot slot)
	{
		String to = slot.to == null ? "the board" : slot.to;
		if (PortTaskReader.COURIER.equals(slot.state))
		{
			int collect = Math.max(0, slot.needed - slot.taken);
			int deliver = Math.max(0, slot.needed - slot.delivered);
			if (slot.needed > 0 && deliver == 0)
			{
				return "All " + crates(slot.needed) + " delivered";
			}
			if (collect > 0)
			{
				return "Collect " + crates(collect) + (slot.from == null ? "" : " at " + slot.from) + " for " + to;
			}
			return "Deliver " + crates(deliver) + " to " + to;
		}
		/* The part's own name, unpluralised: "Orca teeth" and "Narwhal blubber" have no plural to add. */
		String part = slot.part == null ? "Parts" : slot.part;
		if (slot.remaining <= 0)
		{
			return part + " · all collected · hand in at " + to;
		}
		String of = slot.needed > 0 ? slot.remaining + " of " + slot.needed : String.valueOf(slot.remaining);
		return part + " · " + of + " still owed · hand in at " + to;
	}

	/** Free and unreadable slots, the hold, and where the order is. */
	static String footer(List<PortTaskReader.Slot> slots, @Nullable Integer hold)
	{
		List<String> said = new ArrayList<>();
		long free = slots.stream().filter(s -> PortTaskReader.EMPTY.equals(s.state)).count();
		long unknown = slots.stream().filter(s -> PortTaskReader.UNKNOWN.equals(s.state)).count();
		if (unknown > 0)
		{
			said.add(unknown + (unknown == 1 ? " slot" : " slots") + " couldn't be read");
		}
		if (free > 0)
		{
			said.add(free + " free");
		}
		said.add(hold == null ? "hold not seen yet" : "hold carries " + hold + " crates");
		boolean courier = slots.stream().anyMatch(s -> PortTaskReader.COURIER.equals(s.state));
		String line = String.join(" · ", said);
		return Character.toUpperCase(line.charAt(0)) + line.substring(1)
			+ (courier ? ". The map's Port tasks page orders your stops into one run." : ".");
	}

	private static String crates(int n)
	{
		return n + (n == 1 ? " crate" : " crates");
	}

	private static JLabel line(String text, Color color, float size, int top)
	{
		JLabel label = new JLabel();
		label.setForeground(color);
		label.setFont(label.getFont().deriveFont(size));
		label.setBorder(BorderFactory.createEmptyBorder(top, 0, 0, 0));
		MapCompanionPanel.setWrapped(label, text, WIDTH);
		return MapCompanionPanel.left(label);
	}
}
