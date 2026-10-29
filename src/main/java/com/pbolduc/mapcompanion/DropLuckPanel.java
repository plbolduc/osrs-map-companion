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

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Drop luck in the sidebar: one monster's card, or the list of every monster tracked.
 *
 * <p>Every word comes from {@link DropLuckView}; this class decides only where it goes. It is
 * rebuilt when what it shows changes — never on the once-a-second refresh alone — because a rebuild
 * under the cursor takes away the row being clicked and the text being typed into the search.
 *
 * <p>Two views, swapped in place, the way RuneLite's own loot tracker and hiscores panels swap
 * theirs: the card, with "All monsters ›" in its corner, and the list, with "Close" in its. A
 * monster opened from the list stays open until the reader closes it. If they were killing
 * something when they opened it, a "Now killing" bar takes them back — nothing jumps on its own.
 */
final class DropLuckPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	/** The sidebar's column inside the companion panel's ten-pixel insets. */
	private static final int WIDTH = PluginPanel.PANEL_WIDTH - 20;
	/** Text beside a 32px picture and a count, inside a card's eight-pixel insets. */
	private static final int ROW_TEXT_WIDTH = WIDTH - 16 - 36 - 46;
	private static final int TEXT_WIDTH = WIDTH - 16;

	/** The section's name, as the sidebar heads its others. */
	static final String HEADING = "Drop luck";

	private static final Color LUCKY = new Color(0x4ade80);
	private static final Color DRY = new Color(0xf59e0b);
	private static final Color VERY_DRY = new Color(0xf87171);
	private static final Color NORMAL = new Color(0xa1a1aa);
	private static final Color TRACK_MIDDLE = new Color(0x6b7280);

	/**
	 * Where an item's picture comes from: the game's own sprite through {@link ItemManager}, which
	 * caches them, or — for the renderer that photographs this panel — the same sprites from files.
	 */
	interface Pictures
	{
		/** The picture, possibly still loading; {@code loaded} runs when it is ready. Null for none. */
		@Nullable
		BufferedImage image(int itemId, Runnable loaded);

		static Pictures of(ItemManager items)
		{
			return (itemId, loaded) ->
			{
				AsyncBufferedImage image = items.getImage(itemId);
				image.onLoaded(loaded);
				return image;
			};
		}
	}

	private final LootLog log;
	/** Null in tests, where there are no pictures to fetch; rows keep their space. */
	@Nullable
	private final Pictures pictures;

	private enum View
	{
		CARD,
		LIST
	}

	private View view = View.CARD;
	/** Whether the card's own head row says "Drop luck"; see {@link #namesItself}. */
	private boolean namesItself;
	/** The monster opened from the list; null means "the default one" — see DropLuckView. */
	@Nullable
	private String opened;
	/** The drop chosen on the card, per monster shown. */
	private final Map<String, String> chosen = new HashMap<>();
	private DropLuckView.Sort sort = DropLuckView.Sort.RECENT;
	/** Kept across rebuilds so typing is never interrupted; only the results under it are rebuilt. */
	private final IconTextField search = new IconTextField();
	private final JPanel results = column(ColorScheme.DARK_GRAY_COLOR);
	private String drawn = "";

	DropLuckPanel(LootLog log, @Nullable Pictures pictures)
	{
		this.log = log;
		this.pictures = pictures;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		search.setPreferredSize(new Dimension(WIDTH, 28));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				redrawResults();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				redrawResults();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				redrawResults();
			}
		});
	}

	/**
	 * Whether this panel's first row says "Drop luck", so the sidebar's own heading over it would say
	 * it twice. The card's head row has an eyebrow on the left — "Last monster killed" — which is
	 * empty while the card is the monster being killed or one opened from the list; there the
	 * section's name takes the empty place, beside All monsters, rather than a row of its own above.
	 */
	boolean namesItself()
	{
		return namesItself;
	}

	/** Whether there is anything to show at all; the companion panel hides the section otherwise. */
	boolean hasContent()
	{
		return !log.monsters().isEmpty();
	}

	/**
	 * Called once a second on the event thread. Redraws only when the words would change.
	 *
	 * <p>The fingerprint is the view's own text, minute-rounded ages included, so "2 h ago" ticking
	 * over to "3 h ago" redraws and a second passing does not.
	 */
	void refresh()
	{
		long now = System.currentTimeMillis();
		List<LootLog.Monster> monsters = log.monsters();
		String current = log.current();
		String[] pin = log.pin();
		StringBuilder print = new StringBuilder().append(view).append('|').append(opened).append('|')
			.append(sort).append('|').append(current).append('|').append(pin == null ? "" : pin[0] + "/" + pin[1])
			.append('|').append(chosen);
		for (LootLog.Monster monster : monsters)
		{
			print.append('|').append(monster.npc).append(':').append(monster.kills).append(':')
				.append(DropLuckView.ago(now - monster.lastKillMs)).append(':').append(log.taskRemaining(monster.npc));
			for (LootLog.Drop drop : monster.drops)
			{
				print.append(',').append(drop.item).append(drop.obtained).append('/').append(drop.sinceLast);
			}
		}
		if (print.toString().equals(drawn))
		{
			return;
		}
		drawn = print.toString();
		rebuild(monsters, current, pin, now);
	}

	private void invalidateAndRefresh()
	{
		drawn = "";
		refresh();
	}

	private void rebuild(List<LootLog.Monster> monsters, @Nullable String current, @Nullable String[] pin, long now)
	{
		/*
		 * A kill while the list is open redraws it, and removing the focused search field sends the
		 * focus elsewhere — the next keystrokes went nowhere, every few seconds of a grind. The field
		 * itself is kept across rebuilds, so giving the focus back is all a redraw owes it.
		 */
		boolean typing = containsFocus(search);
		removeAll();
		try
		{
			build(monsters, current, pin, now);
		}
		finally
		{
			if (typing && view == View.LIST)
			{
				search.requestFocusInWindow();
			}
		}
	}

	private static boolean containsFocus(Component component)
	{
		if (component.isFocusOwner())
		{
			return true;
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				if (containsFocus(child))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void build(List<LootLog.Monster> monsters, @Nullable String current, @Nullable String[] pin, long now)
	{
		namesItself = false;
		if (monsters.isEmpty())
		{
			revalidate();
			repaint();
			return;
		}
		LootLog.Monster now0 = DropLuckView.find(monsters, current);
		LootLog.Monster shown = view == View.CARD
			? (opened != null ? DropLuckView.find(monsters, opened) : DropLuckView.defaultMonster(monsters, current, pin))
			: null;
		if (view == View.CARD && opened != null && shown == null)
		{
			/* The opened monster left the log (forgotten, or evicted); fall back rather than show nothing. */
			opened = null;
			shown = DropLuckView.defaultMonster(monsters, current, pin);
		}
		boolean bar = now0 != null && (view == View.LIST || shown == null || !shown.npc.equals(now0.npc));
		if (bar)
		{
			add(nowBar(now0));
			add(gap(6));
		}
		if (view == View.LIST)
		{
			buildList(monsters, pin, now);
		}
		else if (shown != null)
		{
			buildCard(shown, now0 != null && now0.npc.equals(shown.npc), pin, now, !bar);
		}
		revalidate();
		repaint();
	}

	private JComponent nowBar(LootLog.Monster monster)
	{
		JPanel bar = new JPanel(new BorderLayout(6, 0));
		bar.setBackground(new Color(0x3a2e1a));
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x7a5410)), new EmptyBorder(3, 6, 3, 6)));
		JLabel text = small("<html>Now killing <font color='#ffffff'>" + escape(monster.npc) + "</font></html>", ColorScheme.LIGHT_GRAY_COLOR);
		bar.add(text, BorderLayout.CENTER);
		bar.add(small("Back ›", ColorScheme.BRAND_ORANGE), BorderLayout.EAST);
		clickable(bar, () ->
		{
			view = View.CARD;
			opened = null;
			invalidateAndRefresh();
		});
		return fill(bar);
	}

	private void buildCard(LootLog.Monster monster, boolean isNow, @Nullable String[] pin, long now, boolean first)
	{
		boolean pinnedHere = pin != null && monster.npc.equalsIgnoreCase(pin[0]);
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		String eyebrow = DropLuckView.eyebrow(opened != null, isNow, pinnedHere);
		/* Only as the first row: under the Now killing bar it would name the section halfway down it. */
		namesItself = first && eyebrow.isEmpty();
		JLabel left = small(namesItself ? HEADING : eyebrow, ColorScheme.LIGHT_GRAY_COLOR);
		if (namesItself)
		{
			left.setFont(left.getFont().deriveFont(Font.BOLD, 11f));
		}
		head.add(left, BorderLayout.WEST);
		head.add(link(opened != null ? "‹ All monsters" : "All monsters ›", () ->
		{
			view = View.LIST;
			opened = null;
			invalidateAndRefresh();
		}), BorderLayout.EAST);
		add(fill(head));

		JPanel card = card();
		/*
		 * Wrapped and not bold: RuneLite's font has no bold face, so a derived bold is drawn wider
		 * than it is measured and "Abyssal demon" lost its last letter.
		 */
		card.add(left(wrapped(monster.npc, TEXT_WIDTH, 15f, Color.WHITE)));
		Integer taskLeft = isNow ? log.taskRemaining(monster.npc) : null;
		card.add(left(wrapped(DropLuckView.context(monster, isNow, taskLeft, now), TEXT_WIDTH, 11f,
			taskLeft != null ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR)));
		card.add(gap(6, ColorScheme.DARKER_GRAY_COLOR));

		if (monster.drops.isEmpty())
		{
			card.add(left(wrapped("No notable drops yet. Kills are counted; a drop gets odds where its rate is on file.",
				TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
			add(card);
			return;
		}
		LootLog.Drop selected = DropLuckView.selected(monster, chosen.get(monster.npc), pin);
		for (LootLog.Drop drop : monster.drops)
		{
			boolean pinned = pinnedHere && drop.item.equalsIgnoreCase(pin[1]);
			card.add(dropRow(monster, drop, drop == selected, pinned));
		}
		if (selected != null)
		{
			card.add(gap(6, ColorScheme.DARKER_GRAY_COLOR));
			card.add(rule());
			card.add(gap(6, ColorScheme.DARKER_GRAY_COLOR));
			buildDetail(card, monster, selected, pinnedHere && selected.item.equalsIgnoreCase(pin[1]), taskLeft);
		}
		add(card);
	}

	private JComponent dropRow(LootLog.Monster monster, LootLog.Drop drop, boolean selected, boolean pinned)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		Color background = selected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		row.setBackground(background);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, selected ? ColorScheme.BRAND_ORANGE : background),
			new EmptyBorder(3, 4, 3, 2)));
		row.add(new Picture(drop.itemId, 32, drop.obtained > 0 || drop.oneIn != null, -1), BorderLayout.WEST);

		JPanel words = column(background);
		/* The pinned drop's name is orange, the sidebar's own accent: an emoji pin is not in RuneLite's font. */
		words.add(left(wrapped(drop.item, ROW_TEXT_WIDTH, 12f, pinned ? ColorScheme.BRAND_ORANGE : Color.WHITE)));
		words.add(left(wrapped(DropLuckView.rowDetail(monster, drop, log.boostedNow(monster.npc, drop.boostWhen)),
			ROW_TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		row.add(words, BorderLayout.CENTER);

		JPanel count = column(background);
		JLabel n = new JLabel(DropOdds.count(drop.obtained), SwingConstants.RIGHT);
		n.setFont(n.getFont().deriveFont(16f));
		n.setForeground(drop.obtained > 0 ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
		n.setAlignmentX(Component.RIGHT_ALIGNMENT);
		count.add(n);
		DropOdds.Luck luck = DropLuckView.rowLuck(monster, drop);
		if (luck.word != null)
		{
			JLabel chip = chip(luck);
			chip.setAlignmentX(Component.RIGHT_ALIGNMENT);
			count.add(chip);
		}
		row.add(count, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		clickable(row, () ->
		{
			chosen.put(monster.npc, drop.item);
			invalidateAndRefresh();
		});
		return row;
	}

	private void buildDetail(JPanel card, LootLog.Monster monster, LootLog.Drop drop, boolean pinned, @Nullable Integer taskLeft)
	{
		DropLuckView.Detail detail = DropLuckView.detail(monster, drop, taskLeft, log.boostedNow(monster.npc, drop.boostWhen));
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		head.add(label(drop.item, 12f, Color.WHITE), BorderLayout.WEST);
		JButton pin = new JButton(pinned ? "Pinned" : "Pin");
		pin.setFont(pin.getFont().deriveFont(11f));
		pin.setMargin(new Insets(1, 6, 1, 6));
		pin.setFocusable(false);
		if (pinned)
		{
			pin.setForeground(ColorScheme.BRAND_ORANGE);
		}
		pin.setToolTipText(pinned ? "Unpin: the card goes back to the last monster you killed"
			: "Keep this drop on the card when you are not killing anything");
		pin.addActionListener(e ->
		{
			if (pinned)
			{
				log.pin(null, null);
			}
			else
			{
				log.pin(monster.npc, drop.item);
			}
			invalidateAndRefresh();
		});
		head.add(pin, BorderLayout.EAST);
		card.add(fillIn(head, ColorScheme.DARKER_GRAY_COLOR));
		card.add(gap(3, ColorScheme.DARKER_GRAY_COLOR));
		card.add(left(wrapped(detail.verdict, TEXT_WIDTH, 13f, Color.WHITE)));
		card.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
		card.add(left(wrapped(detail.sinceLabel, TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		/* Two labels, not one with a <font size>: a size change in markup leaves RuneLite's font and its "·". */
		card.add(left(wrapped(detail.sinceValue, TEXT_WIDTH, 13f, Color.WHITE)));
		if (detail.sinceComparison != null)
		{
			card.add(left(wrapped(detail.sinceComparison, TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		}
		if (detail.drierThan >= 0)
		{
			card.add(gap(3, ColorScheme.DARKER_GRAY_COLOR));
			card.add(new Strip(detail.drierThan));
			JPanel ends = new JPanel(new GridLayout(1, 3));
			ends.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			ends.add(small("Lucky", ColorScheme.MEDIUM_GRAY_COLOR));
			JLabel typical = small("Typical", ColorScheme.MEDIUM_GRAY_COLOR);
			typical.setHorizontalAlignment(SwingConstants.CENTER);
			ends.add(typical);
			JLabel dry = small("Dry", ColorScheme.MEDIUM_GRAY_COLOR);
			dry.setHorizontalAlignment(SwingConstants.RIGHT);
			ends.add(dry);
			card.add(fillIn(ends, ColorScheme.DARKER_GRAY_COLOR));
		}
		if (detail.aheadLabel != null)
		{
			card.add(gap(5, ColorScheme.DARKER_GRAY_COLOR));
			card.add(left(wrapped(detail.aheadLabel, TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
			card.add(left(wrapped(detail.aheadValue, TEXT_WIDTH, 13f, Color.WHITE)));
		}
		if (detail.median != null)
		{
			card.add(gap(4, ColorScheme.DARKER_GRAY_COLOR));
			card.add(left(wrapped(detail.median, TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		}
	}

	private void buildList(List<LootLog.Monster> monsters, @Nullable String[] pin, long now)
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.add(small("Drop luck · your monsters", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		head.add(link("Close", () ->
		{
			view = View.CARD;
			opened = null;
			invalidateAndRefresh();
		}), BorderLayout.EAST);
		add(fill(head));
		add(gap(4));
		add(search);
		add(gap(4));
		JPanel sorts = new JPanel(new GridLayout(1, 3, 2, 0));
		sorts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (DropLuckView.Sort option : DropLuckView.Sort.values())
		{
			JButton button = new JButton(option.label);
			button.setFont(button.getFont().deriveFont(11f));
			button.setMargin(new Insets(1, 2, 1, 2));
			button.setFocusable(false);
			button.setForeground(option == sort ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			if (option == sort)
			{
				button.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
			}
			button.addActionListener(e ->
			{
				sort = option;
				invalidateAndRefresh();
			});
			sorts.add(button);
		}
		add(fill(sorts));
		add(gap(6));
		add(results);
		fillResults(monsters, pin, now);
	}

	private void redrawResults()
	{
		if (view != View.LIST)
		{
			return;
		}
		fillResults(log.monsters(), log.pin(), System.currentTimeMillis());
		results.revalidate();
		results.repaint();
	}

	private void fillResults(List<LootLog.Monster> monsters, @Nullable String[] pin, long now)
	{
		results.removeAll();
		String query = search.getText();
		boolean searching = query != null && !query.trim().isEmpty();
		LootLog.Monster pinnedMonster = pin == null ? null : DropLuckView.find(monsters, pin[0]);
		if (!searching && pinnedMonster != null)
		{
			for (LootLog.Drop drop : pinnedMonster.drops)
			{
				if (drop.item.equalsIgnoreCase(pin[1]))
				{
					results.add(section("Pinned"));
					results.add(pinnedRow(pinnedMonster, drop));
					results.add(gap(4));
				}
			}
		}
		List<LootLog.Monster> shown = DropLuckView.list(monsters, query, sort);
		results.add(section(searching ? shown.size() + " found"
			: monsters.size() + (monsters.size() == 1 ? " monster tracked" : " monsters tracked")));
		if (shown.isEmpty())
		{
			results.add(left(wrapped("Nothing tracked matches “" + query.trim() + "”.", WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		}
		for (LootLog.Monster monster : shown)
		{
			results.add(monsterRow(monster, now));
		}
	}

	private JComponent pinnedRow(LootLog.Monster monster, LootLog.Drop drop)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, ColorScheme.BRAND_ORANGE), new EmptyBorder(3, 4, 3, 4)));
		row.add(new Picture(drop.itemId, 26, true, -1), BorderLayout.WEST);
		JPanel words = column(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		words.add(left(wrapped(drop.item, ROW_TEXT_WIDTH, 12f, ColorScheme.BRAND_ORANGE)));
		words.add(left(wrapped(monster.npc + " · " + DropOdds.count(drop.sinceLast) + " kills since last",
			ROW_TEXT_WIDTH, 11f, ColorScheme.LIGHT_GRAY_COLOR)));
		row.add(words, BorderLayout.CENTER);
		if (drop.oneIn != null)
		{
			int shown = DropOdds.shown(DropOdds.within(drop.rate(), drop.sinceKills()));
			DropOdds.Luck luck = shown >= DropOdds.VERY ? DropOdds.Luck.VERY_DRY : shown >= DropOdds.NOTABLE ? DropOdds.Luck.DRY : DropOdds.Luck.NORMAL;
			/* In a column of its own, so the chip keeps its height rather than filling the row's. */
			JPanel side = column(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			JLabel chip = chip(luck);
			chip.setAlignmentX(Component.RIGHT_ALIGNMENT);
			side.add(chip);
			row.add(side, BorderLayout.EAST);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		clickable(row, () -> open(monster.npc, drop.item));
		return row;
	}

	private JComponent monsterRow(LootLog.Monster monster, long now)
	{
		JPanel row = column(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR), new EmptyBorder(5, 2, 5, 2)));
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(label(monster.npc, 12f, Color.WHITE), BorderLayout.CENTER);
		top.add(small(DropLuckView.listMeta(monster, now), ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		row.add(fillIn(top, ColorScheme.DARK_GRAY_COLOR));
		JPanel icons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
		icons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		int shown = 0;
		for (LootLog.Drop drop : monster.drops)
		{
			if (shown++ >= 6)
			{
				break;
			}
			Picture tile = new Picture(drop.itemId, 22, drop.obtained > 0, drop.obtained);
			tile.setToolTipText(drop.item + ": " + DropOdds.count(drop.obtained));
			icons.add(tile);
		}
		DropOdds.Luck dry = DropLuckView.dryNow(monster);
		if (dry != DropOdds.Luck.NONE)
		{
			icons.add(chip(dry));
		}
		if (monster.drops.isEmpty())
		{
			icons.add(small("No notable drops yet", ColorScheme.MEDIUM_GRAY_COLOR));
		}
		row.add(fillIn(icons, ColorScheme.DARK_GRAY_COLOR));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		clickable(row, () -> open(monster.npc, null));
		return row;
	}

	private void open(String npc, @Nullable String item)
	{
		view = View.CARD;
		opened = npc;
		if (item != null)
		{
			chosen.put(npc, item);
		}
		invalidateAndRefresh();
	}

	/**
	 * An item's picture, or the space for one.
	 *
	 * <p>Painted rather than set as an icon, so a drop not had yet can be drawn grey and faint — the
	 * collection log's own way of saying "not yet" — and a count can sit on the corner the way the
	 * game prints a stack. A picture still loading paints nothing and repaints when it arrives.
	 */
	private final class Picture extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final int size;
		private final boolean had;
		/** Drawn on the corner when positive; -1 for none. */
		private final int count;
		@Nullable
		private final BufferedImage image;

		Picture(int itemId, int size, boolean had, int count)
		{
			this.size = size;
			this.had = had;
			this.count = count;
			this.image = pictures == null || itemId <= 0 ? null : pictures.image(itemId, this::repaint);
			Dimension d = new Dimension(size + 2, size);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (image == null)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			Image drawn = image;
			if (!had)
			{
				/*
				 * Greyed on every paint rather than once: a sprite still loading has its final size
				 * and blank pixels, so a cached grey copy of it would stay blank. It is 36 by 32.
				 */
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
				drawn = ImageUtil.grayscaleImage(image);
			}
			double scale = Math.min((double) size / image.getHeight(), (double) (size + 2) / image.getWidth());
			int w = (int) Math.round(image.getWidth() * scale);
			int h = (int) Math.round(image.getHeight() * scale);
			g2.drawImage(drawn, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
			if (count > 0)
			{
				g2.setComposite(AlphaComposite.SrcOver);
				g2.setFont(getFont().deriveFont(10f));
				String text = count > 999 ? "999+" : Integer.toString(count);
				int x = getWidth() - g2.getFontMetrics().stringWidth(text) - 1;
				int y = getHeight() - 1;
				g2.setColor(Color.BLACK);
				g2.drawString(text, x + 1, y + 1);
				g2.setColor(Color.YELLOW);
				g2.drawString(text, x, y);
			}
			g2.dispose();
		}
	}

	/**
	 * Where a wait sits among players, from lucky to dry.
	 *
	 * <p>Not a progress bar: it does not fill towards a guaranteed drop, because there is none. The
	 * dot moves right as a wait grows past what most players see and jumps back left when the drop
	 * lands. The middle tick is the median player.
	 */
	private static final class Strip extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final double at;

		Strip(double at)
		{
			this.at = Math.max(0.01, Math.min(0.99, at));
			Dimension d = new Dimension(TEXT_WIDTH, 16);
			setPreferredSize(d);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth() - 8;
			int x0 = 4;
			int y = 5;
			int half = w / 2;
			g2.setPaint(new GradientPaint(x0, 0, LUCKY, x0 + (int) (w * 0.35), 0, TRACK_MIDDLE));
			g2.fillRect(x0, y, half, 6);
			g2.setPaint(new GradientPaint(x0 + (int) (w * 0.65), 0, TRACK_MIDDLE, x0 + w, 0, VERY_DRY));
			g2.fillRect(x0 + half, y, w - half, 6);
			g2.setColor(new Color(0x9ca3af));
			g2.fillRect(x0 + half, y - 3, 1, 12);
			int cx = x0 + (int) Math.round(w * at);
			g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g2.fillOval(cx - 7, y - 4, 14, 14);
			g2.setColor(Color.WHITE);
			g2.fillOval(cx - 5, y - 2, 10, 10);
			g2.dispose();
		}
	}

	private static JLabel chip(DropOdds.Luck luck)
	{
		JLabel chip = new JLabel(luck.word == null ? "" : luck.word);
		chip.setOpaque(true);
		chip.setFont(chip.getFont().deriveFont(10f));
		chip.setBorder(new EmptyBorder(0, 4, 0, 4));
		Color background = luck.lucky() ? LUCKY : luck == DropOdds.Luck.VERY_DRY ? VERY_DRY : luck.dry() ? DRY : NORMAL;
		chip.setBackground(background);
		chip.setForeground(new Color(0x18181b));
		return chip;
	}

	private void clickable(JComponent component, Runnable action)
	{
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		/* Clicked rather than released: a press dragged off the row and let go elsewhere is not a choice. */
		MouseAdapter press = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		};
		addMouse(component, press);
	}

	/** Children too: a click on a row's words is a click on the row. Buttons keep their own. */
	private static void addMouse(Component component, MouseAdapter listener)
	{
		if (component instanceof JButton)
		{
			return;
		}
		component.addMouseListener(listener);
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				addMouse(child, listener);
			}
		}
	}

	private JComponent link(String text, Runnable action)
	{
		JLabel link = small(text, ColorScheme.BRAND_ORANGE);
		clickable(link, action);
		return link;
	}

	private static JLabel section(String text)
	{
		JLabel label = small(text, ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(4, 0, 2, 0));
		return left(label);
	}

	private static JLabel small(String text, Color color)
	{
		return label(text, 11f, color);
	}

	private static JLabel label(String text, float size, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(color);
		return label;
	}

	/** A label that wraps at {@code width}: a JLabel does not wrap on its own, it cuts off mid-word. */
	private static JLabel wrapped(String text, int width, float size, Color color)
	{
		return html(escape(text), width, size, color);
	}

	/** The same, for markup that is already escaped where it needs to be. */
	private static JLabel html(String markup, int width, float size, Color color)
	{
		return label("<html><body width='" + width + "'>" + markup + "</body></html>", size, color);
	}

	private static JPanel card()
	{
		JPanel card = column(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 8, 8));
		return card;
	}

	private static JPanel column(Color background)
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(background);
		return left(column);
	}

	private static JComponent rule()
	{
		JPanel rule = new JPanel();
		rule.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rule.setPreferredSize(new Dimension(1, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return left(rule);
	}

	private static JPanel gap(int height)
	{
		return gap(height, ColorScheme.DARK_GRAY_COLOR);
	}

	private static JPanel gap(int height, Color background)
	{
		JPanel spacer = new JPanel();
		spacer.setBackground(background);
		spacer.setPreferredSize(new Dimension(1, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return left(spacer);
	}

	private static JComponent fill(JComponent component)
	{
		return fillIn(component, ColorScheme.DARK_GRAY_COLOR);
	}

	private static JComponent fillIn(JComponent component, Color background)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(background);
		holder.add(component, BorderLayout.CENTER);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return left(holder);
	}

	private static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
