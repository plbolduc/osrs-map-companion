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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.Icon;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar's own small marks — the gear that opens Settings, the arrow that leaves it, the
 * search box's magnifier and the minimap's character dot — drawn rather than loaded.
 *
 * <p>Drawn for the reason the destination's dot and the friends' discs are: a bundled picture is
 * one more file for a reviewer to account for, and RuneLite's own config icons belong to the
 * client, which may move or redraw them. Each mark is sixteen pixels, the size of the client's
 * own, in the panel's light grey so it reads as a control and not as content.
 */
final class SidebarIcons
{
	private SidebarIcons()
	{
	}

	static final int SIZE = 16;

	/** Eight teeth around a ring: the shape every client uses for "settings". */
	static Icon gear()
	{
		return new Mark()
		{
			@Override
			void draw(Graphics2D g)
			{
				Area shape = new Area(new Ellipse2D.Double(3, 3, 10, 10));
				for (int i = 0; i < 8; i++)
				{
					Area tooth = new Area(new Rectangle2D.Double(6.5, 0.5, 3, 4));
					tooth.transform(AffineTransform.getRotateInstance(Math.PI / 4 * i, 8, 8));
					shape.add(tooth);
				}
				shape.subtract(new Area(new Ellipse2D.Double(5.5, 5.5, 5, 5)));
				g.fill(shape);
			}
		};
	}

	/** A chevron pointing back, as RuneLite's config panel heads a plugin's settings. */
	static Icon back()
	{
		return new Mark()
		{
			@Override
			void draw(Graphics2D g)
			{
				Path2D chevron = new Path2D.Double();
				chevron.moveTo(10.5, 2.5);
				chevron.lineTo(5, 8);
				chevron.lineTo(10.5, 13.5);
				g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(chevron);
			}
		};
	}

	/** A magnifier, for a search box drawn as a plain field so it can say what it searches. */
	static Icon magnifier()
	{
		return new Mark()
		{
			@Override
			void draw(Graphics2D g)
			{
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Ellipse2D.Double(2.5, 2.5, 8, 8));
				g.draw(new Line2D.Double(9.5, 9.5, 13.5, 13.5));
			}
		};
	}

	/**
	 * The minimap's yellow dot, which is how the game marks a character, for a search row about one.
	 * The game has no sprite for it to load: the client draws the dot itself.
	 */
	static Icon characterDot()
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(Color.BLACK);
				g2.fillOval(x + 3, y + 3, 10, 10);
				g2.setColor(new Color(0xFF, 0xFF, 0x00));
				g2.fillOval(x + 4, y + 4, 8, 8);
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return SIZE;
			}

			@Override
			public int getIconHeight()
			{
				return SIZE;
			}
		};
	}

	/** A sixteen-pixel mark in light grey, antialiased. */
	private abstract static class Mark implements Icon
	{
		private static final Color INK = ColorScheme.LIGHT_GRAY_COLOR;

		abstract void draw(Graphics2D g);

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.translate(x, y);
			g2.setColor(c != null && !c.isEnabled() ? INK.darker() : INK);
			draw(g2);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
