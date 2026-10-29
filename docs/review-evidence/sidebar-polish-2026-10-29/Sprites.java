package com.pbolduc.mapcompanion;

import com.google.inject.Binder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JLabel;

/**
 * The renderer's stand-in for the client's sprite cache, which needs a running game.
 *
 * <p>The panel asks {@link GameSprites} for an item by id or name and an interface sprite by id;
 * this answers from the website's copies of the same files when {@code OSRS_MAP_PUBLIC} names the
 * map's {@code public} folder — {@code osrs-icons/cache/<sprite id>.png}, and the item sprites by
 * name, for the handful of ids the scenes use. Without it every picture is left empty.
 */
final class Sprites
{
	private Sprites()
	{
	}

	/** The item ids the scenes use, by the website's file for them. */
	static final Map<Integer, String> ITEMS = new HashMap<>();

	static
	{
		ITEMS.put(314, "feather");
		ITEMS.put(2552, "ring-of-dueling-8");
		ITEMS.put(3853, "games-necklace-8");
		ITEMS.put(2722, "clue-scroll-hard");
		ITEMS.put(4151, "abyssal-whip");
	}

	static void bind(Binder binder)
	{
		String root = System.getenv("OSRS_MAP_PUBLIC");
		binder.bind(GameSprites.class).toInstance(new GameSprites(null, null, null)
		{
			@Override
			void item(JLabel label, int itemId, int box)
			{
				String name = ITEMS.get(itemId);
				if (name != null)
				{
					show(label, read(root, "osrs-items/1x/" + name + ".png"), box);
				}
			}

			@Override
			void itemNamed(JLabel label, String name, int box)
			{
				show(label, read(root, "osrs-items/1x/" + name.toLowerCase().replace("'", "").replace(' ', '-') + ".png"), box);
			}

			@Override
			void sprite(JLabel label, int spriteId, int box)
			{
				/* The combat tab's crossed swords is the one sprite the website keeps under a name of its own. */
				show(label, read(root, spriteId == net.runelite.api.gameval.SpriteID.SideIcons.COMBAT
					? "osrs-icons/1x/skill-combat.png" : "osrs-icons/cache/" + spriteId + ".png"), box);
			}

			/**
			 * The world switcher's two stars are not among the website's files, so the renderer paints
			 * a stand-in of their colour and size. The client draws the game's own sprite.
			 */
			@Override
			void sprite(javax.swing.AbstractButton button, int spriteId, int box)
			{
				boolean members = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.MEMBERS;
				boolean free = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.FREE;
				if (!members && !free)
				{
					return;
				}
				BufferedImage star = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g = star.createGraphics();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				java.awt.Polygon shape = new java.awt.Polygon();
				for (int i = 0; i < 10; i++)
				{
					double r = i % 2 == 0 ? 6.4 : 2.7;
					double a = Math.PI / 2 + i * Math.PI / 5;
					shape.addPoint((int) Math.round(6.5 + r * Math.cos(a)), (int) Math.round(6.5 - r * Math.sin(a)));
				}
				g.setColor(members ? new java.awt.Color(0xF5C542) : new java.awt.Color(0xC9CDD3));
				g.fillPolygon(shape);
				g.dispose();
				show(button, star, box);
			}

			@Override
			void skill(JLabel label, String name, int box)
			{
				show(label, read(root, "osrs-icons/1x/skill-" + name.toLowerCase() + ".png"), box);
			}
		});
	}

	static BufferedImage read(String root, String path)
	{
		if (root == null)
		{
			return null;
		}
		try
		{
			File file = new File(root, path);
			return file.isFile() ? ImageIO.read(file) : null;
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
