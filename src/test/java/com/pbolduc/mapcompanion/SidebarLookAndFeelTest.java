package com.pbolduc.mapcompanion;

import static org.junit.Assert.assertEquals;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.UIManager;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

/**
 * The suite's Swing is measured in RuneLite's font, and this says so when it is not.
 *
 * <p>The look and feel is asked for by a system property, and Swing answers a name it cannot load
 * by quietly using its own default. Every other test would then go back to the machine's font and
 * pass or fail by which fonts are installed, with nothing to say why.
 */
public class SidebarLookAndFeelTest
{
	@Test
	public void swingUnderTestIsBuiltUnderTheSidebarsLookAndFeel()
	{
		assertEquals(SidebarLookAndFeel.class, UIManager.getLookAndFeel().getClass());
	}

	@Test
	public void aLabelAndAButtonAreInTheFontRuneLiteGivesItsSidebar()
	{
		String family = FontManager.getRunescapeFont().getFamily();
		assertEquals(family, new JLabel().getFont().getFamily());
		assertEquals(family, new JButton().getFont().getFamily());
	}
}
