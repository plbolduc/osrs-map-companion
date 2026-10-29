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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;

/**
 * Search the map from the sidebar: a box at the top of Home and up to eight places under it.
 *
 * <p>A pick asks for the way there ({@link RoutePress}) and the answer fills the destination's card
 * below, exactly as a destination the map sent does, so the list folds away as soon as one is
 * picked — the card is the answer, and two lists of places stacked in a 225-pixel column would be
 * the second version of the same answer the sidebar refuses. Enter or typing again brings it back.
 *
 * <p>Shown only while paired: the search is the map's, asked as a member, and a box that can only
 * ever say "connect first" is a row of nothing.
 *
 * <p>The rows are rebuilt only when what they say changes, as the drop-luck list is, because a
 * rebuild under the pointer eats the click and the sidebar refreshes every second.
 *
 * <p><b>No type filter.</b> Eight rows already come sorted by kind — a bare kind word is ordered by
 * distance, an item splits into <i>Sold at</i> and <i>Dropped by</i> — and the website's six chips
 * need two rows at this width. What tells a row's kind at a glance is the game's own icon beside it
 * instead: the world map's for a bank, a ride, a patch, a mine or a shop, its combat icon for a
 * monster, the minimap's yellow dot for a character, and an item's own sprite. All of it is the
 * client's ({@link GameSprites}); nothing is fetched.
 *
 * <p>The box is a plain field rather than RuneLite's icon field because only a plain field takes the
 * look and feel's placeholder, and an empty box should say what it searches.
 */
