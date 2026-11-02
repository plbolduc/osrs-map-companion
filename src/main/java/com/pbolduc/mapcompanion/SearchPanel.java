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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;
import static com.pbolduc.mapcompanion.MapCompanionPanel.label;

/**
 * Search the map from the sidebar: a box at the top of Home and up to eight places under it.
 *
 * <p>A pick asks for the way there ({@link RoutePress}) and the answer fills the destination's card
 * below, exactly as a destination the map sent does, so the list folds away once that answer lands
 * — the card is the answer, and two lists of places stacked in a 225-pixel column would be the
 * second version of the same answer the sidebar refuses. Enter or typing again brings it back. Not
 * before it lands: a refusal leaves the rows up under a line that names the row, so trying the next
 * one is a press rather than retyping the search.
 *
 * <p>A pick goes only when a press would: while any card's route is planning, or inside the map's
 * wait between two, the rows take no click and draw their names greyed, as every Route button is
 * greyed. A pick then would only be refused, and its refusal would replace the other card's
 * <i>Planning…</i>, since the press keeps one answer for all of them.
 *
 * <p>Shown only while paired and logged in: the search is the map's, asked as a member, a member is
 * a character, and a box that can only ever say "connect first" or "log in first" is a row of nothing.
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
	private static final int WIDTH = MapCompanionPanel.CARD_TEXT_WIDTH;
	static final String SOURCE = "search";

	/** A row's picture column: the widest sprite it holds, an item's, fitted to this square. */
	static final int PICTURE = 24;
	static final String PLACEHOLDER = "Search places, people, items";

	private final SearchClient search;
	private final RoutePress press;
	@Nullable
	private final GameSprites sprites;
	private final JTextField box = new JTextField();
	private final JPanel rows = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
	private final JLabel status = label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	/** The list is folded once a pick's answer has filled the destination's card; typing or Enter opens it again. */
	private boolean folded;
	/** The rows drawn while a pick would not go, whose tooltip says how long the map's wait has left. */
	private final List<JPanel> heldRows = new ArrayList<>();
	/** Such a row's tooltip while a route plans, with no wait to count. */
	private static final String HELD = "The map plans one route at a time, a few seconds apart. Pick again in a moment.";
	/** The row whose pick is out or was refused, so a refusal can name it; null once answered or typed past. */
	@Nullable
	private SearchClient.Hit picked;
	/** The press's last word this list has acted on, so each answer folds or opens it once. */
	@Nullable
	private RoutePress.Said heard;
	/** A word from a pick the player has typed past, which the line under the box no longer says. */
	@Nullable
	private RoutePress.Said quiet;
	/** The player typed while a pick was planning, so its answer, when it lands, is typed past too. */
	private boolean typedPast;
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
			movedOn();
			search.enter(box.getText());
		});
		add(box);
		/* The one line under the box, above the rows, where the player is looking: under a full list it is out of sight. */
		add(MapCompanionPanel.left(status));
		add(rows);
		status.setName("search.status");
		status.setBorder(new EmptyBorder(3, 0, 1, 0));
		status.setVisible(false);
		search.listen(this::refresh);
	}

	private void changed()
	{
		movedOn();
		search.typed(box.getText());
	}

	/**
	 * The box changed or Enter was pressed: the list opens, and a pick made from the old one goes
	 * quiet — its refusal no longer heads a list it was not picked from, and an answer still on its
	 * way fills the card without folding the new list.
	 */
	private void movedOn()
	{
		folded = false;
		picked = null;
		RoutePress.Said said = press.said(SOURCE);
		quiet = said;
		typedPast = said != null && said.planning;
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
		heard(said);
		RoutePress.Said shown = said == quiet ? null : said;
		long now = System.currentTimeMillis();
		String line = shown != null && shown.planning ? "Planning the way there…"
			: shown != null && shown.message != null ? named(press.words(shown, now))
			: result.searching ? "Searching…"
			: searchLine(result, now);
		/* A pick or a search that did not happen, in the refusal pink; what is on its way, or an empty answer, in grey. */
		boolean refused = shown != null && (shown.planning || shown.message != null) ? shown.refused()
			: !result.searching && result.refused;
		/* Drawn on every refresh rather than with the rows: a wait it says counts its seconds down. */
		status.setVisible(line != null);
		if (line != null)
		{
			MapCompanionPanel.setWrapped(status, line, WIDTH + 16);
			MapCompanionPanel.tone(status, refused);
		}
		boolean live = press.ready();
		String words = folded + "|" + live + "|" + result.query + "|" + result.suggestion + "|"
			+ result.hits.size() + "|" + hitsKey(result);
		if (Objects.equals(words, drawn))
		{
			holdTips(now);
			return;
		}
		drawn = words;
		rows.removeAll();
		heldRows.clear();
		if (!folded)
		{
			String section = null;
			for (SearchClient.Hit hit : result.hits)
			{
				if (hit.section != null && !hit.section.equals(section))
				{
					JLabel head = MapCompanionPanel.wrapped(hit.section, 11f, ColorScheme.LIGHT_GRAY_COLOR, WIDTH);
					head.setFont(head.getFont().deriveFont(java.awt.Font.BOLD));
					head.setBorder(new EmptyBorder(4, 0, 1, 0));
					rows.add(head);
				}
				section = hit.section;
				rows.add(row(hit, live));
			}
			if (result.suggestion != null)
			{
				String suggestion = result.suggestion;
				/* A search it will run, so a button: as grey link text it read as one more status line. */
				JButton mean = MapCompanionPanel.smallButton("Did you mean " + suggestion + "?");
				mean.setName("search.suggestion");
				mean.addActionListener(e ->
				{
					box.setText(suggestion);
					search.enter(suggestion);
				});
				JPanel holder = MapCompanionPanel.fill(mean, ColorScheme.DARK_GRAY_COLOR);
				holder.setBorder(new EmptyBorder(3, 0, 0, 0));
				holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, holder.getPreferredSize().height));
				rows.add(holder);
			}
		}
		holdTips(now);
		rows.revalidate();
		rows.repaint();
		revalidate();
	}

	/**
	 * The search's own line at {@code now}: a busy map counts its wait down, and Enter pressed inside
	 * the wait says when the search goes by itself. Either is the search's own wait, not the routes'.
	 */
	@Nullable
	private String searchLine(SearchClient.Result result, long now)
	{
		if (!MapWait.waits(result.message))
		{
			return result.message;
		}
		long wait = search.waitMs(now);
		return result.refused ? MapWait.tryAgain(result.message, wait) : MapWait.searching(wait);
	}

	/** A row that takes no pick says why, and while the map's wait holds the routes, how long it has left. */
	private void holdTips(long now)
	{
		long wait = press.waitMs(now);
		for (JPanel row : heldRows)
		{
			row.setToolTipText(wait > 0L ? MapWait.tip(wait) : HELD);
		}
	}

	/**
	 * Fold or open the list on each new word from a pick, once: an answer that filled the card folds
	 * it, since the card is the answer now, and a refusal leaves the rows up to try the next one.
	 */
	private void heard(@Nullable RoutePress.Said said)
	{
		if (said == heard)
		{
			return;
		}
		heard = said;
		if (said == null || said.planning)
		{
			return;
		}
		if (typedPast && picked == null)
		{
			/* The answer to a pick the player typed past changes neither the new list nor its line. */
			quiet = said;
		}
		else if (said.key == null)
		{
			folded = false;
		}
		else if (picked != null)
		{
			folded = true;
			picked = null;
		}
		typedPast = false;
	}

	/** A refusal by the row it refuses, which the line above the rows would otherwise leave unnamed. */
	private String named(String message)
	{
		return picked == null ? message : picked.name + ": " + message;
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

	/**
	 * One place: its picture, its name, and the website's grey line under it. The whole row is the
	 * button, pressable only while a press would go: otherwise its name is greyed as a button's is.
	 */
	private JPanel row(SearchClient.Hit hit, boolean live)
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
		picture(picture, hit, PICTURE);
		row.add(picture, BorderLayout.WEST);
		JPanel words = MapCompanionPanel.column(ColorScheme.DARKER_GRAY_COLOR);
		words.setOpaque(false);
		int width = WIDTH - PICTURE - 6;
		JLabel name = MapCompanionPanel.wrapped(hit.name, 12f, live ? Color.WHITE : greyed(), width);
		name.setName("search.name");
		words.add(name);
		if (!hit.line.isEmpty())
		{
			words.add(MapCompanionPanel.wrapped(hit.line, 11f, ColorScheme.LIGHT_GRAY_COLOR, width));
		}
		row.add(words, BorderLayout.CENTER);
		if (!live)
		{
			heldRows.add(row);
		}
		else
		{
			row.setToolTipText("Plan the way to " + hit.name);
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(rowPresses(row, hit));
		}
		JPanel slot = new JPanel(new java.awt.BorderLayout());
		slot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slot.setBorder(new EmptyBorder(0, 0, 2, 0));
		slot.add(row);
		slot.setMaximumSize(new Dimension(Integer.MAX_VALUE, slot.getPreferredSize().height));
		return MapCompanionPanel.left(slot);
	}

	/** The colour a disabled button's words are drawn in, so a row that takes no pick reads as one. */
	private static Color greyed()
	{
		Color disabled = UIManager.getColor("Label.disabledForeground");
		return disabled != null ? disabled : ColorScheme.LIGHT_GRAY_COLOR;
	}

	/** Hover and the press, for a row that takes a pick. */
	private MouseAdapter rowPresses(JPanel row, SearchClient.Hit hit)
	{
		return new MouseAdapter()
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
		};
	}

	/**
	 * The row's picture, from the game: an item's sprite, a world-map icon, or the character dot,
	 * fitted to {@code box}. The destination's card draws the same one beside a pick's answer.
	 */
	private void picture(JLabel picture, SearchClient.Hit hit, int box)
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
			sprites.item(picture, hit.itemId, box);
			return;
		}
		if (SearchClient.ITEMS.equals(hit.section))
		{
			sprites.itemNamed(picture, hit.name, box);
			return;
		}
		int sprite = spriteFor(hit.kind, hit.section);
		if (sprite > 0)
		{
			sprites.sprite(picture, sprite, box);
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

	/**
	 * A pick: the way there, by the row's id, which the map resolves as its own search did. Nothing
	 * while a press would not go, and the list stays up until the answer lands ({@link #heard}).
	 */
	void pick(SearchClient.Hit hit)
	{
		if (!press.ready())
		{
			return;
		}
		WorldPoint stood = hit.tile == null ? null : new WorldPoint(hit.tile[0], hit.tile[1], hit.tile[2]);
		picked = hit;
		typedPast = false;
		press.press(RoutePress.Target.place(SOURCE, hit.id, hit.name, stood, note(hit))
			.showing((label, box) -> picture(label, hit, box)));
		refresh();
	}

	/**
	 * The destination card's second line for a pick. An item's route goes to whatever drops or sells
	 * it, and the card takes that source's name, so its line says which item it is for: the row's own
	 * count under a monster's name would read as a fact about the monster and never name the item.
	 * Any other row keeps its own words, without the distance the row measured from where the search
	 * was typed: the card's line is written once, and the way line under it gives the route's own.
	 */
	@Nullable
	static String note(SearchClient.Hit hit)
	{
		if (SearchClient.ITEMS.equals(hit.section))
		{
			return "For " + hit.name;
		}
		return hit.note.isEmpty() ? null : hit.note;
	}
}
