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
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.util.Text;

/**
 * Everything this plugin puts on the game's world map: a pin while its place is in view, its
 * head held at the map's edge while it is not.
 *
 * <p>The edge is the point. A destination, a meetup or a friend is useful mostly when it is
 * somewhere the reader is <i>not</i> looking, and a point that simply vanishes when the map is
 * panned away says nothing about which way to go. RuneLite's own Party and clue plugins make the
 * same choice in the same way — {@code snapToEdge}, and a different picture while snapped — so a
 * player already knows how to read it.
 *
 * <p>Held there, the head turns its point the way the place lies, and the one the player asked
 * about — the destination, the step being walked, the meetup — says what it is beside it. A bare
 * disc with a 1 on it said neither: it sat on the left edge for a place to the left and for a
 * place the map had only just scrolled past, and the number was a step the reader had to open
 * the sidebar to read. A friend's head points and says nothing more; its letters are the name.
 *
 * <p>A pin is one shape, a head drawn out to a point on its tile, in a light rim inside a dark
 * one. The game's map is every colour a member's disc can be, and its own icons are ringed the
 * same way for the same reason: an edge that is both light and dark shows on any of it.
 *
 * <p>Drawn rather than loaded: a bundled PNG is one more artifact for a Plugin Hub reviewer, and
 * a coloured shape with two letters is a few lines of {@link Graphics2D}. The colours are the
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
	/** The same amber as a font colour, for the sidebar's HTML labels. */
	static final String AMBER_FONT = String.format("#%06x", AMBER.getRGB() & 0xFFFFFF);
	/** The gold of the website's ring round what is yours: its amber-400. */
	static final Color GOLD = new Color(251, 191, 36);

	/** Near-black, the ink of the letters and of the dark rim. */
	private static final Color INK = new Color(24, 24, 27);
	private static final Color RIM = new Color(250, 250, 250);

	private static final int DISC = 18;
	/** The head's radius, a member's disc, and the room its rims and the tip's shadow take round it. */
	private static final double R = DISC / 2.0;
	private static final int PAD = 3;
	/** From the head's centre to the tip on the tile. */
	private static final int TAIL = 17;
	/** From the head's centre to its point at the edge: shorter, where it only has to show a way. */
	private static final int NOSE = 13;
	/** The side of the square a held head is painted on, whichever way it points. */
	static final int HEAD = 2 * (NOSE + PAD);
	/** Ways a held head is drawn pointing. An edge is exactly a quarter turn; only a corner lies between. */
	private static final int WAYS = 16;
	/** The label's height, its room either side of the words, and the most letters it holds. */
	private static final int PILL = 16;
	private static final int GAP = 6;
	private static final int SAYS = 26;
	/** The website draws a quiet member's disc at this opacity; the same number reads the same. */
	private static final float FADED = 0.55f;
	private static final int MAX_LABEL = 40;

	private final Color fill;
	private final Color rim;
	@Nullable
	private final String text;
	private final boolean faded;
	private final BufferedImage pin;
	private final Point tip;
	@Nullable
	private final Supplier<String> tooltip;
	/** The head by the way it points, painted as each way is first asked for; the last is no way known. */
	private final BufferedImage[] held = new BufferedImage[WAYS + 1];
	@Nullable
	private ToDoubleFunction<WorldPoint> way;
	private String says = "";

	/**
	 * @param text one or two letters on the head, or empty for the dot of a place with none
	 * @param tooltip computed when the map asks rather than once, so a countdown in it stays true
	 */
	MapPin(WorldPoint where, Color colour, String text, boolean faded, String name, @Nullable Supplier<String> tooltip)
	{
		this(where, colour, RIM, text, faded, name, tooltip);
	}

	private MapPin(WorldPoint where, Color fill, Color rim, @Nullable String text, boolean faded, String name,
		@Nullable Supplier<String> tooltip)
	{
		super(where, null);
		this.fill = fill;
		this.rim = rim;
		this.text = text;
		this.faded = faded;
		this.pin = drop(TAIL, Math.PI / 2, true);
		this.tip = new Point(pin.getWidth() / 2, PAD + (int) R + TAIL);
		this.tooltip = tooltip;
		setSnapToEdge(true);
		/** Pans the world map to the point. It does not walk, click or hop. */
		setJumpOnClick(true);
		setName(safe(name));
		onEdgeUnsnap();
	}

	/**
	 * How this pin holds the map's edge: pointing where {@code way} says its place lies, and saying
	 * {@code says} beside its head, or nothing for null. Without a way it is a round head.
	 */
	MapPin atEdge(@Nullable ToDoubleFunction<WorldPoint> way, @Nullable String says)
	{
		this.way = way;
		String words = plain(says);
		this.says = words.length() <= SAYS ? words : words.substring(0, SAYS - 1) + "…";
		java.util.Arrays.fill(held, null);
		return this;
	}

	/**
	 * The player's own boat: a dark head in a gold rim, the picture the website puts round the
	 * mooring that holds it and round a saved house, so "yours" is one mark on both. A hull and a
	 * sail are drawn rather than a letter, because a boat has no initials to give.
	 */
	static MapPin boat(WorldPoint where, @Nullable Supplier<String> tooltip)
	{
		return new MapPin(where, new Color(41, 37, 36), GOLD, null, false, "Your boat", tooltip);
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

	/**
	 * RuneLite asks for the picture every frame before it places the point, and says only that a
	 * point is held at the edge, never where. So a held pin looks again each time: the map scrolls
	 * under it, a friend walks, and the way it points has to follow both.
	 */
	@Override
	public BufferedImage getImage()
	{
		if (isCurrentlyEdgeSnapped())
		{
			setImage(heldPicture());
		}
		return super.getImage();
	}

	@Override
	public void onEdgeSnap()
	{
		setImage(heldPicture());
		/** Null centres the picture on the edge, which is where the direction is read from. */
		setImagePoint(null);
	}

	/**
	 * The head for the way the place lies now. With no way to point — the map shut, or the place
	 * come back under the head before RuneLite lets go of it — it is round and says nothing: RuneLite
	 * lets go only once the place is one and a half pictures inside the edge, and a wide label
	 * would hold a place in plain view at the border for most of the map.
	 */
	private BufferedImage heldPicture()
	{
		double to = way == null ? Double.NaN : way.applyAsDouble(getWorldPoint());
		boolean known = !Double.isNaN(to);
		int slot = known ? Math.floorMod((int) Math.round(to * WAYS / (2 * Math.PI)), WAYS) : WAYS;
		if (held[slot] == null)
		{
			double angle = slot * 2 * Math.PI / WAYS;
			BufferedImage head = drop(known ? NOSE : 0, angle, false);
			held[slot] = known && !says.isEmpty() ? labelled(head, angle) : head;
		}
		return held[slot];
	}

	/**
	 * The head with what it is beside it, on the side away from the edge: inward of a head pointing
	 * left or right, and over or under one pointing straight down or up, where a label to one side
	 * would push the head off the column its place is in.
	 */
	private BufferedImage labelled(BufferedImage head, double angle)
	{
		Font font = FontManager.getRunescapeSmallFont();
		Graphics2D measure = head.createGraphics();
		int words = measure.getFontMetrics(font).stringWidth(says) + 2 * GAP;
		measure.dispose();
		double across = Math.cos(angle);
		boolean upright = Math.abs(across) < 0.2;
		/* The label tucks a little under the head's rim, which is drawn over it. */
		int width = upright ? Math.max(HEAD, words) : HEAD + words - 10;
		int x = upright ? (width - words) / 2 : across < 0 ? HEAD - 10 : 0;
		int y = upright ? (Math.sin(angle) < 0 ? HEAD - 4 : 0) : (HEAD - PILL) / 2;
		BufferedImage image = new BufferedImage(width, upright ? HEAD + PILL - 4 : HEAD, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = paint(image, false);
		g.setColor(new Color(24, 24, 27, 225));
		g.fillRoundRect(x, y, words, PILL, 8, 8);
		g.setColor(Color.BLACK);
		g.drawRoundRect(x, y, words - 1, PILL - 1, 8, 8);
		/* The game's small font is drawn to the pixel; smoothing it blurs the words the map's own labels keep sharp. */
		g.setFont(font);
		g.setColor(RIM);
		g.drawString(says, x + GAP, y + PILL - 4);
		g.drawImage(head, upright ? (width - HEAD) / 2 : across < 0 ? 0 : width - HEAD, upright && y == 0 ? PILL - 4 : 0, null);
		g.dispose();
		return image;
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

	/**
	 * A member's disc, the colour and initials their pin's head wears on the world map, for the
	 * sidebar to draw beside their name: the same person in the same colours in both places.
	 */
	static BufferedImage memberDisc(Color colour, @Nullable String name)
	{
		return disc(colour, initials(name), false);
	}

	/**
	 * A member's disc: their colour, a black edge and their letters. The sidebar's Group tab draws a
	 * friend and a meetup's faces with this same picture, so a member is one mark on both.
	 */
	static BufferedImage disc(Color colour, String text, boolean faded)
	{
		BufferedImage image = new BufferedImage(DISC + 2, DISC + 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = paint(image, faded);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		ring(g, colour, Color.BLACK, 1.2f);
		if (!text.isEmpty())
		{
			letters(g, text, image.getWidth() / 2.0, image.getHeight() / 2.0);
		}
		g.dispose();
		return image;
	}

	/** Antialiased drawing on {@code image}, at the fade of a friend who is not here when {@code faded}. */
	private static Graphics2D paint(BufferedImage image, boolean faded)
	{
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (faded)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FADED));
		}
		return g;
	}

	/** A member-sized disc of {@code fill} with an {@code edge} ring {@code width} wide. */
	private static void ring(Graphics2D g, Color fill, Color edge, float width)
	{
		g.setColor(fill);
		g.fillOval(1, 1, DISC - 1, DISC - 1);
		g.setColor(edge);
		g.setStroke(new BasicStroke(width));
		g.drawOval(1, 1, DISC - 1, DISC - 1);
	}

	/**
	 * The head, drawn out to a point {@code reach} from its centre along {@code angle}, or left
	 * round where the reach is inside it. A standing pin is painted tight over the shadow its tip
	 * stands on; a held head on a square of {@link #HEAD}, whichever way it points. Painted solid
	 * and faded whole: fading each layer would show the rims through the fill.
	 */
	private BufferedImage drop(int reach, double angle, boolean standing)
	{
		double centre = standing ? PAD + R : HEAD / 2.0;
		int side = (int) (2 * centre);
		BufferedImage solid = new BufferedImage(side, standing ? side + reach + PAD - 1 : side, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = paint(solid, false);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (standing)
		{
			/* What the tip stands on: without it a pin floats over the map rather than marking a tile. */
			g.setColor(new Color(0, 0, 0, 70));
			g.fill(new Ellipse2D.Double(centre - 5, centre + reach - 2, 10, 4));
		}
		Shape shape = outline(centre, centre, reach, angle);
		g.setColor(INK);
		g.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(shape);
		g.setColor(rim);
		g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(shape);
		g.setColor(fill);
		g.fill(shape);
		g.translate(centre, centre);
		if (text == null)
		{
			g.setColor(new Color(244, 244, 245));
			/* The sail, a triangle off the mast, then the hull under it. */
			g.fillPolygon(new int[]{-1, -1, 4}, new int[]{-6, 2, 2}, 3);
			g.fillPolygon(new int[]{-6, 6, 4, -4}, new int[]{3, 3, 6, 6}, 4);
		}
		else if (text.isEmpty())
		{
			g.setColor(INK);
			g.fill(new Ellipse2D.Double(-3, -3, 6, 6));
		}
		else
		{
			letters(g, text, 0, 0);
		}
		g.dispose();
		if (!faded)
		{
			return solid;
		}
		BufferedImage image = new BufferedImage(solid.getWidth(), solid.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D quiet = paint(image, true);
		quiet.drawImage(solid, 0, 0, null);
		quiet.dispose();
		return image;
	}

	/**
	 * A circle of {@link #R} about a centre, drawn out to a point {@code reach} from it along
	 * {@code angle} — radians clockwise from east, as the screen counts them — or the bare circle
	 * where the reach is inside it.
	 */
	private static Shape outline(double x, double y, double reach, double angle)
	{
		if (reach <= R)
		{
			return new Ellipse2D.Double(x - R, y - R, 2 * R, 2 * R);
		}
		/* The point's sides leave the circle where they are tangent to it. */
		double open = Math.toDegrees(Math.acos(R / reach));
		Path2D shape = new Path2D.Double();
		shape.moveTo(x + reach, y);
		shape.append(new Arc2D.Double(x - R, y - R, 2 * R, 2 * R, open, 360 - 2 * open, Arc2D.OPEN), true);
		shape.closePath();
		return AffineTransform.getRotateInstance(angle, x, y).createTransformedShape(shape);
	}

	/** One or two letters centred on a point, near-black on the palette's light hues as the website's discs are. */
	private static void letters(Graphics2D g, String text, double x, double y)
	{
		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, text.length() > 1 ? 8 : 10));
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, (float) (x - metrics.stringWidth(text) / 2.0), (float) (y - metrics.getHeight() / 2.0 + metrics.getAscent()));
	}
}