final class SearchPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	static final String SOURCE = "search";

	/** A row's picture column: the widest sprite it holds, an item's, fitted to this square. */
	static final int PICTURE = 24;
	static final String PLACEHOLDER = "Search places, people, items";

	private final transient SearchClient search;
	private final transient RoutePress press;
	@Nullable
	private final transient GameSprites sprites;
	private final JTextField box = new JTextField();
	private final JPanel rows = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	private final JLabel status = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	/** The list is folded once a pick is pressed; typing or Enter opens it again. */
	private boolean folded;
	@Nullable
	private String drawn;

	SearchPanel(SearchClient search, RoutePress press, @Nullable GameSprites sprites)
	{
		this.search = search;
		this.press = press;
		this.sprites = sprites;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		MapCompanionPanel.left(this);
		setName("companionSearch");
		box.setName("search.box");
		box.putClientProperty("JTextField.placeholderText", PLACEHOLDER);
		box.putClientProperty("JTextField.leadingIcon", SidebarIcons.magnifier());
		box.putClientProperty("JTextField.showClearButton", true);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setPreferredSize(new Dimension(MapCompanionPanel.CARD_TEXT_WIDTH + 16, 28));
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		box.setToolTipText("Search the map: a place, a character, a monster or an item. A pick plans the way there.");
		MapCompanionPanel.left(box);
		box.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				changed();
			}
		});
		box.addActionListener(e ->
		{
			folded = false;
			search.enter(box.getText());
		});
		add(box);
		add(rows);
		add(MapCompanionPanel.left(status));
		status.setBorder(new EmptyBorder(3, 0, 0, 0));
		status.setVisible(false);
		search.listen(this::refresh);
	}

	private void changed()
	{
		folded = false;
		search.typed(box.getText());
	}

	/** The text in the box, for the panel's own test. */
	JTextField box()
	{
		return box;
	}

	/** Redraw from the search's answer and the last press's. Swing. */
	void refresh()
	{
		search.checkScope();
		SearchClient.Result result = search.result();
		RoutePress.Said said = press.said(SOURCE);
		String line = said != null && said.planning ? "Planning the way there…"
			: said != null && said.message != null ? said.message
			: result.searching ? "Searching…"
			: result.message;
		String words = folded + "|" + result.query + "|" + result.suggestion + "|" + line + "|" + result.hits.size()
			+ "|" + hitsKey(result);
		if (Objects.equals(words, drawn))
		{
			return;
		}
		drawn = words;
		rows.removeAll();
		if (!folded)
		{
			String section = null;
			for (SearchClient.Hit hit : result.hits)
			{
				if (hit.section != null && !hit.section.equals(section))
				{
					JLabel head = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
					head.setFont(head.getFont().deriveFont(java.awt.Font.BOLD));
					head.setBorder(new EmptyBorder(4, 0, 1, 0));
					MapCompanionPanel.setWrapped(head, hit.section, WIDTH);
					rows.add(MapCompanionPanel.left(head));
				}
				section = hit.section;
				rows.add(row(hit));
			}
			if (result.suggestion != null)
			{
				String suggestion = result.suggestion;
				JButton mean = MapCompanionPanel.linkButton("Did you mean " + suggestion + "?");
				mean.setName("search.suggestion");
				mean.addActionListener(e ->
				{
					box.setText(suggestion);
					search.enter(suggestion);
				});
				rows.add(mean);
			}
		}
		status.setVisible(line != null);
		if (line != null)
		{
			MapCompanionPanel.setWrapped(status, line, WIDTH + 16);
		}
		rows.revalidate();
		rows.repaint();
		revalidate();
	}

	private static String hitsKey(SearchClient.Result result)
	{
		StringBuilder sb = new StringBuilder();
		for (SearchClient.Hit hit : result.hits)
		{
			sb.append(hit.id).append('|').append(hit.line).append(';');
		}
		return sb.toString();
	}

	/** One place: its picture, its name, and the website's grey line under it. The whole row is the button. */
	private JPanel row(SearchClient.Hit hit)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setName("search.row");
		row.setBorder(new EmptyBorder(4, 8, 4, 8));
		JLabel picture = new JLabel();
		picture.setName("search.picture");
		picture.setHorizontalAlignment(SwingConstants.CENTER);
		picture.setVerticalAlignment(SwingConstants.TOP);
		picture.setPreferredSize(new Dimension(PICTURE, PICTURE));
		picture(picture, hit);
		row.add(picture, BorderLayout.WEST);
		JPanel words = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		words.setOpaque(false);
		int width = WIDTH - PICTURE - 6;
		JLabel name = label(12f, Color.WHITE);
		MapCompanionPanel.setWrapped(name, hit.name, width);
		words.add(MapCompanionPanel.left(name));
		if (!hit.line.isEmpty())
		{
			JLabel line = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
			MapCompanionPanel.setWrapped(line, hit.line, width);
			words.add(MapCompanionPanel.left(line));
		}
		row.add(words, BorderLayout.CENTER);
		row.setToolTipText("Plan the way to " + hit.name);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				pick(hit);
			}
		});
		JPanel slot = new JPanel(new java.awt.BorderLayout());
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 2, 0));
		slot.add(row);
		slot.setMaximumSize(new Dimension(Integer.MAX_VALUE, slot.getPreferredSize().height));
		return MapCompanionPanel.left(slot);
	}

	/** The row's picture, from the game: an item's sprite, a world-map icon, or the character dot. */
	private void picture(JLabel picture, SearchClient.Hit hit)
	{
		if ("npc".equals(hit.kind))
		{
			picture.setIcon(SidebarIcons.characterDot());
			return;
		}
		if (sprites == null)
		{
			return;
		}
		if (hit.itemId != null)
		{
			sprites.item(picture, hit.itemId, PICTURE);
			return;
		}
		if (SearchClient.ITEMS.equals(hit.section))
		{
			sprites.itemNamed(picture, hit.name, PICTURE);
			return;
		}
		int sprite = spriteFor(hit.kind, hit.section);
		if (sprite > 0)
		{
			sprites.sprite(picture, sprite, PICTURE);
		}
	}

	/**
	 * The world map's icon for a row, by the map's kind for it, or 0 for none. A row under
	 * <i>Sold at</i> is a shop of some kind, and the map does not say which, so it is the general
	 * store's sign rather than a guess at the axe shop's. A town or a point of interest has no one
	 * icon, so it has none.
	 */
	static int spriteFor(@Nullable String kind, @Nullable String section)
	{
		if ("Sold at".equals(section))
		{
			return SpriteID.Mapfunction.GENERAL_STORE;
		}
		if (kind == null)
		{
			return 0;
		}
		switch (kind)
		{
			case "bank":
				return SpriteID.Mapfunction.BANK;
			case "transport":
				return SpriteID.Mapfunction.TRANSPORTATION;
			case "farming_patch":
				return SpriteID.Mapfunction.FARMING_PATCH;
			case "star_site":
				return SpriteID.Mapfunction.MINING_SITE;
			case "monster":
				return SpriteID.SideIcons.COMBAT;
			default:
				return 0;
		}
	}

	/** A pick: the way there, by the row's id, which the map resolves as its own search did. */
	void pick(SearchClient.Hit hit)
	{
		WorldPoint stood = hit.tile == null ? null : new WorldPoint(hit.tile[0], hit.tile[1], hit.tile[2]);
		press.press(RoutePress.Target.place(SOURCE, hit.id, hit.name, stood, hit.line.isEmpty() ? null : hit.line));
		folded = true;
		refresh();
	}

	private static JLabel label(float size, Color colour)
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(size));
		label.setForeground(colour);
		return label;
	}
}
