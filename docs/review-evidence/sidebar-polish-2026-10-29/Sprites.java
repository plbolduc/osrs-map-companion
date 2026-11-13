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
	/**
	 * The spell icons the scenes use, by the website's copy of the same picture: the client's sprite
	 * cache holds the spellbook's icons, which a renderer without a running game cannot open.
	 */
	static final Map<Integer, String> SPELLS = new HashMap<>();
	/** Items the website keeps only as a travel chip, the same wiki sprite already cut to a step's 16px. */
	static final Map<Integer, String> CHIPS = new HashMap<>();

	static
	{
		ITEMS.put(314, "feather");
		ITEMS.put(2552, "ring-of-dueling-8");
		ITEMS.put(3853, "games-necklace-8");
		ITEMS.put(2722, "clue-scroll-hard");
		ITEMS.put(4151, "abyssal-whip");
		ITEMS.put(net.runelite.api.gameval.ItemID.LUNAR_TABLET_CATHERBY_TELEPORT, "catherby-teleport-tablet");
		ITEMS.put(net.runelite.api.gameval.ItemID.MYSTIC_ROBE_BOTTOM_DARK, "mystic-robe-bottom-dark");
		ITEMS.put(net.runelite.api.gameval.ItemID.TWPART3, "cannon-barrels");
		ITEMS.put(6737, "berserker-ring");
		ITEMS.put(6735, "warrior-ring");
		ITEMS.put(6739, "dragon-axe");
		CHIPS.put(net.runelite.api.gameval.ItemID.SAILING_LOG, "captains-log");
		CHIPS.put(net.runelite.api.gameval.ItemID.SAILING_CHARTING_SPYGLASS, "spyglass");
		CHIPS.put(net.runelite.api.gameval.ItemID.SAILING_CHARTING_CROWBAR, "crowbar");
		CHIPS.put(net.runelite.api.gameval.ItemID.SAILING_CHARTING_CURRENT_DUCK, "current-duck");
		CHIPS.put(net.runelite.api.gameval.ItemID.HUNDRED_PIRATE_DIVING_HELMET, "fishbowl-helmet");
		CHIPS.put(net.runelite.api.gameval.ItemID.SAILING_CHARTING_WEATHER_STATION_EMPTY, "portable-weather-station");
		SPELLS.put(net.runelite.api.gameval.SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT, "home-teleport");
		SPELLS.put(net.runelite.api.gameval.SpriteID.MagicNecroOn.DRAYNOR_MANOR_TELEPORT, "draynor-manor-teleport");
		CHIPS.put(net.runelite.api.gameval.ItemID.NZONE_TELETAB_RIMMINGTON, "teleport-tab-rimmington");
	}

	/** The same item sprite that the live client's ItemManager supplies to Drop luck. */
	static BufferedImage itemImage(int itemId)
	{
		String name = ITEMS.get(itemId);
		return name == null ? null : read(System.getenv("OSRS_MAP_PUBLIC"), "osrs-items/1x/" + name + ".png");
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
				else if (CHIPS.containsKey(itemId))
				{
					show(label, read(root, "osrs-icons/1x/" + CHIPS.get(itemId) + ".png"), box);
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
				BufferedImage star = worldStar(spriteId);
				if (star != null)
				{
					show(label, star, box);
					return;
				}
				/*
				 * The combat tab's crossed swords is the one sprite the website keeps under a name of its own.
				 * It also stands in for the multi-combat zone's swords, which the website has no copy of.
				 */
				BufferedImage kept = read(root, spriteId == net.runelite.api.gameval.SpriteID.SideIcons.COMBAT
					|| spriteId == net.runelite.api.gameval.SpriteID.OVERLAY_MULTIWAY
					? "osrs-icons/1x/skill-combat.png" : SPELLS.containsKey(spriteId)
					? "osrs-icons/1x/" + SPELLS.get(spriteId) + ".png" : "osrs-icons/cache/" + spriteId + ".png");
				/*
				 * The walking boot and the two Sailing boats a step is drawn by: sprites the website keeps
				 * no copy of, so they are kept beside this file, as the game cache holds them.
				 */
				show(label, kept != null ? kept
					: read("docs/review-evidence/sidebar-polish-2026-10-29", "sprites/" + spriteId + ".png"), box);
			}

			@Override
			void sprite(javax.swing.AbstractButton button, int spriteId, int box)
			{
				BufferedImage star = worldStar(spriteId);
				if (star != null)
				{
					show(button, star, box);
				}
			}

			@Override
			void skill(JLabel label, String name, int box)
			{
				show(label, read(root, "osrs-icons/1x/skill-" + name.toLowerCase() + ".png"), box);
			}
		});
	}

	/**
	 * The world switcher's stars are not among the website's files, so the renderer paints a stand-in
	 * of each one's colour and size, in the website's own colours for them: gold for members, grey
	 * for free, solid red for PvP and a red outline for Deadman. The client draws the game's own
	 * sprite. Null for any other sprite.
	 */
	static BufferedImage worldStar(int spriteId)
	{
		boolean members = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.MEMBERS;
		boolean free = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.FREE;
		boolean pvp = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.PVP;
		boolean deadman = spriteId == net.runelite.api.gameval.SpriteID.WorldswitcherStars.DEADMAN;
		if (!members && !free && !pvp && !deadman)
		{
			return null;
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
		g.setColor(members ? new java.awt.Color(0xF5C542) : free ? new java.awt.Color(0xC9CDD3) : new java.awt.Color(0xE02020));
		if (deadman)
		{
			g.drawPolygon(shape);
		}
		else
		{
			g.fillPolygon(shape);
		}
		g.dispose();
		return star;
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
