package com.pbolduc.mapcompanion;

import java.awt.Font;
import javax.swing.UIDefaults;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import net.runelite.client.ui.FontManager;

/**
 * The look and feel every test's Swing is built under: Metal, with the font RuneLite gives its
 * sidebar.
 *
 * <p>A sidebar card decides things by measuring text. A Slayer place's name breaks before its last
 * two words when it is too wide for the card's line, and a pinned star scrolls into view by the
 * height its head came to. Under test nothing sets a look and feel, so the measuring was done in
 * the machine's own default font: the suite passed where that font is narrow and failed four
 * tests where it is wide, on the same commit, and neither is the font a player has. RuneLite's
 * own look and feel makes {@code FontManager.getRunescapeFont()} the default for every
 * component, from a file inside the client jar, so that is the font used here and the answer is
 * the same on every machine, and the one the client gives.
 *
 * <p>Named in {@code build.gradle} as {@code swing.defaultlaf}, which Swing reads before the
 * first component is made, so no test has to ask for it and a new test cannot forget to. Only the
 * font is RuneLite's. Its colours and borders are FlatLaf's and are not what these tests read.
 */
public class SidebarLookAndFeel extends MetalLookAndFeel
{
	@Override
	protected void initComponentDefaults(UIDefaults table)
	{
		super.initComponentDefaults(table);
		Font font = new FontUIResource(FontManager.getRunescapeFont());
		for (Object key : table.keySet().toArray())
		{
			if (key instanceof String && ((String) key).toLowerCase().endsWith("font"))
			{
				table.put(key, font);
			}
		}
	}
}
