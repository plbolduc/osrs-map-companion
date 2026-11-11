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
 * colour. The top row is each pin while its place is in view: a friend, the same friend gone
 * quiet, the destination, a route's step, a meetup and the player's boat. Under it are the heads
 * the map's edge holds, each pointing the way its place lies, and the ones the player asked about
 * saying what they are. Compile beside the harness classpath as SidebarShot is
 * (docs/sidebar-layout.md) and pass the output directory.
 */
public class PinShot
{
	private static final WorldPoint AT = new WorldPoint(3222, 3218, 0);
	private static final Color BLUE = MapPin.colour("#38bdf8", Color.GRAY);

	/** A pin as the edge holds it, its place lying {@code degrees} clockwise from east. */
	private static BufferedImage held(MapPin pin, double degrees, String says)
	{
		pin.atEdge(place -> Math.toRadians(degrees), says);
		pin.setCurrentlyEdgeSnapped(true);
		pin.onEdgeSnap();
		return pin.getImage();
	}

	public static void main(String[] args) throws Exception
	{
		MapPin[] pins = {
			new MapPin(AT, BLUE, MapPin.initials("Big Rob"), false, "Big Rob", null),
			new MapPin(AT, BLUE, MapPin.initials("Big Rob"), true, "Big Rob", null),
			new MapPin(AT, MapPin.colour("#a78bfa", Color.GRAY), MapPin.initials("Zezima"), false, "Zezima", null),
			new MapPin(AT, MapPin.AMBER, "", false, "Destination", null),
			new MapPin(AT, MapPin.AMBER, "1", false, "Step 1", null),
			new MapPin(AT, DestinationMarker.STEP, "2", false, "Step 2", null),
			new MapPin(AT, MapPin.AMBER, "M", false, "Star hunt", null),
			MapPin.boat(AT, null),
		};
		String[] labels = {"Friend", "Quiet", "Friend", "Place", "Step", "Ahead", "Meetup", "Boat"};
		BufferedImage[] edge = {
			held(pins[0], 180, null),
			held(pins[1], 225, null),
			held(pins[2], 270, null),
			held(pins[4], 180, "Use Catherby tablet"),
			held(pins[3], 0, "Catherby farming patch"),
			held(pins[6], 90, "Star hunt"),
			held(new MapPin(AT, MapPin.AMBER, "3", false, "Farm stop 3", null), 270, "Ardougne"),
		};
		int scale = 3, cell = 36, width = cell * pins.length, top = 56, row = 46, height = top + 3 * row;
		BufferedImage out = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(0xd8c9a3));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9 * scale / 2));
		for (int i = 0; i < pins.length; i++)
		{
			/* The pins above were held for the rows below; the picture in view is the one they go back to. */
			pins[i].setCurrentlyEdgeSnapped(false);
			pins[i].onEdgeUnsnap();
			BufferedImage pin = pins[i].getImage();
			int x = i * cell + (cell - pin.getWidth()) / 2;
			g.drawImage(pin, x * scale, 2 * scale, pin.getWidth() * scale, pin.getHeight() * scale, null);
			g.setColor(new Color(0x3b2f1e));
			g.drawString(labels[i], (i * cell + 2) * scale, 49 * scale);
		}
		int x = 4, y = top;
		for (BufferedImage head : edge)
		{
			if (x + head.getWidth() > width - 4)
			{
				x = 4;
				y += row;
			}
			g.drawImage(head, x * scale, (y + (44 - head.getHeight()) / 2) * scale, head.getWidth() * scale, head.getHeight() * scale, null);
			x += head.getWidth() + 8;
		}
		g.dispose();
		ImageIO.write(out, "png", new File(args[0], "world-map-pins.png"));
		System.out.println(args[0] + "/world-map-pins.png");
		System.exit(0);
	}
}
