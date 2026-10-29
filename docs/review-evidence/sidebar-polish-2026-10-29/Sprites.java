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
		binder.bind(GameSprites.class).toInstance(new GameSprites(null, null)
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
