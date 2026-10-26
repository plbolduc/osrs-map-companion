package com.pbolduc.mapcompanion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.api.coords.WorldPoint;

/**
 * Paints every picture MapPin draws, at three times their size, on the world map's parchment
 * colour: a friend, the same friend gone quiet, the destination and a meetup, each as the pin it
 * is while its place is in view and as the disc it becomes on the map's edge. Compile beside the
 * harness classpath as SidebarShot is (docs/sidebar-layout.md) and pass the output directory.
 */
public class PinShot
{
	public static void main(String[] args) throws Exception
	{
		WorldPoint at = new WorldPoint(3222, 3218, 0);
		MapPin[] pins = {
			new MapPin(at, MapPin.colour("#38bdf8", Color.GRAY), MapPin.initials("Big Rob"), false, "Big Rob", null),
			new MapPin(at, MapPin.colour("#38bdf8", Color.GRAY), MapPin.initials("Big Rob"), true, "Big Rob", null),
			new MapPin(at, MapPin.colour("#a78bfa", Color.GRAY), MapPin.initials("Zezima"), false, "Zezima", null),
			new MapPin(at, MapPin.AMBER, "", false, "Destination", null),
			new MapPin(at, MapPin.AMBER, "M", false, "Star hunt", null),
		};
		String[] labels = {"Friend", "Quiet", "Friend", "Destination", "Meetup"};
		int scale = 3, cell = 34, width = cell * pins.length, height = 64;
		BufferedImage out = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(0xd8c9a3));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		for (int i = 0; i < pins.length; i++)
		{
			BufferedImage pin = pins[i].getImage();
			int x = i * cell + (cell - pin.getWidth()) / 2;
			g.drawImage(pin, x * scale, 2 * scale, pin.getWidth() * scale, pin.getHeight() * scale, null);
			pins[i].onEdgeSnap();
			BufferedImage disc = pins[i].getImage();
			int dx = i * cell + (cell - disc.getWidth()) / 2;
			g.drawImage(disc, dx * scale, 32 * scale, disc.getWidth() * scale, disc.getHeight() * scale, null);
			g.setColor(new Color(0x3b2f1e));
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9 * scale / 2));
			g.drawString(labels[i], (i * cell + 2) * scale, 61 * scale);
		}
		g.dispose();
		ImageIO.write(out, "png", new File(args[0], "world-map-pins.png"));
		System.out.println(args[0] + "/world-map-pins.png");
		System.exit(0);
	}
}
