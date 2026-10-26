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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.util.Text;

/**
 * Everything this plugin puts on the game's world map: a pin while its place is in view, a disc
 * held at the map's edge while it is not.
 *
 * <p>The edge is the point. A destination, a meetup or a friend is useful mostly when it is
 * somewhere the reader is <i>not</i> looking, and a point that simply vanishes when the map is
 * panned away says nothing about which way to go. RuneLite's own Party and clue plugins make the
 * same choice in the same way — {@code snapToEdge}, and a different picture while snapped — so a
 * player already knows how to read it.
 *
 * <p>Drawn rather than loaded: a bundled PNG is one more artifact for a Plugin Hub reviewer, and
 * a coloured circle with two letters is a few lines of {@link Graphics2D}. The colours are the
 * map's, so a friend is the same colour on the website, in the sidebar and here.
 *
 * <p>Names arrive over the network, typed by whoever owns them, and RuneLite reads colour and
 * image tags in menu text — the jump-to entry a point adds is one. {@link #safe} strips tags and
 * bounds length before any of it is shown.
 */
class MapPin extends WorldMapPoint
{
	/** The map's amber: the colour of "the thing you asked about", there and here. */
	static final Color AMBER = new Color(245, 158, 11);

	private static final int DISC = 18;
	private static final int TAIL = 8;
	/** The website draws a quiet member's disc at this opacity; the same number reads the same. */
	private static final float FADED = 0.55f;
	private static final int MAX_LABEL = 40;

	private final BufferedImage pin;
	private final BufferedImage disc;
	private final Point tip;
	@Nullable
	private final Supplier<String> tooltip;

	/**
	 * @param text one or two letters on the disc, or empty for a plain one
	 * @param tooltip computed when the map asks rather than once, so a countdown in it stays true
	 */
	MapPin(WorldPoint where, Color colour, String text, boolean faded, String name, @Nullable Supplier<String> tooltip)
	{
		super(where, null);
		this.disc = disc(colour, text, faded);
		this.pin = pin(disc, faded);
		this.tip = new Point(pin.getWidth() / 2, pin.getHeight() - 1);
		this.tooltip = tooltip;
		setSnapToEdge(true);
		/** Pans the world map to the point. It does not walk, click or hop. */
		setJumpOnClick(true);
		setName(safe(name));
		onEdgeUnsnap();
	}

	@Override
	public String getTooltip()
	{
		/* Cut to a line: the destination and meetup names in it are whatever a group member typed. */
		if (tooltip == null)
		{
			return super.getTooltip();
		}
		String line = plain(tooltip.get());
		return line.length() <= 80 ? line : line.substring(0, 79) + "…";
	}

	@Override
	public void onEdgeSnap()
	{
		setImage(disc);
		/** Null centres the disc on the edge, which is where the direction is read from. */
		setImagePoint(null);
	}

	@Override
	public void onEdgeUnsnap()
	{
		setImage(pin);
		setImagePoint(tip);
	}

	/** A name as words: no tags, no runaway length, never null. */
	static String safe(@Nullable String text)
	{
		String plain = plain(text);
		return plain.length() <= MAX_LABEL ? plain : plain.substring(0, MAX_LABEL - 1) + "…";
	}

	/** Text with every tag RuneLite would read taken out, and the brackets of any half-made one. */
	static String plain(@Nullable String text)
	{
		return text == null ? "" : Text.removeTags(text).replace("<", "").replace(">", "").trim();
	}

	/**
	 * One or two letters, as the map's markers print them: the first letters of two words, or the
	 * first two of one word — "Big Rob" is BR, "Zezima" is ZE.
	 */
	static String initials(@Nullable String name)
	{
		String[] words = safe(name).split("[\\s_-]+");
		java.util.List<String> kept = new java.util.ArrayList<>();
		for (String word : words)
		{
			if (!word.isEmpty())
			{
				kept.add(word);
			}
		}
		if (kept.isEmpty())
		{
			return "?";
		}
		if (kept.size() == 1)
		{
			String word = kept.get(0);
			return word.substring(0, Math.min(2, word.length())).toUpperCase(java.util.Locale.ROOT);
		}
		return (kept.get(0).substring(0, 1) + kept.get(1).substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
	}

	/** {@code #rrggbb} as a colour, or the fallback for anything else. */
	static Color colour(@Nullable String hex, Color fallback)
	{
		if (hex == null || !hex.matches("#[0-9a-fA-F]{6}"))
		{
			return fallback;
		}
		return new Color(Integer.parseInt(hex.substring(1), 16));
	}

	private static BufferedImage disc(Color colour, String text, boolean faded)
	{
		BufferedImage image = new BufferedImage(DISC + 2, DISC + 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (faded)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FADED));
		}
		g.setColor(colour);
		g.fillOval(1, 1, DISC - 1, DISC - 1);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1.2f));
		g.drawOval(1, 1, DISC - 1, DISC - 1);
		if (!text.isEmpty())
		{
			/** Near-black on the palette's light hues, the contrast the website's discs use. */
			g.setColor(new Color(24, 24, 27));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, text.length() > 1 ? 8 : 10));
			FontMetrics metrics = g.getFontMetrics();
			int x = (image.getWidth() - metrics.stringWidth(text)) / 2;
			int y = (image.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
			g.drawString(text, x, y);
		}
		g.dispose();
		return image;
	}

	/** The disc with a point under it, the tip on the tile; the point fades with the disc. */
	private static BufferedImage pin(BufferedImage disc, boolean faded)
	{
		int width = disc.getWidth();
		BufferedImage image = new BufferedImage(width, disc.getHeight() + TAIL, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int top = disc.getHeight() - 4;
		Polygon tail = new Polygon(
			new int[]{width / 2 - 4, width / 2 + 4, width / 2},
			new int[]{top, top, image.getHeight() - 1},
			3);
		if (faded)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FADED));
		}
		g.setColor(Color.BLACK);
		g.fillPolygon(tail);
		/* The disc carries its own fade; drawing it under this one too would fade it twice. */
		g.setComposite(AlphaComposite.SrcOver);
		g.drawImage(disc, 0, 0, null);
		g.dispose();
		return image;
	}
}
